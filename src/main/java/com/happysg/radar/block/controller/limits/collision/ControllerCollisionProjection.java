package com.happysg.radar.block.controller.limits.collision;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Software orthographic visible-surface pass for the controller GUI. The
 * wireframe is extracted from changes in the nearest surface rather than from
 * every source box edge, which removes seams between coplanar colliders.
 */
public final class ControllerCollisionProjection {
    public static final int RESOLUTION = 256;
    private static final int MAX_RESOLUTION = 1_024;
    private static final float DEPTH_EPSILON = 1.0e-4f;
    private static final float PLANE_EPSILON = 2.5e-3f;
    private static final float NORMAL_DOT_MINIMUM = 0.9995f;
    private static final int[][] FACES = {
            {0, 1, 3, 2}, {4, 6, 7, 5},
            {0, 4, 5, 1}, {2, 3, 7, 6},
            {0, 2, 6, 4}, {1, 5, 7, 3}
    };
    private static final int[][] EDGES = {
            {0, 1}, {0, 2}, {0, 4},
            {1, 3}, {1, 5}, {2, 3}, {2, 6},
            {3, 7}, {4, 5}, {4, 6},
            {5, 7}, {6, 7}
    };

    private ControllerCollisionProjection() {
    }

    public static ProjectedView project(
            ControllerCollisionSnapshot snapshot
    ) {
        return project(snapshot, RESOLUTION);
    }

    public static ProjectedView project(
            ControllerCollisionSnapshot snapshot, int resolution
    ) {
        return project(snapshot, resolution, category -> true);
    }

    public static ProjectedView project(
            ControllerCollisionSnapshot snapshot, int resolution,
            Predicate<ControllerCollisionSnapshot.Category> includedCategory
    ) {
        if (resolution < 1 || resolution > MAX_RESOLUTION) {
            throw new IllegalArgumentException(
                    "Invalid controller collision resolution " + resolution);
        }
        Objects.requireNonNull(includedCategory, "includedCategory");
        SurfaceBuffer surfaces = new SurfaceBuffer(resolution,
                snapshot.depth());
        List<ControllerCollisionSnapshot.OrientedBox> boxes =
                ControllerCollisionBoxMerger.merge(snapshot.boxes().stream()
                        .filter(box -> includedCategory.test(box.category()))
                        .toList());
        for (ControllerCollisionSnapshot.OrientedBox box : boxes) {
            rasterizeBox(box, snapshot.halfSpan(), snapshot.depth(),
                    surfaces);
        }

        int[] styles = extractBoundaries(surfaces);
        return new ProjectedView(resolution,
                mergeRuns(styles, resolution));
    }

    private static void rasterizeBox(
            ControllerCollisionSnapshot.OrientedBox box,
            float halfSpan, float depthRange, SurfaceBuffer surfaces
    ) {
        ViewVertex[] vertices = vertices(box);
        for (int[] face : FACES) {
            List<ViewVertex> polygon = new ArrayList<>(4);
            for (int index : face) {
                polygon.add(vertices[index]);
            }
            Surface surface = Surface.of(polygon.get(0), polygon.get(1),
                    polygon.get(2), box.category());
            if (surface == null) {
                continue;
            }
            polygon = clipDepth(polygon, 0.0f, true);
            polygon = clipDepth(polygon, depthRange, false);
            rasterizePolygon(polygon, surface, halfSpan, surfaces);
        }
        rasterizeCap(vertices, 0.0f, box.category(), halfSpan, surfaces);
        rasterizeCap(vertices, depthRange, box.category(), halfSpan,
                surfaces);
    }

    private static ViewVertex[] vertices(
            ControllerCollisionSnapshot.OrientedBox box
    ) {
        ViewVertex[] result = new ViewVertex[8];
        for (int xBit = 0; xBit < 2; xBit++) {
            float sx = xBit == 0 ? -1.0f : 1.0f;
            for (int yBit = 0; yBit < 2; yBit++) {
                float sy = yBit == 0 ? -1.0f : 1.0f;
                for (int zBit = 0; zBit < 2; zBit++) {
                    float sz = zBit == 0 ? -1.0f : 1.0f;
                    float u = box.centerU()
                            + sx * box.axisXU()
                            + sy * box.axisYU()
                            + sz * box.axisZU();
                    float v = box.centerV()
                            + sx * box.axisXV()
                            + sy * box.axisYV()
                            + sz * box.axisZV();
                    float depth = box.centerDepth()
                            + sx * box.axisXDepth()
                            + sy * box.axisYDepth()
                            + sz * box.axisZDepth();
                    result[(xBit << 2) | (yBit << 1) | zBit] =
                            new ViewVertex(u, v, depth);
                }
            }
        }
        return result;
    }

    private static List<ViewVertex> clipDepth(
            List<ViewVertex> input, float plane, boolean keepGreater
    ) {
        if (input.isEmpty()) {
            return input;
        }
        ArrayList<ViewVertex> output = new ArrayList<>(input.size() + 2);
        ViewVertex previous = input.getLast();
        boolean previousInside = inside(previous, plane, keepGreater);
        for (ViewVertex current : input) {
            boolean currentInside = inside(current, plane, keepGreater);
            if (previousInside != currentInside) {
                output.add(intersection(previous, current, plane));
            }
            if (currentInside) {
                output.add(current);
            }
            previous = current;
            previousInside = currentInside;
        }
        return output;
    }

    private static boolean inside(ViewVertex vertex, float plane,
                                  boolean keepGreater) {
        return keepGreater ? vertex.depth() >= plane - DEPTH_EPSILON
                : vertex.depth() <= plane + DEPTH_EPSILON;
    }

    private static ViewVertex intersection(ViewVertex first,
                                           ViewVertex second,
                                           float plane) {
        float difference = second.depth() - first.depth();
        if (Math.abs(difference) <= DEPTH_EPSILON) {
            return new ViewVertex(first.u(), first.v(), plane);
        }
        float amount = (plane - first.depth()) / difference;
        return new ViewVertex(
                first.u() + (second.u() - first.u()) * amount,
                first.v() + (second.v() - first.v()) * amount,
                plane);
    }

    private static void rasterizeCap(
            ViewVertex[] vertices, float plane,
            ControllerCollisionSnapshot.Category category,
            float halfSpan, SurfaceBuffer surfaces
    ) {
        float minimum = Float.POSITIVE_INFINITY;
        float maximum = Float.NEGATIVE_INFINITY;
        for (ViewVertex vertex : vertices) {
            minimum = Math.min(minimum, vertex.depth());
            maximum = Math.max(maximum, vertex.depth());
        }
        if (minimum >= plane - DEPTH_EPSILON
                || maximum <= plane + DEPTH_EPSILON) {
            return;
        }

        ArrayList<ViewVertex> cap = new ArrayList<>(6);
        for (int[] edge : EDGES) {
            ViewVertex first = vertices[edge[0]];
            ViewVertex second = vertices[edge[1]];
            float firstDistance = first.depth() - plane;
            float secondDistance = second.depth() - plane;
            if (Math.abs(firstDistance) <= DEPTH_EPSILON) {
                addUnique(cap, new ViewVertex(first.u(), first.v(), plane));
            }
            if (Math.abs(secondDistance) <= DEPTH_EPSILON) {
                addUnique(cap, new ViewVertex(second.u(), second.v(), plane));
            }
            if (firstDistance * secondDistance < 0.0f) {
                addUnique(cap, intersection(first, second, plane));
            }
        }
        if (cap.size() < 3) {
            return;
        }
        float centerU = 0.0f;
        float centerV = 0.0f;
        for (ViewVertex vertex : cap) {
            centerU += vertex.u();
            centerV += vertex.v();
        }
        float finalCenterU = centerU / cap.size();
        float finalCenterV = centerV / cap.size();
        cap.sort(Comparator.comparingDouble(vertex -> Math.atan2(
                vertex.v() - finalCenterV, vertex.u() - finalCenterU)));
        Surface surface = new Surface(0.0f, 0.0f, 1.0f, plane,
                category);
        rasterizePolygon(cap, surface, halfSpan, surfaces);
    }

    private static void addUnique(List<ViewVertex> vertices,
                                  ViewVertex candidate) {
        for (ViewVertex existing : vertices) {
            if (Math.abs(existing.u() - candidate.u()) <= DEPTH_EPSILON
                    && Math.abs(existing.v() - candidate.v())
                    <= DEPTH_EPSILON) {
                return;
            }
        }
        vertices.add(candidate);
    }

    private static void rasterizePolygon(
            List<ViewVertex> polygon, Surface surface,
            float halfSpan, SurfaceBuffer surfaces
    ) {
        if (polygon.size() < 3) {
            return;
        }
        ScreenVertex first = toScreen(polygon.get(0), halfSpan,
                surfaces.resolution());
        for (int index = 1; index < polygon.size() - 1; index++) {
            ScreenVertex second = toScreen(polygon.get(index), halfSpan,
                    surfaces.resolution());
            ScreenVertex third = toScreen(polygon.get(index + 1), halfSpan,
                    surfaces.resolution());
            rasterizeTriangle(first, second, third, surface, surfaces);
        }
    }

    private static ScreenVertex toScreen(ViewVertex vertex, float halfSpan,
                                         int resolution) {
        float x = (vertex.u() / (halfSpan * 2.0f) + 0.5f)
                * (resolution - 1);
        float y = (0.5f - vertex.v() / (halfSpan * 2.0f))
                * (resolution - 1);
        return new ScreenVertex(x, y, vertex.depth());
    }

    private static void rasterizeTriangle(
            ScreenVertex first, ScreenVertex second, ScreenVertex third,
            Surface surface, SurfaceBuffer surfaces
    ) {
        float area = edge(first.x(), first.y(), second.x(), second.y(),
                third.x(), third.y());
        if (Math.abs(area) < 1.0e-6f) {
            return;
        }
        int resolution = surfaces.resolution();
        int minX = clamp((int) Math.floor(Math.min(first.x(),
                Math.min(second.x(), third.x()))), 0, resolution - 1);
        int maxX = clamp((int) Math.ceil(Math.max(first.x(),
                Math.max(second.x(), third.x()))), 0, resolution - 1);
        int minY = clamp((int) Math.floor(Math.min(first.y(),
                Math.min(second.y(), third.y()))), 0, resolution - 1);
        int maxY = clamp((int) Math.ceil(Math.max(first.y(),
                Math.max(second.y(), third.y()))), 0, resolution - 1);

        for (int y = minY; y <= maxY; y++) {
            float pixelY = y + 0.5f;
            for (int x = minX; x <= maxX; x++) {
                float pixelX = x + 0.5f;
                float firstWeight = edge(second.x(), second.y(),
                        third.x(), third.y(), pixelX, pixelY) / area;
                float secondWeight = edge(third.x(), third.y(),
                        first.x(), first.y(), pixelX, pixelY) / area;
                float thirdWeight = 1.0f - firstWeight - secondWeight;
                if (firstWeight < -1.0e-5f
                        || secondWeight < -1.0e-5f
                        || thirdWeight < -1.0e-5f) {
                    continue;
                }
                float depth = firstWeight * first.depth()
                        + secondWeight * second.depth()
                        + thirdWeight * third.depth();
                if (depth < -DEPTH_EPSILON
                        || depth > surfaces.depthRange() + DEPTH_EPSILON) {
                    continue;
                }
                surfaces.write(y * resolution + x, depth, surface);
            }
        }
    }

    private static int[] extractBoundaries(SurfaceBuffer surfaces) {
        int resolution = surfaces.resolution();
        int[] styles = new int[resolution * resolution];
        for (int y = 0; y < resolution; y++) {
            for (int x = 0; x < resolution; x++) {
                int current = y * resolution + x;
                if (x == 0) {
                    markBoundary(surfaces, styles, current, -1);
                }
                if (y == 0) {
                    markBoundary(surfaces, styles, current, -1);
                }
                if (x + 1 < resolution) {
                    markBoundary(surfaces, styles, current, current + 1);
                } else {
                    markBoundary(surfaces, styles, current, -1);
                }
                if (y + 1 < resolution) {
                    markBoundary(surfaces, styles, current,
                            current + resolution);
                } else {
                    markBoundary(surfaces, styles, current, -1);
                }
            }
        }
        return styles;
    }

    private static void markBoundary(SurfaceBuffer surfaces, int[] styles,
                                     int first, int second) {
        boolean firstOccupied = first >= 0 && surfaces.occupied(first);
        boolean secondOccupied = second >= 0 && surfaces.occupied(second);
        if (!firstOccupied && !secondOccupied) {
            return;
        }
        if (firstOccupied && secondOccupied
                && surfaces.sameSurface(first, second)) {
            return;
        }
        int selected;
        if (!firstOccupied) {
            selected = second;
        } else if (!secondOccupied) {
            selected = first;
        } else {
            selected = surfaces.preferred(first, second);
        }
        styles[selected] = surfaces.style(selected);
    }

    private static List<LineRun> mergeRuns(int[] styles, int resolution) {
        ArrayList<LineRun> runs = new ArrayList<>();
        for (int y = 0; y < resolution; y++) {
            int x = 0;
            while (x < resolution) {
                int style = styles[y * resolution + x];
                if (style == 0) {
                    x++;
                    continue;
                }
                int start = x++;
                while (x < resolution
                        && styles[y * resolution + x] == style) {
                    x++;
                }
                int value = style - 1;
                ControllerCollisionSnapshot.Category category =
                        ControllerCollisionSnapshot.Category.values()[value / 5];
                runs.add(new LineRun(start, x, y, value % 5, category));
            }
        }
        return List.copyOf(runs);
    }

    private static float edge(float ax, float ay, float bx, float by,
                              float px, float py) {
        return (px - ax) * (by - ay) - (py - ay) * (bx - ax);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ViewVertex(float u, float v, float depth) {
    }

    private record ScreenVertex(float x, float y, float depth) {
    }

    private record Surface(
            float normalU, float normalV, float normalDepth,
            float plane, ControllerCollisionSnapshot.Category category
    ) {
        private static Surface of(ViewVertex first, ViewVertex second,
                                  ViewVertex third,
                                  ControllerCollisionSnapshot.Category category) {
            float firstU = second.u() - first.u();
            float firstV = second.v() - first.v();
            float firstDepth = second.depth() - first.depth();
            float secondU = third.u() - first.u();
            float secondV = third.v() - first.v();
            float secondDepth = third.depth() - first.depth();
            float normalU = firstV * secondDepth
                    - firstDepth * secondV;
            float normalV = firstDepth * secondU
                    - firstU * secondDepth;
            float normalDepth = firstU * secondV - firstV * secondU;
            float length = (float) Math.sqrt(normalU * normalU
                    + normalV * normalV + normalDepth * normalDepth);
            if (length <= DEPTH_EPSILON) {
                return null;
            }
            normalU /= length;
            normalV /= length;
            normalDepth /= length;
            float dominant = Math.abs(normalU) >= Math.abs(normalV)
                    && Math.abs(normalU) >= Math.abs(normalDepth)
                    ? normalU : Math.abs(normalV) >= Math.abs(normalDepth)
                    ? normalV : normalDepth;
            if (dominant < 0.0f) {
                normalU = -normalU;
                normalV = -normalV;
                normalDepth = -normalDepth;
            }
            float plane = normalU * first.u() + normalV * first.v()
                    + normalDepth * first.depth();
            return new Surface(normalU, normalV, normalDepth, plane,
                    category);
        }
    }

    private static final class SurfaceBuffer {
        private final int resolution;
        private final float depthRange;
        private final float[] depths;
        private final float[] normalU;
        private final float[] normalV;
        private final float[] normalDepth;
        private final float[] planes;
        private final int[] categories;

        private SurfaceBuffer(int resolution, float depthRange) {
            this.resolution = resolution;
            this.depthRange = depthRange;
            int size = resolution * resolution;
            depths = new float[size];
            normalU = new float[size];
            normalV = new float[size];
            normalDepth = new float[size];
            planes = new float[size];
            categories = new int[size];
            Arrays.fill(depths, Float.POSITIVE_INFINITY);
            Arrays.fill(categories, -1);
        }

        private int resolution() {
            return resolution;
        }

        private float depthRange() {
            return depthRange;
        }

        private void write(int index, float depth, Surface surface) {
            if (depth > depths[index] + DEPTH_EPSILON) {
                return;
            }
            int category = surface.category().ordinal();
            if (Math.abs(depth - depths[index]) <= DEPTH_EPSILON
                    && categories[index] >= 0
                    && category < categories[index]) {
                return;
            }
            depths[index] = Math.max(0.0f, Math.min(depthRange, depth));
            normalU[index] = surface.normalU();
            normalV[index] = surface.normalV();
            normalDepth[index] = surface.normalDepth();
            planes[index] = surface.plane();
            categories[index] = category;
        }

        private boolean occupied(int index) {
            return categories[index] >= 0;
        }

        private boolean sameSurface(int first, int second) {
            if (categories[first] != categories[second]) {
                return false;
            }
            float dot = normalU[first] * normalU[second]
                    + normalV[first] * normalV[second]
                    + normalDepth[first] * normalDepth[second];
            return dot >= NORMAL_DOT_MINIMUM
                    && Math.abs(planes[first] - planes[second])
                    <= PLANE_EPSILON;
        }

        private int preferred(int first, int second) {
            if (depths[first] < depths[second] - DEPTH_EPSILON) {
                return first;
            }
            if (depths[second] < depths[first] - DEPTH_EPSILON) {
                return second;
            }
            return categories[first] >= categories[second] ? first : second;
        }

        private int style(int index) {
            int band = clamp((int) Math.floor(
                    depths[index] / depthRange * 5.0f), 0, 4);
            return 1 + band + categories[index] * 5;
        }
    }

    public record ProjectedView(int resolution, List<LineRun> runs) {
        public ProjectedView {
            runs = List.copyOf(runs);
        }
    }

    public record LineRun(int startX, int endXExclusive, int y,
                          int depthBand,
                          ControllerCollisionSnapshot.Category category) {
    }
}
