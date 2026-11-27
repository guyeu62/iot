
package com.mushroom.iot;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class SecurityTest {

    private static final String BROKER = MqttConfig.BROKER;

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {

        System.out.println("===== MQTT SECURITY TEST =====");

        String password = MqttConfig.getEnv("MQTT_PASSWORD");

        // SEC01: Tai khoan hop le
        check("SEC01 - Tai khoan hop le",
                testLogin("esp32-room-01", password));

        // SEC02: Sai mat khau
        check("SEC02 - Sai mat khau",
                !testLogin("esp32-room-01", "wrong-password"));

        // SEC03: Tai khoan khong ton tai
        check("SEC03 - Tai khoan khong ton tai",
                !testLogin("unknown-device", password));

        // SEC04: Khong co tai khoan
        check("SEC04 - Khong co tai khoan",
                !testLogin(null, null));

        // SEC05: Chung chi khong duoc tin cay
        check("SEC05 - Chung chi khong duoc tin cay",
                testUntrustedCertificate(password));

        // SEC06: Gui telemetry sang phong khac
        check("SEC06 - Cam gui du lieu sang phong khac",
                testDeniedPublish(
                        password,
                        "mushroom/esp32-room-02/telemetry"
                ));

        // SEC07: ESP32 tu gui command
        check("SEC07 - Cam ESP32 gui command",
                testDeniedPublish(
                        password,
                        "mushroom/esp32-room-01/command"
                ));

        // SEC08: Backend duoc phep gui command
        check("SEC08 - Backend gui command",
                testBackendCommand(password));

        // SEC09: Cong MQTT thuong phai dong
        check("SEC09 - Cong 1883 bi dong",
                !isPortOpen(1883));

        // SEC10: Ket noi TLS thanh cong
        check("SEC10 - Ket noi TLS cong 8883",
                isPortOpen(8883)
                        && testLogin("esp32-room-01", password));

        // Tong hop ket qua
        System.out.println("\n============================");
        System.out.println("SECURITY TEST SUMMARY");
        System.out.println("PASS: " + passed);
        System.out.println("FAIL: " + failed);
        System.out.println("TOTAL: " + (passed + failed));
        System.out.println("============================");

        if (failed > 0) {

            throw new IllegalStateException(
                    "Co kiem thu bao mat that bai"
            );
        }
    }

    // ==========================================
    // KIEM TRA DANG NHAP MQTT
    // ==========================================

    private static boolean testLogin(
            String username,
            String password) throws Exception {

        MqttClient client = new MqttClient(
                BROKER,
                "security-" + UUID.randomUUID(),
                null
        );

        try {

            MqttConnectOptions options =
                    MqttConfig.createOptions();

            options.setAutomaticReconnect(false);

            // Sua loi SEC04: Khong truyen null vao setPassword
            if (username == null || password == null) {

                MqttConnectOptions anonymous =
                        new MqttConnectOptions();

                anonymous.setSocketFactory(
                        options.getSocketFactory()
                );

                anonymous.setHttpsHostnameVerificationEnabled(true);
                anonymous.setAutomaticReconnect(false);
                anonymous.setCleanSession(true);
                anonymous.setConnectionTimeout(10);

                options = anonymous;

            } else {

                options.setUserName(username);

                options.setPassword(
                        password.toCharArray()
                );
            }

            client.connect(options);

            return client.isConnected();

        } catch (MqttException e) {

            if (e.getReasonCode() == 4
                    || e.getReasonCode() == 5) {

                System.out.println(
                        "Broker tu choi: " + e.getMessage()
                );

                return false;
            }

            throw e;

        } finally {

            closeClient(client);
        }
    }

    // ==========================================
    // KIEM TRA CHUNG CHI TLS
    // ==========================================

    private static boolean testUntrustedCertificate(
            String password) throws Exception {

        MqttClient client = new MqttClient(
                BROKER,
                "untrusted-" + UUID.randomUUID(),
                null
        );

        try {

            MqttConnectOptions options =
                    new MqttConnectOptions();

            options.setUserName("esp32-room-01");

            options.setPassword(
                    password.toCharArray()
            );

            options.setConnectionTimeout(5);

            options.setAutomaticReconnect(false);

            options.setHttpsHostnameVerificationEnabled(true);

            // Khong su dung truststore cua du an
            client.connect(options);

            return false;

        } catch (MqttException e) {

            Throwable cause = e;

            while (cause != null) {

                if (cause instanceof
                        javax.net.ssl.SSLHandshakeException) {

                    System.out.println(
                            "TLS tu choi chung chi"
                    );

                    return true;
                }

                cause = cause.getCause();
            }

            throw e;

        } finally {

            closeClient(client);
        }
    }

    // ==========================================
    // KIEM TRA PUBLISH TRAI PHEP
    // ==========================================

    private static boolean testDeniedPublish(
            String password,
            String topic) throws Exception {

        return !testMessageDelivery(
                "esp32-room-01",
                password,
                topic
        );
    }

    // ==========================================
    // KIEM TRA BACKEND GUI COMMAND
    // ==========================================

    private static boolean testBackendCommand(
            String password) throws Exception {

        return testMessageDelivery(
                "backend",
                password,
                "mushroom/esp32-room-01/command"
        );
    }

    // ==========================================
    // KIEM TRA GUI VA NHAN BAN TIN MQTT
    // ==========================================

    private static boolean testMessageDelivery(
            String username,
            String password,
            String topic) throws Exception {

        MqttClient observer = new MqttClient(
                BROKER,
                "observer-" + UUID.randomUUID(),
                null
        );

        MqttClient publisher = new MqttClient(
                BROKER,
                "publisher-" + UUID.randomUUID(),
                null
        );

        String marker = UUID.randomUUID().toString();

        CountDownLatch received =
                new CountDownLatch(1);

        try {

            // Xac dinh tai khoan nhan ban tin
            String observerUser =
                    topic.endsWith("/telemetry")
                            ? "backend"
                            : "esp32-room-01";

            // Cau hinh subscriber
            MqttConnectOptions observerOptions =
                    MqttConfig.createOptions();

            observerOptions.setUserName(observerUser);

            observerOptions.setPassword(
                    password.toCharArray()
            );

            observerOptions.setAutomaticReconnect(false);

            observer.connect(observerOptions);

            // Dang ky topic
            observer.subscribe(topic, 1, (t, message) -> {

                String content = new String(
                        message.getPayload(),
                        StandardCharsets.UTF_8
                );

                if (content.equals(marker)) {

                    received.countDown();
                }
            });

            // Cau hinh publisher
            MqttConnectOptions publisherOptions =
                    MqttConfig.createOptions();

            publisherOptions.setUserName(username);

            publisherOptions.setPassword(
                    password.toCharArray()
            );

            publisherOptions.setAutomaticReconnect(false);

            publisher.connect(publisherOptions);

            // Tao ban tin kiem thu
            MqttMessage message = new MqttMessage(
                    marker.getBytes(StandardCharsets.UTF_8)
            );

            message.setQos(1);

            message.setRetained(false);

            try {

                publisher.publish(topic, message);

            } catch (MqttException e) {

                System.out.println(
                        "Publish bi tu choi hoac gap loi: "
                                + e.getMessage()
                );

                // Khong coi moi loi MQTT la phan quyen thanh cong
                if (e.getReasonCode() != 5
                        && e.getReasonCode() !=
                        MqttException.REASON_CODE_CONNECTION_LOST
                        && e.getReasonCode() !=
                        MqttException.REASON_CODE_CLIENT_NOT_CONNECTED) {

                    throw e;
                }
            }

            // Cho nhan ban tin toi da 5 giay
            return received.await(
                    5,
                    TimeUnit.SECONDS
            );

        } finally {

            closeClient(publisher);

            closeClient(observer);
        }
    }

    // ==========================================
    // KIEM TRA CONG MANG
    // ==========================================

    private static boolean isPortOpen(int port) {

        try (Socket socket = new Socket()) {

            socket.connect(
                    new InetSocketAddress(
                            "localhost",
                            port
                    ),
                    2000
            );

            return true;

        } catch (Exception e) {

            return false;
        }
    }

    // ==========================================
    // DONG KET NOI MQTT
    // ==========================================

    private static void closeClient(MqttClient client) {

        try {

            if (client.isConnected()) {

                client.disconnect();
            }

            client.close();

        } catch (MqttException e) {

            System.out.println(
                    "Loi dong ket noi: " + e.getMessage()
            );
        }
    }

    // ==========================================
    // IN KET QUA KIEM THU
    // ==========================================

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
