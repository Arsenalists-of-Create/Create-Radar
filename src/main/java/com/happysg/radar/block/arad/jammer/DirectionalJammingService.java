package com.happysg.radar.block.arad.jammer;

import com.happysg.radar.block.arad.aradnetworks.ARADData;
import com.happysg.radar.block.arad.aradnetworks.RadarContactRegistry;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.sable.SableSilhouetteStatus;
import com.happysg.radar.compat.sable.SyntheticSableSilhouetteFactory;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.config.server.RadarServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative frequency matching and radar-observation corruption. */
public final class DirectionalJammingService {
    private static final long CONTRIBUTION_TTL_TICKS = 2L;
    private static final long VIEW_TTL_TICKS = 200L;
    private static final double VECTOR_EPSILON = 1.0E-12;
    private static final Map<ResourceKey<Level>, DimensionState> DIMENSIONS =
            new HashMap<>();

    private DirectionalJammingService() {
    }

    public record Profile(
            float jammerRpm,
            float radarRpm,
            float rollingRate,
            float rpmDifference,
            float rateBonus,
            float outerThreshold,
            float directionalThreshold,
            float severeThreshold,
            float alignmentDegrees,
            float alignmentFactor,
            float rangeFactor,
            int clusterSize,
            float stackingFactor,
            float effectivenessFactor,
            float outerStrength,
            float directionalStrength,
            float severeStrength,
            float severeProgress
    ) {
        public static final Profile INACTIVE = new Profile(
                0.0F, 0.0F, 0.0F, Float.POSITIVE_INFINITY,
                0.0F, 15.0F, 10.0F, 5.0F,
                180.0F, 0.0F, 0.0F, 1, 1.0F, 0.0F,
                0.0F, 0.0F, 0.0F, 0.0F);

        public boolean active() {
            return outerStrength > 0.0F;
        }

        public String tierName() {
            if (severeStrength > 0.0F) {
                return "severe";
            }
            if (directionalStrength > 0.0F) {
                return "directional";
            }
            return outerStrength > 0.0F ? "guidance" : "inactive";
        }

        Profile balanced(float requestedRangeFactor, int requestedClusterSize) {
            float safeRangeFactor = clamp(requestedRangeFactor, 0.0F, 1.0F);
            int safeClusterSize = Math.max(1, requestedClusterSize);
            float safeStackingFactor = 1.0F / safeClusterSize;
            float safeEffectivenessFactor = safeRangeFactor
                    * safeStackingFactor;
            return new Profile(jammerRpm, radarRpm, rollingRate, rpmDifference,
                    rateBonus, outerThreshold, directionalThreshold,
                    severeThreshold, alignmentDegrees, alignmentFactor,
                    safeRangeFactor, safeClusterSize, safeStackingFactor,
                    safeEffectivenessFactor,
                    outerStrength * safeEffectivenessFactor,
                    directionalStrength * safeEffectivenessFactor,
                    severeStrength * safeEffectivenessFactor,
                    severeProgress);
        }

        float severeStateScale() {
            return alignmentFactor * effectivenessFactor;
        }
    }

    private record Tuning(
            boolean enabled,
            boolean syntheticShipSilhouettes,
            float syntheticShipChance,
            float fullEffectRangeRatio,
            float zeroEffectRangeRatio,
            float clusterRadiusBlocks,
            float spilloverRadiusBlocks,
            float outerThreshold,
            float directionalThreshold,
            float severeThreshold,
            float rollingRateScale,
            float maxRateBonus,
            float onsetStrength,
            float fullAlignmentDegrees,
            float zeroAlignmentDegrees,
            float coneHalfAngleDegrees,
            float outerErrorDegrees,
            float directionalErrorDegrees,
            float severeErrorDegrees,
            int outerPeriodTicks,
            int directionalPeriodTicks,
            int severePeriodTicks,
            int statePeriodTicks,
            float minDropoutChance,
            float maxDropoutChance,
            float minFriendlyOutageChance,
            float maxFriendlyOutageChance,
            int friendlyPeriodTicks,
            int minGhosts,
            int maxGhosts,
            int minGhostLifetimeTicks,
            int maxGhostLifetimeTicks
    ) {
        static Tuning current() {
            RadarServerConfig config = RadarConfig.server();
            if (config == null) {
                return defaults();
            }
            float[] rangeRatios = {
                    clamp(config.directionalJammingFullEffectRangeRatio.get(),
                            0.0F, 4.0F),
                    clamp(config.directionalJammingZeroEffectRangeRatio.get(),
                            0.0F, 4.0F)
            };
            java.util.Arrays.sort(rangeRatios);
            float[] thresholds = {
                    finitePositive(config.directionalJammingOuterRpmTolerance.get()),
                    finitePositive(config.directionalJammingDirectionalRpmTolerance.get()),
                    finitePositive(config.directionalJammingSevereRpmTolerance.get())
            };
            java.util.Arrays.sort(thresholds);
            float severe = thresholds[0];
            float directional = thresholds[1];
            float outer = thresholds[2];
            float fullAlignment = clamp(config.directionalJammingFullAlignment.get(),
                    0.0F, 180.0F);
            float zeroAlignment = clamp(config.directionalJammingZeroAlignment.get(),
                    0.0F, 180.0F);
            if (zeroAlignment < fullAlignment) {
                float swap = zeroAlignment;
                zeroAlignment = fullAlignment;
                fullAlignment = swap;
            }
            float minDropout = clamp(config.directionalJammingMinDropoutChance.get(),
                    0.0F, 1.0F);
            float maxDropout = clamp(config.directionalJammingMaxDropoutChance.get(),
                    0.0F, 1.0F);
            if (maxDropout < minDropout) {
                float swap = maxDropout;
                maxDropout = minDropout;
                minDropout = swap;
            }
            float minFriendly = clamp(
                    config.directionalJammingMinFriendlyOutageChance.get(),
                    0.0F, 1.0F);
            float maxFriendly = clamp(
                    config.directionalJammingMaxFriendlyOutageChance.get(),
                    0.0F, 1.0F);
            if (maxFriendly < minFriendly) {
                float swap = maxFriendly;
                maxFriendly = minFriendly;
                minFriendly = swap;
            }
            int minGhosts = Math.max(0, config.directionalJammingMinGhosts.get());
            int maxGhosts = Math.max(0, config.directionalJammingMaxGhosts.get());
            if (maxGhosts < minGhosts) {
                int swap = maxGhosts;
                maxGhosts = minGhosts;
                minGhosts = swap;
            }
            int minLifetime = Math.max(1,
                    config.directionalJammingMinGhostLifetime.get());
            int maxLifetime = Math.max(1,
                    config.directionalJammingMaxGhostLifetime.get());
            if (maxLifetime < minLifetime) {
                int swap = maxLifetime;
                maxLifetime = minLifetime;
                minLifetime = swap;
            }
            return new Tuning(
                    config.directionalJammingEnabled.get(),
                    config.directionalJammingGenerateFakeHulls.get(),
                    clamp(config.directionalJammingFakeShipChance.get(),
                            0.0F, 1.0F),
                    rangeRatios[0], rangeRatios[1],
                    finitePositive(config.directionalJammingClusterRadius.get()),
                    finitePositive(config.directionalJammingSpilloverRadius.get()),
                    outer, directional,
                    severe,
                    finitePositive(config.directionalJammingRollingRateScale.get()),
                    finitePositive(config.directionalJammingMaxRateBonus.get()),
                    clamp(config.directionalJammingTierOnsetStrength.get(),
                            0.0F, 1.0F),
                    fullAlignment, zeroAlignment,
                    clamp(config.directionalJammingConeHalfAngle.get(),
                            0.0F, 180.0F),
                    finitePositive(config.directionalJammingOuterError.get()),
                    finitePositive(config.directionalJammingDirectionalError.get()),
                    finitePositive(config.directionalJammingSevereError.get()),
                    Math.max(1, config.directionalJammingOuterPeriod.get()),
                    Math.max(1, config.directionalJammingDirectionalPeriod.get()),
                    Math.max(1, config.directionalJammingSeverePeriod.get()),
                    Math.max(1, config.directionalJammingStatePeriod.get()),
                    minDropout, maxDropout, minFriendly, maxFriendly,
                    Math.max(1, config.directionalJammingFriendlyPeriod.get()),
                    minGhosts, maxGhosts, minLifetime, maxLifetime
            );
        }

        private static Tuning defaults() {
            return new Tuning(true, true, 0.10F,
                    0.50F, 0.85F, 50.0F, 25.0F,
                    15.0F, 10.0F, 5.0F,
                    0.5F, 15.0F, 0.10F, 3.0F, 15.0F, 30.0F,
                    1.5F, 3.0F, 20.0F, 40, 10, 5, 20,
                    0.10F, 0.65F, 0.25F, 0.75F, 40,
                    2, 6, 40, 120);
        }
    }

    private record Contribution(
            BlockPos jammerPos,
            String targetSourceId,
            Vec3 jammerWorldPosition,
            Vec3 targetRadarWorldPosition,
            BlockPos aradGroupRoot,
            Set<UUID> connectedSublevels,
            float rangeFactor,
            Profile profile,
            long heartbeatTick
    ) {
        private Contribution {
            jammerPos = jammerPos.immutable();
            aradGroupRoot = aradGroupRoot == null ? null
                    : aradGroupRoot.immutable();
            connectedSublevels = connectedSublevels == null ? Set.of()
                    : Set.copyOf(connectedSublevels);
        }

        Contribution withProfile(Profile replacement) {
            return new Contribution(jammerPos, targetSourceId,
                    jammerWorldPosition, targetRadarWorldPosition,
                    aradGroupRoot, connectedSublevels, rangeFactor, replacement,
                    heartbeatTick);
        }
    }

    record ClusterIdentity(Vec3 worldPosition, BlockPos aradGroupRoot,
                           Set<UUID> connectedSublevels) {
        ClusterIdentity {
            connectedSublevels = connectedSublevels == null ? Set.of()
                    : Set.copyOf(connectedSublevels);
        }
    }

    private static final class DimensionState {
        final Map<BlockPos, Contribution> contributions = new HashMap<>();
        final Map<UUID, RadarViewState> views = new HashMap<>();
    }

    private static final class RadarViewState {
        final Map<String, RadarTrack> realViews = new LinkedHashMap<>();
        final Map<UUID, Ghost> ghosts = new LinkedHashMap<>();
        long nextGhostSequence;
        long lastAccessTick;
    }

    private record Ghost(
            RadarTrack track,
            Vec3 basePosition,
            Vec3 velocity,
            long spawnTick,
            long expiryTick
    ) {
    }

    private record NoiseSample(Vec3 offset, Vec3 velocityOffset, long token) {
        static final NoiseSample ZERO = new NoiseSample(Vec3.ZERO, Vec3.ZERO, 0L);

        NoiseSample add(NoiseSample other) {
            return new NoiseSample(offset.add(other.offset),
                    velocityOffset.add(other.velocityOffset),
                    mix64(token ^ other.token));
        }
    }

    public static Profile calculateProfile(
            float jammerRpm,
            float radarRollingRpm,
            float radarRollingRate,
            float alignmentDegrees
    ) {
        return calculateProfile(jammerRpm, radarRollingRpm,
                radarRollingRate, alignmentDegrees, Tuning.current());
    }

    static Profile calculateProfile(
            float jammerRpm,
            float radarRollingRpm,
            float radarRollingRate,
            float alignmentDegrees,
            Tuning tuning
    ) {
        float safeJammer = finitePositive(Math.abs(jammerRpm));
        float safeRadar = finitePositive(Math.abs(radarRollingRpm));
        float safeRate = finitePositive(Math.abs(radarRollingRate));
        float safeAlignment = Float.isFinite(alignmentDegrees)
                ? clamp(Math.abs(alignmentDegrees), 0.0F, 180.0F) : 180.0F;
        float bonus = Math.min(tuning.maxRateBonus,
                safeRate * tuning.rollingRateScale);
        float outer = tuning.outerThreshold + bonus;
        float directional = tuning.directionalThreshold + bonus;
        float severe = tuning.severeThreshold + bonus;
        float difference = Math.abs(safeJammer - safeRadar);
        float alignmentFactor = alignmentFactor(safeAlignment,
                tuning.fullAlignmentDegrees, tuning.zeroAlignmentDegrees);
        if (!tuning.enabled || safeJammer <= 1.0E-4F) {
            alignmentFactor = 0.0F;
        }
        float outerRaw = tierStrength(difference, outer, directional,
                tuning.onsetStrength);
        float directionalRaw = tierStrength(difference, directional, severe,
                tuning.onsetStrength);
        float severeProgress = inverseProgress(difference, severe, 0.0F);
        float severeRaw = difference <= severe
                ? Mth.lerp(severeProgress, tuning.onsetStrength, 1.0F) : 0.0F;
        return new Profile(safeJammer, safeRadar, safeRate, difference, bonus,
                outer, directional, severe, safeAlignment, alignmentFactor,
                1.0F, 1, 1.0F, 1.0F,
                outerRaw * alignmentFactor,
                directionalRaw * alignmentFactor,
                severeRaw * alignmentFactor,
                severeProgress);
    }

    static float calculateRangeFactor(double distance, float radarRange,
                                      float firstRatio, float secondRatio) {
        if (!Double.isFinite(distance) || distance < 0.0
                || !Float.isFinite(radarRange) || radarRange <= 0.0F) {
            return 0.0F;
        }
        float safeFirstRatio = finitePositive(firstRatio);
        float safeSecondRatio = finitePositive(secondRatio);
        float fullRatio = clamp(Math.min(safeFirstRatio, safeSecondRatio),
                0.0F, 4.0F);
        float zeroRatio = clamp(Math.max(safeFirstRatio, safeSecondRatio),
                0.0F, 4.0F);
        double ratio = distance / radarRange;
        if (ratio <= fullRatio) {
            return 1.0F;
        }
        if (ratio >= zeroRatio || zeroRatio <= fullRatio) {
            return 0.0F;
        }
        return clamp((float) ((zeroRatio - ratio)
                / (zeroRatio - fullRatio)), 0.0F, 1.0F);
    }

    public static Profile heartbeat(
            ServerLevel level,
            BlockPos jammerPos,
            String targetSourceId,
            Vec3 jammerWorldPosition,
            Vec3 targetRadarWorldPosition,
            float targetRadarRange,
            float jammerRpm,
            float radarRollingRpm,
            float radarRollingRate,
            float alignmentDegrees
    ) {
        if (level == null || jammerPos == null || targetSourceId == null
                || targetSourceId.isBlank() || !finite(jammerWorldPosition)
                || !finite(targetRadarWorldPosition)) {
            return Profile.INACTIVE;
        }
        Profile baseProfile = calculateProfile(jammerRpm, radarRollingRpm,
                radarRollingRate, alignmentDegrees);
        Tuning tuning = Tuning.current();
        float rangeFactor = calculateRangeFactor(
                jammerWorldPosition.distanceTo(targetRadarWorldPosition),
                targetRadarRange, tuning.fullEffectRangeRatio,
                tuning.zeroEffectRangeRatio);
        Profile rangedProfile = baseProfile.balanced(rangeFactor, 1);
        DimensionState state = DIMENSIONS.computeIfAbsent(level.dimension(),
                ignored -> new DimensionState());
        BlockPos key = jammerPos.immutable();
        if (!rangedProfile.active()) {
            state.contributions.remove(key);
            return rangedProfile;
        }
        BlockPos aradGroupRoot = ARADData.get(level).getRwrForJammer(
                level.dimension(), key);
        Set<UUID> connectedSublevels =
                PhysicsHandler.getConnectedSublevelIds(level, key);
        long now = level.getGameTime();
        state.contributions.put(key, new Contribution(key, targetSourceId,
                jammerWorldPosition, targetRadarWorldPosition,
                aradGroupRoot, connectedSublevels, rangeFactor, baseProfile,
                now));
        return effectiveContributions(state, now, tuning).stream()
                .filter(contribution -> contribution.jammerPos.equals(key))
                .map(Contribution::profile)
                .findFirst()
                .orElse(rangedProfile);
    }

    public static void remove(ServerLevel level, BlockPos jammerPos) {
        DimensionState state = level == null ? null : DIMENSIONS.get(level.dimension());
        if (state != null && jammerPos != null) {
            state.contributions.remove(jammerPos);
        }
    }

    public static Collection<RadarTrack> reportedTracks(
            ServerLevel level,
            IRadar radar,
            Collection<RadarTrack> rawTracks
    ) {
        if (level == null || radar == null || rawTracks == null
                || !supportsRadarType(radar.getRadarType())) {
            return rawTracks == null ? List.of() : rawTracks;
        }
        Tuning tuning = Tuning.current();
        if (!tuning.enabled) {
            return rawTracks;
        }
        String sourceId = RadarContactRegistry.radarSourceId(level,
                radar.getWorldPos());
        Vec3 radarWorldPosition = PhysicsHandler.getWorldVec(level,
                radar.getWorldPos().getCenter());
        if (!finite(radarWorldPosition)) {
            return rawTracks;
        }
        Contribution contribution = strongestContribution(level, sourceId,
                radarWorldPosition, tuning);
        if (contribution == null) {
            return rawTracks;
        }
        UUID emitterId = radar.getEmitterId();
        if (emitterId == null) {
            return rawTracks;
        }
        DimensionState dimension = DIMENSIONS.computeIfAbsent(level.dimension(),
                ignored -> new DimensionState());
        RadarViewState view = dimension.views.computeIfAbsent(emitterId,
                ignored -> new RadarViewState());
        long now = level.getGameTime();
        view.lastAccessTick = now;

        List<RadarTrack> raw = rawTracks.stream()
                .filter(java.util.Objects::nonNull).toList();
        List<RadarTrack> reported = new ArrayList<>(raw.size()
                + tuning.maxGhosts);
        Map<String, Boolean> seen = new HashMap<>();
        for (RadarTrack track : raw) {
            seen.put(track.getId(), Boolean.TRUE);
            if (shouldDropRealTrack(track, contribution, tuning, now)) {
                continue;
            }
            RadarTrack proxy = view.realViews.computeIfAbsent(track.getId(),
                    ignored -> track.copy());
            proxy.copyMutableStateFrom(track);
            applyCorruption(proxy, track, radarWorldPosition, contribution,
                    tuning, now);
            reported.add(proxy);
        }
        view.realViews.keySet().removeIf(id -> !seen.containsKey(id));
        maintainGhosts(view, raw, radar, radarWorldPosition, contribution,
                tuning, now);
        for (Ghost ghost : view.ghosts.values()) {
            updateGhost(ghost, contribution, tuning, now);
            reported.add(ghost.track());
        }
        return List.copyOf(reported);
    }

    public static RadarTrack preferObservation(RadarTrack first,
                                               RadarTrack second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        RadarTrack.JammingData firstJam = first.getJammingData();
        RadarTrack.JammingData secondJam = second.getJammingData();
        if (firstJam != null || secondJam != null) {
            if (firstJam == null) {
                return second;
            }
            if (secondJam == null) {
                return first;
            }
            int priority = Float.compare(secondJam.priorityScore(),
                    firstJam.priorityScore());
            if (priority != 0) {
                return priority > 0 ? second : first;
            }
        }
        return second.scannedTime() >= first.scannedTime() ? second : first;
    }

    /** Applies severe IFF outages after ordinary network-secret identification. */
    public static void applyFriendlyInterference(RadarTrack track, long gameTime) {
        if (track == null || !track.isFriendly()
                || track.getJammingData() == null
                || track.getJammingData().friendlyOutageChance() <= 0.0F) {
            return;
        }
        Tuning tuning = Tuning.current();
        float chance = clamp(track.getJammingData().friendlyOutageChance(),
                0.0F, 1.0F);
        long epoch = Math.floorDiv(gameTime, tuning.friendlyPeriodTicks);
        if (unitRandom(seed(track.getId(), track.getJammingData().radarSourceId(),
                epoch, 0x6A09E667F3BCC909L)) < chance) {
            track.setFriendly(false);
        }
    }

    public static void tick(ServerLevel level) {
        DimensionState state = level == null ? null : DIMENSIONS.get(level.dimension());
        if (state == null) {
            return;
        }
        long now = level.getGameTime();
        state.contributions.values().removeIf(contribution ->
                now - contribution.heartbeatTick < 0
                        || now - contribution.heartbeatTick
                        > CONTRIBUTION_TTL_TICKS);
        state.views.values().removeIf(view -> now - view.lastAccessTick < 0
                || now - view.lastAccessTick > VIEW_TTL_TICKS);
        if (state.contributions.isEmpty() && state.views.isEmpty()) {
            DIMENSIONS.remove(level.dimension());
        }
    }

    public static void clear(ServerLevel level) {
        if (level != null) {
            DIMENSIONS.remove(level.dimension());
        }
    }

    private static Contribution strongestContribution(ServerLevel level,
                                                       String sourceId,
                                                       Vec3 radarWorldPosition,
                                                       Tuning tuning) {
        DimensionState state = DIMENSIONS.get(level.dimension());
        if (state == null) {
            return null;
        }
        long now = level.getGameTime();
        double spilloverRadiusSqr = tuning.spilloverRadiusBlocks
                * tuning.spilloverRadiusBlocks;
        return effectiveContributions(state, now, tuning).stream()
                .filter(contribution -> contributionAffectsRadar(
                        contribution.targetSourceId,
                        contribution.targetRadarWorldPosition,
                        sourceId, radarWorldPosition,
                        tuning.spilloverRadiusBlocks,
                        spilloverRadiusSqr))
                .max(Comparator.<Contribution>comparingDouble(contribution ->
                        contribution.profile.severeStrength * 100.0F
                                + contribution.profile.directionalStrength
                                * 10.0F
                                + contribution.profile.outerStrength)
                        .thenComparingLong(contribution ->
                                contribution.jammerPos.asLong()))
                .orElse(null);
    }

    static boolean contributionAffectsRadar(
            String targetSourceId,
            Vec3 targetRadarWorldPosition,
            String radarSourceId,
            Vec3 radarWorldPosition,
            float requestedRadius
    ) {
        float radius = finitePositive(requestedRadius);
        return contributionAffectsRadar(targetSourceId,
                targetRadarWorldPosition, radarSourceId,
                radarWorldPosition, radius, radius * radius);
    }

    private static boolean contributionAffectsRadar(
            String targetSourceId,
            Vec3 targetRadarWorldPosition,
            String radarSourceId,
            Vec3 radarWorldPosition,
            float radius,
            double radiusSqr
    ) {
        if (targetSourceId != null && targetSourceId.equals(radarSourceId)) {
            return true;
        }
        return radius > 0.0F && finite(targetRadarWorldPosition)
                && finite(radarWorldPosition)
                && targetRadarWorldPosition.distanceToSqr(radarWorldPosition)
                <= radiusSqr;
    }

    private static List<Contribution> effectiveContributions(
            DimensionState state, long now, Tuning tuning) {
        List<Contribution> active = state.contributions.values().stream()
                .filter(contribution -> isActiveContribution(
                        contribution.profile, contribution.rangeFactor,
                        now, contribution.heartbeatTick))
                .toList();
        if (active.isEmpty()) {
            return List.of();
        }
        List<ClusterIdentity> identities = active.stream()
                .map(contribution -> new ClusterIdentity(
                        contribution.jammerWorldPosition,
                        contribution.aradGroupRoot,
                        contribution.connectedSublevels))
                .toList();
        int[] sizes = clusterSizes(identities, tuning.clusterRadiusBlocks);
        List<Contribution> effective = new ArrayList<>(active.size());
        for (int index = 0; index < active.size(); index++) {
            Contribution contribution = active.get(index);
            effective.add(contribution.withProfile(
                    contribution.profile.balanced(
                            contribution.rangeFactor, sizes[index])));
        }
        return List.copyOf(effective);
    }

    static boolean isActiveContribution(Profile profile, float rangeFactor,
                                        long now, long heartbeatTick) {
        long age = now - heartbeatTick;
        return profile != null && profile.active() && rangeFactor > 0.0F
                && age >= 0 && age <= CONTRIBUTION_TTL_TICKS;
    }

    static boolean supportsRadarType(String radarType) {
        return !"sonar".equalsIgnoreCase(radarType);
    }

    static int[] clusterSizes(List<ClusterIdentity> identities,
                              float requestedRadius) {
        int size = identities == null ? 0 : identities.size();
        int[] parents = new int[size];
        int[] ranks = new int[size];
        for (int index = 0; index < size; index++) {
            parents[index] = index;
        }
        float radius = finitePositive(requestedRadius);
        double radiusSqr = radius * radius;
        for (int first = 0; first < size; first++) {
            for (int second = first + 1; second < size; second++) {
                if (clusterConnected(identities.get(first),
                        identities.get(second), radius, radiusSqr)) {
                    union(parents, ranks, first, second);
                }
            }
        }
        int[] componentCounts = new int[size];
        for (int index = 0; index < size; index++) {
            componentCounts[find(parents, index)]++;
        }
        int[] result = new int[size];
        for (int index = 0; index < size; index++) {
            result[index] = componentCounts[find(parents, index)];
        }
        return result;
    }

    private static boolean clusterConnected(ClusterIdentity first,
                                            ClusterIdentity second,
                                            float radius,
                                            double radiusSqr) {
        if (radius > 0.0F && finite(first.worldPosition)
                && finite(second.worldPosition)
                && first.worldPosition.distanceToSqr(second.worldPosition)
                <= radiusSqr) {
            return true;
        }
        if (first.aradGroupRoot != null
                && first.aradGroupRoot.equals(second.aradGroupRoot)) {
            return true;
        }
        for (UUID id : first.connectedSublevels) {
            if (second.connectedSublevels.contains(id)) {
                return true;
            }
        }
        return false;
    }

    private static int find(int[] parents, int index) {
        int root = index;
        while (parents[root] != root) {
            root = parents[root];
        }
        while (parents[index] != index) {
            int next = parents[index];
            parents[index] = root;
            index = next;
        }
        return root;
    }

    private static void union(int[] parents, int[] ranks,
                              int first, int second) {
        int firstRoot = find(parents, first);
        int secondRoot = find(parents, second);
        if (firstRoot == secondRoot) {
            return;
        }
        if (ranks[firstRoot] < ranks[secondRoot]) {
            parents[firstRoot] = secondRoot;
        } else if (ranks[firstRoot] > ranks[secondRoot]) {
            parents[secondRoot] = firstRoot;
        } else {
            parents[secondRoot] = firstRoot;
            ranks[firstRoot]++;
        }
    }

    private static void applyCorruption(
            RadarTrack proxy,
            RadarTrack raw,
            Vec3 radarWorldPosition,
            Contribution contribution,
            Tuning tuning,
            long now
    ) {
        boolean directional = withinDirectionalCone(raw.position(),
                radarWorldPosition, contribution.jammerWorldPosition,
                tuning.coneHalfAngleDegrees);
        NoiseSample outer = noise(raw.position(), radarWorldPosition,
                tuning.outerErrorDegrees * contribution.profile.outerStrength,
                tuning.outerPeriodTicks, now,
                seed(raw.getId(), contribution.targetSourceId, 0L,
                        0xBB67AE8584CAA73BL));
        NoiseSample visible = NoiseSample.ZERO;
        if (directional && contribution.profile.directionalStrength > 0.0F) {
            visible = visible.add(noise(raw.position(), radarWorldPosition,
                    tuning.directionalErrorDegrees
                            * contribution.profile.directionalStrength,
                    tuning.directionalPeriodTicks, now,
                    seed(raw.getId(), contribution.targetSourceId, 0L,
                            0x3C6EF372FE94F82BL)));
        }
        if (contribution.profile.severeStrength > 0.0F) {
            visible = visible.add(noise(raw.position(), radarWorldPosition,
                    tuning.severeErrorDegrees
                            * contribution.profile.severeStrength,
                    tuning.severePeriodTicks, now,
                    seed(raw.getId(), contribution.targetSourceId, 0L,
                            0xA54FF53A5F1D36F1L)));
        }
        NoiseSample guidance = outer.add(visible);
        proxy.setPosition(raw.position().add(visible.offset));
        proxy.setVelocity(raw.velocity().add(visible.velocityOffset));
        proxy.setJammingData(new RadarTrack.JammingData(
                contribution.targetSourceId,
                contribution.profile.outerStrength,
                directional ? contribution.profile.directionalStrength : 0.0F,
                contribution.profile.severeStrength,
                friendlyOutageChance(contribution.profile, tuning),
                guidance.offset,
                guidance.velocityOffset,
                mix64(guidance.token
                        ^ Float.floatToIntBits(contribution.profile.outerStrength)
                        ^ ((long) Float.floatToIntBits(
                        contribution.profile.directionalStrength) << 17)
                        ^ ((long) Float.floatToIntBits(
                        contribution.profile.severeStrength) << 33))));
        proxy.setSynthetic(false);
    }

    private static boolean shouldDropRealTrack(
            RadarTrack track,
            Contribution contribution,
            Tuning tuning,
            long now
    ) {
        if (contribution.profile.severeStrength <= 0.0F) {
            return false;
        }
        float chance = severeStateChance(contribution.profile,
                tuning.minDropoutChance, tuning.maxDropoutChance);
        long epoch = Math.floorDiv(now, tuning.statePeriodTicks);
        return unitRandom(seed(track.getId(), contribution.targetSourceId,
                epoch, 0x510E527FADE682D1L)) < chance;
    }

    private static void maintainGhosts(
            RadarViewState view,
            List<RadarTrack> raw,
            IRadar radar,
            Vec3 radarWorldPosition,
            Contribution contribution,
            Tuning tuning,
            long now
    ) {
        Iterator<Ghost> iterator = view.ghosts.values().iterator();
        while (iterator.hasNext()) {
            if (now >= iterator.next().expiryTick) {
                iterator.remove();
            }
        }
        if (contribution.profile.severeStrength <= 0.0F) {
            view.ghosts.clear();
            return;
        }
        int desired = Math.round(Mth.lerp(contribution.profile.severeProgress,
                tuning.minGhosts, tuning.maxGhosts)
                * contribution.profile.severeStateScale());
        desired = Mth.clamp(desired, 0, tuning.maxGhosts);
        while (view.ghosts.size() > desired) {
            UUID last = view.ghosts.keySet().stream().reduce((a, b) -> b)
                    .orElse(null);
            if (last == null) {
                break;
            }
            view.ghosts.remove(last);
        }
        while (view.ghosts.size() < desired) {
            Ghost ghost = createGhost(view, raw, radar, radarWorldPosition,
                    contribution, tuning, now);
            view.ghosts.put(UUID.fromString(ghost.track.getId()), ghost);
        }
    }

    private static Ghost createGhost(
            RadarViewState view,
            List<RadarTrack> raw,
            IRadar radar,
            Vec3 radarWorldPosition,
            Contribution contribution,
            Tuning tuning,
            long now
    ) {
        long sequence = view.nextGhostSequence++;
        long randomSeed = seed(contribution.targetSourceId, "ghost", sequence,
                0x9B05688C2B3E6C1FL);
        RandomSource random = RandomSource.create(randomSeed);
        UUID id = UUID.nameUUIDFromBytes((contribution.targetSourceId + "|"
                + sequence).getBytes(StandardCharsets.UTF_8));
        boolean createFakeShip = shouldCreateFakeShip(Mods.SABLE.isLoaded(),
                tuning.syntheticShipChance, random.nextFloat());
        List<RadarTrack> eligibleTemplates = createFakeShip ? List.of()
                : raw.stream()
                .filter(track -> track.trackCategory() != TrackCategory.SABLE)
                .toList();
        RadarTrack template = eligibleTemplates.isEmpty() ? null
                : eligibleTemplates.get(random.nextInt(eligibleTemplates.size()));
        TrackCategory category = createFakeShip ? TrackCategory.SABLE
                : template == null ? TrackCategory.CONTRAPTION
                : template.trackCategory();
        String entityType = createFakeShip ? "Sable:ship"
                : template == null ? "jamming:synthetic"
                : template.entityType();
        SyntheticSableSilhouetteFactory.Profile syntheticShipProfile =
                category == TrackCategory.SABLE && Mods.SABLE.isLoaded()
                        ? SyntheticSableSilhouetteFactory.profile(id) : null;
        float height = syntheticShipProfile == null
                ? template == null ? 4.0F : template.getEnityHeight()
                : (float) syntheticShipProfile.height();
        double radarRange = Math.max(32.0, radar.getRange());
        double radius = radarRange * (0.25 + random.nextDouble() * 0.65);
        double angle = random.nextDouble() * Math.PI * 2.0;
        double vertical = (random.nextDouble() - 0.5)
                * Math.min(64.0, radarRange * 0.15);
        Vec3 basePosition = radarWorldPosition.add(Math.cos(angle) * radius,
                vertical, Math.sin(angle) * radius);
        Vec3 velocity;
        if (syntheticShipProfile != null) {
            double heading = Math.toRadians(
                    syntheticShipProfile.headingDegrees());
            double speed = 0.05 + random.nextDouble() * 0.17;
            velocity = new Vec3(Math.sin(heading) * speed,
                    (random.nextDouble() - 0.5) * 0.08,
                    Math.cos(heading) * speed);
        } else {
            velocity = new Vec3((random.nextDouble() - 0.5) * 0.3,
                    (random.nextDouble() - 0.5) * 0.08,
                    (random.nextDouble() - 0.5) * 0.3);
        }
        RadarTrack track = new RadarTrack(id.toString(), basePosition, velocity,
                now, category, entityType, height);
        track.setSynthetic(true);
        applySyntheticShipSilhouetteMetadata(track,
                tuning.syntheticShipSilhouettes);
        if (category == TrackCategory.SABLE) {
            track.setFriendly(random.nextBoolean());
        }
        int lifetimeRange = tuning.maxGhostLifetimeTicks
                - tuning.minGhostLifetimeTicks;
        long lifetime = tuning.minGhostLifetimeTicks
                + (lifetimeRange <= 0 ? 0 : random.nextInt(lifetimeRange + 1));
        return new Ghost(track, basePosition, velocity, now, now + lifetime);
    }

    private static void updateGhost(Ghost ghost, Contribution contribution,
                                    Tuning tuning, long now) {
        Vec3 drifted = ghost.basePosition.add(
                ghost.velocity.scale(now - ghost.spawnTick));
        NoiseSample noise = noise(drifted, contribution.jammerWorldPosition,
                tuning.severeErrorDegrees
                        * contribution.profile.severeStrength,
                tuning.severePeriodTicks, now,
                seed(ghost.track.getId(), contribution.targetSourceId, 0L,
                        0x1F83D9ABFB41BD6BL));
        ghost.track.setPosition(drifted.add(noise.offset));
        ghost.track.setVelocity(ghost.velocity.add(noise.velocityOffset));
        ghost.track.setScannedTime(now);
        ghost.track.setSynthetic(true);
        applySyntheticShipSilhouetteMetadata(ghost.track,
                tuning.syntheticShipSilhouettes);
        ghost.track.setJammingData(new RadarTrack.JammingData(
                contribution.targetSourceId,
                contribution.profile.outerStrength,
                contribution.profile.directionalStrength,
                contribution.profile.severeStrength,
                friendlyOutageChance(contribution.profile, tuning),
                Vec3.ZERO, Vec3.ZERO, noise.token));
    }

    static float friendlyOutageChance(Profile profile, Tuning tuning) {
        return severeStateChance(profile, tuning.minFriendlyOutageChance,
                tuning.maxFriendlyOutageChance);
    }

    static float severeStateChance(Profile profile, float minimumChance,
                                   float maximumChance) {
        if (profile == null || profile.severeStrength <= 0.0F) {
            return 0.0F;
        }
        float lowChance = clamp(Math.min(minimumChance, maximumChance),
                0.0F, 1.0F);
        float highChance = clamp(Math.max(minimumChance, maximumChance),
                0.0F, 1.0F);
        return clamp(Mth.lerp(profile.severeProgress,
                        lowChance, highChance)
                * profile.severeStateScale(), 0.0F, 1.0F);
    }

    static void applySyntheticShipSilhouetteMetadata(
            RadarTrack track, boolean enabled) {
        if (!enabled || track == null || !track.isSynthetic()
                || track.trackCategory() != TrackCategory.SABLE) {
            if (track != null && track.isSynthetic()) {
                track.clearSilhouette();
            }
            return;
        }
        try {
            UUID id = UUID.fromString(track.getId());
            track.setSilhouette(id,
                    SyntheticSableSilhouetteFactory.REVISION,
                    SableSilhouetteStatus.READY);
        } catch (IllegalArgumentException ignored) {
            track.clearSilhouette();
        }
    }

    static boolean shouldCreateFakeShip(boolean sableLoaded, float chance,
                                        float roll) {
        if (!sableLoaded || !Float.isFinite(chance) || !Float.isFinite(roll)) {
            return false;
        }
        return roll >= 0.0F && roll < clamp(chance, 0.0F, 1.0F);
    }

    private static NoiseSample noise(Vec3 target, Vec3 origin,
                                     float maxDegrees, int periodTicks,
                                     long now, long baseSeed) {
        if (maxDegrees <= 0.0F || !finite(target) || !finite(origin)) {
            return NoiseSample.ZERO;
        }
        long epoch = Math.floorDiv(now, periodTicks);
        Vec3 current = angularOffset(target, origin, maxDegrees,
                mix64(baseSeed ^ epoch));
        Vec3 previous = angularOffset(target, origin, maxDegrees,
                mix64(baseSeed ^ (epoch - 1L)));
        return new NoiseSample(current,
                current.subtract(previous).scale(1.0 / periodTicks),
                mix64(baseSeed ^ epoch));
    }

    private static Vec3 angularOffset(Vec3 target, Vec3 origin,
                                      float maxDegrees, long randomSeed) {
        Vec3 ray = target.subtract(origin);
        double range = ray.length();
        if (range <= VECTOR_EPSILON) {
            return Vec3.ZERO;
        }
        Vec3 forward = ray.scale(1.0 / range);
        Vec3 reference = Math.abs(forward.y) < 0.9
                ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 right = forward.cross(reference).normalize();
        Vec3 up = right.cross(forward).normalize();
        RandomSource random = RandomSource.create(randomSeed);
        double azimuth = random.nextDouble() * Math.PI * 2.0;
        double angle = Math.toRadians(maxDegrees)
                * Math.sqrt(random.nextDouble());
        Vec3 perpendicular = right.scale(Math.cos(azimuth))
                .add(up.scale(Math.sin(azimuth)));
        return perpendicular.scale(Math.tan(angle) * range);
    }

    private static boolean withinDirectionalCone(Vec3 trackPosition,
                                                  Vec3 radarPosition,
                                                  Vec3 jammerPosition,
                                                  float halfAngleDegrees) {
        Vec3 trackDirection = trackPosition.subtract(radarPosition);
        Vec3 jammerDirection = jammerPosition.subtract(radarPosition);
        if (trackDirection.lengthSqr() <= VECTOR_EPSILON
                || jammerDirection.lengthSqr() <= VECTOR_EPSILON) {
            return false;
        }
        double dot = trackDirection.normalize().dot(jammerDirection.normalize());
        double limit = Math.cos(Math.toRadians(halfAngleDegrees));
        return Mth.clamp(dot, -1.0, 1.0) >= limit;
    }

    private static float alignmentFactor(float angle, float full, float zero) {
        if (angle <= full) {
            return 1.0F;
        }
        if (angle >= zero || zero <= full + 1.0E-5F) {
            return 0.0F;
        }
        return clamp((zero - angle) / (zero - full), 0.0F, 1.0F);
    }

    private static float tierStrength(float difference, float outer,
                                      float inner, float onset) {
        if (difference > outer) {
            return 0.0F;
        }
        return Mth.lerp(inverseProgress(difference, outer, inner),
                onset, 1.0F);
    }

    private static float inverseProgress(float value, float outer,
                                         float inner) {
        if (outer <= inner + 1.0E-5F) {
            return value <= inner ? 1.0F : 0.0F;
        }
        return clamp((outer - value) / (outer - inner), 0.0F, 1.0F);
    }

    private static long seed(String first, String second, long epoch,
                             long salt) {
        long value = salt ^ epoch;
        value = mix64(value ^ (first == null ? 0L : first.hashCode()));
        return mix64(value ^ (second == null ? 0L : second.hashCode()));
    }

    private static double unitRandom(long seed) {
        return (mix64(seed) >>> 11) * 0x1.0p-53;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static float finitePositive(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, value) : 0.0F;
    }

    private static float finitePositive(Number value) {
        return value == null ? 0.0F : finitePositive(value.floatValue());
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(Number value, float min, float max) {
        return value == null ? min : clamp(value.floatValue(), min, max);
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x)
                && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
