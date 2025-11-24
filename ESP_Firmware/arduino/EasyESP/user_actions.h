// ---------------------------------------------------------------- //
// --- USER IMPLEMENTATION - ADD YOUR CUSTOM CODE AND LIBRARIES --- //
// ---------------------------------------------------------------- //

#include <WiFi.h> //Required for Client declaration
extern WiFiClient client; //required for messages

// STEP 1: ADD LIBRARIES HERE
#include <FastLED.h>
#include <ESPping.h>

// STEP 2: DEFINE YOUR PINS AND GLOBAL VARIABLES HERE
#define NEOPIXEL_PIN 48 // The GPIO pin for WS2812 LED (adjust if needed)
#define NUM_LEDS 1
CRGB leds[NUM_LEDS]; // Defines the LED array for FastLED

// STEP 3: CREATE YOUR SETUP FUNCTION
// This function will be called once from the main setup().
void user_setup() {
    FastLED.addLeds<NEOPIXEL, NEOPIXEL_PIN>(leds, NUM_LEDS); //example for LED
    Serial.println("User setup complete.");//example for LED
}

// STEP 4: CREATE YOUR ACTION HANDLER
void handle_user_action(char* type, int pin, int value) {
    Serial.printf("User action handler received: Type=%s, Pin=%d, Value=%d\n", type, pin, value);

    // --- YOUR CUSTOM LOGIC GOES HERE ---

    // **PRIORITY 1: Handle specific, custom pin logic first.**
    // Example: If the command is for the NeoPixel, do something special with it.
    if (pin == NEOPIXEL_PIN) {
        Serial.println("Action is for the NeoPixel pin. Handling with FastLED.");
        if (strcmp(type, "S") == 0) { // Switch
            leds[0] = (value == 1) ? CRGB::Red : CRGB::Black;
            FastLED.show();
        } else if (strcmp(type, "V") == 0) { // Slider
            leds[0] = CRGB::White;
            leds[0].nscale8(value);
            FastLED.show();
        } else if (strcmp(type, "B") == 0) { // Button
            leds[0] = CRGB::Red;
            FastLED.show();
            delay(value);
            leds[0] = CRGB::Black;
            FastLED.show();
        }
        return; // IMPORTANT: Exit after handling the special pin case.
    }

    // **PRIORITY 2: Handle generic, default pin actions.**
    // If the code reaches here, it means the pin was NOT a special use pin.
    // Now, perform the fundamental digital/analog actions.
    if (strcmp(type, "B") == 0) {
        // Default Button: Momentary HIGH signal
        Serial.printf("Action: Generic Button on pin %d\n", pin);
        pinMode(pin, OUTPUT);
        digitalWrite(pin, HIGH);
        delay(value);
        digitalWrite(pin, LOW);
    }
    else if (strcmp(type, "S") == 0) {
        // Default Switch: Set pin HIGH or LOW
        Serial.printf("Action: Generic Switch on pin %d to value %d\n", pin, value);
        pinMode(pin, OUTPUT);
        digitalWrite(pin, (value == 1) ? HIGH : LOW);
    }
    else if (strcmp(type, "V") == 0) {
        // Default Slider: Write an analog value (PWM)
        // NOTE: ESP32's analogWrite is actually ledc. We need to set it up.
        Serial.printf("Action: Generic Slider on pin %d to value %d\n", pin, value);
        // ledc is better, but analogWrite is simpler
        // For a real project, use ledcSetup/ledcAttachPin/ledcWrite.
        pinMode(pin, OUTPUT);
        analogWrite(pin, value); 
    }
}

// STEP 5: HANDLE CUSTOM INTERACTION STRINGS
// This function is called when a command is not a standard Button, Switch, or Value command.
void handle_interaction_command(String line) {
    Serial.printf("User interaction handler received: %s\n", line.c_str());

    // --- YOUR CUSTOM LOGIC GOES HERE ---
        //Example 1: Pinging a website 
        //using command format: "PING:google.com" as text
    if (line.startsWith("PING:")) {
        String host = line.substring(5); // Extract the hostname
        
        // Let the app know we're starting the process
        client.printf("LOG:Pinging host: %s...\n", host.c_str());

        bool success = Ping.ping(host.c_str(), 3); // Ping 3 times

        if (!success) {
            Serial.printf("Ping failed to host %s\n", host.c_str());
            // Send the failure message back to the app's monitor
            client.printf("LOG:Ping failed. Host may be unreachable.\n");
        } else {
            float avg_time = Ping.averageTime();
            Serial.printf("Ping success, avg time: %.2fms\n", avg_time);
            // Send the successful result back to the app's monitor!
            client.printf("LOG:Ping success! Average time: %.2f ms\n", avg_time);
        }
    }

    // Example 2: Handle a "LIGHTS_ON" and "LIGHTS_OFF" command
    
    if (line == "TESTLED_ON") {
        Serial.println("Action: Turning NeoPixel ON.");
        leds[0] = CRGB::Blue;
        FastLED.show();
    } else if (line == "TESTLED_OFF") {
        Serial.println("Action: Turning NeoPixel OFF.");
        leds[0] = CRGB::Black;
        FastLED.show();
    }
}

// ---------------------------------------------------------------- //
// --- END OF USER IMPLEMENTATION --- //
// ---------------------------------------------------------------- //