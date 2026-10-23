package com.claude.cameo.bridge.util;

/** Lowercase digest encoding with the same bytes on Java 11 and Java 17. */
public final class Hex {
    private static final char[] DIGITS = "0123456789abcdef".toCharArray();
    private Hex() {}
    public static String format(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            result[i * 2] = DIGITS[(bytes[i] & 255) >>> 4];
            result[i * 2 + 1] = DIGITS[bytes[i] & 15];
        }
        return new String(result);
    }
}
