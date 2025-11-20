// --- FINAL IOT SKETCH - WIFI & TCP SERVER (Corrected) ---

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <WiFi.h>
#include <ESPmDNS.h>
#include "user_actions.h" //This is where you will build your own custom user actions

// --- WiFi & Network Server ---
WiFiServer tcpServer(8888);
WiFiClient client;

// --- Bluetooth (for initial provisioning) ---
BLEServer* pServer = NULL;
bool deviceConnected = false;
char wifi_ssid[64] = {0};
char wifi_pass[64] = {0};
volatile bool shouldConnectToWifi = false;

// *** NEW STATE FLAGS FOR ROBUSTNESS ***
bool isProvisioned = false;      // Tracks if we have successfully connected to WiFi
bool is_advertising = false;     // Manually track advertising state

#define SERVICE_UUID           "1fc8d4ca-3b3d-42e3-bdf0-1ff2edcf8268"
#define CHARACTERISTIC_UUID_RX "586eb1c5-597a-4c5a-bfcf-655d4909b7a1"
#define CHARACTERISTIC_UUID_TX "586eb1c5-597a-4c5a-bfcf-655d4909b7a2" // ESP -> Phone

BLECharacteristic* pTxCharacteristic = NULL; // Global pointer

//#define LEDC_CHANNEL_0 0 // We'll use channel 0 for our first slider
//#define LEDC_TIMER_8_BIT 8 // 8-bit resolution (0-255)
//#define LEDC_BASE_FREQ 5000 // 5kHz frequency

// --- BLE Callbacks ---
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
        deviceConnected = true;
        is_advertising = false; // We are connected, so we are not advertising
        Serial.println(">>> Device connected");
    }
    void onDisconnect(BLEServer* pServer) {
        deviceConnected = false;
        Serial.println("<<< Device disconnected");
    }
};

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        String rxValue = pCharacteristic->getValue();

        if (rxValue.length() > 0 && rxValue.length() < 128) {
            Serial.print(">>> Received Value: ");
            Serial.println(rxValue);

            if (rxValue.startsWith("WIFI:")) {
                char temp_buffer[128];
                rxValue.toCharArray(temp_buffer, sizeof(temp_buffer));
                temp_buffer[sizeof(temp_buffer) - 1] = '\0';

                char* ssid_ptr = strtok(temp_buffer + 5, ",");
                if (ssid_ptr != NULL) {
                    strncpy(wifi_ssid, ssid_ptr, sizeof(wifi_ssid) - 1);
                    wifi_ssid[sizeof(wifi_ssid) - 1] = '\0';

                    char* pass_ptr = strtok(NULL, ",");
                    if (pass_ptr != NULL) {
                        strncpy(wifi_pass, pass_ptr, sizeof(wifi_pass) - 1);
                        wifi_pass[sizeof(wifi_pass) - 1] = '\0';
                    } else {
                        memset(wifi_pass, 0, sizeof(wifi_pass));
                    }

                    shouldConnectToWifi = true;
                }
            }
        }
    }
};

void start_advertising() {
    BLEDevice::getAdvertising()->start();
    is_advertising = true;
    Serial.println(">>> BLE Provisioning Service Started. Advertising.");
}

void stop_advertising() {
    BLEDevice::getAdvertising()->stop();
    is_advertising = false;
    Serial.println("<<< BLE Advertising Stopped.");
}

void setup() {
    Serial.begin(115200);
    delay(1000);
    Serial.println("\n--- ESP32 IoT Device Booting ---");

    user_setup(); //call user setup
    //Setup LEDC Channel
    //ledcSetup(LEDC_CHANNEL_0, LEDC_BASE_FREQ, LEDC_TIMER_8_BIT);
    //Serial.println("LEDC Channel 0 configured.");

    // Start BLE for provisioning
    BLEDevice::init("MyESP32");
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());
    BLEService *pService = pServer->createService(SERVICE_UUID);
    BLECharacteristic* pRxCharacteristic = pService->createCharacteristic(CHARACTERISTIC_UUID_RX, BLECharacteristic::PROPERTY_WRITE);
    pRxCharacteristic->setCallbacks(new MyCallbacks());
    pTxCharacteristic = pService->createCharacteristic(
        CHARACTERISTIC_UUID_TX,
        BLECharacteristic::PROPERTY_NOTIFY // Use NOTIFY to send updates to the phone
    );
    pTxCharacteristic->addDescriptor(new BLE2902()); // Standard descriptor for notifications   
    pService->start();
    start_advertising(); // Use our new helper function
}

void loop() {
    // --- STATE 1: PROVISIONING ATTEMPT ---
    // If a BLE client has sent us WiFi credentials, this is our highest priority.
    if (shouldConnectToWifi) {
        shouldConnectToWifi = false; // We've acknowledged the request.
        //disconnect previous session
        Serial.println("Framework: Disconnecting previous WiFi configuration before new attempt.");
        WiFi.disconnect(true);
        delay(100); // Give it a brief moment to tear down the connection.
        //attempt reconnect
        Serial.print("Connecting to "); Serial.println(wifi_ssid);
        WiFi.begin(wifi_ssid, wifi_pass);
        int attempts = 0;
        while (WiFi.status() != WL_CONNECTED && attempts < 20) {
            delay(500); Serial.print("."); attempts++;
        }

        if (WiFi.status() == WL_CONNECTED) {
            // SUCCESS
            Serial.println("\nSUCCESS: WiFi Connected!");
            isProvisioned = true;

            // Send success message to the phone over BLE
            if (deviceConnected && pTxCharacteristic != NULL) {
                pTxCharacteristic->setValue("STATUS:OK");
                pTxCharacteristic->notify();
                Serial.println(">>> Sent 'STATUS:OK' to phone.");
            }

            // Now, gracefully shut down BLE and start the network services
            delay(100); // give a moment for the BLE notification to send
            stop_advertising();
            if (deviceConnected) { pServer->disconnect(pServer->getConnId()); }

            if (MDNS.begin("easyesp-device")) {
                Serial.println("mDNS responder started. Hostname: easyesp-device.local");
                MDNS.addService("easyesp", "tcp", 8888);
            }
            tcpServer.begin();
            Serial.println("TCP Server started on port 8888. Ready for app connection.");

        } else {
            // FAILURE
            Serial.println("\nFAILURE: Could not connect to WiFi.");
            // Send failure message back to the phone over BLE
            if (deviceConnected && pTxCharacteristic != NULL) {
                pTxCharacteristic->setValue("STATUS:FAIL");
                pTxCharacteristic->notify();
                Serial.println(">>> Sent 'STATUS:FAIL' to phone.");
            }
            // On failure, we do nothing else. The phone is still connected via BLE
            // and the auto-advertising logic below will take over if it disconnects.
        }
    }

    // --- STATE 2: NORMAL OPERATION (TCP COMMANDS) ---
    // If we are already connected to WiFi, our only job is to handle the TCP client.
    else if (WiFi.status() == WL_CONNECTED) {
        if (!client.connected()) {
            client = tcpServer.available();
            if (client) Serial.println("Framework: App has connected via WiFi/TCP!");
        } else {
            if (client.available()) {
                String line = client.readStringUntil('\n');
                line.trim();
                Serial.print("Framework: Received Command: "); Serial.println(line);

                // --- MODIFIED COMMAND PARSER ---
                char cmd_buffer[50];
                line.toCharArray(cmd_buffer, 50);
                char* type = strtok(cmd_buffer, ",");
                
                if (type != NULL) {
                    if (strcmp(type, "B") == 0 || strcmp(type, "S") == 0 || strcmp(type, "V") == 0) {
                        char* pin_str = strtok(NULL, ",");
                        char* val_str = strtok(NULL, ",");
                        
                        if (pin_str != NULL && val_str != NULL) {
                            int pin = atoi(pin_str);
                            int value = atoi(val_str);
                            
                            // Let the framework acknowledge the command
                            Serial.printf("Framework: Parsed as Type=%s, Pin=%d, Value=%d\n", type, pin, value);
                            client.printf("ACK:%s,%d,%d\n", type, pin, value);

                            // *** CALL THE USER'S HANDLER FUNCTION ***
                            handle_user_action(type, pin, value);
                        }
                    } else {
                        // This is not a standard B, S, or V command.
                        // It must be a custom INTERACTION command.
                        client.print("ACK: " + line + "\n");
                        
                        // *** CALL THE USER'S INTERACTION HANDLER ***
                        handle_interaction_command(line);
                    }
                }
            }
        }
    }

    // --- STATE 3: RECOVERY / IDLE ADVERTISING ---
    // This runs if we are not trying to connect to WiFi and are not yet on WiFi.
    // Its job is to make sure we are discoverable via BLE if we need to be.
    else {
        if (!isProvisioned && !deviceConnected && !is_advertising) {
            Serial.println(">>> Idle state. Restarting BLE advertising for provisioning...");
            delay(500); // Give a moment before restarting
            start_advertising();
        }
    }
}