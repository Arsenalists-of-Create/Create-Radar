package com.happysg.radar.compat.sable;

import com.happysg.radar.targeting.TargetingSolverSelfTest;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Deterministic, world-independent checks for occupied hull aim selection. */
public final class SubLevelAimResolverSelfTest {
    private SubLevelAimResolverSelfTest() {
    }

    public static List<TargetingSolverSelfTest.Result> runChecks() {
        return List.of(
                checkDeadCenterAirIsAvoided(),
                checkBlockedCandidateFallsThrough(),
                checkOppositeCannonsUseOppositeFaces(),
                checkOrderingIsStable(),
                checkFallbackSnapshotsAreNotDetailed());
    }

    private static TargetingSolverSelfTest.Result
    checkDeadCenterAirIsAvoided() {
        List<SubLevelSilhouette.LocalBox> boxes = hollowHull();
        SubLevelAimResolver.LocalAim selected = SubLevelAimResolver.select(
                boxes, new Vec3(-20.0, 0.5, 0.0), 64,
                ignored -> true);
        boolean passed = selected != null
                && selected.point().x < -4.0
                && selected.box().minX() == -5.0;
        return result("sublevel_aim_avoids_empty_center", passed,
                "selected=" + selected);
    }

    private static TargetingSolverSelfTest.Result
    checkBlockedCandidateFallsThrough() {
        List<SubLevelSilhouette.LocalBox> boxes = hollowHull();
        SubLevelAimResolver.LocalAim selected = SubLevelAimResolver.select(
                boxes, new Vec3(-20.0, 0.5, 0.0), 64,
                candidate -> candidate.box().minX() > 0.0);
        boolean passed = selected != null
                && selected.box().minX() == 4.0;
        return result("sublevel_aim_uses_visible_alternate", passed,
                "selected=" + selected);
    }

    private static TargetingSolverSelfTest.Result
    checkOppositeCannonsUseOppositeFaces() {
        SubLevelSilhouette.LocalBox box =
                new SubLevelSilhouette.LocalBox(
                        -1.0, -1.0, -1.0, 1.0, 1.0, 1.0);
        SubLevelAimResolver.LocalAim left = SubLevelAimResolver.select(
                List.of(box), new Vec3(-20.0, 0.0, 0.0), 64,
                ignored -> true);
        SubLevelAimResolver.LocalAim right = SubLevelAimResolver.select(
                List.of(box), new Vec3(20.0, 0.0, 0.0), 64,
                ignored -> true);
        boolean passed = left != null && right != null
                && left.point().x < -0.9
                && right.point().x > 0.9;
        return result("sublevel_aim_is_weapon_specific", passed,
                "left=" + left + " right=" + right);
    }

    private static TargetingSolverSelfTest.Result checkOrderingIsStable() {
        List<SubLevelSilhouette.LocalBox> boxes = new ArrayList<>(hollowHull());
        SubLevelAimResolver.LocalAim first = SubLevelAimResolver.select(
                boxes, new Vec3(0.0, 10.0, -20.0), 64,
                ignored -> true);
        Collections.reverse(boxes);
        SubLevelAimResolver.LocalAim reversed = SubLevelAimResolver.select(
                boxes, new Vec3(0.0, 10.0, -20.0), 64,
                ignored -> true);
        boolean passed = first != null && first.equals(reversed);
        return result("sublevel_aim_order_is_stable", passed,
                "first=" + first + " reversed=" + reversed);
    }

    private static TargetingSolverSelfTest.Result
    checkFallbackSnapshotsAreNotDetailed() {
        SubLevelSilhouette detailed = SubLevelSilhouette.of(hollowHull());
        boolean ready = new SableSilhouetteServerCache.Snapshot(
                detailed, 3, SableSilhouetteStatus.READY)
                .hasDetailedHull();
        boolean fallback = new SableSilhouetteServerCache.Snapshot(
                detailed, 4, SableSilhouetteStatus.FALLBACK)
                .hasDetailedHull();
        boolean failed = new SableSilhouetteServerCache.Snapshot(
                null, 5, SableSilhouetteStatus.FAILED)
                .hasDetailedHull();
        boolean passed = ready && !fallback && !failed;
        return result("sublevel_aim_center_fallback_policy", passed,
                "ready=" + ready + " fallback=" + fallback
                        + " failed=" + failed);
    }

    private static List<SubLevelSilhouette.LocalBox> hollowHull() {
        return List.of(
                new SubLevelSilhouette.LocalBox(
                        -5.0, 0.0, -1.0, -4.0, 1.0, 1.0),
                new SubLevelSilhouette.LocalBox(
                        4.0, 0.0, -1.0, 5.0, 1.0, 1.0));
    }

    private static TargetingSolverSelfTest.Result result(
            String name, boolean passed, String detail) {
        return new TargetingSolverSelfTest.Result(name, passed, detail);
    }
}
