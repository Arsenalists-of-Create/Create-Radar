package com.happysg.radar.compat.cbc;

import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;

import java.util.List;

public class CannonLead {
    private static final double VEL_EPS = 0.01;
    private static final double VEL_EPS_SQR = VEL_EPS * VEL_EPS;
    private static final Logger LOGGER = LogUtils.getLogger();

    public static class LeadSolution {
        public final Vec3 aimPoint;
        public final double pitchDeg;    // pitch solution for the aimPoint
        public final double yawRad;      // yaw solution for the aimPoint
        public final int flightTicks;    // predicted time-of-flight in ticks

        public LeadSolution(Vec3 aimPoint, double pitchDeg, double yawRad, int flightTicks) {
            this.aimPoint = aimPoint;
            this.pitchDeg = pitchDeg;
            this.yawRad = yawRad;
            this.flightTicks = flightTicks;
        }
    }

    public static class SimResult {
        public final int ticks;
        public final Vec3 pos;
        public final Vec3 vel;

        public SimResult(int ticks, Vec3 pos, Vec3 vel) {
            this.ticks = ticks;
            this.pos = pos;
            this.vel = vel;
        }
    }

    // -------------------------
    // tick-based kinematics
    // -------------------------

    public static Vec3 predictPositionTicks(Vec3 pos0, Vec3 velPerTick, Vec3 accelPerTick2, double tTicks) {
        return pos0
                .add(velPerTick.scale(tTicks))
                .add(accelPerTick2.scale(0.5 * tTicks * tTicks));
    }

    public static Vec3 predictVelocityTicks(Vec3 vel0PerTick, Vec3 accelPerTick2, double tTicks) {
        return vel0PerTick.add(accelPerTick2.scale(tTicks));
    }

    // -------------------------
    // aiming helpers
    // -------------------------

    public static Vec3 directionFromYawPitch(double yawRad, double pitchRad) {
        return new Vec3(
                Math.cos(pitchRad) * Math.cos(yawRad),
                Math.sin(pitchRad),
                Math.cos(pitchRad) * Math.sin(yawRad)
        ).normalize();
    }

    // -------------------------
    // projectile sim (tick units)
    // -------------------------

    /**
     * Simple tick integrator using per-tick damping style drag.
     * NOTE: CBC's ballistic "drag" is not necessarily a damping coefficient; if you're matching CBC,
     * prefer a CBC-equivalent sim (see your simulateFlightTicksCBC).
     */
    public static SimResult simulateFlightTicks(
            Vec3 muzzlePos,
            Vec3 shooterVelPerTickAtFire,
            Vec3 dirUnit,
            double muzzleSpeedPerTick,
            double gravityPerTick,
            double drag,
            Vec3 targetPoint,
            double targetHorizontalDist,
            int maxTicks,
            boolean applyDrag
    ) {
        Vec3 pos = muzzlePos;
        Vec3 vel = shooterVelPerTickAtFire.add(dirUnit.scale(muzzleSpeedPerTick));

        double targetDistSqr = targetHorizontalDist * targetHorizontalDist;

        for (int tick = 0; tick <= maxTicks; tick++) {
            double dx = pos.x - muzzlePos.x;
            double dz = pos.z - muzzlePos.z;
            if (dx * dx + dz * dz >= targetDistSqr) {
                return new SimResult(tick, pos, vel);
            }

            if (vel.lengthSqr() <= 1.0e-4) {
                return new SimResult(tick, pos, vel);
            }

            // gravity per tick^2
            vel = vel.add(0.0, gravityPerTick, 0.0);

            // per-tick damping style drag (only valid if drag is small, like 0..1)
            if (applyDrag && drag != 0.0) {
                vel = vel.scale(1.0 - drag);
            }

            pos = pos.add(vel);
        }

        return new SimResult(maxTicks, pos, vel);
    }

    // -------------------------
    // main solver
    // -------------------------

    /**
     * Shooter & target vectors are expected in WORLD SPACE and in TICK UNITS:
     * - velocity: blocks/tick
     * - acceleration: blocks/tick^2
     */
    public static LeadSolution solveLeadPerTickWithAcceleration(
            CannonMountBlockEntity mount,
            AbstractMountedCannonContraption cannon,
            ServerLevel level,

            Vec3 shooterVelPerTick,
            Vec3 shooterAccelPerTick2,

            Vec3 targetPosNow,
            Vec3 targetVelPerTick,
            Vec3 targetAccelPerTick2,

            int fireDelayTicks,
            double maxSimDistanceBlocks
    ) {
        if (mount == null || cannon == null || level == null) return null;
        if (targetPosNow == null || targetVelPerTick == null || targetAccelPerTick2 == null) return null;
        if (shooterVelPerTick == null || shooterAccelPerTick2 == null) return null;

        boolean targetMoving = targetVelPerTick.lengthSqr() >= VEL_EPS_SQR;
        boolean shooterMoving = shooterVelPerTick.lengthSqr() >= VEL_EPS_SQR;

        // If shooter isn't moving, zero its motion to avoid tiny noise causing weird relative motion.
        if (!shooterMoving) {
            shooterVelPerTick = Vec3.ZERO;
            shooterAccelPerTick2 = Vec3.ZERO;
        }

        double muzzleSpeedPerTick = CannonUtil.getInitialVelocity(cannon, level);
        if (muzzleSpeedPerTick <= 0.0) return null;

        // "Origin" in world space (VS2-aware)
        Vec3 originNow = PhysicsHandler.getWorldVec(level, mount.getControllerBlockPos().above(2).getCenter());
        final double latencyTicks = 2.0; // tune 1..3
        int barrelLength = CannonUtil.getBarrelLength(cannon);

        BallisticPropertiesComponent bp = CannonUtil.getBallistics(cannon, level);
        double gravityPerTick = bp.gravity();
        double formDrag = bp.drag();

        // Predict shooter state at fire time (projectile spawn moment)
        Vec3 shooterPosAtFire = predictPositionTicks(originNow, shooterVelPerTick, shooterAccelPerTick2, fireDelayTicks);
        Vec3 shooterVelAtFire = predictVelocityTicks(shooterVelPerTick, shooterAccelPerTick2, fireDelayTicks);

        // Build target relative state anchored at FIRE TIME
        // (We keep targetPosNow "now" and subtract shooterPosAtFire, which anchors relative position at fire.)
        Vec3 targetPosRel0 = targetPosNow.subtract(shooterPosAtFire);
        Vec3 targetVelRel = targetVelPerTick.subtract(shooterVelAtFire);
        Vec3 targetAccelRel = targetAccelPerTick2.subtract(shooterAccelPerTick2);

        // If target isn't moving, just aim directly at it (still compute yaw/pitch)
        if (!targetMoving) {
            Vec3 to = targetPosNow.subtract(shooterPosAtFire);
            double yaw = Math.atan2(to.z, to.x);
            double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
            double pitch = Math.atan2(to.y, Math.max(1.0e-6, horiz));
            return new LeadSolution(targetPosNow, Math.toDegrees(pitch), yaw, 0);
        }

        // Initial guess: horizontal distance / muzzle speed
        double dx0 = targetPosNow.x - shooterPosAtFire.x;
        double dz0 = targetPosNow.z - shooterPosAtFire.z;
        double horiz0 = Math.sqrt(dx0 * dx0 + dz0 * dz0);
        double tGuessTicks = horiz0 / Math.max(1.0e-6, muzzleSpeedPerTick);

        Vec3 aimPoint = targetPosNow;
        double chosenPitchDeg = 0.0;
        double chosenYawRad = 0.0;
        int flightTicks = (int) Math.round(tGuessTicks);

        for (int iter = 0; iter < 8; iter++) {
            // IMPORTANT FIX:
            // targetPosRel0/VelRel/AccelRel are anchored at FIRE TIME,
            // so advance ONLY by FLIGHT TIME (NOT fireDelay+flight).
            double tFlightTicks = tGuessTicks;
            double tLeadTicks = tFlightTicks + latencyTicks;
            // Predict target position (relative) at impact time after firing
            Vec3 aimRel = predictPositionTicks(targetPosRel0, targetVelRel, targetAccelRel, tLeadTicks);
            aimPoint = shooterPosAtFire.add(aimRel);

            Vec3 toPred = aimPoint.subtract(shooterPosAtFire);
            chosenYawRad = Math.atan2(toPred.z, toPred.x);

            // Default LOS pitch
            double horizToPred = Math.sqrt(toPred.x * toPred.x + toPred.z * toPred.z);
            double pitchRad = Math.atan2(toPred.y, Math.max(1.0e-6, horizToPred));

            // CBC pitch solution for predicted intercept point
            List<Double> pitchRoots = CannonTargeting.calculatePitch(mount, shooterPosAtFire, aimPoint, level);
            if (pitchRoots != null && !pitchRoots.isEmpty()) {
                pitchRad = Math.toRadians(pitchRoots.get(0));
            }

            Vec3 dir = directionFromYawPitch(chosenYawRad, pitchRad);
            chosenPitchDeg = Math.toDegrees(pitchRad);

            // Muzzle position offset by barrel length
            Vec3 muzzlePosAtFire = shooterPosAtFire.add(dir.scale(barrelLength));

            double dx = aimPoint.x - muzzlePosAtFire.x;
            double dz = aimPoint.z - muzzlePosAtFire.z;
            double horiz = Math.sqrt(dx * dx + dz * dz);

            SimResult sim = simulateFlightTicks(
                    muzzlePosAtFire,
                    shooterVelAtFire,
                    dir,
                    muzzleSpeedPerTick,
                    gravityPerTick,
                    formDrag,
                    aimPoint,
                    horiz,
                    computeMaxSimTicks(horiz, muzzleSpeedPerTick, maxSimDistanceBlocks),
                    true
            );

            int newFlightTicks = sim.ticks;

            if (Math.abs(newFlightTicks - tGuessTicks) < 0.5) {
                flightTicks = newFlightTicks;
                tGuessTicks = newFlightTicks;
                break;
            }

            flightTicks = newFlightTicks;
            tGuessTicks = newFlightTicks;
        }

        return new LeadSolution(aimPoint, chosenPitchDeg, chosenYawRad, flightTicks);
    }

    public static LeadSolution solveLeadPerTickConstantVelocity(
            CannonMountBlockEntity mount,
            AbstractMountedCannonContraption cannon,
            ServerLevel level,

            Vec3 shooterVelPerTick,
            Vec3 targetPosNow,
            Vec3 targetVelPerTick,

            int fireDelayTicks,
            double maxSimDistanceBlocks
    ) {


        // Treat tiny velocities as zero to reduce noise
        if (shooterVelPerTick.lengthSqr() < VEL_EPS_SQR) shooterVelPerTick = Vec3.ZERO;
        boolean targetMoving = targetVelPerTick.lengthSqr() >= VEL_EPS_SQR;

        double muzzleSpeedPerTick = CannonUtil.getInitialVelocity(cannon, level);
        if (muzzleSpeedPerTick <= 0.0) {
            LOGGER.debug("[LEAD] muzzleSpeedPerTick={} (no ammo/invalid state?) cannon={} mountPos={}",
                    muzzleSpeedPerTick, cannon.getClass().getSimpleName(), mount.getBlockPos());
            return null;
        }


        // "Origin" in world space (VS2-aware)
        Vec3 originNow = PhysicsHandler.getWorldVec(level, mount.getControllerBlockPos().above(2).getCenter());
        int barrelLength = CannonUtil.getBarrelLength(cannon);

        BallisticPropertiesComponent bp = CannonUtil.getBallistics(cannon, level);
        double gravityPerTick = bp.gravity();
        double drag = bp.drag(); // NOTE: if this isn't a damping coefficient, consider using your CBC sim instead.

        // Predict shooter and target at FIRE TIME under constant velocity
        Vec3 shooterPosAtFire = originNow.add(shooterVelPerTick.scale(fireDelayTicks));
        Vec3 shooterVelAtFire = shooterVelPerTick;

        Vec3 targetPosAtFire = targetPosNow.add(targetVelPerTick.scale(fireDelayTicks));
        Vec3 targetVelAtFire = targetVelPerTick;

        // Relative state anchored at FIRE TIME
        Vec3 relPos0 = targetPosAtFire.subtract(shooterPosAtFire);
        Vec3 relVel = targetVelAtFire.subtract(shooterVelAtFire);

        // If target isn't moving (or effectively not), just do a direct aim solve
        if (!targetMoving) {
            Vec3 to = targetPosAtFire.subtract(shooterPosAtFire);
            double yaw = Math.atan2(to.z, to.x);
            double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
            double pitch = Math.atan2(to.y, Math.max(1.0e-6, horiz));
            return new LeadSolution(targetPosAtFire, Math.toDegrees(pitch), yaw, 0);
        }

        // Initial time guess from horizontal distance / muzzle speed
        double horiz0 = Math.sqrt(relPos0.x * relPos0.x + relPos0.z * relPos0.z);
        double tGuessTicks = horiz0 / Math.max(1.0e-6, muzzleSpeedPerTick);

        Vec3 aimPoint = targetPosAtFire;
        double chosenPitchDeg = 0.0;
        double chosenYawRad = 0.0;
        int flightTicks = (int) Math.round(tGuessTicks);

        // Iterate to converge flight time against the projectile sim
        for (int iter = 0; iter < 8; iter++) {
            // Predict target at impact (FIRE TIME + flight)
            Vec3 aimRel = relPos0.add(relVel.scale(tGuessTicks));
            aimPoint = shooterPosAtFire.add(aimRel);

            // Yaw in XZ plane from shooter at fire -> predicted point
            Vec3 toPred = aimPoint.subtract(shooterPosAtFire);
            chosenYawRad = Math.atan2(toPred.z, toPred.x);

            // Default LOS pitch (fallback)
            double horizToPred = Math.sqrt(toPred.x * toPred.x + toPred.z * toPred.z);
            double pitchRad = Math.atan2(toPred.y, Math.max(1.0e-6, horizToPred));

            // CBC ballistic pitch solve for the predicted intercept point
            List<Double> pitchRoots = CannonTargeting.calculatePitch(mount, shooterPosAtFire, aimPoint, level);
            if (pitchRoots != null && !pitchRoots.isEmpty()) {
                pitchRad = Math.toRadians(pitchRoots.get(0));
            }

            Vec3 dir = directionFromYawPitch(chosenYawRad, pitchRad);
            chosenPitchDeg = Math.toDegrees(pitchRad);

            // Offset muzzle forward along barrel direction
            Vec3 muzzlePosAtFire = shooterPosAtFire.add(dir.scale(barrelLength));

            // Horizontal distance from muzzle to predicted point (stop condition for sim)
            double dx = aimPoint.x - muzzlePosAtFire.x;
            double dz = aimPoint.z - muzzlePosAtFire.z;
            double horiz = Math.sqrt(dx * dx + dz * dz);

            SimResult sim = simulateFlightTicks(
                    muzzlePosAtFire,
                    shooterVelAtFire,
                    dir,
                    muzzleSpeedPerTick,
                    gravityPerTick,
                    drag,
                    aimPoint,
                    horiz,
                    computeMaxSimTicks(horiz, muzzleSpeedPerTick, maxSimDistanceBlocks),
                    true
            );

            int newFlightTicks = sim.ticks;

            // Converged?
            if (Math.abs(newFlightTicks - tGuessTicks) < 0.5) {
                flightTicks = newFlightTicks;
                tGuessTicks = newFlightTicks;
                break;
            }

            flightTicks = newFlightTicks;
            tGuessTicks = newFlightTicks;
        }

        return new LeadSolution(aimPoint, chosenPitchDeg, chosenYawRad, flightTicks);
    }

    private static int computeMaxSimTicks(double targetHorizontalDist, double muzzleSpeedPerTick, double maxSimDistanceBlocks) {
        final int HARD_MAX_TICKS = 8000;

        double speed = Math.max(1.0e-6, muzzleSpeedPerTick);
        double cappedDist = Math.min(targetHorizontalDist, Math.max(0.0, maxSimDistanceBlocks));

        int ticksToTarget = (int) Math.ceil(cappedDist / speed);
        int ticks = ticksToTarget + 40;

        if (ticks < 60) ticks = 60;
        if (ticks > HARD_MAX_TICKS) ticks = HARD_MAX_TICKS;
        return ticks;
    }

    // Optional debug helper
    public static void logLeadByBlocks(Vec3 targetPosNow, Vec3 aimPoint, Vec3 targetVelPerTick) {
        if (targetPosNow == null || aimPoint == null) return;

        Vec3 leadVec = aimPoint.subtract(targetPosNow);
        double totalLead = leadVec.length();

        double directionalLead = 0.0;
        if (targetVelPerTick != null && targetVelPerTick.lengthSqr() > 1.0e-9) {
            directionalLead = leadVec.dot(targetVelPerTick.normalize());
        }

        LOGGER.debug("Lead debug -> totalLead={} directionalLead={} leadVec={} targetVelPerTick={}",
                totalLead, directionalLead, leadVec, targetVelPerTick);
    }
}
