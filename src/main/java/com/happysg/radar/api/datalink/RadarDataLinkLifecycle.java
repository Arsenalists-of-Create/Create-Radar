package com.happysg.radar.api.datalink;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public interface RadarDataLinkLifecycle {
    default void onRadarDataLinkAttached(ServerLevel level, BlockPos filtererPos, BlockPos dataLinkPos) {}
    default void onRadarDataLinkDetached(ServerLevel level, BlockPos filtererPos, BlockPos dataLinkPos) {}
}
