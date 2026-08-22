package com.happysg.radar.api.tracking;

import net.minecraft.world.phys.Vec3;

/**
 * Public view of a radar track/contact.
 */
public interface RadarContact {

    /**
     * @return stable contact identifier
     */
    String getId();

    /**
     * @return current world-space position
     */
    Vec3 getPosition();

    /**
     * @return current world-space velocity
     */
    Vec3 getVelocity();
}