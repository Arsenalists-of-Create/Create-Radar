package com.happysg.radar.debug;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record ClientDiagnosticReport(
        String javaVersion,
        String operatingSystem,
        List<String> loadedMods,
        List<DiagnosticEvent> events,
        ClientConflictAppendix conflictAppendix
) {
    private static final int MAX_MODS = 512;
    private static final int MAX_EVENTS =
            DiagnosticRecorder.MAX_CLIENT_EXPORT_GROUPS;

    public ClientDiagnosticReport {
        javaVersion = DiagnosticRecorder.sanitize(javaVersion, 160);
        operatingSystem = DiagnosticRecorder.sanitize(operatingSystem, 160);
        loadedMods = List.copyOf(loadedMods.stream().limit(MAX_MODS)
                .map(value -> DiagnosticRecorder.sanitize(value, 160))
                .toList());
        events = DiagnosticRecorder.sanitizeClientEvents(events);
        conflictAppendix = conflictAppendix == null
                ? ClientConflictAppendix.empty() : conflictAppendix;
    }

    public static ClientDiagnosticReport capture() {
        List<String> mods = ModList.get().getMods().stream()
                .map(mod -> mod.getModId() + " " + mod.getVersion())
                .sorted(Comparator.naturalOrder())
                .limit(MAX_MODS)
                .toList();
        List<DiagnosticEvent> events = DiagnosticRecorder.snapshot(MAX_EVENTS);
        return new ClientDiagnosticReport(
                System.getProperty("java.version", "unknown") + " "
                        + System.getProperty("java.vendor", "unknown"),
                System.getProperty("os.name", "unknown") + " "
                        + System.getProperty("os.version", "unknown") + " "
                        + System.getProperty("os.arch", "unknown"),
                mods, events, ClientConflictAppendix.capture(events));
    }

    public void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(javaVersion, 160);
        buffer.writeUtf(operatingSystem, 160);
        buffer.writeVarInt(loadedMods.size());
        for (String mod : loadedMods) buffer.writeUtf(mod, 160);
        buffer.writeVarInt(events.size());
        for (DiagnosticEvent event : events) encodeEvent(buffer, event);
        conflictAppendix.encode(buffer);
    }

    public static ClientDiagnosticReport decode(RegistryFriendlyByteBuf buffer) {
        String java = buffer.readUtf(160);
        String os = buffer.readUtf(160);
        int modCount = checkedCount(buffer.readVarInt(), MAX_MODS, "mods");
        ArrayList<String> mods = new ArrayList<>(modCount);
        for (int index = 0; index < modCount; index++) {
            mods.add(buffer.readUtf(160));
        }
        int eventCount = checkedCount(buffer.readVarInt(), MAX_EVENTS,
                "events");
        ArrayList<DiagnosticEvent> events = new ArrayList<>(eventCount);
        for (int index = 0; index < eventCount; index++) {
            events.add(decodeEvent(buffer));
        }
        return new ClientDiagnosticReport(java, os, mods, events,
                ClientConflictAppendix.decode(buffer));
    }

    private static void encodeEvent(RegistryFriendlyByteBuf buffer,
                                    DiagnosticEvent event) {
        buffer.writeLong(event.sequence());
        buffer.writeEnum(event.severity());
        buffer.writeUtf(event.subsystem(), 80);
        buffer.writeUtf(event.operation(), 96);
        buffer.writeUtf(event.reason(), 512);
        buffer.writeUtf(event.exceptionType(), 160);
        buffer.writeUtf(event.exceptionMessage(), 512);
        buffer.writeUtf(event.stackFingerprint(), 64);
        buffer.writeUtf(DiagnosticRecorder.sanitize(event.stackTrace(), 2048),
                2048);
        buffer.writeUtf(event.dimension(), 128);
        buffer.writeUtf(event.position(), 80);
        int modCount = Math.min(16, event.implicatedMods().size());
        buffer.writeVarInt(modCount);
        for (int index = 0; index < modCount; index++) {
            buffer.writeUtf(event.implicatedMods().get(index), 80);
        }
        buffer.writeLong(event.firstEpochMillis());
        buffer.writeLong(event.lastEpochMillis());
        buffer.writeLong(event.firstTick());
        buffer.writeLong(event.lastTick());
        buffer.writeVarLong(Math.max(1L, event.occurrences()));
    }

    private static DiagnosticEvent decodeEvent(RegistryFriendlyByteBuf buffer) {
        long sequence = buffer.readLong();
        DiagnosticSeverity severity = buffer.readEnum(DiagnosticSeverity.class);
        String subsystem = buffer.readUtf(80);
        String operation = buffer.readUtf(96);
        String reason = buffer.readUtf(512);
        String exceptionType = buffer.readUtf(160);
        String exceptionMessage = buffer.readUtf(512);
        String fingerprint = buffer.readUtf(64);
        String stackTrace = buffer.readUtf(2048);
        String dimension = buffer.readUtf(128);
        String position = buffer.readUtf(80);
        int modCount = checkedCount(buffer.readVarInt(), 16,
                "implicated mods");
        ArrayList<String> mods = new ArrayList<>(modCount);
        for (int index = 0; index < modCount; index++) {
            mods.add(buffer.readUtf(80));
        }
        return new DiagnosticEvent(sequence, severity, subsystem, operation,
                reason, exceptionType, exceptionMessage, fingerprint,
                stackTrace, "client", dimension, position, mods,
                buffer.readLong(), buffer.readLong(), buffer.readLong(),
                buffer.readLong(), Math.max(1L, buffer.readVarLong()));
    }

    private static int checkedCount(int count, int maximum, String label) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(
                    "Invalid client diagnostic " + label + " count " + count);
        }
        return count;
    }
}
