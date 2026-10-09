package com.happysg.radar.api.tracking;

import com.happysg.radar.block.radar.track.RadarTrackUtil;
import com.happysg.radar.compat.vs2.SableUtils;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Public queries for radar-detectable contacts
 */
public final class RadarContactQuery {
    private RadarContactQuery() {}

    /**
     * Returns loaded moving-frame contacts intersecting the supplied
     * world-space bounds.
     */
    public static List<RadarContact> movingFrameContacts(ServerLevel level, AABB bounds) {
        if (level == null || bounds == null) {
            return List.of();
        }

        List<RadarContact> contacts = new ArrayList<>();

        for (SubLevelAccess frame : SableUtils.getLoadedShips(level, bounds)) {
            if (frame == null) {
                continue;
            }

            Vec3 position = RadarTrackUtil.getPosition(frame);
            Vec3 velocity = RadarTrackUtil.getVelocity(frame, level);

            if (position == null || velocity == null) {
                continue;
            }

            contacts.add(new BasicRadarContact(
                    frame.getUniqueId().toString(),
                    position,
                    velocity,
                    com.happysg.radar.api.tracking.RadarContactCategory.SABLE
            ));
        }

        return List.copyOf(contacts);
    }
}