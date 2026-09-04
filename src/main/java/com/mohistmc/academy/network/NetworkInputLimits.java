package com.mohistmc.academy.network;

/** Shared pure-Java validation for bounded client-controlled text fields. */
public final class NetworkInputLimits {
    public static final int SSID = 32;
    public static final int NODE_NAME = 32;
    public static final int PASSWORD = 64;
    private NetworkInputLimits() {}

    public static boolean validRequired(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max;
    }

    public static boolean validOptional(String value, int max) {
        return value == null || value.length() <= max;
    }
}
