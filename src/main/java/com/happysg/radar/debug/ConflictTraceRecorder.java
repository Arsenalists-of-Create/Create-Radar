package com.happysg.radar.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.happysg.radar.mixin.diagnostic.EarlyDiagnosticJournal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded two-tier runtime tracing with a non-blocking disk journal. */
public final class ConflictTraceRecorder {
    public static final int MAX_EVENTS = 1024;
    private static final long MAX_TRACE_BYTES = 2L * 1024L * 1024L;
    private static final int MAX_QUEUED_LINES = 2048;
    private static final Object LOCK = new Object();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping()
            .create();
    private static final ArrayDeque<TraceEvent> EVENTS = new ArrayDeque<>();
    private static final ConcurrentHashMap<String, MutableHeartbeat> HEARTBEATS =
            new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<String> WRITE_QUEUE =
            new ConcurrentLinkedQueue<>();
    private static final ScheduledExecutorService WRITER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable,
                        "CreateRadar-ConflictTraceWriter");
                thread.setDaemon(true);
                return thread;
            });
    private static final AtomicLong NEXT_SEQUENCE = new AtomicLong(1);
    private static final AtomicLong NEXT_CORRELATION = new AtomicLong(1);
    private static final AtomicLong DROPPED = new AtomicLong();
    private static final AtomicBoolean WRITE_FAILURE_RECORDED =
            new AtomicBoolean();
    private static volatile boolean enabled;
    private static volatile String sessionId = "none";
    private static volatile long sessionStartEpochMillis;
    private static volatile long sessionStartTick = -1L;

    static {
        WRITER.scheduleWithFixedDelay(ConflictTraceRecorder::flushSafely,
                2L, 2L, TimeUnit.SECONDS);
    }

    private ConflictTraceRecorder() {
    }

    public static State setEnabled(boolean shouldEnable,
                                   @Nullable Level level) {
        return setEnabled(shouldEnable, level, null);
    }

    public static State setEnabled(boolean shouldEnable,
                                   @Nullable Level level,
                                   @Nullable String requestedSessionId) {
        synchronized (LOCK) {
            if (shouldEnable && !enabled) {
                enabled = true;
                sessionId = requestedSessionId == null
                        || requestedSessionId.isBlank()
                        ? UUID.randomUUID().toString().substring(0, 8)
                        : DiagnosticRecorder.sanitize(requestedSessionId, 16);
                sessionStartEpochMillis = System.currentTimeMillis();
                sessionStartTick = level == null ? -1L : level.getGameTime();
                EVENTS.clear();
                WRITE_QUEUE.clear();
                DROPPED.set(0L);
                WRITE_FAILURE_RECORDED.set(false);
                rotateTraceFiles();
                recordInternal("diagnostics", "conflict_trace", "SESSION_START",
                        level, null, 0L, Map.of(), true);
            } else if (!shouldEnable && enabled) {
                recordInternal("diagnostics", "conflict_trace", "SESSION_STOP",
                        level, null, 0L, Map.of(), true);
                enabled = false;
                WRITER.execute(ConflictTraceRecorder::flushSafely);
            }
            return state();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static State state() {
        synchronized (LOCK) {
            return new State(enabled, sessionId, sessionStartEpochMillis,
                    sessionStartTick, EVENTS.size(), DROPPED.get(),
                    WRITE_QUEUE.size());
        }
    }

    public static Scope begin(String subsystem, String operation,
                              @Nullable Level level,
                              @Nullable BlockPos position,
                              Map<String, String> context) {
        long correlation = NEXT_CORRELATION.getAndIncrement();
        if (enabled) {
            recordInternal(subsystem, operation, "BEGIN", level, position,
                    correlation, context, false);
        }
        return new Scope(subsystem, operation, level, position, correlation,
                System.nanoTime(), context);
    }

    public static void mark(String subsystem, String operation, String phase,
                            @Nullable Level level,
                            @Nullable BlockPos position,
                            long correlation,
                            Map<String, String> context) {
        if (enabled) {
            recordInternal(subsystem, operation, phase, level, position,
                    correlation, context, false);
        }
    }

    public static void heartbeat(String subsystem, String operation,
                                 @Nullable Level level, boolean success) {
        String key = heartbeatKey(subsystem, operation, level);
        MutableHeartbeat heartbeat = HEARTBEATS.computeIfAbsent(key,
                ignored -> new MutableHeartbeat());
        heartbeat.count.incrementAndGet();
        if (!success) heartbeat.failures.incrementAndGet();
        heartbeat.lastEpochMillis.set(System.currentTimeMillis());
        heartbeat.lastTick.set(level == null ? -1L : level.getGameTime());
    }

    public static long lastHeartbeatTick(String subsystem, String operation,
                                         @Nullable Level level) {
        MutableHeartbeat heartbeat = HEARTBEATS.get(
                heartbeatKey(subsystem, operation, level));
        return heartbeat == null ? -1L : heartbeat.lastTick.get();
    }

    public static long heartbeatCount(String subsystem, String operation,
                                      @Nullable Level level) {
        MutableHeartbeat heartbeat = HEARTBEATS.get(
                heartbeatKey(subsystem, operation, level));
        return heartbeat == null ? 0L : heartbeat.count.get();
    }

    public static boolean invariant(boolean condition, String subsystem,
                                    String operation, String reason,
                                    @Nullable Level level,
                                    @Nullable BlockPos position,
                                    String... implicatedMods) {
        if (condition) return true;
        DiagnosticRecorder.warn(subsystem, operation, reason, null, level,
                position, implicatedMods);
        recordInternal(subsystem, operation, "INVARIANT_FAILED", level,
                position, 0L, Map.of("reason", reason), true);
        return false;
    }

    public static Snapshot snapshot() {
        List<TraceEvent> events;
        synchronized (LOCK) {
            events = List.copyOf(EVENTS);
        }
        ArrayList<Heartbeat> heartbeats = new ArrayList<>();
        HEARTBEATS.forEach((key, value) -> heartbeats.add(new Heartbeat(key,
                value.count.get(), value.failures.get(),
                value.lastEpochMillis.get(), value.lastTick.get())));
        heartbeats.sort(java.util.Comparator.comparing(Heartbeat::operation));
        return new Snapshot(state(), events, heartbeats);
    }

    public static void clearSessionData() {
        synchronized (LOCK) {
            EVENTS.clear();
            WRITE_QUEUE.clear();
            DROPPED.set(0L);
        }
    }

    private static void recordInternal(String subsystem, String operation,
                                       String phase, @Nullable Level level,
                                       @Nullable BlockPos position,
                                       long correlation,
                                       Map<String, String> context,
                                       boolean force) {
        if (!enabled && !force) return;
        LinkedHashMap<String, String> safeContext = new LinkedHashMap<>();
        if (context != null) {
            context.entrySet().stream().limit(16).forEach(entry ->
                    safeContext.put(DiagnosticRecorder.sanitize(
                                    entry.getKey(), 64),
                            DiagnosticRecorder.sanitize(entry.getValue(), 240)));
        }
        TraceEvent event = new TraceEvent(NEXT_SEQUENCE.getAndIncrement(),
                System.currentTimeMillis(),
                level == null ? -1L : level.getGameTime(),
                level == null ? "unknown"
                        : level.isClientSide ? "client" : "server",
                DiagnosticRecorder.sanitize(Thread.currentThread().getName(), 80),
                DiagnosticRecorder.sanitize(subsystem, 80),
                DiagnosticRecorder.sanitize(operation, 96),
                DiagnosticRecorder.sanitize(phase, 80), correlation,
                level == null ? "unknown"
                        : level.dimension().location().toString(),
                position == null ? "" : position.toShortString(),
                Map.copyOf(safeContext));
        synchronized (LOCK) {
            if (EVENTS.size() >= MAX_EVENTS) {
                EVENTS.removeFirst();
                DROPPED.incrementAndGet();
            }
            EVENTS.addLast(event);
        }
        if (enabled || force) enqueue(GSON.toJson(event));
    }

    private static String heartbeatKey(String subsystem, String operation,
                                       @Nullable Level level) {
        String dimension = level == null ? "unknown"
                : level.dimension().location().toString();
        return DiagnosticRecorder.sanitize(dimension, 128) + "|"
                + DiagnosticRecorder.sanitize(subsystem, 80) + "/"
                + DiagnosticRecorder.sanitize(operation, 96);
    }

    private static void enqueue(String line) {
        if (WRITE_QUEUE.size() >= MAX_QUEUED_LINES) {
            DROPPED.incrementAndGet();
            return;
        }
        WRITE_QUEUE.add(line);
        if (WRITE_QUEUE.size() >= 64) {
            WRITER.execute(ConflictTraceRecorder::flushSafely);
        }
    }

    private static void flushSafely() {
        try {
            flush();
        } catch (IOException | RuntimeException failure) {
            WRITE_QUEUE.clear();
            if (WRITE_FAILURE_RECORDED.compareAndSet(false, true)) {
                DiagnosticRecorder.warn("conflict_trace", "disk_flush",
                        "trace_persistence_unavailable", failure, null, null);
            }
        }
    }

    private static void flush() throws IOException {
        if (WRITE_QUEUE.isEmpty()) return;
        Path directory = EarlyDiagnosticJournal.diagnosticDirectory();
        Files.createDirectories(directory);
        Path file = directory.resolve("trace-current.jsonl");
        if (Files.exists(file) && Files.size(file) >= MAX_TRACE_BYTES) {
            Path previous = directory.resolve("trace-previous.jsonl");
            Files.move(file, previous, StandardCopyOption.REPLACE_EXISTING);
        }
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = WRITE_QUEUE.poll()) != null) {
            output.append(line).append(System.lineSeparator());
            if (output.length() >= 256 * 1024) break;
        }
        if (!output.isEmpty()) {
            Files.writeString(file, output, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    private static void rotateTraceFiles() {
        try {
            Path directory = EarlyDiagnosticJournal.diagnosticDirectory();
            Files.createDirectories(directory);
            Path current = directory.resolve("trace-current.jsonl");
            if (Files.exists(current)) {
                Files.move(current, directory.resolve("trace-previous.jsonl"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException failure) {
            if (WRITE_FAILURE_RECORDED.compareAndSet(false, true)) {
                DiagnosticRecorder.warn("conflict_trace", "rotate",
                        "trace_rotation_unavailable", failure, null, null);
            }
        }
    }

    public record State(boolean enabled, String sessionId,
                        long startEpochMillis, long startTick,
                        int retainedEvents, long droppedEvents,
                        int queuedWrites) {
    }

    public record TraceEvent(long sequence, long epochMillis, long gameTick,
                             String side, String thread, String subsystem,
                             String operation, String phase, long correlation,
                             String dimension, String position,
                             Map<String, String> context) {
    }

    public record Heartbeat(String operation, long count, long failures,
                            long lastEpochMillis, long lastTick) {
    }

    public record Snapshot(State state, List<TraceEvent> events,
                           List<Heartbeat> heartbeats) {
    }

    private static final class MutableHeartbeat {
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();
        private final AtomicLong lastEpochMillis = new AtomicLong();
        private final AtomicLong lastTick = new AtomicLong(-1L);
    }

    public static final class Scope implements AutoCloseable {
        private final String subsystem;
        private final String operation;
        private final Level level;
        private final BlockPos position;
        private final long correlation;
        private final long startedNanos;
        private final Map<String, String> context;
        private boolean success = true;
        private String result = "ok";
        private boolean closed;

        private Scope(String subsystem, String operation, Level level,
                      BlockPos position, long correlation, long startedNanos,
                      Map<String, String> context) {
            this.subsystem = subsystem;
            this.operation = operation;
            this.level = level;
            this.position = position;
            this.correlation = correlation;
            this.startedNanos = startedNanos;
            this.context = context == null ? Map.of() : Map.copyOf(context);
        }

        public long correlation() {
            return correlation;
        }

        public Scope failed(String reason) {
            success = false;
            result = reason;
            return this;
        }

        public Scope result(String value) {
            result = value;
            return this;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            heartbeat(subsystem, operation, level, success);
            if (!enabled) return;
            LinkedHashMap<String, String> completed = new LinkedHashMap<>(context);
            completed.put("result", result);
            completed.put("duration_us", Long.toString(Math.max(0L,
                    (System.nanoTime() - startedNanos) / 1_000L)));
            recordInternal(subsystem, operation,
                    success ? "END" : "FAILED", level, position,
                    correlation, completed, false);
        }
    }
}
