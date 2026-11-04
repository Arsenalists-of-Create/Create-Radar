package com.happysg.radar.block.controller.pitch;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.CannonMountContext;
import com.happysg.radar.compat.cbc.CannonTargeting;
import com.happysg.radar.compat.cbc.CannonUtil;
import com.happysg.radar.block.controller.kinetic.ControllerMotionProfile;
import com.happysg.radar.compat.cbc.VS2CannonTargeting;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.config.server.RadarServerConfig;
import com.happysg.radar.debug.MotionTraceRecorder;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class CannonMountPitch {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final AutoPitchControllerBlockEntity controller;
    private final Map<BlockEntity, ControllerMotionProfile.State> motionStates = new WeakHashMap<>();

    private final Map<AbstractMountedCannonContraption, Double> selectionRangeCache = new IdentityHashMap<>();
    private int selectionRangeDepth = 0;

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

    public boolean atTargetPitch(CannonMountContext mount, boolean lag, double minimumToleranceDegrees) {
        return atTargetPitch(mount, lag, minimumToleranceDegrees, Double.POSITIVE_INFINITY);
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

        double desiredPitch = controller.getTargetAngle();
        double pitchError = desiredPitch - currentPitch;
        boolean aligned = Math.abs(pitchError) < tol;

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "cbc_direct.pitch_alignment",
                    controller.getLevel(),
                    controller.getBlockPos(),
                    "mount", mount.getBlockPos(),
                    "rawContraptionPitch", contraption.pitch,
                    "currentPitch", currentPitch,
                    "desiredPitch", desiredPitch,
                    "pitchError", pitchError,
                    "effectiveTolerance", tol,
                    "minimumTolerance", minimumToleranceDegrees,
                    "maximumTolerance", maximumToleranceDegrees,
                    "orientationInversion", invert,
                    "lag", lag,
                    "alignedForFiring", aligned
            );
        }

        return aligned;
    }

    private static double sanitizeTolerance(double tolerance) {
        return Double.isFinite(tolerance) ? Math.max(0.0, tolerance) : 0.0;
    }

    private static double sanitizeMaximumTolerance(double tolerance) {
        return Double.isFinite(tolerance)
                ? Math.max(0.0, tolerance) : Double.POSITIVE_INFINITY;
    }

    public void beginRangeSelection() {
        if (selectionRangeDepth++ == 0) {
            selectionRangeCache.clear();
        }
    }

    public void endRangeSelection() {
        if (selectionRangeDepth > 0 && --selectionRangeDepth == 0) {
            selectionRangeCache.clear();
        }
    }

    public double getMaxEngagementRangeBlocks(CannonMountContext mount, ServerLevel sl) {
        PitchOrientedContraptionEntity ce = mount.getContraption();
        if (ce == null) {
            return 0;
        }
        if (!(ce.getContraption() instanceof AbstractMountedCannonContraption cannon)) {
            return 0;
        }

        double r;

        if (selectionRangeDepth > 0) {
            r = selectionRangeCache.computeIfAbsent(cannon, c -> CannonUtil.getMaxProjectileRangeBlocks(c, sl));
        } else {
            r = CannonUtil.getMaxProjectileRangeBlocks(cannon, sl);
        }

        // LOGGER.debug("RANGE DBG endpoint={} cannon={} range={} blocks", controller.getBlockPos(), cannon.getClass().getSimpleName(), r);
        return r;
    }

    public boolean canEngageTrack(CannonMountContext mount, @Nullable RadarTrack track, boolean requireLos, ServerLevel sl) {
        if (track == null) {
            return false;
        }
        if (controller.firingControl == null) {
            return false;
        }

        PitchOrientedContraptionEntity contraption = mount.getContraption();

        if (contraption == null || !(contraption.getContraption() instanceof AbstractMountedCannonContraption)) {
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

        if (requireLos && !"Sable:ship".equals(track.entityType()) && !controller.firingControl.hasLineOfSightTo(track, true)) {
            return false;
        }

        if (controller.firingControl.usesSublevelAimFrame()) {
            return true;
        }

        return controller.getMovementLimits().intersection(controller.getSupportedMovementLimits()).isPresent();
    }

    private void rotateCBC(CannonMountContext mount) {
        if (mount.isFixed()) {
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "cbc_direct.pitch_inactive",
                        controller.getLevel(),
                        controller.getBlockPos(),
                        "mount", mount.getBlockPos(),
                        "reason", "FIXED_MOUNT"
                );
            }
            return;
        }

        ControllerMotionProfile.State motionState = motionStates.computeIfAbsent(mount.blockEntity(), ignored -> new ControllerMotionProfile.State());

        if (!controller.isRunningController()) {
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "cbc_direct.pitch_inactive",
                        controller.getLevel(),
                        controller.getBlockPos(),
                        "mount", mount.getBlockPos(),
                        "reason", "CONTROLLER_NOT_RUNNING",
                        "targetPitch", controller.getTargetAngle(),
                        "availableRpm", controller.getAvailableInputSpeed()
                );
            }

            motionState.reset();
            return;
        }

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null) {
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "cbc_direct.pitch_inactive",
                        controller.getLevel(),
                        controller.getBlockPos(),
                        "mount", mount.getBlockPos(),
                        "reason", "CONTRAPTION_MISSING"
                );
            }

            motionState.reset();
            return;
        }

        if (!(contraption.getContraption() instanceof AbstractMountedCannonContraption cannonContraption)) {
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "cbc_direct.pitch_inactive",
                        controller.getLevel(),
                        controller.getBlockPos(),
                        "mount", mount.getBlockPos(),
                        "reason", "INVALID_CANNON_CONTRAPTION"
                );
            }

            motionState.reset();
            return;
        }

        double currentPitch = contraption.pitch;
        int invert = -cannonContraption.initialOrientation().getStepX() + cannonContraption.initialOrientation().getStepZ();
        currentPitch = currentPitch * -invert;

        double diff = controller.getTargetAngle() - currentPitch;

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "cbc_direct.pitch_feedback",
                    controller.getLevel(),
                    controller.getBlockPos(),
                    "mount", mount.getBlockPos(),
                    "rawContraptionPitch", contraption.pitch,
                    "controllerSpacePitch", currentPitch,
                    "desiredPitch", controller.getTargetAngle(),
                    "pitchError", diff,
                    "initialOrientation", cannonContraption.initialOrientation(),
                    "orientationInversion", invert,
                    "inputRpm", controller.getAvailableInputSpeed(),
                    "running", controller.isRunningController()
            );
        }

        //LOGGER.debug("PITCH.rotateCBC current={} target={} diff={} speed={}", currentPitch, controller.getTargetAngle(), diff, controller.getAvailableInputSpeed());

        double rpm = Math.abs(controller.getAvailableInputSpeed());

        if (rpm <= 0.0) {
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "cbc_direct.pitch_inactive",
                        controller.getLevel(),
                        controller.getBlockPos(),
                        "mount", mount.getBlockPos(),
                        "reason", "NO_INPUT_RPM",
                        "currentPitch", currentPitch,
                        "desiredPitch", controller.getTargetAngle(),
                        "pitchError", diff,
                        "inputRpm", rpm
                );
            }

            motionState.reset();
            return;
        }

        double move = controller.getLevel() != null ? motionState.nextTrackingStep(diff, rpm, controller.getTargetAngle(), controller.getLevel().getGameTime(), false) : motionState.nextStep(diff, rpm);
        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "cbc_direct.pitch_smoothing",
                    controller.getLevel(),
                    controller.getBlockPos(),
                    "mount", mount.getBlockPos(),
                    "currentPitch", currentPitch,
                    "desiredPitch", controller.getTargetAngle(),
                    "pitchError", diff,
                    "inputRpm", rpm,
                    "plannedMove", move,
                    "nextPitch", currentPitch + move,
                    "orientationInversion", invert,
                    "profileVelocity", motionState.traceVelocityDegPerTick(),
                    "profileTargetRate", motionState.traceTargetRateDegPerTick(),
                    "profileTargetTick", motionState.traceTargetTick()
            );
        }

        //LOGGER.debug("RADAR_PITCH_MOVE diff={} rpm={} move={}", diff, rpm, move);

        if (Math.abs(move) <= 1.0E-9) {
            return;
        }

        double nextCtl = currentPitch + move;
        double rawPitchBeforeWrite = contraption.pitch;
        mount.setPitch((float) nextCtl);

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "cbc_direct.pitch_write",
                    controller.getLevel(),
                    controller.getBlockPos(),
                    "mount", mount.getBlockPos(),
                    "previousControllerPitch", currentPitch,
                    "requestedControllerPitch", nextCtl,
                    "plannedMove", move,
                    "rawPitchBeforeWrite", rawPitchBeforeWrite,
                    "rawPitchAfterWrite", contraption.pitch,
                    "orientationInversion", invert,
                    "inputRpm", rpm
            );
        }

        mount.notifyUpdate();
    }

    private void setTargetCBC(CannonMountContext mount, Vec3 targetPos) {
        if (!(controller.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (PhysicsHandler.isBlockInPlotyard(controller.getLevel(), controller.getBlockPos())) {
            List<List<Double>> angles = VS2CannonTargeting.calculatePitchAndYawVS2(mount, targetPos, serverLevel);
            if (angles == null || angles.isEmpty() || angles.get(0).isEmpty()) {
                if (MotionTraceRecorder.isEnabled()) {
                    MotionTraceRecorder.record(
                            "cbc_direct.pitch_solver_failed",
                            serverLevel,
                            controller.getBlockPos(),
                            "mount", mount.getBlockPos(),
                            "source", "SABLE",
                            "targetPosition", targetPos,
                            "reason", "NO_VALID_SABLE_PITCH_SOLUTION"
                    );
                }
                return;
            }

            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "cbc_direct.pitch_target",
                        serverLevel,
                        controller.getBlockPos(),
                        "mount", mount.getBlockPos(),
                        "source", "SABLE",
                        "targetPosition", targetPos,
                        "calculatedPitch", angles.get(0).get(0),
                        "previousPitch", controller.getTargetAngle(),
                        "solutionCount", angles.size()
                );
            }

            controller.setInternalTargetAngle(angles.get(0).get(0));
            controller.setRunning(true);
            controller.notifyUpdate();
            controller.setChanged();
            return;
        }

        Vec3 origin = controller.getRayStart();
        List<Double> angles = CannonTargeting.calculatePitch(mount, origin, targetPos, serverLevel);

        //LOGGER.debug("PITCH.solve origin={} target={} mountPos={}", origin, targetPos, mount.getBlockPos());
        controller.setLastTargetPos(targetPos);

        if (angles == null || angles.isEmpty()) {
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "cbc_direct.pitch_solver_failed",
                        serverLevel,
                        controller.getBlockPos(),
                        "mount", mount.getBlockPos(),
                        "source", "WORLD",
                        "origin", origin,
                        "targetPosition", targetPos,
                        "reason", "NO_VALID_PITCH_ROOTS",
                        "artilleryMode", controller.isArtillery()
                );
            }

            controller.setRunning(false);
            return;
        }

        if (controller.isArtillery() && angles.size() == 2) {
            controller.setInternalTargetAngle(angles.get(1));
        } else if (!angles.isEmpty()) {
            controller.setInternalTargetAngle(angles.get(0));
        }

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "cbc_direct.pitch_target",
                    serverLevel,
                    controller.getBlockPos(),
                    "mount", mount.getBlockPos(),
                    "source", "WORLD",
                    "origin", origin,
                    "targetPosition", targetPos,
                    "pitchRoots", angles,
                    "solutionCount", angles.size(),
                    "artilleryMode", controller.isArtillery(),
                    "selectedRootIndex",
                    controller.isArtillery() && angles.size() == 2 ? 1 : 0,
                    "selectedPitch", controller.getTargetAngle()
            );
        }

        //LOGGER.debug("PITCH.solve targetAngle={}", controller.getTargetAngle());

        controller.setRunning(true);
        controller.notifyUpdate();
        controller.setChanged();
    }
}
