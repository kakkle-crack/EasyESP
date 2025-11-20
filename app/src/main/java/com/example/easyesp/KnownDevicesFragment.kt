package com.example.easyesp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import android.widget.Button

class KnownDevicesFragment : Fragment() {

    private val TAG = "KnownDevicesFragment"

    // --- ViewModel & Data ---
    private val connectionViewModel: ConnectionViewModel by activityViewModels()
    private lateinit var devicesAdapter: KnownDeviceAdapter
    private val devicesList = mutableListOf<KnownDevice>()
    private val FILENAME = "known_devices.dat"

    // --- UI Elements ---
    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_known_devices, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recycler_view_known_devices)
        fab = view.findViewById(R.id.fab_add_known_device)

        setupRecyclerView()
        loadDevices()

        fab.setOnClickListener {
            showAddDeviceDialog()
        }

        //watch for future changes
        connectionViewModel.connectedDeviceIp.observe(viewLifecycleOwner) { connectedIp ->
            devicesAdapter.setConnectedDevice(connectedIp)
        }

        //manually sync adapter with current state of viewmodel
        devicesAdapter.setConnectedDevice(connectionViewModel.connectedDeviceIp.value)
    }

    private fun setupRecyclerView() {
        devicesAdapter = KnownDeviceAdapter(
            devicesList,
            // onItemClick: Handle connection
            onItemClick = { device ->
                if (connectionViewModel.isTcpConnected.value == true) {
                    Toast.makeText(requireContext(), "Already connected to a device.", Toast.LENGTH_SHORT).show()
                    return@KnownDeviceAdapter
                }
                Toast.makeText(requireContext(), "Connecting to ${device.deviceName}...", Toast.LENGTH_SHORT).show()
                // The connectToTcpServer function needs to be public in WifiTerminalFragment or moved to ViewModel
                // For now, let's navigate to the WiFi fragment and let it handle the connection.
                // We'll pass the IP address as an argument.
                val action = KnownDevicesFragmentDirections.actionKnownDevicesFragmentToWifiTerminalFragment(device.ipAddress)
                findNavController().navigate(action)
            },
            // onItemLongClick: Handle deletion
            onItemLongClick = { device ->
                showDeviceOptionsDialog(device)
            }
        )
        recyclerView.adapter = devicesAdapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun showAddDeviceDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_device, null)
        val nameInput: EditText = dialogView.findViewById(R.id.dialog_device_name)
        val ipInput: EditText = dialogView.findViewById(R.id.dialog_device_ip)

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setTitle("Add New Device")
            .setPositiveButton("Add") { dialog, _ ->
                val name = nameInput.text.toString()
                val ip = ipInput.text.toString()

                if (name.isBlank() || ip.isBlank()) {
                    Toast.makeText(requireContext(), "Name and IP Address cannot be empty.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newDevice = KnownDevice(deviceName = name, ipAddress = ip)
                devicesList.add(newDevice)
                devicesAdapter.updateDevices(devicesList)
                saveDevices()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .create()
            .show()
    }

    private fun showDeviceOptionsDialog(device: KnownDevice) {
        // Inflate the custom layout
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_device_options, null)

        // Create the AlertDialog
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(device.deviceName)
            .setView(dialogView)
            .setNegativeButton("Cancel", null) // Keep a standard cancel button
            .create()

        // Get references to our custom buttons
        val editButton: Button = dialogView.findViewById(R.id.dialog_button_edit)
        val deleteButton: Button = dialogView.findViewById(R.id.dialog_button_delete)
        val disconnectButton: Button = dialogView.findViewById(R.id.dialog_button_disconnect)

        // --- RETAIN FUNCTIONALITY ---

        // Edit Button Click Listener
        editButton.setOnClickListener {
            showEditDeviceDialog(device)
            dialog.dismiss() // Close the options dialog
        }

        // Delete Button Click Listener
        deleteButton.setOnClickListener {
            showDeleteDeviceDialog(device)
            dialog.dismiss() // Close the options dialog
        }

        // --- DYNAMICALLY SHOW/HIDE AND WIRE UP DISCONNECT BUTTON ---
        val isConnectedToThisDevice = connectionViewModel.connectedDeviceIp.value == device.ipAddress
        if (isConnectedToThisDevice) {
            disconnectButton.visibility = View.VISIBLE // Make the button visible
            disconnectButton.setOnClickListener {
                connectionViewModel.disconnect()
                dialog.dismiss() // Close the options dialog
            }
        }
        // Finally, show the dialog
        dialog.show()
    }

    private fun showDeleteDeviceDialog(device: KnownDevice) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Device")
            .setMessage("Are you sure you want to delete '${device.deviceName}'?")
            .setPositiveButton("Delete") { _, _ ->
                // *** NEW: Check if we are deleting the connected device ***
                if (connectionViewModel.connectedDeviceIp.value == device.ipAddress) {
                    connectionViewModel.disconnect()
                }

                devicesList.remove(device)
                devicesAdapter.updateDevices(devicesList)
                saveDevices()
                Toast.makeText(requireContext(), "'${device.deviceName}' deleted.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDeviceDialog(device: KnownDevice) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_device, null)
        val nameInput: EditText = dialogView.findViewById(R.id.dialog_device_name)
        val ipInput: EditText = dialogView.findViewById(R.id.dialog_device_ip)

        // Pre-fill the dialog with existing data
        nameInput.setText(device.deviceName)
        ipInput.setText(device.ipAddress)

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setTitle("Edit Device")
            .setPositiveButton("Save") { _, _ ->
                val newName = nameInput.text.toString()
                val newIp = ipInput.text.toString()

                // Find the original device and update its properties
                device.deviceName = newName
                device.ipAddress = newIp

                devicesAdapter.notifyDataSetChanged() // A simple way to refresh the view
                saveDevices()
                Toast.makeText(requireContext(), "Device updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    // --- Data Persistence ---
    private fun saveDevices() {
        try {
            val fos = requireContext().openFileOutput(FILENAME, Context.MODE_PRIVATE)
            val oos = ObjectOutputStream(fos)
            oos.writeObject(ArrayList(devicesList)) // Write a serializable copy
            oos.close()
            Log.i(TAG, "Successfully saved ${devicesList.size} devices.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save devices", e)
        }
    }

    private fun loadDevices() {
        try {
            if (requireContext().fileList().contains(FILENAME)) {
                val fis = requireContext().openFileInput(FILENAME)
                val ois = ObjectInputStream(fis)
                val loadedList = ois.readObject() as ArrayList<KnownDevice>
                devicesList.clear()
                devicesList.addAll(loadedList)
                ois.close()
                devicesAdapter.updateDevices(devicesList)
                Log.i(TAG, "Successfully loaded ${devicesList.size} devices.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load devices", e)
        }
    }
}