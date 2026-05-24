package io.github.jachimo.identifyeverything.util;

import java.util.Random;

/**
 * Generates and validates Base26-encoded GUIDs.
 * <p>
 * Format: {@code xxxx_xxxx_xxxx_xxxx} where each character is {@code [a-p0-9]}.
 * This is a compact, scannable 19-character representation (16 data chars + 3 underscores).
 */
public final class GuidGenerator {

    private static final String BASE26 = "abcdefghijklmnop0123456789";
    private static final int CHARS_PER_BLOCK = 4;
    private static final int BLOCKS = 4;
    private static final String SEPARATOR = "_";

    private static final String GUID_PATTERN = "^[a-p0-9]{4}_[a-p0-9]{4}_[a-p0-9]{4}_[a-p0-9]{4}$";
    private static final String GUID_PATTERN_NOSEPS = "^[a-p0-9]{16}$";

    private static final Random RANDOM = new Random();

    private GuidGenerator() {
        // Utility class
    }

    /**
     * Generates a new random Base26 GUID string.
     */
    public static String generate() {
        StringBuilder sb = new StringBuilder();
        for (int block = 0; block < BLOCKS; block++) {
            if (block > 0) {
                sb.append(SEPARATOR);
            }
            for (int i = 0; i < CHARS_PER_BLOCK; i++) {
                sb.append(BASE26.charAt(RANDOM.nextInt(BASE26.length())));
            }
        }
        return sb.toString();
    }

    /**
     * Validates a GUID string in the format {@code xxxx_xxxx_xxxx_xxxx}
     * or a plain 16-character string without separators.
     */
    public static boolean isValid(String guid) {
        if (guid == null || guid.isEmpty()) {
            return false;
        }
        // Accept with or without separators
        if (guid.matches(GUID_PATTERN)) {
            return true;
        }
        if (guid.matches(GUID_PATTERN_NOSEPS)) {
            return true;
        }
        return false;
    }

    /**
     * Normalizes a GUID string by ensuring it uses the standard format
     * with underscores (inserts them if missing).
     */
    public static String normalize(String guid) {
        if (guid == null) return null;
        String stripped = guid.replace("_", "");
        if (stripped.length() != 16) return guid;
        return stripped.substring(0, 4) + "_"
                + stripped.substring(4, 8) + "_"
                + stripped.substring(8, 12) + "_"
                + stripped.substring(12, 16);
    }
}