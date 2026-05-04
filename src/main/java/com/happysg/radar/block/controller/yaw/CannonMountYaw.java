package com.happysg.radar.block.controller.yaw;

import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.compat.cbc.VS2CannonTargeting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import java.util.List;

public class CannonMountYaw {

    private final AutoYawControllerBlockEntity controller;

    public CannonMountYaw(AutoYawControllerBlockEntity controller) {
        this.controller = controller;
    }

    public void tick(CannonMountBlockEntity mount) {
        rotateCBC(mount);
    }

    public void setTarget(CannonMountBlockEntity mount, Vec3 targetPos) {
        if (!(controller.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (PhysicsHandler.isBlockInPlotyard(controller.getLevel(), controller.getBlockPos())) {
            List<List<Double>> angles = VS2CannonTargeting.calculatePitchAndYawVS2(mount, targetPos, serverLevel);
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

    public boolean atTargetYaw(CannonMountBlockEntity mount, boolean lag) {
        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null) {
            return false;
        }

        double effectiveTolerance = AutoYawControllerBlockEntity.getToleranceDeg();
        if (!lag) {
            effectiveTolerance += 0.15;
        }

        double desired = AutoYawControllerBlockEntity.wrap360(controller.getTargetAngle());
        double current = controller.hasLastCbcYawWritten()
                ? AutoYawControllerBlockEntity.wrap360(controller.getLastCbcYawWritten())
                : AutoYawControllerBlockEntity.wrap360(contraption.yaw);

        return Math.abs(AutoYawControllerBlockEntity.shortestDelta(current, desired)) < effectiveTolerance;
    }

    private void rotateCBC(CannonMountBlockEntity mount) {
        if (!controller.isRunningController()) {
            return;
        }

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null) {
            return;
        }

        double currentYaw = AutoYawControllerBlockEntity.wrap360(contraption.yaw);
        double desiredYaw = AutoYawControllerBlockEntity.wrap360(controller.getTargetAngle());

        double yawDiff = AutoYawControllerBlockEntity.shortestDelta(currentYaw, desiredYaw);
        if (Math.abs(yawDiff) <= AutoYawControllerBlockEntity.getToleranceDeg()) {
            mount.setYaw((float) desiredYaw);
            controller.recordCbcYawWritten(desiredYaw);
            mount.notifyUpdate();
            return;
        }

        double rpm = Math.abs(controller.getSpeed());
        if (rpm <= 0.0) {
            return;
        }



        double stepDeg = rpm / 24.0;
        double move = Math.signum(yawDiff) * Math.min(Math.abs(yawDiff), stepDeg);
        double nextYaw = AutoYawControllerBlockEntity.wrap360(currentYaw + move);

        mount.setYaw((float) nextYaw);
        controller.recordCbcYawWritten(nextYaw);
        mount.notifyUpdate();
    }
}
