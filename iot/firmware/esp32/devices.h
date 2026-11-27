#ifndef DEVICES_H
#define DEVICES_H

#include "config.h"
#include <Arduino.h>


bool fanState = false;
bool mistState = false;
bool lightState = false;
bool heaterState = false;

void initDevices() {

  fanState = false;
  mistState = false;
  lightState = false;
  heaterState = false;

  Serial.println("Devices initialized");
  Serial.println("All devices OFF");
}

bool controlDevice(String device, String action) {

  if (action != "ON" && action != "OFF") {
    return false;
  }

  bool state = (action == "ON");

  if (device == "fan") {
    fanState = state;
  } else if (device == "mist") {
    mistState = state;
  } else if (device == "light") {
    lightState = state;
  } else if (device == "heater") {
    heaterState = state;
  } else {
    return false;
  }

  Serial.print("Device: ");
  Serial.println(device);

  Serial.print("State: ");
  Serial.println(action);

  return true;
}

void showDevices() {

  Serial.println("===== DEVICE STATUS =====");

  Serial.print("Fan: ");
  Serial.println(fanState ? "ON" : "OFF");

  Serial.print("Mist: ");
  Serial.println(mistState ? "ON" : "OFF");

  Serial.print("Light: ");
  Serial.println(lightState ? "ON" : "OFF");

  Serial.print("Heater: ");
  Serial.println(heaterState ? "ON" : "OFF");
}

#endif