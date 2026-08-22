package com.happysg.radar.api.tracking;

import net.minecraft.world.phys.Vec3;

/**
 * Public view of a radar track/contact.
 */
public interface RadarContact {

    /**
     * Stable identifier for this contact.
     */
    String getId();

    /**
     * Current world-space position of the contact.
     */
    Vec3 getPosition();

    /**
     * Current world-space velocity of the contact.
     */
    Vec3 getVelocity();
}