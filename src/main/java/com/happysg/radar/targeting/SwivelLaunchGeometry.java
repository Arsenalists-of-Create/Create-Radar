package com.happysg.radar.targeting;

import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

public record SwivelLaunchGeometry(Vec3 pivot, Vec3 axis, Vec3 referenceDirection, @Nullable Vec3 pitchPivot, @Nullable Vec3 pitchAxis) {
    public SwivelLaunchGeometry(Vec3 pivot, Vec3 axis, Vec3 referenceDirection) {
        this(pivot, axis, referenceDirection, null, null);
    }

    public SwivelLaunchGeometry {
        axis = axis.normalize();
        referenceDirection = referenceDirection.normalize();

        if (pitchAxis != null) {
            pitchAxis = pitchAxis.normalize();
        }

        if ((pitchPivot == null) != (pitchAxis == null)) {
            throw new IllegalArgumentException("Pitch pivot and axis must be supplied together");
        }
    }


    public static SwivelLaunchGeometry nested(
            Vec3 yawPivot,
            Vec3 yawAxis,
            Vec3 pitchPivot,
            Vec3 pitchAxis,
            Vec3 referenceDirection
    ) {
        return new SwivelLaunchGeometry(
                yawPivot,
                yawAxis,
                referenceDirection,
                pitchPivot,
                pitchAxis
        );
    }

    public Vec3 rotatePoint(Vec3 point, Vec3 direction) {
        if (point == null || !finite(direction) || direction.lengthSqr() < 1.0e-12) {
            return point;
        }

        Vec3 wanted = direction.normalize();
        double parentDelta = angleAboutAxis(referenceDirection, wanted, axis);
        Vec3 moved = rotateAround(point, pivot, axis, parentDelta);


        if (pitchPivot == null) {
            return moved;
        }

        Vec3 movedPitchPivot = rotateAround(pitchPivot, pivot, axis, parentDelta);
        Vec3 movedPitchAxis = rotateVector(pitchAxis, axis, parentDelta);
        Vec3 yawedForward = rotateVector(referenceDirection, axis, parentDelta);
        double pitchDelta = angleAboutAxis(yawedForward, wanted, movedPitchAxis);
        return rotateAround(moved, movedPitchPivot, movedPitchAxis, pitchDelta);
    }

    private static double angleAboutAxis(Vec3 from, Vec3 to, Vec3 axis) {
        Vec3 projectedFrom = from.subtract(axis.scale(from.dot(axis)));
        Vec3 projectedTo = to.subtract(axis.scale(to.dot(axis)));

        if (projectedFrom.lengthSqr() < 1.0e-12 || projectedTo.lengthSqr() < 1.0e-12) {
            return 0.0;
        }

        Vec3 a = projectedFrom.normalize();
        Vec3 b = projectedTo.normalize();

        return Math.atan2(axis.dot(a.cross(b)), a.dot(b));
    }

    private static Vec3 rotateAround(Vec3 point, Vec3 pivot, Vec3 axis, double angle) {
        return pivot.add(rotateVector(point.subtract(pivot), axis, angle));
    }

    private static Vec3 rotateVector(Vec3 vector, Vec3 axis, double angle) {
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);

        return vector.scale(cosine).add(axis.cross(vector).scale(sine)).add(axis.scale(axis.dot(vector) * (1.0 - cosine)));
    }

    private static boolean finite(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
}