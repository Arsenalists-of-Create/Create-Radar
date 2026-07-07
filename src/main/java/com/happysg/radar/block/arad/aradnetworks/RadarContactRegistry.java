package com.happysg.radar.block.arad.aradnetworks;

import net.minecraft.server.level.ServerLevel;

import java.util.Set;
import java.util.UUID;

public final class RadarContactRegistry {
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

    public static boolean isInRange(ServerLevel level, UUID shipId) {
        return RadarContactRegistryData.get(level).isInRange(shipId);
    }

    public static boolean isLocked(ServerLevel level, UUID shipId) {
        return RadarContactRegistryData.get(level).isLocked(shipId);
    }

    public static boolean isEngaged(ServerLevel level, UUID shipId) {
        return RadarContactRegistryData.get(level).isEngaged(shipId);
    }

    public static Set<String> getInRangeSources(ServerLevel level, UUID shipId) {
        return RadarContactRegistryData.get(level).getInRangeSources(shipId);
    }

    public static int getInRangeSignal(ServerLevel level, UUID shipId) {
        return RadarContactRegistryData.get(level).getInRangeSignal(shipId);
    }

    public static void unLock(ServerLevel level, UUID shipId){
        RadarContactRegistryData.get(level).unlockShip(shipId);
    }

    public static void tickDecay(ServerLevel level) {
        RadarContactRegistryData.get(level).tickDecay();
    }
}
