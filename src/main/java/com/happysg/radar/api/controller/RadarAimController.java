package com.happysg.radar.api.controller;

import com.happysg.radar.api.tracking.RadarContact;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;

public interface RadarAimController {
    void setTarget(@Nullable Vec3 targetPos);
    void setTargetAngle(float angle);
    double getTargetAngle();
    void stopController();
    boolean isControllerRunning();

    default void setContact(@Nullable RadarContact contact) {
        if (contact == null) {
            stopController();
            return;
        }
        setTarget(contact.getPosition());
    }
}
