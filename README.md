EasyESP: Android & ESP32 IoT Framework

  Easy ESP is a complete framework designed to simplify the provisioning and control of ESP32 devices.
It consists of a native Android application and a flexible ESP32 firmware template, allowing developers
to get their IoT projects connected and interactive in minutes. The system uses Bluetooth Low Energy
(BLE) for initial setup and then seamlessly transitions to Wi-Fi (TCP) for real-time control, 
providing a relatively user-friendly experience.

<b>Features</b>
- Seamless WiFi Provisioning: Use the Android app to scan for ESP32 devices and send WiFi credentials over BLE. No hardcoding credentials in your sketch!
- Dynamic Control Sandbox: The app features a "Sandbox" environment where you can create custom UI controls (Buttons, Switches, Sliders) on the fly.
- Custom Command Interaction: Send any custom string command from the app to   trigger complex actions on your ESP32.•Extensible Firmware: The ESP32 sketch is designed as a clean framework. All custom logic is written in a separate user_actions.h file, keeping your code organized and easy to manage.
- Device Management: The app saves successfully provisioned devices, allowing for quick reconnection over Wi-Fi with a single tap.
- Live Serial Monitor: View live Serial.print() outputs from your ESP32 directly in the app's Sandbox for easy debugging.

<b> How It Works </b>

The workflow is designed to be simple and intuitive:
- 1.Provision: The Android app discovers the ESP32 via BLE. The user enters WiFi credentials, which are sent securely to the device.
- 2.Connect: The ESP32 disconnects from BLE, connects to the specified WiFi network, and starts a TCP server.
- 3.Control: The Android app connects to the ESP32 via its local IP address over TCP. Users can then interact with the device in real-time through the Sandbox interface, sending commands to control hardware.

<b> Getting Started </b>

You will need to set up both the Android application and the ESP32 firmware.
1. Android App Setup1.Clone the Repository:Kotlingit clone https://github.com/your-username/EasyESP.git
2. Open in Android Studio: Open the cloned EasyESP folder as a project in Android Studio.
3. Build the Project: Let Gradle sync and build the project. You may need to update dependencies as prompted by Android Studio.
4. Run the App: Install the application on an Android device or emulator.
5. ESP32 Firmware SetupThe firmware is located in the ESP32_Firmware directory of this repository.
6. Prerequisites:
- Arduino IDE or PlatformIO installed.
- ESP32 board support installed in your IDE.
- The FastLED library installed. You can install it via the Arduino Library Manager (Sketch > Include Library > Manage Libraries...).
- ESPping library

<b> Flashing the Sketch: </b>
1. Open the EasyESP.ino file in the Arduino IDE. The IDE should automatically open the user_actions.h file in another tab.
2. Select your ESP32 board model and COM port from the Tools menu.
3. Click the Upload button to flash the firmware to your device.
  
<b> Customizing Your ESP32 Actions </b>
- The core principle of this framework is that you only need to edit the user_actions.h file.
- The main sketch handles all networking and command parsing.
- When a valid command is received from the app, it calls one of two functions in user_actions.h:handle_user_action(char* type, int pin, int value)
- This function is called for Buttons, Switches, and Sliders. Use an if/else if chain to check the command parts and trigger your hardware logic.

<b> Example from the file: </b>

    Java// Handle a slider command for the built-in NeoPixel
    else if (strcmp(type, "V") == 0 && pin == NEOPIXEL_PIN) {
    // The 'value' (0-255) from the slider will control the brightness
    Serial.printf("Action: Setting NeoPixel brightness to %d.\n", value);

    // Set a base color and scale its brightness with the slider's value
    leds[0] = CRGB::White;
    leds[0].nscale8(value);
    
    FastLED.show();
    }handle_interaction_command(String line)This function is called for the Interaction control type. It receives the raw command string you defined in the app.Example from the file:Javavoid handle_interaction_command(String line) {
    Serial.printf("User interaction handler received: %s\n", line.c_str());

    // Handle a custom "LIGHTS_ON" command
    if (line == "LIGHTS_ON") {
        Serial.println("Action: Turning NeoPixel ON.");
        leds[0] = CRGB::Blue;
        FastLED.show();
    } else if (line == "LIGHTS_OFF") {
        Serial.println("Action: Turning NeoPixel OFF.");
        leds[0] = CRGB::Black;
        FastLED.show();
    }
    }
<img width="1080" height="2400" alt="main" src="https://github.com/user-attachments/assets/060d2aeb-0785-4cd3-bc49-021bd7b56993" />
<img width="1080" height="2400" alt="connected" src="https://github.com/user-attachments/assets/850586c5-5f87-4e41-9c22-c803e3e8f9d7" />
<img width="1080" height="2400" alt="menu" src="https://github.com/user-attachments/assets/1879f9d8-ae35-48f4-945b-69b51dc2f8d6" />
<img width="1080" height="2400" alt="sandbox" src="https://github.com/user-attachments/assets/9707674b-b2f0-42ff-a95b-a8242101e786" />
<img width="1080" height="2400" alt="addcontrol" src="https://github.com/user-attachments/assets/0d7f77ed-daab-4a88-b0ce-f5c82c1efa70" />
<img width="1080" height="2400" alt="adddevice" src="https://github.com/user-attachments/assets/6845a5e0-d2c9-45de-9e46-dcf3c0f94fa0" />
<img width="1080" height="2400" alt="deviceoptions" src="https://github.com/user-attachments/assets/d0760e7b-385e-4aa6-9182-969b8c7300be" />


