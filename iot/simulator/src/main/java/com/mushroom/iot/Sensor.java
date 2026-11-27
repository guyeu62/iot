package com.mushroom.iot;

import java.util.Random;

public class Sensor {

    private static final Random random = new Random();

    // Gia lap cam bien nhiet do
    public static double getTemperature() {
        double temperature = 23 + random.nextDouble() * 7;
        return Math.round(temperature * 10.0) / 10.0;
    }

    // Gia lap cam bien do am
    public static double getHumidity() {
        double humidity = 75 + random.nextDouble() * 20;
        return Math.round(humidity * 10.0) / 10.0;
    }

    // Gia lap cam bien CO2
    public static int getCO2() {
        return 400 + random.nextInt(801);
    }
}
