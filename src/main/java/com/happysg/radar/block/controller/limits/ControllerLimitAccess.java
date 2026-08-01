package com.happysg.radar.block.controller.limits;

import com.happysg.radar.block.controller.kinetic.CannonAxis;


public interface ControllerLimitAccess {
    CannonAxis getControlledAxis();

    ControllerMovementLimits getMovementLimits();


    boolean setMovementLimits(double minDegrees, double maxDegrees);

    boolean isTargetLimitConstrained();
}
