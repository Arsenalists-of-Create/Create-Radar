package com.happysg.radar.block.controller.kinetic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;

/**
 * Mod-neutral feedback from a kinetic cannon mount. Controller generators
 * never assign the mount endpoint's speed, source, network, or sequence
 * context; ordinary Create cog propagation owns that lifecycle. The only
 * physical-side request is waking an already assembled body while it settles.
 */
public interface KineticMountAdapter {
    CannonAxis axis();

    Direction relativeDirection();

    boolean isValid();

    boolean hasSameEndpoint(KineticMountAdapter other);

    boolean isAssembled();

    boolean isLocked();

    double getTargetAngleDegrees();

    /** Assembly-relative physical angle, or NaN when feedback is unavailable. */
    double getPhysicalAngleDegrees();

    /** Keeps the assembled physics body awake while its locked servo settles. */
    boolean wakePhysicalAssembly();

    @Nullable
    KineticMountFrame frameIdentity();

    double getEndpointTheoreticalSpeed();

    /** Sign of target-angle movement produced by positive endpoint RPM. */
    int getPositiveRotationSign();

    boolean isEndpointFree();

    /** Fully detached, zero-speed, context-free, and not overstressed. */
    boolean isEndpointSafelyReleased();

    /** True only when ordinary Create propagation currently sources it here. */
    boolean isDrivenBy(BlockPos controllerPos);

    /** Sequence context is never part of reference-style Swivel control. */
    boolean hasSequenceContext();

    /**
     * Removes legacy sequence data only when the endpoint is already fully
     * detached. This is a migration escape hatch, not an actuation path.
     */
    boolean discardStaleSequenceContextIfFree();

    /** Input RPM available to the servo after mount-specific speed limits. */
    double maximumDriveRpm(double availableInputRpm);

    double effectiveDegreesPerTick(double expectedEndpointSpeed);
}
