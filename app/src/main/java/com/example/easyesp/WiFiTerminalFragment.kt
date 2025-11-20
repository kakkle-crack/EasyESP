package com.example.easyesp

import android.content.*
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.navArgs // Keep this import
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.net.InetAddress
import java.net.Socket

class WifiTerminalFragment : Fragment() {

    // --- Core Logic & State ---
    private var bluetoothLeService: BluetoothLeService? = null
    private var isBound = false
    private var isProvisioning = false
    private var tcpSocket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private lateinit var nsdManager: NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListener: NsdManager.ResolveListener? = null
    private val SERVICE_TYPE = "_easyesp._tcp."
    private val TAG = "EasyESP_Fragment"
    private var isResolving = false

    // --- ViewModel ---
    private val connectionViewModel: ConnectionViewModel by activityViewModels()

    // --- NEW: Navigation Arguments and Filename for saving ---
    private val args: WifiTerminalFragmentArgs by navArgs()
    private val FILENAME = "known_devices.dat"

    // --- UI Elements ---
    private lateinit var connectionStatusText: TextView
    private lateinit var wifiStatusText: TextView
    private lateinit var serialMonitorTextView: TextView
    private lateinit var serialMonitorScrollView: ScrollView
    private lateinit var ssidInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var connectButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_wifi_terminal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- UI Initialization ---
        connectionStatusText = view.findViewById(R.id.connection_status_text)
        wifiStatusText = view.findViewById(R.id.network_Textview)
        serialMonitorTextView = view.findViewById(R.id.serialMonitorTextView)
        serialMonitorScrollView = view.findViewById(R.id.serialMonitorScrollView)
        ssidInput = view.findViewById(R.id.ssidText1)
        passwordInput = view.findViewById(R.id.passText2)
        connectButton = view.findViewById(R.id.connectButton)

        // --- ViewModel Observers (Your existing code is correct) ---
        connectionViewModel.connectionStatusText.observe(viewLifecycleOwner, Observer { status ->
            connectionStatusText.text = status
        })
        connectionViewModel.wifiStatusText.observe(viewLifecycleOwner, Observer { status ->
            wifiStatusText.text = status
        })
        connectionViewModel.serialLog.observe(viewLifecycleOwner, Observer { log ->
            serialMonitorTextView.text = log
            serialMonitorScrollView.post { serialMonitorScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        })
        connectionViewModel.isBleConnected.observe(viewLifecycleOwner, Observer { isConnected ->
            if (connectionViewModel.isTcpConnected.value == true) return@Observer
            if (isConnected) {
                connectionViewModel.connectionStatusText.value = "Status: Connected (BLE)"
                appendToSerialMonitor("System: Bluetooth Connected")
            } else {
                if (connectionViewModel.connectionStatusText.value?.contains("BLE") == true) {
                    connectionViewModel.connectionStatusText.value = "Status: Disconnected"
                    appendToSerialMonitor("System: Bluetooth Disconnected")
                }
            }
        })

        // Manually update UI from ViewModel
        connectionStatusText.text = connectionViewModel.connectionStatusText.value
        wifiStatusText.text = connectionViewModel.wifiStatusText.value
        serialMonitorTextView.text = connectionViewModel.serialLog.value
        serialMonitorScrollView.post { serialMonitorScrollView.fullScroll(ScrollView.FOCUS_DOWN) }

        // --- Initial Setup Logic (Your existing code is correct) ---
        val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val wifiInfo = wifiManager.connectionInfo
        if (wifiInfo != null && wifiInfo.networkId != -1) {
            var currentSsid = wifiInfo.ssid
            if (currentSsid.startsWith("\"") && currentSsid.endsWith("\"")) {
                currentSsid = currentSsid.substring(1, currentSsid.length - 1)
            }
            ssidInput.setText(currentSsid)
        }
        connectButton.setOnClickListener {
            val ssid = ssidInput.text.toString()
            val pass = passwordInput.text.toString()

            if (ssid.isNotEmpty() && ssid != "SSID") {
                isProvisioning = true
                connectionViewModel.wifiStatusText.value = "WiFi: Provisioning..."
                val wifiData = "WIFI:$ssid,$pass"
                bluetoothLeService?.writeCharacteristic(wifiData)
                appendToSerialMonitor("App -> ESP32 (BLE): Sent WiFi credentials")
            } else {
                Toast.makeText(requireContext(), "SSID cannot be empty.", Toast.LENGTH_LONG).show()
            }
        }

        // *** NEW: Check for incoming IP Address to auto-connect ***
        args.ipAddress?.let { ip ->
            if (connectionViewModel.isTcpConnected.value != true) {
                Log.d(TAG, "Received IP to auto-connect: $ip")
                // Use a coroutine to resolve the InetAddress and connect
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val inetAddr = InetAddress.getByName(ip)
                        // The connect function already handles switching threads
                        connectToTcpServer(inetAddr, 8888) // Assuming default port 8888
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Invalid or unreachable IP Address.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun connectToTcpServer(host: InetAddress, port: Int) {
        if (connectionViewModel.isTcpConnected.value == true) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                tcpSocket = Socket(host, port)
                connectionViewModel.tcpClient = tcpSocket
                writer = PrintWriter(tcpSocket!!.getOutputStream(), true)
                reader = BufferedReader(InputStreamReader(tcpSocket!!.getInputStream()))
                connectionViewModel.tcpWriter = writer

                withContext(Dispatchers.Main) {
                    connectionViewModel.isTcpConnected.value = true
                    connectionViewModel.connectionStatusText.value = "Status: Connected (WiFi)"
                    connectionViewModel.wifiStatusText.value = "Host: ${host.hostAddress}"
                    connectionViewModel.connectedDeviceIp.value = host.hostAddress
                    appendToSerialMonitor("System: TCP socket connected to ESP32 at ${host.hostAddress}")
                    Toast.makeText(requireContext(), "Connected to ESP32 via WiFi!", Toast.LENGTH_SHORT).show()
                }

                // *** NEW: Save the successfully connected device ***
                // Defaulting the name, but this could be improved later (e.g., asking the user)
                val deviceName = "ESP32-${host.hostAddress.takeLast(3)}"
                val newDevice = KnownDevice(deviceName = deviceName, ipAddress = host.hostAddress)
                saveNewDevice(newDevice)

                this@WifiTerminalFragment.listenForTcpMessages()

            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "WiFi Connection Failed: ${e.message}", Toast.LENGTH_LONG).show()
                    disconnectTcp()
                }
            }
        }
    }

    // *** NEW: Helper function to load devices for checking duplicates ***
    private fun loadDevices(): List<KnownDevice> {
        return try {
            if (requireContext().fileList().contains(FILENAME)) {
                val fis = requireContext().openFileInput(FILENAME)
                val ois = ObjectInputStream(fis)
                val loadedList = ois.readObject() as ArrayList<KnownDevice>
                ois.close()
                loadedList
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load devices for saving check", e)
            emptyList()
        }
    }

    // *** NEW: Helper function to save the new device to the file ***
    private fun saveNewDevice(newDevice: KnownDevice) {
        val knownDevices = loadDevices().toMutableList()
        // Prevent duplicates based on IP address
        if (knownDevices.none { it.ipAddress == newDevice.ipAddress }) {
            knownDevices.add(newDevice)
            try {
                val fos = requireContext().openFileOutput(FILENAME, Context.MODE_PRIVATE)
                val oos = ObjectOutputStream(fos)
                oos.writeObject(ArrayList(knownDevices))
                oos.close()
                Log.i(TAG, "Successfully saved new device: ${newDevice.deviceName}")
                // Inform the user on the main thread
                activity?.runOnUiThread{
                    Toast.makeText(context, "Saved '${newDevice.deviceName}' to Known Devices.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save new device", e)
            }
        }
    }

    //
    // --- NO OTHER CHANGES NEEDED BELOW THIS LINE ---
    // The rest of your WifiTerminalFragment file (onResume, onPause, listeners, etc.) is correct.
    //

    override fun onResume() {
        super.onResume()
        // Bind to the service and register the broadcast receiver
        val gattServiceIntent = Intent(requireContext(), BluetoothLeService::class.java)
        requireActivity().bindService(gattServiceIntent, serviceConnection, AppCompatActivity.BIND_AUTO_CREATE)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(provisioningStatusReceiver, makeGattUpdateIntentFilter())
    }

    override fun onPause() {
        super.onPause()
        // Unregister the receiver to prevent memory leaks when the view is not visible
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(provisioningStatusReceiver)
        if (isBound) {
            requireActivity().unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onDestroy() {
        // This is called when the entire Activity is being destroyed for good.
        // This is the final cleanup point for long-lived resources like the TCP socket.
        super.onDestroy()
        disconnectTcp()
    }

    private fun appendToSerialMonitor(message: String) {
        if (isAdded) {
            val currentLog = connectionViewModel.serialLog.value ?: ""
            // The SandboxFragment's observer will now be triggered by this change.
            connectionViewModel.serialLog.value = currentLog + "$message\n"
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothLeService.LocalBinder
            bluetoothLeService = binder.getService()
            isBound = true
            if (bluetoothLeService!!.initialize()) {
                Log.i(TAG, "Bluetooth service bound and initialized.")
                bluetoothLeService?.startBleScan()
            } else {
                Log.e(TAG, "Unable to initialize Bluetooth.")
                Toast.makeText(requireContext(), "Device does not support Bluetooth", Toast.LENGTH_LONG).show()
                requireActivity().finish()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothLeService = null
            isBound = false
        }
    }

    private fun startNetworkDiscovery() {
        if (!isAdded) return
        appendToSerialMonitor("System: Starting network discovery for service: $SERVICE_TYPE")
        nsdManager = requireContext().getSystemService(Context.NSD_SERVICE) as NsdManager
        initializeResolveListener()
        initializeDiscoveryListener()
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        activity?.runOnUiThread {
            connectionViewModel.connectionStatusText.value = "Status: Searching on WiFi..." // Update ViewModel
        }
    }

    private fun stopNetworkDiscovery() {
        isResolving = false // Reset the flag
        if (this::nsdManager.isInitialized && discoveryListener != null) {
            try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (e: Exception) { Log.e(TAG, "Error stopping NSD", e) }
        }
        discoveryListener = null
        resolveListener = null
    }

    private fun initializeDiscoveryListener() {
        // This function does not need changes, it already calls other functions that use the ViewModel.
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                //Wrap UI calls in runOnUiThread ***
                activity?.runOnUiThread {
                    appendToSerialMonitor("System: NSD discovery started.")
                }
            }
            override fun onServiceFound(service: NsdServiceInfo) {
                if (isResolving) {
                    Log.d(TAG, "Ignoring duplicate service found while resolving.")
                    return
                }
                if (service.serviceType.contains(SERVICE_TYPE.trim('.'))) {
                    //Wrap UI calls in runOnUiThread ***
                    activity?.runOnUiThread {
                        appendToSerialMonitor("System: Service matches. Resolving...")
                    }
                    isResolving = true
                    nsdManager.resolveService(service, resolveListener)
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                if (service.serviceName == "easyesp-device") {
                    // disconnectTcp already handles its own threading, so this is safe.
                    disconnectTcp()
                }
            }
            override fun onDiscoveryStopped(serviceType: String) { Log.i(TAG, "Discovery stopped: $serviceType") }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                // *** FIX IS HERE: Wrap UI calls in runOnUiThread ***
                activity?.runOnUiThread {
                    appendToSerialMonitor("System: ERROR - Discovery failed. Code: $errorCode")
                }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { Log.e(TAG, "Stop Discovery failed: Error code:$errorCode") }
        }
    }

    private fun initializeResolveListener() {
        resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                isResolving = false
                // Wrap UI calls in runOnUiThread ***
                activity?.runOnUiThread {
                    appendToSerialMonitor("System: ERROR - Failed to resolve service. Code: $errorCode")
                }
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                isResolving = false
                //Wrap UI calls in runOnUiThread ***
                activity?.runOnUiThread {
                    appendToSerialMonitor("System: Service resolved! IP: ${serviceInfo.host}, Port: ${serviceInfo.port}")
                }
                // These calls are safe as they are. connectToTcpServer handles its own threading.
                connectToTcpServer(serviceInfo.host, serviceInfo.port)
                stopNetworkDiscovery()
            }
        }
    }

    // This function is now a regular "suspending" function, not one that launches its own coroutine.
// It will "suspend" the coroutine from connectToTcpServer while it waits for messages.
    private suspend fun listenForTcpMessages() {
        val currentReader = reader ?: run {
            // *** ADD THIS DEBUG LOG ***
            Log.e("DEBUG_TRACE", "[4] listenForTcpMessages: FAILED. Reader is NULL at start.")
            return
        }
        Log.d("DEBUG_TRACE", "[4] listenForTcpMessages: Starting to listen on TCP socket...")

        try {
            while (connectionViewModel.isTcpConnected.value == true) {
                val message = currentReader.readLine() // This is a blocking call, perfect for IO dispatcher
                if (message != null) {
                    Log.d("DEBUG_TRACE", "[5] listenForTcpMessages: SUCCESS. Received raw message: '$message'")
                    // Switch to the Main thread just for the moment we update the UI
                    connectionViewModel.latestTcpMessage.postValue(message)
                } else {
                    Log.w("DEBUG_TRACE", "[5] listenForTcpMessages: readLine() returned NULL. Stream has ended.")
                    // Stream ended, disconnect
                    withContext(Dispatchers.Main) { disconnectTcp() }
                    break
                }
            }
        } catch (e: Exception) {
            Log.e("DEBUG_TRACE", "[5] listenForTcpMessages: FAILED with exception during read.", e)
            // Error, disconnect
            Log.e(TAG, "Error reading from TCP socket, disconnecting.", e)
            withContext(Dispatchers.Main) { disconnectTcp() }
        }
        Log.d("DEBUG_TRACE", "[6] listenForTcpMessages: Loop has finished.")
    }

    // You also need a disconnect function that runs on the main thread
    private fun disconnectTcp() {
        isResolving = false // Reset the flag
        if (connectionViewModel.isTcpConnected.value == false) return

        // UI updates must be on the Main thread
        activity?.runOnUiThread {
            appendToSerialMonitor("System: Disconnected from WiFi device.")
            connectionViewModel.isTcpConnected.value = false
            if(connectionViewModel.connectionStatusText.value?.contains("WiFi") == true) {
                connectionViewModel.connectionStatusText.value = "Status: Disconnected"
            }
        }

        // Network operations should be on a background thread
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                tcpSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing TCP socket.", e)
            } finally {
                tcpSocket = null
                reader = null
                writer = null
                connectionViewModel.tcpWriter = null
            }
        }
        stopNetworkDiscovery()
    }

    companion object {
        private fun makeGattUpdateIntentFilter(): IntentFilter {
            val intentFilter = IntentFilter()
            intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE)
            return intentFilter
        }
    }

    private val provisioningStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothLeService.ACTION_DATA_AVAILABLE) {
                val data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA)
                Log.d(TAG, "Received provisioning status: $data")
                if (data == "STATUS:OK") {
                    // SUCCESS CASE
                    isProvisioning = false
                    connectionViewModel.wifiStatusText.value = "WiFi: Provisioning Succeeded"
                    appendToSerialMonitor("System: ESP32 ACK_STATUS:OK. Provisioning successful.")
                    Toast.makeText(requireContext(), "ESP32 on WiFi. Searching for device...", Toast.LENGTH_LONG).show()
                    startNetworkDiscovery()
                } else if (data == "STATUS:FAIL") {
                    // FAILURE CASE
                    isProvisioning = false
                    connectionViewModel.wifiStatusText.value = "WiFi: Provisioning Failed"
                    appendToSerialMonitor("System: ESP32 ACK_STATUS:FAIL. WiFi provisioning failed. Check password and try again.")
                    Toast.makeText(requireContext(), "WiFi Connection Failed!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}