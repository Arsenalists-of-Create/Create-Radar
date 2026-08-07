package com.happysg.radar.debug;

import java.util.List;

public record ConflictAnalysisSnapshot(
        String status,
        List<ConflictCandidate> candidates,
        List<MixinAuditEntry> appliedMixins,
        List<MixinOverlap> mixinOverlaps,
        List<ClassOwnership> relevantClasses,
        List<String> analysisWarnings
) {
    public ConflictAnalysisSnapshot {
        status = DiagnosticRecorder.sanitize(status, 80);
        candidates = candidates == null ? List.of()
                : candidates.stream().limit(64).toList();
        appliedMixins = appliedMixins == null ? List.of()
                : appliedMixins.stream().limit(256).toList();
        mixinOverlaps = mixinOverlaps == null ? List.of()
                : mixinOverlaps.stream().limit(128).toList();
        relevantClasses = relevantClasses == null ? List.of()
                : relevantClasses.stream().limit(256).toList();
        analysisWarnings = analysisWarnings == null ? List.of()
                : analysisWarnings.stream()
                .map(value -> DiagnosticRecorder.sanitize(value, 240))
                .limit(32).toList();
    }

    public static ConflictAnalysisSnapshot unavailable(String reason) {
        return new ConflictAnalysisSnapshot("unavailable", List.of(),
                List.of(), List.of(), List.of(), List.of(reason));
    }
}
