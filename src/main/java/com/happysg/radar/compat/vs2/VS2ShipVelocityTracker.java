package com.happysg.radar.compat.vs2;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VS2ShipVelocityTracker {

    private static final Map<UUID, Vec3> LAST_VEL_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> SMOOTHED_VEL_TICK = new ConcurrentHashMap<>();

    /**
     * Sable reports velocity in blocks/second. The targeting math uses blocks/tick.
     */
    public static Vec3 getShipVelocityPerTick(SubLevelAccess ship, Level level) {
        if (ship == null || ship.boundingBox() == null) return Vec3.ZERO;
        return getShipVelocityPerTick(ship, level, ship.boundingBox().center(new Vector3d()));
    }

    public static Vec3 getShipVelocityPerTick(SubLevelAccess ship, Level level, Vec3 worldSamplePos) {
        return getShipVelocityPerTick(ship, level,
                new Vector3d(worldSamplePos.x, worldSamplePos.y, worldSamplePos.z));
    }

    public static Vec3 getShipVelocityPerTick(SubLevelAccess ship, Level level, Vector3dc worldSamplePos) {
        if (ship == null || level == null || worldSamplePos == null) return Vec3.ZERO;

        // The explicit-sublevel Sable overload expects a point in that sublevel's
        // local coordinates. The position-only overload instead tries to infer a
        // sublevel from plot-grid coordinates, so passing a global bounding-box
        // position to it resolves no sublevel and returns zero.
        Vector3d localSamplePos = ship.logicalPose()
                .transformPositionInverse(new Vector3d(worldSamplePos));
        Vector3d velocityPerSecond = SableCompanion.INSTANCE.getVelocity(
                level, ship, localSamplePos, new Vector3d());
        Vec3 velocity = toVec3(velocityPerSecond).scale(1.0 / 20.0);
        LAST_VEL_TICK.put(ship.getUniqueId(), velocity);
        return velocity;
    }

    private static Vec3 toVec3(Object velocity) {
        if (velocity instanceof Vector3dc vec)
            return new Vec3(vec.x(), vec.y(), vec.z());

        if (velocity instanceof Vector3fc vec)
            return new Vec3(vec.x(), vec.y(), vec.z());

        if (velocity instanceof Vector3f vec)
            return new Vec3(vec.x(), vec.y(), vec.z());

        if (velocity instanceof Vec3 vec)
            return vec;

        return Vec3.ZERO;
    }


//    public static Vec3 getShipVelocityPerTickSmoothed(SubLevelAccess ship, double alpha) {
//        if (ship == null) return Vec3.ZERO;
//
//        Vec3 raw = getShipVelocityPerTick(ship);
//        UUID id = ship.getUniqueId();
//
//        Vec3 prev = SMOOTHED_VEL_TICK.get(id);
//        if (prev == null) {
//            SMOOTHED_VEL_TICK.put(id, raw);
//            return raw;
//        }
//
//        Vec3 smoothed = prev.scale(alpha).add(raw.scale(1.0 - alpha));
//        SMOOTHED_VEL_TICK.put(id, smoothed);
//        return smoothed;
//    }

    public static Vec3 getLastShipVelocityPerTick(UUID shipId) {
        return LAST_VEL_TICK.getOrDefault(shipId, Vec3.ZERO);
    }

    public static void clear(UUID shipId) {
        LAST_VEL_TICK.remove(shipId);
        SMOOTHED_VEL_TICK.remove(shipId);
    }
}
