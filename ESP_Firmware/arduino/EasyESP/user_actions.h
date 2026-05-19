// ---------------------------------------------------------------- //
// --- USER IMPLEMENTATION - ADD YOUR CUSTOM CODE AND LIBRARIES --- //
// ---------------------------------------------------------------- //

// Included Guards prevent this file from being loaded 
// twice by the compiler, avoiding "multiple definition" errors!
#ifndef USER_ACTIONS_H
#define USER_ACTIONS_H

#include <Arduino.h>
#include <WiFi.h> 
#include <FastLED.h>
#include <ESPping.h>

extern WiFiClient client; // Allows use of client.printf to send logs to the app

// STEP 1: DEFINE YOUR PINS AND GLOBAL VARIABLES HERE
#define NEOPIXEL_PIN 48 // The GPIO pin for WS2812 LED
#define NUM_LEDS 1
CRGB leds[NUM_LEDS]; // Defines the LED array for FastLED

// STEP 2: CREATE YOUR SETUP FUNCTION
// This function will be called once from the main setup().
void user_setup() {
    FastLED.addLeds<NEOPIXEL, NEOPIXEL_PIN>(leds, NUM_LEDS); 
    Serial.println("User setup complete.");
}

// STEP 3: CREATE YOUR ACTION HANDLER
// Called for standard Sandbox App controls (Buttons, Switches, Sliders)
void handle_user_action(char* type, int pin, int value) {
    Serial.printf("User Action -> Type: %s | Pin: %d | Value: %d\n", type, pin, value);

    // --- YOUR CUSTOM LOGIC GOES HERE ---

    // **PRIORITY 1: Handle specific, custom pin logic first.**
    if (pin == NEOPIXEL_PIN) {
        Serial.println("Action is for the NeoPixel pin. Handling with FastLED.");
        
        if (strcmp(type, "S") == 0) { // Switch
            leds[0] = (value == 1) ? CRGB::Red : CRGB::Black;
            FastLED.show();
        } 
        else if (strcmp(type, "V") == 0) { // Slider
            leds[0] = CRGB::White;
            leds[0].nscale8(value);
            FastLED.show();
        } 
        else if (strcmp(type, "B") == 0) { // Button
            leds[0] = CRGB::Red;
            FastLED.show();
            delay(value); // Note: using delay() will briefly block the TCP heartbeat!
            leds[0] = CRGB::Black;
            FastLED.show();
        }
        return; // IMPORTANT: Exit after handling the special pin case.
    }

    // **PRIORITY 2: Handle generic, default pin actions.**
    if (strcmp(type, "B") == 0) {
        // Default Button: Momentary HIGH signal
        pinMode(pin, OUTPUT);
        digitalWrite(pin, HIGH);
        delay(value);
        digitalWrite(pin, LOW);
    }
    else if (strcmp(type, "S") == 0) {
        // Default Switch: Set pin HIGH or LOW
        pinMode(pin, OUTPUT);
        digitalWrite(pin, (value == 1) ? HIGH : LOW);
    }
    else if (strcmp(type, "V") == 0) {
        // Default Slider: Write an analog value (PWM)
        // NOTE: ESP32's analogWrite uses ledc under the hood in modern cores.
        pinMode(pin, OUTPUT);
        analogWrite(pin, value); 
    }
}

// STEP 4: HANDLE CUSTOM INTERACTION STRINGS
// Called when sending free-form text commands from the App.
void handle_interaction_command(String line) {
    Serial.printf("Interaction Action -> Command: %s\n", line.c_str());

    // --- YOUR CUSTOM LOGIC GOES HERE ---
    
    // Example 1: Pinging a website (Command format: "PING:google.com")
    if (line.startsWith("PING:")) {
        String host = line.substring(5); 
        
        // Use client.printf to send logs directly to the Android App screen
        client.printf("LOG:Pinging host: %s...\n", host.c_str());

        bool success = Ping.ping(host.c_str(), 3); // Ping 3 times

        if (!success) {
            Serial.printf("Ping failed to host %s\n", host.c_str());
            client.printf("LOG:Ping failed. Host may be unreachable.\n");
        } else {
            float avg_time = Ping.averageTime();
            Serial.printf("Ping success, avg time: %.2fms\n", avg_time);
            client.printf("LOG:Ping success! Average time: %.2f ms\n", avg_time);
        }
    }

    // Example 2: Simple string triggers
    if (line == "TESTLED_ON") {
        Serial.println("Action: Turning NeoPixel ON.");
        leds[0] = CRGB::Blue;
        FastLED.show();
    } 
    else if (line == "TESTLED_OFF") {
        Serial.println("Action: Turning NeoPixel OFF.");
        leds[0] = CRGB::Black;
        FastLED.show();
    }
}

#endif // USER_ACTIONS_H