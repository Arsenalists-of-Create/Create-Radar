package com.happysg.radar.block.controller.yaw;

import com.happysg.radar.api.mount.RadarMountAdapter;
import com.happysg.radar.block.controller.kinetic.ControllerMotionProfile;
import com.happysg.radar.debug.MotionTraceRecorder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class ApiMountYaw {

    private final AutoYawControllerBlockEntity controller;

    private final ControllerMotionProfile.State motionState = new ControllerMotionProfile.State();
    private BlockPos trackedMountPos;

    public ApiMountYaw(AutoYawControllerBlockEntity controller) {
        this.controller = controller;
    }

    public void tick(RadarMountAdapter mount) {
        if (controller.getLevel() == null || !controller.isRunningController() || !mount.isValid() || !mount.isAssembled() || !mount.supportsYaw()) {
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "api_mount.yaw_inactive",
                        controller.getLevel(),
                        controller.getBlockPos(),
                        "mount", mount.getMountPos(),
                        "levelPresent", controller.getLevel() != null,
                        "running", controller.isRunningController(),
                        "valid", mount.isValid(),
                        "assembled", mount.isAssembled(),
                        "supportsYaw", mount.supportsYaw(),
                        "previousMount", trackedMountPos,
                        "previousProfileVelocity",
                        motionState.traceVelocityDegPerTick()
                );
            }

            motionState.reset();
            trackedMountPos = null;
            return;
        }

        BlockPos mountPos = mount.getMountPos();

        if (!mountPos.equals(trackedMountPos)) {
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "api_mount.yaw_mount_switch",
                        controller.getLevel(),
                        controller.getBlockPos(),
                        "previousMount", trackedMountPos,
                        "newMount", mountPos,
                        "previousVelocity",
                        motionState.traceVelocityDegPerTick()
                );
            }

            motionState.reset();
            trackedMountPos = mountPos.immutable();
        }

        double observedYaw = mount.getYaw();
        double requestedYaw = controller.getTargetAngle();

        if (!Double.isFinite(observedYaw) || !Double.isFinite(requestedYaw)) {
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "api_mount.yaw_invalid_feedback",
                        controller.getLevel(),
                        controller.getBlockPos(),
                        "mount", mountPos,
                        "observedYaw", observedYaw,
                        "requestedYaw", requestedYaw
                );
            }

            motionState.reset();
            return;
        }

        double currentYaw = AutoYawControllerBlockEntity.wrap360(observedYaw);
        double desiredYaw = AutoYawControllerBlockEntity.wrap360(requestedYaw);

        double yawDiff = AutoYawControllerBlockEntity.shortestDelta(currentYaw, desiredYaw);
        double rpm = Math.abs(controller.getAvailableInputSpeed());

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "api_mount.yaw_feedback",
                    controller.getLevel(),
                    controller.getBlockPos(),
                    "mount", mountPos,
                    "rawYaw", observedYaw,
                    "currentYaw", currentYaw,
                    "desiredYaw", desiredYaw,
                    "yawError", yawDiff,
                    "inputRpm", rpm,
                    "running", controller.isRunningController(),
                    "profileVelocity",
                    motionState.traceVelocityDegPerTick(),
                    "profileTargetRate",
                    motionState.traceTargetRateDegPerTick()
            );
        }

        if (!Double.isFinite(rpm) || rpm <= 0.0) {
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "api_mount.yaw_inactive",
                        controller.getLevel(),
                        controller.getBlockPos(),
                        "mount", mountPos,
                        "reason", "NO_VALID_INPUT_RPM",
                        "rpm", rpm,
                        "currentYaw", currentYaw,
                        "desiredYaw", desiredYaw,
                        "yawError", yawDiff
                );
            }

            motionState.reset();
            return;
        }

        double move = motionState.nextTrackingStep(yawDiff, rpm, desiredYaw, controller.getLevel().getGameTime(), true);

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "api_mount.yaw_smoothing",
                    controller.getLevel(),
                    controller.getBlockPos(),
                    "mount", mountPos,
                    "currentYaw", currentYaw,
                    "desiredYaw", desiredYaw,
                    "yawError", yawDiff,
                    "inputRpm", rpm,
                    "plannedMove", move,
                    "nextYaw",
                    AutoYawControllerBlockEntity.wrap360(currentYaw + move),
                    "profileVelocity",
                    motionState.traceVelocityDegPerTick(),
                    "profileTargetRate",
                    motionState.traceTargetRateDegPerTick(),
                    "profileTargetTick",
                    motionState.traceTargetTick()
            );
        }

        if (Math.abs(move) <= 1.0E-9) {
            return;
        }

        double commandedYaw = AutoYawControllerBlockEntity.wrap360(currentYaw + move);
        mount.setYaw(commandedYaw);

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "api_mount.yaw_write",
                    controller.getLevel(),
                    controller.getBlockPos(),
                    "mount", mountPos,
                    "previousYaw", currentYaw,
                    "commandedYaw", commandedYaw,
                    "observedYawAfterWrite", mount.getYaw(),
                    "plannedMove", move,
                    "inputRpm", rpm
            );
        }
    }

    public void setTarget(RadarMountAdapter mount, Vec3 targetPos) {
        if (!mount.isValid() || !mount.supportsYaw()) {
            return;
        }

        Vec3 origin = mount.getAimOrigin();
        if (origin == null) {
            return;
        }

        double angle = controller.computeYawToTargetDeg(origin, targetPos);
        double targetYaw = AutoYawControllerBlockEntity.wrap360(angle + 180.0);
        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "api_mount.yaw_target",
                    controller.getLevel(),
                    controller.getBlockPos(),
                    "mount", mount.getMountPos(),
                    "aimOrigin", origin,
                    "targetPosition", targetPos,
                    "calculatedYaw", targetYaw,
                    "previousTargetYaw", controller.getTargetAngle()
            );
        }

        controller.setInternalTargetAngle(targetYaw);
        controller.setRunning(true);
        controller.notifyUpdate();
        controller.setChanged();
    }

    public boolean atTargetYaw(RadarMountAdapter mount, boolean lag, double minimumToleranceDegrees, double maximumToleranceDegrees) {
        if (!mount.isValid() || !mount.isAssembled() || !mount.supportsYaw()) {
            return false;
        }

        double current = AutoYawControllerBlockEntity.wrap360(mount.getYaw());
        double desired = AutoYawControllerBlockEntity.wrap360(controller.getTargetAngle());

        if (!Double.isFinite(current) || !Double.isFinite(desired)) {
            return false;
        }

        double tolerance = AutoYawControllerBlockEntity.getToleranceDeg();

        if (!lag) {
            tolerance += 0.15;
        }

        if (Double.isFinite(minimumToleranceDegrees)) {
            tolerance = Math.max(tolerance, Math.max(0.0, minimumToleranceDegrees));
        }

        if (Double.isFinite(maximumToleranceDegrees)) {
            tolerance = Math.min(tolerance, Math.max(0.0, maximumToleranceDegrees));
        }

        double yawError = AutoYawControllerBlockEntity.shortestDelta(current, desired);
        boolean aligned = Math.abs(yawError) < tolerance;

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "api_mount.yaw_alignment",
                    controller.getLevel(),
                    controller.getBlockPos(),
                    "mount", mount.getMountPos(),
                    "currentYaw", current,
                    "desiredYaw", desired,
                    "yawError", yawError,
                    "effectiveTolerance", tolerance,
                    "minimumTolerance", minimumToleranceDegrees,
                    "maximumTolerance", maximumToleranceDegrees,
                    "lag", lag,
                    "alignedForFiring", aligned
            );
        }

        return aligned;
    }
}