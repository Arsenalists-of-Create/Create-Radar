package com.happysg.radar.api.tracking;

import com.happysg.radar.block.radar.track.RadarTrackUtil;
import com.happysg.radar.compat.vs2.VS2Utils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.core.api.ships.Ship;

import java.util.ArrayList;
import java.util.List;

public final class RadarContactQuery {
    private RadarContactQuery() {}

    public static List<RadarContact> movingFrameContacts(ServerLevel level, AABB bounds) {
        if (level == null || bounds == null) return List.of();
        List<RadarContact> contacts = new ArrayList<>();
        for (Ship ship : VS2Utils.getLoadedShips(level, bounds)) {
            if (ship == null) continue;
            Vec3 position = RadarTrackUtil.getPosition(ship);
            Vec3 velocity = RadarTrackUtil.getVelocity(ship);
            if (position == null || velocity == null) continue;
            contacts.add(new BasicRadarContact(Long.toString(ship.getId()), position, velocity, RadarContactCategory.VS2));
        }
        return List.copyOf(contacts);
    }
}
