package com.happysg.radar.block.controller.limits.collision;

import net.minecraft.world.phys.Vec3;

/** Orthonormal world-to-screen frame used by controller collision snapshots. */
public record ControllerCollisionViewFrame(
        Vec3 origin, Vec3 right, Vec3 up, Vec3 depth
) {
    private static final double EPSILON = 1.0e-10;

    public ControllerCollisionViewFrame {
        origin = finite(origin, Vec3.ZERO);
        right = normalized(right, new Vec3(1, 0, 0));
        up = normalized(up, new Vec3(0, 1, 0));
        depth = normalized(depth, new Vec3(0, 0, 1));
    }

    public static ControllerCollisionViewFrame pitch(
            Vec3 origin, Vec3 parentUp, Vec3 cannonForward,
            Vec3 playerLook
    ) {
        Vec3 vertical = normalized(parentUp, new Vec3(0, 1, 0));
        Vec3 forward = reject(cannonForward, vertical);
        if (forward.lengthSqr() < EPSILON) {
            forward = reject(playerLook, vertical);
        }
        forward = normalized(forward, new Vec3(0, 0, 1));

        Vec3 side = vertical.cross(forward);
        side = normalized(side, new Vec3(1, 0, 0));
        Vec3 lookSide = reject(playerLook, vertical);
        Vec3 viewDepth = side;
        if (lookSide.lengthSqr() >= EPSILON && viewDepth.dot(lookSide) < 0) {
            viewDepth = viewDepth.scale(-1);
        }

        Vec3 screenRight = viewDepth.cross(vertical);
        return new ControllerCollisionViewFrame(origin,
                normalized(screenRight, forward), vertical, viewDepth);
    }

    public static ControllerCollisionViewFrame yaw(
            Vec3 origin, Vec3 parentUp, Vec3 cannonForward
    ) {
        Vec3 vertical = normalized(parentUp, new Vec3(0, 1, 0));
        Vec3 screenUp = reject(cannonForward, vertical);
        screenUp = normalized(screenUp, new Vec3(0, 0, 1));
        Vec3 viewDepth = vertical.scale(-1);
        Vec3 screenRight = viewDepth.cross(screenUp);
        return new ControllerCollisionViewFrame(origin,
                normalized(screenRight, new Vec3(1, 0, 0)),
                screenUp, viewDepth);
    }

    public Vec3 pointToView(Vec3 point) {
        return vectorToView(point.subtract(origin));
    }

    public Vec3 vectorToView(Vec3 vector) {
        return new Vec3(vector.dot(right), vector.dot(up),
                vector.dot(depth));
    }

    public Vec3 viewToWorld(double u, double v, double d) {
        return origin.add(right.scale(u)).add(up.scale(v)).add(depth.scale(d));
    }

    /**
     * Moves the origin to the viewer-facing side of a depth range while
     * keeping the current origin at the range's center.
     */
    public ControllerCollisionViewFrame withCenteredDepthRange(
            double depthRange
    ) {
        if (!Double.isFinite(depthRange) || depthRange <= 0.0) {
            throw new IllegalArgumentException("Depth range must be positive");
        }
        return new ControllerCollisionViewFrame(
                origin.subtract(depth.scale(depthRange * 0.5)),
                right, up, depth);
    }

    private static Vec3 reject(Vec3 vector, Vec3 normal) {
        Vec3 safe = finite(vector, Vec3.ZERO);
        return safe.subtract(normal.scale(safe.dot(normal)));
    }

    private static Vec3 normalized(Vec3 vector, Vec3 fallback) {
        Vec3 safe = finite(vector, fallback);
        return safe.lengthSqr() < EPSILON ? fallback : safe.normalize();
    }

    private static Vec3 finite(Vec3 vector, Vec3 fallback) {
        return vector != null && Double.isFinite(vector.x)
                && Double.isFinite(vector.y) && Double.isFinite(vector.z)
                ? vector : fallback;
    }
}
