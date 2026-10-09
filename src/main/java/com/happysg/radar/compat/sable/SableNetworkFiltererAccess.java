
package com.happysg.radar.compat.sable;

import com.happysg.radar.block.arad.rwr.RwrTargetReference;
import com.happysg.radar.block.radar.track.RadarTrackUtil;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

public final class SableNetworkFiltererAccess {
    private SableNetworkFiltererAccess() {}

    public static boolean isShipLoaded(ServerLevel level, UUID shipId) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && container.getSubLevel(shipId) != null;
    }

    @Nullable
    public static String resolveShipId(ServerLevel level, RwrTargetReference target) {
        SubLevelAccess ship = target.resolveSableShip(level);
        if (ship == null) {
            return null;
        }

        UUID shipId = ship.getUniqueId();
        return shipId == null ? null : shipId.toString();
    }

    @Nullable
    public static Vec3 resolveShipPosition(ServerLevel level, UUID shipId) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevelAccess ship = container == null ? null : container.getSubLevel(shipId);

        return ship == null ? null : RadarTrackUtil.getPosition(ship);
    }
}
