package com.happysg.radar.block.arad.jammer;

import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shared world-space orientation math for the directional jammer visual. */
public final class JammerOrientation {
    private static final float PARALLEL_EPSILON = 1.0e-6f;

    private JammerOrientation() {
    }

    public static Quaternionf rotation(Direction mountFacing, float yawDegrees,
                                       float pitchDegrees) {
        Vector3f forward = forward(yawDegrees, pitchDegrees);
        Vector3f preferredUp = mountFacing.getAxis().isVertical()
                ? step(mountFacing) : new Vector3f(0.0f, 1.0f, 0.0f);
        Vector3f up = perpendicularUp(preferredUp, forward, mountFacing);
        Vector3f right = new Vector3f(forward).cross(up).normalize();
        Vector3f back = new Vector3f(forward).negate();

        Matrix3f basis = new Matrix3f();
        basis.setColumn(0, right);
        basis.setColumn(1, up);
        basis.setColumn(2, back);
        return new Quaternionf().setFromNormalized(basis).normalize();
    }

    public static Vector3f forward(float yawDegrees, float pitchDegrees) {
        float yaw = (float) Math.toRadians(yawDegrees);
        float pitch = (float) Math.toRadians(pitchDegrees);
        float horizontal = (float) Math.cos(pitch);
        return new Vector3f(
                horizontal * (float) Math.cos(yaw),
                (float) Math.sin(pitch),
                horizontal * (float) Math.sin(yaw)).normalize();
    }

    private static Vector3f perpendicularUp(Vector3f preferredUp,
                                            Vector3f forward,
                                            Direction mountFacing) {
        Vector3f projected = projectPerpendicular(preferredUp, forward);
        if (projected.lengthSquared() > PARALLEL_EPSILON) {
            return projected.normalize();
        }

        Vector3f[] fallbacks = {
                step(mountFacing),
                new Vector3f(0.0f, 0.0f, -1.0f),
                new Vector3f(1.0f, 0.0f, 0.0f)
        };
        for (Vector3f fallback : fallbacks) {
            projected = projectPerpendicular(fallback, forward);
            if (projected.lengthSquared() > PARALLEL_EPSILON) {
                return projected.normalize();
            }
        }
        return new Vector3f(0.0f, 1.0f, 0.0f);
    }

    private static Vector3f projectPerpendicular(Vector3f vector,
                                                 Vector3f normal) {
        return new Vector3f(vector)
                .sub(new Vector3f(normal).mul(vector.dot(normal)));
    }

    private static Vector3f step(Direction direction) {
        return new Vector3f(direction.getStepX(), direction.getStepY(),
                direction.getStepZ());
    }
}
