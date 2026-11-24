
package com.happysg.radar.compat.sable;

import com.happysg.radar.compat.vs2.SableUtils;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class SableChaffAccess {
    private SableChaffAccess() {}

    public static Set<String> getLoadedShipIds(ServerLevel level, AABB launchVolume) {
        Set<String> ids = new HashSet<>();
        SableAssemblyRegistry assemblies = SableAssemblyRegistry.get(level);

        for (SubLevel subLevel : SableUtils.getLoadedShips(level, launchVolume)) {
            if (subLevel != null && subLevel.getUniqueId() != null) {
                UUID id = subLevel.getUniqueId();
                ids.add(id.toString());
                ids.add(assemblies.groupOf(id).toString());
            }
        }

        return ids;
    }

    public static AABB getShipBounds(ServerLevel level, UUID shipId) {
        AABB result = null;

        for (SubLevel member : SableAssemblyRegistry.get(level).loadedMembers(level, shipId)) {
            BoundingBox3dc box = member.boundingBox();

            if (box == null) {
                continue;
            }

            AABB bounds = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());

            result = result == null ? bounds : new AABB(Math.min(result.minX, bounds.minX), Math.min(result.minY, bounds.minY), Math.min(result.minZ, bounds.minZ), Math.max(result.maxX, bounds.maxX), Math.max(result.maxY, bounds.maxY), Math.max(result.maxZ, bounds.maxZ));
        }

        return result;
    }

    public static boolean isShipLoaded(ServerLevel level, UUID shipId) {
        return !SableAssemblyRegistry.get(level).loadedMembers(level, shipId).isEmpty();
    }
}
