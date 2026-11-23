package com.happysg.radar.api.tracking;

import net.minecraft.world.phys.Vec3;

public interface RadarContact {
    String getId();
    Vec3 getPosition();
    Vec3 getVelocity();
    RadarContactCategory getCategory();
}
