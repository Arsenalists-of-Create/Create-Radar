package com.happysg.radar.block.arad.aradnetworks;

import net.minecraft.server.level.ServerLevel;

public final class RadarContactRegistry {
    private RadarContactRegistry() {}

    public static void markInRange(ServerLevel level, long shipId, int ttlTicks) {
        RadarContactRegistryData.get(level).markInRange(shipId, ttlTicks);
    }

    public static void markLocked(ServerLevel level, long shipId, int ttlTicks) {
        RadarContactRegistryData.get(level).markLocked(shipId, ttlTicks);
    }
    public static boolean isInRange(ServerLevel level, long shipId) {
        return RadarContactRegistryData.get(level).isInRange(shipId);
    }

    public static boolean isLocked(ServerLevel level, long shipId) {
        return RadarContactRegistryData.get(level).isLocked(shipId);
    }
    public static void unLock(ServerLevel level, long shipId){
        RadarContactRegistryData.get(level).unlockShip(shipId);
    }

    public static void tickDecay(ServerLevel level) {
        RadarContactRegistryData.get(level).tickDecay();
    }
}
