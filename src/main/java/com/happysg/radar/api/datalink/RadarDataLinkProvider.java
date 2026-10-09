package com.happysg.radar.api.datalink;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Resolves addon-defined Data Link endpoints.
 */
@FunctionalInterface
public interface RadarDataLinkProvider {

    /**
     * Attempts to resolve an addon-defined Data Link endpoint.
     *
     * @return the endpoint, or {@code null} when this provider does not recognize the supplied position
     */
    @Nullable
    RadarDataLinkEndpoint find(Level level, BlockPos clickedPos);
}