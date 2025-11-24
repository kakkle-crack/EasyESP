package com.example.easyesp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
//import androidx.glance.visibility
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputLayout
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class SandboxFragment : Fragment() {

    private val TAG = "SandboxFragment"

    // --- ViewModel & Data ---
    private val connectionViewModel: ConnectionViewModel by activityViewModels()
    private lateinit var controlsAdapter: SandboxControlsAdapter
    private val controlsList = mutableListOf<SandboxControl>()
    private val FILENAME = "sandbox_controls.dat"

    // --- UI Elements ---
    private lateinit var recyclerView: RecyclerView
    private lateinit var addControlButton: Button
    private lateinit var monitorTextView: TextView
    private lateinit var monitorScrollView: ScrollView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_sandbox, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- UI Initialization ---
        addControlButton = view.findViewById(R.id.button_add_control)
        recyclerView = view.findViewById(R.id.recycler_view_controls)
        monitorTextView = view.findViewById(R.id.monitor_textView)
        monitorScrollView = view.findViewById(R.id.monitor_scrollview)

        // --- Setup ---
        setupRecyclerView()
        loadControls() // Load saved controls from device storage

        addControlButton.setOnClickListener {
            showAddControlDialog()
        }

        // --- Observers ---
        // Observe the main serial log from the ViewModel and append it here
        connectionViewModel.serialLog.observe(viewLifecycleOwner, Observer { log ->
            monitorTextView.text = log
            // Scroll to the bottom whenever new text is added
            monitorScrollView.post { monitorScrollView.fullScroll(View.FOCUS_DOWN) }
        })
        // This observer listens for ACKs from our controls.
        connectionViewModel.latestTcpMessage.observe(viewLifecycleOwner, Observer { message ->
            // We can add a check if we want to be specific, e.g., if(message.startsWith("ACK:"))
            if (message != null) {
                // Check if the message is a custom log from the ESP32
                if (message.startsWith("LOG:")) {
                    // It's a log message
                    val logMessage = message.substringAfter("LOG:").trim()
                    // Prepend with "ESP32:" to show it came FROM the device
                    appendToSandboxMonitor("ESP32: $logMessage")

                } else if (message.startsWith("ACK:")) {
                    // It's a standard acknowledgement
                    val ackMessage = message.substringAfter("ACK:").trim()
                    appendToSandboxMonitor("ACK from ESP32: $ackMessage")

                } else {
                    // For any other message that doesn't fit a known format
                    appendToSandboxMonitor("ESP32 (RAW): $message")
                }
            }
        })
    }

    private fun setupRecyclerView() {
        // The adapter is initialized with an interaction listener.
        // When a control is used in the adapter, it calls this lambda function.
        controlsAdapter = SandboxControlsAdapter(
            controlsList,
            // The first lambda for sending commands (unchanged)
            { control, command ->
                Log.d("DEBUG_TRACE", "[1] SandboxFragment: Interaction received. Command: '$command'")
                Log.d(TAG, "Interaction callback: Sending command '$command'")
                if (connectionViewModel.isTcpConnected.value == true) {
                    //ADD TO LOCAL MONITOR
                    appendToSandboxMonitor("App -> ESP32: $command")
                    connectionViewModel.sendTcpCommand(command)
                } else {
                    Toast.makeText(requireContext(), "Not connected via WiFi", Toast.LENGTH_SHORT).show()
                }
            },
            { controlToDelete ->
                showDeleteConfirmationDialog(controlToDelete)
            }
        )
        recyclerView.adapter = controlsAdapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun appendToSandboxMonitor(message: String) {
        // Append to the existing text in the TextView
        monitorTextView.append("\n$message")
        // Ensure it scrolls down
        monitorScrollView.post { monitorScrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun showDeleteConfirmationDialog(control: SandboxControl) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Control")
            .setMessage("Are you sure you want to delete '${control.name}'?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Delete") { _, _ ->
                controlsList.remove(control)
                controlsAdapter.updateControls(controlsList)
                saveControls()
                Toast.makeText(requireContext(), "'${control.name}' deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddControlDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_control, null)

        // --- Get references to all UI elements ---
        val nameInput: EditText = dialogView.findViewById(R.id.dialog_control_name)
        val typeSpinner: Spinner = dialogView.findViewById(R.id.dialog_control_type_spinner)

        val pinInput: EditText = dialogView.findViewById(R.id.dialog_control_pin)
        // *** FIX 1: Correctly get the parent layout for the pin input ***
        val pinLayout = pinInput.parent.parent as TextInputLayout

        val valueLayout: TextInputLayout = dialogView.findViewById(R.id.dialog_value_layout)
        val valueInput: EditText = dialogView.findViewById(R.id.dialog_control_value)

        val commandLayout: TextInputLayout = dialogView.findViewById(R.id.dialog_command_layout)
        val commandInput: EditText = dialogView.findViewById(R.id.dialog_control_command)

        // Setup the dropdown (Spinner)
        val controlTypes = ControlType.values().map { it.name }
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, controlTypes)
        typeSpinner.adapter = spinnerAdapter

        // --- Corrected logic for showing/hiding fields ---
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (ControlType.valueOf(controlTypes[position])) {
                    ControlType.BUTTON -> {
                        pinLayout.visibility = View.VISIBLE
                        valueLayout.visibility = View.VISIBLE
                        commandLayout.visibility = View.GONE
                        valueLayout.hint = "Duration (ms)"
                    }
                    ControlType.SLIDER -> {
                        pinLayout.visibility = View.VISIBLE
                        valueLayout.visibility = View.VISIBLE
                        commandLayout.visibility = View.GONE
                        valueLayout.hint = "Initial Value (0-255)"
                    }
                    ControlType.SWITCH -> {
                        pinLayout.visibility = View.VISIBLE
                        valueLayout.visibility = View.GONE
                        commandLayout.visibility = View.GONE
                    }
                    ControlType.INTERACTION -> {
                        pinLayout.visibility = View.GONE
                        valueLayout.visibility = View.GONE
                        commandLayout.visibility = View.VISIBLE
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Build and show the dialog
        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            // Set Title on the builder itself, not in the XML layout
            .setTitle("Add New Control")
            .setPositiveButton("Add") { dialog, _ ->
                val name = nameInput.text.toString()
                val type = ControlType.valueOf(typeSpinner.selectedItem.toString())

                if (name.isBlank()) {
                    Toast.makeText(requireContext(), "Name cannot be empty.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newControl: SandboxControl? = when (type) {
                    ControlType.BUTTON, ControlType.SLIDER, ControlType.SWITCH -> {
                        val pin = pinInput.text.toString().toIntOrNull()
                        if (pin == null) {
                            Toast.makeText(requireContext(), "Pin cannot be empty for this control type.", Toast.LENGTH_SHORT).show()
                            null
                        } else {
                            val value = when (type) {
                                ControlType.BUTTON -> valueInput.text.toString().toIntOrNull() ?: 250
                                ControlType.SLIDER -> valueInput.text.toString().toIntOrNull() ?: 0
                                ControlType.SWITCH -> 0
                                else -> 0
                            }
                            // Create control with pin and value
                            SandboxControl(name = name, type = type, pin = pin, value = value)
                        }
                    }
                    ControlType.INTERACTION -> {
                        val command = commandInput.text.toString()
                        if (command.isBlank()) {
                            Toast.makeText(requireContext(), "Command cannot be empty for Interaction.", Toast.LENGTH_SHORT).show()
                            null
                        } else {
                            // Create control with a command, pin and value will be null by default
                            SandboxControl(name = name, type = type, command = command)
                        }
                    }
                }

                if (newControl != null) {
                    controlsList.add(newControl)
                    controlsAdapter.updateControls(controlsList)
                    saveControls()
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .create()
            .show()
    }

    // --- Data Persistence ---
    private fun saveControls() {
        try {
            val fos = requireContext().openFileOutput(FILENAME, Context.MODE_PRIVATE)
            val oos = ObjectOutputStream(fos)
            oos.writeObject(ArrayList(controlsList)) // Write a copy
            oos.close()
            Log.i(TAG, "Successfully saved ${controlsList.size} controls.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save controls", e)
        }
    }

    private fun loadControls() {
        try {
            if (requireContext().fileList().contains(FILENAME)) {
                val fis = requireContext().openFileInput(FILENAME)
                val ois = ObjectInputStream(fis)
                val loadedList = ois.readObject() as ArrayList<SandboxControl>
                controlsList.clear()
                controlsList.addAll(loadedList)
                ois.close()
                controlsAdapter.updateControls(controlsList) // Update the UI
                Log.i(TAG, "Successfully loaded ${controlsList.size} controls.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load controls", e)
        }
    }
}