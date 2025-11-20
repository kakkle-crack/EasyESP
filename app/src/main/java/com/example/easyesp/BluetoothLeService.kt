// In C:/Users/kakkl/AndroidStudioProjects/EasyESP/app/src/main/java/com/example/easyesp/BluetoothLeService.kt
package com.example.easyesp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.Handler
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.UUID
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.core.app.NotificationManagerCompat

class BluetoothLeService : Service() {

    private val bluetoothManager: BluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val bluetoothAdapter: BluetoothAdapter by lazy { bluetoothManager.adapter }
    private val bleScanner by lazy { bluetoothAdapter.bluetoothLeScanner }
    private var isScanning = false
    private val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
    private var bluetoothGatt: BluetoothGatt? = null

    private val handler = Handler(Looper.getMainLooper())
    private val scanRunnable = Runnable { startBleScan() }

    companion object {
        private const val TAG = "BluetoothLeService"
        private const val FOREGROUND_CHANNEL_ID = "ESP32_Service_Channel"
        private const val EVENTS_CHANNEL_ID = "ESP32_Events_Channel"
        private const val FOREGROUND_NOTIFICATION_ID = 1

        const val ACTION_GATT_CONNECTED = "com.example.easyesp.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "com.example.easyesp.ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED = "com.example.easyesp.ACTION_GATT_SERVICES_DISCOVERED"
        const val ACTION_DATA_AVAILABLE = "com.example.easyesp.ACTION_DATA_AVAILABLE"
        const val EXTRA_DATA = "com.example.easyesp.EXTRA_DATA"

        const val ACTION_MANAGE_SCAN_STATE = "com.example.easyesp.ACTION_MANAGE_SCAN_STATE"

        val SERVICE_UUID: UUID = UUID.fromString("1fc8d4ca-3b3d-42e3-bdf0-1ff2edcf8268")
        // Your ESP code only needs the RX characteristic for provisioning.
        val CHARACTERISTIC_UUID_RX: UUID = UUID.fromString("586eb1c5-597a-4c5a-bfcf-655d4909b7a1")
        val CHARACTERISTIC_UUID_TX: UUID = UUID.fromString("586eb1c5-597a-4c5a-bfcf-655d4909b7a2")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
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
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {            val deviceName = if (ContextCompat.checkSelfPermission(this@BluetoothLeService, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                gatt?.device?.name ?: "Unknown Device"
            } else { "Unknown Device" }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.w(TAG, "****** SUCCESSFULLY CONNECTED to ${gatt?.device?.address} ******")
                    bluetoothGatt = gatt
                    // *** FIX: Discover services immediately upon connection ***
                    // Don't wait for MTU. For this simple use case, it's more reliable.
                    if (ContextCompat.checkSelfPermission(this@BluetoothLeService, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        bluetoothGatt?.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w(TAG, "****** DISCONNECTED from ${gatt?.device?.address} ******")
                    ViewModelHolder.connectionViewModel?.isBleConnected?.postValue(false)
                    if (ContextCompat.checkSelfPermission(this@BluetoothLeService, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        bluetoothGatt?.close()
                    }
                    bluetoothGatt = null
                    startBleScan()
                }
            } else {
                Log.e(TAG, "Connection Error: status=$status, newState=$newState")
                disconnect()
            }
        }

        // *** FIX: REMOVED onMtuChanged callback entirely for simplicity and reliability ***

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered successfully.")
                // *** FIX: Broadcast that we are fully connected *after* services are discovered ***
                // This is the true "ready" state.
                ViewModelHolder.connectionViewModel?.isBleConnected?.postValue(true)
                enableNotifications(gatt)
            } else {
                Log.w(TAG, "Service discovery failed with status: $status")
                disconnect() // If service discovery fails, we can't do anything. Disconnect.
            }
        }
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val data = value.toString(Charsets.UTF_8)
            Log.i(TAG, "Received notification: $data")
            broadcastUpdate(ACTION_DATA_AVAILABLE, data)
        }
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            // *** FIX: Use BLUETOOTH_SCAN permission for scanning, not CONNECT ***
            if (ContextCompat.checkSelfPermission(this@BluetoothLeService, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Found device: ${result.device.name ?: "Unnamed"} - Address: ${result.device.address}")
                stopBleScan()
                //bluetoothGatt = result.device.connectGatt(this@BluetoothLeService, false, gattCallback)
                connectToDevice(result.device) //needed? correct? idk
            }
        }
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan Failed with error code: $errorCode")
            isScanning = false
        }
    }

    fun startBleScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot start scan, BLUETOOTH_SCAN permission not granted.")
            return
        }

        val sharedPreferences = getSharedPreferences(BluetoothSettingsFragment.PREFS_NAME, Service.MODE_PRIVATE)
        val isDiscoveryEnabled = sharedPreferences.getBoolean(BluetoothSettingsFragment.KEY_DISCOVERY_ENABLED, false)

        // If discovery is disabled, ensure everything is stopped and do not proceed.
        if (!isDiscoveryEnabled) {
            Log.i(TAG, "Scan not starting: Device Discovery is disabled in settings.")
            stopBleScan() // This will stop scanning and cancel any pending runnables.
            if (bluetoothGatt != null) {
                disconnect()
            }
            return
        }

        // If we are already connected, there's no need to scan. Stop the loop.
        if (bluetoothGatt != null) {
            Log.i(TAG, "Scan check stopped: Already connected to a device.")
            stopBleScan()
            return
        }

        // If we got here, discovery is on and we are not connected. Let's scan.
        if (!isScanning) {
            val deviceNameToScanFor = sharedPreferences.getString(BluetoothSettingsFragment.KEY_DEVICE_NAME, BluetoothSettingsFragment.DEFAULT_DEVICE_NAME)
            Log.i(TAG, "Starting BLE scan for device name: $deviceNameToScanFor")
            val scanFilter = ScanFilter.Builder().setDeviceName(deviceNameToScanFor).build()
            bleScanner.startScan(listOf(scanFilter), scanSettings, leScanCallback)
            isScanning = true
        }

        // Always schedule the next check. This runnable will call startBleScan() again,
        // which will then decide if it needs to continue or stop.
        handler.postDelayed(scanRunnable, 15000)
    }

    private fun stopBleScan() {
        // This function's only job is to stop everything: the hardware scan AND the scheduled checks.
        handler.removeCallbacks(scanRunnable)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        if (isScanning) {
            bleScanner.stopScan(leScanCallback)
            isScanning = false
            Log.i(TAG, "Hardware BLE scan stopped.")
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt?) {
        val service = gatt?.getService(SERVICE_UUID)
        val txCharacteristic = service?.getCharacteristic(CHARACTERISTIC_UUID_TX)
        if (txCharacteristic == null) {
            Log.e(TAG, "TX Characteristic not found.")
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        gatt.setCharacteristicNotification(txCharacteristic, true)

        val descriptor = txCharacteristic.getDescriptor(CCCD_UUID)
        if (descriptor != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
            Log.i(TAG, "Enabled notifications for TX characteristic.")
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) { return }
        Log.i(TAG, "Attempting to connect to device: ${device.address}")
        device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) { return }
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    // *** FIX: REMOVED enableNotifications(). It's no longer needed. ***

    fun writeCharacteristic(data: String) {
        if (bluetoothGatt == null) {
            Log.e(TAG, "Cannot write, GATT not connected.")
            return
        }
        val service = bluetoothGatt?.getService(SERVICE_UUID)
        if (service == null) {
            Log.e(TAG, "Service UUID not found.")
            return
        }
        val rxCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID_RX)
        if (rxCharacteristic == null) {
            Log.e(TAG, "RX Characteristic not found.")
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) { return }
        val dataBytes = data.toByteArray(Charsets.UTF_8)
        rxCharacteristic.value = dataBytes
        rxCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        bluetoothGatt?.writeCharacteristic(rxCharacteristic)
        Log.i(TAG, "Wrote to characteristic: $data")
    }

    // --- Utility Functions ---
    private fun broadcastUpdate(action: String, data: String) {
        val intent = Intent(action)
        intent.putExtra(EXTRA_DATA, data)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    // *** All other functions like onCreate, onStartCommand, etc., remain the same but are not shown for brevity ***
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BluetoothLeService created.")
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BluetoothLeService started.")
        if (intent?.action == ACTION_MANAGE_SCAN_STATE) {
            Log.i(TAG, "Received command to manage scan state.")
            // This will re-evaluate the scanning logic based on current settings
            startBleScan()
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        Log.d(TAG, "BluetoothLeService destroyed.")
    }
}