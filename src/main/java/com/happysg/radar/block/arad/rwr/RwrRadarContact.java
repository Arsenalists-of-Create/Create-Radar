package com.happysg.radar.block.arad.rwr;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record RwrRadarContact(
        String sourceId,
        BlockPos radarPos,
        ResourceLocation radarTypeId,
        float bearingDegrees,
        float signalStrength,
        boolean lockCapable,
        boolean withinRadarRange,
        boolean exactLocked,
        boolean engaged,
        boolean friendly
) {
}
