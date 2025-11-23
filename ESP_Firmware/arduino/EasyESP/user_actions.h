// ---------------------------------------------------------------- //
// --- USER IMPLEMENTATION - ADD YOUR CUSTOM CODE AND LIBRARIES --- //
// ---------------------------------------------------------------- //

// STEP 1: ADD LIBRARIES HERE
#include <FastLED.h>

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
// This function is called by the main loop() whenever a valid command is received.
void handle_user_action(char* type, int pin, int value) {
    Serial.printf("User action handler received: Type=%s, Pin=%d, Value=%d\n", type, pin, value);

    // --- YOUR CUSTOM LOGIC GOES HERE ---
    // Example Below: Make the onboard NeoPixel light up for a button press on pin 1

    if (strcmp(type, "B") == 0 && pin == NEOPIXEL_PIN) {
        Serial.println("Action: Triggering NeoPixel.");
        leds[0] = CRGB::Red; // Turn the LED Red
        FastLED.show();
        delay(value);       // Keep it on for the duration specified by the app
        leds[0] = CRGB::Black; // Turn it off
        FastLED.show();
    } else if (strcmp(type, "S") == 0 && pin == NEOPIXEL_PIN) {
        Serial.printf("Action: Triggering NeoPixel (Switch) to value %d.\n", value);
        if (value == 1) {
            // This is the ON state
            leds[0] = CRGB::Red; // Turn the LED Red
            FastLED.show();
        } else {
            // This is the OFF state (value == 0)
            leds[0] = CRGB::Black; // Turn it off
            FastLED.show();
        }
    } else if (strcmp(type, "V") == 0 && pin == NEOPIXEL_PIN) {
        // Here, the 'value' (0-255) from the app's slider will control the brightness.
        Serial.printf("Action: Setting NeoPixel brightness to %d.\n", value);
        
        // Set the color (e.g., White) and then scale its brightness using the slider value.
        // The nscale8_video function is a fast way to apply a brightness level (0-255) to a color.
        leds[0] = CRGB::White; // Start with a base color
        leds[0].nscale8(value); // Scale its brightness by the slider's value
        
        FastLED.show();
    }
}

// STEP 5: HANDLE CUSTOM INTERACTION STRINGS
// This function is called when a command is not a standard Button, Switch, or Value command.
void handle_interaction_command(String line) {
    Serial.printf("User interaction handler received: %s\n", line.c_str());

    // --- YOUR CUSTOM LOGIC GOES HERE ---
    // Example: Handle a "LIGHTS_ON" and "LIGHTS_OFF" command
    
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