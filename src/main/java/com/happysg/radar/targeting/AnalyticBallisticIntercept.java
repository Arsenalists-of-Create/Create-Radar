package com.happysg.radar.targeting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class AnalyticBallisticIntercept {
    private AnalyticBallisticIntercept() {}

    public static List<Vec3> directions(TargetingSnapshot snapshot, ProjectileModel model, double accelerationTrust) {
        if (snapshot == null || model == null || model.usesCustomDynamics() || model.drag() != 0.0 || model.muzzleSpeed() <= 0.0 || !Double.isFinite(model.gravity()) || snapshot.maxFlightTicks() <= 0) {
            return List.of();
        }

        Vec3 pivot = snapshot.hasCoupledMuzzle() ? snapshot.launchPivotPosition() : snapshot.muzzlePosition();
        double muzzleOffset = snapshot.hasCoupledMuzzle() ? snapshot.muzzleForwardOffset() : 0.0;
        if (pivot == null || !Double.isFinite(muzzleOffset) || muzzleOffset < 0.0) {
            return List.of();
        }

        Vec3 gravity = new Vec3(0.0, model.gravity(), 0.0);
        AnalyticTargetMotion targetMotion = AnalyticTargetMotion.from(snapshot, accelerationTrust);

        if (targetMotion == null) {
            return List.of();
        }

        Vec3 targetAcceleration = targetMotion.acceleration();
        Vec3 relativeVelocity = targetMotion.velocity().subtract(snapshot.inheritedVelocity());

        if (!model.cbcPhysics()) {
            relativeVelocity = relativeVelocity.add(gravity.scale(0.5));
        }

        List<Vec3> directions = new ArrayList<>(4);
        AABB box = snapshot.targetAabb();

        if (box == null || snapshot.rigidBodyTargetMotion() != null) {
            addPiecewiseIntercepts(
                    directions,
                    targetMotion.position(),
                    pivot,
                    relativeVelocity,
                    targetAcceleration,
                    gravity,
                    muzzleOffset,
                    model.muzzleSpeed(),
                    snapshot.maxFlightTicks(),
                    targetMotion.accelerationRemainingTicks()
            );
        } else {
            Vec3 center = box.getCenter();

            addPiecewiseIntercepts(
                    directions,
                    center,
                    pivot,
                    relativeVelocity,
                    targetAcceleration,
                    gravity,
                    muzzleOffset,
                    model.muzzleSpeed(),
                    snapshot.maxFlightTicks(),
                    targetMotion.accelerationRemainingTicks()
            );

            Vec3 upper = new Vec3(center.x, box.minY + 0.75 * box.getYsize(), center.z);

            addPiecewiseIntercepts(
                    directions,
                    upper,
                    pivot,
                    relativeVelocity,
                    targetAcceleration,
                    gravity,
                    muzzleOffset,
                    model.muzzleSpeed(),
                    snapshot.maxFlightTicks(),
                    targetMotion.accelerationRemainingTicks()
            );
        }

        return directions;
    }

    private static void addPiecewiseIntercepts(
            List<Vec3> solutions,
            Vec3 target,
            Vec3 pivot,
            Vec3 relativeVelocity,
            Vec3 targetAcceleration,
            Vec3 gravity,
            double muzzleOffset,
            double speed,
            int maxTicks,
            double remainingAccelerationTicks) {

        double cutoff = Math.max(0.0, Math.min(maxTicks, remainingAccelerationTicks));

        if (targetAcceleration.lengthSqr() < 1.0e-18) {
            addIntercepts(solutions, target, pivot, relativeVelocity, gravity.scale(-0.5), muzzleOffset, speed, 1.0e-6, maxTicks);
            return;
        }

        if (cutoff > 1.0e-6) {
            addIntercepts(
                    solutions,
                    target,
                    pivot,
                    relativeVelocity,
                    targetAcceleration.subtract(gravity).scale(0.5),
                    muzzleOffset,
                    speed,
                    1.0e-6,
                    cutoff);
        }

        if (maxTicks > cutoff) {
            Vec3 shiftedTarget = target.subtract(targetAcceleration.scale(0.5 * cutoff * cutoff));
            Vec3 shiftedVelocity = relativeVelocity.add(targetAcceleration.scale(cutoff));

            addIntercepts(
                    solutions,
                    shiftedTarget,
                    pivot,
                    shiftedVelocity,
                    gravity.scale(-0.5),
                    muzzleOffset,
                    speed,
                    Math.max(1.0e-6, cutoff),
                    maxTicks);
        }
    }

    private static void addIntercepts(
            List<Vec3> solutions,
            Vec3 target,
            Vec3 pivot,
            Vec3 velocity,
            Vec3 quadratic,
            double offset,
            double speed,
            double minTicks,
            double maxTicks
    ) {
        Vec3 relative = target.subtract(pivot);

        double[] polynomial = {
                relative.dot(relative) - offset * offset,
                2.0 * (relative.dot(velocity) - offset * speed),
                velocity.dot(velocity) + 2.0 * relative.dot(quadratic) - speed * speed,
                2.0 * velocity.dot(quadratic),
                quadratic.dot(quadratic)
        };

        for (double time : PolynomialRoots.inRange(polynomial, minTicks, maxTicks)) {
            double pathLength = offset + speed * time;

            if (pathLength <= 1.0e-8) continue;
            Vec3 direction = relative.add(velocity.scale(time)).add(quadratic.scale(time * time)).scale(1.0 / pathLength);

            if (!Double.isFinite(direction.x) || !Double.isFinite(direction.y) || !Double.isFinite(direction.z) || direction.lengthSqr() < 1.0e-12) {
                continue;
            }

            direction = direction.normalize();
            boolean duplicate = false;

            for (Vec3 prior : solutions) {
                if (prior.distanceToSqr(direction) < 1.0e-12) {
                    duplicate = true;
                    break;
                }
            }

            if (!duplicate) solutions.add(direction);
        }
    }
}