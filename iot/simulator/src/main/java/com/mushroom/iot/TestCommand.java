
package com.mushroom.iot;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class TestCommand {

    private static final String BROKER =
            MqttConfig.BROKER;

    private static final String DEVICE_ID =
            "esp32-room-01";

    private static final String BASE_TOPIC =
            "mushroom/" + DEVICE_ID;

    private static final String COMMAND_TOPIC =
            BASE_TOPIC + "/command";

    private static final String ACK_TOPIC =
            BASE_TOPIC + "/ack";

    private static final String STATUS_TOPIC =
            BASE_TOPIC + "/status";

    private static final ConcurrentHashMap
            <String, CompletableFuture<JsonObject>> pending =
            new ConcurrentHashMap<>();

    public static void main(String[] args) {

        MqttClient client = null;

        Scanner scanner = new Scanner(System.in);

        try {

            client = new MqttClient(
                    BROKER,
                    "backend-" + UUID.randomUUID(),
                    null
            );

            client.setCallback(new MqttCallback() {

                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("Mat ket noi MQTT");
                }

                @Override
                public void messageArrived(
                        String topic,
                        MqttMessage message) {

                    String json = new String(
                            message.getPayload(),
                            StandardCharsets.UTF_8
                    );

                    try {

                        JsonObject data = JsonParser
                                .parseString(json)
                                .getAsJsonObject();

                        if (topic.equals(STATUS_TOPIC)) {

                            System.out.println(
                                    "\nTrang thai thiet bi: "
                                            + data
                            );

                        } else if (topic.equals(ACK_TOPIC)) {

                            String commandId = data
                                    .get("commandId")
                                    .getAsString();

                            CompletableFuture<JsonObject> future =
                                    pending.get(commandId);

                            if (future != null) {
                                future.complete(data);
                            }
                        }

                    } catch (Exception e) {

                        System.out.println(
                                "Loi doc MQTT: " + e.getMessage()
                        );
                    }
                }

                @Override
                public void deliveryComplete(
                        IMqttDeliveryToken token) {
                }
            });

            MqttConnectOptions options =
                    MqttConfig.createOptions();

            client.connect(options);

            client.subscribe(ACK_TOPIC, 1);
            client.subscribe(STATUS_TOPIC, 1);

            System.out.println("==========================");
            System.out.println("BACKEND MQTT TLS CONNECTED");
            System.out.println("Broker: " + BROKER);
            System.out.println("==========================");

            while (true) {

                System.out.println("\n===== DEVICE CONTROL =====");
                System.out.println("1. Fan");
                System.out.println("2. Mist");
                System.out.println("3. Light");
                System.out.println("4. Heater");
                System.out.println("0. Exit");
                System.out.print("Lua chon: ");

                String choice = scanner.nextLine();

                if (choice.equals("0")) {
                    break;
                }

                String device;

                switch (choice) {

                    case "1":
                        device = "fan";
                        break;

                    case "2":
                        device = "mist";
                        break;

                    case "3":
                        device = "light";
                        break;

                    case "4":
                        device = "heater";
                        break;

                    default:
                        System.out.println("Lua chon khong hop le");
                        continue;
                }

                System.out.print("Nhap ON hoac OFF: ");

                String action = scanner
                        .nextLine()
                        .trim()
                        .toUpperCase();

                if (!action.equals("ON")
                        && !action.equals("OFF")) {

                    System.out.println("Lenh khong hop le");

                    continue;
                }

                String commandId =
                        UUID.randomUUID().toString();

                JsonObject command = new JsonObject();

                command.addProperty("commandId", commandId);
                command.addProperty("deviceId", DEVICE_ID);
                command.addProperty("device", device);
                command.addProperty("action", action);

                CompletableFuture<JsonObject> future =
                        new CompletableFuture<>();

                pending.put(commandId, future);

                try {

                    MqttMessage message = new MqttMessage(
                            command.toString().getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

                    message.setQos(1);
                    message.setRetained(false);

                    client.publish(COMMAND_TOPIC, message);

                    System.out.println("Da gui lenh: " + command);

                    JsonObject ack = future.get(
                            10,
                            TimeUnit.SECONDS
                    );

                    System.out.println("ACK: " + ack);

                    if (ack.get("success").getAsBoolean()) {

                        System.out.println(
                                "DIEU KHIEN THANH CONG"
                        );

                    } else {

                        System.out.println(
                                "DIEU KHIEN THAT BAI"
                        );
                    }

                } catch (TimeoutException e) {

                    System.out.println(
                            "TIMEOUT: Khong nhan ACK sau 10 giay"
                    );

                } finally {

                    pending.remove(commandId);
                }
            }

        } catch (Exception e) {

            System.out.println(
                    "Loi chuong trinh: " + e.getMessage()
            );

        } finally {

            scanner.close();

            if (client != null) {

                try {

                    if (client.isConnected()) {
                        client.disconnect();
                    }

                    client.close();

                } catch (MqttException e) {

                    System.out.println(e.getMessage());
                }
            }
        }
    }
}
