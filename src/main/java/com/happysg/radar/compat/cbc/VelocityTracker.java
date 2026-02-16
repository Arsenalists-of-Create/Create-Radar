package com.happysg.radar.compat.cbc;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VelocityTracker {
    private static final Map<UUID, Vec3> LAST_POS  = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> LAST_VEL  = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_TICK = new ConcurrentHashMap<>();

    // teleport/spike reject: >5 blocks/tick (~100 blocks/sec)
    private static final double MAX_VEL_SQR = 25.0;

    // smoothing (0..1). Higher = more responsive, lower = smoother.
    private static final double VEL_ALPHA = 0.35;

    public static Vec3 getEstimatedVelocityPerTick(Entity e) {
        if (e == null) return Vec3.ZERO;

        int tick = e.tickCount;
        UUID id = e.getUUID();

        Integer lastTick = LAST_TICK.get(id);
        if (lastTick != null && lastTick == tick) {
            return LAST_VEL.getOrDefault(id, Vec3.ZERO);
        }

        Vec3 now = e.position();
        Vec3 lastPos = LAST_POS.put(id, now);
        LAST_TICK.put(id, tick);

        if (lastPos == null) {
            LAST_VEL.put(id, Vec3.ZERO);
            return Vec3.ZERO;
        }

        Vec3 rawVel = now.subtract(lastPos); // blocks/tick

        // spike rejection (teleport, chunk correction, etc.)
        if (rawVel.lengthSqr() > MAX_VEL_SQR) {
            rawVel = Vec3.ZERO;
        }

        // EMA smoothing
        Vec3 prev = LAST_VEL.getOrDefault(id, Vec3.ZERO);
        Vec3 vel = prev.scale(1.0 - VEL_ALPHA).add(rawVel.scale(VEL_ALPHA));

        LAST_VEL.put(id, vel);
        return vel;
    }

    public static Vec3 getLastVelocityPerTick(UUID id) {
        return LAST_VEL.getOrDefault(id, Vec3.ZERO);
    }

    public static void clear(UUID id) {
        if (id == null) return;
        LAST_POS.remove(id);
        LAST_VEL.remove(id);
        LAST_TICK.remove(id);
    }
}
