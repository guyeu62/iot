#ifndef CONFIG_H
#define CONFIG_H

#define SIMULATION_MODE 1

#define WIFI_SSID "YOUR_WIFI_NAME"
#define WIFI_PASSWORD "YOUR_WIFI_PASSWORD"

#define MQTT_HOST "YOUR_BROKER_IP"
#define MQTT_PORT 8883

#define MQTT_USERNAME "YOUR_DEVICE_USERNAME"
#define MQTT_PASSWORD "YOUR_DEVICE_PASSWORD"

#define DEVICE_ID "esp32-room-01"

#define TELEMETRY_TOPIC "mushroom/" DEVICE_ID "/telemetry"
#define COMMAND_TOPIC "mushroom/" DEVICE_ID "/command"
#define ACK_TOPIC "mushroom/" DEVICE_ID "/ack"
#define STATUS_TOPIC "mushroom/" DEVICE_ID "/status"

#define TELEMETRY_INTERVAL 30000UL
#define MQTT_RECONNECT_INTERVAL 5000UL

#define MQTT_QOS 1

#define DHT_PIN 4

#define FAN_PIN 25
#define MIST_PIN 26
#define LIGHT_PIN 27
#define HEATER_PIN 33

#define I2C_SDA 21
#define I2C_SCL 22

#endif