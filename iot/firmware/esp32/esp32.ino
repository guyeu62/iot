#include "config.h"
#include "devices.h"
#include "sensors.h"
#include <Arduino.h>


unsigned long lastTelemetry = 0;

void setup() {

  Serial.begin(115200);

  delay(1000);

  Serial.println("==========================");
  Serial.println("MUSHROOM ESP32 FIRMWARE");
  Serial.println("SIMULATION MODE");
  Serial.println("==========================");

  initSensors();

  initDevices();

  // Kiem tra dieu khien
  controlDevice("fan", "ON");
  controlDevice("mist", "ON");

  showDevices();

  // Doc du lieu cam bien
  SensorData data = readSensors();

  printSensorData(data);

  lastTelemetry = millis();
}

void loop() {

  unsigned long currentTime = millis();

  if (currentTime - lastTelemetry >= TELEMETRY_INTERVAL) {

    lastTelemetry = currentTime;

    SensorData data = readSensors();

    printSensorData(data);

    showDevices();
  }

  delay(10);
}