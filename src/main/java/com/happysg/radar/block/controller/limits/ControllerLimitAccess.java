package com.happysg.radar.block.controller.limits;

import com.happysg.radar.block.controller.kinetic.CannonAxis;


public interface ControllerLimitAccess {
    CannonAxis getControlledAxis();

    ControllerMovementLimits getMovementLimits();

    default ControllerMovementLimits getSupportedMovementLimits() {
        return ControllerMovementLimits.defaults(getControlledAxis());
    }

    default boolean hasAssembledControlledMount() {
        return false;
    }

    boolean setMovementLimits(double minDegrees, double maxDegrees);

    boolean isTargetLimitConstrained();
}
