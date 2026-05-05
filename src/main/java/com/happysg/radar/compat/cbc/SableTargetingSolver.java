package com.happysg.radar.compat.cbc;

import com.happysg.radar.math3.analysis.MultivariateFunction;
import com.happysg.radar.math3.optim.InitialGuess;
import com.happysg.radar.math3.optim.MaxEval;
import com.happysg.radar.math3.optim.PointValuePair;
import com.happysg.radar.math3.optim.SimpleBounds;
import com.happysg.radar.math3.optim.nonlinear.scalar.GoalType;
import com.happysg.radar.math3.optim.nonlinear.scalar.MultiStartMultivariateOptimizer;
import com.happysg.radar.math3.optim.nonlinear.scalar.ObjectiveFunction;
import com.happysg.radar.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer;
import com.happysg.radar.math3.random.RandomVectorGenerator;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector3d;

import java.util.*;

import static com.happysg.radar.compat.cbc.CannonUtil.getCannonMountOffset;
import static java.lang.Math.*;

public class SableTargetingSolver {
    private final double u;
    private final double drag;
    private final Vec3 targetPos;
    private final Vec3 mountPos;
    private final double g;
    private final Level level;
    double initialTheta;
    double initialZeta;
    double initialPsi;
    double l;
    Matrix4d shipToWorld = new Matrix4d();
    Matrix4d worldToShip = new Matrix4d();

    private static final double TOLERANCE = 1e-3;

    public SableTargetingSolver(Level level, double u, double drag, double g, double barrelLength, Vec3 mountPos, Vec3 targetPos, double initialTheta, double initialZeta, double initialPsi, SubLevel subLevel) {
        this.level = level;
        this.u = u;
        this.drag = drag;
        this.g = abs(g);
        this.initialTheta = initialTheta;
        this.initialZeta = initialZeta;
        this.initialPsi = initialPsi;
        
        var pose = subLevel.logicalPose();
        pose.bakeIntoMatrix(this.shipToWorld);
        this.shipToWorld.invert(this.worldToShip);
        
        this.targetPos = targetPos;
        this.mountPos = mountPos; // shipyard coord
        this.l = barrelLength;
    }

    private MultivariateFunction createFunction() {
        return point -> {
            double theta = point[0];
            double zeta = point[1];
            double thetaRad = toRadians(theta);
            double zetaRad = toRadians(zeta);
            
            Vec3 pivotPoint = mountPos;
            // Front of barrel in local ship space
            Vec3 shipyardFrontOfBarrel = mountPos.add(cos(zetaRad + PI / 2) * cos(thetaRad) * l, sin(thetaRad) * l, sin(zetaRad + PI / 2) * cos(thetaRad) * l);

            Vec3 offset = getCannonMountOffset(level, net.minecraft.core.BlockPos.containing(mountPos.x, mountPos.y, mountPos.z));
            pivotPoint = pivotPoint.add(offset);
            shipyardFrontOfBarrel = shipyardFrontOfBarrel.add(offset);

            // Transform to world space
            Vector3d vFront = new Vector3d(shipyardFrontOfBarrel.x, shipyardFrontOfBarrel.y, shipyardFrontOfBarrel.z);
            shipToWorld.transformPosition(vFront);
            Vec3 frontOfBarrel = new Vec3(vFront.x, vFront.y, vFront.z);
            
            Vector3d vPivot = new Vector3d(pivotPoint.x, pivotPoint.y, pivotPoint.z);
            shipToWorld.transformPosition(vPivot);
            Vec3 frontOfPivot = new Vec3(vPivot.x, vPivot.y, vPivot.z);

            Vec3 diffVec = targetPos.subtract(frontOfBarrel);
            double dZ = diffVec.z;
            double dY = diffVec.y;
            double dX = diffVec.x;

            Vec3 pivotDir = frontOfBarrel.subtract(frontOfPivot).normalize();
            double pitch = asin(pivotDir.y);
            double yaw = atan2(pivotDir.z, pivotDir.x);
            if (yaw < 0) yaw += 2 * PI;
            
            thetaRad = Double.isNaN(pitch) ? 0 : pitch;
            zetaRad = Double.isNaN(yaw) ? 0 : yaw;

            double logVal = 1 - (drag * dZ) / (u * cos(thetaRad) * sin(zetaRad));
            if (logVal <= 0) return Double.POSITIVE_INFINITY;
            double time = log(logVal) / -drag;
            if (time <= 0) return Double.POSITIVE_INFINITY;
            double dragDecay = (1 - exp(-drag * time));
            double newX = u * cos(thetaRad) * cos(zetaRad) * dragDecay / drag;
            double newY = (drag * u * sin(thetaRad) + g) * dragDecay / (drag * drag) - g * time / drag;
            
            return abs(dY - newY) + abs(dX - newX);
        };
    }

    RandomVectorGenerator randomVectorGenerator = new RandomVectorGenerator() {
        private final Random random = new Random();
        @Override
        public double[] nextVector() {
            double theta = -90 + random.nextDouble() * 180;
            double zeta = random.nextDouble() * 360;
            return new double[]{theta, zeta};
        }
    };

    public List<List<Double>> solveThetaZeta() {
        int numStarts = 2;
        MultiStartMultivariateOptimizer optimizer = new MultiStartMultivariateOptimizer(
                new BOBYQAOptimizer(5), numStarts, randomVectorGenerator
        );

        double[] lowerBounds = {-90, 0};
        double[] upperBounds = {90, 360};
        try {
            optimizer.optimize(
                    new MaxEval(200),
                    new ObjectiveFunction(createFunction()),
                    GoalType.MINIMIZE,
                    new InitialGuess(new double[]{0, 0}),
                    new SimpleBounds(lowerBounds, upperBounds)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        PointValuePair[] optima = optimizer.getOptima();
        List<List<Double>> results = new ArrayList<>();
        Set<String> uniqueSolutions = new HashSet<>();
        for (PointValuePair opt : optima) {
            if (opt == null) continue;
            double error = opt.getValue();
            if (error < TOLERANCE) {
                double[] point = opt.getPoint();
                double theta = point[0];
                double zeta = point[1];
                String key = String.format("%d_%d", (int) Math.floor(theta), (int) Math.floor(zeta));
                if (!uniqueSolutions.contains(key)) {
                    uniqueSolutions.add(key);
                    results.add(List.of(theta, zeta));
                }
            }
        }
        return results;
    }
}
