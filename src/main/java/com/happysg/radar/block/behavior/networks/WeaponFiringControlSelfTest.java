package com.happysg.radar.block.behavior.networks;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.targeting.TargetingSolverSelfTest;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Deterministic checks for the target and async-solve lifecycle. These run as
 * part of the existing targeting solver self-test command.
 */
public final class WeaponFiringControlSelfTest {
    private static final double EPSILON_SQUARED = 1.0e-12;

    private WeaponFiringControlSelfTest() {
    }

    public static List<TargetingSolverSelfTest.Result> runChecks() {
        List<TargetingSolverSelfTest.Result> results = new ArrayList<>();
        results.add(checkObservationRefreshKeepsIdentity());
        results.add(checkIdentityChangesInvalidate());
        results.add(checkDelayedSolveIsKept());
        results.add(checkSolveWatchdogAndGeneration());
        results.add(checkConventionalPendingPolicy());
        results.add(checkDelayedSolveCannotFire());
        results.add(checkStructuralMotionTransitions());
        results.add(checkStructuralMotionJitter());
        results.add(checkStructuralProvisionalPolicy());
        results.add(checkStructuralSlewStabilityGate());
        results.add(checkProvisionalAimTranslation());
        results.add(checkProvisionalAimWithoutSolution());
        return List.copyOf(results);
    }

    private static TargetingSolverSelfTest.Result
    checkObservationRefreshKeepsIdentity() {
        RadarTrack original = track("mob-1", TrackCategory.HOSTILE,
                Vec3.ZERO, 20L);
        RadarTrack refreshed = track("mob-1", TrackCategory.HOSTILE,
                new Vec3(12.0, 3.0, -4.0), 25L);
        boolean passed = WeaponFiringControl.sameTargetIdentity(
                original, refreshed);
        return result("swivel_same_target_observation_refresh", passed,
                "sameIdentity=" + passed);
    }

    private static TargetingSolverSelfTest.Result
    checkIdentityChangesInvalidate() {
        RadarTrack original = track("mob-1", TrackCategory.HOSTILE,
                Vec3.ZERO, 20L);
        RadarTrack differentId = track("mob-2", TrackCategory.HOSTILE,
                Vec3.ZERO, 25L);
        RadarTrack differentCategory = track("mob-1", TrackCategory.PLAYER,
                Vec3.ZERO, 25L);
        boolean passed = !WeaponFiringControl.sameTargetIdentity(
                original, differentId)
                && !WeaponFiringControl.sameTargetIdentity(
                original, differentCategory);
        return result("swivel_target_identity_change", passed,
                "differentIdAndCategoryRejected=" + passed);
    }

    private static TargetingSolverSelfTest.Result checkDelayedSolveIsKept() {
        boolean passed = WeaponFiringControl.shouldKeepPendingSolve(
                5L, true, true)
                && WeaponFiringControl.shouldKeepPendingSolve(
                100L, true, true);
        return result("swivel_delayed_solve_preserved", passed,
                "age5And100Kept=" + passed);
    }

    private static TargetingSolverSelfTest.Result
    checkSolveWatchdogAndGeneration() {
        boolean passed = !WeaponFiringControl.shouldKeepPendingSolve(
                101L, true, true)
                && !WeaponFiringControl.shouldKeepPendingSolve(
                5L, false, true)
                && !WeaponFiringControl.shouldKeepPendingSolve(
                5L, true, false);
        return result("swivel_solve_supersession", passed,
                "timeoutGenerationAndShotRejected=" + passed);
    }

    private static TargetingSolverSelfTest.Result
    checkDelayedSolveCannotFire() {
        boolean fresh = WeaponFiringControl.isAsyncSolutionFreshForFire(
                4L, 4.0, 1.0);
        boolean passed = fresh
                && !WeaponFiringControl.isAsyncSolutionFreshForFire(
                5L, 0.0, 0.0)
                && !WeaponFiringControl.isAsyncSolutionFreshForFire(
                1L, 4.01, 0.0)
                && !WeaponFiringControl.isAsyncSolutionFreshForFire(
                1L, 0.0, 1.01);
        return result("swivel_delayed_solve_fire_gate", passed,
                "freshBoundary=" + fresh + " delayedAndMovedRejected="
                        + passed);
    }

    private static TargetingSolverSelfTest.Result
    checkConventionalPendingPolicy() {
        boolean passed =
                WeaponFiringControl.shouldKeepConventionalPendingSolve(
                        3L, true, 4.0, true)
                && !WeaponFiringControl.shouldKeepConventionalPendingSolve(
                        4L, true, 0.0, true)
                && !WeaponFiringControl.shouldKeepConventionalPendingSolve(
                        1L, false, 0.0, true)
                && !WeaponFiringControl.shouldKeepConventionalPendingSolve(
                        1L, true, 4.01, true)
                && !WeaponFiringControl.shouldKeepConventionalPendingSolve(
                        1L, true, 0.0, false);
        return result("cbc_async_policy_preserved", passed,
                "ageTargetMovementAndShotBoundaries=" + passed);
    }

    private static TargetingSolverSelfTest.Result
    checkStructuralMotionTransitions() {
        Vec3 east = new Vec3(0.25, 0.0, 0.0);
        Vec3 west = new Vec3(-0.25, 0.0, 0.0);
        Vec3 northEast = new Vec3(0.25, 0.0, 0.1);
        boolean stopped =
                WeaponFiringControl.isStructuralMotionDiscontinuity(
                        east, true, Vec3.ZERO, false, 1L);
        boolean started =
                WeaponFiringControl.isStructuralMotionDiscontinuity(
                        Vec3.ZERO, false, east, true, 1L);
        boolean reversed =
                WeaponFiringControl.isStructuralMotionDiscontinuity(
                        east, true, west, true, 1L);
        boolean continuous =
                !WeaponFiringControl.isStructuralMotionDiscontinuity(
                        east, true, northEast, true, 1L);
        boolean sampleGap =
                WeaponFiringControl.isStructuralMotionDiscontinuity(
                        east, true, east, true, 2L);
        boolean passed = stopped && started && reversed
                && continuous && sampleGap;
        return result("swivel_motion_revision_transitions", passed,
                "stop=" + stopped + " start=" + started
                        + " reverse=" + reversed
                        + " continuous=" + continuous
                        + " gap=" + sampleGap);
    }

    private static TargetingSolverSelfTest.Result
    checkStructuralMotionJitter() {
        Vec3 boundaryJitter = new Vec3(1.0e-4, 0.0, 0.0);
        Vec3 materialMotion = new Vec3(1.01e-4, 0.0, 0.0);
        boolean jitterStationary =
                !WeaponFiringControl.isObservedMoving(boundaryJitter);
        boolean materialMoving =
                WeaponFiringControl.isObservedMoving(materialMotion);
        boolean noStationaryRevision =
                !WeaponFiringControl.isStructuralMotionDiscontinuity(
                        Vec3.ZERO, false, boundaryJitter,
                        false, 1L);
        boolean passed = jitterStationary && materialMoving
                && noStationaryRevision;
        return result("swivel_motion_jitter_guard", passed,
                "jitterStationary=" + jitterStationary
                        + " materialMoving=" + materialMoving
                        + " noRevision=" + noStationaryRevision);
    }

    private static TargetingSolverSelfTest.Result
    checkStructuralProvisionalPolicy() {
        boolean exactFresh =
                !WeaponFiringControl.isStructuralSteeringProvisional(
                        4L, 4.0);
        boolean delayed =
                WeaponFiringControl.isStructuralSteeringProvisional(
                        5L, 0.0);
        boolean farMoved =
                WeaponFiringControl.isStructuralSteeringProvisional(
                        1L, 4.01);
        boolean passed = exactFresh && delayed && farMoved;
        return result("swivel_provisional_fire_policy", passed,
                "exactFresh=" + exactFresh + " delayed=" + delayed
                        + " farMoved=" + farMoved);
    }

    private static TargetingSolverSelfTest.Result
    checkStructuralSlewStabilityGate() {
        boolean conventional =
                WeaponFiringControl.shouldApplyStructuralSlewPrediction(
                        false, true, 1);
        boolean transitionBlocked =
                !WeaponFiringControl.shouldApplyStructuralSlewPrediction(
                        true, true, 3);
        boolean warmingBlocked =
                !WeaponFiringControl.shouldApplyStructuralSlewPrediction(
                        true, false, 2);
        boolean stableAllowed =
                WeaponFiringControl.shouldApplyStructuralSlewPrediction(
                        true, false, 3);
        boolean passed = conventional && transitionBlocked
                && warmingBlocked && stableAllowed;
        return result("swivel_slew_stability_gate", passed,
                "conventional=" + conventional
                        + " transitionBlocked=" + transitionBlocked
                        + " warmingBlocked=" + warmingBlocked
                        + " stableAllowed=" + stableAllowed);
    }

    private static TargetingSolverSelfTest.Result
    checkProvisionalAimTranslation() {
        Vec3 validatedAim = new Vec3(100.0, 8.0, 12.0);
        Vec3 validatedTarget = new Vec3(100.0, 2.0, 0.0);
        Vec3 liveTarget = new Vec3(112.0, 5.0, -3.0);
        Vec3 expected = new Vec3(112.0, 11.0, 9.0);
        Vec3 actual = WeaponFiringControl.provisionalAimPoint(
                validatedAim, validatedTarget, liveTarget);
        boolean passed = actual.distanceToSqr(expected) <= EPSILON_SQUARED;
        return result("swivel_provisional_aim_translation", passed,
                "actual=" + actual + " expected=" + expected);
    }

    private static TargetingSolverSelfTest.Result
    checkProvisionalAimWithoutSolution() {
        Vec3 liveTarget = new Vec3(30.0, 7.0, -2.0);
        Vec3 actual = WeaponFiringControl.provisionalAimPoint(
                null, null, liveTarget);
        boolean passed = actual.distanceToSqr(liveTarget)
                <= EPSILON_SQUARED;
        return result("swivel_provisional_direct_aim", passed,
                "actual=" + actual);
    }

    private static RadarTrack track(String id, TrackCategory category,
                                    Vec3 position, long scannedTime) {
        return new RadarTrack(id, position, Vec3.ZERO, scannedTime,
                category, "test", 1.0F);
    }

    private static TargetingSolverSelfTest.Result result(
            String name, boolean passed, String detail) {
        return new TargetingSolverSelfTest.Result(name, passed, detail);
    }
}
