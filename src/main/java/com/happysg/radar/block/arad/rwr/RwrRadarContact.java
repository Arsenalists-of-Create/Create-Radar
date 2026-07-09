package com.happysg.radar.block.arad.rwr;

import net.minecraft.core.BlockPos;

public record RwrRadarContact(
        String sourceId,
        BlockPos radarPos,
        RadarType radarType,
        float bearingDegrees,
        float signalStrength,
        boolean lockCapable,
        boolean withinRadarRange,
        boolean exactLocked,
        boolean friendly
) {
}
