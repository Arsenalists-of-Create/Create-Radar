package com.happysg.radar.debug;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

public final class DiagnosticPrivacy {
    private static final Pattern UUID = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b");
    private static final Pattern WINDOWS_PATH = Pattern.compile(
            "(?i)\\b[a-z]:\\\\(?:[^\\r\\n\\t<>|\"']+)");
    private static final Pattern UNIX_USER_PATH = Pattern.compile(
            "(?i)(?:/home/|/users/|/tmp/|/var/tmp/)[^\\r\\n\\t \"']+");
    private static final Pattern IPV4 = Pattern.compile(
            "\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?\\b");
    private static final Pattern ASSIGNED_SECRET = Pattern.compile(
            "(?i)\\b(secret|password|token|credential)(\\s*[=:]\\s*)([^,;\\s]+)");

    private DiagnosticPrivacy() {
    }

    public static String redact(@Nullable String input) {
        if (input == null || input.isEmpty()) return input == null ? "" : input;
        String value = UUID.matcher(input).replaceAll("<uuid>");
        value = WINDOWS_PATH.matcher(value).replaceAll("<path>");
        value = UNIX_USER_PATH.matcher(value).replaceAll("<path>");
        value = IPV4.matcher(value).replaceAll("<address>");
        return ASSIGNED_SECRET.matcher(value)
                .replaceAll("$1$2[redacted]");
    }
}
