package com.happysg.radar.block.radar.behavior;

import com.happysg.radar.api.tracking.RadarContact;
import com.happysg.radar.api.tracking.RadarSource;
import com.happysg.radar.block.arad.rwr.RadarType;
import com.happysg.radar.block.arad.rwr.RwrContactEvaluation;
import com.happysg.radar.block.arad.rwr.RwrTargetReference;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.debug.DebugInspectable;
import com.happysg.radar.debug.DiagnosticContext;
import com.happysg.radar.debug.DiagnosticSnapshotBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.UUID;

public interface IRadar extends RadarSource, DebugInspectable {
    Collection<RadarTrack> getTracks();

    @Override
    default Collection<? extends RadarContact> getContacts() {
        return getTracks();
    }

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

    @Override
    default void appendDebugInfo(DiagnosticSnapshotBuilder builder,
                                 DiagnosticContext context) {
        builder.add("Radar", "type", getRadarType())
                .add("Radar", "running", isRunning())
                .add("Radar", "range", getRange())
                .add("Radar", "tracks", getTracks().size())
                .add("Radar", "angle", getGlobalAngle())
                .add("Radar", "facing", getradarDirection())
                .add("Radar", "field of view", getFovDegrees())
                .add("Radar", "sweep degrees/tick",
                        getSweepAngularSpeedDegreesPerTick())
                .add("Radar", "reported world position", getWorldPos());
    }

}
