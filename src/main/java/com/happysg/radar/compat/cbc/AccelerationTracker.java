package com.happysg.radar.compat.cbc;

import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks acceleration in blocks/tick^2 from timestamped velocity samples. */
public final class AccelerationTracker {
    private static final long MAX_SAMPLE_GAP_TICKS = 2L;
    private static final long STALE_SAMPLE_TICKS = 1200L;
    private static final int CLEANUP_THRESHOLD = 512;
    private static final double ACCEL_ALPHA = 0.35;
    private static final double MAX_ACCELERATION = 0.25;

    private static final Map<UUID, Sample> ENTITY_SAMPLES =
            new ConcurrentHashMap<>();
    private static final Map<Long, Sample> SHIP_SAMPLES =
            new ConcurrentHashMap<>();

    private AccelerationTracker() {
    }

    public static Vec3 getAccelerationPerTick2(
            UUID id, Vec3 velocityPerTick, long tick) {
        if (id == null || !finite(velocityPerTick)) {
            return Vec3.ZERO;
        }
        return sample(ENTITY_SAMPLES, id, velocityPerTick, tick);
    }

    /**
     * Compatibility overload for callers without a world tick. Internal aim
     * code uses the timestamped overload above.
     */
    public static Vec3 getAccelerationPerTick2(
            UUID id, Vec3 velocityPerTick) {
        Sample previous = id == null ? null : ENTITY_SAMPLES.get(id);
        long nextTick = previous == null ? 0L : previous.tick + 1L;
        return getAccelerationPerTick2(id, velocityPerTick, nextTick);
    }

    public static Vec3 getLastAccelerationPerTick2(UUID id) {
        Sample sample = id == null ? null : ENTITY_SAMPLES.get(id);
        return sample == null ? Vec3.ZERO : sample.acceleration;
    }

    public static void clear(UUID id) {
        if (id != null) {
            ENTITY_SAMPLES.remove(id);
        }
    }

    public static Vec3 getAccelerationPerTick2(
            long shipId, Vec3 velocityPerTick, long tick) {
        if (!finite(velocityPerTick)) {
            return Vec3.ZERO;
        }
        return sample(SHIP_SAMPLES, shipId, velocityPerTick, tick);
    }

    /** Compatibility overload; prefer the timestamped overload. */
    public static Vec3 getAccelerationPerTick2(
            long shipId, Vec3 velocityPerTick) {
        Sample previous = SHIP_SAMPLES.get(shipId);
        long nextTick = previous == null ? 0L : previous.tick + 1L;
        return getAccelerationPerTick2(
                shipId, velocityPerTick, nextTick);
    }

    public static Vec3 getLastAccelerationPerTick2(long shipId) {
        Sample sample = SHIP_SAMPLES.get(shipId);
        return sample == null ? Vec3.ZERO : sample.acceleration;
    }

    public static void clearShip(long shipId) {
        SHIP_SAMPLES.remove(shipId);
    }

    private static <K> Vec3 sample(
            Map<K, Sample> samples, K id, Vec3 velocity, long tick) {
        Sample previous = samples.get(id);
        if (previous != null && previous.tick == tick) {
            return previous.acceleration;
        }

        long elapsedTicks = previous == null ? 0L : tick - previous.tick;
        if (previous == null || elapsedTicks <= 0L
                || elapsedTicks > MAX_SAMPLE_GAP_TICKS) {
            samples.put(id, new Sample(velocity, Vec3.ZERO, tick));
            cleanup(samples, tick);
            return Vec3.ZERO;
        }

        Vec3 rawAcceleration = velocity.subtract(previous.velocity)
                .scale(1.0 / (double) elapsedTicks);
        rawAcceleration = clamp(rawAcceleration, MAX_ACCELERATION);
        Vec3 acceleration = previous.acceleration.scale(1.0 - ACCEL_ALPHA)
                .add(rawAcceleration.scale(ACCEL_ALPHA));
        samples.put(id, new Sample(velocity, acceleration, tick));
        cleanup(samples, tick);
        return acceleration;
    }

    private static <K> void cleanup(Map<K, Sample> samples, long tick) {
        if (samples.size() <= CLEANUP_THRESHOLD) {
            return;
        }
        samples.entrySet().removeIf(entry -> {
            long age = tick - entry.getValue().tick;
            return age < 0L || age > STALE_SAMPLE_TICKS;
        });
    }

    private static Vec3 clamp(Vec3 vector, double maximum) {
        if (!finite(vector)) {
            return Vec3.ZERO;
        }
        double lengthSquared = vector.lengthSqr();
        return lengthSquared > maximum * maximum
                ? vector.normalize().scale(maximum) : vector;
    }

    private static boolean finite(Vec3 vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    private record Sample(Vec3 velocity, Vec3 acceleration, long tick) {
    }
}
