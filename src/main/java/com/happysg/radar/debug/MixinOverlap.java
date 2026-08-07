package com.happysg.radar.debug;

import java.util.List;

public record MixinOverlap(
        String targetClass,
        String createRadarMixin,
        String foreignMixin,
        List<String> foreignMods,
        Risk risk,
        List<String> sharedMethods,
        List<String> foreignInjectorKinds,
        int createRadarPriority,
        int foreignPriority
) {
    public MixinOverlap {
        targetClass = DiagnosticRecorder.sanitize(targetClass, 240);
        createRadarMixin = DiagnosticRecorder.sanitize(createRadarMixin, 240);
        foreignMixin = DiagnosticRecorder.sanitize(foreignMixin, 240);
        foreignMods = foreignMods == null ? List.of() : foreignMods.stream()
                .map(value -> DiagnosticRecorder.sanitize(value, 80))
                .distinct().sorted().limit(16).toList();
        risk = risk == null ? Risk.LOW : risk;
        sharedMethods = sharedMethods == null ? List.of()
                : sharedMethods.stream().distinct().sorted().limit(16).toList();
        foreignInjectorKinds = foreignInjectorKinds == null ? List.of()
                : foreignInjectorKinds.stream().distinct().sorted().limit(16)
                .toList();
    }

    public enum Risk {
        LOW,
        MEDIUM,
        HIGH
    }
}
