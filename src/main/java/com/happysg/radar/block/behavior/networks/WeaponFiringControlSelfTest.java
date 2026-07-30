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
    private WeaponFiringControlSelfTest() {
    }

    public static List<TargetingSolverSelfTest.Result> runChecks() {
        List<TargetingSolverSelfTest.Result> results = new ArrayList<>();
        results.add(checkObservationRefreshKeepsIdentity());
        results.add(checkIdentityChangesInvalidate());
        results.add(checkAimPointRebase());
        results.add(checkPendingSolveLifetime());
        results.add(checkOrdinaryMotionKeepsPendingSolve());
        results.add(checkFireFreshnessWindow());
        results.add(checkAsyncBallisticAimHandoff());
        results.add(checkSmoothAimKeepsStability());
        results.add(checkDualMountYawConvergence());
        results.add(checkDualFirePolicy());
        results.add(checkDualYawTopologyPolicy());
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

    private static TargetingSolverSelfTest.Result
    checkAimPointRebase() {
        Vec3 requestedTarget = new Vec3(10.0, 2.0, -4.0);
        Vec3 solvedAim = new Vec3(15.0, 3.0, -1.0);
        Vec3 liveTarget = new Vec3(11.0, 2.5, -6.0);
        Vec3 rebased = WeaponFiringControl.rebaseAimPoint(
                solvedAim, requestedTarget, liveTarget);
        Vec3 expected = new Vec3(16.0, 3.5, -3.0);
        boolean passed = rebased.distanceToSqr(expected) < 1.0E-12;
        return result("async_cached_aim_rebases_continuously", passed,
                "rebased=" + rebased + " expected=" + expected);
    }

    private static TargetingSolverSelfTest.Result
    checkPendingSolveLifetime() {
        boolean fresh = WeaponFiringControl.shouldKeepPendingSolve(
                99L, true, true);
        boolean expired = !WeaponFiringControl.shouldKeepPendingSolve(
                101L, true, true);
        boolean targetChanged = !WeaponFiringControl.shouldKeepPendingSolve(
                1L, false, true);
        boolean shotChanged = !WeaponFiringControl.shouldKeepPendingSolve(
                1L, true, false);
        boolean passed = fresh && expired && targetChanged
                && shotChanged;
        return result("async_solve_watchdog_lifetime", passed,
                "fresh=" + fresh + " expired=" + expired
                        + " targetChanged=" + targetChanged
                        + " shotChanged=" + shotChanged);
    }

    private static TargetingSolverSelfTest.Result
    checkOrdinaryMotionKeepsPendingSolve() {
        boolean accelerationKept =
                WeaponFiringControl.shouldKeepPendingSolve(
                        1L, true, true);
        boolean directionChangeKept =
                WeaponFiringControl.shouldKeepPendingSolve(
                        4L, true, true);
        boolean stopKept =
                WeaponFiringControl.shouldKeepPendingSolve(
                        8L, true, true);
        boolean passed = accelerationKept
                && directionChangeKept && stopKept;
        return result("async_solve_ordinary_motion_not_cancelled", passed,
                "acceleration=" + accelerationKept
                        + " directionChange=" + directionChangeKept
                        + " stop=" + stopKept);
    }

    private static TargetingSolverSelfTest.Result
    checkFireFreshnessWindow() {
        boolean fresh = WeaponFiringControl.isAsyncSolutionFreshForFire(
                4L, 4.0, 1.0);
        boolean old = !WeaponFiringControl.isAsyncSolutionFreshForFire(
                5L, 0.0, 0.0);
        boolean targetMoved =
                !WeaponFiringControl.isAsyncSolutionFreshForFire(
                        1L, 4.01, 0.0);
        boolean muzzleMoved =
                !WeaponFiringControl.isAsyncSolutionFreshForFire(
                        1L, 0.0, 1.01);
        boolean passed = fresh && old && targetMoved && muzzleMoved;
        return result("async_fire_freshness_fails_closed", passed,
                "fresh=" + fresh + " old=" + old
                        + " targetMoved=" + targetMoved
                        + " muzzleMoved=" + muzzleMoved);
    }

    private static TargetingSolverSelfTest.Result
    checkAsyncBallisticAimHandoff() {
        WeaponFiringControl.AimUpdateMode firstSolution =
                WeaponFiringControl.selectAimUpdateMode(true, true);
        WeaponFiringControl.AimUpdateMode pending =
                WeaponFiringControl.selectAimUpdateMode(true, true);
        WeaponFiringControl.AimUpdateMode nextSolution =
                WeaponFiringControl.selectAimUpdateMode(true, true);
        WeaponFiringControl.AimUpdateMode initialWait =
                WeaponFiringControl.selectAimUpdateMode(false, true);
        WeaponFiringControl.AimUpdateMode intentionalDirect =
                WeaponFiringControl.selectAimUpdateMode(false, false);

        boolean passed =
                firstSolution == WeaponFiringControl.AimUpdateMode.SOLVED
                && pending == WeaponFiringControl.AimUpdateMode.SOLVED
                && nextSolution == WeaponFiringControl.AimUpdateMode.SOLVED
                && initialWait == WeaponFiringControl.AimUpdateMode.HOLD
                && intentionalDirect == WeaponFiringControl.AimUpdateMode.DIRECT
                && WeaponFiringControl.shouldIssueAimCommand(pending)
                && !WeaponFiringControl.shouldIssueAimCommand(initialWait)
                && WeaponFiringControl.shouldIssueAimCommand(firstSolution)
                && WeaponFiringControl.shouldIssueAimCommand(intentionalDirect)
                && !WeaponFiringControl.hasFireEligibleAim(
                        false, false, false)
                && WeaponFiringControl.hasFireEligibleAim(
                        false, true, false)
                && WeaponFiringControl.hasFireEligibleAim(
                        true, false, false)
                && WeaponFiringControl.hasFireEligibleAim(
                        false, false, true);
        return result("async_ballistic_gap_continues_aim", passed,
                "timeline=" + firstSolution + "->" + pending + "->"
                        + nextSolution + " initial=" + initialWait
                        + " direct=" + intentionalDirect);
    }

    private static TargetingSolverSelfTest.Result
    checkSmoothAimKeepsStability() {
        Vec3 previous = Vec3.ZERO;
        int stableTicks = 0;
        for (int tick = 1; tick <= 8; ++tick) {
            Vec3 current = new Vec3(tick * 0.2, 0.0, 0.0);
            stableTicks = WeaponFiringControl.nextAimStableTicks(
                    previous, current, stableTicks, 0.5);
            previous = current;
        }
        int jumpReset = WeaponFiringControl.nextAimStableTicks(
                previous, previous.add(1.0, 0.0, 0.0),
                stableTicks, 0.5);
        boolean passed = stableTicks == 8 && jumpReset == 0;
        return result("smooth_aim_keeps_fire_stability", passed,
                "smoothTicks=" + stableTicks
                        + " jumpReset=" + jumpReset);
    }

    private static TargetingSolverSelfTest.Result
    checkDualMountYawConvergence() {
        Vec3 leftOrigin = new Vec3(-2.0, 0.0, 0.0);
        Vec3 rightOrigin = new Vec3(2.0, 0.0, 0.0);
        Vec3 aimPoint = new Vec3(0.0, 0.0, 100.0);
        Vec3 rightAxis = new Vec3(1.0, 0.0, 0.0);
        Vec3 upAxis = new Vec3(0.0, 1.0, 0.0);
        Vec3 forwardAxis = new Vec3(0.0, 0.0, 1.0);

        Double leftYaw =
                WeaponFiringControl.calculateControllerYawForFrame(
                        leftOrigin, aimPoint,
                        rightAxis, upAxis, forwardAxis);
        Double rightYaw =
                WeaponFiringControl.calculateControllerYawForFrame(
                        rightOrigin, aimPoint,
                        rightAxis, upAxis, forwardAxis);
        boolean finite = leftYaw != null && rightYaw != null
                && Double.isFinite(leftYaw)
                && Double.isFinite(rightYaw);
        double separation = finite
                ? Math.abs(shortestDelta(leftYaw, rightYaw))
                : 0.0;
        boolean oppositeCorrections = finite
                && Math.abs(shortestDelta(0.0, leftYaw)
                + shortestDelta(0.0, rightYaw)) < 1.0e-6;
        boolean passed = finite && separation > 2.0
                && oppositeCorrections;
        return result("t_pitch_per_mount_yaw_convergence", passed,
                "left=" + leftYaw + " right=" + rightYaw
                        + " separation=" + separation);
    }

    private static TargetingSolverSelfTest.Result
    checkDualFirePolicy() {
        boolean allReady =
                WeaponFiringControl.dualSideFireEligible(
                        true, true, true,
                        true, true, true);
        boolean yawBlocksOnlySide =
                !WeaponFiringControl.dualSideFireEligible(
                        true, true, false,
                        true, true, true);
        boolean profileBlocksOnlySide =
                !WeaponFiringControl.dualSideFireEligible(
                        true, true, true,
                        true, true, false);
        boolean sharedGateStops =
                !WeaponFiringControl.dualSideFireEligible(
                        false, true, true,
                        true, true, true);
        boolean passed = allReady && yawBlocksOnlySide
                && profileBlocksOnlySide && sharedGateStops;
        return result("t_pitch_independent_fire_gates", passed,
                "ready=" + allReady
                        + " yawBlocked=" + yawBlocksOnlySide
                        + " profileBlocked=" + profileBlocksOnlySide
                        + " sharedBlocked=" + sharedGateStops);
    }

    private static TargetingSolverSelfTest.Result
    checkDualYawTopologyPolicy() {
        boolean oneDirect =
                WeaponFiringControl.selectDualYawMode(1, 0)
                == WeaponFiringControl.DualYawMode.PER_MOUNT;
        boolean twoDirect =
                WeaponFiringControl.selectDualYawMode(2, 0)
                == WeaponFiringControl.DualYawMode.PER_MOUNT;
        boolean oneSwivel =
                WeaponFiringControl.selectDualYawMode(1, 1)
                == WeaponFiringControl.DualYawMode.SHARED_STRUCTURAL;
        boolean mixedRejected =
                WeaponFiringControl.selectDualYawMode(2, 1)
                == WeaponFiringControl.DualYawMode.INVALID_MIXED;
        boolean passed = oneDirect && twoDirect
                && oneSwivel && mixedRejected;
        return result("t_pitch_shared_swivel_yaw_policy", passed,
                "oneDirect=" + oneDirect
                        + " twoDirect=" + twoDirect
                        + " oneSwivel=" + oneSwivel
                        + " mixedRejected=" + mixedRejected);
    }

    private static double shortestDelta(double from, double to) {
        return (to - from + 540.0) % 360.0 - 180.0;
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
