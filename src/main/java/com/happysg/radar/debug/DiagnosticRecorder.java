package com.happysg.radar.debug;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A bounded, session-only flight recorder for failures Create Radar can safely
 * observe. It deliberately does not install a global uncaught exception
 * handler or attempt to intercept failures owned by other mods.
 */
public final class DiagnosticRecorder {
    public static final int MAX_GROUPS = 512;
    public static final int MAX_CLIENT_EXPORT_GROUPS = 64;
    private static final int MAX_STACK_CHARS = 8192;
    private static final int MAX_TEXT_CHARS = 512;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Object LOCK = new Object();
    private static final LinkedHashMap<String, MutableEvent> EVENTS =
            new LinkedHashMap<>();
    private static final AtomicLong NEXT_SEQUENCE = new AtomicLong(1L);

    private DiagnosticRecorder() {
    }

    public static void info(String subsystem, String operation, String reason,
                            @Nullable Level level, @Nullable BlockPos position,
                            String... implicatedMods) {
        record(DiagnosticSeverity.INFO, subsystem, operation, reason, null,
                level, position, implicatedMods);
    }

    public static void warn(String subsystem, String operation, String reason,
                            @Nullable Throwable throwable,
                            @Nullable Level level, @Nullable BlockPos position,
                            String... implicatedMods) {
        record(DiagnosticSeverity.WARN, subsystem, operation, reason, throwable,
                level, position, implicatedMods);
    }

    public static void error(String subsystem, String operation, String reason,
                             @Nullable Throwable throwable,
                             @Nullable Level level, @Nullable BlockPos position,
                             String... implicatedMods) {
        record(DiagnosticSeverity.ERROR, subsystem, operation, reason, throwable,
                level, position, implicatedMods);
    }

    public static void record(DiagnosticSeverity severity, String subsystem,
                              String operation, String reason,
                              @Nullable Throwable throwable,
                              @Nullable Level level,
                              @Nullable BlockPos position,
                              String... implicatedMods) {
        if (throwable != null && !isRecordableFailure(throwable)) {
            return;
        }
        Objects.requireNonNull(severity, "severity");
        String safeSubsystem = sanitize(subsystem, 80);
        String safeOperation = sanitize(operation, 96);
        String safeReason = sanitize(reason, MAX_TEXT_CHARS);
        String exceptionType = throwable == null ? "" :
                sanitize(throwable.getClass().getName(), 160);
        String exceptionMessage = throwable == null ? "" :
                sanitize(throwable.getMessage(), MAX_TEXT_CHARS);
        String stack = throwable == null ? "" : stackTrace(throwable);
        String fingerprint = fingerprint(exceptionType, stack);
        String side = level == null ? "unknown" :
                (level.isClientSide ? "client" : "server");
        String dimension = level == null ? "unknown" :
                level.dimension().location().toString();
        String positionText = position == null ? "" : position.toShortString();
        long tick = level == null ? -1L : level.getGameTime();
        long now = System.currentTimeMillis();
        List<String> mods = Arrays.stream(implicatedMods == null
                        ? new String[0] : implicatedMods)
                .filter(Objects::nonNull)
                .map(value -> sanitize(value.toLowerCase(Locale.ROOT), 80))
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
        String key = String.join("|", severity.name(), safeSubsystem,
                safeOperation, safeReason, exceptionType, fingerprint,
                dimension, positionText, String.join(",", mods));

        synchronized (LOCK) {
            MutableEvent existing = EVENTS.get(key);
            if (existing != null) {
                existing.lastEpochMillis = now;
                existing.lastTick = tick;
                existing.occurrences++;
                return;
            }
            while (EVENTS.size() >= MAX_GROUPS) {
                EVENTS.remove(EVENTS.keySet().iterator().next());
            }
            EVENTS.put(key, new MutableEvent(
                    NEXT_SEQUENCE.getAndIncrement(), severity, safeSubsystem,
                    safeOperation, safeReason, exceptionType, exceptionMessage,
                    fingerprint, stack, side, dimension, positionText, mods,
                    now, tick));
        }
    }

    public static List<DiagnosticEvent> snapshot() {
        return snapshot(MAX_GROUPS);
    }

    public static List<DiagnosticEvent> snapshot(int maximum) {
        int limit = Math.max(0, Math.min(MAX_GROUPS, maximum));
        synchronized (LOCK) {
            ArrayList<DiagnosticEvent> result = new ArrayList<>(
                    Math.min(limit, EVENTS.size()));
            int skip = Math.max(0, EVENTS.size() - limit);
            int index = 0;
            for (MutableEvent event : EVENTS.values()) {
                if (index++ >= skip) {
                    result.add(event.freeze());
                }
            }
            return List.copyOf(result);
        }
    }

    public static Summary summary() {
        long info = 0L;
        long warnings = 0L;
        long errors = 0L;
        DiagnosticHealth health = DiagnosticHealth.HEALTHY;
        synchronized (LOCK) {
            for (MutableEvent event : EVENTS.values()) {
                switch (event.severity) {
                    case INFO -> info += event.occurrences;
                    case WARN -> warnings += event.occurrences;
                    case ERROR -> errors += event.occurrences;
                }
                health = health.combine(
                        DiagnosticHealth.from(event.severity));
            }
            return new Summary(health, EVENTS.size(), info, warnings, errors);
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            EVENTS.clear();
        }
    }

    /** Accepts already bounded client data as untrusted report input. */
    public static List<DiagnosticEvent> sanitizeClientEvents(
            List<DiagnosticEvent> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        ArrayList<DiagnosticEvent> safe = new ArrayList<>();
        int count = Math.min(MAX_CLIENT_EXPORT_GROUPS, events.size());
        for (int index = 0; index < count; index++) {
            DiagnosticEvent event = events.get(index);
            if (event == null) continue;
            safe.add(new DiagnosticEvent(
                    event.sequence(), event.severity(),
                    sanitize(event.subsystem(), 80),
                    sanitize(event.operation(), 96),
                    sanitize(event.reason(), MAX_TEXT_CHARS),
                    sanitize(event.exceptionType(), 160),
                    sanitize(event.exceptionMessage(), MAX_TEXT_CHARS),
                    sanitize(event.stackFingerprint(), 64),
                    boundedMultiline(event.stackTrace(), MAX_STACK_CHARS),
                    "client", sanitize(event.dimension(), 128),
                    sanitize(event.position(), 80),
                    event.implicatedMods().stream().limit(16)
                            .map(value -> sanitize(value, 80)).toList(),
                    event.firstEpochMillis(), event.lastEpochMillis(),
                    event.firstTick(), event.lastTick(),
                    Math.max(1L, event.occurrences())));
        }
        return List.copyOf(safe);
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter output = new StringWriter();
        throwable.printStackTrace(new PrintWriter(output));
        return boundedMultiline(output.toString(), MAX_STACK_CHARS);
    }

    private static boolean isRecordableFailure(Throwable throwable) {
        return throwable instanceof Exception
                || throwable instanceof LinkageError;
    }

    private static String boundedMultiline(@Nullable String value,
                                           int maximum) {
        if (value == null) return "";
        String safe = DiagnosticPrivacy.redact(value)
                .replace('\u0000', ' ').trim();
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }

    private static String fingerprint(String type, String stack) {
        String material = type + "\n" + stack;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(16);
            for (int index = 0; index < 8; index++) {
                result.append(String.format("%02x", digest[index]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            LOGGER.debug("SHA-256 unavailable for diagnostic fingerprint", impossible);
            return Integer.toHexString(material.hashCode());
        }
    }

    public static String fingerprintText(@Nullable String value) {
        return fingerprint("text", value == null ? "" : value);
    }

    public static String sanitize(@Nullable String value, int maximum) {
        if (value == null) return "";
        int limit = Math.max(0, maximum);
        String safe = DiagnosticPrivacy.redact(value)
                .replace('\r', ' ').replace('\n', ' ')
                .replace('\u0000', ' ').trim();
        return safe.length() <= limit ? safe : safe.substring(0, limit);
    }

    public record Summary(DiagnosticHealth health, int groups,
                          long infoOccurrences, long warningOccurrences,
                          long errorOccurrences) {
    }

    private static final class MutableEvent {
        private final long sequence;
        private final DiagnosticSeverity severity;
        private final String subsystem;
        private final String operation;
        private final String reason;
        private final String exceptionType;
        private final String exceptionMessage;
        private final String stackFingerprint;
        private final String stackTrace;
        private final String side;
        private final String dimension;
        private final String position;
        private final List<String> implicatedMods;
        private final long firstEpochMillis;
        private final long firstTick;
        private long lastEpochMillis;
        private long lastTick;
        private long occurrences = 1L;

        private MutableEvent(long sequence, DiagnosticSeverity severity,
                             String subsystem, String operation, String reason,
                             String exceptionType, String exceptionMessage,
                             String stackFingerprint, String stackTrace,
                             String side, String dimension, String position,
                             List<String> implicatedMods, long now, long tick) {
            this.sequence = sequence;
            this.severity = severity;
            this.subsystem = subsystem;
            this.operation = operation;
            this.reason = reason;
            this.exceptionType = exceptionType;
            this.exceptionMessage = exceptionMessage;
            this.stackFingerprint = stackFingerprint;
            this.stackTrace = stackTrace;
            this.side = side;
            this.dimension = dimension;
            this.position = position;
            this.implicatedMods = implicatedMods;
            this.firstEpochMillis = now;
            this.lastEpochMillis = now;
            this.firstTick = tick;
            this.lastTick = tick;
        }

        private DiagnosticEvent freeze() {
            return new DiagnosticEvent(sequence, severity, subsystem,
                    operation, reason, exceptionType, exceptionMessage,
                    stackFingerprint, stackTrace, side, dimension, position,
                    implicatedMods, firstEpochMillis, lastEpochMillis,
                    firstTick, lastTick, occurrences);
        }
    }
}
