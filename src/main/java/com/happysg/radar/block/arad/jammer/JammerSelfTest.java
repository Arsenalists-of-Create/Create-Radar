package com.happysg.radar.block.arad.jammer;

import com.happysg.radar.api.arad.RollingRpmTracker;
import com.happysg.radar.api.jamming.DirectionalJammingApi;
import com.happysg.radar.block.monitor.MonitorPonderJammingSelfTest;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.RadarTrackUtil;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.sable.SableSilhouetteStatus;
import com.happysg.radar.compat.sable.SubLevelSilhouette;
import com.happysg.radar.compat.sable.SyntheticSableSilhouetteFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class JammerSelfTest {
    private static final float EPSILON = 1.0e-4f;

    private JammerSelfTest() {
    }

    public static void main(String[] args) {
        verifyPlacementDefaults();
        verifyKineticStepping();
        verifyRollingRpmTelemetry();
        verifyAutomaticAimAngles();
        verifyOrientationBasis();
        verifyVisualPivot();
        verifyJammingProfile();
        verifyNoiseInterpolation();
        verifyJammerClustering();
        verifyActiveJammerEligibility();
        verifyNearbyRadarSpillover();
        verifyBalancedEffectScaling();
        verifyJammingTrackMetadata();
        verifyExternalGuidanceObservation();
        verifyJammedObservationWins();
        verifySilhouetteJitterAlignment();
        verifyProceduralFakeShipSilhouettes();
        verifyFakeHullConfigToggle();
        verifyFakeShipRarityGate();
        MonitorPonderJammingSelfTest.verify();
        System.out.println("PASS directional jammer placement, aiming, telemetry, and jamming checks");
    }

    private static void verifyExternalGuidanceObservation() {
        RadarTrack track = new RadarTrack(UUID.randomUUID().toString(),
                new Vec3(12.0, 3.0, 4.0), new Vec3(0.5, 0.0, 0.0), 42L,
                TrackCategory.CONTRAPTION, "test:target", 1.0F);
        track.setJammingData(new RadarTrack.JammingData("test:emitter",
                1.0F, 0.5F, 0.0F, 0.0F,
                new Vec3(2.0, -1.0, 3.0), new Vec3(0.1, 0.2, 0.3), 99L));
        DirectionalJammingApi.GuidanceObservation observation =
                DirectionalJammingApi.resolveGuidance(track,
                        new Vec3(10.0, 10.0, 10.0), new Vec3(1.0, 2.0, 3.0));
        require(observation != null && observation.jammed(),
                "external guidance observation should retain jamming metadata");
        require(observation.position().equals(new Vec3(12.0, 9.0, 13.0)),
                "external guidance should apply the full offset once");
        require(observation.velocity().equals(new Vec3(1.1, 2.2, 3.3)),
                "external guidance should apply the velocity offset once");

        track.setSynthetic(true);
        DirectionalJammingApi.GuidanceObservation synthetic =
                DirectionalJammingApi.resolveGuidance(track,
                        new Vec3(100.0, 100.0, 100.0), Vec3.ZERO);
        require(synthetic != null && synthetic.synthetic()
                        && synthetic.position().equals(track.position()),
                "synthetic guidance should use its reported coordinates");
    }

    private static void verifyPlacementDefaults() {
        requireNear(JammerBlockEntity.defaultYaw(Direction.EAST, null), 0.0f,
                "east wall yaw");
        requireNear(JammerBlockEntity.defaultYaw(Direction.SOUTH, null), 90.0f,
                "south wall yaw");
        requireNear(JammerBlockEntity.defaultYaw(Direction.WEST, null), 180.0f,
                "west wall yaw");
        requireNear(JammerBlockEntity.defaultYaw(Direction.NORTH, null), 270.0f,
                "north wall yaw");

        requireNear(JammerBlockEntity.defaultYaw(Direction.UP, Direction.NORTH),
                90.0f, "floor placement should face a north-facing player");
        requireNear(JammerBlockEntity.defaultYaw(Direction.DOWN, Direction.EAST),
                180.0f, "ceiling placement should face an east-facing player");
        requireNear(JammerBlockEntity.defaultYaw(Direction.UP, null), 270.0f,
                "non-player vertical placement fallback");
    }

    private static void verifyKineticStepping() {
        requireNear(JammerBlockEntity.degreesPerTick(64.0f), 19.2f,
                "positive RPM conversion");
        requireNear(JammerBlockEntity.degreesPerTick(-64.0f), 19.2f,
                "negative RPM conversion must use magnitude");

        requireNear(JammerBlockEntity.moveTowardWrapped(350.0f, 10.0f, 5.0f),
                355.0f, "yaw should take the shortest wrapped path");
        requireNear(JammerBlockEntity.moveTowardWrapped(5.0f, 355.0f, 5.0f),
                0.0f, "yaw should wrap through north");
        requireNear(JammerBlockEntity.moveTowardWrapped(10.0f, 90.0f, 0.0f),
                10.0f, "zero RPM should pause yaw");

        float step = JammerBlockEntity.degreesPerTick(32.0f);
        requireNear(JammerBlockEntity.moveTowardWrapped(0.0f, 90.0f, step),
                step, "yaw receives the full per-axis step");
        requireNear(JammerBlockEntity.moveToward(0.0f, 45.0f, step), step,
                "pitch receives the full per-axis step");
        requireNear(JammerBlockEntity.clampPitch(120.0f), 90.0f,
                "upper pitch clamp");
        requireNear(JammerBlockEntity.clampPitch(-120.0f), -90.0f,
                "lower pitch clamp");
    }

    private static void verifyRollingRpmTelemetry() {
        require(RollingRpmTracker.WINDOW_SAMPLES == 40,
                "20-second window sampled every 10 ticks must contain 40 samples");

        RollingRpmTracker steady = new RollingRpmTracker();
        for (int sample = 0; sample < RollingRpmTracker.WINDOW_SAMPLES; sample++) {
            steady.sample(sample % 2 == 0 ? 50.0f : -50.0f);
        }
        requireNear(steady.snapshot().rollingRpm(), 50.0f,
                "rolling RPM uses input magnitude");
        requireNear(steady.snapshot().rollingRate(), 0.0f,
                "constant RPM magnitude has zero rolling rate");

        RollingRpmTracker changing = new RollingRpmTracker();
        for (int sample = 0; sample < RollingRpmTracker.WINDOW_SAMPLES; sample++) {
            changing.sample(100.0f);
        }
        float slowRate = changing.sample(140.0f).rollingRate();
        float fastRate = changing.sample(180.0f).rollingRate();
        requireNear(changing.snapshot().rollingRpm(), 103.0f,
                "rolling window evicts the oldest samples");
        require(slowRate > 0.0f,
                "changed RPM produces a positive rolling rate");
        require(fastRate > slowRate,
                "larger rolling-RPM changes produce a greater rate");
    }

    private static void verifyAutomaticAimAngles() {
        requireAim(new Vec3(1, 0, 0), 0.0f, 0.0f,
                "east target");
        requireAim(new Vec3(0, 0, 1), 90.0f, 0.0f,
                "south target");
        requireAim(new Vec3(-1, 0, 0), 180.0f, 0.0f,
                "west target");
        requireAim(new Vec3(0, 0, -1), 270.0f, 0.0f,
                "north target");
        requireAim(new Vec3(1, 1, 0), 0.0f, 45.0f,
                "elevated east target");
        requireAim(new Vec3(0, -1, 1), 90.0f, -45.0f,
                "depressed south target");

        JammerBlockEntity.AimAngles vertical = JammerBlockEntity.aimAngles(
                new Vec3(0, 4, 0), 123.0f);
        require(vertical != null, "vertical target should resolve");
        requireNear(vertical.yaw(), 123.0f,
                "vertical target should retain its fallback yaw");
        requireNear(vertical.pitch(), 90.0f,
                "vertical target should aim straight up");

        require(JammerBlockEntity.aimAngles(Vec3.ZERO, 0.0f) == null,
                "coincident target should not change aim");
        require(JammerBlockEntity.aimAngles(
                new Vec3(Double.NaN, 0, 0), 0.0f) == null,
                "non-finite target should not change aim");
    }

    private static void requireAim(Vec3 direction, float expectedYaw,
                                   float expectedPitch, String label) {
        JammerBlockEntity.AimAngles aim = JammerBlockEntity.aimAngles(
                direction, 42.0f);
        require(aim != null, label + " should resolve");
        requireNear(aim.yaw(), expectedYaw, label + " yaw");
        requireNear(aim.pitch(), expectedPitch, label + " pitch");

        Vector3f expectedForward = new Vector3f((float) direction.x,
                (float) direction.y, (float) direction.z).normalize();
        requireVectorNear(JammerOrientation.forward(aim.yaw(), aim.pitch()),
                expectedForward, label + " forward");
    }

    private static void verifyOrientationBasis() {
        requireOrientation(Direction.UP, 270.0f, 0.0f,
                new Vector3f(0, 0, -1), new Vector3f(0, 1, 0),
                "floor north");
        requireOrientation(Direction.DOWN, 270.0f, 0.0f,
                new Vector3f(0, 0, -1), new Vector3f(0, -1, 0),
                "ceiling north inverted");
        requireOrientation(Direction.EAST, 0.0f, 0.0f,
                new Vector3f(1, 0, 0), new Vector3f(0, 1, 0),
                "east wall outward and upright");

        Quaternionf pole = JammerOrientation.rotation(Direction.UP, 45.0f,
                90.0f);
        Vector3f poleForward = new Vector3f(0, 0, -1).rotate(pole);
        requireFinite(poleForward, "vertical aim orientation");
        requireVectorNear(poleForward, new Vector3f(0, 1, 0),
                "positive pitch should aim upward");
    }

    private static void verifyVisualPivot() {
        requireNear(JammerBlockEntity.TURRET_PIVOT_OFFSET, 1.0f,
                "turret pivot should be centered in the adjacent block");
        requireNear(JammerBlockEntity.TURRET_MODEL_PIVOT_Y, 9.0f / 16.0f,
                "partial-model pivot should account for its mount overlap");

        Vector3f modelPivot = new Vector3f(0.5f,
                JammerBlockEntity.TURRET_MODEL_PIVOT_Y, 0.5f);
        for (Direction mountFacing : Direction.values()) {
            Vector3f expectedWorldPivot = new Vector3f(
                    0.5f + mountFacing.getStepX(),
                    0.5f + mountFacing.getStepY(),
                    0.5f + mountFacing.getStepZ());

            Quaternionf rotation = JammerOrientation.rotation(mountFacing,
                    37.0f, -23.0f);
            Vector3f transformedPivot = new Vector3f(modelPivot)
                    .sub(0.5f, JammerBlockEntity.TURRET_MODEL_PIVOT_Y, 0.5f)
                    .rotate(rotation)
                    .add(expectedWorldPivot);
            requireVectorNear(transformedPivot, expectedWorldPivot,
                    mountFacing.getName() + " visual pivot");
        }
    }

    private static void verifyJammingProfile() {
        DirectionalJammingService.Profile stopped =
                DirectionalJammingService.calculateProfile(
                        0.0f, 0.0f, 0.0f, 0.0f);
        require(!stopped.active(), "zero jammer RPM must disable jamming");

        DirectionalJammingService.Profile outerEdge =
                DirectionalJammingService.calculateProfile(
                        65.0f, 50.0f, 0.0f, 0.0f);
        requireNear(outerEdge.rpmDifference(), 15.0f,
                "outer threshold RPM difference");
        require(outerEdge.outerStrength() > 0.0f,
                "outer threshold must begin guidance corruption");
        requireNear(outerEdge.directionalStrength(), 0.0f,
                "outer threshold must not begin directional jitter");

        DirectionalJammingService.Profile closer =
                DirectionalJammingService.calculateProfile(
                        61.0f, 50.0f, 0.0f, 0.0f);
        require(closer.outerStrength() > outerEdge.outerStrength(),
                "guidance corruption must increase toward the inner tier");

        DirectionalJammingService.Profile directionalEdge =
                DirectionalJammingService.calculateProfile(
                        60.0f, 50.0f, 0.0f, 0.0f);
        require(directionalEdge.directionalStrength() > 0.0f,
                "10 RPM difference must begin directional jitter");

        DirectionalJammingService.Profile severeEdge =
                DirectionalJammingService.calculateProfile(
                        55.0f, 50.0f, 0.0f, 0.0f);
        require(severeEdge.severeStrength() > 0.0f,
                "5 RPM difference must begin severe deception");

        DirectionalJammingService.Profile changingRadar =
                DirectionalJammingService.calculateProfile(
                        75.0f, 50.0f, 20.0f, 0.0f);
        requireNear(changingRadar.rateBonus(), 10.0f,
                "rolling-rate tolerance expansion");
        requireNear(changingRadar.outerThreshold(), 25.0f,
                "expanded outer threshold");
        require(changingRadar.outerStrength() > 0.0f,
                "expanded threshold must activate at its edge");

        DirectionalJammingService.Profile fullAlignment =
                DirectionalJammingService.calculateProfile(
                        50.0f, 50.0f, 0.0f, 3.0f);
        DirectionalJammingService.Profile halfAlignment =
                DirectionalJammingService.calculateProfile(
                        50.0f, 50.0f, 0.0f, 9.0f);
        DirectionalJammingService.Profile missed =
                DirectionalJammingService.calculateProfile(
                        50.0f, 50.0f, 0.0f, 15.0f);
        requireNear(fullAlignment.alignmentFactor(), 1.0f,
                "full-alignment boundary");
        requireNear(halfAlignment.alignmentFactor(), 0.5f,
                "alignment taper midpoint");
        require(!missed.active(),
                "15-degree turret error must disable the contribution");
    }

    private static void verifyNoiseInterpolation() {
        Vec3 target = new Vec3(0.0, 0.0, 100.0);
        Vec3 origin = Vec3.ZERO;
        int periodTicks = 5;
        long seed = 0x5A17C0DEL;

        DirectionalJammingService.NoiseSample first =
                DirectionalJammingService.noise(
                        target, origin, 20.0F, periodTicks, 0L, seed);
        DirectionalJammingService.NoiseSample beforeBoundary =
                DirectionalJammingService.noise(
                        target, origin, 20.0F, periodTicks, 4L, seed);
        DirectionalJammingService.NoiseSample atBoundary =
                DirectionalJammingService.noise(
                        target, origin, 20.0F, periodTicks, 5L, seed);
        require(first.token() == beforeBoundary.token(),
                "noise token must remain stable within a segment");
        require(first.token() != atBoundary.token(),
                "noise token must change at a segment boundary");

        for (long tick = 0L; tick < 15L; tick++) {
            DirectionalJammingService.NoiseSample current =
                    DirectionalJammingService.noise(
                            target, origin, 20.0F, periodTicks, tick, seed);
            DirectionalJammingService.NoiseSample next =
                    DirectionalJammingService.noise(
                            target, origin, 20.0F, periodTicks,
                            tick + 1L, seed);
            Vec3 actualStep = next.offset().subtract(current.offset());
            require(actualStep.distanceToSqr(current.velocityOffset())
                            <= 1.0E-18,
                    "noise velocity must match the next-tick position step"
                            + " at tick " + tick + ": expected "
                            + actualStep + ", got "
                            + current.velocityOffset());
        }

        DirectionalJammingService.NoiseSample periodOne =
                DirectionalJammingService.noise(
                        target, origin, 20.0F, 1, 9L, seed);
        DirectionalJammingService.NoiseSample nextPeriodOne =
                DirectionalJammingService.noise(
                        target, origin, 20.0F, 1, 10L, seed);
        require(nextPeriodOne.offset().subtract(periodOne.offset())
                        .distanceToSqr(periodOne.velocityOffset())
                        <= 1.0E-18,
                "one-tick noise periods must remain position/velocity consistent");

        DirectionalJammingService.NoiseSample clampedPeriod =
                DirectionalJammingService.noise(
                        target, origin, 20.0F, 0, 9L, seed);
        require(Double.isFinite(clampedPeriod.offset().x)
                        && Double.isFinite(clampedPeriod.offset().y)
                        && Double.isFinite(clampedPeriod.offset().z)
                        && Double.isFinite(clampedPeriod.velocityOffset().x)
                        && Double.isFinite(clampedPeriod.velocityOffset().y)
                        && Double.isFinite(clampedPeriod.velocityOffset().z),
                "invalid noise periods must be clamped to finite samples");

        DirectionalJammingService.NoiseSample disabled =
                DirectionalJammingService.noise(
                        target, origin, 0.0F, periodTicks, 0L, seed);
        require(disabled.offset().equals(Vec3.ZERO)
                        && disabled.velocityOffset().equals(Vec3.ZERO),
                "zero-strength noise must remain disabled");
    }

    private static void verifyJammerClustering() {
        BlockPos group = new BlockPos(5, 6, 7);
        List<DirectionalJammingService.ClusterIdentity> mixedChain = List.of(
                new DirectionalJammingService.ClusterIdentity(
                        Vec3.ZERO, null, Set.of()),
                new DirectionalJammingService.ClusterIdentity(
                        new Vec3(40.0, 0.0, 0.0), group, Set.of()),
                new DirectionalJammingService.ClusterIdentity(
                        new Vec3(1000.0, 0.0, 0.0), group, Set.of()));
        requireArrayEquals(DirectionalJammingService.clusterSizes(
                        mixedChain, 50.0F), new int[]{3, 3, 3},
                "proximity and ARAD links must form a transitive cluster");

        UUID commonSublevel = UUID.fromString(
                "c260f433-ef0f-44bc-bf28-f86d85d3b2f1");
        List<DirectionalJammingService.ClusterIdentity> sableChain = List.of(
                new DirectionalJammingService.ClusterIdentity(
                        Vec3.ZERO, null, Set.of(commonSublevel,
                        UUID.fromString("b73c778e-c155-4b36-a30e-860fce72828d"))),
                new DirectionalJammingService.ClusterIdentity(
                        new Vec3(500.0, 0.0, 0.0), null,
                        Set.of(commonSublevel,
                        UUID.fromString("77ca6209-dbbb-435a-89a4-2c3d9437e0ef"))));
        requireArrayEquals(DirectionalJammingService.clusterSizes(
                        sableChain, 0.0F), new int[]{2, 2},
                "connected Sable sublevels must share a cluster");

        List<DirectionalJammingService.ClusterIdentity> independent = List.of(
                new DirectionalJammingService.ClusterIdentity(
                        Vec3.ZERO, null, Set.of()),
                new DirectionalJammingService.ClusterIdentity(
                        new Vec3(40.0, 0.0, 0.0), null, Set.of()));
        requireArrayEquals(DirectionalJammingService.clusterSizes(
                        independent, 0.0F), new int[]{1, 1},
                "zero proximity radius must disable distance clustering");
        requireArrayEquals(DirectionalJammingService.clusterSizes(
                        independent, 40.0F), new int[]{2, 2},
                "proximity radius boundary must be inclusive");
    }

    private static void verifyActiveJammerEligibility() {
        DirectionalJammingService.Profile active =
                DirectionalJammingService.calculateProfile(
                        50.0F, 50.0F, 0.0F, 0.0F);
        DirectionalJammingService.Profile stopped =
                DirectionalJammingService.calculateProfile(
                        0.0F, 50.0F, 0.0F, 0.0F);
        require(DirectionalJammingService.isActiveContribution(
                        active, 102L, 100L),
                "an active contribution at the TTL boundary must count");
        require(!DirectionalJammingService.isActiveContribution(
                        active, 103L, 100L),
                "an expired contribution must not count");
        require(!DirectionalJammingService.isActiveContribution(
                        stopped, 100L, 100L),
                "a stopped jammer must not count");
    }

    private static void verifyNearbyRadarSpillover() {
        Vec3 targetRadar = Vec3.ZERO;
        require(DirectionalJammingService.contributionAffectsRadar(
                        "target", targetRadar, "target",
                        new Vec3(1000.0, 0.0, 0.0), 0.0F),
                "the directly targeted radar must always receive its contribution");
        require(DirectionalJammingService.contributionAffectsRadar(
                        "target", targetRadar, "neighbor",
                        new Vec3(25.0, 0.0, 0.0), 25.0F),
                "spillover radius boundary must be inclusive");
        require(!DirectionalJammingService.contributionAffectsRadar(
                        "target", targetRadar, "outside",
                        new Vec3(25.01, 0.0, 0.0), 25.0F),
                "radars beyond the spillover radius must remain unaffected");
        require(!DirectionalJammingService.contributionAffectsRadar(
                        "target", targetRadar, "neighbor",
                        targetRadar, 0.0F),
                "zero spillover radius must disable inherited effects");
        require(!DirectionalJammingService.contributionAffectsRadar(
                        "target", targetRadar, "second-hop",
                        new Vec3(50.0, 0.0, 0.0), 25.0F),
                "spillover must not propagate outward from an inherited radar");
        require(DirectionalJammingService.supportsRadarType("ground")
                        && DirectionalJammingService.supportsRadarType("plane")
                        && !DirectionalJammingService.supportsRadarType("sonar"),
                "spillover must preserve native sonar immunity");
    }

    private static void verifyBalancedEffectScaling() {
        DirectionalJammingService.Profile full =
                DirectionalJammingService.calculateProfile(
                        50.0F, 50.0F, 0.0F, 0.0F);
        DirectionalJammingService.Profile half = full.balanced(2);
        require(half.clusterSize() == 2,
                "profile cluster size");
        requireNear(half.stackingFactor(), 0.5F,
                "profile stacking factor");
        requireNear(half.effectivenessFactor(), 0.5F,
                "cluster effectiveness factor");
        requireNear(half.outerStrength(), full.outerStrength() * 0.5F,
                "outer guidance scaling");
        requireNear(half.directionalStrength(),
                full.directionalStrength() * 0.5F,
                "directional jitter scaling");
        requireNear(half.severeStrength(), full.severeStrength() * 0.5F,
                "severe jitter scaling");
        requireNear(DirectionalJammingService.severeStateChance(
                        half, 0.25F, 0.75F), 0.375F,
                "dropout and IFF cluster scaling");
        requireNear(half.severeStateScale(), 0.5F,
                "synthetic-contact count scaling");
    }

    private static void verifyJammingTrackMetadata() {
        RadarTrack original = new RadarTrack(
                "d8257dad-e25d-4aa6-8a84-d37cabc26676",
                new Vec3(10.0, 20.0, 30.0),
                new Vec3(0.25, -0.5, 0.75), 42L,
                TrackCategory.SABLE, "Sable:ship", 6.0f);
        original.setFriendly(true);
        original.setSynthetic(true);
        UUID silhouetteId = UUID.fromString(original.getId());
        original.setSilhouette(silhouetteId,
                SyntheticSableSilhouetteFactory.REVISION,
                SableSilhouetteStatus.READY);
        original.setJammingData(new RadarTrack.JammingData(
                "minecraft:overworld|12", 0.25f, 0.5f, 0.75f, 0.55f,
                new Vec3(1.0, 2.0, 3.0),
                new Vec3(0.1, 0.2, 0.3), 987654321L));

        RadarTrack restored = RadarTrack.deserializeNBT(
                original.serializeNBT());
        require(restored.isSynthetic(),
                "synthetic marker must survive track serialization");
        require(restored.isFriendly(),
                "friendly marker must survive track serialization");
        require(restored.getJammingData() != null,
                "jamming metadata must survive track serialization");
        requireNear(restored.getJammingData().severeStrength(), 0.75f,
                "serialized severe strength");
        requireNear(restored.getJammingData().friendlyOutageChance(), 0.55f,
                "serialized friendly outage chance");
        require(restored.getJammingData().guidancePositionOffset()
                        .distanceToSqr(new Vec3(1.0, 2.0, 3.0)) <= EPSILON,
                "serialized guidance position offset");
        require(restored.getJammingData().sampleToken() == 987654321L,
                "serialized jamming sample token");
        require(silhouetteId.equals(restored.getSilhouetteId()),
                "synthetic silhouette ID must survive track serialization");
        require(restored.getSilhouetteRevision()
                        == SyntheticSableSilhouetteFactory.REVISION,
                "synthetic silhouette revision must survive track serialization");
        require(restored.getSilhouetteStatus() == SableSilhouetteStatus.READY,
                "synthetic silhouette status must survive track serialization");
    }

    private static void verifyJammedObservationWins() {
        RadarTrack clean = new RadarTrack("target", Vec3.ZERO, Vec3.ZERO,
                100L, TrackCategory.CONTRAPTION, "test", 1.0f);
        RadarTrack jammed = new RadarTrack("target", Vec3.ZERO, Vec3.ZERO,
                50L, TrackCategory.CONTRAPTION, "test", 1.0f);
        jammed.setJammingData(new RadarTrack.JammingData(
                "radar", 0.1f, 0.0f, 0.0f, 0.0f,
                Vec3.ZERO, Vec3.ZERO, 1L));
        require(DirectionalJammingService.preferObservation(clean, jammed)
                        == jammed,
                "jammed report must win over a newer clean report");
        require(DirectionalJammingService.preferObservation(jammed, clean)
                        == jammed,
                "jammed report must win regardless of merge order");

        RadarTrack stronger = new RadarTrack("target", Vec3.ZERO, Vec3.ZERO,
                25L, TrackCategory.CONTRAPTION, "test", 1.0f);
        stronger.setJammingData(new RadarTrack.JammingData(
                "nearby-radar", 0.2f, 0.3f, 0.4f, 0.1f,
                Vec3.ZERO, Vec3.ZERO, 2L));
        require(DirectionalJammingService.preferObservation(jammed, stronger)
                        == stronger,
                "strongest overlapping jammed observation must win");
        require(DirectionalJammingService.preferObservation(stronger, jammed)
                        == stronger,
                "strongest overlap selection must ignore merge order");
    }

    private static void verifySilhouetteJitterAlignment() {
        Vec3 physicalCenter = new Vec3(100.0, 40.0, -25.0);
        Vec3 reportedCenter = new Vec3(112.0, 37.0, 4.0);
        Vec3 offset = RadarTrackUtil.getReportedPositionOffset(
                reportedCenter, physicalCenter);
        require(offset.distanceToSqr(new Vec3(12.0, -3.0, 29.0))
                        <= EPSILON,
                "silhouette must receive the full reported-track offset");

        Vec3 physicalSilhouettePoint = new Vec3(103.0, 40.0, -27.0);
        Vec3 shiftedPoint = physicalSilhouettePoint.add(offset);
        Vec3 relativeToIcon = shiftedPoint.subtract(reportedCenter);
        Vec3 relativeToShip = physicalSilhouettePoint.subtract(physicalCenter);
        require(relativeToIcon.distanceToSqr(relativeToShip) <= EPSILON,
                "silhouette shape must remain anchored to the jittered icon");
    }

    private static void verifyProceduralFakeShipSilhouettes() {
        UUID id = UUID.fromString("43f4f848-a26c-4d0f-91b8-f919ce370ab6");
        SyntheticSableSilhouetteFactory.Profile firstProfile =
                SyntheticSableSilhouetteFactory.profile(id);
        SyntheticSableSilhouetteFactory.Profile secondProfile =
                SyntheticSableSilhouetteFactory.profile(id);
        require(firstProfile.equals(secondProfile),
                "fake hull profile must be deterministic for its track ID");
        require(firstProfile.length() >= 12.0 && firstProfile.length() <= 36.0,
                "fake hull length must remain in the configured design range");
        require(firstProfile.beam() >= 5.0 && firstProfile.beam() <= 14.0,
                "fake hull beam must remain in the configured design range");
        require(firstProfile.height() >= 3.0 && firstProfile.height() <= 8.0,
                "fake hull height must remain in the configured design range");
        require(firstProfile.headingDegrees() >= 0.0F
                        && firstProfile.headingDegrees() < 360.0F,
                "fake hull heading must be normalized");

        SubLevelSilhouette firstHull =
                SyntheticSableSilhouetteFactory.create(id);
        SubLevelSilhouette secondHull =
                SyntheticSableSilhouetteFactory.create(id);
        require(!firstHull.isEmpty(), "fake hull must contain visible geometry");
        require(firstHull.localBoxes().equals(secondHull.localBoxes()),
                "fake hull geometry must be deterministic for its track ID");
        SubLevelSilhouette.ProjectedSilhouette projected =
                SyntheticSableSilhouetteFactory.project(id, firstHull,
                        SubLevelSilhouette.ProjectionSettings
                                .distantContactDefault());
        require(!projected.isEmpty(),
                "fake hull must produce a drawable top-down silhouette");

        Set<Integer> archetypes = new HashSet<>();
        for (int index = 1; index <= 64; index++) {
            archetypes.add(SyntheticSableSilhouetteFactory.profile(
                    new UUID(0L, index)).archetype());
        }
        require(archetypes.equals(Set.of(0, 1, 2)),
                "deterministic fake hull generation must exercise all archetypes");
    }

    private static void verifyFakeHullConfigToggle() {
        UUID id = UUID.fromString("e2088232-1646-468b-a5f4-a9ae15ded056");
        RadarTrack fakeShip = new RadarTrack(id.toString(), Vec3.ZERO,
                Vec3.ZERO, 1L, TrackCategory.SABLE, "Sable:ship", 5.0F);
        fakeShip.setSynthetic(true);

        DirectionalJammingService.applySyntheticShipSilhouetteMetadata(
                fakeShip, true);
        require(id.equals(fakeShip.getSilhouetteId()),
                "enabled fake hull generation must attach ghost-owned metadata");
        require(fakeShip.getSilhouetteRevision()
                        == SyntheticSableSilhouetteFactory.REVISION,
                "enabled fake hull generation must use the procedural revision");
        require(fakeShip.getSilhouetteStatus() == SableSilhouetteStatus.READY,
                "procedural fake hull metadata must be immediately drawable");

        DirectionalJammingService.applySyntheticShipSilhouetteMetadata(
                fakeShip, false);
        require(fakeShip.getSilhouetteId() == null,
                "disabled fake hull generation must remove silhouette metadata");
        require(fakeShip.isSynthetic(),
                "disabling fake hulls must leave the fake contact itself intact");
    }

    private static void verifyFakeShipRarityGate() {
        require(DirectionalJammingService.shouldCreateFakeShip(
                        true, 0.10F, 0.099F),
                "a roll below the fake-ship chance must create a ship contact");
        require(!DirectionalJammingService.shouldCreateFakeShip(
                        true, 0.10F, 0.10F),
                "a roll at the fake-ship chance must remain an ordinary contact");
        require(!DirectionalJammingService.shouldCreateFakeShip(
                        false, 1.0F, 0.0F),
                "fake ship contacts require Sable compatibility");
        require(!DirectionalJammingService.shouldCreateFakeShip(
                        true, 0.0F, 0.0F),
                "zero fake-ship chance must disable fake ships");
        require(DirectionalJammingService.shouldCreateFakeShip(
                        true, 1.0F, 0.999F),
                "full fake-ship chance must accept every valid roll");
    }

    private static void requireOrientation(Direction mountFacing, float yaw,
                                           float pitch,
                                           Vector3f expectedForward,
                                           Vector3f expectedUp,
                                           String label) {
        Quaternionf rotation = JammerOrientation.rotation(mountFacing, yaw,
                pitch);
        Vector3f actualForward = new Vector3f(0, 0, -1).rotate(rotation);
        Vector3f actualUp = new Vector3f(0, 1, 0).rotate(rotation);
        requireFinite(actualForward, label + " forward");
        requireFinite(actualUp, label + " up");
        requireVectorNear(actualForward, expectedForward, label + " forward");
        requireVectorNear(actualUp, expectedUp, label + " up");
    }

    private static void requireFinite(Vector3f vector, String label) {
        require(Float.isFinite(vector.x()) && Float.isFinite(vector.y())
                        && Float.isFinite(vector.z()),
                label + " contains a non-finite component: " + vector);
    }

    private static void requireVectorNear(Vector3f actual, Vector3f expected,
                                          String label) {
        require(actual.distance(expected) <= EPSILON,
                label + ": expected " + expected + ", got " + actual);
    }

    private static void requireNear(float actual, float expected,
                                    String label) {
        require(Math.abs(actual - expected) <= EPSILON,
                label + ": expected " + expected + ", got " + actual);
    }

    private static void requireArrayEquals(int[] actual, int[] expected,
                                           String label) {
        require(java.util.Arrays.equals(actual, expected),
                label + ": expected " + java.util.Arrays.toString(expected)
                        + ", got " + java.util.Arrays.toString(actual));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
