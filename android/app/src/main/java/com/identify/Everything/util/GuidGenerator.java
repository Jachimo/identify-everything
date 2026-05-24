package com.identify.Everything.util;

/**
 * Generates Base26 encoded GUIDs offline-first for item identification.
 * Uses FreeBSD-5 convention (a=0, z=25) for scannable, human-readable codes.
 *
 * Example: UUID -> Base26: 00000000-0000-0000-0000-000000000000 ->
 *          3k7x9b_p1j4_nv6d_abc12_def3d_ghi4e_jkl5f
 */
public class GuidGenerator {

    private static final String BASE26 = "0123456789abcdefghijklmnopqrstuv";

    /**
     * Generate a random Base26-encoded GUID (28 characters)
     */
    public static String generateGuid() {
        String uuid = java.util.UUID.randomUUID().toString();
        return encodeUuidToBase26(uuid);
    }

    /**
     * Encode a UUID string to Base26 with visual separators
     * @param uuid UUID in format "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
     * @return Base26-encoded GUID with underscores (28 characters)
     */
    public static String encodeUuidToBase26(String uuid) {
        // Remove hyphens
        uuid = uuid.replace("-", "");

        // Take 128 bits (32 hex chars) (UUID4 generates 128-bit UUID)
        String hex = uuid.substring(0, 32);

        // Encoding: uppercase letters a-p (0-15) + digits (16-25)
        // We'll map: 0=0, 1=1, ..., 9=9, a=10, b=11, ..., p=25
        StringBuilder base26 = new StringBuilder();

        for (int i = 0; i < hex.length() - 1; i += 2) {
            int first = Integer.parseInt(hex.substring(i, i + 1), 16);
            int second = Integer.parseInt(hex.substring(i + 1, i + 2), 16);

            int combined = (first << 4) | second;
            base26.append(BASE26.charAt(combined % 26));
        }

        // Add underscores every 4 characters
        return insertSeparators(base26.toString());
    }

    /**
     * Add underscores every 4 characters for visual readability
     */
    private static String insertSeparators(String base26) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < base26.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                result.append('_');
            }
            result.append(base26.charAt(i));
        }
        return result.toString();
    }

    /**
     * Base26 pattern: exactly 28 characters with underscores every 4
     */
    public static final Pattern GUID_PATTERN = Pattern.compile("^[a-p0-9]{28}$");

    /**
     * Validate a Base26 GUID
     */
    public static boolean isValidGuid(String guid) {
        if (guid == null) return false;
        return GUID_PATTERN.matcher(guid).matches();
    }

    /**
     * Extract GUID from encoded URL
     * URL: https://{domain}/objects/v1/3k7x9b_p1j4_nv6d
     * GUID: 3k7x9b_p1j4_nv6d_abc12_def3d_ghi4e_jkl5f
     */
    public static String extractGuidFromUrl(String url) {
        if (url == null) return null;

        // Parse URL, extract path component
        try {
            int index = url.indexOf("/objects/v1/");
            if (index >= 0) {
                String path = url.substring(index + "/objects/v1/".length());
                // Remove trailing slash if present
                if (path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }
                return path;
            }
        } catch (Exception e) {
            // Parse error
        }

        return null;
    }
}
