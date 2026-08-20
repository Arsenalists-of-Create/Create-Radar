package com.happysg.radar.compat.sable;

import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Selects deterministic, cannon-facing occupied points from a Sable hull. */
public final class SubLevelAimResolver {
    private static final double MAX_FACE_INSET = 0.05;

    private SubLevelAimResolver() {
    }

    @Nullable
    public static LocalAim select(List<SubLevelSilhouette.LocalBox> boxes,
                                  Vec3 localOrigin,
                                  int candidateLimit,
                                  Predicate<LocalAim> accept) {
        Objects.requireNonNull(boxes, "boxes");
        Objects.requireNonNull(localOrigin, "localOrigin");
        Objects.requireNonNull(accept, "accept");
        if (boxes.isEmpty() || candidateLimit < 1) {
            return null;
        }

        Vec3 hullCenter = hullCenter(boxes);
        ArrayList<RankedAim> ranked = new ArrayList<>(boxes.size());
        for (SubLevelSilhouette.LocalBox box : boxes) {
            Vec3 point = facingPoint(box, localOrigin);
            Vec3 boxCenter = center(box);
            ranked.add(new RankedAim(
                    new LocalAim(box, point),
                    distanceToLineSqr(boxCenter, localOrigin, hullCenter),
                    boxCenter.distanceToSqr(hullCenter),
                    point.distanceToSqr(localOrigin)));
        }

        ranked.sort(Comparator
                .comparingDouble(RankedAim::sightlineDistanceSquared)
                .thenComparingDouble(RankedAim::centerDistanceSquared)
                .thenComparingDouble(RankedAim::originDistanceSquared)
                .thenComparingDouble(aim -> aim.aim().box().minX())
                .thenComparingDouble(aim -> aim.aim().box().minY())
                .thenComparingDouble(aim -> aim.aim().box().minZ())
                .thenComparingDouble(aim -> aim.aim().box().maxX())
                .thenComparingDouble(aim -> aim.aim().box().maxY())
                .thenComparingDouble(aim -> aim.aim().box().maxZ()));

        int limit = Math.min(candidateLimit, ranked.size());
        for (int index = 0; index < limit; index++) {
            LocalAim candidate = ranked.get(index).aim();
            if (accept.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public static Vec3 toLocal(SubLevelAccess subLevel, Vec3 worldPoint) {
        Vector3d local = subLevel.logicalPose().transformPositionInverse(
                new Vector3d(worldPoint.x, worldPoint.y, worldPoint.z));
        return new Vec3(local.x, local.y, local.z);
    }

    public static Vec3 toWorld(SubLevelAccess subLevel, Vec3 localPoint) {
        Vector3d world = subLevel.logicalPose().transformPosition(
                new Vector3d(localPoint.x, localPoint.y, localPoint.z));
        return new Vec3(world.x, world.y, world.z);
    }

    public static AABB toWorldAabb(SubLevelAccess subLevel,
                                   SubLevelSilhouette.LocalBox box) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int xBit = 0; xBit < 2; xBit++) {
            double x = xBit == 0 ? box.minX() : box.maxX();
            for (int yBit = 0; yBit < 2; yBit++) {
                double y = yBit == 0 ? box.minY() : box.maxY();
                for (int zBit = 0; zBit < 2; zBit++) {
                    double z = zBit == 0 ? box.minZ() : box.maxZ();
                    Vec3 world = toWorld(subLevel, new Vec3(x, y, z));
                    minX = Math.min(minX, world.x);
                    minY = Math.min(minY, world.y);
                    minZ = Math.min(minZ, world.z);
                    maxX = Math.max(maxX, world.x);
                    maxY = Math.max(maxY, world.y);
                    maxZ = Math.max(maxZ, world.z);
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Vec3 facingPoint(SubLevelSilhouette.LocalBox box,
                                    Vec3 origin) {
        double insetX = Math.min(MAX_FACE_INSET,
                (box.maxX() - box.minX()) * 0.25);
        double insetY = Math.min(MAX_FACE_INSET,
                (box.maxY() - box.minY()) * 0.25);
        double insetZ = Math.min(MAX_FACE_INSET,
                (box.maxZ() - box.minZ()) * 0.25);
        return new Vec3(
                clamp(origin.x, box.minX() + insetX, box.maxX() - insetX),
                clamp(origin.y, box.minY() + insetY, box.maxY() - insetY),
                clamp(origin.z, box.minZ() + insetZ, box.maxZ() - insetZ));
    }

    private static Vec3 hullCenter(List<SubLevelSilhouette.LocalBox> boxes) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (SubLevelSilhouette.LocalBox box : boxes) {
            minX = Math.min(minX, box.minX());
            minY = Math.min(minY, box.minY());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxX());
            maxY = Math.max(maxY, box.maxY());
            maxZ = Math.max(maxZ, box.maxZ());
        }
        return new Vec3((minX + maxX) * 0.5,
                (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
    }

    private static Vec3 center(SubLevelSilhouette.LocalBox box) {
        return new Vec3((box.minX() + box.maxX()) * 0.5,
                (box.minY() + box.maxY()) * 0.5,
                (box.minZ() + box.maxZ()) * 0.5);
    }

    private static double distanceToLineSqr(Vec3 point, Vec3 start,
                                            Vec3 end) {
        Vec3 line = end.subtract(start);
        double lengthSquared = line.lengthSqr();
        if (lengthSquared < 1.0E-12) {
            return point.distanceToSqr(end);
        }
        double t = clamp(point.subtract(start).dot(line) / lengthSquared,
                0.0, 1.0);
        return point.distanceToSqr(start.add(line.scale(t)));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record LocalAim(SubLevelSilhouette.LocalBox box, Vec3 point) {
    }

    private record RankedAim(LocalAim aim, double sightlineDistanceSquared,
                             double centerDistanceSquared,
                             double originDistanceSquared) {
    }
}
