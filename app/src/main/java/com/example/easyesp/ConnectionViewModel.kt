package com.example.easyesp

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.net.Socket

// This class will hold the state of connection and logs
class ConnectionViewModel : ViewModel() {

    // --- EXISTING PROPERTIES ---
    val isTcpConnected = MutableLiveData<Boolean>(false)
    val connectionStatusText = MutableLiveData<String>("Status: Disconnected")
    val wifiStatusText = MutableLiveData<String>("Network Details")
    val serialLog = MutableLiveData<String>("")
    val isBleConnected = MutableLiveData<Boolean>(false)
    val latestTcpMessage = MutableLiveData<String>()
    var tcpWriter: PrintWriter? = null

    // --- NEW PROPERTIES FOR OUR FEATURES ---

    /** Holds the IP address of the currently connected device. Null if disconnected. */
    val connectedDeviceIp = MutableLiveData<String?>(null)

    /** The actual TCP socket. Storing it here allows us to disconnect from anywhere. */
    var tcpClient: Socket? = null

    // --- EXISTING FUNCTION ---
    fun sendTcpCommand(command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (tcpWriter == null) {
                    Log.e("ConnectionViewModel", "Cannot send command, tcpWriter is null.")
                    return@launch
                }
                tcpWriter?.println(command)
                tcpWriter?.flush()
            } catch (e: Exception) {
                Log.e("ConnectionViewModel", "Failed to send TCP command", e)
            }
        }
    }

    // --- FUNCTION TO ALLOW DISCONNECTING ---
    /** Disconnects from the current TCP socket if one is active. */
    fun disconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Close the socket and writer that are now stored in the ViewModel
                tcpClient?.close()
                tcpWriter?.close()
            } catch (e: Exception) {
                Log.e("ConnectionViewModel", "Error while closing socket", e)
            } finally {
                // Reset all state variables on the main thread
                withContext(Dispatchers.Main) {
                    tcpClient = null
                    tcpWriter = null
                    isTcpConnected.value = false
                    connectedDeviceIp.value = null // This is key for the UI update
                    connectionStatusText.value = "Status: Disconnected"
                    serialLog.value = (serialLog.value ?: "") + "\n\n[Disconnected by user]\n"
                }
            }
        }
    }
}