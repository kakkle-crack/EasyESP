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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.easyesp.databinding.FragmentKnownDevicesBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import android.widget.Button

/**
 * KnownDevicesFragment displays a list of previously connected ESP32 devices.
 * Migrated to ViewBinding and StateFlow.
 */
class KnownDevicesFragment : Fragment() {

    private val TAG = "KnownDevicesFragment"
    private val FILENAME = "known_devices.dat"

    private var _binding: FragmentKnownDevicesBinding? = null
    private val binding get() = _binding!!

    private val connectionViewModel: ConnectionViewModel by activityViewModels()
    private lateinit var devicesAdapter: KnownDeviceAdapter
    private val devicesList = mutableListOf<KnownDevice>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKnownDevicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadDevices()

        binding.fabAddKnownDevice.setOnClickListener {
            showAddDeviceDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                connectionViewModel.connectedDeviceIp.collectLatest { connectedIp ->
                    devicesAdapter.setConnectedDevice(connectedIp)
                }
            }
        }
    }

    private fun setupRecyclerView() {
        devicesAdapter = KnownDeviceAdapter(
            devicesList,
            onItemClick = { device ->
                if (connectionViewModel.isTcpConnected.value) {
                    Toast.makeText(requireContext(), "Already connected to a device.", Toast.LENGTH_SHORT).show()
                    return@KnownDeviceAdapter
                }
                Toast.makeText(requireContext(), "Connecting to ${device.deviceName}...", Toast.LENGTH_SHORT).show()
                val action = KnownDevicesFragmentDirections.actionKnownDevicesFragmentToWifiTerminalFragment(device.ipAddress)
                findNavController().navigate(action)
            },
            onItemLongClick = { device ->
                showDeviceOptionsDialog(device)
            }
        )
        binding.recyclerViewKnownDevices.adapter = devicesAdapter
        binding.recyclerViewKnownDevices.layoutManager = LinearLayoutManager(requireContext())
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
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_device_options, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(device.deviceName)
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .create()

        val editButton: Button = dialogView.findViewById(R.id.dialog_button_edit)
        val deleteButton: Button = dialogView.findViewById(R.id.dialog_button_delete)
        val disconnectButton: Button = dialogView.findViewById(R.id.dialog_button_disconnect)

        editButton.setOnClickListener {
            showEditDeviceDialog(device)
            dialog.dismiss()
        }

        deleteButton.setOnClickListener {
            showDeleteDeviceDialog(device)
            dialog.dismiss()
        }

        val isConnectedToThisDevice = connectionViewModel.connectedDeviceIp.value == device.ipAddress
        if (isConnectedToThisDevice) {
            disconnectButton.visibility = View.VISIBLE
            disconnectButton.setOnClickListener {
                connectionViewModel.disconnect()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showDeleteDeviceDialog(device: KnownDevice) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Device")
            .setMessage("Are you sure you want to delete '${device.deviceName}'?")
            .setPositiveButton("Delete") { _, _ ->
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

        nameInput.setText(device.deviceName)
        ipInput.setText(device.ipAddress)

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setTitle("Edit Device")
            .setPositiveButton("Save") { _, _ ->
                device.deviceName = nameInput.text.toString()
                device.ipAddress = ipInput.text.toString()
                devicesAdapter.notifyDataSetChanged()
                saveDevices()
                Toast.makeText(requireContext(), "Device updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun saveDevices() {
        try {
            val fos = requireContext().openFileOutput(FILENAME, Context.MODE_PRIVATE)
            val oos = ObjectOutputStream(fos)
            oos.writeObject(ArrayList(devicesList))
            oos.close()
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load devices", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
