package com.happysg.radar.debug;

public enum DiagnosticHealth {
    HEALTHY,
    DEGRADED,
    FAILED;

    public static DiagnosticHealth from(DiagnosticSeverity severity) {
        return switch (severity) {
            case INFO -> HEALTHY;
            case WARN -> DEGRADED;
            case ERROR -> FAILED;
        };
    }

    public DiagnosticHealth combine(DiagnosticHealth other) {
        return ordinal() >= other.ordinal() ? this : other;
    }
}
