package com.happysg.radar.compat.sable;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.config.RadarConfig;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class SableSilhouetteServerCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_SYNC_BOXES = 16_384;
    private static final Map<ResourceKey<Level>, Map<UUID, Entry>> BY_DIMENSION = new HashMap<>();

    private SableSilhouetteServerCache() {
    }

    public static void attachMetadata(ServerLevel level, RadarTrack track) {
        if (!"Sable:ship".equals(track.entityType())) {
            track.clearSilhouette();
            return;
        }

        UUID id;
        try {
            id = UUID.fromString(track.id());
        } catch (IllegalArgumentException ignored) {
            track.clearSilhouette();
            return;
        }

        Entry entry = getOrBuild(level, id, false);
        if (entry == null || entry.status == SableSilhouetteStatus.NONE) {
            track.clearSilhouette();
            return;
        }
        track.setSilhouette(id, entry.revision, entry.status);
    }

    public static SubLevelSilhouette getSilhouette(ServerLevel level, UUID id) {
        Entry entry = getOrBuild(level, id, true);
        return entry == null ? null : entry.silhouette;
    }

    public static int getRevision(ServerLevel level, UUID id) {
        Entry entry = entries(level).get(id);
        return entry == null ? -1 : entry.revision;
    }

    public static byte getStatus(ServerLevel level, UUID id) {
        Entry entry = entries(level).get(id);
        return entry == null ? SableSilhouetteStatus.NONE : entry.status;
    }

    public static void markDirty(ServerLevel level, UUID id) {
        Entry entry = entries(level).computeIfAbsent(id, ignored -> new Entry());
        long now = level.getGameTime();
        entry.dirty = true;
        entry.nextBuildTick = Math.max(entry.nextBuildTick, now + RadarConfig.server().sableSilhouetteRebuildDebounceTicks.get());
    }

    public static void remove(ServerLevel level, UUID id) {
        entries(level).remove(id);
    }

    public static void clearLevel(ServerLevel level) {
        BY_DIMENSION.remove(level.dimension());
    }

    public static void clearAll() {
        BY_DIMENSION.clear();
    }

    public static void prune(ServerLevel level) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            clearLevel(level);
            return;
        }
        Iterator<UUID> iterator = entries(level).keySet().iterator();
        while (iterator.hasNext()) {
            if (container.getSubLevel(iterator.next()) == null) {
                iterator.remove();
            }
        }
    }

    private static Entry getOrBuild(ServerLevel level, UUID id, boolean forceRequest) {
        Entry entry = entries(level).computeIfAbsent(id, ignored -> new Entry());
        long now = level.getGameTime();
        if (!forceRequest && entry.silhouette != null && !entry.dirty && now < entry.nextRefreshTick) {
            return entry;
        }
        if (now < entry.nextBuildTick) {
            return entry;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel subLevel = container == null ? null : container.getSubLevel(id);
        if (subLevel == null || subLevel.isRemoved()) {
            entries(level).remove(id);
            return null;
        }

        build(level, id, subLevel, entry, now);
        return entry;
    }

    private static void build(ServerLevel level, UUID id, SubLevel subLevel, Entry entry, long now) {
        entry.lastBuildTick = now;
        entry.nextBuildTick = now + RadarConfig.server().sableSilhouetteFailureCooldownTicks.get();
        entry.nextRefreshTick = now + RadarConfig.server().sableSilhouetteRefreshTicks.get();
        entry.dirty = false;

        try {
            SubLevelSilhouette silhouette = SubLevelSilhouetteScanner.scanLoadedChunks(
                    subLevel,
                    SubLevelSilhouetteScanner.defaultHullFilter(),
                    RadarConfig.server().sableSilhouetteMaxScannedBlocks.get(),
                    Math.min(RadarConfig.server().sableSilhouetteMaxCollisionBoxes.get(), MAX_SYNC_BOXES)
            );
            byte status = silhouette.isEmpty() ? SableSilhouetteStatus.NONE : SableSilhouetteStatus.READY;
            if (status == SableSilhouetteStatus.NONE && RadarConfig.server().sableSilhouetteUseFallbackBox.get()) {
                silhouette = SubLevelSilhouetteScanner.boundingBoxFallback(subLevel);
                status = silhouette.isEmpty() ? SableSilhouetteStatus.NONE : SableSilhouetteStatus.FALLBACK;
            }
            store(entry, silhouette, status, now);
        } catch (SubLevelSilhouetteScanner.ScanLimitExceededException limit) {
            if (RadarConfig.server().sableSilhouetteUseFallbackBox.get()) {
                store(entry, SubLevelSilhouetteScanner.boundingBoxFallback(subLevel), SableSilhouetteStatus.FALLBACK, now);
            } else {
                store(entry, null, SableSilhouetteStatus.FAILED, now);
            }
            debug("Sable silhouette limit for {}: {}", id, limit.getMessage());
        } catch (RuntimeException ex) {
            store(entry, null, SableSilhouetteStatus.FAILED, now);
            debug("Sable silhouette failed for {}: {}", id, ex.toString());
        }
    }

    private static void store(Entry entry, SubLevelSilhouette silhouette, byte status, long now) {
        entry.silhouette = silhouette;
        entry.status = status;
        entry.revision++;
        entry.lastBuildTick = now;
        if (RadarConfig.server().sableSilhouetteDebugLogging.get()) {
            int boxes = silhouette == null ? 0 : silhouette.localBoxes().size();
            LOGGER.info("Sable silhouette revision={} status={} boxes={}", entry.revision, status, boxes);
        }
    }

    private static Map<UUID, Entry> entries(ServerLevel level) {
        return BY_DIMENSION.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
    }

    private static void debug(String message, Object... args) {
        if (RadarConfig.server().sableSilhouetteDebugLogging.get()) {
            LOGGER.info(message, args);
        }
    }

    private static final class Entry {
        private SubLevelSilhouette silhouette;
        private int revision;
        private byte status = SableSilhouetteStatus.NONE;
        private boolean dirty = true;
        private long lastBuildTick;
        private long nextBuildTick;
        private long nextRefreshTick;
    }
}
