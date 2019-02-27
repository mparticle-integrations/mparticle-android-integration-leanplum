package com.leanplum;


public class Leanplum {
    private static LeanplumDeviceIdMode mode;
    private static String deviceId;

    public static void setDeviceIdMode(LeanplumDeviceIdMode mode) {
        Leanplum.mode = mode;
    }

    public static LeanplumDeviceIdMode getMode() {
        return mode;
    }

    public static void setDeviceId(String deviceId) {
        Leanplum.deviceId = deviceId;
    }

    public static String getDeviceId() {
        return deviceId;
    }

    public static void clear() {
        mode = null;
        deviceId = null;
    }
}
