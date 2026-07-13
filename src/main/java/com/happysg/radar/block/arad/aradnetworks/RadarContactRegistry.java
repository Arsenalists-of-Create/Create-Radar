package com.happysg.radar.block.arad.aradnetworks;

import com.happysg.radar.block.arad.rwr.RwrTargetKey;
import com.happysg.radar.block.arad.rwr.RwrTargetReference;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class RadarContactRegistry {
    private static final Map<ResourceKey<Level>, Map<ExactLockKey, Integer>> EXACT_LOCKS_BY_DIMENSION = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<SourceEngagementKey, Integer>> SOURCE_ENGAGEMENTS_BY_DIMENSION = new HashMap<>();

    private RadarContactRegistry() {}

    public static void markInRange(ServerLevel level, UUID shipId, int ttlTicks) {
        RadarContactRegistryData.get(level).markInRange(shipId, ttlTicks);
    }

    public static void markInRange(ServerLevel level, UUID shipId, String sourceId, int ttlTicks) {
        RadarContactRegistryData.get(level).markInRange(shipId, sourceId, ttlTicks);
    }

    public static void markInRange(ServerLevel level, UUID shipId, String sourceId, int ttlTicks, int signalStrength) {
        RadarContactRegistryData.get(level).markInRange(shipId, sourceId, ttlTicks, signalStrength);
    }

    public static void markLocked(ServerLevel level, UUID shipId, int ttlTicks) {
        RadarContactRegistryData.get(level).markLocked(shipId, ttlTicks);
    }

    public static void markEngaged(ServerLevel level, UUID shipId, int ttlTicks) {
        RadarContactRegistryData.get(level).markEngaged(shipId, ttlTicks);
    }

    public static void markEngaged(ServerLevel level, UUID shipId, String sourceId, int ttlTicks) {
        markEngaged(level, shipId, ttlTicks);
        if (sourceId == null || sourceId.isBlank()) {
            return;
        }

        ttlTicks = ttlTicks <= 0 ? RadarContactRegistryData.DEFAULT_ENGAGED_TTL : ttlTicks;
        SOURCE_ENGAGEMENTS_BY_DIMENSION
                .computeIfAbsent(level.dimension(), ignored -> new HashMap<>())
                .merge(new SourceEngagementKey(shipId, sourceId), ttlTicks, Math::max);
    }

    public static boolean isInRange(ServerLevel level, UUID shipId) {
        return RadarContactRegistryData.get(level).isInRange(shipId);
    }

    public static boolean isLocked(ServerLevel level, UUID shipId) {
        return RadarContactRegistryData.get(level).isLocked(shipId);
    }

    public static boolean isEngaged(ServerLevel level, UUID shipId) {
        return RadarContactRegistryData.get(level).isEngaged(shipId);
    }

    public static boolean isSourceEngaged(ServerLevel level, UUID shipId, String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return false;
        }
        Map<SourceEngagementKey, Integer> engagements = SOURCE_ENGAGEMENTS_BY_DIMENSION.get(level.dimension());
        return engagements != null
                && engagements.getOrDefault(new SourceEngagementKey(shipId, sourceId), 0) > 0;
    }

    public static String radarSourceId(ServerLevel level, BlockPos radarPos) {
        return level.dimension().location() + "|" + radarPos.asLong();
    }

    public static Set<String> getInRangeSources(ServerLevel level, UUID shipId) {
        return RadarContactRegistryData.get(level).getInRangeSources(shipId);
    }

    public static void removeInRangeSource(ServerLevel level, UUID shipId, String sourceId) {
        RadarContactRegistryData.get(level).removeInRangeSource(shipId, sourceId);
    }

    public static int getInRangeSignal(ServerLevel level, UUID shipId) {
        return RadarContactRegistryData.get(level).getInRangeSignal(shipId);
    }

    public static void unLock(ServerLevel level, UUID shipId){
        RadarContactRegistryData.get(level).unlockShip(shipId);
    }

    public static void markExactLocked(ServerLevel level, UUID emitterId, RwrTargetReference target, int ttlTicks) {
        Optional<RwrTargetKey> targetKey = target.lockKey(level);
        if (targetKey.isEmpty()) {
            return;
        }

        ttlTicks = Math.max(1, ttlTicks);
        EXACT_LOCKS_BY_DIMENSION
                .computeIfAbsent(level.dimension(), ignored -> new HashMap<>())
                .merge(new ExactLockKey(emitterId, targetKey.get()), ttlTicks, Math::max);
    }

    public static void clearExactLocked(ServerLevel level, UUID emitterId, RwrTargetReference target) {
        Optional<RwrTargetKey> targetKey = target.lockKey(level);
        if (targetKey.isEmpty()) {
            return;
        }

        Map<ExactLockKey, Integer> locks = EXACT_LOCKS_BY_DIMENSION.get(level.dimension());
        if (locks == null) {
            return;
        }

        locks.remove(new ExactLockKey(emitterId, targetKey.get()));
        if (locks.isEmpty()) {
            EXACT_LOCKS_BY_DIMENSION.remove(level.dimension());
        }
    }

    public static boolean isExactLockedOn(ServerLevel level, UUID emitterId, RwrTargetReference target) {
        Optional<RwrTargetKey> targetKey = target.lockKey(level);
        if (targetKey.isEmpty()) {
            return false;
        }

        Map<ExactLockKey, Integer> locks = EXACT_LOCKS_BY_DIMENSION.get(level.dimension());
        if (locks == null) {
            return false;
        }

        return locks.getOrDefault(new ExactLockKey(emitterId, targetKey.get()), 0) > 0;
    }

    public static void tickDecay(ServerLevel level) {
        RadarContactRegistryData.get(level).tickDecay();
        tickExactLockDecay(level);
        tickSourceEngagementDecay(level);
    }

    private static void tickExactLockDecay(ServerLevel level) {
        Map<ExactLockKey, Integer> locks = EXACT_LOCKS_BY_DIMENSION.get(level.dimension());
        if (locks == null) {
            return;
        }

        Iterator<Map.Entry<ExactLockKey, Integer>> it = locks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ExactLockKey, Integer> entry = it.next();
            int ttl = entry.getValue() - 1;
            if (ttl <= 0) {
                it.remove();
            } else {
                entry.setValue(ttl);
            }
        }

        if (locks.isEmpty()) {
            EXACT_LOCKS_BY_DIMENSION.remove(level.dimension());
        }
    }

    private static void tickSourceEngagementDecay(ServerLevel level) {
        Map<SourceEngagementKey, Integer> engagements = SOURCE_ENGAGEMENTS_BY_DIMENSION.get(level.dimension());
        if (engagements == null) {
            return;
        }

        Iterator<Map.Entry<SourceEngagementKey, Integer>> it = engagements.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<SourceEngagementKey, Integer> entry = it.next();
            int ttl = entry.getValue() - 1;
            if (ttl <= 0) {
                it.remove();
            } else {
                entry.setValue(ttl);
            }
        }

        if (engagements.isEmpty()) {
            SOURCE_ENGAGEMENTS_BY_DIMENSION.remove(level.dimension());
        }
    }

    private record ExactLockKey(UUID emitterId, RwrTargetKey targetKey) {
    }

    private record SourceEngagementKey(UUID shipId, String sourceId) {
    }
}
