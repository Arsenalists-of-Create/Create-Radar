package com.happysg.radar.debug;

import java.util.List;

public record ClassOwnership(String className, List<Owner> owners,
                             boolean duplicate) {
    public ClassOwnership {
        className = DiagnosticRecorder.sanitize(className, 240);
        owners = owners == null ? List.of() : List.copyOf(owners);
    }

    public record Owner(String modId, String version, String fileName) {
        public Owner {
            modId = DiagnosticRecorder.sanitize(modId, 80);
            version = DiagnosticRecorder.sanitize(version, 120);
            fileName = DiagnosticRecorder.sanitize(fileName, 180);
        }
    }
}
