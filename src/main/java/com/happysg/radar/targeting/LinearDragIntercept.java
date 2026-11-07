
package com.happysg.radar.targeting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class LinearDragIntercept {
    private static final Vec3 UNIT_X = new Vec3(1.0, 0.0, 0.0);
    private static final Vec3 ZERO = Vec3.ZERO;

    private LinearDragIntercept() {}

    public static List<Vec3> directions(
            TargetingSnapshot snapshot,
            ProjectileModel model,
            double accelerationTrust) {

        if (snapshot == null
                || !LinearDragMath.supports(model)
                || model.drag() <= 0.0
                || snapshot.swivelLaunchGeometry() != null
                || snapshot.launchAngularVelocity().lengthSqr() > 1.0e-18
                || snapshot.maxFlightTicks() <= 0) {
            return List.of();
        }

        Vec3 pivot = snapshot.steeringOrigin();
        double muzzleOffset = snapshot.hasCoupledMuzzle() ? snapshot.muzzleForwardOffset() : 0.0;
        AnalyticTargetMotion targetMotion = AnalyticTargetMotion.from(snapshot, accelerationTrust);

        if (targetMotion == null) {
            return List.of();
        }

        List<Vec3> results = new ArrayList<>(4);
        AABB box = snapshot.targetAabb();

        if (box == null || snapshot.rigidBodyTargetMotion() != null) {
            solvePoint(results, snapshot, model, pivot, muzzleOffset, targetMotion.position(), targetMotion);
        } else {
            Vec3 center = box.getCenter();
            Vec3 upper = new Vec3(center.x, box.minY + box.getYsize() * 0.75, center.z);

            solvePoint(results, snapshot, model, pivot, muzzleOffset, center, targetMotion);
            solvePoint(results, snapshot, model, pivot, muzzleOffset, upper, targetMotion);
        }

        return results;
    }

    private static void solvePoint(
            List<Vec3> results,
            TargetingSnapshot snapshot,
            ProjectileModel model,
            Vec3 pivot,
            double muzzleOffset,
            Vec3 target,
            AnalyticTargetMotion targetMotion) {
        int maxTicks = snapshot.maxFlightTicks();
        int samples = Math.min(96, Math.max(32, (int) Math.ceil(Math.sqrt(maxTicks) * 4.0)));

        double previousTime = 1.0e-6;
        double previousResidual = sample(snapshot, model, pivot, muzzleOffset, target, targetMotion, previousTime).residual();

        for (int i = 1; i <= samples; i++) {
            if ((i & 15) == 0 && Thread.currentThread().isInterrupted()) {
                return;
            }

            double fraction = (double) i / samples;
            double time = 1.0e-6 + (maxTicks - 1.0e-6) * fraction * fraction;

            Sample current = sample(snapshot, model, pivot, muzzleOffset, target, targetMotion, time);
            double residual = current.residual();

            if (!Double.isFinite(previousResidual) || !Double.isFinite(residual)) {
                previousTime = time;
                previousResidual = residual;
                continue;
            }

            boolean crossed = (previousResidual > 0.0 && residual < 0.0) || (previousResidual < 0.0 && residual > 0.0);

            if (crossed || residual == 0.0) {
                double root = time;

                if (crossed) {
                    double low = previousTime;
                    double high = time;
                    double lowResidual = previousResidual;

                    for (int iteration = 0; iteration < 36; iteration++) {
                        double mid = (low + high) * 0.5;
                        double midResidual = sample(snapshot, model, pivot, muzzleOffset, target, targetMotion, mid).residual();

                        if (!Double.isFinite(midResidual)) {
                            break;
                        }

                        if (Math.abs(midResidual) < 1.0e-8) {
                            low = high = mid;
                            break;
                        }

                        if ((lowResidual > 0.0) == (midResidual > 0.0)) {
                            low = mid;
                            lowResidual = midResidual;
                        } else {
                            high = mid;
                        }
                    }

                    root = (low + high) * 0.5;
                }

                Sample hit = sample(snapshot, model, pivot, muzzleOffset, target, targetMotion, root);

                if (hit.radius() > 1.0e-8 && Double.isFinite(hit.residual()) && Math.abs(hit.residual()) <= 1.0e-4) {
                    Vec3 direction = hit.relative().normalize();
                    if (finite(direction)) {
                        boolean duplicate = false;

                        for (Vec3 existing : results) {
                            if (existing.distanceToSqr(direction) < 1.0e-10) {
                                duplicate = true;
                                break;
                            }
                        }

                        if (!duplicate) {
                            results.add(direction);
                        }
                    }
                }
            }

            previousTime = time;
            previousResidual = residual;
        }
    }

    private static Sample sample(
            TargetingSnapshot snapshot,
            ProjectileModel model,
            Vec3 pivot,
            double muzzleOffset,
            Vec3 target,
            AnalyticTargetMotion targetMotion,
            double time) {
        Vec3 basis = LinearDragMath.positionAtTime(ZERO, UNIT_X, model, time);

        double velocityWeight = basis.x;
        double gravityDisplacement = basis.y;

        Vec3 relative = target.subtract(pivot)
                .add(targetMotion.velocity().scale(time))
                .add(targetMotion.acceleration().scale(targetMotion.accelerationFactor(time)))
                .subtract(snapshot.inheritedVelocity().scale(velocityWeight))
                .add(0.0, -gravityDisplacement, 0.0);

        double radius = muzzleOffset + model.muzzleSpeed() * velocityWeight;

        return new Sample(relative, radius);
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private record Sample(Vec3 relative, double radius) {
        double residual() {
            return relative.length() - radius;
        }
    }
}
