package com.happysg.radar.block.controller.yaw;

import com.happysg.radar.api.mount.RadarMountAdapter;
import com.happysg.radar.compat.cbc.DirectCbcMountMotion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class ApiMountYaw {

    private final AutoYawControllerBlockEntity controller;

    private final DirectCbcMountMotion.State motionState = new DirectCbcMountMotion.State();
    private BlockPos trackedMountPos;

    public ApiMountYaw(AutoYawControllerBlockEntity controller) {
        this.controller = controller;
    }

    public void tick(RadarMountAdapter mount) {
        if (controller.getLevel() == null || !controller.isRunningController() || !mount.isValid() || !mount.isAssembled() || !mount.supportsYaw()) {
            motionState.reset();
            trackedMountPos = null;
            return;
        }

        BlockPos mountPos = mount.getMountPos();

        if (!mountPos.equals(trackedMountPos)) {
            motionState.reset();
            trackedMountPos = mountPos.immutable();
        }

        double observedYaw = mount.getYaw();
        double requestedYaw = controller.getTargetAngle();

        if (!Double.isFinite(observedYaw) || !Double.isFinite(requestedYaw)) {
            motionState.reset();
            return;
        }

        double currentYaw = AutoYawControllerBlockEntity.wrap360(observedYaw);
        double desiredYaw = AutoYawControllerBlockEntity.wrap360(requestedYaw);

        double yawDiff = AutoYawControllerBlockEntity.shortestDelta(currentYaw, desiredYaw);
        double rpm = Math.abs(controller.getAvailableInputSpeed());

        if (!Double.isFinite(rpm) || rpm <= 0.0) {
            motionState.reset();
            return;
        }

        double move = motionState.nextTrackingStep(yawDiff, rpm, desiredYaw, controller.getLevel().getGameTime(), true);

        if (Math.abs(move) <= 1.0E-9) {
            return;
        }

        mount.setYaw(AutoYawControllerBlockEntity.wrap360(currentYaw + move));
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

        return Math.abs(AutoYawControllerBlockEntity.shortestDelta(current, desired)) < tolerance;
    }
}