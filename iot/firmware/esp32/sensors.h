#ifndef SENSORS_H
#define SENSORS_H

#include "config.h"
#include <Arduino.h>
#include <esp_system.h>


struct SensorData {
  float temperature;
  float humidity;
  int co2;
};

void initSensors() {

  randomSeed(esp_random());

  Serial.println("Sensors initialized");
  Serial.println("SIMULATION MODE ENABLED");
}

float getTemperature() { return random(230, 301) / 10.0; }

float getHumidity() { return random(750, 951) / 10.0; }

int getCO2() { return random(400, 1201); }

SensorData readSensors() {

  SensorData data;

  data.temperature = getTemperature();
  data.humidity = getHumidity();
  data.co2 = getCO2();

  return data;
}

void printSensorData(SensorData data) {

  Serial.println("========================");

  Serial.print("Temperature: ");
  Serial.print(data.temperature);
  Serial.println(" C");

  Serial.print("Humidity: ");
  Serial.print(data.humidity);
  Serial.println(" %RH");

  Serial.print("CO2: ");
  Serial.print(data.co2);
  Serial.println(" ppm");

  Serial.println("========================");
}

#endif