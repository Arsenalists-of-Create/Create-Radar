package com.happysg.radar.block.controller.kinetic;

/** Selects the legal signed controller-space path to a target angle. */
@FunctionalInterface
public interface ControllerAngleDelta {
    double remainingDegrees(double currentControllerDegrees,
                            double targetControllerDegrees);

    ControllerAngleDelta SHORTEST = KineticAngleMath::shortestDelta;
}
