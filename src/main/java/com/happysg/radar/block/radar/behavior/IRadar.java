package com.happysg.radar.block.radar.behavior;

import com.happysg.radar.block.radar.track.RadarTrack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Collection;

public interface IRadar {
    Collection<RadarTrack> getTracks();

    float getRange();

    boolean isRunning();

    BlockPos getWorldPos();
    net.minecraft.world.level.Level getLevel();

    default net.minecraft.world.phys.Vec3 getRadarCenterPos(float partialTicks) {
        // Use getWorldVec for sub-block precision (BlockPos rounds to integer)
        net.minecraft.world.level.Level level = getLevel();
        net.minecraft.core.BlockPos localPos = getWorldPos();
        if (level != null && (com.happysg.radar.compat.Mods.SABLE.isLoaded()
                || com.happysg.radar.compat.Mods.AERONAUTICS.isLoaded()
                || com.happysg.radar.compat.Mods.SIMULATED.isLoaded())) {
            return com.happysg.radar.compat.PhysicsHandler.getWorldVec(level, localPos);
        }
        return net.minecraft.world.phys.Vec3.atCenterOf(localPos);
    }

    default net.minecraft.world.phys.Vec3 getRadarCenterPos() {
        return getRadarCenterPos(1.0f);
    }

    float getGlobalAngle();

    String getRadarType();
    Direction getradarDirection();
    
    default boolean renderRelativeToMonitor() {
        return true;
    }

    float getSpeed();

}