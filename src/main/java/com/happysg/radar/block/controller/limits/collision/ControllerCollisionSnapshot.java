package com.happysg.radar.block.controller.limits.collision;

import com.happysg.radar.block.controller.kinetic.CannonAxis;
import com.happysg.radar.block.controller.kinetic.KineticAngleMath;
import com.happysg.radar.block.controller.limits.ControllerMovementLimits;

import java.util.List;

/**
 * Immutable, view-space collision data sent to one open controller screen.
 * Visible depth starts at zero and extends forward to {@link #depth()}.
 */
public record ControllerCollisionSnapshot(
        Status status,
        CannonAxis axis,
        float halfSpan,
        float depth,
        float dialCenterU,
        float dialCenterV,
        float dialZeroDegrees,
        double supportedMinDegrees,
        double supportedMaxDegrees,
        double minDegrees,
        double maxDegrees,
        boolean spanClipped,
        boolean scanTruncated,
        List<OrientedBox> boxes
) {
    public static final float DEFAULT_HALF_SPAN = 5.0f;
    public static final float DEFAULT_DEPTH = 5.0f;
    public static final int MAX_PACKET_BOXES = 16_384;

    public ControllerCollisionSnapshot {
        boxes = List.copyOf(boxes);
        if (!Float.isFinite(dialCenterU)
                || !Float.isFinite(dialCenterV)
                || !Float.isFinite(dialZeroDegrees)
                || ControllerMovementLimits.validated(axis,
                supportedMinDegrees, supportedMaxDegrees).isEmpty()
                || ControllerMovementLimits.validated(
                axis, minDegrees, maxDegrees).isEmpty()
                || minDegrees < supportedMinDegrees
                || maxDegrees > supportedMaxDegrees) {
            throw new IllegalArgumentException(
                    "Invalid controller dial limits");
        }
        dialZeroDegrees = (float) KineticAngleMath.wrap180(
                dialZeroDegrees);
        if (boxes.size() > MAX_PACKET_BOXES) {
            throw new IllegalArgumentException("Too many controller collision boxes");
        }
    }

    public static ControllerCollisionSnapshot error(Status status,
                                                    CannonAxis axis) {
        ControllerMovementLimits limits =
                ControllerMovementLimits.defaults(axis);
        return new ControllerCollisionSnapshot(status, axis,
                DEFAULT_HALF_SPAN, DEFAULT_DEPTH,
                0.0f, 0.0f, 0.0f,
                limits.minDegrees(), limits.maxDegrees(),
                limits.minDegrees(), limits.maxDegrees(),
                false, false, List.of());
    }

    public enum Status {
        OK,
        NO_CONTROLLER,
        NO_MOUNT,
        INVALID_REQUEST,
        RATE_LIMITED
    }

    public enum Category {
        ENVIRONMENT,
        CANNON
    }

    /**
     * An oriented collision box in controller-view coordinates. The three
     * axis vectors run from the center to the positive face (half extents).
     */
    public record OrientedBox(
            float centerU, float centerV, float centerDepth,
            float axisXU, float axisXV, float axisXDepth,
            float axisYU, float axisYV, float axisYDepth,
            float axisZU, float axisZV, float axisZDepth,
            Category category
    ) {
        public boolean isFinite() {
            return Float.isFinite(centerU) && Float.isFinite(centerV)
                    && Float.isFinite(centerDepth)
                    && Float.isFinite(axisXU) && Float.isFinite(axisXV)
                    && Float.isFinite(axisXDepth)
                    && Float.isFinite(axisYU) && Float.isFinite(axisYV)
                    && Float.isFinite(axisYDepth)
                    && Float.isFinite(axisZU) && Float.isFinite(axisZV)
                    && Float.isFinite(axisZDepth);
        }

        public float radiusU() {
            return Math.abs(axisXU) + Math.abs(axisYU) + Math.abs(axisZU);
        }

        public float radiusV() {
            return Math.abs(axisXV) + Math.abs(axisYV) + Math.abs(axisZV);
        }

        public float radiusDepth() {
            return Math.abs(axisXDepth) + Math.abs(axisYDepth)
                    + Math.abs(axisZDepth);
        }

        public boolean intersects(float requestedHalfSpan,
                                  float requestedDepth) {
            return centerU + radiusU() >= -requestedHalfSpan
                    && centerU - radiusU() <= requestedHalfSpan
                    && centerV + radiusV() >= -requestedHalfSpan
                    && centerV - radiusV() <= requestedHalfSpan
                    && centerDepth + radiusDepth() >= 0.0f
                    && centerDepth - radiusDepth() <= requestedDepth;
        }
    }
}
