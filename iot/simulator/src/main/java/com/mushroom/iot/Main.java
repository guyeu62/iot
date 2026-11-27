
package com.mushroom.iot;

public class Main {

    public static void main(String[] args) {

        System.out.println(
                "===== MUSHROOM IOT SIMULATOR ====="
        );

        try {

            MqttService mqtt = new MqttService();

            // Ket noi HiveMQ
            mqtt.connect();

            // Ngat ket noi khi dung chuong trinh
            Runtime.getRuntime().addShutdownHook(
                    new Thread(mqtt::disconnect)
            );

            while (true) {

                try {

                    // Gui du lieu cam bien
                    mqtt.sendTelemetry();

                } catch (Exception e) {

                    System.out.println(
                            "Loi gui du lieu: "
                            + e.getMessage()
                    );
                }

                // Cho 30 giay
                Thread.sleep(30000);
            }

        } catch (Exception e) {

            System.out.println(
                    "Loi chuong trinh: "
                    + e.getMessage()
            );
        }
    }
}
