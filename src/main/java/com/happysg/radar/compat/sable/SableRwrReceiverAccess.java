
package com.happysg.radar.compat.sable;

import com.happysg.radar.block.radar.track.RadarTrackUtil;
import com.happysg.radar.compat.vs2.SableUtils;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SableRwrReceiverAccess {
    private SableRwrReceiverAccess() {}
    public record ShipTarget(UUID shipId, Vec3 position) { }

    public static List<ShipTarget> resolveReceiverChain(ServerLevel level, BlockPos receiverPos) {
        SubLevelAccess containingShip = SableCompanion.INSTANCE.getContaining(level, receiverPos);

        if (containingShip == null) {
            return List.of();
        }

        List<ShipTarget> targets = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        addTarget(targets, seen, containingShip);

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return List.copyOf(targets);
        }

        SubLevel source = container.getSubLevel(containingShip.getUniqueId());
        if (source == null) {
            return List.copyOf(targets);
        }

        for (SubLevel subLevel : SubLevelHelper.getConnectedChain(source)) {
            if (subLevel != null) {
                addTarget(targets, seen, subLevel);
            }
        }

        return List.copyOf(targets);
    }

    private static void addTarget(List<ShipTarget> targets, Set<UUID> seen, SubLevelAccess ship) {
        UUID shipId = ship.getUniqueId();

        if (shipId != null && seen.add(shipId)) {
            targets.add(new ShipTarget(shipId, RadarTrackUtil.getPosition(ship)));
        }
    }

    public static Vec3 getSoundPos(BlockEntity receiver) {
        return Vec3.atCenterOf(SableUtils.getWorldPos(receiver));
    }
}
