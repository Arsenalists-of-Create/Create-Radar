package com.happysg.radar.block.controller.kinetic;

import net.minecraft.core.Direction;

import javax.annotation.Nullable;
import java.util.function.DoubleConsumer;

/** Captured-pose out-and-back motion used by the in-game Swivel debug command. */
public final class DebugSwivelSweep {
    private static final double TARGET_EPSILON = 1.0e-6;
    private static final int STAGE_TIMEOUT_PADDING = 100;
    private static final int HARD_TIMEOUT_TICKS = 2400;

    private Stage stage = Stage.IDLE;
    private double offsetDegrees;
    private double capturedPoseTarget;
    private double productionTarget;
    private boolean productionRunning;
    private double commandedDegrees;
    private boolean commandedRunning;
    private int stageTicks;
    private int stageTimeout;
    private int totalTicks;

    public void start(double degrees, double capturedPoseTarget,
                      double productionTarget, boolean productionRunning,
                      double effectiveDegreesPerTick, TargetCommand commandTarget) {
        this.offsetDegrees = degrees;
        this.capturedPoseTarget = capturedPoseTarget;
        this.productionTarget = productionTarget;
        this.productionRunning = productionRunning;
        this.stage = Stage.MOVING_OUT;
        this.stageTicks = 0;
        this.totalTicks = 0;
        this.stageTimeout = stageTimeout(Math.abs(degrees), effectiveDegreesPerTick);
        command(capturedPoseTarget + degrees, true, commandTarget);
    }

    /** Compatibility helper for deterministic tests. */
    public void start(double degrees, DoubleConsumer commandTarget) {
        start(degrees, 0.0, 0.0, true, 1.0,
                (target, running) -> commandTarget.accept(target));
    }

    public void enforce(double controllerTargetDegrees, TargetCommand commandTarget) {
        if (stage != Stage.IDLE
                && (Math.abs(KineticAngleMath.shortestDelta(
                        controllerTargetDegrees, commandedDegrees)) > TARGET_EPSILON
                || !commandedRunning)) {
            commandTarget.accept(commandedDegrees, commandedRunning);
        }
    }

    public void enforce(double controllerTargetDegrees, DoubleConsumer commandTarget) {
        enforce(controllerTargetDegrees, (target, running) -> commandTarget.accept(target));
    }

    /** @return completion/cancellation reason, or null while still active. */
    @Nullable
    public String tick(boolean validSelection, boolean atDestination,
                       double effectiveDegreesPerTick, TargetCommand commandTarget) {
        if (stage == Stage.IDLE) {
            return null;
        }
        stageTicks++;
        totalTicks++;
        if (!validSelection) {
            return cancel("selection_invalid", commandTarget);
        }
        if (totalTicks > HARD_TIMEOUT_TICKS) {
            return cancel("hard_timeout", commandTarget);
        }
        if (stageTicks > stageTimeout) {
            return cancel("stage_timeout_" + stage.name().toLowerCase(), commandTarget);
        }
        if (!atDestination) {
            return null;
        }

        if (stage == Stage.MOVING_OUT) {
            stage = Stage.RETURNING_CAPTURED_POSE;
            stageTicks = 0;
            stageTimeout = stageTimeout(Math.abs(offsetDegrees), effectiveDegreesPerTick);
            command(capturedPoseTarget, true, commandTarget);
            return null;
        }

        command(productionTarget, productionRunning, commandTarget);
        stage = Stage.IDLE;
        return "completed";
    }

    /** Compatibility helper for deterministic tests. */
    public void tick(boolean kineticSelected, boolean atDestination, DoubleConsumer commandTarget) {
        tick(kineticSelected, atDestination, 1.0,
                (target, running) -> commandTarget.accept(target));
    }

    public String cancel(String reason, TargetCommand commandTarget) {
        if (stage == Stage.IDLE) {
            return reason;
        }
        command(productionTarget, productionRunning, commandTarget);
        stage = Stage.IDLE;
        return reason;
    }

    public boolean isActive() {
        return stage != Stage.IDLE;
    }

    private void command(double degrees, boolean running, TargetCommand commandTarget) {
        commandedDegrees = degrees;
        commandedRunning = running;
        commandTarget.accept(degrees, running);
    }

    private static int stageTimeout(double travelDegrees, double effectiveDegreesPerTick) {
        if (!Double.isFinite(effectiveDegreesPerTick) || effectiveDegreesPerTick <= 1.0e-6) {
            return STAGE_TIMEOUT_PADDING;
        }
        return Math.min(HARD_TIMEOUT_TICKS,
                (int) Math.ceil(2.0 * travelDegrees / effectiveDegreesPerTick)
                        + STAGE_TIMEOUT_PADDING);
    }

    @FunctionalInterface
    public interface TargetCommand {
        void accept(double targetDegrees, boolean running);
    }

    public record StartResult(boolean started, String reason, @Nullable Direction bearingDirection) {
        public static StartResult started(Direction bearingDirection) {
            return new StartResult(true, "started", bearingDirection);
        }

        public static StartResult failed(String reason) {
            return new StartResult(false, reason, null);
        }
    }

    private enum Stage {
        IDLE,
        MOVING_OUT,
        RETURNING_CAPTURED_POSE
    }
}
