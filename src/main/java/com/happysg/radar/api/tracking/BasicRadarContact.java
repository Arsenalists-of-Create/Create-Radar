package com.happysg.radar.api.tracking;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public record BasicRadarContact(String id, Vec3 position, Vec3 velocity, com.happysg.radar.api.tracking.RadarContactCategory category) implements RadarContact {
    public BasicRadarContact {
        id = Objects.requireNonNull(id, "id");
        position = Objects.requireNonNull(position, "position");
        velocity = Objects.requireNonNull(velocity, "velocity");
        category = Objects.requireNonNull(category, "category");
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Vec3 getPosition() {
        return position;
    }

    @Override
    public Vec3 getVelocity() {
        return velocity;
    }

    @Override
    public com.happysg.radar.api.tracking.RadarContactCategory getCategory() {
        return category;
    }
}