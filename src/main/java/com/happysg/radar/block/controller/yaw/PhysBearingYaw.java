package com.happysg.radar.block.controller.yaw;

import com.happysg.radar.block.behavior.networks.WeaponNetworkData;
import com.happysg.radar.config.RadarConfig;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.PhysBearingBlockEntity;
import org.valkyrienskies.clockwork.platform.api.ContraptionController;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

public class PhysBearingYaw {

    private static final double MIN_MOVE_PER_TICK = 0.02;
    private static final double MAX_MOVE_PER_TICK = RadarConfig.server().controllerPhysbearingMaxSpeed.get();
    private static final double SNAP_DISTANCE = 37.0;

    private final AutoYawControllerBlockEntity controller;

    private double yawZeroOffsetDeg = 0.0;
    private boolean hasYawZeroOffset = false;
    private double lastYawZeroOffsetDeg = 0.0;

    public PhysBearingYaw(AutoYawControllerBlockEntity controller) {
        this.controller = controller;
    }

    public void tick(PhysBearingBlockEntity mount) {
        rotatePhysBearing(mount);
    }

    public void setTarget(PhysBearingBlockEntity mount, Vec3 targetPos) {
        Vec3 cannonCenter = controller.isUpsideDown()
                ? controller.getBlockPos().below(3).getCenter()
                : controller.getBlockPos().above(3).getCenter();

        double angle = controller.computeYawToTargetDeg(cannonCenter, targetPos);
        double newAngle = AutoYawControllerBlockEntity.wrap360(angle) + 180.0;

        if (hasYawZeroOffset) {
            newAngle = AutoYawControllerBlockEntity.wrap360(newAngle - yawZeroOffsetDeg);
        }

        controller.setInternalTargetAngle(newAngle);
        controller.setRunning(true);
        controller.notifyUpdate();
        controller.setChanged();
    }

    public boolean atTargetYaw(PhysBearingBlockEntity mount, boolean lag) {
        Double actualRad = mount.getActualAngle();
        if (actualRad == null) {
            return false;
        }

        double effectiveTolerance = AutoYawControllerBlockEntity.getToleranceDeg();
        if (!lag) {
            effectiveTolerance += 0.15;
        }

        double currentDeg = AutoYawControllerBlockEntity.wrap360(Math.toDegrees(actualRad));
        double desiredDeg = AutoYawControllerBlockEntity.wrap360(360.0 - controller.getTargetAngle());

        return Math.abs(AutoYawControllerBlockEntity.shortestDelta(currentDeg, desiredDeg))
                < Math.max(effectiveTolerance, AutoYawControllerBlockEntity.getDeadbandDeg());
    }

    public void maybeUpdateYawZeroFromCannonInitialOrientation() {
        if (!(controller.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        var data = WeaponNetworkData.get(serverLevel);
        var cannonPos = data.getMountForController(serverLevel.dimension(), controller.getBlockPos());
        if (cannonPos == null) {
            return;
        }

        if (!(controller.getLevel().getBlockEntity(cannonPos) instanceof CannonMountBlockEntity cannonMount)) {
            return;
        }

        PitchOrientedContraptionEntity ce = cannonMount.getContraption();
        if (ce == null) {
            return;
        }

        if (!(ce.getContraption() instanceof AbstractMountedCannonContraption cannonContraption)) {
            return;
        }

        Direction initial = cannonContraption.initialOrientation();
        if (initial == null || !initial.getAxis().isHorizontal()) {
            return;
        }

        double newOffset = controllerYawForCardinalDirection(initial);

        if (!hasYawZeroOffset) {
            yawZeroOffsetDeg = AutoYawControllerBlockEntity.wrap360(newOffset);
            lastYawZeroOffsetDeg = yawZeroOffsetDeg;
            hasYawZeroOffset = true;
            controller.setChanged();
            return;
        }

        double oldOffset = lastYawZeroOffsetDeg;
        double delta = AutoYawControllerBlockEntity.shortestDelta(oldOffset, newOffset);

        if (Math.abs(delta) < 1.0e-6) {
            return;
        }

        yawZeroOffsetDeg = AutoYawControllerBlockEntity.wrap360(newOffset);
        lastYawZeroOffsetDeg = yawZeroOffsetDeg;

        controller.setInternalTargetAngle(AutoYawControllerBlockEntity.wrap360(controller.getTargetAngle() - delta));
        controller.setInternalMinAngleDeg(AutoYawControllerBlockEntity.wrap360(controller.getMinAngleDeg() - delta));
        controller.setInternalMaxAngleDeg(AutoYawControllerBlockEntity.wrap360(controller.getMaxAngleDeg() - delta));

        controller.notifyUpdate();
        controller.setChanged();
    }

    public void reset() {
        yawZeroOffsetDeg = 0.0;
        hasYawZeroOffset = false;
        lastYawZeroOffsetDeg = 0.0;
    }

    public void read(CompoundTag compound) {
        yawZeroOffsetDeg = compound.getDouble("YawZeroOffsetDeg");
        hasYawZeroOffset = compound.getBoolean("HasYawZeroOffset");
        lastYawZeroOffsetDeg = compound.getDouble("LastYawZeroOffsetDeg");
    }

    public void write(CompoundTag compound) {
        compound.putDouble("YawZeroOffsetDeg", yawZeroOffsetDeg);
        compound.putBoolean("HasYawZeroOffset", hasYawZeroOffset);
        compound.putDouble("LastYawZeroOffsetDeg", lastYawZeroOffsetDeg);
    }

    private void rotatePhysBearing(PhysBearingBlockEntity mount) {
        ensureFollowAngleMode(mount);

        if (!controller.isRunningController()) {
            return;
        }

        double rpm = Math.abs(controller.getSpeed());
        if (rpm <= 0.0) {
            return;
        }

        Double actualRad = mount.getActualAngle();
        if (actualRad == null) {
            return;
        }

        double currentPhysDeg = AutoYawControllerBlockEntity.wrap360(Math.toDegrees(actualRad));
        double currentCtlDeg = AutoYawControllerBlockEntity.wrap360(360.0 - currentPhysDeg);
        double desiredCtlDeg = controller.getTargetAngle();

        double diffCtl = AutoYawControllerBlockEntity.shortestDelta(currentCtlDeg, desiredCtlDeg);
        double distCtl = Math.abs(diffCtl);

        if (distCtl <= Math.max(AutoYawControllerBlockEntity.getToleranceDeg(), AutoYawControllerBlockEntity.getDeadbandDeg())) {
            double desiredPhysDeg = AutoYawControllerBlockEntity.wrap360(360.0 - desiredCtlDeg);
            mount.setAngle((float) desiredPhysDeg);
            mount.notifyUpdate();
            return;
        }

        double stepDeg = getStep(SNAP_DISTANCE, distCtl);
        double nextCtlDeg;

        double move = Math.min(stepDeg, distCtl);
        nextCtlDeg = AutoYawControllerBlockEntity.wrap360(currentCtlDeg + Math.signum(diffCtl) * move);



        double nextPhysDeg = AutoYawControllerBlockEntity.wrap360(360.0 - nextCtlDeg);
        mount.setAngle((float) nextPhysDeg);
        mount.notifyUpdate();
    }

    private static void ensureFollowAngleMode(PhysBearingBlockEntity mount) {
        ScrollOptionBehaviour<ContraptionController.LockedMode> mode = mount.getMovementMode();
        if (mode != null && mode.getValue() != ContraptionController.LockedMode.FOLLOW_ANGLE.ordinal()) {
            mode.setValue(ContraptionController.LockedMode.FOLLOW_ANGLE.ordinal());
        }
    }

    private static double controllerYawForCardinalDirection(Direction d) {
        return switch (d) {
            case SOUTH -> 0.0;
            case WEST -> 90.0;
            case NORTH -> 180.0;
            case EAST -> 270.0;
            default -> 0.0;
        };
    }

    private double getStep(double range, double dist) {
        double rpm = Math.abs(controller.getSpeed());
        double r = Math.min(256.0, Math.max(0.0, rpm));

        double gamma = 1.6;
        double x = r / 256.0;
        double effectiveRpm = 1.0 + (r - 1.0) * Math.pow(x, gamma);

        double degPerTick = effectiveRpm * 0.3;
        double radPerTick = degPerTick * (Math.PI / 180.0);

        double maxStep = range * radPerTick;
        maxStep = Math.max(MIN_MOVE_PER_TICK, Math.min(MAX_MOVE_PER_TICK, maxStep));

        return Math.min(dist, maxStep);
    }
}
