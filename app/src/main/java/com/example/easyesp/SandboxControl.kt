package com.example.easyesp

import java.io.Serializable

// Enum to define the types of controls we can have.
// This prevents errors from using simple strings.
enum class ControlType {
    BUTTON,
    SWITCH,
    SLIDER, // We decided a Slider is a better name for the "Value" type
    INTERACTION //new
}

/**
 * Represents a single user-defined control in the Sandbox.
 *
 * @property id A unique identifier for the control, typically the timestamp of creation.
 * @property name The user-given name for the control (e.g., "Main LED").
 * @property type The kind of control this is (BUTTON, SWITCH, or SLIDER).
 * @property pin The GPIO pin on the ESP32 this control is linked to.
 * @property value The current state or configuration of the control.
 *           - For BUTTON: The duration of the HIGH signal in milliseconds.
 *           - For SWITCH: The current state, 0 for LOW (off) or 1 for HIGH (on).
 *           - For SLIDER: The current analog value, typically from 0 to 255.
 */
data class SandboxControl(
    val id: Long = System.currentTimeMillis(),
    var name: String,
    val type: ControlType,
    val pin: Int? = null,
    var value: Int? = null,
    var command: String? = null // New field for INTERACTION
) : Serializable