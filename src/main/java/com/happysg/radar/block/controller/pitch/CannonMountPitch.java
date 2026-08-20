package com.happysg.radar.block.controller.pitch;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.CannonMountContext;
import com.happysg.radar.compat.cbc.CannonTargeting;
import com.happysg.radar.compat.cbc.CannonUtil;
import com.happysg.radar.compat.cbc.DirectCbcMountMotion;
import com.happysg.radar.compat.cbc.VS2CannonTargeting;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.config.server.RadarServerConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import javax.annotation.Nullable;
import java.util.List;

public class CannonMountPitch {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final AutoPitchControllerBlockEntity controller;
    private final DirectCbcMountMotion.State motionState =
            new DirectCbcMountMotion.State();

    public CannonMountPitch(AutoPitchControllerBlockEntity controller) {
        this.controller = controller;
    }

    public void tick(CannonMountContext mount) {
        rotateCBC(mount);
    }

    public void setTarget(CannonMountContext mount, Vec3 targetPos) {
        setTargetCBC(mount, targetPos);
    }

    public boolean atTargetPitch(CannonMountContext mount, boolean lag) {
        return atTargetPitch(mount, lag, 0.0);
    }

    public boolean atTargetPitch(CannonMountContext mount, boolean lag,
                                 double minimumToleranceDegrees) {
        return atTargetPitch(mount, lag, minimumToleranceDegrees,
                Double.POSITIVE_INFINITY);
    }

    public boolean atTargetPitch(CannonMountContext mount, boolean lag,
                                 double minimumToleranceDegrees,
                                 double maximumToleranceDegrees) {
        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null) {
            return false;
        }

        if (!(contraption.getContraption() instanceof AbstractMountedCannonContraption cannonContraption)) {
            return false;
        }

        double tol = AutoPitchControllerBlockEntity.getCbcTolerance();
        if (!lag) {
            tol += RadarConfig.server().targetLoosenAmount.get();
        }
        tol = Math.max(tol, sanitizeTolerance(minimumToleranceDegrees));
        tol = Math.min(tol, sanitizeMaximumTolerance(maximumToleranceDegrees));

        double currentPitch = contraption.pitch;
        int invert = -cannonContraption.initialOrientation().getStepX() + cannonContraption.initialOrientation().getStepZ();
        currentPitch = currentPitch * -invert;

        return Math.abs(currentPitch - controller.getTargetAngle()) < tol;
    }

    private static double sanitizeTolerance(double tolerance) {
        return Double.isFinite(tolerance) ? Math.max(0.0, tolerance) : 0.0;
    }

    private static double sanitizeMaximumTolerance(double tolerance) {
        return Double.isFinite(tolerance)
                ? Math.max(0.0, tolerance) : Double.POSITIVE_INFINITY;
    }

    public double getMaxEngagementRangeBlocks(CannonMountContext mount, ServerLevel sl) {
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

    public boolean canEngageTrack(CannonMountContext mount, @Nullable RadarTrack track, boolean requireLos, ServerLevel sl) {
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

        Vec3 p = controller.firingControl.resolveEngagementAimPoint(
                sl, track, requireLos);
        if (p == null) {
            return false;
        }
        if (!controller.firingControl.canAimAtFixedYaw(p)) {
            return false;
        }
        if (!controller.firingControl.canYawAimAtTarget(p)) {
            return false;
        }

        double max = controller.getMaxEngagementRangeBlocks();
        if (max > 0.0) {
            Vec3 start = controller.firingControl.getCannonRayStart();
            if (start.distanceToSqr(p) > (max * max)) {
                return false;
            }
        }

        if (Mods.SABLE.isLoaded()
                && controller.firingControl.usesSublevelAimFrame()) {
            List<List<Double>> angles = VS2CannonTargeting.calculatePitchAndYawVS2(mount, p, sl);
            boolean hasLegalAngles = angles != null && angles.stream()
                    .anyMatch(candidate -> candidate != null
                            && candidate.size() >= 2
                            && controller.firingControl.canApplyMountCommand(
                            candidate.get(0), candidate.get(1)));
            if (!hasLegalAngles) {
                return false;
            }
        } else {
            Vec3 origin = controller.getRayStart();
            List<Double> pitches = CannonTargeting.calculatePitch(mount, origin, p, sl);
            var effectivePitch = controller.getMovementLimits()
                    .intersection(controller.getSupportedMovementLimits())
                    .orElse(null);
            if (effectivePitch == null || pitches == null
                    || pitches.stream().noneMatch(pitch -> pitch != null
                    && effectivePitch.allowsControllerTarget(pitch, 0.0))) {
                return false;
            }
        }

        return !requireLos || "Sable:ship".equals(track.entityType())
                || controller.firingControl.hasLineOfSightTo(track, true);
    }

    private void rotateCBC(CannonMountContext mount) {
        if (!controller.isRunningController()) {
            motionState.reset();
            LOGGER.debug("PITCH.rotateCBC aborted: isRunning=false");
            return;
        }

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null) {
            motionState.reset();
            return;
        }

        if (!(contraption.getContraption() instanceof AbstractMountedCannonContraption cannonContraption)) {
            motionState.reset();
            return;
        }

        double currentPitch = contraption.pitch;
        int invert = -cannonContraption.initialOrientation().getStepX() + cannonContraption.initialOrientation().getStepZ();
        currentPitch = currentPitch * -invert;

        double diff = controller.getTargetAngle() - currentPitch;

        LOGGER.debug(
                "PITCH.rotateCBC current={} target={} diff={} speed={}",
                currentPitch,
                controller.getTargetAngle(),
                diff,
                controller.getAvailableInputSpeed()
        );

        double rpm = Math.abs(controller.getAvailableInputSpeed());
        if (rpm <= 0.0) {
            motionState.reset();
            return;
        }

        double move = motionState.nextStep(diff, rpm);
        if (Math.abs(move) <= 1.0E-9) {
            return;
        }
        double nextCtl = currentPitch + move;

        mount.setPitch((float) nextCtl);
        mount.notifyUpdate();
    }

    private void setTargetCBC(CannonMountContext mount, Vec3 targetPos) {
        if (!(controller.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (PhysicsHandler.isBlockInPlotyard(controller.getLevel(), controller.getBlockPos())) {
            List<List<Double>> angles = VS2CannonTargeting.calculatePitchAndYawVS2(mount, targetPos, serverLevel);
            if (angles == null || angles.isEmpty() || angles.get(0).isEmpty()) {
                LOGGER.debug("ping-3{}", angles);
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
