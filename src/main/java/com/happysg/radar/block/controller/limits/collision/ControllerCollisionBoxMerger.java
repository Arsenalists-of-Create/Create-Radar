package com.happysg.radar.block.controller.limits.collision;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Combines adjacent view-space boxes before wireframe rasterization. Merging is
 * deliberately limited to axes parallel to the screen, so separate depth
 * layers retain their own outlines and occlusion behavior.
 */
final class ControllerCollisionBoxMerger {
    private static final double EPSILON = 1.0e-4;
    private static final int AXIS_COUNT = 3;
    private static final int MAX_PASSES = 3;

    private ControllerCollisionBoxMerger() {
    }

    static List<ControllerCollisionSnapshot.OrientedBox> merge(
            List<ControllerCollisionSnapshot.OrientedBox> boxes
    ) {
        List<ControllerCollisionSnapshot.OrientedBox> merged =
                new ArrayList<>(boxes);
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            int previousSize = merged.size();
            for (int axis = 0; axis < AXIS_COUNT; axis++) {
                merged = mergeAlongAxis(merged, axis);
            }
            if (merged.size() == previousSize) {
                break;
            }
        }
        return List.copyOf(merged);
    }

    private static List<ControllerCollisionSnapshot.OrientedBox>
    mergeAlongAxis(
            List<ControllerCollisionSnapshot.OrientedBox> boxes,
            int mergeAxis
    ) {
        Map<MergeKey, List<BoxSpan>> groups = new HashMap<>();
        List<ControllerCollisionSnapshot.OrientedBox> unmergeable =
                new ArrayList<>();

        for (ControllerCollisionSnapshot.OrientedBox box : boxes) {
            BoxBasis basis = BoxBasis.of(box);
            if (basis == null
                    || Math.abs(basis.unit(mergeAxis).depth()) > EPSILON) {
                unmergeable.add(box);
                continue;
            }
            groups.computeIfAbsent(key(basis, mergeAxis), ignored ->
                    new ArrayList<>()).add(new BoxSpan(box, basis,
                    basis.minimum(mergeAxis), basis.maximum(mergeAxis)));
        }

        List<ControllerCollisionSnapshot.OrientedBox> result =
                new ArrayList<>(boxes.size());
        result.addAll(unmergeable);
        for (List<BoxSpan> group : groups.values()) {
            group.sort(Comparator.comparingDouble(BoxSpan::minimum));
            BoxSpan current = group.get(0);
            for (int index = 1; index < group.size(); index++) {
                BoxSpan next = group.get(index);
                if (next.minimum() <= current.maximum() + EPSILON
                        && compatible(current.basis(), next.basis(),
                        mergeAxis)) {
                    current = mergedSpan(current, next, mergeAxis);
                } else {
                    result.add(current.box());
                    current = next;
                }
            }
            result.add(current.box());
        }
        return result;
    }

    private static BoxSpan mergedSpan(BoxSpan first, BoxSpan second,
                                      int mergeAxis) {
        double minimum = Math.min(first.minimum(), second.minimum());
        double maximum = Math.max(first.maximum(), second.maximum());
        ControllerCollisionSnapshot.OrientedBox box = withInterval(
                first.box(), first.basis(), mergeAxis, minimum, maximum);
        return new BoxSpan(box, BoxBasis.of(box), minimum, maximum);
    }

    private static ControllerCollisionSnapshot.OrientedBox withInterval(
            ControllerCollisionSnapshot.OrientedBox box,
            BoxBasis basis, int mergeAxis, double minimum, double maximum
    ) {
        Vector unit = basis.unit(mergeAxis);
        double oldCoordinate = basis.center().dot(unit);
        double newCoordinate = (minimum + maximum) * 0.5;
        Vector center = basis.center().add(unit.scale(
                newCoordinate - oldCoordinate));
        Vector[] axes = basis.axes().clone();
        axes[mergeAxis] = unit.scale((maximum - minimum) * 0.5);
        return new ControllerCollisionSnapshot.OrientedBox(
                (float) center.u(), (float) center.v(),
                (float) center.depth(),
                (float) axes[0].u(), (float) axes[0].v(),
                (float) axes[0].depth(),
                (float) axes[1].u(), (float) axes[1].v(),
                (float) axes[1].depth(),
                (float) axes[2].u(), (float) axes[2].v(),
                (float) axes[2].depth(), box.category());
    }

    private static boolean compatible(BoxBasis first, BoxBasis second,
                                      int mergeAxis) {
        for (int axis = 0; axis < AXIS_COUNT; axis++) {
            if (!first.unit(axis).closeTo(second.unit(axis))) {
                return false;
            }
            if (axis != mergeAxis
                    && (!close(first.minimum(axis), second.minimum(axis))
                    || !close(first.maximum(axis), second.maximum(axis)))) {
                return false;
            }
        }
        return true;
    }

    private static MergeKey key(BoxBasis basis, int mergeAxis) {
        int firstOther = (mergeAxis + 1) % AXIS_COUNT;
        int secondOther = (mergeAxis + 2) % AXIS_COUNT;
        Vector x = basis.unit(0);
        Vector y = basis.unit(1);
        Vector z = basis.unit(2);
        return new MergeKey(basis.box().category(),
                quantize(x.u()), quantize(x.v()), quantize(x.depth()),
                quantize(y.u()), quantize(y.v()), quantize(y.depth()),
                quantize(z.u()), quantize(z.v()), quantize(z.depth()),
                quantize(basis.minimum(firstOther)),
                quantize(basis.maximum(firstOther)),
                quantize(basis.minimum(secondOther)),
                quantize(basis.maximum(secondOther)));
    }

    private static long quantize(double value) {
        return Math.round(value / EPSILON);
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) <= EPSILON;
    }

    private record BoxBasis(
            ControllerCollisionSnapshot.OrientedBox box,
            Vector center,
            Vector[] axes,
            Vector[] units,
            double[] lengths
    ) {
        private static BoxBasis of(
                ControllerCollisionSnapshot.OrientedBox box
        ) {
            Vector center = new Vector(box.centerU(), box.centerV(),
                    box.centerDepth());
            Vector[] axes = {
                    new Vector(box.axisXU(), box.axisXV(), box.axisXDepth()),
                    new Vector(box.axisYU(), box.axisYV(), box.axisYDepth()),
                    new Vector(box.axisZU(), box.axisZV(), box.axisZDepth())
            };
            Vector[] units = new Vector[AXIS_COUNT];
            double[] lengths = new double[AXIS_COUNT];
            for (int axis = 0; axis < AXIS_COUNT; axis++) {
                lengths[axis] = axes[axis].length();
                if (lengths[axis] <= EPSILON) {
                    return null;
                }
                units[axis] = axes[axis].scale(1.0 / lengths[axis]);
            }
            return new BoxBasis(box, center, axes, units, lengths);
        }

        private Vector unit(int axis) {
            return units[axis];
        }

        private double minimum(int axis) {
            return center.dot(unit(axis)) - lengths[axis];
        }

        private double maximum(int axis) {
            return center.dot(unit(axis)) + lengths[axis];
        }
    }

    private record BoxSpan(
            ControllerCollisionSnapshot.OrientedBox box,
            BoxBasis basis,
            double minimum,
            double maximum
    ) {
    }

    private record Vector(double u, double v, double depth) {
        private Vector add(Vector other) {
            return new Vector(u + other.u, v + other.v,
                    depth + other.depth);
        }

        private Vector scale(double scale) {
            return new Vector(u * scale, v * scale, depth * scale);
        }

        private double dot(Vector other) {
            return u * other.u + v * other.v + depth * other.depth;
        }

        private double length() {
            return Math.sqrt(dot(this));
        }

        private boolean closeTo(Vector other) {
            return close(u, other.u) && close(v, other.v)
                    && close(depth, other.depth);
        }
    }

    private record MergeKey(
            ControllerCollisionSnapshot.Category category,
            long xx, long xv, long xd,
            long yx, long yv, long yd,
            long zx, long zv, long zd,
            long firstMinimum, long firstMaximum,
            long secondMinimum, long secondMaximum
    ) {
    }
}
