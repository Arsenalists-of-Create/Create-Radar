package com.happysg.radar.block.controller.limits.collision;

import com.happysg.radar.block.controller.kinetic.CannonAxis;
import com.happysg.radar.block.controller.limits.ControllerLimitDialMath;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public final class ControllerCollisionSelfTest {
    private static final double EPSILON = 1.0e-6;

    private ControllerCollisionSelfTest() {
    }

    public static void main(String[] args) {
        testPitchMirrorsWithPlayerSide();
        testYawUsesCannonForwardAsScreenTop();
        testLimitDialDirections();
        testLimitDialSnappingAndCrossing();
        testYawLimitDialKeepsSignedEndpoints();
        testDepthRangeStartsOnViewerSide();
        testSteppedBlockCollapsesToOneEnvelope();
        testOutlineOnlyBlockRetainsVisualEnvelope();
        testHalfShaftProxyDimensions();
        testMountBracketProxyDimensions();
        testControlledMountUsesCannonCategory();
        testIntersectionAndBaseDimensions();
        testForwardOnlyDepthIntersection();
        testAdjacentBoxesMergeWithinLayer();
        testRectangularLayerMergesAcrossBothAxes();
        testDepthLayersRemainSeparate();
        testDifferentShapesAndCategoriesRemainSeparate();
        testProjectionRejectsGeometryBehindView();
        testNearPlaneCapOccludesDeeperGeometry();
        testUnequalStackedBoxesHaveNoInternalSeam();
        testDepthBreakRemainsVisible();
        testRequestedProjectionResolutionIsRetained();
        testFullWallOccludesDeeperCannon();
        testEnvironmentFilterRevealsDeeperCannon();
        testGapRevealsDeeperCannon();
        System.out.println("PASS controller collision projection checks");
    }

    private static void testSteppedBlockCollapsesToOneEnvelope() {
        VoxelShape stepped = Shapes.or(
                Shapes.create(new AABB(0, 0, 0, 1, 0.5, 1)),
                Shapes.create(new AABB(0, 0.5, 0, 0.5, 1, 1)));
        AABB envelope = ControllerDisplayShapeResolver.envelope(
                stepped, Shapes.empty());
        require(envelope != null
                        && close(envelope.minX, 0) && close(envelope.minY, 0)
                        && close(envelope.minZ, 0) && close(envelope.maxX, 1)
                        && close(envelope.maxY, 1) && close(envelope.maxZ, 1),
                "A stepped block must collapse to one outer envelope");
    }

    private static void testOutlineOnlyBlockRetainsVisualEnvelope() {
        AABB envelope = ControllerDisplayShapeResolver.envelope(
                Shapes.empty(), Shapes.create(new AABB(
                        0.25, 0.125, 0.375,
                        0.75, 0.875, 0.625)));
        require(envelope != null
                        && close(envelope.minX, 0.25)
                        && close(envelope.minY, 0.125)
                        && close(envelope.minZ, 0.375)
                        && close(envelope.maxX, 0.75)
                        && close(envelope.maxY, 0.875)
                        && close(envelope.maxZ, 0.625),
                "An outline-only block must still contribute display geometry");
    }

    private static void testHalfShaftProxyDimensions() {
        AABB east = ControllerDisplayShapeResolver.shaftBox(Direction.EAST);
        require(close(east.minX, 0.5) && close(east.maxX, 1.0)
                        && close(east.minY, 6.0 / 16.0)
                        && close(east.maxY, 10.0 / 16.0)
                        && close(east.minZ, 6.0 / 16.0)
                        && close(east.maxZ, 10.0 / 16.0),
                "The Create shaft proxy must match the rendered half-shaft");
    }

    private static void testMountBracketProxyDimensions() {
        List<ControllerVisualBox> bracket =
                ControllerMountVisualGeometry.bracket(Vec3.ZERO,
                        new Vec3(0, 1, 0), new Vec3(0, 0, 1));
        require(bracket.size() == 3,
                "The CBC mount proxy must contain one base and two arms");
        ControllerVisualBox base = bracket.get(0);
        ControllerVisualBox left = bracket.get(1);
        ControllerVisualBox right = bracket.get(2);
        require(close(base.center().y, 3.0 / 32.0)
                        && close(base.axisX().length(), 10.0 / 16.0)
                        && close(base.axisY().length(), 3.0 / 32.0)
                        && close(base.axisZ().length(), 8.0 / 16.0),
                "The CBC mount proxy base dimensions changed unexpectedly");
        require(close(left.center().x, -9.0 / 16.0)
                        && close(right.center().x, 9.0 / 16.0)
                        && close(left.center().y, 1.0)
                        && close(right.center().y, 1.0),
                "The CBC mount proxy arms must straddle the yaw axis");
    }

    private static void testControlledMountUsesCannonCategory() {
        require(ControllerCollisionSnapshotBuilder.categoryForBlock(
                        ControllerCollisionSnapshot.Category.ENVIRONMENT,
                        true)
                        == ControllerCollisionSnapshot.Category.CANNON,
                "A controlled mount must remain with white cannon geometry");
        require(ControllerCollisionSnapshotBuilder.categoryForBlock(
                        ControllerCollisionSnapshot.Category.ENVIRONMENT,
                        false)
                        == ControllerCollisionSnapshot.Category.ENVIRONMENT,
                "An unrelated mount must retain its surrounding category");
    }

    private static void testPitchMirrorsWithPlayerSide() {
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 forward = new Vec3(0, 0, 1);
        ControllerCollisionViewFrame westView =
                ControllerCollisionViewFrame.pitch(Vec3.ZERO, up,
                        forward, new Vec3(1, 0, 0));
        ControllerCollisionViewFrame eastView =
                ControllerCollisionViewFrame.pitch(Vec3.ZERO, up,
                        forward, new Vec3(-1, 0, 0));

        require(close(westView.vectorToView(forward).x, 1.0),
                "Cannon should point screen-right when viewed looking east");
        require(close(eastView.vectorToView(forward).x, -1.0),
                "Opposite player side should mirror pitch view");
        require(close(westView.vectorToView(up).y, 1.0),
                "Parent up must remain screen-up");
        require(westView.vectorToView(new Vec3(1, 0, 0)).z > 0.0
                        && eastView.vectorToView(new Vec3(-1, 0, 0)).z > 0.0,
                "Pitch depth must follow the player's selected look side");
    }

    private static void testYawUsesCannonForwardAsScreenTop() {
        Vec3 forward = new Vec3(1, 0, 0);
        ControllerCollisionViewFrame frame =
                ControllerCollisionViewFrame.yaw(Vec3.ZERO,
                        new Vec3(0, 1, 0), forward);
        Vec3 projected = frame.vectorToView(forward);
        require(close(projected.x, 0.0) && close(projected.y, 1.0),
                "Yaw cannon forward must project to screen top");
        require(close(frame.vectorToView(new Vec3(0, -1, 0)).z, 1.0),
                "Yaw depth must progress downward from the top view");
    }

    private static void testLimitDialDirections() {
        ControllerLimitDialMath.Point pitchZero =
                ControllerLimitDialMath.direction(
                        CannonAxis.PITCH, 0.0, 0.0);
        ControllerLimitDialMath.Point pitchUp =
                ControllerLimitDialMath.direction(
                        CannonAxis.PITCH, 90.0, 0.0);
        ControllerLimitDialMath.Point pitchLeft =
                ControllerLimitDialMath.direction(
                        CannonAxis.PITCH, 0.0, 180.0);
        ControllerLimitDialMath.Point pitchLeftUp =
                ControllerLimitDialMath.direction(
                        CannonAxis.PITCH, 45.0, 180.0);
        ControllerLimitDialMath.Point yawZero =
                ControllerLimitDialMath.direction(
                        CannonAxis.YAW, 0.0, 0.0);
        ControllerLimitDialMath.Point yawRight =
                ControllerLimitDialMath.direction(
                        CannonAxis.YAW, 90.0, 0.0);
        ControllerLimitDialMath.Point rotatedYawZero =
                ControllerLimitDialMath.direction(
                        CannonAxis.YAW, 0.0, 90.0);
        require(close(pitchZero.x(), 1.0) && close(pitchZero.y(), 0.0),
                "Pitch zero must point screen-right");
        require(close(pitchUp.x(), 0.0) && close(pitchUp.y(), -1.0),
                "Positive pitch must rotate toward screen-top");
        require(close(pitchLeft.x(), -1.0)
                        && close(pitchLeft.y(), 0.0),
                "Pitch arms must mirror when the barrel faces left");
        require(pitchLeftUp.x() < 0.0 && pitchLeftUp.y() < 0.0,
                "Positive left-facing pitch must still rotate upward");
        require(close(yawZero.x(), 0.0) && close(yawZero.y(), -1.0),
                "Yaw neutral must point screen-top when aligned to barrel");
        require(close(yawRight.x(), 1.0) && close(yawRight.y(), 0.0),
                "Positive yaw must rotate clockwise");
        require(close(rotatedYawZero.x(), 1.0)
                        && close(rotatedYawZero.y(), 0.0),
                "Yaw neutral ray must rotate independently of barrel-up view");
    }

    private static void testLimitDialSnappingAndCrossing() {
        require(close(ControllerLimitDialMath.snapTenth(12.34), 12.3)
                        && close(ControllerLimitDialMath.snapTenth(-12.36),
                        -12.4),
                "Dial values must snap to tenths of a degree");
        double lower = ControllerLimitDialMath.draggedValue(
                CannonAxis.PITCH, ControllerLimitDialMath.Handle.LOWER,
                -1.0, -1.0, 0.0, -20.0, -20.0, 20.0);
        double upper = ControllerLimitDialMath.draggedValue(
                CannonAxis.PITCH, ControllerLimitDialMath.Handle.UPPER,
                -1.0, 1.0, 0.0, 20.0, -20.0, 20.0);
        double leftFacing = ControllerLimitDialMath.draggedValue(
                CannonAxis.PITCH, ControllerLimitDialMath.Handle.UPPER,
                -1.0, -1.0, 180.0, 20.0, -20.0, 90.0);
        require(close(lower, 20.0),
                "Lower limit must stop at the upper arm");
        require(close(upper, -20.0),
                "Upper limit must stop at the lower arm");
        require(close(leftFacing, 45.0),
                "Dragging a left-facing arm upward must produce positive pitch");
    }

    private static void testYawLimitDialKeepsSignedEndpoints() {
        double negative = ControllerLimitDialMath.draggedValue(
                CannonAxis.YAW, ControllerLimitDialMath.Handle.LOWER,
                0.0, 1.0, 0.0, -180.0, -180.0, 100.0);
        double positive = ControllerLimitDialMath.draggedValue(
                CannonAxis.YAW, ControllerLimitDialMath.Handle.UPPER,
                0.0, 1.0, 0.0, 180.0, -100.0, 180.0);
        require(close(negative, -180.0) && close(positive, 180.0),
                "Coincident yaw endpoints must retain their signed side");
    }

    private static void testDepthRangeStartsOnViewerSide() {
        ControllerCollisionViewFrame centerFrame =
                ControllerCollisionViewFrame.pitch(Vec3.ZERO,
                        new Vec3(0, 1, 0), new Vec3(0, 0, 1),
                        new Vec3(1, 0, 0));
        ControllerCollisionViewFrame frame =
                centerFrame.withCenteredDepthRange(5.0);
        require(close(frame.pointToView(Vec3.ZERO).z, 2.5),
                "Mount must remain centered within forward depth");
        require(close(frame.pointToView(centerFrame.origin().subtract(
                centerFrame.depth().scale(2.0))).z, 0.5),
                "Player-facing occluders must remain inside the view");
        require(close(frame.viewToWorld(0, 0, 5.0).subtract(
                frame.viewToWorld(0, 0, 0.0)).length(), 5.0),
                "View must still scan exactly five blocks forward");
    }

    private static void testIntersectionAndBaseDimensions() {
        ControllerCollisionSnapshot.OrientedBox center = box(0, 0, 0,
                0.5f, 0.5f, 0.5f,
                ControllerCollisionSnapshot.Category.ENVIRONMENT);
        ControllerCollisionSnapshot.OrientedBox outside = box(7, 0, 0,
                0.5f, 0.5f, 0.5f,
                ControllerCollisionSnapshot.Category.ENVIRONMENT);
        require(center.intersects(5.0f, 5.0f),
                "Centered collider should intersect 10x10x5 base view");
        require(!outside.intersects(5.0f, 5.0f),
                "Collider beyond base half-span should be rejected");
    }

    private static void testForwardOnlyDepthIntersection() {
        ControllerCollisionSnapshot.OrientedBox behind = box(0, 0, -1.0f,
                0.25f, 0.25f, 0.25f,
                ControllerCollisionSnapshot.Category.ENVIRONMENT);
        ControllerCollisionSnapshot.OrientedBox crossingNear = box(0, 0,
                0.0f, 0.25f, 0.25f, 0.25f,
                ControllerCollisionSnapshot.Category.ENVIRONMENT);
        ControllerCollisionSnapshot.OrientedBox beyond = box(0, 0, 5.5f,
                0.25f, 0.25f, 0.25f,
                ControllerCollisionSnapshot.Category.ENVIRONMENT);
        require(!behind.intersects(5.0f, 5.0f),
                "Collider behind the view plane must be rejected");
        require(crossingNear.intersects(5.0f, 5.0f),
                "Collider crossing the near plane must be retained");
        require(!beyond.intersects(5.0f, 5.0f),
                "Collider beyond forward depth must be rejected");
    }

    private static void testAdjacentBoxesMergeWithinLayer() {
        List<ControllerCollisionSnapshot.OrientedBox> merged =
                ControllerCollisionBoxMerger.merge(List.of(
                        box(-0.5f, 0, 0, 0.5f, 0.5f, 0.5f,
                                ControllerCollisionSnapshot.Category.ENVIRONMENT),
                        box(0.5f, 0, 0, 0.5f, 0.5f, 0.5f,
                                ControllerCollisionSnapshot.Category.ENVIRONMENT)));
        require(merged.size() == 1,
                "Face-adjacent boxes on one layer should merge");
        ControllerCollisionSnapshot.OrientedBox box = merged.get(0);
        require(close(box.centerU(), 0.0) && close(box.radiusU(), 1.0),
                "Merged box should span both adjacent blocks");
    }

    private static void testRectangularLayerMergesAcrossBothAxes() {
        List<ControllerCollisionSnapshot.OrientedBox> merged =
                ControllerCollisionBoxMerger.merge(List.of(
                        box(-0.5f, -0.5f, 0, 0.5f, 0.5f, 0.5f,
                                ControllerCollisionSnapshot.Category.ENVIRONMENT),
                        box(0.5f, -0.5f, 0, 0.5f, 0.5f, 0.5f,
                                ControllerCollisionSnapshot.Category.ENVIRONMENT),
                        box(-0.5f, 0.5f, 0, 0.5f, 0.5f, 0.5f,
                                ControllerCollisionSnapshot.Category.ENVIRONMENT),
                        box(0.5f, 0.5f, 0, 0.5f, 0.5f, 0.5f,
                                ControllerCollisionSnapshot.Category.ENVIRONMENT)));
        require(merged.size() == 1,
                "A filled rectangular layer should become one shape");
        ControllerCollisionSnapshot.OrientedBox box = merged.get(0);
        require(close(box.radiusU(), 1.0) && close(box.radiusV(), 1.0),
                "Merged rectangle should retain its full outer bounds");
    }

    private static void testDepthLayersRemainSeparate() {
        List<ControllerCollisionSnapshot.OrientedBox> merged =
                ControllerCollisionBoxMerger.merge(List.of(
                        box(0, 0, -0.5f, 0.5f, 0.5f, 0.5f,
                                ControllerCollisionSnapshot.Category.ENVIRONMENT),
                        box(0, 0, 0.5f, 0.5f, 0.5f, 0.5f,
                                ControllerCollisionSnapshot.Category.ENVIRONMENT)));
        require(merged.size() == 2,
                "Front-to-back boxes must retain separate depth layers");
    }

    private static void testDifferentShapesAndCategoriesRemainSeparate() {
        List<ControllerCollisionSnapshot.OrientedBox> differentHeight =
                ControllerCollisionBoxMerger.merge(List.of(
                        box(-0.5f, 0, 0, 0.5f, 0.5f, 0.5f,
                                ControllerCollisionSnapshot.Category.ENVIRONMENT),
                        box(0.5f, 0, 0, 0.5f, 0.25f, 0.5f,
                                ControllerCollisionSnapshot.Category.ENVIRONMENT)));
        require(differentHeight.size() == 2,
                "Different collision extents must not fill missing space");

        List<ControllerCollisionSnapshot.OrientedBox> differentCategory =
                ControllerCollisionBoxMerger.merge(List.of(
                        box(-0.5f, 0, 0, 0.5f, 0.5f, 0.5f,
                                ControllerCollisionSnapshot.Category.ENVIRONMENT),
                        box(0.5f, 0, 0, 0.5f, 0.5f, 0.5f,
                                ControllerCollisionSnapshot.Category.CANNON)));
        require(differentCategory.size() == 2,
                "Environment and cannon wireframes must remain distinct");
    }

    private static void testFullWallOccludesDeeperCannon() {
        ControllerCollisionSnapshot snapshot = snapshot(List.of(
                box(0, 0, 0.5f, 2.0f, 2.0f, 0.25f,
                        ControllerCollisionSnapshot.Category.ENVIRONMENT),
                box(0, 0, 2.0f, 0.5f, 0.5f, 0.25f,
                        ControllerCollisionSnapshot.Category.CANNON)));
        ControllerCollisionProjection.ProjectedView view =
                ControllerCollisionProjection.project(snapshot);
        require(view.runs().stream().noneMatch(run -> run.category()
                        == ControllerCollisionSnapshot.Category.CANNON),
                "Full near wall must hide the deeper cannon wireframe");
    }

    private static void testEnvironmentFilterRevealsDeeperCannon() {
        ControllerCollisionSnapshot snapshot = snapshot(List.of(
                box(0, 0, 0.5f, 2.0f, 2.0f, 0.25f,
                        ControllerCollisionSnapshot.Category.ENVIRONMENT),
                box(0, 0, 2.0f, 0.5f, 0.5f, 0.25f,
                        ControllerCollisionSnapshot.Category.CANNON)));
        ControllerCollisionProjection.ProjectedView view =
                ControllerCollisionProjection.project(snapshot, 101,
                        category -> category
                                == ControllerCollisionSnapshot.Category.CANNON);
        require(view.runs().stream().anyMatch(run -> run.category()
                        == ControllerCollisionSnapshot.Category.CANNON),
                "Hidden surroundings must stop occluding the cannon");
        require(view.runs().stream().noneMatch(run -> run.category()
                        == ControllerCollisionSnapshot.Category.ENVIRONMENT),
                "Disabled surroundings must not produce environment lines");
    }

    private static void testGapRevealsDeeperCannon() {
        ControllerCollisionSnapshot snapshot = snapshot(List.of(
                box(-1.5f, 0, 0.5f, 0.45f, 2.0f, 0.25f,
                        ControllerCollisionSnapshot.Category.ENVIRONMENT),
                box(1.5f, 0, 0.5f, 0.45f, 2.0f, 0.25f,
                        ControllerCollisionSnapshot.Category.ENVIRONMENT),
                box(0, 0, 2.0f, 0.5f, 0.5f, 0.25f,
                        ControllerCollisionSnapshot.Category.CANNON)));
        ControllerCollisionProjection.ProjectedView view =
                ControllerCollisionProjection.project(snapshot);
        require(view.runs().stream().anyMatch(run -> run.category()
                        == ControllerCollisionSnapshot.Category.CANNON),
                "Collision-shape gap must reveal the deeper cannon");
    }

    private static void testProjectionRejectsGeometryBehindView() {
        ControllerCollisionSnapshot snapshot = snapshot(List.of(
                box(0, 0, -1.0f, 0.5f, 0.5f, 0.25f,
                        ControllerCollisionSnapshot.Category.ENVIRONMENT),
                box(1, 0, 6.0f, 0.5f, 0.5f, 0.25f,
                        ControllerCollisionSnapshot.Category.ENVIRONMENT)));
        ControllerCollisionProjection.ProjectedView view =
                ControllerCollisionProjection.project(snapshot, 101);
        require(view.runs().isEmpty(),
                "Projection must reject geometry outside forward depth");
    }

    private static void testNearPlaneCapOccludesDeeperGeometry() {
        ControllerCollisionSnapshot snapshot = snapshot(List.of(
                box(0, 0, 0.0f, 2.0f, 2.0f, 0.5f,
                        ControllerCollisionSnapshot.Category.ENVIRONMENT),
                box(0, 0, 2.0f, 0.5f, 0.5f, 0.25f,
                        ControllerCollisionSnapshot.Category.CANNON)));
        ControllerCollisionProjection.ProjectedView view =
                ControllerCollisionProjection.project(snapshot, 101);
        require(view.runs().stream().noneMatch(run -> run.category()
                        == ControllerCollisionSnapshot.Category.CANNON),
                "A near-plane clipping cap must occlude deeper geometry");
    }

    private static void testUnequalStackedBoxesHaveNoInternalSeam() {
        ControllerCollisionSnapshot snapshot = snapshot(List.of(
                box(0, -0.5f, 1.0f, 2.0f, 0.5f, 0.5f,
                        ControllerCollisionSnapshot.Category.ENVIRONMENT),
                box(0, 0.5f, 1.0f, 1.5f, 0.5f, 0.5f,
                        ControllerCollisionSnapshot.Category.ENVIRONMENT)));
        ControllerCollisionProjection.ProjectedView view =
                ControllerCollisionProjection.project(snapshot, 101);
        require(!hasLineAt(view, 50, 50),
                "Coplanar stacked boxes must not draw an internal seam");
        require(hasLineAt(view, 32, 50) || hasLineAt(view, 33, 50),
                "A real step in the combined silhouette must remain visible");
    }

    private static void testDepthBreakRemainsVisible() {
        ControllerCollisionSnapshot snapshot = snapshot(List.of(
                box(-0.5f, 0, 0.75f, 0.5f, 0.5f, 0.25f,
                        ControllerCollisionSnapshot.Category.ENVIRONMENT),
                box(0.5f, 0, 1.75f, 0.5f, 0.5f, 0.25f,
                        ControllerCollisionSnapshot.Category.ENVIRONMENT)));
        ControllerCollisionProjection.ProjectedView view =
                ControllerCollisionProjection.project(snapshot, 101);
        require(hasLineAt(view, 49, 50) || hasLineAt(view, 50, 50),
                "A visible depth discontinuity must retain its boundary");
    }

    private static void testRequestedProjectionResolutionIsRetained() {
        ControllerCollisionProjection.ProjectedView view =
                ControllerCollisionProjection.project(snapshot(List.of(
                        box(0, 0, 1.0f, 0.5f, 0.5f, 0.5f,
                                ControllerCollisionSnapshot.Category.ENVIRONMENT))),
                        173);
        require(view.resolution() == 173,
                "Projection must match the GUI display resolution");
    }

    private static boolean hasLineAt(
            ControllerCollisionProjection.ProjectedView view, int x, int y
    ) {
        return view.runs().stream().anyMatch(run -> run.y() == y
                && run.startX() <= x && run.endXExclusive() > x);
    }

    private static ControllerCollisionSnapshot snapshot(
            List<ControllerCollisionSnapshot.OrientedBox> boxes
    ) {
        return new ControllerCollisionSnapshot(
                ControllerCollisionSnapshot.Status.OK, CannonAxis.PITCH,
                5.0f, 5.0f, 0.0f, 0.0f, 0.0f, -90.0, 90.0,
                false, false, boxes);
    }

    private static ControllerCollisionSnapshot.OrientedBox box(
            float u, float v, float depth,
            float halfU, float halfV, float halfDepth,
            ControllerCollisionSnapshot.Category category
    ) {
        return new ControllerCollisionSnapshot.OrientedBox(
                u, v, depth,
                halfU, 0, 0,
                0, halfV, 0,
                0, 0, halfDepth,
                category);
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) <= EPSILON;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
