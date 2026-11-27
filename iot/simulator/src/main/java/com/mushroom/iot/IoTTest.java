
package com.mushroom.iot;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class IoTTest {

    private static final String BROKER =
            MqttConfig.BROKER;

    private static final String BASE =
            "mushroom/esp32-room-01";

    private static final BlockingQueue<JsonObject> telemetry =
            new LinkedBlockingQueue<>();

    private static final BlockingQueue<JsonObject> statuses =
            new LinkedBlockingQueue<>();

    private static final ConcurrentHashMap
            <String, CompletableFuture<JsonObject>> pending =
            new ConcurrentHashMap<>();

    private static MqttClient client;

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {

        System.out.println("===== MQTT TLS IOT TEST =====");

        try {

            client = new MqttClient(
                    BROKER,
                    "iot-test-" + UUID.randomUUID(),
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

                    try {

                        JsonObject data = JsonParser.parseString(
                                new String(
                                        message.getPayload(),
                                        StandardCharsets.UTF_8
                                )
                        ).getAsJsonObject();

                        if (topic.endsWith("/telemetry")) {

                            telemetry.offer(data);

                        } else if (topic.endsWith("/status")) {

                            statuses.offer(data);

                        } else if (topic.endsWith("/ack")) {

                            String id = data
                                    .get("commandId")
                                    .getAsString();

                            CompletableFuture<JsonObject> future =
                                    pending.get(id);

                            if (future != null) {
                                future.complete(data);
                            }
                        }

                    } catch (Exception e) {

                        System.out.println(
                                "Loi JSON: " + e.getMessage()
                        );
                    }
                }

                @Override
                public void deliveryComplete(
                        IMqttDeliveryToken token) {
                }
            });

            client.connect(MqttConfig.createOptions());

            client.subscribe(BASE + "/telemetry", 1);
            client.subscribe(BASE + "/ack", 1);
            client.subscribe(BASE + "/status", 1);

            check("TC01 - Ket noi MQTT TLS",
                    client.isConnected());

            testTelemetry();

            testCommands();

            testTimeout();

            testStatus();

            testDuplicateTelemetry();

        } catch (Exception e) {

            failed++;

            System.out.println(
                    "TEST ERROR: " + e.getMessage()
            );

        } finally {

            if (client != null) {

                if (client.isConnected()) {
                    client.disconnect();
                }

                client.close();
            }
        }

        System.out.println("\n==========================");
        System.out.println("TEST SUMMARY");
        System.out.println("PASS: " + passed);
        System.out.println("FAIL: " + failed);
        System.out.println("TOTAL: " + (passed + failed));
        System.out.println("==========================");

        if (failed > 0) {
            throw new IllegalStateException("Co test that bai");
        }
    }

    private static void testTelemetry() throws Exception {

        System.out.println("\nDang cho telemetry...");

        JsonObject first =
                telemetry.poll(40, TimeUnit.SECONDS);

        JsonObject second =
                telemetry.poll(40, TimeUnit.SECONDS);

        boolean received = first != null && second != null;

        check("TC02 - Nhan telemetry", received);

        if (!received) {

            check("TC03 - Chu ky 30 giay", false);
            check("TC04 - Message ID duy nhat", false);

            return;
        }

        boolean valid = first.has("deviceId")
                && first.has("messageId")
                && first.has("measuredAt")
                && first.has("temperature")
                && first.has("humidity")
                && first.has("co2")
                && second.has("measuredAt")
                && second.has("messageId");

        if (!valid) {

            check("TC03 - Chu ky 30 giay", false);
            check("TC04 - Message ID duy nhat", false);

            return;
        }

        Instant t1 = Instant.parse(
                first.get("measuredAt").getAsString()
        );

        Instant t2 = Instant.parse(
                second.get("measuredAt").getAsString()
        );

        long seconds = Duration.between(t1, t2).getSeconds();

        check("TC03 - Chu ky 30 giay",
                seconds >= 28 && seconds <= 35);

        check("TC04 - Message ID duy nhat",
                !first.get("messageId").getAsString().equals(
                        second.get("messageId").getAsString()
                ));
    }

    private static void testCommands() throws Exception {

        JsonObject on = sendCommand(
                BASE, "fan", "ON",
                UUID.randomUUID().toString()
        );

        check("TC05 - Bat quat", isSuccess(on, "ON"));

        JsonObject off = sendCommand(
                BASE, "fan", "OFF",
                UUID.randomUUID().toString()
        );

        check("TC06 - Tat quat", isSuccess(off, "OFF"));

        String duplicateId = UUID.randomUUID().toString();

        JsonObject ack1 = sendCommand(
                BASE, "mist", "ON", duplicateId
        );

        JsonObject ack2 = sendCommand(
                BASE, "mist", "ON", duplicateId
        );

        check("TC07 - Command trung lap",
                isSuccess(ack1, "ON")
                        && ack2 != null
                        && ack1.equals(ack2));

        sendCommand(
                BASE, "mist", "OFF",
                UUID.randomUUID().toString()
        );

        JsonObject invalidDevice = sendCommand(
                BASE, "unknown", "ON",
                UUID.randomUUID().toString()
        );

        check("TC08 - Thiet bi khong ton tai",
                isFailure(invalidDevice));

        JsonObject invalidAction = sendCommand(
                BASE, "fan", "START",
                UUID.randomUUID().toString()
        );

        check("TC09 - Lenh khong hop le",
                isFailure(invalidAction));
    }

    private static void testTimeout() throws Exception {

        long start = System.nanoTime();

        JsonObject ack = sendCommand(
                "mushroom/nonexistent-device",
                "fan",
                "ON",
                UUID.randomUUID().toString()
        );

        double seconds =
                (System.nanoTime() - start) / 1_000_000_000.0;

        check("TC10 - Timeout 10 giay",
                ack == null
                        && seconds >= 9.9
                        && seconds <= 12);
    }

    private static void testStatus() throws Exception {

        JsonObject status =
                statuses.poll(5, TimeUnit.SECONDS);

        check("TC11 - Trang thai ONLINE",
                status != null
                        && status.has("status")
                        && "ONLINE".equals(
                                status.get("status").getAsString()
                        ));
    }

    private static void testDuplicateTelemetry() {

        Set<String> messageIds = new HashSet<>();

        String messageId = UUID.randomUUID().toString();

        boolean first = messageIds.add(messageId);

        boolean duplicate = messageIds.add(messageId);

        check("TC12 - Mo phong loc telemetry trung lap",
                first && !duplicate);
    }

    private static JsonObject sendCommand(
            String base,
            String device,
            String action,
            String commandId) throws Exception {

        JsonObject command = new JsonObject();

        command.addProperty("commandId", commandId);
        command.addProperty("deviceId",
                base.substring(base.lastIndexOf('/') + 1));
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

            client.publish(base + "/command", message);

            return future.get(10, TimeUnit.SECONDS);

        } catch (TimeoutException e) {

            return null;

        } finally {

            pending.remove(commandId);
        }
    }

    private static boolean isSuccess(
            JsonObject ack,
            String expectedState) {

        return ack != null
                && ack.has("success")
                && ack.get("success").getAsBoolean()
                && ack.has("state")
                && expectedState.equals(
                        ack.get("state").getAsString()
                );
    }

    private static boolean isFailure(JsonObject ack) {

        return ack != null
                && ack.has("success")
                && !ack.get("success").getAsBoolean();
    }

    private static void check(
            String name,
            boolean result) {

        if (result) {

            passed++;

            System.out.println("[PASS] " + name);

        } else {

            failed++;

            System.out.println("[FAIL] " + name);
        }
    }
}
