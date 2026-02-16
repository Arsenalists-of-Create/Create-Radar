package com.happysg.radar.compat.cbc;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;

import java.util.Comparator;
import java.util.List;

public class CannonLead {
    private static final double VEL_EPS = .01;
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
        // i’m using x = x0 + v*t + 0.5*a*t^2
        return pos0
                .add(velPerTick.scale(tTicks))
                .add(accelPerTick2.scale(0.5 * tTicks * tTicks));
    }

    public static Vec3 predictVelocityTicks(Vec3 vel0PerTick, Vec3 accelPerTick2, double tTicks) {
        // i’m using v = v0 + a*t
        return vel0PerTick.add(accelPerTick2.scale(tTicks));
    }

    // -------------------------
    // aiming helpers
    // -------------------------

    public static Vec3 directionFromYawPitch(double yawRad, double pitchRad) {
        // i’m matching the usual MC-style yaw in the XZ plane and pitch upward
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
     * i’m simming the projectile forward until it reaches the requested horizontal distance.
     * gravityPerTick is blocks/tick^2 (CBC ballistic gravity is typically already tick tuned)
     * drag is applied as a per-tick damping (big cannons); set applyDrag=false for no-drag weapons
     */
    public static SimResult simulateFlightTicks(
            Vec3 muzzlePos,
            Vec3 shooterVelPerTickAtFire,
            Vec3 dirUnit,
            double muzzleSpeedPerTick,
            double gravityPerTick,
            double drag,
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

            // i’m applying gravity per tick
            vel = vel.add(0.0, gravityPerTick, 0.0);

            // i’m applying drag as a per-tick damping; if this is slightly off from CBC, this is the knob to swap
            if (applyDrag && drag != 0.0) {
                vel = vel.scale(1.0 - drag);
            }

            // i’m integrating one tick forward
            pos = pos.add(vel);
        }

        return new SimResult(maxTicks, pos, vel);
    }

    public static SimResult simulateFlightTicksCBC(
            Vec3 muzzlePos,
            Vec3 shooterVelPerTickAtFire,
            Vec3 dirUnit,
            double muzzleSpeedPerTick,

            double gravityPerTick,
            double formDrag,
            boolean quadraticDrag,

            double targetHorizontalDist,
            int maxTicks
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


            double speed = vel.length();

            // accel = gravity + dragAccel
            Vec3 accel = new Vec3(0.0, gravityPerTick, 0.0);

            if (speed > 1.0e-9 && formDrag != 0.0) {
                // CBC: dragForce = formDrag * density * |v| (*|v| if quadratic), capped to |v|
                double dragForce = formDrag * speed;       // density assumed 1.0 (air)
                if (quadraticDrag) dragForce *= speed;
                dragForce = Math.min(dragForce, speed);

                accel = accel.add(vel.normalize().scale(-dragForce));
            }

            // CBC integration
            pos = pos.add(vel).add(accel.scale(0.5));
            vel = vel.add(accel);
        }

        return new SimResult(maxTicks, pos, vel);
    }

    // -------------------------
    // main solver
    // -------------------------

    /**
     * requires:
     * - shooterVelPerTick / shooterAccelPerTick2 in world-space
     * - targetVelPerTick / targetAccelPerTick2 in world-space
     *
     * notes:
     * - shooter acceleration is only relevant up to fire time (fireDelayTicks)
     * - target acceleration is relevant until impact (fireDelay + flight)
     */
    public static LeadSolution solveLeadPerTickWithAcceleration(
            CannonMountBlockEntity mount,
            AbstractMountedCannonContraption cannon,
            ServerLevel level,

//            Vec3 shooterVelPerTick,
//            Vec3 shooterAccelPerTick2,

            Vec3 targetPosNow,
            Vec3 targetVelPerTick,
            Vec3 targetAccelPerTick2,

            int fireDelayTicks,
            double maxSimDistanceBlocks
    ) {
        if (mount == null || cannon == null || level == null) return null;
        if (targetPosNow == null || targetVelPerTick == null || targetAccelPerTick2 == null) return null;

        // Neoforge 1.21.1 Temp
        Vec3 shooterVelPerTick = null;
        Vec3 shooterAccelPerTick2 = null;

        if (shooterVelPerTick == null || shooterAccelPerTick2 == null) return null;
        boolean targetMoving = targetVelPerTick.lengthSqr() >= VEL_EPS_SQR;
        boolean shooterMoving = shooterVelPerTick.lengthSqr() >= VEL_EPS_SQR;

        if (!targetMoving) {
            return new LeadSolution(targetPosNow, 0.0, 0.0, 0);
        }

        if (!shooterMoving) {
            shooterVelPerTick = Vec3.ZERO;
            shooterAccelPerTick2 = Vec3.ZERO;
        }

        double muzzleSpeedPerTick = CannonUtil.getInitialVelocity(cannon, level);
        if (muzzleSpeedPerTick <= 0.0) return null;

        Vec3 originNow = PhysicsHandler.getWorldVec(level, mount.getControllerBlockPos().above(2).getCenter());
        int barrelLength = CannonUtil.getBarrelLength(cannon);

        BallisticPropertiesComponent bp = CannonUtil.getBallistics(cannon, level);
        double gravityPerTick = bp.gravity();         // (your old path was fine too)
        double formDrag = bp.drag();
        boolean quadratic = bp.isQuadraticDrag();


        // i’m predicting shooter state at the moment the projectile spawns
        Vec3 shooterPosAtFire = predictPositionTicks(originNow, shooterVelPerTick, shooterAccelPerTick2, fireDelayTicks);
        Vec3 shooterVelAtFire = predictVelocityTicks(shooterVelPerTick, shooterAccelPerTick2, fireDelayTicks);

        Vec3 targetPosRel0 = targetPosNow.subtract(shooterPosAtFire);
        Vec3 targetVelRel = targetVelPerTick.subtract(shooterVelAtFire);
        Vec3 targetAccelRel = targetAccelPerTick2.subtract(shooterAccelPerTick2);

        // initial guess: straight-line ticks (horizontal only)
        double dx0 = targetPosNow.x - shooterPosAtFire.x;
        double dz0 = targetPosNow.z - shooterPosAtFire.z;
        double horiz0 = Math.sqrt(dx0 * dx0 + dz0 * dz0);
        double tGuessTicks = horiz0 / Math.max(1.0e-6, muzzleSpeedPerTick);

        Vec3 aimPoint = targetPosNow;
        double chosenPitchDeg = 0.0;
        double chosenYawRad = 0.0;
        int flightTicks = (int) Math.round(tGuessTicks);

        // i’m iterating to converge predicted impact time and ballistic time-of-flight
        for (int iter = 0; iter < 8; iter++) {
            double tImpactTicks = fireDelayTicks + tGuessTicks;

            // i’m predicting the target at impact using constant acceleration
            Vec3 aimRel = predictPositionTicks(targetPosRel0, targetVelRel, targetAccelRel, tImpactTicks);
            aimPoint = shooterPosAtFire.add(aimRel);

            // i’m yawing from the shooter position at fire time (where the projectile will actually spawn)
            Vec3 toPred = aimPoint.subtract(shooterPosAtFire);
            chosenYawRad = Math.atan2(toPred.z, toPred.x);

// cheap pitch guess: point at the intercept (no ballistic solve here)
            double horizToPred = Math.sqrt(toPred.x * toPred.x + toPred.z * toPred.z);
            double pitchRad = Math.atan2(toPred.y, Math.max(1.0e-6, horizToPred));

            Vec3 dir = directionFromYawPitch(chosenYawRad, pitchRad);

// optional: keep pitchDeg populated for debugging only
            chosenPitchDeg = Math.toDegrees(pitchRad);

            // i’m offsetting muzzle forward along the barrel direction
            Vec3 muzzlePosAtFire = shooterPosAtFire.add(dir.scale(barrelLength));

            // i’m measuring horizontal distance from muzzle to predicted point (this is what the sim uses to stop)
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
                    horiz,
                    computeMaxSimTicks(horiz, muzzleSpeedPerTick, maxSimDistanceBlocks),
                    true
            );


            int newFlightTicks = sim.ticks;

            // i’m stopping when flight time stabilizes
            if (Math.abs(newFlightTicks - tGuessTicks) < 0.5) {
                flightTicks = newFlightTicks;
                tGuessTicks = newFlightTicks;
                break;
            }

            flightTicks = newFlightTicks;
            tGuessTicks = newFlightTicks;
        }
        //logLeadByBlocks(targetPosNow,aimPoint,targetVelPerTick);
        return new LeadSolution(aimPoint, chosenPitchDeg, chosenYawRad, flightTicks);
    }

    private static int computeMaxSimTicks(double targetHorizontalDist, double muzzleSpeedPerTick, double maxSimDistanceBlocks) {
        final int HARD_MAX_TICKS = 8000;

        double speed = Math.max(1.0e-6, muzzleSpeedPerTick);

        // cap sim distance by radar-derived max distance (relative to cannon)
        double cappedDist = Math.min(targetHorizontalDist, Math.max(0.0, maxSimDistanceBlocks));

        int ticksToTarget = (int) Math.ceil(cappedDist / speed);

        int ticks = ticksToTarget + 40;

        if (ticks < 60) ticks = 60;
        if (ticks > HARD_MAX_TICKS) ticks = HARD_MAX_TICKS;
        return ticks;
    }


    public static void logLeadByBlocks(Vec3 targetPosNow, Vec3 aimPoint, Vec3 targetVelPerTick) {
        if (targetPosNow == null || aimPoint == null) return;

        Vec3 leadVec = aimPoint.subtract(targetPosNow);
        double totalLead = leadVec.length();

        double directionalLead = 0.0;
        if (targetVelPerTick != null && targetVelPerTick.lengthSqr() > 1.0e-9) {
            directionalLead = leadVec.dot(targetVelPerTick.normalize());
        }

        LOGGER.warn("Lead debug → totalLead={} directionalLead={} leadVec={} targetVelPerTick={}",
                totalLead, directionalLead, leadVec, targetVelPerTick);
    }
}
