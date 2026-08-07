package com.happysg.radar.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.happysg.radar.mixin.diagnostic.EarlyDiagnosticJournal;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Strictly bounded client-side conflict evidence sent only on report request. */
public record ClientConflictAppendix(
        String analysisJson,
        String traceJson,
        String startupJournal,
        String filteredLogEvidence
) {
    public static final int MAX_ANALYSIS_CHARS = 64 * 1024;
    public static final int MAX_TRACE_CHARS = 64 * 1024;
    public static final int MAX_STARTUP_CHARS = 32 * 1024;
    public static final int MAX_LOG_CHARS = 64 * 1024;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping()
            .create();

    public ClientConflictAppendix {
        analysisJson = boundedJson(analysisJson, MAX_ANALYSIS_CHARS);
        traceJson = boundedJson(traceJson, MAX_TRACE_CHARS);
        startupJournal = bounded(startupJournal, MAX_STARTUP_CHARS);
        filteredLogEvidence = bounded(filteredLogEvidence, MAX_LOG_CHARS);
    }

    public static ClientConflictAppendix capture(List<DiagnosticEvent> events) {
        ConflictAnalysisSnapshot analysis = ConflictAnalyzer.capture(events);
        ConflictTraceRecorder.Snapshot trace = ConflictTraceRecorder.snapshot();
        return new ClientConflictAppendix(GSON.toJson(analysis),
                GSON.toJson(trace), readJournals(),
                FilteredLogEvidence.capture().text());
    }

    public static ClientConflictAppendix empty() {
        return new ClientConflictAppendix("{}", "{}", "", "");
    }

    public void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(analysisJson, MAX_ANALYSIS_CHARS);
        buffer.writeUtf(traceJson, MAX_TRACE_CHARS);
        buffer.writeUtf(startupJournal, MAX_STARTUP_CHARS);
        buffer.writeUtf(filteredLogEvidence, MAX_LOG_CHARS);
    }

    public static ClientConflictAppendix decode(RegistryFriendlyByteBuf buffer) {
        return new ClientConflictAppendix(
                buffer.readUtf(MAX_ANALYSIS_CHARS),
                buffer.readUtf(MAX_TRACE_CHARS),
                buffer.readUtf(MAX_STARTUP_CHARS),
                buffer.readUtf(MAX_LOG_CHARS));
    }

    private static String readJournals() {
        StringBuilder result = new StringBuilder();
        for (Path path : EarlyDiagnosticJournal.journalFiles()) {
            if (!Files.isRegularFile(path)) continue;
            try {
                String header = "=== " + path.getFileName() + " ===\n";
                String value = Files.readString(path, StandardCharsets.UTF_8);
                int remaining = MAX_STARTUP_CHARS - result.length();
                if (remaining <= 0) break;
                String combined = header + value;
                result.append(combined, 0,
                        Math.min(combined.length(), remaining));
            } catch (IOException ignored) {
            }
        }
        return result.toString();
    }

    private static String bounded(String value, int maximum) {
        if (value == null) return "";
        String safe = DiagnosticPrivacy.redact(value).replace('\u0000', ' ');
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }

    private static String boundedJson(String value, int maximum) {
        String safe = bounded(value, maximum);
        if (value != null && DiagnosticPrivacy.redact(value).length() > maximum) {
            return "{\"status\":\"truncated\",\"fingerprint\":\""
                    + DiagnosticRecorder.fingerprintText(value) + "\"}";
        }
        try {
            JsonParser.parseString(safe);
            return safe;
        } catch (RuntimeException malformed) {
            return "{\"status\":\"invalid\"}";
        }
    }
}
