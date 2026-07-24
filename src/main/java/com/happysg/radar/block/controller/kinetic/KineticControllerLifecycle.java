package com.happysg.radar.block.controller.kinetic;

public enum KineticControllerLifecycle {
    CLOSED,
    WAITING_FOR_ASSEMBLY,
    WAITING_FOR_LOCK,
    ARMING,
    VERIFYING_ENDPOINT,
    MOVING,
    SETTLING,
    REVERSING,
    BLOCKED
}
