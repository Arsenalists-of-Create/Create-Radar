package com.happysg.radar.api.arad;

import com.happysg.radar.block.arad.aradnetworks.RadarContactRegistry;
import com.happysg.radar.block.arad.rwr.RwrContactEvaluation;
import com.happysg.radar.block.arad.rwr.RwrTargetReference;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.track.RadarTrackUtil;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative native-radar discovery and ARAD target construction.
 *
 * <p>Native radar block entities heartbeat into a dimension-scoped index. Every public lookup still
 * resolves the current block entity and verifies its emitter UUID, so the index never acts as an
 * ownership or liveness authority by itself.</p>
 */
public final class ARADTargeting {
    public static final int NATIVE_RADAR_HEARTBEAT_TTL_TICKS = 20;
    public static final double MAX_PASSIVE_RANGE_RATIO = 1.5;

    private static final double RANGE_EPSILON = 1.0E-6;
    private static final double MIDDLE_SPLIT_RADIUS = 10.0;
    private static final double MIDDLE_MAX_RADIUS = 20.0;
    private static final double OUTER_MIN_RADIUS = 30.0;
    private static final double OUTER_MAX_RADIUS = 50.0;

    private static final Map<ResourceKey<Level>, Map<String, NativeRadarHeartbeat>> NATIVE_RADARS =
            new HashMap<>();

    private ARADTargeting() {
    }

    /** A live receiver reference and the world position used for exact range/error calculations. */
    public record Receiver(
            RwrTargetReference reference,
            Vec3 worldPosition,
            @Nullable UUID sublevelId
    ) {
        public Receiver {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(worldPosition, "worldPosition");
            if (!finite(worldPosition)) {
                throw new IllegalArgumentException("ARAD receiver position must be finite");
            }
        }
    }

    /** A revalidated native emitter as seen from one receiver. */
    public record NativeRadarContact(
            String sourceId,
            UUID emitterId,
            BlockPos radarPos,
            Vec3 radarWorldPosition,
            float radarRange,
            double rangeRatio,
            float signalStrength,
            @Nullable UUID targetSublevelId
    ) {
        public NativeRadarContact {
            Objects.requireNonNull(sourceId, "sourceId");
            Objects.requireNonNull(emitterId, "emitterId");
            radarPos = Objects.requireNonNull(radarPos, "radarPos").immutable();
            Objects.requireNonNull(radarWorldPosition, "radarWorldPosition");
        }
    }

    /** Registers one server-tick heartbeat for a native Create Radar block entity. */
    public static void heartbeatNativeRadar(ServerLevel level, IRadar radar) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(radar, "radar");

        UUID emitterId = radar.getEmitterId();
        BlockPos radarPos = radar.getWorldPos();
        if (emitterId == null || radarPos == null) {
            return;
        }

        String sourceId = RadarContactRegistry.radarSourceId(level, radarPos);
        NATIVE_RADARS
                .computeIfAbsent(level.dimension(), ignored -> new LinkedHashMap<>())
                .put(sourceId, new NativeRadarHeartbeat(emitterId, radarPos.immutable(), level.getGameTime()));
    }

    /** Builds a receiver for a missile or other sensor in ordinary world space. */
    public static Receiver worldReceiver(Vec3 worldPosition) {
        Objects.requireNonNull(worldPosition, "worldPosition");
        return new Receiver(RwrTargetReference.worldPosition(worldPosition), worldPosition, null);
    }

    /** Builds a receiver at the current representative position of a loaded Sable sublevel. */
    public static Optional<Receiver> sableReceiver(ServerLevel level, UUID sublevelId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(sublevelId, "sublevelId");
        if (!Mods.SABLE.isLoaded()) {
            return Optional.empty();
        }

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevelAccess sublevel = container == null ? null : container.getSubLevel(sublevelId);
        if (sublevel == null) {
            return Optional.empty();
        }

        Vec3 position = RadarTrackUtil.getPosition(sublevel);
        if (position == null || !finite(position)) {
            return Optional.empty();
        }
        return Optional.of(new Receiver(RwrTargetReference.sableShip(sublevelId), position, sublevelId));
    }

    /** Returns every live native radar detectable by the supplied receiver. */
    public static List<NativeRadarContact> findNativeContacts(ServerLevel level, Receiver receiver) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(receiver, "receiver");

        Receiver currentReceiver = refreshReceiver(level, receiver).orElse(null);
        if (currentReceiver == null) {
            return List.of();
        }

        List<NativeRadarContact> contacts = new ArrayList<>();
        for (IRadar radar : liveNativeRadars(level)) {
            toContact(level, currentReceiver, radar).ifPresent(contacts::add);
        }
        contacts.sort(Comparator.comparing(NativeRadarContact::sourceId));
        return List.copyOf(contacts);
    }

    /** Resolves one live, detectable native contact by its stable RWR source ID. */
    public static Optional<NativeRadarContact> resolveNativeContact(
            ServerLevel level,
            Receiver receiver,
            String sourceId
    ) {
        if (sourceId == null || sourceId.isBlank()) {
            return Optional.empty();
        }
        Receiver currentReceiver = refreshReceiver(level, receiver).orElse(null);
        if (currentReceiver == null) {
            return Optional.empty();
        }

        return resolveNativeRadar(level, sourceId)
                .flatMap(radar -> toContact(level, currentReceiver, radar));
    }

    /** Resolves a current, running native radar without applying receiver coverage. */
    public static Optional<IRadar> resolveNativeRadar(ServerLevel level, String sourceId) {
        Objects.requireNonNull(level, "level");
        if (sourceId == null || sourceId.isBlank()) {
            return Optional.empty();
        }

        for (IRadar radar : liveNativeRadars(level)) {
            String currentSourceId = RadarContactRegistry.radarSourceId(level, radar.getWorldPos());
            if (sourceId.equals(currentSourceId) && radar.isRunning()) {
                return Optional.of(radar);
            }
        }

        // Point resolution must not depend on tick ordering at chunk/level startup. The heartbeat
        // index is only the discovery mechanism; a stable source ID can resolve its loaded owner directly.
        BlockPos radarPos = parseRadarSource(level, sourceId).orElse(null);
        if (radarPos == null) {
            return Optional.empty();
        }
        BlockEntity blockEntity = level.getBlockEntity(radarPos);
        if (!(blockEntity instanceof IRadar radar)
                || blockEntity.isRemoved()
                || !radar.isRunning()
                || !sourceId.equals(RadarContactRegistry.radarSourceId(level, radar.getWorldPos()))) {
            return Optional.empty();
        }
        return Optional.of(radar);
    }

    /** Applies the shared ARAD range-error bands and creates a fixed or moving target reference. */
    public static @Nullable ARADTargetDesignationEvent.Target createNoisyTarget(
            ServerLevel level,
            NativeRadarContact contact,
            RandomSource random
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(contact, "contact");
        Objects.requireNonNull(random, "random");

        Vec3 horizontalError = sampleHorizontalError(random, contact.rangeRatio());
        BlockPos radarPos = contact.radarPos();
        UUID targetSublevelId = contact.targetSublevelId();
        Vec3 targetLocalPosition = null;
        Vec3 noisyWorldPosition;

        SubLevelAccess targetSublevel = targetSublevelId == null
                ? null
                : resolveSublevel(level, targetSublevelId);
        if (targetSublevelId != null && targetSublevel == null) {
            return null;
        }
        if (targetSublevel != null) {
            targetLocalPosition = radarPos.getCenter().add(horizontalError);
            Vector3d transformed = targetSublevel.logicalPose().transformPosition(new Vector3d(
                    targetLocalPosition.x,
                    targetLocalPosition.y,
                    targetLocalPosition.z
            ));
            noisyWorldPosition = new Vec3(transformed.x(), transformed.y(), transformed.z());
        } else {
            targetSublevelId = null;
            noisyWorldPosition = contact.radarWorldPosition().add(horizontalError);
        }

        return new ARADTargetDesignationEvent.Target(
                radarPos,
                contact.emitterId(),
                contact.rangeRatio(),
                noisyWorldPosition,
                targetSublevelId,
                targetLocalPosition
        );
    }

    /** Clears the transient native-radar index for an unloading server level. */
    public static void clearNativeRadars(ServerLevel level) {
        NATIVE_RADARS.remove(level.dimension());
    }

    /** Prunes expired or no-longer-owned heartbeats at the end of each server-level tick. */
    public static void tickNativeRadars(ServerLevel level) {
        liveNativeRadars(level);
    }

    private static Optional<Receiver> refreshReceiver(ServerLevel level, Receiver receiver) {
        if (receiver.sublevelId() != null) {
            return sableReceiver(level, receiver.sublevelId());
        }
        return receiver.reference().kind() == RwrTargetReference.Kind.WORLD_POSITION
                && finite(receiver.worldPosition())
                ? Optional.of(worldReceiver(receiver.worldPosition()))
                : Optional.empty();
    }

    private static Optional<NativeRadarContact> toContact(
            ServerLevel level,
            Receiver receiver,
            IRadar radar
    ) {
        if (!radar.isRunning()) {
            return Optional.empty();
        }

        float radarRange = radar.getRange();
        if (!Float.isFinite(radarRange) || radarRange <= 0.0F) {
            return Optional.empty();
        }

        Vec3 radarWorldPosition = PhysicsHandler.getWorldVec(level, radar.getWorldPos().getCenter());
        if (radarWorldPosition == null || !finite(radarWorldPosition)) {
            return Optional.empty();
        }

        double dx = radarWorldPosition.x - receiver.worldPosition().x;
        double dz = radarWorldPosition.z - receiver.worldPosition().z;
        double rangeRatio = Math.sqrt(dx * dx + dz * dz) / radarRange;
        if (!Double.isFinite(rangeRatio) || rangeRatio > MAX_PASSIVE_RANGE_RATIO + RANGE_EPSILON) {
            return Optional.empty();
        }

        RwrContactEvaluation evaluation = radar.evaluateRwrContact(
                level,
                receiver.reference(),
                receiver.reference()
        );
        if (!evaluation.emitting() || !evaluation.detectableByReceiver()
                || !Float.isFinite(evaluation.signalStrength()) || evaluation.signalStrength() <= 0.0F) {
            return Optional.empty();
        }

        BlockPos radarPos = radar.getWorldPos().immutable();
        SubLevelAccess targetSublevel = Mods.SABLE.isLoaded()
                ? SableCompanion.INSTANCE.getContaining(level, radarPos)
                : null;
        UUID targetSublevelId = targetSublevel == null ? null : targetSublevel.getUniqueId();
        return Optional.of(new NativeRadarContact(
                RadarContactRegistry.radarSourceId(level, radarPos),
                radar.getEmitterId(),
                radarPos,
                radarWorldPosition,
                radarRange,
                rangeRatio,
                evaluation.signalStrength(),
                targetSublevelId
        ));
    }

    private static List<IRadar> liveNativeRadars(ServerLevel level) {
        Map<String, NativeRadarHeartbeat> dimensionRadars = NATIVE_RADARS.get(level.dimension());
        if (dimensionRadars == null || dimensionRadars.isEmpty()) {
            return List.of();
        }

        long now = level.getGameTime();
        List<IRadar> radars = new ArrayList<>();
        Iterator<Map.Entry<String, NativeRadarHeartbeat>> iterator = dimensionRadars.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, NativeRadarHeartbeat> entry = iterator.next();
            NativeRadarHeartbeat heartbeat = entry.getValue();
            long age = now - heartbeat.gameTime();
            if (age < 0L || age > NATIVE_RADAR_HEARTBEAT_TTL_TICKS) {
                iterator.remove();
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(heartbeat.radarPos());
            if (!(blockEntity instanceof IRadar radar)
                    || blockEntity.isRemoved()
                    || !heartbeat.emitterId().equals(radar.getEmitterId())
                    || !heartbeat.radarPos().equals(radar.getWorldPos())
                    || !entry.getKey().equals(RadarContactRegistry.radarSourceId(level, radar.getWorldPos()))) {
                iterator.remove();
                continue;
            }
            radars.add(radar);
        }

        if (dimensionRadars.isEmpty()) {
            NATIVE_RADARS.remove(level.dimension());
        }
        return radars;
    }

    private static @Nullable SubLevelAccess resolveSublevel(ServerLevel level, UUID sublevelId) {
        if (!Mods.SABLE.isLoaded()) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container == null ? null : container.getSubLevel(sublevelId);
    }

    private static Optional<BlockPos> parseRadarSource(ServerLevel level, String sourceId) {
        int separator = sourceId == null ? -1 : sourceId.indexOf('|');
        if (separator <= 0 || separator >= sourceId.length() - 1
                || !level.dimension().location().toString().equals(sourceId.substring(0, separator))) {
            return Optional.empty();
        }

        try {
            return Optional.of(BlockPos.of(Long.parseLong(sourceId.substring(separator + 1))));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static Vec3 sampleHorizontalError(RandomSource random, double rangeRatio) {
        if (rangeRatio <= 0.75) {
            return Vec3.ZERO;
        }

        double minRadius;
        double maxRadius;
        if (rangeRatio <= 1.0) {
            if (random.nextBoolean()) {
                minRadius = 0.0;
                maxRadius = MIDDLE_SPLIT_RADIUS;
            } else {
                minRadius = MIDDLE_SPLIT_RADIUS;
                maxRadius = MIDDLE_MAX_RADIUS;
            }
        } else {
            minRadius = OUTER_MIN_RADIUS;
            maxRadius = OUTER_MAX_RADIUS;
        }

        double radius = Math.sqrt(random.nextDouble()
                * (maxRadius * maxRadius - minRadius * minRadius)
                + minRadius * minRadius);
        double angle = random.nextDouble() * Math.PI * 2.0;
        return new Vec3(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
    }

    private static boolean finite(Vec3 position) {
        return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z);
    }

    private record NativeRadarHeartbeat(UUID emitterId, BlockPos radarPos, long gameTime) {
    }
}
