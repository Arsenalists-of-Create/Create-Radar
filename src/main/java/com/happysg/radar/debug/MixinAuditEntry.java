package com.happysg.radar.debug;

import java.util.List;

public record MixinAuditEntry(
        String targetClass,
        String mixinClass,
        String config,
        List<String> ownerMods,
        int priority,
        boolean createRadarMixin,
        boolean targetOwnedByCreateRadar,
        List<Injection> injections
) {
    public MixinAuditEntry {
        targetClass = DiagnosticRecorder.sanitize(targetClass, 240);
        mixinClass = DiagnosticRecorder.sanitize(mixinClass, 240);
        config = DiagnosticRecorder.sanitize(config, 180);
        ownerMods = ownerMods == null ? List.of() : ownerMods.stream()
                .map(value -> DiagnosticRecorder.sanitize(value, 80))
                .distinct().sorted().limit(16).toList();
        injections = injections == null ? List.of()
                : injections.stream().limit(64).toList();
    }

    public record Injection(String handler, String kind,
                            List<String> methodSelectors,
                            List<String> atSelectors,
                            boolean cancellable) {
        public Injection {
            handler = DiagnosticRecorder.sanitize(handler, 160);
            kind = DiagnosticRecorder.sanitize(kind, 80);
            methodSelectors = methodSelectors == null ? List.of()
                    : methodSelectors.stream()
                    .map(value -> DiagnosticRecorder.sanitize(value, 240))
                    .limit(16).toList();
            atSelectors = atSelectors == null ? List.of()
                    : atSelectors.stream()
                    .map(value -> DiagnosticRecorder.sanitize(value, 240))
                    .limit(16).toList();
        }
    }
}
