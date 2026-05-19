package com.example.easyesp

import android.content.*
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.navArgs
import com.example.easyesp.databinding.FragmentWifiTerminalBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.net.InetAddress
import java.net.Socket

/**
 * WifiTerminalFragment handles the WiFi provisioning process and TCP communication.
 * Migrated to ViewBinding and StateFlow.
 */
class WifiTerminalFragment : Fragment() {

    private val TAG = "WifiTerminalFragment"
    private val SERVICE_TYPE = "_easyesp._tcp."
    private val FILENAME = "known_devices.dat"

    private var _binding: FragmentWifiTerminalBinding? = null
    private val binding get() = _binding!!

    private var bluetoothLeService: BluetoothLeService? = null
    private var isBound = false
    private var tcpSocket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private lateinit var nsdManager: NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListener: NsdManager.ResolveListener? = null
    private var isResolving = false

    private val connectionViewModel: ConnectionViewModel by activityViewModels()
    private val args: WifiTerminalFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWifiTerminalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupWifiInfo()
        setupClickListeners()
        observeViewModel()
        handleArguments()
    }

    private fun setupWifiInfo() {
        val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        if (wifiInfo != null && wifiInfo.networkId != -1) {
            var currentSsid = wifiInfo.ssid
            if (currentSsid.startsWith("\"") && currentSsid.endsWith("\"")) {
                currentSsid = currentSsid.substring(1, currentSsid.length - 1)
            }
            binding.ssidText1.setText(currentSsid)
        }
    }

    private fun setupClickListeners() {
        binding.connectButton.setOnClickListener {
            val ssid = binding.ssidText1.text.toString()
            val pass = binding.passText2.text.toString()

            if (ssid.isNotEmpty() && ssid != "SSID") {
                connectionViewModel.updateWifiStatus("WiFi: Provisioning...")
                val wifiData = "WIFI:$ssid,$pass"
                bluetoothLeService?.writeCharacteristic(wifiData)
                connectionViewModel.appendToLog("App -> ESP32 (BLE): Sent WiFi credentials")
            } else {
                Toast.makeText(requireContext(), "SSID cannot be empty.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    connectionViewModel.connectionStatusText.collectLatest { status ->
                        binding.connectionStatusText.text = status
                    }
                }
                launch {
                    connectionViewModel.serialLog.collectLatest { log ->
                        binding.serialMonitorTextView.text = log
                        binding.serialMonitorScrollView.post { 
                            binding.serialMonitorScrollView.fullScroll(View.FOCUS_DOWN) 
                        }
                    }
                }
                launch {
                    connectionViewModel.isBleConnected.collectLatest { isConnected ->
                        if (connectionViewModel.isTcpConnected.value) return@collectLatest
                        if (isConnected) {
                            connectionViewModel.updateConnectionStatus("Status: Connected (BLE)")
                            connectionViewModel.appendToLog("System: Bluetooth Connected")
                        } else {
                            if (connectionViewModel.connectionStatusText.value.contains("BLE")) {
                                connectionViewModel.updateConnectionStatus("Status: Disconnected")
                                connectionViewModel.appendToLog("System: Bluetooth Disconnected")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleArguments() {
        args.ipAddress?.let { ip ->
            if (!connectionViewModel.isTcpConnected.value) {
                Log.d(TAG, "Received IP to auto-connect: $ip")
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val inetAddr = InetAddress.getByName(ip)
                        connectToTcpServer(inetAddr, 8888)
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
        if (connectionViewModel.isTcpConnected.value) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                tcpSocket = Socket(host, port)
                connectionViewModel.tcpClient = tcpSocket
                writer = PrintWriter(tcpSocket!!.getOutputStream(), true)
                reader = BufferedReader(InputStreamReader(tcpSocket!!.getInputStream()))
                connectionViewModel.tcpWriter = writer

                withContext(Dispatchers.Main) {
                    connectionViewModel.setTcpConnected(host.hostAddress)
                    connectionViewModel.updateConnectionStatus("Status: Connected (WiFi)")
                    connectionViewModel.updateWifiStatus("Host: ${host.hostAddress}")
                    connectionViewModel.appendToLog("System: TCP socket connected to ESP32 at ${host.hostAddress}")
                    Toast.makeText(requireContext(), "Connected to ESP32 via WiFi!", Toast.LENGTH_SHORT).show()
                }

                val deviceName = "ESP32-${host.hostAddress.takeLast(3)}"
                saveNewDevice(KnownDevice(deviceName = deviceName, ipAddress = host.hostAddress))

                listenForTcpMessages()

            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "WiFi Connection Failed: ${e.message}", Toast.LENGTH_LONG).show()
                    disconnectTcp()
                }
            }
        }
    }

    private fun saveNewDevice(newDevice: KnownDevice) {
        val knownDevices = loadDevices().toMutableList()
        if (knownDevices.none { it.ipAddress == newDevice.ipAddress }) {
            knownDevices.add(newDevice)
            try {
                val fos = requireContext().openFileOutput(FILENAME, Context.MODE_PRIVATE)
                val oos = ObjectOutputStream(fos)
                oos.writeObject(ArrayList(knownDevices))
                oos.close()
                activity?.runOnUiThread {
                    Toast.makeText(context, "Saved '${newDevice.deviceName}' to Known Devices.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save new device", e)
            }
        }
    }

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
            Log.e(TAG, "Failed to load devices", e)
            emptyList()
        }
    }

    private suspend fun listenForTcpMessages() {
        val currentReader = reader ?: return
        try {
            while (connectionViewModel.isTcpConnected.value) {
                val message = currentReader.readLine()
                if (message != null) {
                    connectionViewModel.emitTcpMessage(message)
                } else {
                    withContext(Dispatchers.Main) { disconnectTcp() }
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from TCP socket, disconnecting.", e)
            withContext(Dispatchers.Main) { disconnectTcp() }
        }
    }

    private fun disconnectTcp() {
        isResolving = false
        if (!connectionViewModel.isTcpConnected.value) return

        activity?.runOnUiThread {
            connectionViewModel.appendToLog("System: Disconnected from WiFi device.")
            if (connectionViewModel.connectionStatusText.value.contains("WiFi")) {
                connectionViewModel.updateConnectionStatus("Status: Disconnected")
            }
        }

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

    private fun startNetworkDiscovery() {
        if (!isAdded) return
        connectionViewModel.appendToLog("System: Starting network discovery for service: $SERVICE_TYPE")
        nsdManager = requireContext().getSystemService(Context.NSD_SERVICE) as NsdManager
        initializeResolveListener()
        initializeDiscoveryListener()
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        activity?.runOnUiThread {
            connectionViewModel.updateConnectionStatus("Status: Searching on WiFi...")
        }
    }

    private fun stopNetworkDiscovery() {
        isResolving = false
        if (this::nsdManager.isInitialized && discoveryListener != null) {
            try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (e: Exception) { Log.e(TAG, "Error stopping NSD", e) }
        }
        discoveryListener = null
        resolveListener = null
    }

    private fun initializeDiscoveryListener() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                activity?.runOnUiThread { connectionViewModel.appendToLog("System: NSD discovery started.") }
            }
            override fun onServiceFound(service: NsdServiceInfo) {
                if (isResolving) return
                if (service.serviceType.contains(SERVICE_TYPE.trim('.'))) {
                    activity?.runOnUiThread { connectionViewModel.appendToLog("System: Service matches. Resolving...") }
                    isResolving = true
                    nsdManager.resolveService(service, resolveListener)
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                if (service.serviceName == "easyesp-device") { disconnectTcp() }
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                activity?.runOnUiThread { connectionViewModel.appendToLog("System: ERROR - Discovery failed. Code: $errorCode") }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
    }

    private fun initializeResolveListener() {
        resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                isResolving = false
                activity?.runOnUiThread { connectionViewModel.appendToLog("System: ERROR - Failed to resolve service. Code: $errorCode") }
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                isResolving = false
                activity?.runOnUiThread { connectionViewModel.appendToLog("System: Service resolved! IP: ${serviceInfo.host}, Port: ${serviceInfo.port}") }
                connectToTcpServer(serviceInfo.host, serviceInfo.port)
                stopNetworkDiscovery()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val gattServiceIntent = Intent(requireContext(), BluetoothLeService::class.java)
        requireActivity().bindService(gattServiceIntent, serviceConnection, AppCompatActivity.BIND_AUTO_CREATE)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(provisioningStatusReceiver, IntentFilter(BluetoothLeService.ACTION_DATA_AVAILABLE))
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(provisioningStatusReceiver)
        if (isBound) {
            requireActivity().unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectTcp()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothLeService.LocalBinder
            bluetoothLeService = binder.getService()
            isBound = true
            if (bluetoothLeService!!.initialize()) {
                bluetoothLeService?.startBleScan()
            } else {
                Toast.makeText(requireContext(), "Device does not support Bluetooth", Toast.LENGTH_LONG).show()
                requireActivity().finish()
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothLeService = null
            isBound = false
        }
    }

    private val provisioningStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothLeService.ACTION_DATA_AVAILABLE) {
                val data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA)
                if (data == "STATUS:OK") {
                    connectionViewModel.updateWifiStatus("WiFi: Provisioning Succeeded")
                    connectionViewModel.appendToLog("System: ESP32 ACK_STATUS:OK. Provisioning successful.")
                    Toast.makeText(requireContext(), "ESP32 on WiFi. Searching for device...", Toast.LENGTH_LONG).show()
                    startNetworkDiscovery()
                } else if (data == "STATUS:FAIL") {
                    connectionViewModel.updateWifiStatus("WiFi: Provisioning Failed")
                    connectionViewModel.appendToLog("System: ESP32 ACK_STATUS:FAIL. WiFi provisioning failed. Check password and try again.")
                    Toast.makeText(requireContext(), "WiFi Connection Failed!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
