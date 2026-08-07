package com.happysg.radar.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DiagnosticSnapshotBuilder {
    public static final int MAX_SECTIONS = 12;
    public static final int MAX_ENTRIES = 64;
    private final ServerLevel level;
    private final BlockPos position;
    private final String blockId;
    private final String blockEntityType;
    private final Map<String, List<DiagnosticEntry>> sections =
            new LinkedHashMap<>();
    private int entryCount;

    public DiagnosticSnapshotBuilder(ServerLevel level, BlockPos position,
                                     ResourceLocation blockId,
                                     String blockEntityType) {
        this.level = level;
        this.position = position.immutable();
        this.blockId = blockId.toString();
        this.blockEntityType = DiagnosticRecorder.sanitize(
                blockEntityType, 160);
    }

    public DiagnosticSnapshotBuilder add(String section, String key,
                                         Object value) {
        return add(section, key, value, DiagnosticSeverity.INFO);
    }

    public DiagnosticSnapshotBuilder warn(String section, String key,
                                          Object value) {
        return add(section, key, value, DiagnosticSeverity.WARN);
    }

    public DiagnosticSnapshotBuilder error(String section, String key,
                                           Object value) {
        return add(section, key, value, DiagnosticSeverity.ERROR);
    }

    public DiagnosticSnapshotBuilder add(String section, String key,
                                         Object value,
                                         DiagnosticSeverity severity) {
        if (entryCount >= MAX_ENTRIES) return this;
        String title = DiagnosticRecorder.sanitize(section,
                DiagnosticSection.MAX_TITLE_LENGTH);
        List<DiagnosticEntry> entries = sections.get(title);
        if (entries == null) {
            if (sections.size() >= MAX_SECTIONS) return this;
            entries = new ArrayList<>();
            sections.put(title, entries);
        }
        entries.add(new DiagnosticEntry(key, String.valueOf(value), severity));
        entryCount++;
        return this;
    }

    public BlockDiagnosticSnapshot build() {
        List<DiagnosticSection> frozen = sections.entrySet().stream()
                .map(entry -> new DiagnosticSection(entry.getKey(),
                        entry.getValue()))
                .toList();
        return new BlockDiagnosticSnapshot(
                BlockDiagnosticSnapshot.Status.OK,
                level.dimension().location().toString(), position, blockId,
                blockEntityType, level.getGameTime(), "", frozen);
    }
}
