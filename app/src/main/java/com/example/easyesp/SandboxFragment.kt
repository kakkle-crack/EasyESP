package com.example.easyesp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.easyesp.databinding.FragmentSandboxBinding
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * SandboxFragment allows users to create custom controls and interact with their ESP32.
 * Migrated to ViewBinding and StateFlow.
 */
class SandboxFragment : Fragment() {

    private val TAG = "SandboxFragment"

    private var _binding: FragmentSandboxBinding? = null
    private val binding get() = _binding!!

    // --- ViewModel & Data ---
    private val connectionViewModel: ConnectionViewModel by activityViewModels()
    private lateinit var controlsAdapter: SandboxControlsAdapter
    private val controlsList = mutableListOf<SandboxControl>()
    private val FILENAME = "sandbox_controls.dat"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSandboxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- Setup ---
        setupRecyclerView()
        loadControls()

        binding.buttonAddControl.setOnClickListener {
            showAddControlDialog()
        }

        // --- Observers & StateFlow Collection ---
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    connectionViewModel.serialLog.collectLatest { log ->
                        binding.monitorTextView.text = log
                        binding.monitorScrollview.post { 
                            binding.monitorScrollview.fullScroll(View.FOCUS_DOWN) 
                        }
                    }
                }

                launch {
                    connectionViewModel.tcpMessageEvent.collect { message ->
                        processIncomingMessage(message)
                    }
                }
            }
        }
    }

    private fun processIncomingMessage(message: String) {
        when {
            message.startsWith("LOG:") -> {
                val logMessage = message.substringAfter("LOG:").trim()
                appendToSandboxMonitor("ESP32: $logMessage")
            }
            message.startsWith("ACK:") -> {
                val ackMessage = message.substringAfter("ACK:").trim()
                appendToSandboxMonitor("ACK from ESP32: $ackMessage")
            }
            else -> {
                appendToSandboxMonitor("ESP32 (RAW): $message")
            }
        }
    }

    private fun setupRecyclerView() {
        controlsAdapter = SandboxControlsAdapter(
            controlsList,
            { _, command ->
                Log.d(TAG, "Interaction callback: Sending command '$command'")
                if (connectionViewModel.isTcpConnected.value) {
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
        binding.recyclerViewControls.adapter = controlsAdapter
        binding.recyclerViewControls.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun appendToSandboxMonitor(message: String) {
        binding.monitorTextView.append("\n$message")
        binding.monitorScrollview.post { binding.monitorScrollview.fullScroll(View.FOCUS_DOWN) }
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

        val nameInput: EditText = dialogView.findViewById(R.id.dialog_control_name)
        val typeSpinner: Spinner = dialogView.findViewById(R.id.dialog_control_type_spinner)
        val pinInput: EditText = dialogView.findViewById(R.id.dialog_control_pin)
        val pinLayout = pinInput.parent.parent as TextInputLayout
        val valueLayout: TextInputLayout = dialogView.findViewById(R.id.dialog_value_layout)
        val valueInput: EditText = dialogView.findViewById(R.id.dialog_control_value)
        val commandLayout: TextInputLayout = dialogView.findViewById(R.id.dialog_command_layout)
        val commandInput: EditText = dialogView.findViewById(R.id.dialog_control_command)

        val controlTypes = ControlType.entries.map { it.name }
        val spinnerAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item, controlTypes)
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        typeSpinner.adapter = spinnerAdapter

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

        AlertDialog.Builder(requireContext(), R.style.Theme_EasyESP_Dialog)
            .setView(dialogView)
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
                            SandboxControl(name = name, type = type, pin = pin, value = value)
                        }
                    }
                    ControlType.INTERACTION -> {
                        val command = commandInput.text.toString()
                        if (command.isBlank()) {
                            Toast.makeText(requireContext(), "Command cannot be empty for Interaction.", Toast.LENGTH_SHORT).show()
                            null
                        } else {
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

    private fun saveControls() {
        try {
            val fos = requireContext().openFileOutput(FILENAME, Context.MODE_PRIVATE)
            val oos = ObjectOutputStream(fos)
            oos.writeObject(ArrayList(controlsList))
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
                controlsAdapter.updateControls(controlsList)
                Log.i(TAG, "Successfully loaded ${controlsList.size} controls.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load controls", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
