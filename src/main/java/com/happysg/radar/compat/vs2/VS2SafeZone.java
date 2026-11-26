package com.happysg.radar.compat.vs2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.core.internal.world.VsiShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import javax.annotation.Nullable;
import java.util.UUID;

public final class VS2SafeZone {
    private VS2SafeZone() {
    }

    @Nullable
    public static UUID shipIdAt(Level level, BlockPos pos) {
        LoadedShip ship = VS2Utils.getShipManagingPos(level, pos);
        return ship == null ? null : new UUID(0L, ship.getId());
    }

    public static Vec3 worldCenter(Level level, BlockPos pos) {
        return VS2Utils.getWorldVec(level, pos);
    }

    @Nullable
    public static Vec3 localPosition(Level level, UUID shipId, Vec3 worldPosition) {
        LoadedShip ship = loadedShip(level, shipId);
        if (ship == null) {
            return null;
        }
        Vector3d local = ship.getWorldToShip().transformPosition(new Vector3d(worldPosition.x, worldPosition.y, worldPosition.z));
        return new Vec3(local.x, local.y, local.z);
    }

    @Nullable
    public static AABB worldBounds(Level level, UUID shipId, AABB bounds) {
        LoadedShip ship = loadedShip(level, shipId);
        if (ship == null) {
            return null;
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{bounds.minX, bounds.maxX}) {
            for (double y : new double[]{bounds.minY, bounds.maxY}) {
                for (double z : new double[]{bounds.minZ, bounds.maxZ}) {
                    Vector3d world = ship.getShipToWorld().transformPosition(new Vector3d(x, y, z));
                    minX = Math.min(minX, world.x);
                    minY = Math.min(minY, world.y);
                    minZ = Math.min(minZ, world.z);
                    maxX = Math.max(maxX, world.x);
                    maxY = Math.max(maxY, world.y);
                    maxZ = Math.max(maxZ, world.z);
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Nullable
    private static LoadedShip loadedShip(Level level, UUID shipId) {
        if (level == null || shipId == null || shipId.getMostSignificantBits() != 0L) {
            return null;
        }
        VsiShipWorld world = VSGameUtilsKt.getShipWorldNullable(level);
        return world == null ? null : world.getLoadedShips().getById(shipId.getLeastSignificantBits());
    }
}
