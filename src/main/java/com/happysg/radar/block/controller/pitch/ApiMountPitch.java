package com.happysg.radar.block.controller.pitch;

import com.happysg.radar.api.mount.RadarMountAdapter;
import net.minecraft.world.phys.Vec3;

public class ApiMountPitch {

    private final AutoPitchControllerBlockEntity controller;

    public ApiMountPitch(AutoPitchControllerBlockEntity controller) {
        this.controller = controller;
    }

    public void tick(RadarMountAdapter mount) {
        if (!controller.isRunningController()) {
            return;
        }

        if (!mount.isValid() || !mount.isAssembled() || !mount.supportsPitch()) {
            return;
        }

        double current = clampPitch(mount.getPitch());
        double target = clampPitch(controller.getTargetAngle());

        if (!Double.isFinite(current) || !Double.isFinite(target)) {
            return;
        }

        double diff = target - current;
        double tolerance = AutoPitchControllerBlockEntity.getCbcTolerance();

        if (Math.abs(diff) <= tolerance) {
            mount.setPitch(target);
            return;
        }

        double step = Math.abs(controller.getSpeed()) / 24.0;

        if (step <= 0.0) {
            return;
        }

        double next = current + Math.signum(diff) * Math.min(Math.abs(diff), step);
        mount.setPitch(clampPitch(next));
    }

    public void setTarget(RadarMountAdapter mount, Vec3 targetPos) {
        if (!mount.isValid() || !mount.supportsPitch()) {
            return;
        }

        Vec3 origin = mount.getAimOrigin();

        if (origin == null) {
            return;
        }

        double dx = targetPos.x - origin.x;
        double dy = targetPos.y - origin.y;
        double dz = targetPos.z - origin.z;

        double horizontal = Math.sqrt(dx * dx + dz * dz);

        double pitch = Math.toDegrees(
                Math.atan2(dy, horizontal)
        );

        controller.setInternalTargetAngle(clampPitch(pitch));
        controller.setLastTargetPos(targetPos);
        controller.setRunning(true);

        controller.notifyUpdate();
        controller.setChanged();
    }

    public boolean atTargetPitch(RadarMountAdapter mount, boolean lag, double minimumToleranceDegrees) {
        if (!mount.isValid() || !mount.isAssembled() || !mount.supportsPitch()) {
            return false;
        }

        double current = clampPitch(mount.getPitch());
        double target = clampPitch(controller.getTargetAngle());

        if (!Double.isFinite(current) || !Double.isFinite(target)) {
            return false;
        }

        double tolerance = AutoPitchControllerBlockEntity.getCbcTolerance();

        if (!lag) {
            tolerance += 0.15;
        }

        if (Double.isFinite(minimumToleranceDegrees)) {
            tolerance = Math.max(tolerance, Math.max(0.0, minimumToleranceDegrees));
        }

        return Math.abs(target - current) < tolerance;
    }

    private static double clampPitch(double pitch) {
        return Math.max(-90.0, Math.min(90.0, pitch));
    }
}