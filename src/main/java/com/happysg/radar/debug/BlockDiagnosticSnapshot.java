package com.happysg.radar.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record BlockDiagnosticSnapshot(
        Status status,
        String dimension,
        BlockPos position,
        String blockId,
        String blockEntityType,
        long gameTime,
        String message,
        List<DiagnosticSection> sections
) {
    public static final int MAX_NETWORK_SECTIONS = 12;
    public static final int MAX_NETWORK_ENTRIES = 64;

    public BlockDiagnosticSnapshot {
        status = status == null ? Status.ERROR : status;
        dimension = DiagnosticRecorder.sanitize(dimension, 128);
        position = position == null ? BlockPos.ZERO : position.immutable();
        blockId = DiagnosticRecorder.sanitize(blockId, 160);
        blockEntityType = DiagnosticRecorder.sanitize(blockEntityType, 160);
        message = DiagnosticRecorder.sanitize(message, 240);
        sections = List.copyOf(sections);
    }

    public static BlockDiagnosticSnapshot status(Status status,
                                                  String message) {
        return new BlockDiagnosticSnapshot(status, "", BlockPos.ZERO,
                "", "", -1L, message, List.of());
    }

    public DiagnosticHealth health() {
        DiagnosticHealth health = status == Status.OK
                ? DiagnosticHealth.HEALTHY : DiagnosticHealth.DEGRADED;
        for (DiagnosticSection section : sections) {
            for (DiagnosticEntry entry : section.entries()) {
                health = health.combine(
                        DiagnosticHealth.from(entry.severity()));
            }
        }
        return health;
    }

    public void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(status);
        buffer.writeUtf(dimension, 128);
        buffer.writeBlockPos(position);
        buffer.writeUtf(blockId, 160);
        buffer.writeUtf(blockEntityType, 160);
        buffer.writeLong(gameTime);
        buffer.writeUtf(message, 240);
        int sectionCount = Math.min(MAX_NETWORK_SECTIONS, sections.size());
        buffer.writeVarInt(sectionCount);
        int remainingEntries = MAX_NETWORK_ENTRIES;
        for (int sectionIndex = 0; sectionIndex < sectionCount;
             sectionIndex++) {
            DiagnosticSection section = sections.get(sectionIndex);
            buffer.writeUtf(section.title(),
                    DiagnosticSection.MAX_TITLE_LENGTH);
            int entryCount = Math.min(remainingEntries,
                    section.entries().size());
            buffer.writeVarInt(entryCount);
            for (int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
                DiagnosticEntry entry = section.entries().get(entryIndex);
                buffer.writeUtf(entry.key(), DiagnosticEntry.MAX_KEY_LENGTH);
                buffer.writeUtf(entry.value(), DiagnosticEntry.MAX_VALUE_LENGTH);
                buffer.writeEnum(entry.severity());
            }
            remainingEntries -= entryCount;
        }
    }

    public static BlockDiagnosticSnapshot decode(
            RegistryFriendlyByteBuf buffer) {
        Status status = buffer.readEnum(Status.class);
        String dimension = buffer.readUtf(128);
        BlockPos position = buffer.readBlockPos();
        String blockId = buffer.readUtf(160);
        String blockEntityType = buffer.readUtf(160);
        long gameTime = buffer.readLong();
        String message = buffer.readUtf(240);
        int sectionCount = buffer.readVarInt();
        if (sectionCount < 0 || sectionCount > MAX_NETWORK_SECTIONS) {
            throw new IllegalArgumentException(
                    "Invalid diagnostic section count " + sectionCount);
        }
        int totalEntries = 0;
        ArrayList<DiagnosticSection> sections = new ArrayList<>(sectionCount);
        for (int sectionIndex = 0; sectionIndex < sectionCount;
             sectionIndex++) {
            String title = buffer.readUtf(DiagnosticSection.MAX_TITLE_LENGTH);
            int entryCount = buffer.readVarInt();
            totalEntries += entryCount;
            if (entryCount < 0 || totalEntries > MAX_NETWORK_ENTRIES) {
                throw new IllegalArgumentException(
                        "Invalid diagnostic entry count " + totalEntries);
            }
            ArrayList<DiagnosticEntry> entries = new ArrayList<>(entryCount);
            for (int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
                entries.add(new DiagnosticEntry(
                        buffer.readUtf(DiagnosticEntry.MAX_KEY_LENGTH),
                        buffer.readUtf(DiagnosticEntry.MAX_VALUE_LENGTH),
                        buffer.readEnum(DiagnosticSeverity.class)));
            }
            sections.add(new DiagnosticSection(title, entries));
        }
        return new BlockDiagnosticSnapshot(status, dimension, position,
                blockId, blockEntityType, gameTime, message, sections);
    }

    public enum Status {
        OK,
        MISS,
        NOT_CREATE_RADAR,
        UNLOADED,
        DENIED,
        RATE_LIMITED,
        ERROR
    }
}
