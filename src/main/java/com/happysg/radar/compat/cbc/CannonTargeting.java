package com.happysg.radar.compat.cbc;

import com.happysg.radar.compat.vs2.PhysicsHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.solvers.BrentSolver;
import org.apache.commons.math3.analysis.solvers.UnivariateSolver;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Double.NaN;
import static java.lang.Math.log;
import static java.lang.Math.toRadians;

public class CannonTargeting {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static Double calculateYaw(CannonMountBlockEntity cannonMountBlockEntity, Vec3 targetPos) {
        Vec3 cannonCenter = cannonMountBlockEntity.getBlockPos().getCenter();
        double dx = cannonCenter.x - targetPos.x;
        double dz = cannonCenter.z - targetPos.z;

        Double targetYaw = Math.toDegrees(Math.atan2(dz, dx)) + 90;
        // Normalize yaw to 0-360 degrees
        if (targetYaw < 0) {
            targetYaw += 360;
        }
        return targetYaw;
    }

    public static double calculateProjectileYatX(double speed, double dX, double thetaRad,double drag, double g ) {
        double log = log(1 - (drag * dX) / (speed * Math.cos(thetaRad)));
        if (Double.isInfinite(log)) log = NaN;
        return dX * Math.tan(thetaRad) + (dX * g) / (drag * speed * Math.cos(thetaRad)) + g*log / (drag * drag);
    }

    public static List<Double> calculatePitch(CannonMountBlockEntity mount, Vec3 targetPos, ServerLevel level) {
        LOGGER.debug("calculatePitch start: mount={}, targetPos={}", mount.getBlockPos(), targetPos);
        if (targetPos == null) {
            return null;
        }

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if ( contraption == null || !(contraption.getContraption() instanceof AbstractMountedCannonContraption cannonContraption)) {
            LOGGER.debug(" → aborting: no contraption or wrong type");
            return null;
        }
        float speed = CannonUtil.getInitialVelocity(cannonContraption, level);
        LOGGER.debug(" → speed={}", speed);


        Vec3 originPos = PhysicsHandler.getWorldVec(level, mount.getBlockPos().above(2).getCenter());
        int barrelLength = CannonUtil.getBarrelLength(cannonContraption);

        double drag = CannonUtil.getProjectileDrag(cannonContraption, level);
        double gravity = CannonUtil.getProjectileGravity(cannonContraption, level);
        LOGGER.debug(" → origin={}, barrelLength={}, drag={}, gravity={}", originPos, barrelLength, drag, gravity);

        if (speed == 0) {
            LOGGER.debug(" → aborting: speed=0");
            return null;
        }
        double d1 = targetPos.x - originPos.x;
        double d2 = targetPos.z - originPos.z;
        double distance = Math.abs(Math.sqrt(d1 * d1 + d2 * d2));
        double d3 = targetPos.y - originPos.y;
        LOGGER.debug(" → horizontalDist={}, verticalDist={}", distance, d3);
        double g = Math.abs(gravity);
        UnivariateFunction diffFunction = theta -> {
            double thetaRad = toRadians(theta);

            double dX = distance - (Math.cos(thetaRad) * (barrelLength));
            double dY = d3 - (Math.sin(thetaRad) * (barrelLength));
            double y = calculateProjectileYatX(speed, dX, thetaRad, drag, g);
            return y - dY;
        };

        UnivariateSolver solver = new BrentSolver(1e-32);

        double start = -90;
        double end = 90;
        double step = 1.0;
        List<Double> roots = new ArrayList<>();

        double prevValue = diffFunction.value(start);
        double prevTheta = start;
        for (double theta = start + step; theta <= end; theta += step) {
            double currValue = diffFunction.value(theta);
            if (prevValue * currValue < 0) {
                try {
                    double root = solver.solve(1000, diffFunction, prevTheta, theta);
                    LOGGER.debug("   • found root {} between {} and {}", root, prevTheta, theta);
                    roots.add(root);
                } catch (Exception e) {
                    LOGGER.debug("   • solver threw {}, aborting", e.toString());
                    return null;
                }
            }
            prevTheta = Double.isNaN(currValue) ? prevTheta : theta;
            prevValue = Double.isNaN(currValue) ? prevValue : currValue;
        }
        if (roots.isEmpty()) {
            LOGGER.debug(" → aborting: no roots found");
            return null;
        }
        LOGGER.debug(" → returning roots {}", roots);
        return roots;
    }
}
