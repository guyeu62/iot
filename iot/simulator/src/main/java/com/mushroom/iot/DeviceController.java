
package com.mushroom.iot;

import java.util.HashMap;
import java.util.Map;

public class DeviceController {

    private final Map<String, Boolean> devices =
            new HashMap<>();

    // Khoi tao trang thai thiet bi
    public DeviceController() {

        devices.put("fan", false);
        devices.put("mist", false);
        devices.put("light", false);
        devices.put("heater", false);
    }

    // Dieu khien thiet bi
    public synchronized boolean control(
            String device, String action) {

        if (!devices.containsKey(device)) {
            throw new IllegalArgumentException(
                    "Thiet bi khong ton tai: " + device
            );
        }

        if (!"ON".equals(action)
                && !"OFF".equals(action)) {

            throw new IllegalArgumentException(
                    "Lenh khong hop le: " + action
            );
        }

        boolean state = action.equals("ON");

        devices.put(device, state);

        System.out.println("----------------------------");
        System.out.println("Thiet bi: " + device);
        System.out.println("Trang thai: " + action);
        System.out.println("----------------------------");

        return state;
    }

    // Lay trang thai cua mot thiet bi
    public synchronized boolean getState(String device) {

        if (!devices.containsKey(device)) {
            throw new IllegalArgumentException(
                    "Thiet bi khong ton tai"
            );
        }

        return devices.get(device);
    }

    // Hien thi tat ca thiet bi
    public synchronized void showDevices() {

        System.out.println("===== DEVICE STATUS =====");

        for (String device : devices.keySet()) {

            String status = devices.get(device)
                    ? "ON" : "OFF";

            System.out.println(device + ": " + status);
        }
    }
}
