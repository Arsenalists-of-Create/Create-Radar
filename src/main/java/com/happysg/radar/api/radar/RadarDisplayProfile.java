package com.happysg.radar.api.radar;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Description of how a radar should
 * be presented by Create: Radars displays.
 */
public record RadarDisplayProfile(
        ResourceLocation typeId,
        RadarSweepStyle sweepStyle,
        boolean lockCapable,
        boolean renderRelativeToMonitor
) {

    public RadarDisplayProfile {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(sweepStyle, "sweepStyle");
    }
}