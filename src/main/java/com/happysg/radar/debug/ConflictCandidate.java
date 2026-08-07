package com.happysg.radar.debug;

import java.util.Comparator;
import java.util.List;

public record ConflictCandidate(
        String modId,
        String version,
        int score,
        ConflictConfidence confidence,
        List<ConflictEvidence> evidence,
        List<String> counterEvidence
) {
    public ConflictCandidate {
        modId = DiagnosticRecorder.sanitize(modId, 80);
        version = DiagnosticRecorder.sanitize(version, 120);
        score = Math.max(0, Math.min(100, score));
        confidence = confidence == null
                ? ConflictConfidence.fromScore(score) : confidence;
        evidence = evidence == null ? List.of() : evidence.stream()
                .sorted(Comparator.comparingInt(ConflictEvidence::weight)
                        .reversed().thenComparing(ConflictEvidence::type))
                .limit(24).toList();
        counterEvidence = counterEvidence == null ? List.of()
                : counterEvidence.stream()
                .map(value -> DiagnosticRecorder.sanitize(value, 240))
                .limit(8).toList();
    }
}
