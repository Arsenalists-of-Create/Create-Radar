package com.happysg.radar.api.controller;

import com.happysg.radar.api.tracking.RadarContact;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Public interface for Create Radar aiming controllers.
 */
public interface RadarAimController {

    /**
     * Commands the controller to aim at a world-space position.
     */
    void setTarget(@Nullable Vec3 targetPos);

    /**
     * Commands the controller to aim at an angle in degrees.
     */
    void setTargetAngle(float angle);

    /**
     * Current commanded target angle in degrees.
     */
    double getTargetAngle();

    /**
     * Stops active controller movement/tracking.
     */
    void stopController();

    /**
     * @return true while this controller is actively commanding its mount.
     */
    boolean isControllerRunning();

    /**
     * Method for targeting a public radar contact.
     */
    default void setContact(@Nullable RadarContact contact) {
        if (contact == null) {
            stopController();
            return;
        }

        setTarget(contact.getPosition());
    }
}