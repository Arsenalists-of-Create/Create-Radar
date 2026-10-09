package com.happysg.radar.api.radar;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Optional display capability for radar sources.
 */
public interface RadarDisplaySource {

    /**
     * Describes the general monitor presentation of this radar.
     */
    RadarDisplayProfile getRadarDisplayProfile();

    /**
     * Current world-space radar/sweep angle in degrees.
     */
    default float getDisplayAngleDegrees() {
        return 0.0f;
    }

    /**
     * Angular display velocity in degrees per tick.
     * Used for client interpolation when the live source cannot be resolved.
     */
    default float getDisplayAngularSpeedDegreesPerTick() {
        return 0.0f;
    }

    /**
     * Current field of view.
     */
    default float getDisplayFovDegrees() {
        return 360.0f;
    }

    /**
     * Physical radar direction.
     */
    @Nullable
    default Direction getDisplayDirection() {
        return null;
    }
}