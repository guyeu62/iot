
package com.mushroom.iot;

import java.io.FileInputStream;
import java.security.KeyStore;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;

public class MqttConfig {

    public static final String BROKER =
            "ssl://localhost:8883";

    // Doc bien moi truong
    public static String getEnv(String name) {

        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Thieu bien moi truong: " + name
            );
        }

        return value;
    }

    // Tao cau hinh ket noi MQTT TLS
    public static MqttConnectOptions createOptions()
            throws Exception {

        String username = getEnv("MQTT_USERNAME");

        String password = getEnv("MQTT_PASSWORD");

        String truststorePath =
                getEnv("MQTT_TRUSTSTORE");

        String truststorePassword =
                getEnv("MQTT_TRUSTSTORE_PASSWORD");

        // Doc truststore
        KeyStore truststore = KeyStore.getInstance("JKS");

        try (FileInputStream input =
                     new FileInputStream(truststorePath)) {

            truststore.load(
                    input,
                    truststorePassword.toCharArray()
            );
        }

        // Xac minh chung chi Broker
        TrustManagerFactory factory =
                TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm()
                );

        factory.init(truststore);

        // Khoi tao TLS
        SSLContext sslContext =
                SSLContext.getInstance("TLS");

        sslContext.init(
                null,
                factory.getTrustManagers(),
                null
        );

        // Cau hinh MQTT
        MqttConnectOptions options =
                new MqttConnectOptions();

        options.setUserName(username);

        options.setPassword(password.toCharArray());

        options.setSocketFactory(
                sslContext.getSocketFactory()
        );

        options.setHttpsHostnameVerificationEnabled(true);

        options.setCleanSession(true);

        options.setAutomaticReconnect(true);

        options.setConnectionTimeout(10);

        options.setKeepAliveInterval(30);

        return options;
    }
}
