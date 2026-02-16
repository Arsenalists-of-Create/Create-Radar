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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4dc;
import org.joml.Vector3f;
import org.valkyrienskies.core.api.ships.Ship;

import java.util.*;

import static com.happysg.radar.compat.cbc.CannonUtil.getCannonMountOffset;
import static com.happysg.radar.compat.vs2.VS2Utils.*;
import static java.lang.Math.*;

public class VS2TargetingSolver {
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
    Matrix4dc shipToWorld;
    Matrix4dc worldToShip;

    private static final double TOLERANCE = 1e-3;


    // Constructor to set up the known values
    public VS2TargetingSolver(Level level, double u, double drag, double g, double barrelLength, Vec3 mountPos, Vec3 targetPos, double initialTheta, double initialZeta, double initialPsi, Ship ship ) {
        this.level = level;
        this.u = u;
        this.drag = drag;
        this.g = abs(g);
        this.initialTheta = initialTheta;
        this.initialZeta = initialZeta;
        this.initialPsi = initialPsi;
        this.shipToWorld = ship.getShipToWorld();
        this.worldToShip = ship.getWorldToShip();
        this.targetPos = targetPos;
        this.mountPos = mountPos; // shipyard coord
        this.l = barrelLength;


    }

    private MultivariateFunction createFunction() { //TODO vypočítat čas z ZED a pak použít na vypočet y a x
        return point -> {

            double theta = point[0];
            double zeta = point[1];
            double thetaRad = toRadians(theta);
            double zetaRad = toRadians(zeta);
            Vec3 pivotPoint = mountPos;
            Vec3 shipyardFrontOfBarrel = mountPos.add(cos(zetaRad+PI/2)*cos(thetaRad)*l, sin(thetaRad)*l, sin(zetaRad+PI/2)*cos(thetaRad)*l); //+90 degrees cuz used a space offset by that in my math and was too lazy to rewrite it all

            Vec3 offset = getCannonMountOffset(level, getBlockPosFromVec3(mountPos));
            pivotPoint.add(offset);
            shipyardFrontOfBarrel.add(offset);

            Vec3 frontOfBarrel = getVec3FromVector(shipToWorld.transformPosition(getVector3dFromVec3(shipyardFrontOfBarrel)));
            pivotPoint = getVec3FromVector(shipToWorld.transformPosition(getVector3dFromVec3(pivotPoint)));

            Vec3 diffVec = targetPos.subtract(frontOfBarrel);
            double dZ = diffVec.z;
            double dY = diffVec.y+1; //kinda band-aid
            double dX = diffVec.x;

            Vector3f pivotVector = frontOfBarrel.subtract(pivotPoint).toVector3f();
            pivotVector = pivotVector.normalize();
            double pitch = asin(pivotVector.y);
            double yaw = Math.atan2(pivotVector.z, pivotVector.x);
            if (yaw < 0) {
                yaw += 2 * Math.PI;
            }
            thetaRad = Double.isNaN(pitch) ? 0 : pitch;
            zetaRad = Double.isNaN(yaw) ? 0 : yaw;

            double log = 1-(drag*dZ)/(u*cos(thetaRad)*sin(zetaRad));
            if(log <= 0) return Double.POSITIVE_INFINITY;
            double time = log(log)/-drag;
            if(time <= 0) return Double.POSITIVE_INFINITY;
            double dragDecay = (1-exp(-drag*time));
            double newX = u*cos(thetaRad)*cos(zetaRad)*dragDecay/drag;

            double newY = (drag*u*sin(thetaRad)+g)*dragDecay/(drag*drag) - g*time/drag;
            return abs(dY-newY)+abs(dX-newX);
        };
    }
    RandomVectorGenerator randomVectorGenerator = new RandomVectorGenerator() {
        private final Random random = new Random();
        @Override
        public double[] nextVector() {
            // Define your bounds:
            double thetaLower = -90;
            double thetaUpper = 90;
            double zetaLower = 0;
            double zetaUpper = 360;
            double theta = thetaLower + random.nextDouble() * (thetaUpper - thetaLower);
            double zeta = zetaLower + random.nextDouble() * (zetaUpper - zetaLower);
            return new double[]{theta, zeta};
        }
    };

    public List<List<Double>> solveThetaZeta() {
        int numStarts = 2; // Number of starting points for multi-start optimization.
        MultiStartMultivariateOptimizer optimizer = new MultiStartMultivariateOptimizer(
                new BOBYQAOptimizer(5), numStarts, randomVectorGenerator
        );

        // Define search bounds (theta in [-90, 90] and zeta in [0, 360] degrees).
        double[] lowerBounds = {-90, 0};
        double[] upperBounds = {90, 360};
        try {
            optimizer.optimize(
                    new MaxEval(1000),
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
                    List<Double> pair = new ArrayList<>();
                    pair.add(theta);
                    pair.add(zeta);
                    results.add(pair);
                }
            }
        }
        return results;
    }
}
