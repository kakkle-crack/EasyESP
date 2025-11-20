package com.example.easyesp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial

class BluetoothSettingsFragment : Fragment() {

    private lateinit var deviceNameEditText: EditText
    private lateinit var discoverDevicesSwitch: SwitchMaterial
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        // These keys are public so the Service can read them
        const val PREFS_NAME = "BluetoothSettingsPrefs"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_DISCOVERY_ENABLED = "discovery_enabled"
        const val DEFAULT_DEVICE_NAME = "MyESP32"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_bluetooth_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize SharedPreferences
        sharedPreferences = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Find the views
        deviceNameEditText = view.findViewById(R.id.edit_text_device_name)
        discoverDevicesSwitch = view.findViewById(R.id.switch_discover_devices)

        // Load saved settings and populate the UI
        loadSettings()

        // Set up listeners to save changes when the user interacts with the UI
        setupListeners()
    }

    private fun loadSettings() {
        // Load the saved device name, or use the default if it's not set
        val savedName = sharedPreferences.getString(KEY_DEVICE_NAME, DEFAULT_DEVICE_NAME)
        deviceNameEditText.setText(savedName)

        // Load the saved state of the switch
        val isDiscoveryEnabled = sharedPreferences.getBoolean(KEY_DISCOVERY_ENABLED, false)
        discoverDevicesSwitch.isChecked = isDiscoveryEnabled
    }

    private fun setupListeners() {
        // Save the device name whenever the user finishes editing it
        deviceNameEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveDeviceName()
            }
            discoverDevicesSwitch.setOnCheckedChangeListener { _, isChecked ->
                // First, save the state as before
                saveDiscoveryState(isChecked)

                // *** THE FIX IS HERE ***
                // Now, create an explicit Intent to command the service.
                val intent = Intent(requireContext(), BluetoothLeService::class.java).apply {
                    // Set the action we want the service to perform.
                    action = BluetoothLeService.ACTION_MANAGE_SCAN_STATE
                }
                // Start the service with this command. If the service is running,
                // it will receive this in onStartCommand.
                requireContext().startService(intent)
            }
        }

        // Save the switch state whenever the user toggles it
        discoverDevicesSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveDiscoveryState(isChecked)
        }
    }

    private fun saveDeviceName() {
        val editor = sharedPreferences.edit()
        editor.putString(KEY_DEVICE_NAME, deviceNameEditText.text.toString())
        editor.apply() // apply() saves the data in the background
    }

    private fun saveDiscoveryState(isEnabled: Boolean) {
        val editor = sharedPreferences.edit()
        editor.putBoolean(KEY_DISCOVERY_ENABLED, isEnabled)
        editor.apply()

        // Here we can also add logic to start/stop the BLE service scan if needed,
        // but for now, the service will just read this value when it's told to scan.
    }

    override fun onPause() {
        super.onPause()
        // Make sure the latest device name is saved if the user leaves the screen
        saveDeviceName()
    }
}