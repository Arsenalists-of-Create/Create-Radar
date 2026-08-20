package com.happysg.radar.compat.cbc;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VelocityTracker {
    private static final Map<UUID, Sample> SAMPLES = new ConcurrentHashMap<>();

    // A target not observed for several ticks is a new acquisition, not a
    // displacement sample spanning the gap.
    private static final long MAX_SAMPLE_GAP_TICKS = 2L;
    private static final long STALE_SAMPLE_TICKS = 1200L;
    private static final int CLEANUP_THRESHOLD = 512;

    // teleport/spike reject: >5 blocks/tick (~100 blocks/sec)
    private static final double MAX_VEL_SQR = 25.0;
    private static final double VEL_ALPHA = 0.35;

    private VelocityTracker() {
    }

    public static Vec3 getEstimatedVelocityPerTick(Entity entity) {
        if (entity == null) {
            return Vec3.ZERO;
        }
        return sample(entity.getUUID(), entity.position(),
                entity.level().getGameTime());
    }

    static Vec3 sample(UUID id, Vec3 position, long tick) {
        if (id == null || !finite(position)) {
            return Vec3.ZERO;
        }

        Sample previous = SAMPLES.get(id);
        if (previous != null && previous.tick == tick) {
            return previous.velocity;
        }

        long elapsedTicks = previous == null ? 0L : tick - previous.tick;
        if (previous == null || elapsedTicks <= 0L
                || elapsedTicks > MAX_SAMPLE_GAP_TICKS) {
            SAMPLES.put(id, new Sample(position, Vec3.ZERO, tick));
            cleanup(tick);
            return Vec3.ZERO;
        }

        Vec3 rawVelocity = position.subtract(previous.position)
                .scale(1.0 / (double) elapsedTicks);
        if (!finite(rawVelocity) || rawVelocity.lengthSqr() > MAX_VEL_SQR) {
            SAMPLES.put(id, new Sample(position, Vec3.ZERO, tick));
            cleanup(tick);
            return Vec3.ZERO;
        }

        Vec3 velocity = previous.velocity.scale(1.0 - VEL_ALPHA)
                .add(rawVelocity.scale(VEL_ALPHA));
        SAMPLES.put(id, new Sample(position, velocity, tick));
        cleanup(tick);
        return velocity;
    }

    public static Vec3 getLastVelocityPerTick(UUID id) {
        Sample sample = id == null ? null : SAMPLES.get(id);
        return sample == null ? Vec3.ZERO : sample.velocity;
    }

    public static void clear(UUID id) {
        if (id != null) {
            SAMPLES.remove(id);
        }
    }

    private static void cleanup(long tick) {
        if (SAMPLES.size() <= CLEANUP_THRESHOLD) {
            return;
        }
        SAMPLES.entrySet().removeIf(entry -> {
            long age = tick - entry.getValue().tick;
            return age < 0L || age > STALE_SAMPLE_TICKS;
        });
    }

    private static boolean finite(Vec3 vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    private record Sample(Vec3 position, Vec3 velocity, long tick) {
    }
}
