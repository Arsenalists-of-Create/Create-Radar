package com.happysg.radar.compat.cbc;

import com.happysg.radar.compat.cbc_at.CBCATCannonCompat;
import com.happysg.radar.compat.cbc_at.CBCATRocketAimSolver;
import com.happysg.radar.compat.cbc_at.CBCATRocketProjectileModel;
import com.happysg.radar.compat.cbcmoreshells.CBCMSAimSolver;
import com.happysg.radar.compat.cbcmoreshells.CBCMSCannonCompat;
import com.happysg.radar.compat.cbcmw.CBCMWCannonCompat;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.math3.analysis.UnivariateFunction;
import com.happysg.radar.math3.analysis.solvers.BrentSolver;
import com.happysg.radar.math3.analysis.solvers.UnivariateSolver;
import com.mojang.logging.LogUtils;
import com.happysg.radar.targeting.ObstructionChecker;
import com.happysg.radar.targeting.PitchConstraint;
import com.happysg.radar.targeting.TargetingResult;
import com.happysg.radar.targeting.TargetingSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionProperties;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionPropertiesHandler;
import rbasamoyai.createbigcannons.munitions.config.FluidDragHandler;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Double.NaN;
import static java.lang.Math.log;
import static java.lang.Math.toRadians;

public class CannonTargeting {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double BIG_CANNON_PITCH_MIN = -89.0;
    private static final double BIG_CANNON_PITCH_MAX = 89.0;
    private static final double BIG_CANNON_SCAN_STEP = 1.0;
    private static final double BIG_CANNON_ROOT_EPS = 1.0e-4;
    private static final int BIG_CANNON_ROOT_ITERS = 48;
    private static final int BIG_CANNON_MAX_SIM_TICKS = 8000;

    private static List<Double> directPitchToTarget(Vec3 originPos, Vec3 targetPos) {
        double dX = Math.hypot(targetPos.x - originPos.x, targetPos.z - originPos.z);
        double dY = targetPos.y - originPos.y;
        return List.of(Math.toDegrees(Math.atan2(dY, dX)));
    }

    public static double calculateProjectileYatX(double speed, double dX, double thetaRad, double drag, double g) {
        double l = log(1 - (drag * dX) / (speed * Math.cos(thetaRad)));
        if (Double.isInfinite(l)) l = NaN;
        return dX * Math.tan(thetaRad)
                + (dX * g) / (drag * speed * Math.cos(thetaRad))
                + g * l / (drag * drag);
    }

    public static List<Double> calculatePitch(
            CannonMountContext mount,
            Vec3 originPos,
            Vec3 targetPos,
            ServerLevel level
    ) {
        if (mount == null || targetPos == null || originPos == null) return null;

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null || !(contraption.getContraption() instanceof AbstractMountedCannonContraption cannon)) return null;

        if (CannonUtil.isLaserCannon(cannon)) {
            return directPitchToTarget(originPos, targetPos);
        }

        CBCMSCannonCompat.ShotState cbcmsShot = CannonUtil.resolveCBCMSShotState(cannon, level);
        if (cbcmsShot != null) {
            if ((Boolean) RadarConfig.server().forceLegacyCannonLeadSolver.get()
                    && !cbcmsShot.legacyEligible()) {
                return null;
            }
            Vec3 muzzle = CBCMuzzleUtil.getCBCSpawnAnchorWorld(contraption);
            if (cbcmsShot.solverMode() == CBCMSCannonCompat.SolverMode.CBCMS_SERVER) {
                TargetingSnapshot snapshot = TargetingSnapshot.builder(level)
                        .muzzlePosition(muzzle)
                        .targetPosition(targetPos)
                        .projectileSpeed(cbcmsShot.projectileModel().muzzleSpeed())
                        .gravity(cbcmsShot.projectileModel().gravity())
                        .drag(cbcmsShot.projectileModel().drag())
                        .quadraticDrag(cbcmsShot.projectileModel().quadraticDrag())
                        .cbcPhysics(true)
                        .dragDensity(cbcmsShot.projectileModel().dragDensity())
                        .maxFlightTicks(cbcmsShot.lifetimeCapTicks())
                        .pitchConstraint(PitchConstraint.unconstrained())
                        .build();
                TargetingResult result = CBCMSAimSolver.createComputer(ObstructionChecker.NONE)
                        .solve(snapshot, cbcmsShot.projectileModel());
                return result != null && result.valid() && result.hasShot()
                        ? List.of(result.desiredPitchDeg())
                        : null;
            }
            List<Double> roots = calculateSimulatedPitchRoots(
                    muzzle, targetPos, cbcmsShot.projectileModel().muzzleSpeed(),
                    cbcmsShot.projectileModel().gravity(), cbcmsShot.projectileModel().drag(), 0.0,
                    cbcmsShot.projectileModel().dragDensity(), cbcmsShot.projectileModel().quadraticDrag(), level);
            return roots.isEmpty() ? null : roots;
        }
        if (CannonUtil.isCBCMSCannon(cannon)) {
            return null;
        }

        CBCATCannonCompat.ShotState cbcAtShot = CannonUtil.resolveCBCATShotState(cannon, level);
        if (CannonUtil.isPoweredRocket(cannon)) {
            if ((Boolean) RadarConfig.server().forceLegacyCannonLeadSolver.get()) {
                LOGGER.debug("cbc_at_powered_rocket_unsupported_in_forced_legacy");
                return null;
            }
            CBCATRocketProjectileModel rocketModel = cbcAtShot == null ? null : cbcAtShot.rocketModel();
            if (rocketModel == null) {
                return null;
            }
            Vec3 muzzle = CBCMuzzleUtil.getCBCSpawnAnchorWorld(contraption);
            List<Double> roots = CBCATRocketAimSolver.solveStationaryPitchRoots(level, muzzle, targetPos, rocketModel);
            return roots.isEmpty() ? null : roots;
        }
        if (CannonUtil.isCBCATCannon(cannon) && cbcAtShot == null) {
            return null;
        }

        if (CannonUtil.isBigCannon(cannon)) {
            List<Double> roots = calculateBigCannonPitch(cannon, originPos, targetPos, level);
            return roots.isEmpty() ? null : roots;
        }
        if (cbcAtShot != null) {
            Vec3 muzzle = CBCMuzzleUtil.getCBCSpawnAnchorWorld(contraption);
            List<Double> roots = calculateSimulatedPitchRoots(
                    muzzle,
                    targetPos,
                    cbcAtShot.projectileModel().muzzleSpeed(),
                    cbcAtShot.projectileModel().gravity(),
                    cbcAtShot.projectileModel().drag(),
                    0.0,
                    cbcAtShot.projectileModel().dragDensity(),
                    cbcAtShot.projectileModel().quadraticDrag(),
                    level
            );
            return roots.isEmpty() ? null : roots;
        }

        CBCMWCannonCompat.ShotState cbcmwShot = CannonUtil.resolveCBCMWShotState(cannon, level);
        if (CannonUtil.isCBCMWCannon(cannon)) {
            if (cbcmwShot == null) {
                return null;
            }
            BallisticPropertiesComponent ballistics = cbcmwShot.ballistics();
            DimensionMunitionProperties dimension = DimensionMunitionPropertiesHandler.getProperties(level);
            Vec3 muzzle = CBCMuzzleUtil.getCBCSpawnAnchorWorld(contraption);
            List<Double> roots = calculateSimulatedPitchRoots(
                    muzzle,
                    targetPos,
                    cbcmwShot.speed(),
                    ballistics.gravity() * dimension.gravityMultiplier(),
                    ballistics.drag(),
                    0.0,
                    dimension.dragMultiplier(),
                    ballistics.isQuadraticDrag(),
                    level
            );
            return roots.isEmpty() ? null : roots;
        }

        CannonUtil.logCannonTypeReadFailure("calculatePitch", cannon);

        float speed = CannonUtil.getInitialVelocity(cannon, level);
        double drag = CannonUtil.getProjectileDrag(cannon, level);
        double gravity = CannonUtil.getProjectileGravity(cannon, level);
        if (speed <= 0) return directPitchToTarget(originPos, targetPos);

        double dX = Math.hypot(targetPos.x - originPos.x, targetPos.z - originPos.z);
        double dY = targetPos.y - originPos.y;
        double g = Math.abs(gravity);

        UnivariateFunction diffFunction = theta -> {
            double thetaRad = toRadians(theta);
            double y = calculateProjectileYatX(speed, dX, thetaRad, drag, g);
            return y - dY;
        };

        UnivariateSolver solver = new BrentSolver(1e-32);

        double start = -90, end = 90, step = 1.0;
        List<Double> roots = new ArrayList<>();

        double prevValue = diffFunction.value(start);
        double prevTheta = start;

        for (double theta = start + step; theta <= end; theta += step) {
            double currValue = diffFunction.value(theta);

            if (prevValue * currValue < 0) {
                try {
                    double root = solver.solve(1000, diffFunction, prevTheta, theta);
                    roots.add(root);
                } catch (Exception e) {
                    return null;
                }
            }

            prevTheta = Double.isNaN(currValue) ? prevTheta : theta;
            prevValue = Double.isNaN(currValue) ? prevValue : currValue;
        }

        return roots.isEmpty() ? null : roots;
    }

    private static List<Double> calculateBigCannonPitch(
            AbstractMountedCannonContraption cannon,
            Vec3 originPos,
            Vec3 targetPos,
            ServerLevel level
    ) {
        CannonUtil.BigCannonShotState shotState = CannonUtil.resolveBigCannonShotState(cannon, level);
        double speed = shotState.speed();
        BallisticPropertiesComponent ballistics = shotState.ballistics();
        DimensionMunitionProperties dimension = level == null ? new DimensionMunitionProperties(1.0, 1.0) : DimensionMunitionPropertiesHandler.getProperties(level);
        double gravity = ballistics.gravity() * dimension.gravityMultiplier();
        double drag = ballistics.drag();
        double dragDensity = dimension.dragMultiplier();
        boolean quadraticDrag = ballistics.isQuadraticDrag();
        double muzzleForwardOffset = shotState.muzzleForwardOffset();
        LOGGER.debug("Big cannon pitch solve: origin={} target={} speed={} projectile={} gravity={} drag={} dragDensity={} quadratic={} muzzleOffset={} reason={}",
                originPos, targetPos, speed, shotState.projectileClass(), gravity, drag, dragDensity, quadraticDrag, muzzleForwardOffset, shotState.reason());
        return calculateSimulatedPitchRoots(originPos, targetPos, speed, gravity, drag, muzzleForwardOffset, dragDensity, quadraticDrag, level);
    }

    public static List<Double> calculateSimulatedPitchRoots(
            Vec3 originPos,
            Vec3 targetPos,
            double speed,
            double gravity,
            double drag,
            int barrelLength
    ) {
        return calculateSimulatedPitchRoots(originPos, targetPos, speed, gravity, drag, Math.max(0, barrelLength), 1.0, false);
    }

    public static List<Double> calculateSimulatedPitchRoots(
            Vec3 originPos,
            Vec3 targetPos,
            double speed,
            double gravity,
            double drag,
            double muzzleForwardOffset,
            double dragDensity,
            boolean quadraticDrag
    ) {
        return calculateSimulatedPitchRoots(originPos, targetPos, speed, gravity, drag, muzzleForwardOffset, dragDensity, quadraticDrag, null);
    }

    private static List<Double> calculateSimulatedPitchRoots(
            Vec3 originPos,
            Vec3 targetPos,
            double speed,
            double gravity,
            double drag,
            double muzzleForwardOffset,
            double dragDensity,
            boolean quadraticDrag,
            ServerLevel level
    ) {
        if (originPos == null || targetPos == null) {
            return List.of();
        }
        if (!Double.isFinite(speed) || speed <= 0.0) {
            return directPitchToTarget(originPos, targetPos);
        }
        if (!Double.isFinite(gravity)) {
            gravity = -0.05;
        }
        if (gravity > 0.0) {
            gravity = -gravity;
        }
        if (!Double.isFinite(drag)) {
            drag = 0.0;
        }
        if (!Double.isFinite(muzzleForwardOffset)) {
            muzzleForwardOffset = 0.0;
        }
        muzzleForwardOffset = Math.max(0.0, muzzleForwardOffset);
        if (!Double.isFinite(dragDensity) || dragDensity < 0.0) {
            dragDensity = 1.0;
        }

        double dx = targetPos.x - originPos.x;
        double dz = targetPos.z - originPos.z;
        double horizontal = Math.hypot(dx, dz);
        if (horizontal < 1.0e-6) {
            return directPitchToTarget(originPos, targetPos);
        }

        Vec3 horizontalUnit = new Vec3(dx / horizontal, 0.0, dz / horizontal);
        List<Double> roots = new ArrayList<>();
        double prevPitch = Double.NaN;
        double prevError = Double.NaN;

        for (double pitch = BIG_CANNON_PITCH_MIN; pitch <= BIG_CANNON_PITCH_MAX + 1.0e-9; pitch += BIG_CANNON_SCAN_STEP) {
            double error = simulatedHeightError(originPos, targetPos, horizontalUnit, speed, gravity, drag, muzzleForwardOffset, dragDensity, quadraticDrag, level, pitch);
            if (!Double.isFinite(error)) {
                continue;
            }

            if (Math.abs(error) <= BIG_CANNON_ROOT_EPS) {
                addRoot(roots, pitch);
            } else if (Double.isFinite(prevError) && prevError * error < 0.0) {
                addRoot(roots, refineSimulatedPitchRoot(originPos, targetPos, horizontalUnit, speed, gravity, drag, muzzleForwardOffset, dragDensity, quadraticDrag, level, prevPitch, pitch));
            }

            prevPitch = pitch;
            prevError = error;
        }

        return List.copyOf(roots);
    }

    private static double refineSimulatedPitchRoot(
            Vec3 originPos,
            Vec3 targetPos,
            Vec3 horizontalUnit,
            double speed,
            double gravity,
            double drag,
            double muzzleForwardOffset,
            double dragDensity,
            boolean quadraticDrag,
            ServerLevel level,
            double lo,
            double hi
    ) {
        double loError = simulatedHeightError(originPos, targetPos, horizontalUnit, speed, gravity, drag, muzzleForwardOffset, dragDensity, quadraticDrag, level, lo);
        double hiError = simulatedHeightError(originPos, targetPos, horizontalUnit, speed, gravity, drag, muzzleForwardOffset, dragDensity, quadraticDrag, level, hi);
        if (!Double.isFinite(loError)) {
            return hi;
        }
        if (!Double.isFinite(hiError)) {
            return lo;
        }

        for (int i = 0; i < BIG_CANNON_ROOT_ITERS; i++) {
            double mid = (lo + hi) * 0.5;
            double midError = simulatedHeightError(originPos, targetPos, horizontalUnit, speed, gravity, drag, muzzleForwardOffset, dragDensity, quadraticDrag, level, mid);
            if (!Double.isFinite(midError) || Math.abs(midError) <= BIG_CANNON_ROOT_EPS) {
                return mid;
            }

            if (loError * midError <= 0.0) {
                hi = mid;
                hiError = midError;
            } else {
                lo = mid;
                loError = midError;
            }
        }

        return (lo + hi) * 0.5;
    }

    private static double simulatedHeightError(
            Vec3 originPos,
            Vec3 targetPos,
            Vec3 horizontalUnit,
            double speed,
            double gravity,
            double drag,
            double muzzleForwardOffset,
            double dragDensity,
            boolean quadraticDrag,
            ServerLevel level,
            double pitchDeg
    ) {
        double pitchRad = Math.toRadians(pitchDeg);
        double cos = Math.cos(pitchRad);
        if (cos <= 1.0e-6) {
            return Double.NaN;
        }

        Vec3 dir = new Vec3(horizontalUnit.x * cos, Math.sin(pitchRad), horizontalUnit.z * cos).normalize();
        Vec3 muzzle = originPos.add(dir.scale(muzzleForwardOffset));
        double targetTravel = targetPos.subtract(muzzle).dot(horizontalUnit);
        if (targetTravel <= 1.0e-6) {
            return Double.NaN;
        }

        Vec3 pos = muzzle;
        Vec3 vel = dir.scale(speed);
        double prevTravel = 0.0;
        double prevY = pos.y;
        int maxTicks = computeBigCannonSimTicks(targetTravel, speed);

        for (int tick = 0; tick <= maxTicks; tick++) {
            double travel = pos.subtract(muzzle).dot(horizontalUnit);
            if (travel >= targetTravel) {
                double span = travel - prevTravel;
                double t = Math.abs(span) <= 1.0e-9 ? 0.0 : (targetTravel - prevTravel) / span;
                double y = prevY + (pos.y - prevY) * Math.max(0.0, Math.min(1.0, t));
                return y - targetPos.y;
            }

            if (vel.lengthSqr() <= 1.0e-8) {
                return Double.NaN;
            }

            prevTravel = travel;
            prevY = pos.y;
            Vec3 acceleration = getCBCAcceleration(pos, vel, gravity, drag, dragDensity, quadraticDrag, level);
            pos = pos.add(vel).add(acceleration.scale(0.5));
            vel = vel.add(acceleration);
        }

        return Double.NaN;
    }

    private static Vec3 getCBCAcceleration(
            Vec3 position,
            Vec3 velocity,
            double gravity,
            double drag,
            double dragDensity,
            boolean quadraticDrag,
            ServerLevel level
    ) {
        double speed = velocity.length();
        Vec3 acceleration = new Vec3(0.0, gravity, 0.0);
        if (speed <= 1.0e-8 || drag <= 0.0) {
            return acceleration;
        }

        double density = dragDensity;
        if (level != null) {
            density += FluidDragHandler.getFluidDrag(level.getFluidState(BlockPos.containing(position)));
        }
        if (density <= 0.0) {
            return acceleration;
        }

        double dragForce = drag * density * speed;
        if (quadraticDrag) {
            dragForce *= speed;
        }
        dragForce = Math.min(dragForce, speed);
        return velocity.normalize().scale(-dragForce).add(acceleration);
    }

    private static int computeBigCannonSimTicks(double targetHorizontalDist, double speed) {
        int ticks = (int) Math.ceil(targetHorizontalDist / Math.max(1.0e-6, speed)) + 80;
        return Math.max(80, Math.min(BIG_CANNON_MAX_SIM_TICKS, ticks));
    }

    private static void addRoot(List<Double> roots, double root) {
        if (!Double.isFinite(root)) {
            return;
        }
        for (double existing : roots) {
            if (Math.abs(existing - root) < 0.05) {
                return;
            }
        }
        roots.add(root);
    }

    // OLD: legacy origin
    public static List<Double> calculatePitch(CannonMountContext mount, Vec3 targetPos, ServerLevel level) {
        if (mount == null || targetPos == null) return null;
        Vec3 originPos = PhysicsHandler.getWorldVec(level, mount.getBlockPos().above(2).getCenter());
        return calculatePitch(mount, originPos, targetPos, level);
    }
}
