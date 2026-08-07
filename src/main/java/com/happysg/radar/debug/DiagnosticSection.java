package com.happysg.radar.debug;

import java.util.List;

public record DiagnosticSection(String title, List<DiagnosticEntry> entries) {
    public static final int MAX_TITLE_LENGTH = 80;

    public DiagnosticSection {
        title = DiagnosticRecorder.sanitize(title, MAX_TITLE_LENGTH);
        entries = List.copyOf(entries);
    }
}
