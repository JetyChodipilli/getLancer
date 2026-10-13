// Original getLancer MIT reference firmware. Hardware execution proof is absent.
#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <DHT.h>
#include <math.h>

// Configure a separate isolated LAN broker and WiFi locally; never commit secrets.
const char* WIFI_SSID = "REPLACE_LOCALLY";
const char* WIFI_PASSWORD = "REPLACE_LOCALLY";
const char* BROKER_HOST = "127.0.0.1"; // Placeholder; Node demo is loopback-only.
const uint16_t BROKER_PORT = 18830;
const char* CLIENT_ID = "getlancer-publisher-00000000-0000-4000-8000-000000000001";
const char* TOPIC = "getlancer/00000000-0000-4000-8000-000000000001/telemetry";
const float THRESHOLD_C = 30.0f;
const uint8_t SENSOR_PIN = 4;
DHT sensor(SENSOR_PIN, DHT22);
WiFiClient transport;
PubSubClient mqtt(transport);
unsigned long lastSample = 0;
uint8_t samples = 0, failures = 0;
bool stopped = false;

void stopSafely() { stopped = true; mqtt.disconnect(); WiFi.disconnect(); }
void setup() {
  Serial.begin(115200); sensor.begin();
  if (strcmp(WIFI_SSID, "REPLACE_LOCALLY") == 0 || strcmp(BROKER_HOST, "127.0.0.1") == 0) {
    Serial.println("Configure your optional isolated hardware exercise locally."); stopSafely(); return;
  }
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  unsigned long started = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - started < 10000) delay(100);
  if (WiFi.status() != WL_CONNECTED) { stopSafely(); return; }
  mqtt.setServer(BROKER_HOST, BROKER_PORT); mqtt.setBufferSize(512);
  mqtt.setKeepAlive(30); mqtt.setSocketTimeout(2);
  if (!mqtt.connect(CLIENT_ID)) stopSafely();
}
void loop() {
  if (stopped) { delay(50); return; }
  if (!mqtt.connected() || samples >= 20 || failures >= 3) { stopSafely(); return; }
  mqtt.loop(); if (millis() - lastSample < 2000) return; lastSample = millis();
  float temperature = sensor.readTemperature(), humidity = sensor.readHumidity();
  if (!isfinite(temperature) || !isfinite(humidity) || temperature < -20 || temperature > 80 || humidity < 0 || humidity > 100) { failures++; return; }
  char payload[160];
  int bytes = snprintf(payload, sizeof(payload), "{\"temperatureC\":%.2f,\"humidityPercent\":%.2f,\"thresholdC\":%.2f}", temperature, humidity, THRESHOLD_C);
  if (bytes <= 0 || bytes >= static_cast<int>(sizeof(payload)) || !mqtt.publish(TOPIC, payload, false)) { failures++; return; }
  samples++; Serial.println(temperature >= THRESHOLD_C ? "Threshold flag on; no actuator is connected." : "Threshold flag off; no actuator is connected.");
}
