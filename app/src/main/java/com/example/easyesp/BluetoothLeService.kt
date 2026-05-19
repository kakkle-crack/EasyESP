package com.example.easyesp

import android.Manifest
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.UUID

/**
 * BluetoothLeService manages the BLE connection and communication with the ESP32.
 * Optimized for battery life and better code structure.
 */
class BluetoothLeService : Service() {

    private val bluetoothManager: BluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val bluetoothAdapter: BluetoothAdapter by lazy { bluetoothManager.adapter }
    private val bleScanner by lazy { bluetoothAdapter.bluetoothLeScanner }
    private var isScanning = false
    private var bluetoothGatt: BluetoothGatt? = null

    private val handler = Handler(Looper.getMainLooper())
    private val scanRunnable = Runnable { startBleScan() }

    companion object {
        private const val TAG = "BluetoothLeService"
        const val ACTION_DATA_AVAILABLE = "com.example.easyesp.ACTION_DATA_AVAILABLE"
        const val EXTRA_DATA = "com.example.easyesp.EXTRA_DATA"
        const val ACTION_MANAGE_SCAN_STATE = "com.example.easyesp.ACTION_MANAGE_SCAN_STATE"

        val SERVICE_UUID: UUID = UUID.fromString("1fc8d4ca-3b3d-42e3-bdf0-1ff2edcf8268")
        val CHARACTERISTIC_UUID_RX: UUID = UUID.fromString("586eb1c5-597a-4c5a-bfcf-655d4909b7a1")
        val CHARACTERISTIC_UUID_TX: UUID = UUID.fromString("586eb1c5-597a-4c5a-bfcf-655d4909b7a2")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val SCAN_PERIOD = 10000L
        private const val SCAN_INTERVAL = 30000L
    }

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() { fun getService(): BluetoothLeService = this@BluetoothLeService }
    override fun onBind(intent: Intent): IBinder = binder

    fun initialize(): Boolean {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
            return false
        }
        return true
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Connected to GATT server.")
                    bluetoothGatt = gatt
                    if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                        bluetoothGatt?.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "Disconnected from GATT server.")
                    ViewModelHolder.connectionViewModel?.setBleConnected(false)
                    close()
                    startBleScan()
                }
            } else {
                Log.e(TAG, "GATT connection error: status=$status")
                disconnect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered.")
                ViewModelHolder.connectionViewModel?.setBleConnected(true)
                enableNotifications(gatt)
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
                disconnect()
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val data = value.toString(Charsets.UTF_8)
            broadcastUpdate(ACTION_DATA_AVAILABLE, data)
        }
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.i(TAG, "Found compatible device: ${result.device.address} (${result.device.name ?: "Unnamed"})")
            stopBleScan()
            connectToDevice(result.device)
        }
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            isScanning = false
        }
    }

    fun startBleScan() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            Log.e(TAG, "Cannot start scan: BLUETOOTH_SCAN permission not granted.")
            return
        }

        val sharedPreferences = getSharedPreferences(BluetoothSettingsFragment.PREFS_NAME, MODE_PRIVATE)
        val isDiscoveryEnabled = sharedPreferences.getBoolean(BluetoothSettingsFragment.KEY_DISCOVERY_ENABLED, false)

        if (!isDiscoveryEnabled || bluetoothGatt != null) {
            stopBleScan()
            return
        }

        if (!isScanning) {
            // RELAXED FILTERING: Use Service UUID to find any compatible EasyESP device.
            // This is much more robust than name-based filtering.
            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()
            
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()

            Log.i(TAG, "Starting BLE scan for Service UUID: $SERVICE_UUID")
            bleScanner.startScan(listOf(scanFilter), scanSettings, leScanCallback)
            isScanning = true

            // Stop scanning after SCAN_PERIOD to save battery
            handler.postDelayed({ stopBleScan() }, SCAN_PERIOD)
        }

        // Reschedule next scan check
        handler.removeCallbacks(scanRunnable)
        handler.postDelayed(scanRunnable, SCAN_INTERVAL)
    }

    private fun stopBleScan() {
        if (isScanning && hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            bleScanner.stopScan(leScanCallback)
            isScanning = false
            Log.i(TAG, "Scan stopped.")
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt?) {
        val service = gatt?.getService(SERVICE_UUID)
        val txChar = service?.getCharacteristic(CHARACTERISTIC_UUID_TX) ?: return

        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        gatt.setCharacteristicNotification(txChar, true)

        val descriptor = txChar.getDescriptor(CCCD_UUID)
        if (descriptor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        Log.i(TAG, "Connecting to ${device.address}")
        device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            bluetoothGatt?.disconnect()
        }
    }

    private fun close() {
        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            bluetoothGatt?.close()
        }
        bluetoothGatt = null
    }

    fun writeCharacteristic(data: String) {
        val gatt = bluetoothGatt ?: return
        val service = gatt.getService(SERVICE_UUID) ?: return
        val rxChar = service.getCharacteristic(CHARACTERISTIC_UUID_RX) ?: return

        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        val bytes = data.toByteArray(Charsets.UTF_8)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(rxChar, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            rxChar.value = bytes
            rxChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            gatt.writeCharacteristic(rxChar)
        }
    }

    private fun broadcastUpdate(action: String, data: String) {
        val intent = Intent(action).apply { putExtra(EXTRA_DATA, data) }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_MANAGE_SCAN_STATE) {
            startBleScan()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(scanRunnable)
        disconnect()
        close()
    }
}
