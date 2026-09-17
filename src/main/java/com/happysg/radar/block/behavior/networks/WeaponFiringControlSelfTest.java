package com.happysg.radar.block.behavior.networks;

import com.happysg.radar.block.controller.kinetic.CannonAxis;
import com.happysg.radar.block.controller.kinetic.KineticAimFrame;
import com.happysg.radar.block.controller.limits.ControllerMovementLimits;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.cbc.CannonUtil;
import com.happysg.radar.targeting.PitchConstraint;
import com.happysg.radar.targeting.ProjectileModel;
import com.happysg.radar.targeting.TargetingSolverSelfTest;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;

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
        results.add(checkJammingGuidanceRefresh());
        results.add(checkMutableJammingSampleRefresh());
        results.add(checkGuidanceAabbTranslation());
        results.add(checkAimPointRebase());
        results.add(checkAimDirectionRebase());
        results.add(checkPendingSolveLifetime());
        results.add(checkBigCannonFingerprintTracksLiveProperties());
        results.add(checkOrdinaryMotionKeepsPendingSolve());
        results.add(checkFireFreshnessWindow());
        results.add(checkAsyncBallisticAimHandoff());
        results.add(checkDirectAdapterAimGating());
        results.add(checkSublevelFrameTracksCarrierRotation());
        results.add(checkNestedSwivelYawLimits());
        results.add(checkStructuralPitchLimits());
        results.add(checkLimitBoundaryTrackingFailsClosed());
        results.add(checkUnavailableSourceFrameFailsClosed());
        results.add(checkSmoothAimKeepsStability());
        results.add(checkDualMountYawConvergence());
        results.add(checkDualFirePolicy());
        results.add(checkPerSideSafeZoneGeometry());
        results.add(checkDualAimingSourceRecovery());
        results.add(checkSecondarySolutionIdentity());
        results.add(checkDualYawTopologyPolicy());
        results.add(checkRangeAwareFiringTolerance());
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
    checkJammingGuidanceRefresh() {
        RadarTrack first = track("mob-1", TrackCategory.HOSTILE,
                new Vec3(10.0, 20.0, 30.0), 20L);
        first.setJammingData(new RadarTrack.JammingData(
                "radar", 0.5f, 0.25f, 0.0f, 0.0f,
                new Vec3(2.0, -3.0, 4.0),
                new Vec3(0.1, -0.2, 0.3), 100L));
        RadarTrack next = first.copy();
        next.setJammingData(new RadarTrack.JammingData(
                "radar", 0.5f, 0.25f, 0.0f, 0.0f,
                Vec3.ZERO, Vec3.ZERO, 101L));

        WeaponFiringControl.JammedGuidance guidance =
                WeaponFiringControl.applyJammingGuidance(
                        first.position(), new Vec3(1.0, 2.0, 3.0), first);
        boolean positionShifted = guidance.position().distanceToSqr(
                new Vec3(12.0, 17.0, 34.0)) < 1.0E-12;
        boolean velocityShifted = guidance.velocity().distanceToSqr(
                new Vec3(1.1, 1.8, 3.3)) < 1.0E-12;
        boolean offsetPreserved = guidance.positionOffset().distanceToSqr(
                new Vec3(2.0, -3.0, 4.0)) < 1.0E-12;
        boolean tokenChanged = WeaponFiringControl.jammingSampleToken(first)
                != WeaponFiringControl.jammingSampleToken(next);
        boolean passed = positionShifted && velocityShifted
                && offsetPreserved && tokenChanged;
        return result("jammed_guidance_refreshes_ballistic_sample", passed,
                "position=" + guidance.position() + " velocity="
                        + guidance.velocity() + " offset="
                        + guidance.positionOffset() + " tokenChanged="
                        + tokenChanged);
    }

    private static TargetingSolverSelfTest.Result
    checkMutableJammingSampleRefresh() {
        RadarTrack shared = track("mob-1", TrackCategory.HOSTILE,
                Vec3.ZERO, 20L);
        shared.setJammingData(new RadarTrack.JammingData(
                "radar", 0.5f, 0.25f, 0.0f, 0.0f,
                Vec3.ZERO, Vec3.ZERO, 100L));
        long retainedToken = WeaponFiringControl.jammingSampleToken(shared);

        shared.setJammingData(new RadarTrack.JammingData(
                "radar", 0.5f, 0.25f, 0.0f, 0.0f,
                Vec3.ZERO, Vec3.ZERO, 101L));
        boolean aliasedMutationDetected =
                WeaponFiringControl.jammingSampleChanged(
                        true, retainedToken, shared);

        long jammedToken = WeaponFiringControl.jammingSampleToken(shared);
        shared.setJammingData(null);
        boolean jammingEndDetected =
                WeaponFiringControl.jammingSampleChanged(
                        true, jammedToken, shared);
        boolean unchangedCleanIgnored =
                !WeaponFiringControl.jammingSampleChanged(
                        true, Long.MIN_VALUE, shared);
        boolean differentTargetIgnored =
                !WeaponFiringControl.jammingSampleChanged(
                        false, retainedToken, shared);
        boolean passed = aliasedMutationDetected && jammingEndDetected
                && unchangedCleanIgnored && differentTargetIgnored;
        return result("mutable_jamming_proxy_invalidates_sample", passed,
                "aliased=" + aliasedMutationDetected
                        + " ended=" + jammingEndDetected
                        + " clean=" + unchangedCleanIgnored
                        + " different=" + differentTargetIgnored);
    }

    private static TargetingSolverSelfTest.Result
    checkGuidanceAabbTranslation() {
        AABB physical = new AABB(-1.0, -2.0, -3.0,
                1.0, 2.0, 3.0);
        Vec3 guidance = new Vec3(4.0, 5.0, 6.0);
        Vec3 prediction = new Vec3(0.5, -1.0, 2.0);
        AABB translated = WeaponFiringControl.translateTargetAabb(
                physical, guidance, prediction);
        AABB unchanged = WeaponFiringControl.translateTargetAabb(
                physical, Vec3.ZERO, Vec3.ZERO);
        AABB missing = WeaponFiringControl.translateTargetAabb(
                null, guidance, prediction);

        Vec3 expectedCenter = guidance.add(prediction);
        boolean centerAligned = translated != null
                && translated.getCenter().distanceToSqr(expectedCenter)
                < 1.0E-12;
        boolean dimensionsPreserved = translated != null
                && Math.abs(translated.getXsize() - physical.getXsize())
                < 1.0E-12
                && Math.abs(translated.getYsize() - physical.getYsize())
                < 1.0E-12
                && Math.abs(translated.getZsize() - physical.getZsize())
                < 1.0E-12;
        boolean zeroUnchanged = unchanged != null
                && unchanged.getCenter().distanceToSqr(
                physical.getCenter()) < 1.0E-12;
        boolean nullPreserved = missing == null;
        boolean passed = centerAligned && dimensionsPreserved
                && zeroUnchanged && nullPreserved;
        return result("jammed_guidance_translates_target_aabb", passed,
                "center=" + (translated == null
                        ? null : translated.getCenter())
                        + " expected=" + expectedCenter
                        + " dimensions=" + dimensionsPreserved
                        + " null=" + nullPreserved);
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
    checkRangeAwareFiringTolerance() {
        WeaponFiringControl.FiringAlignmentTolerance dual =
                WeaponFiringControl.firingAlignmentTolerance(
                        1000.0, 2.0, 2);
        WeaponFiringControl.FiringAlignmentTolerance single =
                WeaponFiringControl.firingAlignmentTolerance(
                        1000.0, 2.0, 1);
        double dualCombinedLateralError = Math.sqrt(2.0) * 1000.0
                * Math.tan(Math.toRadians(dual.maximumDegrees()));
        double singleLateralError = 1000.0
                * Math.tan(Math.toRadians(single.maximumDegrees()));
        double invalidTolerance = WeaponFiringControl
                .firingAlignmentTolerance(Double.NaN, 2.0, 2)
                .maximumDegrees();
        boolean passed = Math.abs(dualCombinedLateralError - 2.0) < 1.0E-9
                && Math.abs(singleLateralError - 2.0) < 1.0E-9
                && dual.maximumDegrees() < 2.9
                && dual.axisCount() == 2
                && invalidTolerance == 0.0;
        return result("range_aware_firing_tolerance",
                passed,
                "dualDegrees=" + dual.maximumDegrees()
                        + " dualCombinedError=" + dualCombinedLateralError
                        + " singleDegrees=" + single.maximumDegrees()
                        + " singleError=" + singleLateralError);
    }

    private static TargetingSolverSelfTest.Result
    checkAimDirectionRebase() {
        Vec3 requestedOrigin = Vec3.ZERO;
        Vec3 originalAimPoint = new Vec3(10.0, 0.0, 1.0);
        Vec3 originalDirection =
                new Vec3(10.0, 1.0, 1.0).normalize();
        Vec3 targetTranslation =
                new Vec3(0.0, 0.0, 0.2);
        Vec3 translatedAimPoint =
                originalAimPoint.add(targetTranslation);

        Vec3 unchanged =
                WeaponFiringControl.rebaseAimDirection(
                        originalDirection, originalAimPoint,
                        requestedOrigin, requestedOrigin,
                        originalAimPoint);
        Vec3 rebased =
                WeaponFiringControl.rebaseAimDirection(
                        originalDirection, originalAimPoint,
                        requestedOrigin, requestedOrigin,
                        translatedAimPoint);

        double rayDistance =
                requestedOrigin.distanceTo(originalAimPoint);
        Vec3 correction = requestedOrigin.add(
                originalDirection.scale(rayDistance))
                .subtract(originalAimPoint);
        Vec3 expected = translatedAimPoint.add(correction)
                .subtract(requestedOrigin).normalize();
        Vec3 lineOfSight = translatedAimPoint
                .subtract(requestedOrigin).normalize();
        boolean passed = unchanged != null && rebased != null
                && unchanged.distanceToSqr(originalDirection)
                < 1.0E-12
                && rebased.distanceToSqr(expected) < 1.0E-12
                && rebased.y > lineOfSight.y;
        return result(
                "async_rebase_preserves_ballistic_correction",
                passed, "unchanged=" + unchanged
                        + " rebased=" + rebased
                        + " expected=" + expected
                        + " los=" + lineOfSight);
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
        boolean originMoved =
                !WeaponFiringControl.isAsyncSolutionFreshForFire(
                        1L, 0.0, 1.01);
        boolean passed = fresh && old && targetMoved && originMoved;
        return result("async_fire_freshness_fails_closed", passed,
                "fresh=" + fresh + " old=" + old
                        + " targetMoved=" + targetMoved
                        + " originMoved=" + originMoved);
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
    checkBigCannonFingerprintTracksLiveProperties() {
        BallisticPropertiesComponent baselineBallistics =
                new BallisticPropertiesComponent(
                        -0.05, 0.01, false,
                        2.0F, 1.0F, 1.0F, 0.7F);
        BallisticPropertiesComponent changedBallistics =
                new BallisticPropertiesComponent(
                        -0.08, 0.02, true,
                        2.0F, 1.0F, 1.0F, 0.7F);
        CannonUtil.BigCannonShotState baseline = bigCannonShot(
                2.0F, baselineBallistics, 2.0F);
        CannonUtil.BigCannonShotState changedPropellant = bigCannonShot(
                10.0F, baselineBallistics, 10.0F);
        CannonUtil.BigCannonShotState changedProjectile = bigCannonShot(
                2.0F, changedBallistics, 2.0F);

        String baselineFingerprint = WeaponFiringControl.bigCannonShotFingerprint(
                ProjectileModel.cbc(2.0, -0.05, 0.01, 1.0, false),
                baseline);
        String propellantFingerprint = WeaponFiringControl.bigCannonShotFingerprint(
                ProjectileModel.cbc(10.0, -0.05, 0.01, 1.0, false),
                changedPropellant);
        String projectileFingerprint = WeaponFiringControl.bigCannonShotFingerprint(
                ProjectileModel.cbc(2.0, -0.08, 0.02, 1.0, true),
                changedProjectile);
        boolean passed = !baselineFingerprint.equals(propellantFingerprint)
                && !baselineFingerprint.equals(projectileFingerprint)
                && !WeaponFiringControl.shouldKeepPendingSolve(
                1L, true, baselineFingerprint.equals(propellantFingerprint));
        return result("big_cannon_live_properties_invalidate_solve", passed,
                "baseline=" + baselineFingerprint
                        + " propellant=" + propellantFingerprint
                        + " projectile=" + projectileFingerprint);
    }

    private static CannonUtil.BigCannonShotState bigCannonShot(
            float speed,
            BallisticPropertiesComponent ballistics,
            float propellantPower
    ) {
        return new CannonUtil.BigCannonShotState(
                speed, ballistics, "test.Projectile", BlockPos.ZERO,
                BlockPos.ZERO, 0.0, 1, propellantPower,
                0.0F, "loaded_projectile");
    }

    private static TargetingSolverSelfTest.Result
    checkDirectAdapterAimGating() {
        boolean adapterHasResolvedAim =
                WeaponFiringControl.hasResolvedAimForUpdate(
                        false, false, true, true);
        boolean unresolvedLaserIsNotResolved =
                WeaponFiringControl.hasResolvedAimForUpdate(
                        false, false, true, true);
        boolean directIgnoresBallisticSolutions =
                !WeaponFiringControl.hasResolvedAimForUpdate(
                        true, true, true, true);
        WeaponFiringControl.AimUpdateMode adapterMode =
                WeaponFiringControl.selectAimUpdateMode(
                        adapterHasResolvedAim, false);
        boolean unresolvedReady =
                WeaponFiringControl.isDirectAdapterAimReady(true, false);
        boolean resolvedReady =
                WeaponFiringControl.isDirectAdapterAimReady(true, true);
        boolean nonAdapterReady =
                WeaponFiringControl.isDirectAdapterAimReady(false, false);
        boolean firePrivilege =
                WeaponFiringControl.hasFireEligibleAim(
                        false, false, true);
        boolean passed = !adapterHasResolvedAim
                && !unresolvedLaserIsNotResolved
                && directIgnoresBallisticSolutions
                && adapterMode == WeaponFiringControl.AimUpdateMode.DIRECT
                && !unresolvedReady
                && resolvedReady
                && nonAdapterReady
                && firePrivilege
                && !(firePrivilege && unresolvedReady)
                && firePrivilege && resolvedReady;
        return result("direct_adapter_aim_fails_closed", passed,
                "mode=" + adapterMode
                        + " unresolvedReady=" + unresolvedReady
                        + " resolvedReady=" + resolvedReady
                        + " unresolvedLaser="
                        + unresolvedLaserIsNotResolved);
    }

    private static TargetingSolverSelfTest.Result
    checkSublevelFrameTracksCarrierRotation() {
        ControllerMovementLimits yawLimits =
                new ControllerMovementLimits(
                        CannonAxis.YAW, -45.0, 45.0);
        WeaponFiringControl.MountAimFrame unrotated = frame(
                new Vec3(1.0, 0.0, 0.0),
                new Vec3(0.0, 1.0, 0.0),
                new Vec3(0.0, 0.0, 1.0),
                yawLimits, 270.0);
        WeaponFiringControl.MountAimFrame rotated = frame(
                new Vec3(0.0, 0.0, -1.0),
                new Vec3(0.0, 1.0, 0.0),
                new Vec3(1.0, 0.0, 0.0),
                yawLimits, 270.0);
        Vec3 cachedWorldDirection = new Vec3(1.0, 0.0, 0.0);
        WeaponFiringControl.AimCommandEvaluation before =
                unrotated.evaluate(cachedWorldDirection);
        WeaponFiringControl.AimCommandEvaluation after =
                rotated.evaluate(cachedWorldDirection);
        double yawChange = Math.abs(shortestDelta(
                before.requestedControllerYawDeg(),
                after.requestedControllerYawDeg()));
        boolean passed = before.fireEligible()
                && after.valid() && after.yawConstrained()
                && !after.fireEligible()
                && close(yawChange, 90.0)
                && close(after.appliedControllerYawDeg(), 315.0);
        return result("sublevel_frame_tracks_carrier_rotation", passed,
                "beforeYaw=" + before.requestedControllerYawDeg()
                        + " afterYaw="
                        + after.requestedControllerYawDeg()
                        + " applied="
                        + after.appliedControllerYawDeg());
    }

    private static TargetingSolverSelfTest.Result
    checkNestedSwivelYawLimits() {
        boolean legal = true;
        boolean illegal = true;
        boolean boundary = true;
        boolean roundTrip = true;
        for (KineticAimFrame parent : List.of(KineticAimFrame.world(),
                new KineticAimFrame(new Vec3(0, 1, 0),
                        new Vec3(-1, 0, 0), new Vec3(0, 0, 1)))) {
            for (double stageYaw : new double[]{80.0, -80.0}) {
                KineticAimFrame pitchParent = new KineticAimFrame(
                        parent.worldDirection(stageYaw - 90.0, 0.0),
                        parent.upAxis(), parent.worldDirection(stageYaw, 0.0));
                WeaponFiringControl.MountAimFrame nested =
                        new WeaponFiringControl.MountAimFrame(
                                WeaponFiringControl.MountFrameKind.STRUCTURAL,
                                null, new PitchConstraint(-70.0, 70.0,
                                pitchParent.rightAxis(), pitchParent.upAxis(),
                                pitchParent.forwardAxis()),
                                new ControllerMovementLimits(CannonAxis.YAW, -90.0, 90.0),
                                0.0, null, parent);
                // Legal in the yaw parent's frame, 160 degrees away in the
                // pitch parent's frame. The old shared-frame check rejected it.
                Vec3 target = parent.worldDirection(-stageYaw, 25.0);
                var evaluation = nested.evaluate(target);
                legal &= evaluation.fireEligible();
                roundTrip &= nested.worldDirection(evaluation.requestedPitchDeg(),
                                evaluation.requestedControllerYawDeg())
                        .distanceTo(target) < 1.0e-6;

                // Conversely, a target locally near the pitch stage must not
                // bypass the actual yaw actuator's limits.
                Vec3 outside = parent.worldDirection(Math.copySign(110.0, stageYaw), 25.0);
                var rejected = nested.evaluate(outside);
                illegal &= rejected.valid() && rejected.yawConstrained()
                        && !rejected.fireEligible();
                Vec3 applied = nested.worldDirection(rejected.appliedPitchDeg(),
                        rejected.appliedControllerYawDeg());
                boundary &= nested.evaluate(applied).fireEligible()
                        && applied.distanceTo(parent.worldDirection(
                        Math.copySign(90.0, stageYaw), 25.0)) < 1.0e-6;
            }
        }
        return result("nested_swivel_limits_use_yaw_parent", legal && illegal
                        && boundary && roundTrip,
                "legal=" + legal + " rejected=" + illegal
                        + " boundary=" + boundary + " roundTrip=" + roundTrip);
    }

    private static TargetingSolverSelfTest.Result
    checkStructuralPitchLimits() {
        KineticAimFrame tilted = new KineticAimFrame(new Vec3(0, 1, 0),
                new Vec3(-1, 0, 0), new Vec3(0, 0, 1));
        PitchConstraint swivel = WeaponFiringControl.mountPitchConstraint(
                true, -70.0, 70.0, -5.0, 15.0,
                tilted.rightAxis(), tilted.upAxis(), tilted.forwardAxis());
        PitchConstraint direct = WeaponFiringControl.mountPitchConstraint(
                false, -70.0, 70.0, -5.0, 15.0,
                tilted.rightAxis(), tilted.upAxis(), tilted.forwardAxis());
        PitchConstraint configured = WeaponFiringControl.mountPitchConstraint(
                true, -20.0, 20.0, -5.0, 15.0,
                tilted.rightAxis(), tilted.upAxis(), tilted.forwardAxis());
        boolean passed = swivel.allows(tilted.worldDirection(0.0, 60.0))
                && swivel.allows(tilted.worldDirection(0.0, -60.0))
                && !swivel.allows(tilted.worldDirection(0.0, 80.0))
                && !configured.allows(tilted.worldDirection(0.0, 60.0))
                && !direct.allows(tilted.worldDirection(0.0, 60.0))
                && !direct.allows(tilted.worldDirection(0.0, -60.0))
                && direct.allows(tilted.worldDirection(0.0, 10.0));
        return result("swivel_pitch_uses_assembly_limits", passed,
                "swivel=" + swivel.summary() + " direct=" + direct.summary());
    }

    private static TargetingSolverSelfTest.Result
    checkLimitBoundaryTrackingFailsClosed() {
        WeaponFiringControl.MountAimFrame frame = frame(
                new Vec3(1.0, 0.0, 0.0),
                new Vec3(0.0, 1.0, 0.0),
                new Vec3(0.0, 0.0, 1.0),
                new ControllerMovementLimits(
                        CannonAxis.YAW, -30.0, 30.0), 270.0,
                -10.0, 10.0);
        Vec3 outside = new Vec3(0.0, 1.0, 1.0).normalize();
        WeaponFiringControl.AimCommandEvaluation evaluation =
                frame.evaluate(outside);
        Vec3 boundaryWorld = frame.worldDirection(
                evaluation.appliedPitchDeg(),
                evaluation.appliedControllerYawDeg());
        WeaponFiringControl.AimCommandEvaluation boundary =
                frame.evaluate(boundaryWorld);
        boolean passed = evaluation.valid()
                && evaluation.pitchConstrained()
                && evaluation.yawConstrained()
                && !evaluation.fireEligible()
                && close(evaluation.appliedPitchDeg(), 10.0)
                && boundary.fireEligible();
        return result("movement_limits_track_boundary_and_block_fire",
                passed, "reason=" + evaluation.reason()
                        + " requested="
                        + evaluation.requestedPitchDeg() + "/"
                        + evaluation.requestedControllerYawDeg()
                        + " applied="
                        + evaluation.appliedPitchDeg() + "/"
                        + evaluation.appliedControllerYawDeg());
    }

    private static TargetingSolverSelfTest.Result
    checkUnavailableSourceFrameFailsClosed() {
        WeaponFiringControl.MountAimFrame unavailable =
                new WeaponFiringControl.MountAimFrame(
                        WeaponFiringControl.MountFrameKind.UNAVAILABLE,
                        null, PitchConstraint.unconstrained(),
                        ControllerMovementLimits.defaults(CannonAxis.YAW),
                        0.0, "source_frame_unavailable");
        WeaponFiringControl.AimCommandEvaluation evaluation =
                unavailable.evaluate(new Vec3(1.0, 0.0, 0.0));
        boolean passed = !evaluation.valid()
                && !evaluation.fireEligible()
                && "source_frame_unavailable".equals(
                evaluation.reason());
        return result("missing_sublevel_frame_fails_closed", passed,
                "reason=" + evaluation.reason());
    }

    private static WeaponFiringControl.MountAimFrame frame(
            Vec3 right, Vec3 up, Vec3 forward,
            ControllerMovementLimits yawLimits,
            double yawNeutral) {
        return frame(right, up, forward, yawLimits, yawNeutral,
                -89.0, 89.0);
    }

    private static WeaponFiringControl.MountAimFrame frame(
            Vec3 right, Vec3 up, Vec3 forward,
            ControllerMovementLimits yawLimits,
            double yawNeutral, double minPitch, double maxPitch) {
        return new WeaponFiringControl.MountAimFrame(
                WeaponFiringControl.MountFrameKind.SUBLEVEL,
                null, new PitchConstraint(
                minPitch, maxPitch, right, up, forward),
                yawLimits, yawNeutral, null);
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
                        true, true, true, true, true);
        boolean yawBlocksOnlySide =
                !WeaponFiringControl.dualSideFireEligible(
                        true, true, false,
                        true, true, true, true, true);
        boolean profileBlocksOnlySide =
                !WeaponFiringControl.dualSideFireEligible(
                        true, true, true,
                        true, true, false, true, true);
        boolean sharedGateStops =
                !WeaponFiringControl.dualSideFireEligible(
                        false, true, true,
                        true, true, true, true, true);
        boolean unsafeSideStops =
                !WeaponFiringControl.dualSideFireEligible(
                        true, true, true,
                        true, true, true, true, false);
        boolean staleAimStops =
                !WeaponFiringControl.dualSideFireEligible(
                        true, true, true,
                        true, true, true, false, true);
        boolean passed = allReady && yawBlocksOnlySide
                && profileBlocksOnlySide && sharedGateStops
                && unsafeSideStops && staleAimStops;
        return result("t_pitch_independent_fire_gates", passed,
                "ready=" + allReady
                        + " yawBlocked=" + yawBlocksOnlySide
                        + " profileBlocked=" + profileBlocksOnlySide
                        + " sharedBlocked=" + sharedGateStops
                        + " unsafeBlocked=" + unsafeSideStops
                        + " staleBlocked=" + staleAimStops);
    }

    private static TargetingSolverSelfTest.Result
    checkPerSideSafeZoneGeometry() {
        Vec3 leftStart = new Vec3(-2.0, 0.0, 0.0);
        Vec3 rightStart = new Vec3(2.0, 0.0, 0.0);
        Vec3 leftEnd = new Vec3(-2.0, 0.0, 100.0);
        Vec3 rightEnd = new Vec3(2.0, 0.0, 100.0);
        SafeZone leftZone = new SafeZone(
                new AABB(-2.5, -1.0, 10.0,
                        -1.5, 1.0, 11.0), null);
        SafeZone rightZone = new SafeZone(
                new AABB(1.5, -1.0, 10.0,
                        2.5, 1.0, 11.0), null);
        boolean leftOnly = WeaponFiringControl.pathTouchesSafeZone(
                List.of(leftZone), null, leftStart, leftEnd)
                && !WeaponFiringControl.pathTouchesSafeZone(
                List.of(leftZone), null, rightStart, rightEnd);
        boolean reversed = WeaponFiringControl.pathTouchesSafeZone(
                List.of(rightZone), null, rightStart, rightEnd)
                && !WeaponFiringControl.pathTouchesSafeZone(
                List.of(rightZone), null, leftStart, leftEnd);
        return result("t_pitch_per_side_safe_zone_geometry",
                leftOnly && reversed,
                "leftOnly=" + leftOnly + " reversed=" + reversed);
    }

    private static TargetingSolverSelfTest.Result
    checkDualAimingSourceRecovery() {
        int initial = WeaponFiringControl.selectDualAimingSourceIndex(
                -1, 0, List.of(true, true));
        int primaryRetained =
                WeaponFiringControl.selectDualAimingSourceIndex(
                        0, 0, List.of(true, true));
        int fallback = WeaponFiringControl.selectDualAimingSourceIndex(
                0, 0, List.of(false, true));
        int fallbackRetainedAfterPrimaryReload =
                WeaponFiringControl.selectDualAimingSourceIndex(
                        1, 0, List.of(true, true));
        int primaryRecovery =
                WeaponFiringControl.selectDualAimingSourceIndex(
                        1, 0, List.of(true, false));
        int bothEmptyPrimary =
                WeaponFiringControl.selectDualAimingSourceIndex(
                        0, 0, List.of(false, false));
        int bothEmptyFallback =
                WeaponFiringControl.selectDualAimingSourceIndex(
                        1, 0, List.of(false, false));
        boolean passed = initial == 0 && primaryRetained == 0
                && fallback == 1
                && fallbackRetainedAfterPrimaryReload == 1
                && primaryRecovery == 0
                && bothEmptyPrimary == 0 && bothEmptyFallback == 1;
        return result("t_pitch_loaded_source_reload_recovery", passed,
                "sequence=" + initial + "," + primaryRetained + ","
                        + fallback + ","
                        + fallbackRetainedAfterPrimaryReload + ","
                        + primaryRecovery + "," + bothEmptyPrimary
                        + "," + bothEmptyFallback);
    }

    private static TargetingSolverSelfTest.Result
    checkSecondarySolutionIdentity() {
        WeaponFiringControl.SideSolutionKey baseline =
                new WeaponFiringControl.SideSolutionKey(
                        BlockPos.ZERO, 11, BlockPos.ZERO.above(),
                        "hostile:target", "ammo-a", false);
        WeaponFiringControl.SideSolutionKey same =
                new WeaponFiringControl.SideSolutionKey(
                        BlockPos.ZERO, 11, BlockPos.ZERO.above(),
                        "hostile:target", "ammo-a", false);
        WeaponFiringControl.SideSolutionKey changedAmmo =
                new WeaponFiringControl.SideSolutionKey(
                        BlockPos.ZERO, 11, BlockPos.ZERO.above(),
                        "hostile:target", "ammo-b", false);
        WeaponFiringControl.SideSolutionKey changedMount =
                new WeaponFiringControl.SideSolutionKey(
                        BlockPos.ZERO, 12, BlockPos.ZERO.above(),
                        "hostile:target", "ammo-a", false);
        WeaponFiringControl.SideSolutionKey changedSource =
                new WeaponFiringControl.SideSolutionKey(
                        BlockPos.ZERO, 11, BlockPos.ZERO.below(),
                        "hostile:target", "ammo-a", false);
        WeaponFiringControl.SideSolutionKey changedTarget =
                new WeaponFiringControl.SideSolutionKey(
                        BlockPos.ZERO, 11, BlockPos.ZERO.above(),
                        "hostile:other", "ammo-a", false);
        boolean passed = baseline.equals(same)
                && !baseline.equals(changedAmmo)
                && !baseline.equals(changedMount)
                && !baseline.equals(changedSource)
                && !baseline.equals(changedTarget);
        return result("t_pitch_secondary_solution_identity", passed,
                "same=" + baseline.equals(same)
                        + " ammo=" + baseline.equals(changedAmmo)
                        + " mount=" + baseline.equals(changedMount)
                        + " source=" + baseline.equals(changedSource)
                        + " target=" + baseline.equals(changedTarget));
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

    private static boolean close(double first, double second) {
        return Math.abs(first - second) < 1.0E-6;
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
