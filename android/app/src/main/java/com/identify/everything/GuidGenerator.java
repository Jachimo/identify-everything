package com.identify.everything;

import java.util.UUID;

public class GuidGenerator {

    private static final String BASE26 = "0123456789abcdefghijklmnopqrstuv";

    public static String generate() {
        String hex = UUID.randomUUID().toString().replace("-", "");
        StringBuilder base26 = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 2) {
            int val = Integer.parseInt(hex.substring(i, i + 2), 16);
            base26.append(BASE26.charAt(val % 32));
        }
        StringBuilder withSeparators = new StringBuilder();
        for (int i = 0; i < base26.length(); i += 4) {
            if (withSeparators.length() > 0) withSeparators.append('_');
            withSeparators.append(base26, i, Math.min(i + 4, base26.length()));
        }
        return withSeparators.toString();
    }

    public static boolean isValid(String guid) {
        if (guid == null) return false;
        String stripped = guid.replace("_", "");
        if (stripped.length() != 16) return false;
        for (char c : stripped.toCharArray()) {
            if (BASE26.indexOf(c) == -1) return false;
        }
        return true;
    }

    public static String buildUrl(String domain, String guid) {
        return "https://" + domain + "/objects/v1/" + guid;
    }
}
