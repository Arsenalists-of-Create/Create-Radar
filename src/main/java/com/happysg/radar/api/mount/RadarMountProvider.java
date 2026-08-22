package com.happysg.radar.api.mount;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Finds a RadarMountAdapter for a mount at a given position.
 */
@FunctionalInterface
public interface RadarMountProvider {
    /**
     * Attempts to create an adapter for the block at the supplied position.
     *
     * @return an adapter if this provider recognizes the mount, otherwise null.
     */
    @Nullable
    RadarMountAdapter find(Level level, BlockPos pos);
}