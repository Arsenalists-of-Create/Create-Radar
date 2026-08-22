package com.happysg.radar.block.controller.yaw;

import com.happysg.radar.api.mount.RadarMountAdapter;
import net.minecraft.world.phys.Vec3;

public class ApiMountYaw {

    private final AutoYawControllerBlockEntity controller;

    public ApiMountYaw(AutoYawControllerBlockEntity controller) {
        this.controller = controller;
    }

    public void tick(RadarMountAdapter mount) {
        if (!controller.isRunningController()) {
            return;
        }

        if (!mount.isValid() || !mount.isAssembled() || !mount.supportsYaw()) {
            return;
        }

        double currentYaw = AutoYawControllerBlockEntity.wrap360(mount.getYaw());
        double desiredYaw = AutoYawControllerBlockEntity.wrap360(controller.getTargetAngle());

        if (!Double.isFinite(currentYaw) || !Double.isFinite(desiredYaw)) {
            return;
        }

        double yawDiff = AutoYawControllerBlockEntity.shortestDelta(currentYaw, desiredYaw);

        if (Math.abs(yawDiff) <= AutoYawControllerBlockEntity.getToleranceDeg()) {
            mount.setYaw(desiredYaw);
            return;
        }

        double rpm = Math.abs(controller.getSpeed());
        if (rpm <= 0.0) {
            return;
        }

        double stepDeg = rpm / 24.0;
        double move = Math.signum(yawDiff) * Math.min(Math.abs(yawDiff), stepDeg);

        double nextYaw = AutoYawControllerBlockEntity.wrap360(currentYaw + move);

        mount.setYaw(nextYaw);
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

    public boolean atTargetYaw(RadarMountAdapter mount, boolean lag, double minimumToleranceDegrees) {
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

        return Math.abs(AutoYawControllerBlockEntity.shortestDelta(current, desired)) < tolerance;
    }
}