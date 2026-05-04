package com.happysg.radar.compat.vs2;

import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VS2ShipVelocityTracker {

    private static final Map<UUID, Vec3> LAST_VEL_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> SMOOTHED_VEL_TICK = new ConcurrentHashMap<>();

    /**
     * I am converting VS2 ship velocity to blocks/tick (Vec3) so it matches the rest of the aiming math
     * most VS2 velocity outputs are blocks/second, so i divide by 20
     */
    public static Vec3 getShipVelocityPerTick(SubLevelAccess ship) {
        if (ship == null) return Vec3.ZERO;

        Vector3d origin = new Vector3d();
        Vector3d currentPos = ship.logicalPose().transformPosition(new Vector3d(origin), new Vector3d());
        Vector3d lastPos = ship.lastPose().transformPosition(new Vector3d(origin), new Vector3d());

        Vec3 velPerTick = new Vec3(
                currentPos.x() - lastPos.x(),
                currentPos.y() - lastPos.y(),
                currentPos.z() - lastPos.z()
        );

        LAST_VEL_TICK.put(ship.getUniqueId(), velPerTick);
        return velPerTick;
    }


    public static Vec3 getShipVelocityPerTickSmoothed(SubLevelAccess ship, double alpha) {
        if (ship == null) return Vec3.ZERO;

        Vec3 raw = getShipVelocityPerTick(ship);
        UUID id = ship.getUniqueId();

        Vec3 prev = SMOOTHED_VEL_TICK.get(id);
        if (prev == null) {
            SMOOTHED_VEL_TICK.put(id, raw);
            return raw;
        }

        Vec3 smoothed = prev.scale(alpha).add(raw.scale(1.0 - alpha));
        SMOOTHED_VEL_TICK.put(id, smoothed);
        return smoothed;
    }

    public static Vec3 getLastShipVelocityPerTick(UUID shipId) {
        return LAST_VEL_TICK.getOrDefault(shipId, Vec3.ZERO);
    }

    public static void clear(UUID shipId) {
        LAST_VEL_TICK.remove(shipId);
        SMOOTHED_VEL_TICK.remove(shipId);
    }
}
