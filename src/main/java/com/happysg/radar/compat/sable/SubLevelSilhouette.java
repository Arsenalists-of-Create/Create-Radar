package com.happysg.radar.compat.sable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;


public final class SubLevelSilhouette {

    private static final double EPSILON = 1.0e-8;

    private final List<LocalBox> localBoxes;

    private SubLevelSilhouette(final List<LocalBox> localBoxes) {
        this.localBoxes = List.copyOf(localBoxes);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SubLevelSilhouette of(final List<LocalBox> localBoxes) {
        Objects.requireNonNull(localBoxes, "localBoxes");
        return new SubLevelSilhouette(localBoxes);
    }

    public List<LocalBox> localBoxes() {
        return this.localBoxes;
    }

    public boolean isEmpty() {
        return this.localBoxes.isEmpty();
    }

    /**
     * Produces a filled top-down silhouette and boundary line segments in global X/Z coordinates.
     *
     * <p>Do not call this for every radar contact every tick. It is intended for selected,
     * classified, or otherwise important contacts. Cache the resulting silhouette for a few
     * ticks when the monitor does not need perfectly continuous shape rotation.</p>
     */
    public ProjectedSilhouette project(final PointProjector projector, final ProjectionSettings requestedSettings) {
        Objects.requireNonNull(projector, "projector");
        Objects.requireNonNull(requestedSettings, "requestedSettings");

        if (this.localBoxes.isEmpty()) {
            return ProjectedSilhouette.empty(requestedSettings.preferredCellSize());
        }

        final List<Polygon> projectedBoxes = new ArrayList<>(this.localBoxes.size());
        final MutableVec3 transformed = new MutableVec3();

        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (final LocalBox box : this.localBoxes) {
            final Polygon polygon = projectBox(box, projector, transformed);
            if (polygon.points.size() < 3 || polygon.areaSquared() <= EPSILON) {
                continue;
            }

            projectedBoxes.add(polygon);
            minX = Math.min(minX, polygon.minX);
            minZ = Math.min(minZ, polygon.minZ);
            maxX = Math.max(maxX, polygon.maxX);
            maxZ = Math.max(maxZ, polygon.maxZ);
        }

        if (projectedBoxes.isEmpty()) {
            return ProjectedSilhouette.empty(requestedSettings.preferredCellSize());
        }

        final GridSpec grid = GridSpec.fromBounds(minX, minZ, maxX, maxZ, requestedSettings);
        final BitSet occupied = new BitSet(grid.width * grid.height);

        for (final Polygon polygon : projectedBoxes) {
            rasterizePolygon(polygon, grid, occupied);
        }

        final List<LineSegment> boundary = extractBoundary(grid, occupied, requestedSettings.maxOutputSegments());
        return new ProjectedSilhouette(grid.cellSize, grid.originX, grid.originZ, grid.width, grid.height, boundary);
    }

    private static Polygon projectBox(final LocalBox box, final PointProjector projector, final MutableVec3 transformed) {
        final ArrayList<Vec2> points = new ArrayList<>(8);

        for (int xBit = 0; xBit < 2; xBit++) {
            final double x = xBit == 0 ? box.minX : box.maxX;
            for (int yBit = 0; yBit < 2; yBit++) {
                final double y = yBit == 0 ? box.minY : box.maxY;
                for (int zBit = 0; zBit < 2; zBit++) {
                    final double z = zBit == 0 ? box.minZ : box.maxZ;
                    projector.transform(x, y, z, transformed);
                    addUnique(points, new Vec2(transformed.x, transformed.z));
                }
            }
        }

        return new Polygon(convexHull(points));
    }

    private static void addUnique(final List<Vec2> points, final Vec2 candidate) {
        for (final Vec2 existing : points) {
            final double dx = existing.x - candidate.x;
            final double dz = existing.z - candidate.z;
            if (dx * dx + dz * dz <= EPSILON * EPSILON) {
                return;
            }
        }
        points.add(candidate);
    }

    /** Monotone-chain hull. Result is counter-clockwise without a repeated first point. */
    private static List<Vec2> convexHull(final List<Vec2> input) {
        if (input.size() <= 2) {
            return List.copyOf(input);
        }

        final ArrayList<Vec2> sorted = new ArrayList<>(input);
        sorted.sort(Comparator.comparingDouble(Vec2::x).thenComparingDouble(Vec2::z));

        final ArrayList<Vec2> lower = new ArrayList<>();
        for (final Vec2 point : sorted) {
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), point) <= EPSILON) {
                lower.remove(lower.size() - 1);
            }
            lower.add(point);
        }

        final ArrayList<Vec2> upper = new ArrayList<>();
        for (int index = sorted.size() - 1; index >= 0; index--) {
            final Vec2 point = sorted.get(index);
            while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), point) <= EPSILON) {
                upper.remove(upper.size() - 1);
            }
            upper.add(point);
        }

        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);
        lower.addAll(upper);
        return lower;
    }

    private static double cross(final Vec2 a, final Vec2 b, final Vec2 c) {
        return (b.x - a.x) * (c.z - a.z) - (b.z - a.z) * (c.x - a.x);
    }

    private static void rasterizePolygon(final Polygon polygon, final GridSpec grid, final BitSet occupied) {
        final int startX = grid.clampX((int) Math.floor((polygon.minX - grid.originX) / grid.cellSize) - 1);
        final int endX = grid.clampX((int) Math.ceil((polygon.maxX - grid.originX) / grid.cellSize) + 1);
        final int startZ = grid.clampZ((int) Math.floor((polygon.minZ - grid.originZ) / grid.cellSize) - 1);
        final int endZ = grid.clampZ((int) Math.ceil((polygon.maxZ - grid.originZ) / grid.cellSize) + 1);

        for (int z = startZ; z <= endZ; z++) {
            final double cellMinZ = grid.originZ + z * grid.cellSize;
            final double cellMaxZ = cellMinZ + grid.cellSize;
            for (int x = startX; x <= endX; x++) {
                final double cellMinX = grid.originX + x * grid.cellSize;
                final double cellMaxX = cellMinX + grid.cellSize;
                if (polygonIntersectsCell(polygon, cellMinX, cellMinZ, cellMaxX, cellMaxZ)) {
                    occupied.set(grid.index(x, z));
                }
            }
        }
    }

    /**
     * Conservative polygon/cell overlap. The conservative behavior prevents thin angled pieces
     * from disappearing merely because no cell center lands inside their projected polygon.
     */
    private static boolean polygonIntersectsCell(final Polygon polygon, final double minX, final double minZ,
                                                  final double maxX, final double maxZ) {
        for (final Vec2 point : polygon.points) {
            if (point.x >= minX - EPSILON && point.x <= maxX + EPSILON
                && point.z >= minZ - EPSILON && point.z <= maxZ + EPSILON) {
                return true;
            }
        }

        final Vec2 bottomLeft = new Vec2(minX, minZ);
        final Vec2 bottomRight = new Vec2(maxX, minZ);
        final Vec2 topRight = new Vec2(maxX, maxZ);
        final Vec2 topLeft = new Vec2(minX, maxZ);

        if (polygon.contains(bottomLeft) || polygon.contains(bottomRight)
            || polygon.contains(topRight) || polygon.contains(topLeft)) {
            return true;
        }

        for (int index = 0; index < polygon.points.size(); index++) {
            final Vec2 a = polygon.points.get(index);
            final Vec2 b = polygon.points.get((index + 1) % polygon.points.size());
            if (segmentsIntersect(a, b, bottomLeft, bottomRight)
                || segmentsIntersect(a, b, bottomRight, topRight)
                || segmentsIntersect(a, b, topRight, topLeft)
                || segmentsIntersect(a, b, topLeft, bottomLeft)) {
                return true;
            }
        }

        return false;
    }

    private static boolean segmentsIntersect(final Vec2 a, final Vec2 b, final Vec2 c, final Vec2 d) {
        final double abC = cross(a, b, c);
        final double abD = cross(a, b, d);
        final double cdA = cross(c, d, a);
        final double cdB = cross(c, d, b);

        if (((abC > EPSILON && abD < -EPSILON) || (abC < -EPSILON && abD > EPSILON))
            && ((cdA > EPSILON && cdB < -EPSILON) || (cdA < -EPSILON && cdB > EPSILON))) {
            return true;
        }

        return Math.abs(abC) <= EPSILON && pointOnSegment(c, a, b)
            || Math.abs(abD) <= EPSILON && pointOnSegment(d, a, b)
            || Math.abs(cdA) <= EPSILON && pointOnSegment(a, c, d)
            || Math.abs(cdB) <= EPSILON && pointOnSegment(b, c, d);
    }

    private static boolean pointOnSegment(final Vec2 point, final Vec2 a, final Vec2 b) {
        return point.x >= Math.min(a.x, b.x) - EPSILON && point.x <= Math.max(a.x, b.x) + EPSILON
            && point.z >= Math.min(a.z, b.z) - EPSILON && point.z <= Math.max(a.z, b.z) + EPSILON;
    }

    private static List<LineSegment> extractBoundary(final GridSpec grid, final BitSet occupied, final int maxSegments) {
        final ArrayList<LineSegment> result = new ArrayList<>();

        for (int z = 0; z < grid.height; z++) {
            for (int x = 0; x < grid.width; x++) {
                if (!occupied.get(grid.index(x, z))) {
                    continue;
                }

                final double minX = grid.originX + x * grid.cellSize;
                final double minZ = grid.originZ + z * grid.cellSize;
                final double maxX = minX + grid.cellSize;
                final double maxZ = minZ + grid.cellSize;

                if (!isOccupied(grid, occupied, x, z - 1)) {
                    result.add(new LineSegment(new Vec2(minX, minZ), new Vec2(maxX, minZ)));
                }
                if (!isOccupied(grid, occupied, x + 1, z)) {
                    result.add(new LineSegment(new Vec2(maxX, minZ), new Vec2(maxX, maxZ)));
                }
                if (!isOccupied(grid, occupied, x, z + 1)) {
                    result.add(new LineSegment(new Vec2(maxX, maxZ), new Vec2(minX, maxZ)));
                }
                if (!isOccupied(grid, occupied, x - 1, z)) {
                    result.add(new LineSegment(new Vec2(minX, maxZ), new Vec2(minX, minZ)));
                }

                if (result.size() >= maxSegments) {
                    return List.copyOf(result);
                }
            }
        }

        return List.copyOf(result);
    }

    private static boolean isOccupied(final GridSpec grid, final BitSet occupied, final int x, final int z) {
        return x >= 0 && x < grid.width && z >= 0 && z < grid.height && occupied.get(grid.index(x, z));
    }

    /** A collision box in the sub-level's local/plot coordinate space. */
    public record LocalBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public LocalBox {
            if (!(maxX > minX && maxY > minY && maxZ > minZ)) {
                throw new IllegalArgumentException("LocalBox must have positive volume");
            }
        }
    }

    /** A top-down coordinate in global space. x is world X, z is world Z. */
    public record Vec2(double x, double z) {
    }

    /** A drawable world-space line segment for the monitor. */
    public record LineSegment(Vec2 start, Vec2 end) {
    }

    /**
     * Mutable destination passed into point transforms to avoid allocating eight vectors for every
     * collision box on every projection update.
     */
    public static final class MutableVec3 {
        public double x;
        public double y;
        public double z;

        public MutableVec3 set(final double x, final double y, final double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }
    }

    /** Converts one local/plot-space point into global world coordinates. */
    @FunctionalInterface
    public interface PointProjector {
        void transform(double localX, double localY, double localZ, MutableVec3 destination);
    }

    /**
     * preferredCellSize controls visual detail. 0.5 is a good selected-target default.
     * maxGridSide and maxRasterCells prevent a huge ship or an extreme camera range from
     * producing an unbounded amount of work.
     */
    public record ProjectionSettings(double preferredCellSize, int maxGridSide, int maxRasterCells, int maxOutputSegments) {
        public ProjectionSettings {
            if (!(preferredCellSize > 0.0)) {
                throw new IllegalArgumentException("preferredCellSize must be > 0");
            }
            if (maxGridSide < 8) {
                throw new IllegalArgumentException("maxGridSide must be at least 8");
            }
            if (maxRasterCells < 64) {
                throw new IllegalArgumentException("maxRasterCells must be at least 64");
            }
            if (maxOutputSegments < 4) {
                throw new IllegalArgumentException("maxOutputSegments must be at least 4");
            }
        }

        public static ProjectionSettings selectedTargetDefault() {
            return new ProjectionSettings(0.5, 384, 120_000, 12_000);
        }

        public static ProjectionSettings distantContactDefault() {
            return new ProjectionSettings(1.0, 192, 36_000, 4_000);
        }
    }

    /**
     * World-space result. The monitor can draw every segment directly after converting world X/Z
     * into its own local screen coordinates.
     */
    public record ProjectedSilhouette(double cellSize, double originX, double originZ, int gridWidth, int gridHeight,
                                      List<LineSegment> boundarySegments) {
        public ProjectedSilhouette {
            boundarySegments = List.copyOf(boundarySegments);
        }

        private static ProjectedSilhouette empty(final double cellSize) {
            return new ProjectedSilhouette(cellSize, 0.0, 0.0, 0, 0, List.of());
        }

        public boolean isEmpty() {
            return this.boundarySegments.isEmpty();
        }
    }

    public static final class Builder {
        private final ArrayList<LocalBox> localBoxes = new ArrayList<>();

        public Builder add(final LocalBox box) {
            this.localBoxes.add(Objects.requireNonNull(box, "box"));
            return this;
        }

        public Builder add(final double minX, final double minY, final double minZ,
                           final double maxX, final double maxY, final double maxZ) {
            return this.add(new LocalBox(minX, minY, minZ, maxX, maxY, maxZ));
        }

        public SubLevelSilhouette build() {
            return new SubLevelSilhouette(this.localBoxes);
        }
    }

    private static final class Polygon {
        private final List<Vec2> points;
        private final double minX;
        private final double minZ;
        private final double maxX;
        private final double maxZ;

        private Polygon(final List<Vec2> points) {
            this.points = points;

            double minX = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (final Vec2 point : points) {
                minX = Math.min(minX, point.x);
                minZ = Math.min(minZ, point.z);
                maxX = Math.max(maxX, point.x);
                maxZ = Math.max(maxZ, point.z);
            }
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
        }

        private double areaSquared() {
            double areaTwice = 0.0;
            for (int index = 0; index < this.points.size(); index++) {
                final Vec2 a = this.points.get(index);
                final Vec2 b = this.points.get((index + 1) % this.points.size());
                areaTwice += a.x * b.z - a.z * b.x;
            }
            return areaTwice * areaTwice;
        }

        private boolean contains(final Vec2 point) {
            if (this.points.size() < 3) {
                return false;
            }

            for (int index = 0; index < this.points.size(); index++) {
                final Vec2 a = this.points.get(index);
                final Vec2 b = this.points.get((index + 1) % this.points.size());
                if (cross(a, b, point) < -EPSILON) {
                    return false;
                }
            }
            return true;
        }
    }

    private record GridSpec(double cellSize, double originX, double originZ, int width, int height) {

        private static GridSpec fromBounds(final double minX, final double minZ, final double maxX, final double maxZ,
                                               final ProjectionSettings settings) {
                double cellSize = settings.preferredCellSize();
                final double widthWorld = Math.max(cellSize, maxX - minX);
                final double heightWorld = Math.max(cellSize, maxZ - minZ);

                cellSize = Math.max(cellSize, widthWorld / Math.max(1, settings.maxGridSide() - 2));
                cellSize = Math.max(cellSize, heightWorld / Math.max(1, settings.maxGridSide() - 2));
                cellSize = Math.max(cellSize, Math.sqrt((widthWorld * heightWorld) / Math.max(1, settings.maxRasterCells())));

                final double originX = Math.floor(minX / cellSize) * cellSize - cellSize;
                final double originZ = Math.floor(minZ / cellSize) * cellSize - cellSize;
                final int width = Math.max(1, Math.min(settings.maxGridSide(),
                        (int) Math.ceil((maxX - originX) / cellSize) + 2));
                final int height = Math.max(1, Math.min(settings.maxGridSide(),
                        (int) Math.ceil((maxZ - originZ) / cellSize) + 2));
                return new GridSpec(cellSize, originX, originZ, width, height);
            }

            private int index(final int x, final int z) {
                return z * this.width + x;
            }

            private int clampX(final int x) {
                return Math.max(0, Math.min(this.width - 1, x));
            }

            private int clampZ(final int z) {
                return Math.max(0, Math.min(this.height - 1, z));
            }
        }
}
