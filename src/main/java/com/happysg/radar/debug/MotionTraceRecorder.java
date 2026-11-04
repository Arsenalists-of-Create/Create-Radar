package com.happysg.radar.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MotionTraceRecorder {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final Object LOCK = new Object();

    private static volatile boolean enabled;
    private static volatile Path path;
    private static volatile String lastError;

    private static BufferedWriter writer;
    private static long sequence;

    private MotionTraceRecorder() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean start() {
        synchronized (LOCK) {
            if (enabled) {
                return true;
            }

            try {
                Path directory = FMLPaths.GAMEDIR.get().resolve("logs").resolve("create_radar").resolve("motion_traces");
                Files.createDirectories(directory);
                String baseName = "motion-" + DATE.format(LocalDateTime.now());
                Path selected = directory.resolve(baseName + ".jsonl");

                int suffix = 1;
                while (Files.exists(selected)) {
                    selected = directory.resolve(baseName + "-" + suffix++ + ".jsonl");
                }

                writer = Files.newBufferedWriter(selected, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                path = selected;
                sequence = 0L;
                lastError = null;
                enabled = true;

                writeLocked("session.start", null, null, new Object[]{"action", "start"});

                return enabled;

            } catch (IOException | RuntimeException exception) {
                enabled = false;
                lastError = exception.toString();
                closeQuietly();
                return false;
            }
        }
    }

    public static void stop() {
        synchronized (LOCK) {
            if (!enabled && writer == null) {
                return;
            }

            if (enabled) {
                writeLocked("session.stop", null, null, new Object[]{"action", "stop"});
            }

            enabled = false;

            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException exception) {
                    lastError = exception.toString();
                } finally {
                    writer = null;
                }
            }
        }
    }

    @Nullable
    public static Path path() {
        return path;
    }

    @Nullable
    public static String lastError() {
        return lastError;
    }

    public static long entries() {
        synchronized (LOCK) {
            return sequence;
        }
    }

    public static void record(String event, @Nullable Level level, @Nullable BlockPos controller, Object... keyValues) {
        if (!enabled || (level != null && level.isClientSide)) {
            return;
        }

        synchronized (LOCK) {
            if (!enabled) {
                return;
            }

            writeLocked(event, level, controller, keyValues);
        }
    }

    private static void writeLocked(String event, @Nullable Level level, @Nullable BlockPos controller, Object[] keyValues) {
        if (writer == null) {
            return;
        }

        try {
            Map<String, Object> fields = new LinkedHashMap<>();

            for (int i = 0; i + 1 < keyValues.length; i += 2) {
                Object value = keyValues[i + 1];

                if (value instanceof Double d && !Double.isFinite(d)) {
                    value = d.toString();
                } else if (value instanceof Float f && !Float.isFinite(f)) {
                    value = f.toString();
                } else if (value instanceof Enum<?> e) {
                    value = e.name();
                } else if (value != null && !(value instanceof Number) && !(value instanceof Boolean) && !(value instanceof String)) {
                    value = String.valueOf(value);
                }

                fields.put(String.valueOf(keyValues[i]), value);
            }

            Map<String, Object> line = new LinkedHashMap<>();
            line.put("seq", ++sequence);
            line.put("timeMs", System.currentTimeMillis());
            line.put("thread", Thread.currentThread().getName());
            line.put("event", event);
            line.put("tick", level == null ? -1L : level.getGameTime());
            line.put("dimension", level == null ? "unknown" : level.dimension().location().toString());
            line.put("controller", controller == null ? null : controller.toShortString());
            line.put("fields", fields);

            writer.write(GSON.toJson(line));
            writer.newLine();

        } catch (IOException | RuntimeException exception) {
            lastError = exception.toString();
            enabled = false;
            closeQuietly();
        }
    }

    private static void closeQuietly() {
        if (writer == null) {
            return;
        }

        try {
            writer.close();
        } catch (IOException exception) {
            if (lastError == null) {
                lastError = exception.toString();
            }
        } finally {
            writer = null;
        }
    }
}
