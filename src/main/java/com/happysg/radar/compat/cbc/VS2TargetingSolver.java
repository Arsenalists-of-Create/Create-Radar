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
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import dev.ryanhcode.sable.companion.SubLevelAccess;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.*;

import static com.happysg.radar.compat.cbc.CannonUtil.getCannonMountOffset;
import static com.happysg.radar.compat.vs2.SableUtils.*;
import static java.lang.Math.*;

public class VS2TargetingSolver {
    private static final Logger LOGGER = LogUtils.getLogger();

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
    private final SubLevelAccess ship;
    private Double preferredThetaDeg = null;
    private Double preferredZetaDeg = null;

    private static final double TOLERANCE = 1e-3;
    private static final double MAX_MISS_DISTANCE_BLOCKS = 0.5;
    private static final double MIN_FLIGHT_TICKS = 0.05;
    private static final double MAX_FLIGHT_TICKS = 800.0;
    private static final double NEAR_VERTICAL_PITCH_DEG = 85.0;
    private static final double NEAR_VERTICAL_HORIZONTAL_BLOCKS = 2.0;


    // Constructor to set up the known values
    public VS2TargetingSolver(Level level, double u, double drag, double g, double barrelLength, Vec3 mountPos, Vec3 targetPos, double initialTheta, double initialZeta, double initialPsi, SubLevelAccess ship ) {
        this.level = level;
        this.u = u;
        this.drag = drag;
        this.g = abs(g);
        this.initialTheta = initialTheta;
        this.initialZeta = initialZeta;
        this.initialPsi = initialPsi;
        this.ship = ship;
        this.targetPos = targetPos;
        this.mountPos = mountPos; // shipyard coord
        this.l = barrelLength;


    }

    public VS2TargetingSolver(Level level, double u, double drag, double g, double barrelLength, Vec3 mountPos, Vec3 targetPos, double initialTheta, double initialZeta, double initialPsi, SubLevelAccess ship, Double preferredThetaDeg, Double preferredZetaDeg) {
        this(level, u, drag, g, barrelLength, mountPos, targetPos, initialTheta, initialZeta, initialPsi, ship);
        this.preferredThetaDeg = preferredThetaDeg;
        this.preferredZetaDeg = preferredZetaDeg;
    }

    private MultivariateFunction createFunction() {
        return point -> {

            double theta = point[0];
            double zeta = point[1];
            double time = max(MIN_FLIGHT_TICKS, point[2]);
            double thetaRad = toRadians(theta);
            double zetaRad = toRadians(zeta);
            Vec3 pivotPoint = mountPos;
            Vec3 shipyardFrontOfBarrel = mountPos.add(cos(zetaRad+PI/2)*cos(thetaRad)*l, sin(thetaRad)*l, sin(zetaRad+PI/2)*cos(thetaRad)*l); //+90 degrees cuz used a space offset by that in my math and was too lazy to rewrite it all

            Vec3 offset = getCannonMountOffset(level, getBlockPosFromVec3(mountPos));
            pivotPoint = pivotPoint.add(offset);
            shipyardFrontOfBarrel = shipyardFrontOfBarrel.add(offset);

            Vec3 frontOfBarrel = getVec3FromVector(ship.logicalPose().transformPosition(getVector3dFromVec3(shipyardFrontOfBarrel)));
            pivotPoint = getVec3FromVector(ship.logicalPose().transformPosition(getVector3dFromVec3(pivotPoint)));

            Vector3f pivotVector = frontOfBarrel.subtract(pivotPoint).toVector3f();
            pivotVector = pivotVector.normalize();
            double pitch = asin(pivotVector.y);
            double yaw = Math.atan2(pivotVector.z, pivotVector.x);
            if (yaw < 0) {
                yaw += 2 * Math.PI;
            }
            thetaRad = Double.isNaN(pitch) ? 0 : pitch;
            zetaRad = Double.isNaN(yaw) ? 0 : yaw;

            Vec3 projectilePos = frontOfBarrel.add(displacement(thetaRad, zetaRad, time));
            return projectilePos.distanceToSqr(targetPos);
        };
    }

    private Vec3 displacement(double thetaRad, double zetaRad, double time) {
        double vx = u * cos(thetaRad) * cos(zetaRad);
        double vy = u * sin(thetaRad);
        double vz = u * cos(thetaRad) * sin(zetaRad);

        if (abs(drag) < 1.0e-9) {
            return new Vec3(
                    vx * time,
                    vy * time - 0.5 * g * time * time,
                    vz * time
            );
        }

        double decay = 1.0 - exp(-drag * time);
        return new Vec3(
                vx * decay / drag,
                ((drag * vy + g) * decay) / (drag * drag) - g * time / drag,
                vz * decay / drag
        );
    }

    private double initialFlightGuessTicks() {
        Vec3 front = getVec3FromVector(ship.logicalPose().transformPosition(getVector3dFromVec3(mountPos)));
        double distance = max(1.0, front.distanceTo(targetPos));
        return min(MAX_FLIGHT_TICKS, max(MIN_FLIGHT_TICKS, distance / max(1.0e-6, u)));
    }

    private double[] initialAngleGuess() {
        Vec3 localTarget = getVec3FromVector(ship.logicalPose().transformPositionInverse(getVector3dFromVec3(targetPos)));
        Vec3 localDiff = localTarget.subtract(mountPos);
        double horizontal = hypot(localDiff.x, localDiff.z);
        double theta = toDegrees(atan2(localDiff.y, max(1.0e-6, horizontal)));
        double zeta = toDegrees(atan2(localDiff.z, localDiff.x)) + 270.0;
        zeta %= 360.0;
        if (zeta < 0.0) {
            zeta += 360.0;
        }
        return new double[]{theta, zeta};
    }

    private double targetHorizontalDistanceFromMuzzle() {
        Vec3 front = getVec3FromVector(ship.logicalPose().transformPosition(getVector3dFromVec3(mountPos)));
        return hypot(targetPos.x - front.x, targetPos.z - front.z);
    }

    RandomVectorGenerator randomVectorGenerator = new RandomVectorGenerator() {
//        private final Random random = new Random();
        private final Random random = new Random(0L);
        @Override
        public double[] nextVector() {
            // Define your bounds:
            double thetaLower = -90;
            double thetaUpper = 90;
            double zetaLower = 0;
            double zetaUpper = 360;
            double timeLower = MIN_FLIGHT_TICKS;
            double timeUpper = MAX_FLIGHT_TICKS;
            double theta = thetaLower + random.nextDouble() * (thetaUpper - thetaLower);
            double zeta = zetaLower + random.nextDouble() * (zetaUpper - zetaLower);
            double time = timeLower + random.nextDouble() * (timeUpper - timeLower);
            return new double[]{theta, zeta, time};
        }
    };

    public List<List<Double>> solveThetaZeta() {
        int numStarts = 8; // Number of starting points for multi-start optimization.
        MultiStartMultivariateOptimizer optimizer = new MultiStartMultivariateOptimizer(
                new BOBYQAOptimizer(7), numStarts, randomVectorGenerator
        );

        // Define search bounds (theta in [-90, 90], zeta in [0, 360], time in ticks).
        double[] lowerBounds = {-90, 0, MIN_FLIGHT_TICKS};
        double[] upperBounds = {90, 360, MAX_FLIGHT_TICKS};
        try {
            double[] angleGuess = initialAngleGuess();
            optimizer.optimize(
                    new MaxEval(1200),
                    new ObjectiveFunction(createFunction()),
                    GoalType.MINIMIZE,
                    new InitialGuess(new double[]{angleGuess[0], angleGuess[1], initialFlightGuessTicks()}),
                    new SimpleBounds(lowerBounds, upperBounds)
            );
        } catch (Exception e) {
            LOGGER.debug("Sable cannon targeting solve failed", e);
        }

        PointValuePair[] optima = optimizer.getOptima();
        List<List<Double>> results = new ArrayList<>();
        Set<String> uniqueSolutions = new HashSet<>();
        double horizontalToTarget = targetHorizontalDistanceFromMuzzle();
        for (PointValuePair opt : optima) {
            if (opt == null) continue;
            double error = opt.getValue();
            if (error < MAX_MISS_DISTANCE_BLOCKS * MAX_MISS_DISTANCE_BLOCKS) {
                double[] point = opt.getPoint();
                double theta = point[0];
                double zeta = point[1];
                if (abs(theta) >= NEAR_VERTICAL_PITCH_DEG && horizontalToTarget > NEAR_VERTICAL_HORIZONTAL_BLOCKS) {
                    continue;
                }
                String key = String.format("%d_%d", (int) Math.floor(theta), (int) Math.floor(zeta));
                if (!uniqueSolutions.contains(key)) {
                    uniqueSolutions.add(key);
                    List<Double> pair = new ArrayList<>();
                    pair.add(theta);
                    pair.add(zeta);
                    results.add(pair);
                }
            }
        }
//        results.sort(Comparator.comparingDouble(pair -> abs(pair.get(0))));
        results.sort(this::compareSolutionsByContinuity);
        return results;
    }

    private int compareSolutionsByContinuity(List<Double> a, List<Double> b) {
        return Double.compare(solutionScore(a), solutionScore(b));
    }

    private double solutionScore(List<Double> pair) {
        double theta = pair.get(0);
        double zeta = pair.get(1);

        if (preferredThetaDeg == null || preferredZetaDeg == null) {
            return abs(theta);
        }

        double pitchDelta = theta - preferredThetaDeg;
        double yawDelta = shortestAngleDelta(preferredZetaDeg, zeta);
        return pitchDelta * pitchDelta + yawDelta * yawDelta;
    }

    private static double shortestAngleDelta(double fromDeg, double toDeg) {
        return ((toDeg - fromDeg + 540.0) % 360.0) - 180.0;
    }
}
