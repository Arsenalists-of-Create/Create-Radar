package com.happysg.radar.api.targeting;

import javax.annotation.Nullable;

/**
 * Allows an addon to replace the world-space target state used by a weapon.
 * Return {@code null} when this provider does not apply. Returned vectors
 * must be expressed in world coordinates.
 */
@FunctionalInterface
public interface RadarTargetingProvider {
    @Nullable
    RadarTargetingSolution resolve(RadarTargetingContext context);
}