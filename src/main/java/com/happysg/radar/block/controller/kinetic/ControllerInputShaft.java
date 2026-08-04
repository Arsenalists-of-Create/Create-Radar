package com.happysg.radar.block.controller.kinetic;

import net.minecraft.core.Direction;

/**
 * Client rendering contract for controllers that sample, rather than join,
 * an adjacent kinetic input.
 */
public interface ControllerInputShaft {
    double getAvailableInputSpeed();

    Direction getInputShaftDirection();
}
