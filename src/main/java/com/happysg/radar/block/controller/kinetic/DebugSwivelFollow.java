package com.happysg.radar.block.controller.kinetic;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/** Transient player-follow override used by the in-game Swivel debug command. */
public final class DebugSwivelFollow {
    private static final double MIN_DIRECTION_LENGTH_SQUARED = 1.0e-8;

    @Nullable private UUID playerId;
    @Nullable private KineticMountFrame frame;
    @Nullable private Direction bearingDirection;
    private double productionTarget;
    private boolean productionRunning;

    public void start(UUID playerId, KineticMountFrame frame, Direction bearingDirection,
                      double productionTarget, boolean productionRunning) {
        this.playerId = playerId;
        this.frame = frame;
        this.bearingDirection = bearingDirection;
        this.productionTarget = productionTarget;
        this.productionRunning = productionRunning;
    }

    public void update(double controllerTarget, DebugSwivelSweep.TargetCommand command) {
        if (isActive() && Double.isFinite(controllerTarget)) {
            command.accept(controllerTarget, true);
        }
    }

    public String stop(String reason, DebugSwivelSweep.TargetCommand command) {
        if (!isActive()) {
            return reason;
        }
        double restoreTarget = productionTarget;
        boolean restoreRunning = productionRunning;
        clear();
        command.accept(restoreTarget, restoreRunning);
        return reason;
    }

    public void clear() {
        playerId = null;
        frame = null;
        bearingDirection = null;
    }

    public boolean isActive() {
        return playerId != null;
    }

    public boolean follows(UUID candidate) {
        return candidate != null && candidate.equals(playerId);
    }

    @Nullable
    public UUID playerId() {
        return playerId;
    }

    public boolean matches(KineticMountFrame liveFrame, Direction liveBearingDirection) {
        return isActive() && frame != null && frame.equals(liveFrame)
                && bearingDirection == liveBearingDirection;
    }

    public double persistentTarget(double currentTarget) {
        return isActive() ? productionTarget : currentTarget;
    }

    public boolean persistentRunning(boolean currentRunning) {
        return isActive() ? productionRunning : currentRunning;
    }

    public static double yawTargetDegrees(Vec3 origin, Vec3 target) {
        Vec3 direction = target.subtract(origin);
        if (direction.horizontalDistanceSqr() <= MIN_DIRECTION_LENGTH_SQUARED) {
            return Double.NaN;
        }
        return KineticAngleMath.wrap360(
                Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0);
    }

    public static double pitchTargetDegrees(Vec3 origin, Vec3 target) {
        Vec3 direction = target.subtract(origin);
        if (direction.lengthSqr() <= MIN_DIRECTION_LENGTH_SQUARED) {
            return Double.NaN;
        }
        return Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()));
    }

    public enum ToggleState {
        STARTED,
        STOPPED,
        FAILED
    }

    public record ToggleResult(ToggleState state, String reason,
                               @Nullable Direction bearingDirection) {
        public static ToggleResult started(Direction bearingDirection) {
            return new ToggleResult(ToggleState.STARTED, "started", bearingDirection);
        }

        public static ToggleResult stopped() {
            return new ToggleResult(ToggleState.STOPPED, "stopped", null);
        }

        public static ToggleResult failed(String reason) {
            return new ToggleResult(ToggleState.FAILED, reason, null);
        }
    }
}
