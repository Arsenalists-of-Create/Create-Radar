package com.happysg.radar.block.arad.rwr;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Transient RWR emitters supplied by optional integrations. Emitters must be
 * refreshed while active and are never persisted with the world.
 */
public final class ExternalRwrEmitterRegistry {
    private static final double MIN_DISTANCE_SQR = 1.0e-8D;
    private static final double TARGET_HIT_TOLERANCE_SQR = 0.05D * 0.05D;
    private static final Map<ResourceKey<Level>, Map<String, Entry>> EMITTERS_BY_DIMENSION = new HashMap<>();

    public enum ThreatStage {
        IN_RANGE,
        LOCKED,
        ENGAGED;

        public ThreatStage downgraded() {
            return switch (this) {
                case ENGAGED -> LOCKED;
                case LOCKED, IN_RANGE -> IN_RANGE;
            };
        }
    }

    public record EmitterState(
            String sourceId,
            Vec3 position,
            Vec3 forward,
            double range,
            double halfAngleDegrees,
            @Nullable UUID targetShipId,
            ThreatStage stage,
            RadarType radarType,
            boolean requireLineOfSight
    ) {
    }

    private record Entry(EmitterState state, long expiresAtTick) {
    }

    private ExternalRwrEmitterRegistry() {
    }

    public static synchronized void heartbeat(ServerLevel level, EmitterState state, int ttlTicks) {
        if (level == null || !isValid(state)) {
            return;
        }

        long expiresAt = level.getGameTime() + Math.max(1, ttlTicks);
        EMITTERS_BY_DIMENSION
                .computeIfAbsent(level.dimension(), ignored -> new HashMap<>())
                .put(state.sourceId(), new Entry(normalize(state), expiresAt));
    }

    public static synchronized void remove(ServerLevel level, String sourceId) {
        if (level == null || sourceId == null || sourceId.isBlank()) {
            return;
        }

        Map<String, Entry> emitters = EMITTERS_BY_DIMENSION.get(level.dimension());
        if (emitters == null) {
            return;
        }
        emitters.remove(sourceId);
        if (emitters.isEmpty()) {
            EMITTERS_BY_DIMENSION.remove(level.dimension());
        }
    }

    public static synchronized boolean isActiveSource(ServerLevel level, String sourceId) {
        if (level == null || sourceId == null || sourceId.isBlank()) {
            return false;
        }
        Map<String, Entry> emitters = EMITTERS_BY_DIMENSION.get(level.dimension());
        Entry entry = emitters == null ? null : emitters.get(sourceId);
        return entry != null && entry.expiresAtTick() > level.getGameTime();
    }

    static synchronized List<RwrRadarContact> contactsFor(
            ServerLevel level,
            UUID receiverShipId,
            RwrTargetReference receiver,
            Vec3 displayReceiverPosition
    ) {
        Map<String, Entry> emitters = EMITTERS_BY_DIMENSION.get(level.dimension());
        if (emitters == null || emitters.isEmpty()) {
            return List.of();
        }

        Vec3 receiverPosition = receiver.resolvePosition(level).orElse(null);
        if (!isFinite(receiverPosition) || !isFinite(displayReceiverPosition)) {
            return List.of();
        }

        long now = level.getGameTime();
        List<RwrRadarContact> contacts = new ArrayList<>();
        for (Entry entry : emitters.values()) {
            if (entry.expiresAtTick() <= now) {
                continue;
            }

            EmitterState state = entry.state();
            if (state.targetShipId() != null && !state.targetShipId().equals(receiverShipId)) {
                continue;
            }
            if (!isWithinEnvelope(state, receiverPosition)) {
                continue;
            }
            if (state.requireLineOfSight() && !hasLineOfSight(level, state.position(), receiverPosition, receiverShipId)) {
                continue;
            }

            contacts.add(new RwrRadarContact(
                    state.sourceId(),
                    BlockPos.containing(state.position()),
                    state.radarType(),
                    bearingDegrees(displayReceiverPosition, state.position()),
                    signalStrength(state, receiverPosition),
                    true,
                    true,
                    state.stage() != ThreatStage.IN_RANGE,
                    state.stage() == ThreatStage.ENGAGED,
                    false
            ));
        }
        return List.copyOf(contacts);
    }

    public static synchronized void tickDecay(ServerLevel level) {
        Map<String, Entry> emitters = EMITTERS_BY_DIMENSION.get(level.dimension());
        if (emitters == null) {
            return;
        }
        long now = level.getGameTime();
        emitters.values().removeIf(entry -> entry.expiresAtTick() <= now);
        if (emitters.isEmpty()) {
            EMITTERS_BY_DIMENSION.remove(level.dimension());
        }
    }

    public static synchronized void clear(ServerLevel level) {
        EMITTERS_BY_DIMENSION.remove(level.dimension());
    }

    private static boolean isValid(@Nullable EmitterState state) {
        return state != null
                && state.sourceId() != null
                && !state.sourceId().isBlank()
                && isFinite(state.position())
                && isFinite(state.forward())
                && state.forward().lengthSqr() >= MIN_DISTANCE_SQR
                && Double.isFinite(state.range())
                && state.range() > 0.0D
                && Double.isFinite(state.halfAngleDegrees())
                && state.stage() != null
                && state.radarType() != null;
    }

    private static EmitterState normalize(EmitterState state) {
        return new EmitterState(
                state.sourceId(),
                state.position(),
                state.forward().normalize(),
                Math.max(1.0D, state.range()),
                Math.max(0.0D, Math.min(180.0D, state.halfAngleDegrees())),
                state.targetShipId(),
                state.stage(),
                state.radarType(),
                state.requireLineOfSight()
        );
    }

    private static boolean isWithinEnvelope(EmitterState state, Vec3 targetPosition) {
        Vec3 delta = targetPosition.subtract(state.position());
        double distanceSqr = delta.lengthSqr();
        if (distanceSqr < MIN_DISTANCE_SQR || distanceSqr > state.range() * state.range()) {
            return false;
        }
        double minimumDot = Math.cos(Math.toRadians(state.halfAngleDegrees()));
        return state.forward().dot(delta.normalize()) >= minimumDot;
    }

    private static boolean hasLineOfSight(ServerLevel level, Vec3 origin, Vec3 targetPosition, UUID targetShipId) {
        ClipContext context = new ClipContext(
                origin,
                targetPosition,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                (Entity) null
        );

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel targetSubLevel = container == null ? null : container.getSubLevel(targetShipId);
        if (targetSubLevel != null && context instanceof ClipContextExtension extension) {
            extension.sable$setIgnoredSubLevel(targetSubLevel);
        }

        BlockHitResult hit = level.clip(context);
        return hit.getType() == HitResult.Type.MISS
                || hit.getLocation().distanceToSqr(targetPosition) <= TARGET_HIT_TOLERANCE_SQR;
    }

    private static float signalStrength(EmitterState state, Vec3 receiverPosition) {
        double normalizedDistance = Math.max(0.0D,
                Math.min(1.0D, receiverPosition.distanceTo(state.position()) / state.range()));
        return Math.max(1.0F, Math.min(14.0F, (float) Math.ceil((1.0D - normalizedDistance) * 14.0D)));
    }

    private static float bearingDegrees(Vec3 from, Vec3 to) {
        double angle = Math.toDegrees(Math.atan2(to.x() - from.x(), to.z() - from.z()));
        angle %= 360.0D;
        if (angle < 0.0D) {
            angle += 360.0D;
        }
        return (float) angle;
    }

    private static boolean isFinite(@Nullable Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
