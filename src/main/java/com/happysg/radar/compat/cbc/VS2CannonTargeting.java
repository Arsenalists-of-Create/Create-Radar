package com.happysg.radar.compat.cbc;

import com.happysg.radar.compat.cbc_at.CBCATCannonCompat;
import com.happysg.radar.compat.cbc_at.CBCATRocketAimSolver;
import com.happysg.radar.compat.cbc_at.CBCATRocketProjectileModel;
import com.happysg.radar.compat.cbcmoreshells.CBCMSAimSolver;
import com.happysg.radar.compat.cbcmoreshells.CBCMSCannonCompat;
import com.happysg.radar.compat.cbcmw.CBCMWCannonCompat;
import com.happysg.radar.compat.vs2.SableUtils;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.targeting.PitchConstraint;
import com.happysg.radar.targeting.ObstructionChecker;
import com.happysg.radar.targeting.TargetingSnapshot;
import com.happysg.radar.targeting.TargetingMath;
import com.happysg.radar.targeting.TargetingResult;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import java.util.List;

public class VS2CannonTargeting {
    private static List<List<Double>> directAimToTarget(Vec3 mountPos, Vec3 targetPos) {
        Vec3 diff = targetPos.subtract(mountPos);
        double horizontal = Math.hypot(diff.x, diff.z);
        double pitch = Math.toDegrees(Math.atan2(diff.y, horizontal));
        double yaw = wrap360(Math.toDegrees(Math.atan2(diff.z, diff.x)) + 270.0);
        return List.of(List.of(pitch, yaw));
    }

    private static double wrap360(double deg) {
        deg %= 360.0;
        if (deg < 0) {
            deg += 360.0;
        }
        return deg;
    }

    public static List<List<Double>> calculatePitchAndYawVS2(CannonMountContext mount, Vec3 targetPos, ServerLevel level) {
        return calculatePitchAndYawVS2(mount, targetPos, level, null, null);
    }

    public static List<List<Double>> calculatePitchAndYawVS2(CannonMountContext mount, Vec3 targetPos, ServerLevel level, Double preferredPitchDeg, Double preferredYawDeg) {
        if (mount == null || targetPos == null) {
            return null;
        }

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null || !(contraption.getContraption() instanceof AbstractMountedCannonContraption cannonContraption)) {
            return null;
        }

        Vec3 mountPos = mount.getBlockPos().getCenter();
        int barrelLength = CannonUtil.getBarrelLength(cannonContraption);
        Direction initialDirection = cannonContraption.initialOrientation();

        if (CannonUtil.isLaserCannon(cannonContraption)) {
            SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(level, mountPos);
            Vec3 localTarget = ship == null ? targetPos : toShipPosition(ship, targetPos);
            return directAimToTarget(mountPos, localTarget);
        }

        CBCMSCannonCompat.ShotState cbcmsShot = CannonUtil.resolveCBCMSShotState(cannonContraption, level);
        if (cbcmsShot != null) {
            if ((Boolean) RadarConfig.server().forceLegacyCannonLeadSolver.get()
                    && !cbcmsShot.legacyEligible()) {
                return null;
            }
            Vec3 muzzleShipyard = CBCMuzzleUtil.getCBCSpawnAnchorWorld(contraption);
            SubLevelAccess mountShip = SableCompanion.INSTANCE.getContaining(level, mountPos);
            Vec3 muzzleWorld = SableUtils.getWorldVec(muzzleShipyard, mountShip);
            if (cbcmsShot.solverMode() == CBCMSCannonCompat.SolverMode.CBCMS_SERVER) {
                Double solverPreferredYaw = preferredYawDeg == null
                        ? null
                        : TargetingMath.wrap180(preferredYawDeg - 270.0);
                TargetingSnapshot snapshot = TargetingSnapshot.builder(level)
                        .muzzlePosition(muzzleWorld)
                        .targetPosition(targetPos)
                        .projectileSpeed(cbcmsShot.projectileModel().muzzleSpeed())
                        .gravity(cbcmsShot.projectileModel().gravity())
                        .drag(cbcmsShot.projectileModel().drag())
                        .quadraticDrag(cbcmsShot.projectileModel().quadraticDrag())
                        .cbcPhysics(true)
                        .dragDensity(cbcmsShot.projectileModel().dragDensity())
                        .maxFlightTicks(cbcmsShot.lifetimeCapTicks())
                        .preferredPitchDeg(preferredPitchDeg)
                        .preferredYawDeg(solverPreferredYaw)
                        .pitchConstraint(PitchConstraint.unconstrained())
                        .build();
                TargetingResult result = CBCMSAimSolver.createComputer(ObstructionChecker.NONE)
                        .solve(snapshot, cbcmsShot.projectileModel());
                if (result == null || !result.valid() || !result.hasShot() || result.aimSolution() == null) {
                    return null;
                }
                Vec3 localDirection = mountShip == null
                        ? result.aimSolution().aimDirection()
                        : SableUtils.getShipVecDirectionTransform(result.aimSolution().aimDirection(), mountShip);
                TargetingMath.YawPitch angles = TargetingMath.yawPitchFromDirection(localDirection);
                return List.of(List.of(angles.pitchDeg(), wrap360(angles.yawDeg() + 270.0)));
            }
            Vec3 localTarget = mountShip == null ? targetPos : toShipPosition(mountShip, targetPos);
            List<Double> roots = CannonTargeting.calculatePitch(mount, muzzleShipyard, localTarget, level);
            if (roots == null || roots.isEmpty()) return null;
            double pitch = roots.getFirst();
            Vec3 diff = localTarget.subtract(muzzleShipyard);
            double yaw = wrap360(Math.toDegrees(Math.atan2(diff.z, diff.x)) + 270.0);
            return List.of(List.of(pitch, yaw));
        }
        if (CannonUtil.isCBCMSCannon(cannonContraption)) {
            return null;
        }

        if (CannonUtil.isPoweredRocket(cannonContraption)) {
            if ((Boolean) RadarConfig.server().forceLegacyCannonLeadSolver.get()) {
                return null;
            }
            CBCATCannonCompat.ShotState shot = CannonUtil.resolveCBCATShotState(cannonContraption, level);
            CBCATRocketProjectileModel model = shot == null ? null : shot.rocketModel();
            if (model == null) {
                return null;
            }
            Vec3 muzzleShipyard = CBCMuzzleUtil.getCBCSpawnAnchorWorld(contraption);
            SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(level, mountPos);
            Vec3 muzzleWorld = SableUtils.getWorldVec(muzzleShipyard, ship);
            Double solverPreferredYaw = preferredYawDeg == null
                    ? null
                    : TargetingMath.wrap180(preferredYawDeg - 270.0);
            TargetingResult result = CBCATRocketAimSolver.solveStationary(
                    level,
                    muzzleWorld,
                    targetPos,
                    model,
                    false,
                    preferredPitchDeg,
                    solverPreferredYaw,
                    PitchConstraint.unconstrained()
            );
            if (result == null || !result.valid() || !result.hasShot() || result.aimSolution() == null) {
                return null;
            }
            Vec3 localDirection = ship == null
                    ? result.aimSolution().aimDirection()
                    : SableUtils.getShipVecDirectionTransform(result.aimSolution().aimDirection(), ship);
            TargetingMath.YawPitch angles = TargetingMath.yawPitchFromDirection(localDirection);
            return List.of(List.of(angles.pitchDeg(), wrap360(angles.yawDeg() + 270.0)));
        }

        CBCMWCannonCompat.ShotState cbcmwShot =
                CannonUtil.resolveCBCMWShotState(cannonContraption, level);
        if (CannonUtil.isCBCMWCannon(cannonContraption)) {
            if (cbcmwShot == null) {
                return null;
            }
            Vec3 muzzleShipyard = CBCMuzzleUtil.getCBCSpawnAnchorWorld(contraption);
            SubLevelAccess mountShip = SableCompanion.INSTANCE.getContaining(level, mountPos);
            Vec3 localTarget = mountShip == null ? targetPos : toShipPosition(mountShip, targetPos);
            List<Double> roots = CannonTargeting.calculatePitch(
                    mount, muzzleShipyard, localTarget, level);
            if (roots == null || roots.isEmpty()) {
                return null;
            }
            Vec3 difference = localTarget.subtract(muzzleShipyard);
            double yaw = wrap360(Math.toDegrees(Math.atan2(difference.z, difference.x)) + 270.0);
            return List.of(List.of(roots.getFirst(), yaw));
        }

        float chargePower = CannonUtil.getInitialVelocity(cannonContraption, level);
        double drag = CannonUtil.getProjectileDrag(cannonContraption, level);
        double gravity = CannonUtil.getProjectileGravity(cannonContraption, level);

        if (chargePower <= 0) {
            if (CannonUtil.isCBCATCannon(cannonContraption)) {
                return null;
            }
            return directAimToTarget(mountPos, targetPos);
        }

//        return calculatePitchAndYawVS2(level, chargePower, targetPos, mountPos, barrelLength, initialDirection, drag, gravity);
        return calculatePitchAndYawVS2(level, chargePower, targetPos, mountPos, barrelLength, initialDirection, drag, gravity, preferredPitchDeg, preferredYawDeg);
    }

    private static Vec3 toShipPosition(SubLevelAccess ship, Vec3 worldPos) {
        Vector3d local = ship.logicalPose().transformPositionInverse(new Vector3d(worldPos.x, worldPos.y, worldPos.z));
        return new Vec3(local.x(), local.y(), local.z());
    }

    public static List<List<Double>> calculatePitchAndYawVS2(Level level, double speed, Vec3 targetPos, Vec3 mountPos, int barrelLength, Direction initialDirection, double drag, double gravity) {
        return calculatePitchAndYawVS2(level, speed, targetPos, mountPos, barrelLength, initialDirection, drag, gravity, null, null);
    }

    public static List<List<Double>> calculatePitchAndYawVS2(Level level, double speed, Vec3 targetPos, Vec3 mountPos, int barrelLength, Direction initialDirection, double drag, double gravity, Double preferredPitchDeg, Double preferredYawDeg) {
        SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(level, mountPos);
        if (ship == null) {
            System.out.println("null");
            return null;
        }
        Vector3d right = ship.logicalPose().transformNormal(new Vector3d(1, 0, 0));
        Vector3d up    = ship.logicalPose().transformNormal(new Vector3d(0, 1, 0));
        Vector3d fwd   = ship.logicalPose().transformNormal(new Vector3d(0, 0, 1));
        Matrix3d rot = new Matrix3d(right.x, right.y, right.z, up.x, up.y, up.z, fwd.x, fwd.y, fwd.z);
        Vector3d eulerAngles = new Vector3d();
        new Quaterniond().setFromNormalized(rot).getEulerAnglesYXZ(eulerAngles);
        double x = eulerAngles.x;
        double z = eulerAngles.z;
        double initialZeta = -eulerAngles.y; // Yaw
        double initialPsi = 0; // Roll
        double initialTheta = 0; // Pitch

        if (initialDirection == Direction.NORTH) {
            initialPsi = -z;
            initialTheta = x;
        } else if (initialDirection == Direction.SOUTH) {
            initialPsi = z;
            initialTheta = -x;
        } else if (initialDirection == Direction.EAST) {
            initialPsi = x;
            initialTheta = z;
        } else if (initialDirection == Direction.WEST) {
            initialPsi = -x;
            initialTheta = -z;
        }

//        VS2TargetingSolver targetingSolver = new VS2TargetingSolver(level, speed, drag, gravity, barrelLength, mountPos, targetPos, initialTheta, initialZeta, initialPsi, ship);
        VS2TargetingSolver targetingSolver = new VS2TargetingSolver(level, speed, drag, gravity, barrelLength, mountPos, targetPos, initialTheta, initialZeta, initialPsi, ship, preferredPitchDeg, preferredYawDeg);
        return targetingSolver.solveThetaZeta();
    }
}
