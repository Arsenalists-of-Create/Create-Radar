package com.happysg.radar.debug;

import com.happysg.radar.mixin.diagnostic.EarlyDiagnosticJournal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;

/** Reads bounded, relevant windows rather than copying general logs. */
public final class FilteredLogEvidence {
    public static final int MAX_LINES = 400;
    public static final int MAX_CHARS = 128 * 1024;
    private static final int MAX_SCANNED_LINES_PER_FILE = 200_000;
    private static final long MAX_SOURCE_BYTES = 32L * 1024L * 1024L;
    private static final int BEFORE = 8;
    private static final int AFTER = 20;
    private static final List<String> MARKERS = List.of(
            "create_radar", "com.happysg.radar", "mixinapplyerror",
            "invalidmixinexception", "injectionerror", "injection failed",
            "noclassdeffounderror", "nosuchmethoderror",
            "abstractmethoderror", "linkageerror", "classcastexception");

    private FilteredLogEvidence() {
    }

    public static Evidence capture() {
        ArrayList<String> warnings = new ArrayList<>();
        ArrayList<Path> sources = sources(warnings);
        StringBuilder output = new StringBuilder();
        ArrayList<String> usedSources = new ArrayList<>();
        int retained = 0;
        for (Path source : sources) {
            if (retained >= MAX_LINES || output.length() >= MAX_CHARS) break;
            try {
                List<NumberedLine> lines = relevantLines(source,
                        MAX_LINES - retained);
                if (lines.isEmpty()) continue;
                String fileName = DiagnosticRecorder.sanitize(
                        source.getFileName().toString(), 180);
                usedSources.add(fileName);
                output.append("=== ").append(fileName).append(" ===\n");
                for (NumberedLine line : lines) {
                    String safe = DiagnosticPrivacy.redact(line.text());
                    int remaining = MAX_CHARS - output.length();
                    if (remaining <= 0) break;
                    String rendered = line.number() + " | " + safe + "\n";
                    output.append(rendered, 0,
                            Math.min(rendered.length(), remaining));
                    retained++;
                    if (retained >= MAX_LINES) break;
                }
            } catch (IOException | RuntimeException failure) {
                warnings.add(source.getFileName() + ": "
                        + failure.getClass().getSimpleName());
            }
        }
        return new Evidence(output.toString(), usedSources, warnings,
                retained, retained >= MAX_LINES || output.length() >= MAX_CHARS);
    }

    private static ArrayList<Path> sources(List<String> warnings) {
        ArrayList<Path> result = new ArrayList<>();
        Path gameDirectory = EarlyDiagnosticJournal.diagnosticDirectory()
                .getParent();
        if (gameDirectory == null) return result;
        Path logs = gameDirectory.resolve("logs");
        Path latest = logs.resolve("latest.log");
        if (regularBounded(latest)) result.add(latest);
        newest(logs, path -> {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            return !path.equals(latest)
                    && (name.endsWith(".log") || name.endsWith(".log.gz"));
        }).ifPresent(result::add);
        newest(gameDirectory.resolve("crash-reports"), path ->
                path.getFileName().toString().toLowerCase(Locale.ROOT)
                        .endsWith(".txt")).ifPresent(result::add);
        return result;
    }

    private static java.util.Optional<Path> newest(Path directory,
                                                   Predicate<Path> filter) {
        if (!Files.isDirectory(directory)) return java.util.Optional.empty();
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).filter(filter)
                    .filter(FilteredLogEvidence::regularBounded)
                    .max(Comparator.comparingLong(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis();
                        } catch (IOException ignored) {
                            return Long.MIN_VALUE;
                        }
                    }));
        } catch (IOException ignored) {
            return java.util.Optional.empty();
        }
    }

    private static boolean regularBounded(Path path) {
        try {
            return Files.isRegularFile(path)
                    && Files.size(path) <= MAX_SOURCE_BYTES;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static List<NumberedLine> relevantLines(Path source,
                                                    int maximum)
            throws IOException {
        ArrayDeque<NumberedLine> previous = new ArrayDeque<>();
        LinkedHashSet<NumberedLine> retained = new LinkedHashSet<>();
        int following = 0;
        try (InputStream raw = Files.newInputStream(source);
             InputStream decoded = source.getFileName().toString()
                     .toLowerCase(Locale.ROOT).endsWith(".gz")
                     ? new GZIPInputStream(raw) : raw;
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     decoded, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null
                    && lineNumber < MAX_SCANNED_LINES_PER_FILE
                    && retained.size() < maximum) {
                lineNumber++;
                NumberedLine numbered = new NumberedLine(lineNumber, line);
                boolean match = relevant(line) && !chat(line);
                if (match) {
                    previous.stream()
                            .filter(value -> !chat(value.text()))
                            .forEach(retained::add);
                    retained.add(numbered);
                    following = AFTER;
                } else if (following > 0 && !chat(line)) {
                    retained.add(numbered);
                    following--;
                }
                previous.addLast(numbered);
                while (previous.size() > BEFORE) previous.removeFirst();
            }
        }
        return retained.stream().limit(maximum).toList();
    }

    private static boolean relevant(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        return MARKERS.stream().anyMatch(normalized::contains);
    }

    private static boolean chat(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        return normalized.contains("[chat]")
                || normalized.contains("chat message")
                || normalized.contains("<--[here]");
    }

    public record Evidence(String text, List<String> sources,
                           List<String> warnings, int retainedLines,
                           boolean truncated) {
        public Evidence {
            text = text == null ? "" : text;
            sources = List.copyOf(sources);
            warnings = warnings.stream()
                    .map(value -> DiagnosticRecorder.sanitize(value, 240))
                    .limit(16).toList();
        }
    }

    private record NumberedLine(int number, String text) {
    }
}
