
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

        for (SubLevel subLevel : SableUtils.getLoadedShips(level, launchVolume)) {
            if (subLevel != null && subLevel.getUniqueId() != null) {
                ids.add(subLevel.getUniqueId().toString());
            }
        }

        return ids;
    }

    public static AABB getShipBounds(ServerLevel level, UUID shipId) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);

        if (container == null) {
            return null;
        }

        SubLevelAccess subLevel = container.getSubLevel(shipId);

        if (subLevel == null || subLevel.boundingBox() == null) {
            return null;
        }

        BoundingBox3dc box = subLevel.boundingBox();
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    public static boolean isShipLoaded(ServerLevel level, UUID shipId) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && container.getSubLevel(shipId) != null;
    }
}
