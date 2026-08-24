package com.happysg.radar.api.arad;

import com.happysg.radar.block.arad.rwr.RadarType;
import com.happysg.radar.block.radar.behavior.IRadar;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side, transient rolling RPM history for native radar emitters. */
final class RadarRpmTelemetry {
    private static final Map<ResourceKey<Level>, Map<UUID, Entry>> BY_DIMENSION =
            new HashMap<>();

    private RadarRpmTelemetry() {
    }

    static void heartbeat(ServerLevel level, IRadar radar) {
        UUID emitterId = radar.getEmitterId();
        if (emitterId == null) {
            return;
        }

        long now = level.getGameTime();
        Entry entry = BY_DIMENSION
                .computeIfAbsent(level.dimension(), ignored -> new HashMap<>())
                .computeIfAbsent(emitterId, ignored -> new Entry());
        entry.lastHeartbeatTick = now;

        if (radar.getRadarTypeEnum() == RadarType.AIRBORNE) {
            return;
        }
        if (entry.lastSampleTick == Long.MIN_VALUE
                || now - entry.lastSampleTick >= RollingRpmTracker.SAMPLE_INTERVAL_TICKS
                || now < entry.lastSampleTick) {
            entry.tracker.sample(radar.getInputRpm());
            entry.lastSampleTick = now;
        }
    }

    static RollingRpmTracker.Snapshot snapshot(ServerLevel level, IRadar radar) {
        if (radar.getRadarTypeEnum() == RadarType.AIRBORNE
                || radar.getEmitterId() == null) {
            return RollingRpmTracker.Snapshot.ZERO;
        }
        Map<UUID, Entry> entries = BY_DIMENSION.get(level.dimension());
        Entry entry = entries == null ? null : entries.get(radar.getEmitterId());
        return entry == null
                ? RollingRpmTracker.Snapshot.ZERO
                : entry.tracker.snapshot();
    }

    static void tick(ServerLevel level, int heartbeatTtlTicks) {
        Map<UUID, Entry> entries = BY_DIMENSION.get(level.dimension());
        if (entries == null) {
            return;
        }
        long now = level.getGameTime();
        entries.values().removeIf(entry -> now < entry.lastHeartbeatTick
                || now - entry.lastHeartbeatTick > heartbeatTtlTicks);
        if (entries.isEmpty()) {
            BY_DIMENSION.remove(level.dimension());
        }
    }

    static void clear(ServerLevel level) {
        BY_DIMENSION.remove(level.dimension());
    }

    private static final class Entry {
        private final RollingRpmTracker tracker = new RollingRpmTracker();
        private long lastSampleTick = Long.MIN_VALUE;
        private long lastHeartbeatTick;
    }
}
