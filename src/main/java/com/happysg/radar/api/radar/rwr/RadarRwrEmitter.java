package com.happysg.radar.api.radar.rwr;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public interface RadarRwrEmitter {

    UUID getRwrEmitterId();

    BlockPos getRwrEmitterPosition();

    ResourceLocation getRwrEmitterTypeId();

    float getRwrRange();

    boolean isRwrEmitting();

    default RadarRwrEvaluation evaluateRwrContact(ServerLevel level, RadarRwrTarget receiver, RadarRwrTarget target) {
        return RadarRwrEvaluation.notEmitting();
    }
}