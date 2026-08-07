package com.happysg.radar.debug;

public record ConflictEvidence(
        String type,
        int weight,
        String summary,
        String eventFingerprint,
        String targetClass,
        String targetMethod
) {
    public ConflictEvidence {
        type = DiagnosticRecorder.sanitize(type, 80);
        weight = Math.max(0, Math.min(100, weight));
        summary = DiagnosticRecorder.sanitize(summary, 512);
        eventFingerprint = DiagnosticRecorder.sanitize(eventFingerprint, 64);
        targetClass = DiagnosticRecorder.sanitize(targetClass, 240);
        targetMethod = DiagnosticRecorder.sanitize(targetMethod, 240);
    }
}
