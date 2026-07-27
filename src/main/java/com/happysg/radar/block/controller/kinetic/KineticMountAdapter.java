package com.happysg.radar.block.controller.kinetic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

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

    /**
     * World basis of the bearing's containing (parent) level. This must never
     * be derived from the child assembly rotated by this bearing.
     */
    @Nullable
    KineticAimFrame aimFrame();

    default double controllerTargetForWorldDirection(Vec3 worldDirection) {
        KineticAimFrame aim = aimFrame();
        return aim == null ? Double.NaN
                : aim.controllerTargetDegrees(axis(), worldDirection);
    }

    default double getPhysicalControllerAngleDegrees() {
        KineticMountFrame identity = frameIdentity();
        double physical = getPhysicalAngleDegrees();
        if (identity == null || !Double.isFinite(physical)) {
            return Double.NaN;
        }
        double controllerAngle = identity.controllerTargetFor(physical);
        return axis() == CannonAxis.PITCH
                ? KineticAngleMath.wrap180(controllerAngle) : controllerAngle;
    }

    default Vec3 getPhysicalWorldDirection() {
        KineticMountFrame identity = frameIdentity();
        KineticAimFrame aim = aimFrame();
        double physicalController = getPhysicalControllerAngleDegrees();
        if (identity == null || aim == null || !Double.isFinite(physicalController)) {
            return Vec3.ZERO;
        }
        if (axis() == CannonAxis.YAW) {
            return aim.worldDirection(physicalController, 0.0);
        }
        Direction initial = identity.cannonInitialOrientation();
        double yaw = switch (initial == null ? Direction.SOUTH : initial) {
            case SOUTH -> 0.0;
            case WEST -> 90.0;
            case NORTH -> 180.0;
            case EAST -> 270.0;
            default -> 0.0;
        };
        return aim.worldDirection(yaw, physicalController);
    }

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
