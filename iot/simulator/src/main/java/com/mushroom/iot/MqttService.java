
package com.mushroom.iot;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class MqttService {

    // Cau hinh MQTT TLS
    private static final String BROKER =
            MqttConfig.BROKER;

    private static final String DEVICE_ID =
            MqttConfig.getEnv("MQTT_USERNAME");

    private static final String BASE_TOPIC =
            "mushroom/" + DEVICE_ID;

    private static final String TELEMETRY_TOPIC =
            BASE_TOPIC + "/telemetry";

    private static final String COMMAND_TOPIC =
            BASE_TOPIC + "/command";

    private static final String ACK_TOPIC =
            BASE_TOPIC + "/ack";

    private static final String STATUS_TOPIC =
            BASE_TOPIC + "/status";

    private final MqttClient client;

    private final DeviceController controller =
            new DeviceController();

    // Luu cac command da xu ly
    private final Map<String, JsonObject> processedCommands =
            new HashMap<>();

    public MqttService() throws MqttException {

        client = new MqttClient(
                BROKER,
                DEVICE_ID,
                null
        );
    }

    // Ket noi MQTT TLS
    public void connect() throws Exception {

        MqttConnectOptions options =
                MqttConfig.createOptions();

        // Last Will: OFFLINE khi mat ket noi
        JsonObject offline = createStatus("OFFLINE");

        options.setWill(
                STATUS_TOPIC,
                offline.toString().getBytes(StandardCharsets.UTF_8),
                1,
                true
        );

        client.setCallback(new MqttCallbackExtended() {

            @Override
            public void connectComplete(
                    boolean reconnect,
                    String serverURI) {

                try {

                    client.subscribe(COMMAND_TOPIC, 1);

                    System.out.println(
                            "Da dang ky topic: " + COMMAND_TOPIC
                    );

                    sendStatus("ONLINE");

                    if (reconnect) {
                        System.out.println("Da ket noi lai MQTT");
                    }

                } catch (MqttException e) {

                    System.out.println(
                            "Loi MQTT: " + e.getMessage()
                    );
                }
            }

            @Override
            public void connectionLost(Throwable cause) {

                System.out.println("Mat ket noi MQTT");

                if (cause != null) {
                    System.out.println(
                            "Nguyen nhan: " + cause.getMessage()
                    );
                }
            }

            @Override
            public void messageArrived(
                    String topic,
                    MqttMessage message) {

                String json = new String(
                        message.getPayload(),
                        StandardCharsets.UTF_8
                );

                System.out.println("Da nhan lenh: " + json);

                handleCommand(json);
            }

            @Override
            public void deliveryComplete(
                    IMqttDeliveryToken token) {
            }
        });

        client.connect(options);

        System.out.println("============================");
        System.out.println("DA KET NOI MQTT TLS");
        System.out.println("Broker: " + BROKER);
        System.out.println("Device ID: " + DEVICE_ID);
        System.out.println("============================");

        controller.showDevices();
    }

    // Gui du lieu cam bien
    public void sendTelemetry() throws MqttException {

        if (!client.isConnected()) {

            System.out.println("MQTT chua ket noi!");

            return;
        }

        JsonObject data = new JsonObject();

        data.addProperty("deviceId", DEVICE_ID);

        data.addProperty(
                "messageId",
                UUID.randomUUID().toString()
        );

        data.addProperty(
                "measuredAt",
                Instant.now().toString()
        );

        // Nhiet do
        JsonObject temperature = new JsonObject();

        temperature.addProperty(
                "value",
                Sensor.getTemperature()
        );

        temperature.addProperty("unit", "°C");

        // Do am
        JsonObject humidity = new JsonObject();

        humidity.addProperty(
                "value",
                Sensor.getHumidity()
        );

        humidity.addProperty("unit", "%RH");

        // Nong do CO2
        JsonObject co2 = new JsonObject();

        co2.addProperty(
                "value",
                Sensor.getCO2()
        );

        co2.addProperty("unit", "ppm");

        data.add("temperature", temperature);
        data.add("humidity", humidity);
        data.add("co2", co2);

        publish(TELEMETRY_TOPIC, data, false);

        System.out.println("Da gui telemetry: " + data);
    }

    // Xu ly lenh dieu khien
    private void handleCommand(String json) {

        String commandId;

        JsonObject command;

        try {

            command = JsonParser
                    .parseString(json)
                    .getAsJsonObject();

            commandId = command
                    .get("commandId")
                    .getAsString();

            if (commandId.isBlank()) {

                throw new IllegalArgumentException(
                        "commandId khong hop le"
                );
            }

        } catch (Exception e) {

            System.out.println(
                    "Lenh khong co commandId hop le"
            );

            return;
        }

        // Kiem tra command trung lap
        if (processedCommands.containsKey(commandId)) {

            System.out.println(
                    "Lenh trung lap: " + commandId
            );

            sendAck(processedCommands.get(commandId));

            return;
        }

        JsonObject ack = new JsonObject();

        ack.addProperty("deviceId", DEVICE_ID);

        ack.addProperty("commandId", commandId);

        try {

            String device = command
                    .get("device")
                    .getAsString();

            String action = command
                    .get("action")
                    .getAsString();

            boolean state = controller.control(
                    device,
                    action
            );

            ack.addProperty("success", true);

            ack.addProperty("device", device);

            ack.addProperty(
                    "state",
                    state ? "ON" : "OFF"
            );

            controller.showDevices();

        } catch (Exception e) {

            ack.addProperty("success", false);

            ack.addProperty("reason", e.getMessage());
        }

        ack.addProperty(
                "timestamp",
                Instant.now().toString()
        );

        // Luu ACK de xu ly command trung lap
        processedCommands.put(commandId, ack);

        sendAck(ack);
    }

    // Gui ACK
    private void sendAck(JsonObject ack) {

        try {

            publish(ACK_TOPIC, ack, false);

            System.out.println("Da gui ACK: " + ack);

        } catch (MqttException e) {

            System.out.println(
                    "Loi gui ACK: " + e.getMessage()
            );
        }
    }

    // Tao trang thai thiet bi
    private JsonObject createStatus(String status) {

        JsonObject data = new JsonObject();

        data.addProperty("deviceId", DEVICE_ID);

        data.addProperty("status", status);

        return data;
    }

    // Gui ONLINE/OFFLINE
    private void sendStatus(String status)
            throws MqttException {

        publish(
                STATUS_TOPIC,
                createStatus(status),
                true
        );

        System.out.println("Trang thai MQTT: " + status);
    }

    // Ham publish MQTT dung chung
    private void publish(
            String topic,
            JsonObject data,
            boolean retained) throws MqttException {

        MqttMessage message = new MqttMessage(
                data.toString().getBytes(
                        StandardCharsets.UTF_8
                )
        );

        message.setQos(1);

        message.setRetained(retained);

        client.publish(topic, message);
    }

    // Ngat ket noi MQTT
    public void disconnect() {

        try {

            if (client.isConnected()) {

                try {

                    sendStatus("OFFLINE");

                } finally {

                    client.disconnect();
                }
            }

            client.close();

            System.out.println("Da ngat ket noi MQTT");

        } catch (MqttException e) {

            System.out.println(
                    "Loi MQTT: " + e.getMessage()
            );
        }
    }
}
