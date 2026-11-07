package com.happysg.radar.targeting;

import net.minecraft.world.phys.Vec3;

public final class LinearDragMath {
    private LinearDragMath() {}

    public static boolean supports(ProjectileModel model) {
        return model != null
                && !model.usesCustomDynamics()
                && !model.quadraticDrag()
                && Double.isFinite(model.drag())
                && model.drag() >= 0.0
                && Double.isFinite(model.gravity())
                && (!model.cbcPhysics() || (Double.isFinite(model.dragDensity())
                && model.dragDensity() >= 0.0));
    }

    public static Vec3 positionAtTick(Vec3 start, Vec3 initialVelocity, ProjectileModel model, int ticks) {
        if (!supports(model)) {
            throw new IllegalArgumentException("Unsupported linear drag model");
        }

        int n = Math.max(0, ticks);
        if (n == 0) {
            return start;
        }

        double drag = model.drag() * (model.cbcPhysics() ? model.dragDensity() : 1.0);
        double k = Math.min(1.0, drag);
        boolean cbc = model.cbcPhysics();

        double positionFactor = cbc ? 1.0 - 0.5 * k : 1.0;
        double gravityOffset = cbc ? 0.5 : 0.0;

        double velocitySum;
        double gravitySum;

        if (k == 0.0) {
            velocitySum = n;
            gravitySum = 0.5 * n * (n - 1.0);
        } else if (k * n < 0.05) {
            velocitySum = n;
            double term = n;

            for (int j = 1; j <= 8; j++) {
                term *= -k * (n - j) / (j + 1.0);
                velocitySum += term;
            }

            gravitySum = 0.5 * n * (n - 1.0);
            double gravityTerm = gravitySum;

            for (int j = 1; j < 8; j++) {
                gravityTerm *= -k * (n - 1.0 - j) / (j + 2.0);
                gravitySum += gravityTerm;
            }
        } else {
            velocitySum = k == 1.0 ? 1.0 : -Math.expm1(n * Math.log1p(-k)) / k;
            gravitySum = (n - velocitySum) / k;
        }

        double velocityWeight = positionFactor * velocitySum;
        double gravityWeight = positionFactor * gravitySum + gravityOffset * n;

        return start.add(initialVelocity.scale(velocityWeight)).add(0.0, model.gravity() * gravityWeight, 0.0);
    }

    public static Vec3 positionAtTime(Vec3 start, Vec3 initialVelocity, ProjectileModel model, double ticks) {
        if (!Double.isFinite(ticks) || ticks < 0.0) {
            throw new IllegalArgumentException("Invalid flight time");
        }

        int wholeTicks = (int) Math.floor(ticks);
        double fraction = ticks - wholeTicks;

        Vec3 before = positionAtTick(start, initialVelocity, model, wholeTicks);
        if (fraction <= 0.0) {
            return before;
        }
        Vec3 after = positionAtTick(start, initialVelocity, model, wholeTicks + 1);

        return before.add(after.subtract(before).scale(fraction));
    }
}
