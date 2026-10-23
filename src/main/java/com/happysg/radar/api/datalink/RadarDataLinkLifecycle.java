
package com.happysg.radar.api.datalink;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Optional lifecycle callbacks for Data Link endpoints.
 *
 * Implement on an addon block entity registered through
 * RadarDataLinkRegistry.
 */
public interface RadarDataLinkLifecycle {
    default void onRadarDataLinkAttached(
            ServerLevel level,
            BlockPos filtererPos,
            BlockPos dataLinkPos
    ) {}

    default void onRadarDataLinkDetached(
            ServerLevel level,
            BlockPos filtererPos,
            BlockPos dataLinkPos
    ) {}
}
