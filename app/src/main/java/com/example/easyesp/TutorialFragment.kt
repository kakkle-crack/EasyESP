package com.example.easyesp

import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class TutorialFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_tutorial, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tutorialTextView: TextView = view.findViewById(R.id.tutorial_text_view)

        // Using fromHtml to easily format the text with bolding, etc.
        val tutorialText = """
            <h1>I. Welcome to EasyESP</h1>
            <p>This guide will walk you through using the app to provision and control your ESP32 devices.</p>
            <br>

            <h1>II. Bluetooth Connection</h1>
            <p>The first step is always to connect to your ESP32 via Bluetooth Low Energy (BLE). The app automatically scans for nearby ESP32 devices when you open it.</p>
            <p><b>Step 1:</b> Ensure your ESP32 is powered on and running the correct firmware.</p>
            <p><b>Step 2:</b> The app will show "Status: Connected (BLE)" on the WiFi Terminal screen once a connection is established.</p>
            <p>If a connection is not made, ensure Bluetooth is enabled on your phone and you are near the device.</p>
            <br>

            <h1>III. WiFi Provisioning</h1>
            <p>Once connected via BLE, you can send WiFi credentials to your ESP32.</p>
            <p><b>Step 1:</b> On the "WiFi Terminal" screen, the app should automatically fill in the SSID of the WiFi network your phone is currently connected to.</p>
            <p><b>Step 2:</b> Enter the password for that WiFi network.</p>
            <p><b>Step 3:</b> Tap the "Connect" button. The credentials will be sent to the ESP32 over BLE.</p>
            <p><b>Step 4:</b> The ESP32 will disconnect from BLE, connect to your WiFi network, and begin listening for TCP connections. The app will automatically save this device to your Known Devices list for future use.</p>
            <br>

            <h1>IV. Known Devices List</h1>
            <p>This screen shows all the devices you have successfully provisioned. From here, you can quickly connect to any device on the same local network.</p>
            <p><b>Connecting:</b> Simply tap on a device in the list to initiate a WiFi (TCP) connection.</p>
            <p><b>Device Options:</b> To manage a device, <b>press and hold</b> the device entry in the list. This will open an options menu:</p>
            <ul>
                <li><b>Edit:</b> Allows you to rename the device.</li>
                <li><b>Delete:</b> Forgets the device from the app's memory. If you are connected to the device, this will also disconnect you.</li>
                <li><b>Disconnect:</b> This option only appears if you are currently connected to the device. It allows you to safely end the TCP connection.</li>
            </ul>
            <p>The currently connected device will be highlighted in the list.</p>
            <br>

            <h1>V. Sandbox</h1>
            <p>The Sandbox is a powerful tool for interacting directly with your connected ESP32. It shows a live serial monitor and allows you to create and send custom commands. The commands are sent in a specific format: <b>"Type,Pin,Value"</b>.</p>
            <p><b>Adding Controls:</b> Tap the '+' floating action button to add a new control.</p>
            <p><b>Managing Controls:</b> To edit or delete a control, <b>press and hold</b> it. This will open its options menu.</p>
            <br>
            <h3>How Each Control Works:</h3>
            <p><b>1. Button (Type "B")</b></p>
            <p>The button is used for momentary actions. Based on the firmware, the "Value" field corresponds to a <b>duration in milliseconds</b>.</p>
            <ul>
                <li><b>Command Sent:</b> B,Pin,Value</li>
                <li><b>ESP32 Action:</b> The ESP32 sets the specified pin to HIGH, waits for the number of milliseconds defined in "Value", and then sets the pin back to LOW.</li>
                <li><b>Example:</b> A button configured with Pin=2 and Value=1000 will turn on pin 2 for exactly one second and then turn it off.</li>
            </ul>
            <br>
            <p><b>2. Switch (Type "S")</b></p>
            <p>The switch is used for maintaining a state (On or Off). The "Value" is automatically handled by the switch's state.</p>
            <ul>
                <li><b>Command Sent (On):</b> S,Pin,1</li>
                <li><b>Command Sent (Off):</b> S,Pin,0</li>
                <li><b>ESP32 Action:</b> The ESP32 sets the specified pin to HIGH when the switch is turned on and LOW when it is turned off. It remains in that state until changed.</li>
                <li><b>Example:</b> A switch on Pin=4 will set pin 4 HIGH when toggled on and LOW when toggled off.</li>
            </ul>
            <br>
            <p><b>3. Slider (Type "V")</b></p>
            <p>The slider is used to send a variable value, typically for tasks like dimming an LED or controlling motor speed. It uses the ESP32's LEDC (PWM) controller.</p>
            <ul>
                <li><b>Command Sent:</b> V,Pin,Value</li>
                <li><b>ESP32 Action:</b> The ESP32 receives the "Value" (from 0 to 255) from the slider's position and applies it as a PWM duty cycle to the specified pin.</li>
                <li><b>Example:</b> A slider on Pin=15 will control the brightness of an LED on that pin. Moving the slider will change the value from 0 (off) to 255 (full brightness).</li>
            </ul>
            <br>
            
            <p><b>4. Interaction (Custom Command)</b></p>
            <p>This control type allows you to send any custom string command to your ESP32, giving you maximum flexibility for actions that aren't tied to a specific pin.</p>
            <ul>
                <li><b>Command Sent:</b> The exact string you define (e.g., "REBOOT_DEVICE", "RUN_CLEANING_CYCLE").</li>
                <li><b>ESP32 Action:</b> The firmware's <b>handle_interaction_command()</b> function is called, allowing you to parse the string and trigger any custom logic you've written.</li>
                <li><b>Example:</b> An Interaction control with the command "TESTLED_ON" paired to pin 48 (built-in LED pin) can be used to trigger the built-in LED on your ESP32. See user_actions.h for more examples to include button, switch, and slider usage.</li>
            </ul>
            <br>

            <h1>VI. Making It Your Own: The <code>user_actions.h</code> File</h1>
            <p>The true power of this framework lies in the ESP32 sketch's <code>user_actions.h</code> file. This file is where you, the developer, bring the app's commands to life.</p>
            <p>When you send a command from the Sandbox (e.g., "S,4,1"), the main ESP32 code handles the networking and parses the command into its parts (type, pin, and value). It then calls one of two functions in <code>user_actions.h</code> for you to implement:</p>
            <ul>
                <li><b><code>handle_user_action(type, pin, value)</code></b> is called for Buttons, Switches, and Sliders. Inside this function, you can use an <code>if/else if</code> chain to check the command parts and trigger your specific hardware logic, like turning on a motor or a FastLED strip.</li>
                <li><b><code>handle_interaction_command(line)</code></b> is called for the Interaction control type. It receives the raw command string you defined in the app, allowing you to implement unique, high-level commands like "TEST_MODE" or "SYSTEM_RESET".</li>
            </ul>
            <p>By editing only the <code>user_actions.h</code> file, you can build complex projects without ever needing to modify the core networking or communication logic of the firmware.</p>
            <br>

            <h1>VII. Troubleshooting</h1>
            <p><b>Can't connect via BLE?</b><br>Ensure your phone's Bluetooth is on, location services are enabled (required by Android for BLE scanning), and you are close to the ESP32.</p>
            <p><b>WiFi Provisioning Fails?</b><br>Double-check the WiFi password. Ensure the SSID and password are for a 2.4GHz network, as many ESP32 models do not support 5GHz.</p>
            <p><b>Can't connect from Known Devices?</b><br>Ensure your phone is on the same WiFi network as the ESP32. The device might have a different IP address than what is saved; try re-provisioning it.</p>
            <br>
            
            <h1>VII. Contact</h1>
            <p>For bugs, feature requests, or questions, please open an issue on the project's GitHub page or contact the developer directly.</p>
        """.trimIndent()

        tutorialTextView.text = Html.fromHtml(tutorialText, Html.FROM_HTML_MODE_LEGACY)
        tutorialTextView.movementMethod = LinkMovementMethod.getInstance() // Allows clicking links if you add any
    }
}