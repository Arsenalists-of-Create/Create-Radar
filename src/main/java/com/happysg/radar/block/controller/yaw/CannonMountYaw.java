package com.happysg.radar.block.controller.yaw;

import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.compat.cbc.CannonMountContext;
import com.happysg.radar.compat.cbc.DirectCbcMountMotion;
import com.happysg.radar.compat.cbc.VS2CannonTargeting;
import com.happysg.radar.config.RadarConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import java.util.List;

public class CannonMountYaw {

    private final AutoYawControllerBlockEntity controller;
    private final DirectCbcMountMotion.State motionState =
            new DirectCbcMountMotion.State();

    public CannonMountYaw(AutoYawControllerBlockEntity controller) {
        this.controller = controller;
    }

    public void tick(CannonMountContext mount) {
        rotateCBC(mount);
    }

    public void setTarget(CannonMountContext mount, Vec3 targetPos) {
        if (!(controller.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (PhysicsHandler.isBlockInPlotyard(controller.getLevel(), controller.getBlockPos())) {
            List<List<Double>> angles = VS2CannonTargeting.calculatePitchAndYawVS2(
                    mount, targetPos, serverLevel);
            if (angles == null || angles.isEmpty() || angles.get(0).isEmpty()) {
                return;
            }

            controller.setInternalTargetAngle(angles.get(0).get(1));
            controller.setRunning(true);
            controller.notifyUpdate();
            controller.setChanged();
            return;
        }

        Vec3 cannonCenter = controller.isUpsideDown()
                ? controller.getBlockPos().below(3).getCenter()
                : controller.getBlockPos().above(3).getCenter();

        double angle = controller.computeYawToTargetDeg(cannonCenter, targetPos);
        double newAngle = AutoYawControllerBlockEntity.wrap360(angle) + 180.0;

        controller.setInternalTargetAngle(newAngle);
        controller.setRunning(true);
        controller.notifyUpdate();
        controller.setChanged();
    }

    public boolean atTargetYaw(CannonMountContext mount, boolean lag) {
        return atTargetYaw(mount, lag, 0.0);
    }

    public boolean atTargetYaw(CannonMountContext mount, boolean lag,
                               double minimumToleranceDegrees) {
        return atTargetYaw(mount, lag, minimumToleranceDegrees,
                Double.POSITIVE_INFINITY);
    }

    public boolean atTargetYaw(CannonMountContext mount, boolean lag,
                               double minimumToleranceDegrees,
                               double maximumToleranceDegrees) {
        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null) {
            return false;
        }

        double effectiveTolerance = AutoYawControllerBlockEntity.getToleranceDeg();
        if (!lag) {
            effectiveTolerance += RadarConfig.server().targetLoosenAmount.get();
        }
        effectiveTolerance = Math.max(
                effectiveTolerance, sanitizeTolerance(minimumToleranceDegrees));
        effectiveTolerance = Math.min(effectiveTolerance,
                sanitizeMaximumTolerance(maximumToleranceDegrees));

        double desired = AutoYawControllerBlockEntity.wrap360(controller.getTargetAngle());
        double current = controller.hasLastCbcYawWritten()
                ? AutoYawControllerBlockEntity.wrap360(controller.getLastCbcYawWritten())
                : AutoYawControllerBlockEntity.wrap360(contraption.yaw);

        return Math.abs(AutoYawControllerBlockEntity.shortestDelta(current, desired)) < effectiveTolerance;
    }

    private static double sanitizeTolerance(double tolerance) {
        return Double.isFinite(tolerance) ? Math.max(0.0, tolerance) : 0.0;
    }

    private static double sanitizeMaximumTolerance(double tolerance) {
        return Double.isFinite(tolerance)
                ? Math.max(0.0, tolerance) : Double.POSITIVE_INFINITY;
    }

    private void rotateCBC(CannonMountContext mount) {
        if (!controller.isRunningController()) {
            motionState.reset();
            return;
        }
        if (!mount.supportsDirectYawControl()) {
            motionState.reset();
            return;
        }

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null) {
            motionState.reset();
            return;
        }

        double currentYaw = AutoYawControllerBlockEntity.wrap360(contraption.yaw);
        double desiredYaw = AutoYawControllerBlockEntity.wrap360(controller.getTargetAngle());

        double yawDiff = controller.legalYawDelta(currentYaw, desiredYaw);
        double rpm = Math.abs(controller.getAvailableInputSpeed());
        if (rpm <= 0.0) {
            motionState.reset();
            return;
        }

        double move = motionState.nextStep(yawDiff, rpm);
        if (Math.abs(move) <= 1.0E-9) {
            return;
        }
        double nextYaw = AutoYawControllerBlockEntity.wrap360(currentYaw + move);

        if (!mount.trySetYaw((float) nextYaw)) {
            motionState.reset();
            return;
        }
        controller.recordCbcYawWritten(nextYaw);
        mount.notifyUpdate();
    }
}
