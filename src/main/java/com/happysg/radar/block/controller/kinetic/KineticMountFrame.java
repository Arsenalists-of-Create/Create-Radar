package com.happysg.radar.block.controller.kinetic;

import net.minecraft.core.Direction;

import javax.annotation.Nullable;
import java.util.UUID;

/** Persistent identity and deterministic controller-to-bearing conversion. */
public record KineticMountFrame(int version,
                                Direction bearingFacing,
                                Direction controllerFacing,
                                UUID assemblyId,
                                @Nullable Direction cannonInitialOrientation,
                                int conversionSign,
                                double controllerNeutralDegrees) {
    public static final int CURRENT_VERSION = 2;

    public KineticMountFrame {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("unsupported kinetic frame version " + version);
        }
        if (conversionSign != -1 && conversionSign != 1) {
            throw new IllegalArgumentException("conversionSign must be -1 or 1");
        }
    }

    public double bearingTargetFor(double controllerTargetDegrees) {
        double relative = KineticAngleMath.shortestDelta(controllerNeutralDegrees, controllerTargetDegrees);
        return KineticAngleMath.wrap360(conversionSign * relative);
    }

    public double controllerTargetFor(double bearingAngleDegrees) {
        double relative = KineticAngleMath.shortestDelta(0.0, bearingAngleDegrees);
        return KineticAngleMath.wrap360(controllerNeutralDegrees + conversionSign * relative);
    }

    /**
     * Converts controller-space angles into Simulated's bearing-local angle sign.
     * The physical axis belongs to the bearing, not the side-adjacent controller.
     */
    public static int conversionSignFor(CannonAxis axis, Direction bearingFacing,
                                        Direction controllerFacing,
                                        @Nullable Direction cannonInitialOrientation) {
        if (axis == CannonAxis.YAW) {
            if (bearingFacing.getAxis() != Direction.Axis.Y) {
                throw new IllegalArgumentException("yaw bearing must use the Y axis");
            }
            return bearingFacing == Direction.UP ? -1 : 1;
        }

        if (bearingFacing.getAxis() == Direction.Axis.Y) {
            throw new IllegalArgumentException("pitch bearing must use a horizontal axis");
        }
        if (cannonInitialOrientation != null
                && cannonInitialOrientation.getAxis().isHorizontal()
                && cannonInitialOrientation.getAxis() != bearingFacing.getAxis()) {
            int upwardSign = bearingFacing.getStepZ() * cannonInitialOrientation.getStepX()
                    - bearingFacing.getStepX() * cannonInitialOrientation.getStepZ();
            if (upwardSign != 0) {
                return Integer.signum(upwardSign);
            }
        }

        // A non-cannon fixture has no forward vector. Preserve the explicit
        // controller/bearing mirroring convention as its deterministic fallback.
        return bearingFacing == controllerFacing ? 1 : -1;
    }
}
