package com.example.easyesp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class KnownDeviceAdapter(
    private var devices: List<KnownDevice>,
    private val onItemClick: (device: KnownDevice) -> Unit,
    private val onItemLongClick: (device: KnownDevice) -> Unit
) : RecyclerView.Adapter<KnownDeviceAdapter.DeviceViewHolder>() {

    /**
     * ViewHolder holds the references to the UI views for each item in the list.
     */
    private var connectedIp: String? = null //holds IP adddress for known devices fragment

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.known_device_name)
        val ipTextView: TextView = itemView.findViewById(R.id.known_device_ip)
    }

    /**
     * Called when RecyclerView needs a new ViewHolder. It inflates the item layout XML.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_known_device, parent, false)
        return DeviceViewHolder(view)
    }

    /**
     * Called by RecyclerView to display the data at the specified position.
     * This method updates the contents of the ViewHolder's views to reflect the item.
     */
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.nameTextView.text = device.deviceName
        holder.ipTextView.text = device.ipAddress

        //VISUAL INDICATOR LOGIC
        val layout = holder.itemView.findViewById<View>(R.id.known_device_layout)

        if (device.ipAddress == connectedIp) {
            // Set background to yellow highlight. had to add yellow_highlight in colors.xml
            layout.setBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, R.color.yellow_highlight)
            )
        } else {
            // Set background back to the default teal color.
            layout.setBackgroundColor(
                ContextCompat.getColor(holder.itemView.context, R.color.teal_700)
            )
        }
        //END VISUAL INDICATOR
        // Set the click listener for the whole item view
        holder.itemView.setOnClickListener {
            onItemClick(device)
        }

        // Set the long click listener for the whole item view
        holder.itemView.setOnLongClickListener {
            onItemLongClick(device)
            true // Return true to indicate the long click was handled
        }
    }

    /**
     * Returns the total number of items in the list.
     */
    override fun getItemCount() = devices.size

    /**
     * A helper function to update the list of devices in the adapter from the fragment.
     */
    fun updateDevices(newDevices: List<KnownDevice>) {
        this.devices = newDevices
        notifyDataSetChanged() // Redraw the entire list. For more efficiency, DiffUtil could be used.
    }

    fun setConnectedDevice(ip: String?) {
        this.connectedIp = ip
        notifyDataSetChanged() // Redraw the entire list to apply the new highlight.
    }
}