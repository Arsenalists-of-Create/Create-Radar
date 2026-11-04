package com.happysg.radar.block.controller.pitch;

import com.happysg.radar.api.mount.RadarMountAdapter;
import com.happysg.radar.block.controller.kinetic.ControllerMotionProfile;
import com.happysg.radar.debug.MotionTraceRecorder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

public class ApiMountPitch {
    private final AutoPitchControllerBlockEntity controller;

    private static final class MotionSession {
        final ControllerMotionProfile.State motion = new ControllerMotionProfile.State();
        long lastTick = Long.MIN_VALUE;
    }

    private final Map<BlockPos, MotionSession> motionSessions = new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockPos, MotionSession> eldest) {
            return size() > 8;
        }
    };

    public ApiMountPitch(AutoPitchControllerBlockEntity controller) {
        this.controller = controller;
    }

    public void tick(RadarMountAdapter mount) {
        if (controller.getLevel() == null || !controller.isRunningController() || !mount.isValid() || !mount.isAssembled() || !mount.supportsPitch()) {
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "api_mount.pitch_inactive",
                        controller.getLevel(),
                        controller.getBlockPos(),
                        "mount", mount.getMountPos(),
                        "levelPresent", controller.getLevel() != null,
                        "running", controller.isRunningController(),
                        "valid", mount.isValid(),
                        "assembled", mount.isAssembled(),
                        "supportsPitch", mount.supportsPitch()
                );
            }

            return;
        }

        BlockPos mountPos = mount.getMountPos();
        MotionSession session = motionSessions.computeIfAbsent(mountPos.immutable(), ignored -> new MotionSession());

        long tick = controller.getLevel().getGameTime();

        if (session.lastTick != tick - 1) {
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "api_mount.pitch_session_reset",
                        controller.getLevel(),
                        controller.getBlockPos(),
                        "mount", mountPos,
                        "currentTick", tick,
                        "previousTick", session.lastTick,
                        "previousVelocity",
                        session.motion.traceVelocityDegPerTick(),
                        "previousTargetRate",
                        session.motion.traceTargetRateDegPerTick()
                );
            }

            session.motion.reset();
        }

        session.lastTick = tick;

        double observedPitch = mount.getPitch();
        double requestedPitch = controller.getTargetAngle();

        if (!Double.isFinite(observedPitch) || !Double.isFinite(requestedPitch)) {
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "api_mount.pitch_invalid_feedback",
                        controller.getLevel(),
                        controller.getBlockPos(),
                        "mount", mountPos,
                        "observedPitch", observedPitch,
                        "requestedPitch", requestedPitch
                );
            }

            session.motion.reset();
            return;
        }

        double current = clampPitch(observedPitch);
        double target = clampPitch(requestedPitch);
        double diff = target - current;

        double rpm = Math.abs(controller.getAvailableInputSpeed());

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "api_mount.pitch_feedback",
                    controller.getLevel(),
                    controller.getBlockPos(),
                    "mount", mountPos,
                    "rawPitch", observedPitch,
                    "currentPitch", current,
                    "requestedPitch", requestedPitch,
                    "desiredPitch", target,
                    "pitchError", diff,
                    "inputRpm", rpm,
                    "profileVelocity",
                    session.motion.traceVelocityDegPerTick(),
                    "profileTargetRate",
                    session.motion.traceTargetRateDegPerTick()
            );
        }

        if (!Double.isFinite(rpm) || rpm <= 0.0) {
            if (MotionTraceRecorder.isEnabled()) {
                MotionTraceRecorder.record(
                        "api_mount.pitch_inactive",
                        controller.getLevel(),
                        controller.getBlockPos(),
                        "mount", mountPos,
                        "reason", "NO_VALID_INPUT_RPM",
                        "rpm", rpm,
                        "currentPitch", current,
                        "desiredPitch", target,
                        "pitchError", diff
                );
            }

            session.motion.reset();
            return;
        }

        double move = session.motion.nextTrackingStep(diff, rpm, target, tick, false);

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "api_mount.pitch_smoothing",
                    controller.getLevel(),
                    controller.getBlockPos(),
                    "mount", mountPos,
                    "currentPitch", current,
                    "desiredPitch", target,
                    "pitchError", diff,
                    "inputRpm", rpm,
                    "plannedMove", move,
                    "nextPitch", clampPitch(current + move),
                    "profileVelocity",
                    session.motion.traceVelocityDegPerTick(),
                    "profileTargetRate",
                    session.motion.traceTargetRateDegPerTick(),
                    "profileTargetTick",
                    session.motion.traceTargetTick()
            );
        }

        if (Math.abs(move) <= 1.0E-9) {
            return;
        }

        double commandedPitch = clampPitch(current + move);
        mount.setPitch(commandedPitch);

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "api_mount.pitch_write",
                    controller.getLevel(),
                    controller.getBlockPos(),
                    "mount", mountPos,
                    "previousPitch", current,
                    "commandedPitch", commandedPitch,
                    "observedPitchAfterWrite", mount.getPitch(),
                    "plannedMove", move,
                    "inputRpm", rpm
            );
        }
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
        double pitch = Math.toDegrees(Math.atan2(dy, horizontal));

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "api_mount.pitch_target",
                    controller.getLevel(),
                    controller.getBlockPos(),
                    "mount", mount.getMountPos(),
                    "aimOrigin", origin,
                    "targetPosition", targetPos,
                    "calculatedPitch", pitch,
                    "clampedPitch", clampPitch(pitch),
                    "previousTargetPitch", controller.getTargetAngle()
            );
        }

        controller.setInternalTargetAngle(clampPitch(pitch));
        controller.setLastTargetPos(targetPos);
        controller.setRunning(true);

        controller.notifyUpdate();
        controller.setChanged();
    }

    public boolean atTargetPitch(RadarMountAdapter mount, boolean lag, double minimumToleranceDegrees, double maximumToleranceDegrees) {
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

        if (Double.isFinite(maximumToleranceDegrees)) {
            tolerance = Math.min(tolerance, Math.max(0.0, maximumToleranceDegrees));
        }

        double pitchError = target - current;
        boolean aligned = Math.abs(pitchError) < tolerance;

        if (MotionTraceRecorder.isEnabled()) {
            MotionTraceRecorder.record(
                    "api_mount.pitch_alignment",
                    controller.getLevel(),
                    controller.getBlockPos(),
                    "mount", mount.getMountPos(),
                    "currentPitch", current,
                    "desiredPitch", target,
                    "pitchError", pitchError,
                    "effectiveTolerance", tolerance,
                    "minimumTolerance", minimumToleranceDegrees,
                    "maximumTolerance", maximumToleranceDegrees,
                    "lag", lag,
                    "alignedForFiring", aligned
            );
        }

        return aligned;
    }

    private static double clampPitch(double pitch) {
        return Math.max(-90.0, Math.min(90.0, pitch));
    }
}