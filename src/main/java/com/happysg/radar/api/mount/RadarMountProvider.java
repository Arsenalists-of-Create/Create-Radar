package com.happysg.radar.api.mount;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Finds a {@link RadarMountAdapter} for a mount at a given position.
 *
 * <p>Providers should return {@code null} when they do not recognize the
 * supplied position.</p>
 */
@FunctionalInterface
public interface RadarMountProvider {
    /**
     * Attempts to create an adapter for the mount at the supplied position.
     *
     * @param level level containing the mount
     * @param pos position to inspect
     * @return an adapter if this provider recognizes the mount,
     *         otherwise {@code null}
     */
    @Nullable
    RadarMountAdapter find(Level level, BlockPos pos);
}