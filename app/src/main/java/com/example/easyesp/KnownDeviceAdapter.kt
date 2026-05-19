package com.example.easyesp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for displaying known ESP32 devices in a RecyclerView.
 * Highlights the currently connected device.
 */
class KnownDeviceAdapter(
    private var devices: List<KnownDevice>,
    private val onItemClick: (device: KnownDevice) -> Unit,
    private val onItemLongClick: (device: KnownDevice) -> Unit
) : RecyclerView.Adapter<KnownDeviceAdapter.DeviceViewHolder>() {

    private var connectedIp: String? = null

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.known_device_name)
        val ipTextView: TextView = itemView.findViewById(R.id.known_device_ip)
        val layout: View = itemView.findViewById(R.id.known_device_layout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_known_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.nameTextView.text = device.deviceName
        holder.ipTextView.text = device.ipAddress

        val context = holder.itemView.context
        if (device.ipAddress == connectedIp) {
            holder.layout.setBackgroundColor(ContextCompat.getColor(context, R.color.yellow_highlight))
        } else {
            holder.layout.setBackgroundColor(ContextCompat.getColor(context, R.color.teal_700))
        }

        holder.itemView.setOnClickListener { onItemClick(device) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(device)
            true
        }
    }

    override fun getItemCount() = devices.size

    /**
     * Updates the data set and refreshes the UI.
     */
    fun updateDevices(newDevices: List<KnownDevice>) {
        this.devices = newDevices
        notifyDataSetChanged()
    }

    /**
     * Sets the IP of the currently connected device to update highlighting.
     */
    fun setConnectedDevice(ip: String?) {
        this.connectedIp = ip
        notifyDataSetChanged()
    }
}
