package com.happysg.radar.debug;

import java.util.List;

public record DiagnosticEvent(
        long sequence,
        DiagnosticSeverity severity,
        String subsystem,
        String operation,
        String reason,
        String exceptionType,
        String exceptionMessage,
        String stackFingerprint,
        String stackTrace,
        String side,
        String dimension,
        String position,
        List<String> implicatedMods,
        long firstEpochMillis,
        long lastEpochMillis,
        long firstTick,
        long lastTick,
        long occurrences
) {
    public DiagnosticEvent {
        implicatedMods = List.copyOf(implicatedMods);
    }
}
