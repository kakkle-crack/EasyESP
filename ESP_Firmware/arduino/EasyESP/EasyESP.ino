#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <WiFi.h>
#include <ESPmDNS.h>
#include <Preferences.h>

// Include custom actions
#include "user_actions.h" 

// --- Configuration ---
#define SERVICE_UUID           "1fc8d4ca-3b3d-42e3-bdf0-1ff2edcf8268"
#define CHARACTERISTIC_UUID_RX "586eb1c5-597a-4c5a-bfcf-655d4909b7a1"
#define CHARACTERISTIC_UUID_TX "586eb1c5-597a-4c5a-bfcf-655d4909b7a2"
#define TCP_PORT               8888
#define DEVICE_NAME            "EasyESPDevice"

// --- Global Objects ---
WiFiServer tcpServer(TCP_PORT);
WiFiClient client;
Preferences preferences;
BLECharacteristic* pTxChar = NULL;

// --- State Management ---
enum DeviceState { IDLE, PROVISIONING, CONNECTING, OPERATIONAL };
DeviceState currentState = IDLE;

bool bleConnected = false;
unsigned long stateStartTime = 0;
const unsigned long WIFI_TIMEOUT_MS = 20000; // 20 seconds to try connecting

// --- Forward Declarations ---
void startBLE();
void stopBLE();
void handleTcpServer();

// --- BLE Callbacks ---
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) { 
        bleConnected = true; 
        Serial.println("System: BLE Connected"); 
    }
    void onDisconnect(BLEServer* pServer) { 
        bleConnected = false; 
        Serial.println("System: BLE Disconnected"); 
        // If disconnected during provisioning, restart advertising
        if (currentState == PROVISIONING) {
            delay(500);
            BLEDevice::startAdvertising();
        }
    }
};

class ProvisioningCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pChar) {
        String rx = pChar->getValue().c_str(); // Safely get string
        
        if (rx.startsWith("WIFI:")) {
            int commaIndex = rx.indexOf(',', 5);
            if (commaIndex != -1) {
                String ssid = rx.substring(5, commaIndex);
                String pass = rx.substring(commaIndex + 1);

                Serial.printf("System: Received Credentials for %s\n", ssid.c_str());

                // Save to non-volatile storage
                preferences.begin("wifi", false);
                preferences.putString("ssid", ssid);
                preferences.putString("pass", pass);
                preferences.end();

                // Send success back to the Android App
                if (pTxChar) { 
                    pTxChar->setValue("STATUS:OK"); 
                    pTxChar->notify(); 
                }
                
                // Allow time for the BLE packet to send before restarting
                delay(1000); 
                Serial.println("System: Credentials saved. Restarting to apply...");
                ESP.restart(); // A clean reboot is best practice for initializing WiFi
            } else {
                 if (pTxChar) { pTxChar->setValue("STATUS:FAIL"); pTxChar->notify(); }
            }
        }
    }
};

void setup() {
    Serial.begin(115200);
    delay(1000);
    Serial.println("\n--- EasyESP Framework Booting ---");

    user_setup(); // Initialize user's custom hardware

    // Check for saved credentials
    preferences.begin("wifi", true);
    String ssid = preferences.getString("ssid", "");
    String pass = preferences.getString("pass", "");
    preferences.end();

    if (ssid != "") {
        currentState = CONNECTING;
        stateStartTime = millis();
        WiFi.begin(ssid.c_str(), pass.c_str());
        Serial.printf("System: Connecting to saved network: %s...\n", ssid.c_str());
    } else {
        currentState = PROVISIONING;
        startBLE();
    }
}

void loop() {
    switch (currentState) {
        case CONNECTING:
            if (WiFi.status() == WL_CONNECTED) {
                Serial.println("\nSystem: WiFi Connected Successfully!");
                
                // Start mDNS and TCP services
                if (MDNS.begin("easyesp-device")) {
                    MDNS.addService("easyesp", "tcp", TCP_PORT);
                }
                tcpServer.begin();

                // MEMORY OPTIMIZATION: Completely shut down BLE to free up ~50KB of RAM
                BLEDevice::deinit(true); 
                Serial.println("System: BLE De-initialized to free RAM.");

                currentState = OPERATIONAL;
            } 
            else if (millis() - stateStartTime > WIFI_TIMEOUT_MS) {
                Serial.println("\nSystem: WiFi Connection timeout.");
                WiFi.disconnect();
                
                // Clear bad credentials so it does not get stuck in a boot loop
                preferences.begin("wifi", false);
                preferences.clear();
                preferences.end();
                
                Serial.println("System: Starting BLE Provisioning...");
                currentState = PROVISIONING;
                startBLE();
            }
            break;

        case OPERATIONAL:
            // Check for dropped connections
            if (WiFi.status() != WL_CONNECTED) {
                Serial.println("System: WiFi Connection Lost! Reconnecting...");
                WiFi.disconnect();
                WiFi.reconnect();
                currentState = CONNECTING;
                stateStartTime = millis();
                break;
            }
            
            // Handle incoming App commands
            handleTcpServer();
            break;

        case PROVISIONING:
            // The BLE Server handles this automatically in the background via callbacks
            break;

        case IDLE:
            break;
    }
}

void startBLE() {
    BLEDevice::init(DEVICE_NAME);
    BLEServer* pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());
    BLEService* pService = pServer->createService(SERVICE_UUID);

    BLECharacteristic* pRx = pService->createCharacteristic(
        CHARACTERISTIC_UUID_RX, 
        BLECharacteristic::PROPERTY_WRITE
    );
    pRx->setCallbacks(new ProvisioningCallbacks());

    pTxChar = pService->createCharacteristic(
        CHARACTERISTIC_UUID_TX, 
        BLECharacteristic::PROPERTY_NOTIFY
    );
    pTxChar->addDescriptor(new BLE2902());

    pService->start();

    // --- IMPROVED ADVERTISING ---
    BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    
    // These two lines help with Android discovery and connection stability
    pAdvertising->setMinPreferred(0x06);  
    pAdvertising->setMinPreferred(0x12);
    
    BLEDevice::startAdvertising();
    Serial.println("System: BLE Advertising active. Ready for App.");
}

void handleTcpServer() {
    if (!client.connected()) {
        client = tcpServer.available();
    } else {
        if (client.available()) {
            String line = client.readStringUntil('\n');
            line.trim();

            // Intercept Heartbeat from App
            if (line == "HEARTBEAT") {
                client.println("HEARTBEAT_ACK");
                return;
            }

            Serial.print("App Command: "); Serial.println(line);

            // MEMORY OPTIMIZATION: We use char arrays and strtok for standard commands
            // This prevents heap fragmentation that occurs when doing heavy String manipulation
            char cmd_buffer[128];
            line.toCharArray(cmd_buffer, sizeof(cmd_buffer));
            
            char* type = strtok(cmd_buffer, ",");
            
            if (type != NULL) {
                // If it is a standard Sandbox UI control (Button, Switch, Value/Slider)
                if (strcmp(type, "B") == 0 || strcmp(type, "S") == 0 || strcmp(type, "V") == 0) {
                    char* pin_str = strtok(NULL, ",");
                    char* val_str = strtok(NULL, ",");
                    
                    if (pin_str != NULL && val_str != NULL) {
                        int pin = atoi(pin_str);
                        int value = atoi(val_str);
                        
                        // Acknowledge receipt
                        client.printf("ACK:%s,%d,%d\n", type, pin, value);
                        
                        // Pass to user logic
                        handle_user_action(type, pin, value);
                    }
                } else {
                    // It is a custom interaction command (like "PING:google.com")
                    client.printf("ACK:%s\n", line.c_str());
                    handle_interaction_command(line);
                }
            }
        }
    }
}