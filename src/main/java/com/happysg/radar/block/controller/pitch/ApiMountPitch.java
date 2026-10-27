package com.happysg.radar.block.controller.pitch;

import com.happysg.radar.api.mount.RadarMountAdapter;
import com.happysg.radar.compat.cbc.DirectCbcMountMotion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

public class ApiMountPitch {

    private final AutoPitchControllerBlockEntity controller;

    private static final class MotionSession {
        final DirectCbcMountMotion.State motion = new DirectCbcMountMotion.State();
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
            return;
        }

        BlockPos mountPos = mount.getMountPos();
        MotionSession session = motionSessions.computeIfAbsent(mountPos.immutable(), ignored -> new MotionSession());

        long tick = controller.getLevel().getGameTime();

        if (session.lastTick != tick - 1) {
            session.motion.reset();
        }

        session.lastTick = tick;

        double observedPitch = mount.getPitch();
        double requestedPitch = controller.getTargetAngle();

        if (!Double.isFinite(observedPitch) || !Double.isFinite(requestedPitch)) {
            session.motion.reset();
            return;
        }

        double current = clampPitch(observedPitch);
        double target = clampPitch(requestedPitch);
        double diff = target - current;

        double rpm = Math.abs(controller.getAvailableInputSpeed());

        if (!Double.isFinite(rpm) || rpm <= 0.0) {
            session.motion.reset();
            return;
        }

        double move = session.motion.nextTrackingStep(diff, rpm, target, tick, false);

        if (Math.abs(move) <= 1.0E-9) {
            return;
        }

        mount.setPitch(clampPitch(current + move));
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

        return Math.abs(target - current) < tolerance;
    }

    private static double clampPitch(double pitch) {
        return Math.max(-90.0, Math.min(90.0, pitch));
    }
}