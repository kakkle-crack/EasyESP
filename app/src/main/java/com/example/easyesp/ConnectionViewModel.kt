package com.example.easyesp

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.net.Socket

/**
 * ViewModel responsible for managing connection states and communication logs.
 * Migrated to StateFlow/SharedFlow for better coroutine support and modern architecture.
 */
class ConnectionViewModel : ViewModel() {

    private val _isTcpConnected = MutableStateFlow(false)
    val isTcpConnected = _isTcpConnected.asStateFlow()

    private val _connectionStatusText = MutableStateFlow("Status: Disconnected")
    val connectionStatusText = _connectionStatusText.asStateFlow()

    private val _wifiStatusText = MutableStateFlow("Network Details")
    val wifiStatusText = _wifiStatusText.asStateFlow()

    private val _serialLog = MutableStateFlow("")
    val serialLog = _serialLog.asStateFlow()

    private val _isBleConnected = MutableStateFlow(false)
    val isBleConnected = _isBleConnected.asStateFlow()

    // Using SharedFlow for messages so that identical consecutive messages are still collected.
    private val _tcpMessageEvent = MutableSharedFlow<String>(replay = 0)
    val tcpMessageEvent = _tcpMessageEvent.asSharedFlow()

    var tcpWriter: PrintWriter? = null

    private val _connectedDeviceIp = MutableStateFlow<String?>(null)
    val connectedDeviceIp = _connectedDeviceIp.asStateFlow()

    var tcpClient: Socket? = null

    /**
     * Updates the connection status text.
     */
    fun updateConnectionStatus(status: String) {
        _connectionStatusText.value = status
    }

    /**
     * Updates the WiFi status text.
     */
    fun updateWifiStatus(status: String) {
        _wifiStatusText.value = status
    }

    /**
     * Appends a message to the serial log.
     */
    fun appendToLog(message: String) {
        _serialLog.value += message + "\n"
    }

    /**
     * Sets the BLE connection state.
     */
    fun setBleConnected(connected: Boolean) {
        _isBleConnected.value = connected
    }

    /**
     * Emits a new TCP message event.
     */
    fun emitTcpMessage(message: String) {
        viewModelScope.launch {
            _tcpMessageEvent.emit(message)
        }
    }

    /**
     * Sends a command over the TCP connection.
     */
    fun sendTcpCommand(command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                tcpWriter?.let { writer ->
                    writer.println(command)
                    writer.flush()
                } ?: Log.e("ConnectionViewModel", "Cannot send command, tcpWriter is null.")
            } catch (e: Exception) {
                Log.e("ConnectionViewModel", "Failed to send TCP command", e)
            }
        }
    }

    /**
     * Disconnects from the current TCP socket and resets connection state.
     */
    fun disconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                tcpClient?.close()
                tcpWriter?.close()
            } catch (e: Exception) {
                Log.e("ConnectionViewModel", "Error while closing socket", e)
            } finally {
                withContext(Dispatchers.Main) {
                    tcpClient = null
                    tcpWriter = null
                    _isTcpConnected.value = false
                    _connectedDeviceIp.value = null
                    _connectionStatusText.value = "Status: Disconnected"
                    _serialLog.value += "\n\n[Disconnected by user]\n"
                }
            }
        }
    }

    /**
     * Updates the connected device IP and sets TCP connection state to true.
     */
    fun setTcpConnected(ip: String) {
        _isTcpConnected.value = true
        _connectedDeviceIp.value = ip
    }
}
