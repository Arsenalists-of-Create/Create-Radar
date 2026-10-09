package com.happysg.radar.api.radar;

import com.happysg.radar.api.tracking.RadarSource;
import net.minecraft.core.BlockPos;

/**
 * A radar source that may be linked into a Create: Radars network.
 * <p>
 * The position is the canonical level-space block position used by
 * NetworkData and Data Links. World/render-space positions should not be
 * returned here.
 */
public interface NetworkRadarSource extends RadarSource {
    /**
     * Canonical block position used to identify this radar as a network endpoint.
     */
    BlockPos getRadarPosition();
}