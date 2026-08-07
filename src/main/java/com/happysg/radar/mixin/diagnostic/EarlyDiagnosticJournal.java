package com.happysg.radar.mixin.diagnostic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A deliberately JDK-only journal which is safe to load before NeoForge. It
 * must never throw into the Mixin transformer.
 */
public final class EarlyDiagnosticJournal {
    public static final int MAX_FILE_BYTES = 128 * 1024;
    private static final int MAX_MEMORY_EVENTS = 256;
    private static final Object LOCK = new Object();
    private static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty("createRadar.diagnostics.enabled", "true"));
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b");
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]{1,5})?(?![0-9])");
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)\\b(secret|password|token|credential)(\\s*[=:]\\s*)([^,;\\s]+)");
    private static final List<EarlyEvent> EVENTS = new ArrayList<>();
    private static final String BOOT_ID = UUID.randomUUID().toString()
            .substring(0, 8);
    private static Path currentFile;
    private static boolean started;
    private static boolean capped;

    private EarlyDiagnosticJournal() {
    }

    public static void beginBoot(String source) {
        if (!ENABLED) return;
        synchronized (LOCK) {
            if (started) return;
            started = true;
            try {
                Path directory = diagnosticDirectory();
                Files.createDirectories(directory);
                Path current = directory.resolve("boot-current.jsonl");
                rotate(current, directory.resolve("boot-previous-1.jsonl"),
                        directory.resolve("boot-previous-2.jsonl"));
                currentFile = current;
            } catch (IOException | RuntimeException ignored) {
                currentFile = null;
            }
            recordLocked("BOOT", "START", Map.of(
                    "source", safe(source, 160),
                    "java", safe(System.getProperty("java.version", "unknown"), 80)));
        }
    }

    public static void record(String stage, String status,
                              Map<String, String> details) {
        if (!ENABLED) return;
        synchronized (LOCK) {
            if (!started) beginBoot("late_initialization");
            recordLocked(stage, status, details == null ? Map.of() : details);
        }
    }

    public static void recordFailure(String stage, String target,
                                     String mixin, String config,
                                     Throwable failure) {
        LinkedHashMap<String, String> details = new LinkedHashMap<>();
        details.put("target", target);
        details.put("mixin", mixin);
        details.put("config", config);
        if (failure != null) {
            details.put("exception", failure.getClass().getName());
            details.put("message", String.valueOf(failure.getMessage()));
            details.put("fingerprint", fingerprint(failure));
        }
        record(stage, "ERROR", details);
    }

    public static void gracefulShutdown(String side) {
        record("BOOT", "GRACEFUL_SHUTDOWN", Map.of("side", side));
    }

    public static List<EarlyEvent> snapshot() {
        synchronized (LOCK) {
            return List.copyOf(EVENTS);
        }
    }

    public static Path diagnosticDirectory() {
        String working = System.getProperty("user.dir", ".");
        return Path.of(working).toAbsolutePath().normalize()
                .resolve("create_radar_debug");
    }

    public static List<Path> journalFiles() {
        Path directory = diagnosticDirectory();
        return List.of(directory.resolve("boot-current.jsonl"),
                directory.resolve("boot-previous-1.jsonl"),
                directory.resolve("boot-previous-2.jsonl"));
    }

    private static void recordLocked(String stage, String status,
                                     Map<String, String> details) {
        LinkedHashMap<String, String> safeDetails = new LinkedHashMap<>();
        details.entrySet().stream()
                .limit(24)
                .forEach(entry -> safeDetails.put(safe(entry.getKey(), 64),
                        safe(entry.getValue(), 512)));
        EarlyEvent event = new EarlyEvent(System.currentTimeMillis(), BOOT_ID,
                safe(stage, 80), safe(status, 80), Map.copyOf(safeDetails));
        if (EVENTS.size() >= MAX_MEMORY_EVENTS) EVENTS.removeFirst();
        EVENTS.add(event);
        append(event);
    }

    private static void append(EarlyEvent event) {
        if (currentFile == null || capped) return;
        try {
            if (Files.exists(currentFile)
                    && Files.size(currentFile) >= MAX_FILE_BYTES) {
                capped = true;
                return;
            }
            Files.writeString(currentFile, toJson(event) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignored) {
            currentFile = null;
        }
    }

    private static void rotate(Path current, Path previousOne,
                               Path previousTwo) throws IOException {
        if (Files.exists(previousOne)) {
            Files.move(previousOne, previousTwo,
                    StandardCopyOption.REPLACE_EXISTING);
        }
        if (Files.exists(current)) {
            Files.move(current, previousOne,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String toJson(EarlyEvent event) {
        StringBuilder result = new StringBuilder(256);
        result.append('{')
                .append("\"epoch_ms\":").append(event.epochMillis())
                .append(",\"boot_id\":\"").append(json(event.bootId()))
                .append("\",\"stage\":\"").append(json(event.stage()))
                .append("\",\"status\":\"").append(json(event.status()))
                .append("\",\"details\":{");
        boolean first = true;
        for (Map.Entry<String, String> entry : event.details().entrySet()) {
            if (!first) result.append(',');
            first = false;
            result.append('\"').append(json(entry.getKey())).append("\":\"")
                    .append(json(entry.getValue())).append('\"');
        }
        return result.append("}}").toString();
    }

    private static String json(String value) {
        StringBuilder result = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) {
                        result.append(String.format("\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.toString();
    }

    private static String safe(String value, int maximum) {
        if (value == null) return "";
        String result = value.replace('\u0000', ' ')
                .replace('\r', ' ').replace('\n', ' ').trim();
        String userHome = System.getProperty("user.home", "");
        if (!userHome.isBlank()) result = result.replace(userHome, "<home>");
        result = UUID_PATTERN.matcher(result).replaceAll("<uuid>");
        result = IPV4_PATTERN.matcher(result).replaceAll("<ip>");
        result = SECRET_PATTERN.matcher(result).replaceAll("$1$2<redacted>");
        return result.length() <= maximum ? result : result.substring(0, maximum);
    }

    private static String fingerprint(Throwable failure) {
        StringBuilder material = new StringBuilder(failure.getClass().getName());
        for (StackTraceElement element : failure.getStackTrace()) {
            material.append('\n').append(element.getClassName()).append('.')
                    .append(element.getMethodName());
            if (material.length() > 4096) break;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(16);
            for (int index = 0; index < 8; index++) {
                result.append(String.format("%02x", digest[index]));
            }
            return result.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(material.toString().hashCode());
        }
    }

    public record EarlyEvent(long epochMillis, String bootId, String stage,
                             String status, Map<String, String> details) {
    }
}
