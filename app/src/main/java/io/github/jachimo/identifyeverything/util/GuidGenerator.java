package io.github.jachimo.identifyeverything.util;

import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Generates Base26 encoded GUIDs offline-first
 */
public class GuidGenerator {

    private static final String BASE26 = "0123456789abcdefghijklmnopqrstuv";

    public static String generateGuid() {
        String uuid = java.util.UUID.randomUUID().toString();
        return encodeUuidToBase26(uuid);
    }

    public static String encodeUuidToBase26(String uuid) {
        uuid = uuid.replace("-", "");
        String hex = uuid.substring(0, 32);

        StringBuilder base26 = new StringBuilder();

        for (int i = 0; i < hex.length() - 1; i += 2) {
            int first = Integer.parseInt(hex.substring(i, i + 1), 16);
            int second = Integer.parseInt(hex.substring(i + 1, i + 2), 16);

            int combined = (first << 4) | second;
            base26.append(BASE26.charAt(combined % 26));
        }

        return insertSeparators(base26.toString());
    }

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

    public static final Pattern GUID_PATTERN = Pattern.compile("^[a-p0-9]{28}$");

    public static boolean isValidGuid(String guid) {
        if (guid == null) return false;
        return GUID_PATTERN.matcher(guid).matches();
    }

    public static String extractGuidFromUrl(String url) {
        if (url == null) return null;

        try {
            int index = url.indexOf("/objects/v1/");
            if (index >= 0) {
                String path = url.substring(index + "/objects/v1/".length());
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
