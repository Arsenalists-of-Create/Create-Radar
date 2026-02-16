package com.happysg.radar.compat.vs2;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.Ship;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VS2ShipVelocityTracker {

    private static final Map<Long, Vec3> LAST_VEL_TICK = new ConcurrentHashMap<>();
    private static final Map<Long, Vec3> SMOOTHED_VEL_TICK = new ConcurrentHashMap<>();

    /**
     * i’m converting VS2 ship velocity to blocks/tick (Vec3) so it matches the rest of the aiming math
     * most VS2 velocity outputs are blocks/second, so i divide by 20
     */
    public static Vec3 getShipVelocityPerTick(Ship ship) {
        if (ship == null) return Vec3.ZERO;

        Vector3dc v = ship.getVelocity(); // Vector3dc
        if (v == null) return Vec3.ZERO;

        // i assume VS2 returns blocks/second; convert to blocks/tick
        Vec3 velPerTick = new Vec3(v.x() / 20.0, v.y() / 20.0, v.z() / 20.0);

        LAST_VEL_TICK.put(ship.getId(), velPerTick);
        return velPerTick;
    }

    /**
     * i’m smoothing velocity to reduce aim jitter; alpha closer to 1 = smoother (more lag)
     * typical alpha: 0.6–0.9
     */
    public static Vec3 getShipVelocityPerTickSmoothed(Ship ship, double alpha) {
        if (ship == null) return Vec3.ZERO;

        Vec3 raw = getShipVelocityPerTick(ship);
        long id = ship.getId();

        Vec3 prev = SMOOTHED_VEL_TICK.get(id);
        if (prev == null) {
            SMOOTHED_VEL_TICK.put(id, raw);
            return raw;
        }

        // i’m doing simple exponential smoothing: smoothed = prev*alpha + raw*(1-alpha)
        Vec3 smoothed = prev.scale(alpha).add(raw.scale(1.0 - alpha));
        SMOOTHED_VEL_TICK.put(id, smoothed);
        return smoothed;
    }

    public static Vec3 getLastShipVelocityPerTick(long shipId) {
        return LAST_VEL_TICK.getOrDefault(shipId, Vec3.ZERO);
    }

    public static void clear(long shipId) {
        LAST_VEL_TICK.remove(shipId);
        SMOOTHED_VEL_TICK.remove(shipId);
    }

}
