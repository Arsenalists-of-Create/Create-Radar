package com.happysg.radar.block.radar.behavior;

import com.happysg.radar.block.arad.rwr.RadarType;
import com.happysg.radar.block.arad.rwr.RwrContactEvaluation;
import com.happysg.radar.block.arad.rwr.RwrTargetReference;
import com.happysg.radar.block.radar.track.RadarTrack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.UUID;

public interface IRadar {
    Collection<RadarTrack> getTracks();

    float getRange();

    boolean isRunning();

    BlockPos getWorldPos();

    float getGlobalAngle();

    String getRadarType();

    UUID getEmitterId();

    RadarType getRadarTypeEnum();

    RwrContactEvaluation evaluateRwrContact(ServerLevel level, RwrTargetReference receiver, RwrTargetReference target);

    Direction getradarDirection();

    default float getFovDegrees() {
        return 90f;
    }

    default float getSweepAngularSpeedDegreesPerTick() {
        return 0f;
    }

    //todo better name and/or plan to handle different types of radars
    default boolean renderRelativeToMonitor() {
        return true;
    }

}
