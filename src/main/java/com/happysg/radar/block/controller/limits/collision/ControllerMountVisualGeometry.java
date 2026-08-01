package com.happysg.radar.block.controller.limits.collision;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Simplified U-shaped proxy for CBC's renderer-only rotating mount. */
final class ControllerMountVisualGeometry {
    private static final double EPSILON = 1.0e-10;

    private ControllerMountVisualGeometry() {
    }

    static List<ControllerVisualBox> bracket(
            Vec3 pivot, Vec3 upDirection, Vec3 forwardDirection
    ) {
        Vec3 up = normalized(upDirection, new Vec3(0, 1, 0));
        Vec3 forward = forwardDirection.subtract(up.scale(
                forwardDirection.dot(up)));
        Vec3 fallback = Math.abs(up.y) > 0.9
                ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        forward = normalized(forward, fallback);
        Vec3 right = normalized(up.cross(forward), new Vec3(1, 0, 0));
        forward = normalized(right.cross(up), forward);

        ControllerVisualBox base = box(
                pivot.add(up.scale(3.0 / 32.0)),
                right, 10.0 / 16.0,
                up, 3.0 / 32.0,
                forward, 8.0 / 16.0);
        Vec3 armCenter = pivot.add(up.scale(1.0));
        ControllerVisualBox leftArm = box(
                armCenter.add(right.scale(-9.0 / 16.0)),
                right, 1.0 / 16.0,
                up, 13.0 / 16.0,
                forward, 5.0 / 16.0);
        ControllerVisualBox rightArm = box(
                armCenter.add(right.scale(9.0 / 16.0)),
                right, 1.0 / 16.0,
                up, 13.0 / 16.0,
                forward, 5.0 / 16.0);
        return List.of(base, leftArm, rightArm);
    }

    private static ControllerVisualBox box(
            Vec3 center,
            Vec3 xDirection, double halfX,
            Vec3 yDirection, double halfY,
            Vec3 zDirection, double halfZ
    ) {
        return new ControllerVisualBox(center,
                xDirection.scale(halfX), yDirection.scale(halfY),
                zDirection.scale(halfZ));
    }

    private static Vec3 normalized(Vec3 vector, Vec3 fallback) {
        return vector.lengthSqr() <= EPSILON ? fallback : vector.normalize();
    }
}
