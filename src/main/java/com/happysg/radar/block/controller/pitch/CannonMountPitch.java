package com.happysg.radar.block.controller.pitch;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.CannonTargeting;
import com.happysg.radar.compat.cbc.CannonUtil;
import com.happysg.radar.compat.cbc.VS2CannonTargeting;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.compat.vs2.SableUtils;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import javax.annotation.Nullable;
import java.util.List;

public class CannonMountPitch {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final AutoPitchControllerBlockEntity controller;

    public CannonMountPitch(AutoPitchControllerBlockEntity controller) {
        this.controller = controller;
    }

    public void tick(CannonMountBlockEntity mount) {
        rotateCBC(mount);
    }

    public void setTarget(CannonMountBlockEntity mount, Vec3 targetPos) {
        setTargetCBC(mount, targetPos);
    }

    public boolean atTargetPitch(CannonMountBlockEntity mount, boolean lag) {
        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null) {
            return false;
        }

        if (!(contraption.getContraption() instanceof AbstractMountedCannonContraption cannonContraption)) {
            return false;
        }

        double tol = AutoPitchControllerBlockEntity.getCbcTolerance();
        if (!lag) {
            tol += 0.15;
        }

        double currentPitch = contraption.pitch;
        int invert = -cannonContraption.initialOrientation().getStepX() + cannonContraption.initialOrientation().getStepZ();
        currentPitch = currentPitch * -invert;

        return Math.abs(currentPitch - controller.getTargetAngle()) < tol;
    }

    public double getMaxEngagementRangeBlocks(CannonMountBlockEntity mount, ServerLevel sl) {
        PitchOrientedContraptionEntity ce = mount.getContraption();
        if (ce == null) {
            return 0;
        }
        if (!(ce.getContraption() instanceof AbstractMountedCannonContraption cannon)) {
            return 0;
        }

        double r = CannonUtil.getMaxProjectileRangeBlocks(cannon, sl);
        LOGGER.debug("RANGE DBG endpoint={} cannon={} range={} blocks", controller.getBlockPos(), cannon.getClass().getSimpleName(), r);
        return r;
    }

    public boolean canEngageTrack(CannonMountBlockEntity mount, @Nullable RadarTrack track, boolean requireLos, ServerLevel sl) {
        if (track == null) {
            return false;
        }
        if (controller.firingControl == null) {
            return false;
        }
        if (mount.getContraption() == null) {
            return false;
        }
        if (!(mount.getContraption().getContraption() instanceof AbstractMountedCannonContraption)) {
            return false;
        }

        Vec3 p = track.position();
        if (p == null) {
            return false;
        }

        double max = controller.getMaxEngagementRangeBlocks();
        if (max > 0.0) {
            Vec3 start = controller.firingControl.getCannonRayStart();
            if (Mods.SABLE.isLoaded() && PhysicsHandler.isBlockInPlotyard(controller.getLevel(), controller.getBlockPos())) {
                start = PhysicsHandler.getWorldVec(controller.getLevel(), start);
            }
            if (start.distanceToSqr(p) > (max * max)) {
                return false;
            }
        }

        if (Mods.SABLE.isLoaded() && PhysicsHandler.isBlockInPlotyard(controller.getLevel(), controller.getBlockPos())) {
            Vec3 mountPos = SableUtils.getWorldVec(controller.getLevel(), mount.getBlockPos().getCenter());
            List<List<Double>> angles = VS2CannonTargeting.calculatePitchAndYawVS2(mount, p, sl);
            if (angles == null || angles.isEmpty() || angles.get(0).isEmpty()) {
                return false;
            }
        } else {
            Vec3 origin = controller.getRayStart();
            List<Double> pitches = CannonTargeting.calculatePitch(mount, origin, p, sl);
            if (pitches == null || pitches.isEmpty()) {
                return false;
            }
        }

        return controller.firingControl.hasLineOfSightTo(track, requireLos);
    }

    private void rotateCBC(CannonMountBlockEntity mount) {
        if (!controller.isRunningController()) {
            LOGGER.debug("PITCH.rotateCBC aborted: isRunning=false");
            return;
        }

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null) {
            return;
        }

        if (!(contraption.getContraption() instanceof AbstractMountedCannonContraption cannonContraption)) {
            return;
        }

        double currentPitch = contraption.pitch;
        int invert = -cannonContraption.initialOrientation().getStepX() + cannonContraption.initialOrientation().getStepZ();
        currentPitch = currentPitch * -invert;

        double diff = controller.getTargetAngle() - currentPitch;

        double nearDeadbandDeg = AutoPitchControllerBlockEntity.getCbcTolerance();
        if (controller.firingControl != null) {
            Vec3 muzzle = controller.firingControl.getCannonRayStart();
            Vec3 target = controller.getLastTargetPos();
            if (target != null) {
                double dist = muzzle.distanceTo(target);
                if (dist <= 10.0) {
                    nearDeadbandDeg = 6.0;
                }
            }
        }

        LOGGER.debug(
                "PITCH.rotateCBC current={} target={} diff={} speed={} deadband={}",
                currentPitch,
                controller.getTargetAngle(),
                diff,
                controller.getSpeed(),
                nearDeadbandDeg
        );

        if (Math.abs(diff) <= nearDeadbandDeg) {
            mount.setPitch((float) controller.getTargetAngle());
            mount.notifyUpdate();
            return;
        }

        double rpm = Math.abs(controller.getSpeed());
        if (rpm <= 0.0) {
            return;
        }



        double stepDeg = rpm / 24.0;
        double move = Math.signum(diff) * Math.min(Math.abs(diff), stepDeg);
        double nextCtl = currentPitch + move;

        mount.setPitch((float) nextCtl);
        mount.notifyUpdate();
    }

    private void setTargetCBC(CannonMountBlockEntity mount, Vec3 targetPos) {
        if (!(controller.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (PhysicsHandler.isBlockInPlotyard(controller.getLevel(), controller.getBlockPos())) {
            List<List<Double>> angles = VS2CannonTargeting.calculatePitchAndYawVS2(mount, targetPos, serverLevel);
            if (angles == null || angles.isEmpty() || angles.get(0).isEmpty()) {
                LOGGER.warn("ping-3{}", angles);
                return;
            }

            controller.setInternalTargetAngle(angles.get(0).get(0));
            controller.setRunning(true);
            controller.notifyUpdate();
            controller.setChanged();
            return;
        }

        Vec3 origin = controller.getRayStart();
        List<Double> angles = CannonTargeting.calculatePitch(mount, origin, targetPos, serverLevel);

        LOGGER.debug("PITCH.solve origin={} target={} mountPos={}", origin, targetPos, mount.getBlockPos());
        controller.setLastTargetPos(targetPos);

        if (angles == null || angles.isEmpty()) {
            LOGGER.debug("PITCH.solve FAILED: no pitch roots");
            controller.setRunning(false);
            return;
        }

        if (controller.isArtillery() && angles.size() == 2) {
            controller.setInternalTargetAngle(angles.get(1));
        } else if (!angles.isEmpty()) {
            controller.setInternalTargetAngle(angles.get(0));
        }

        LOGGER.debug("PITCH.solve targetAngle={}", controller.getTargetAngle());

        controller.setRunning(true);
        controller.notifyUpdate();
        controller.setChanged();
    }
}
