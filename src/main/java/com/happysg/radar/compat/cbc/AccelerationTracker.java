package com.happysg.radar.compat.cbc;

import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * i’m tracking acceleration in tick space:
 * - velocity is blocks/tick
 * - acceleration is blocks/tick^2
 *
 * if velocity magnitude is below VELOCITY_EPSILON,
 * i treat it as stationary and skip acceleration updates entirely
 */
public class AccelerationTracker {

    // blocks per tick; tweak if needed
    private static final double VELOCITY_EPSILON = 1.0;

    private static final double VELOCITY_EPSILON_SQR = VELOCITY_EPSILON * VELOCITY_EPSILON;

    private static final Map<UUID, Vec3> LAST_VEL_PER_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> LAST_ACCEL_PER_TICK2 = new ConcurrentHashMap<>();

    private static final Map<Long, Vec3> LAST_VEL_PER_TICK_SHIP = new ConcurrentHashMap<>();
    private static final Map<Long, Vec3> LAST_ACCEL_PER_TICK2_SHIP = new ConcurrentHashMap<>();

    // -------------------------
    // entity-based (UUID)
    // -------------------------

    public static Vec3 getAccelerationPerTick2(UUID id, Vec3 velPerTickNow) {
        if (id == null || velPerTickNow == null) return Vec3.ZERO;

        // skip tiny velocities to avoid jitter + bad math
        if (velPerTickNow.lengthSqr() < VELOCITY_EPSILON_SQR) {
            return Vec3.ZERO;
        }

        Vec3 lastVel = LAST_VEL_PER_TICK.put(id, velPerTickNow);
        if (lastVel == null) {
            LAST_ACCEL_PER_TICK2.put(id, Vec3.ZERO);
            return Vec3.ZERO;
        }

        Vec3 accel = velPerTickNow.subtract(lastVel);
        LAST_ACCEL_PER_TICK2.put(id, accel);
        return accel;
    }

    public static Vec3 getLastAccelerationPerTick2(UUID id) {
        if (id == null) return Vec3.ZERO;
        return LAST_ACCEL_PER_TICK2.getOrDefault(id, Vec3.ZERO);
    }

    public static void clear(UUID id) {
        if (id == null) return;
        LAST_VEL_PER_TICK.remove(id);
        LAST_ACCEL_PER_TICK2.remove(id);
    }

    // -------------------------
    // ship-based (long shipId)
    // -------------------------

    public static Vec3 getAccelerationPerTick2(long shipId, Vec3 velPerTickNow) {
        if (velPerTickNow == null) return Vec3.ZERO;

        // skip tiny velocities
        if (velPerTickNow.lengthSqr() < VELOCITY_EPSILON_SQR) {
            return Vec3.ZERO;
        }

        Vec3 lastVel = LAST_VEL_PER_TICK_SHIP.put(shipId, velPerTickNow);
        if (lastVel == null) {
            LAST_ACCEL_PER_TICK2_SHIP.put(shipId, Vec3.ZERO);
            return Vec3.ZERO;
        }

        Vec3 accel = velPerTickNow.subtract(lastVel);
        LAST_ACCEL_PER_TICK2_SHIP.put(shipId, accel);
        return accel;
    }

    public static Vec3 getLastAccelerationPerTick2(long shipId) {
        return LAST_ACCEL_PER_TICK2_SHIP.getOrDefault(shipId, Vec3.ZERO);
    }

    public static void clearShip(long shipId) {
        LAST_VEL_PER_TICK_SHIP.remove(shipId);
        LAST_ACCEL_PER_TICK2_SHIP.remove(shipId);
    }
}
