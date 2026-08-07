package com.happysg.radar.debug;

public record DiagnosticEntry(
        String key,
        String value,
        DiagnosticSeverity severity
) {
    public static final int MAX_KEY_LENGTH = 80;
    public static final int MAX_VALUE_LENGTH = 240;

    public DiagnosticEntry {
        key = DiagnosticRecorder.sanitize(key, MAX_KEY_LENGTH);
        value = DiagnosticRecorder.sanitize(value, MAX_VALUE_LENGTH);
        severity = severity == null ? DiagnosticSeverity.INFO : severity;
        String normalizedKey = key.toLowerCase(java.util.Locale.ROOT);
        if (normalizedKey.contains("secret")
                || normalizedKey.contains("password")
                || normalizedKey.contains("token")
                || normalizedKey.contains("credential")) {
            value = "[redacted]";
        }
    }
}
