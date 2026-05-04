package com.happysg.radar.block.controller.pitch;

import com.happysg.radar.compat.Mods;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.PhysBearingBlockEntity;
import org.valkyrienskies.clockwork.platform.api.ContraptionController;

import javax.annotation.Nullable;

public class PhysBearingPitch {
    private static final double SNAP_DISTANCE = 37.0;
    private static final double MIN_MOVE_PER_TICK = 0.02;
    private static final double MAX_MOVE_PER_TICK = 2.0;

    private static final double MIN_DEG_PER_TICK = 0.30;
    private static final double MAX_DEG_PER_TICK = 18.0;
    private static final double CURVE_GAMMA = 1.65;

    private final AutoPitchControllerBlockEntity controller;

    @Nullable
    private Vec3 desiredTarget = null;

    @Nullable
    private Vec3 smoothedTarget = null;

    private double lastCommandedDeg = Double.NaN;

    public PhysBearingPitch(AutoPitchControllerBlockEntity controller) {
        this.controller = controller;
    }

    public void tick(PhysBearingBlockEntity mount) {
        rotatePhysBearing(mount);
    }

    public void setTarget(PhysBearingBlockEntity mount, Vec3 targetPos) {
        SubLevelAccess ship = getShipIfPresent();
        Vec3 desired = ship != null ? toShipSpace(ship, targetPos) : targetPos;

        desiredTarget = desired;
        if (smoothedTarget == null) {
            smoothedTarget = desired;
        }

        controller.setRunning(true);
        lastCommandedDeg = Double.NaN;

        controller.notifyUpdate();
        controller.setChanged();
    }

    public boolean atTargetPitch(PhysBearingBlockEntity mount, boolean lag) {
        Double actualRad = mount.getActualAngle();
        if (actualRad == null) {
            return false;
        }

        double tol = AutoPitchControllerBlockEntity.getPhysToleranceDeg();
        if (!lag) {
            tol += 0.15;
        }

        double currentDeg = AutoPitchControllerBlockEntity.wrap360(Math.toDegrees(actualRad));
        double desiredDeg = AutoPitchControllerBlockEntity.wrap360(controller.getTargetAngle());

        return Math.abs(AutoPitchControllerBlockEntity.shortestDelta(currentDeg, desiredDeg))
                < Math.max(tol, AutoPitchControllerBlockEntity.getDeadbandDeg());
    }

    public void reset() {
        desiredTarget = null;
        smoothedTarget = null;
        lastCommandedDeg = Double.NaN;
    }

    public void read(CompoundTag compound) {
        reset();
    }

    public void write(CompoundTag compound) {
    }

    private void rotatePhysBearing(PhysBearingBlockEntity mount) {
        ensureFollowAngleMode(mount);

        double rpmAbs = Math.abs(controller.getSpeed());
        if (rpmAbs <= 0.0) {
            return;
        }

        if (!controller.isRunningController()) {
            return;
        }

        updateSmoothedTargetAndAngle(rpmAbs, mount);

        Double actualRad = mount.getActualAngle();
        if (actualRad == null) {
            return;
        }

        double currentDeg = AutoPitchControllerBlockEntity.wrap360(Math.toDegrees(actualRad));
        double desiredDeg = AutoPitchControllerBlockEntity.wrap360(controller.getTargetAngle());



        if (Double.isNaN(lastCommandedDeg)) {
            lastCommandedDeg = currentDeg;
        }

        double desiredContinuous = AutoPitchControllerBlockEntity.unwrapNear(lastCommandedDeg, desiredDeg);

        if (Math.abs(AutoPitchControllerBlockEntity.shortestDelta(currentDeg, desiredDeg))
                <= Math.max(AutoPitchControllerBlockEntity.getPhysToleranceDeg(), AutoPitchControllerBlockEntity.getDeadbandDeg())) {
            mount.setAngle((float) desiredDeg);
            mount.notifyUpdate();
            controller.setRunning(false);
            lastCommandedDeg = desiredContinuous;
            return;
        }

        lastCommandedDeg = desiredContinuous;
        mount.setAngle((float) AutoPitchControllerBlockEntity.wrap360(desiredContinuous));
        mount.notifyUpdate();
    }

    private void updateSmoothedTargetAndAngle(double rpmAbs, PhysBearingBlockEntity mount) {
        if (desiredTarget == null) {
            return;
        }

        Direction facing = controller.getBlockState().getValue(AutoPitchControllerBlock.HORIZONTAL_FACING);
        Vec3 pivot = mount.getBlockPos().getCenter();

        if (smoothedTarget == null) {
            smoothedTarget = desiredTarget;
        } else {
            Vec3 delta = desiredTarget.subtract(smoothedTarget);
            double dist = delta.length();

            if (dist > SNAP_DISTANCE) {
                smoothedTarget = desiredTarget;
            } else if (dist > 1.0e-6) {
                double radius = diskRadius(pivot, smoothedTarget, facing);
                double step = stepTowardTarget(radius, dist, rpmAbs);
                smoothedTarget = smoothedTarget.add(delta.scale(step / dist));
            }
        }

        double newAngle = rollAroundFacingDeg(pivot, smoothedTarget, facing);
        if (Math.abs(AutoPitchControllerBlockEntity.shortestDelta(controller.getTargetAngle(), newAngle))
                < AutoPitchControllerBlockEntity.getDeadbandDeg()) {
            return;
        }

        controller.setInternalTargetAngle(
                approachWrapped(controller.getTargetAngle(), newAngle)
        );
    }

    private static void ensureFollowAngleMode(PhysBearingBlockEntity mount) {
        ScrollOptionBehaviour<ContraptionController.LockedMode> mode = mount.getMovementMode();
        if (mode != null && mode.getValue() != ContraptionController.LockedMode.FOLLOW_ANGLE.ordinal()) {
            mode.setValue(ContraptionController.LockedMode.FOLLOW_ANGLE.ordinal());
        }
    }

    @Nullable
    private SubLevelAccess getShipIfPresent() {
        if (controller.getLevel() == null) {
            return null;
        }

        if (!Mods.SABLE.isLoaded()) {
            return null;
        }

        return SableCompanion.INSTANCE.getContaining(controller.getLevel(), controller.getBlockPos());
    }

    private static Vec3 toShipSpace(SubLevelAccess ship, Vec3 worldPos) {
        Vector3d v = ship.logicalPose().transformPositionInverse(new Vector3d(worldPos.x, worldPos.y, worldPos.z));
        return new Vec3(v.x(), v.y(), v.z());
    }

    private static Vec3 forwardHoriz(Direction facing) {
        Vec3 f = new Vec3(facing.getStepX(), 0, facing.getStepZ());
        if (f.lengthSqr() < 1.0e-8) {
            return new Vec3(0, 0, 1);
        }
        return f.normalize();
    }

    private static Vec3 rightHoriz(Direction facing) {
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 fwd = forwardHoriz(facing);
        Vec3 r = up.cross(fwd);
        double ls = r.lengthSqr();
        return ls < 1.0e-8 ? new Vec3(1, 0, 0) : r.scale(1.0 / Math.sqrt(ls));
    }

    private static double rollAroundFacingDeg(Vec3 pivot, Vec3 target, Direction facing) {
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 fwd = forwardHoriz(facing);
        Vec3 right = rightHoriz(facing);

        Vec3 v = target.subtract(pivot);
        Vec3 vDisk = v.subtract(fwd.scale(v.dot(fwd)));

        double r = vDisk.dot(right);
        double u = vDisk.dot(up);

        if (Math.abs(r) < 1.0e-10 && Math.abs(u) < 1.0e-10) {
            return 0.0;
        }

        return AutoPitchControllerBlockEntity.wrap360(Math.toDegrees(Math.atan2(u, r)));
    }

    private static double diskRadius(Vec3 pivot, Vec3 target, Direction facing) {
        Vec3 fwd = forwardHoriz(facing);
        Vec3 v = target.subtract(pivot);
        Vec3 vDisk = v.subtract(fwd.scale(v.dot(fwd)));
        return Math.sqrt(vDisk.lengthSqr());
    }

    private static double stepTowardTarget(double radius, double distToSmoothed, double rpmAbs) {
        double degPerTick = degPerTickFromRpm(rpmAbs);
        double radPerTick = degPerTick * (Math.PI / 180.0);

        double maxStep = radius * radPerTick;
        maxStep = Math.max(MIN_MOVE_PER_TICK, Math.min(MAX_MOVE_PER_TICK, maxStep));

        return Math.min(distToSmoothed, maxStep);
    }

    private static double degPerTickFromRpm(double rpmAbs) {
        double r = Math.max(0.0, Math.min(256.0, rpmAbs));

        double t;
        if (r <= 1.0) {
            t = 0.0;
        } else {
            t = (r - 1.0) / 255.0;
            t = Math.max(0.0, Math.min(1.0, t));
        }

        double shaped = Math.pow(t, CURVE_GAMMA);
        return MIN_DEG_PER_TICK + (MAX_DEG_PER_TICK - MIN_DEG_PER_TICK) * shaped;
    }

    private static double approachWrapped(double currentWrapped, double newWrapped) {
        return AutoPitchControllerBlockEntity.wrap360(
                currentWrapped + AutoPitchControllerBlockEntity.shortestDelta(currentWrapped, newWrapped)
        );
    }
}
