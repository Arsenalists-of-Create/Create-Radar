package com.happysg.radar.block.radar.behavior;

import com.happysg.radar.api.radar.NetworkRadarSource;
import com.happysg.radar.api.radar.RadarDisplayProfiles;
import com.happysg.radar.api.radar.RadarDisplayProfile;
import com.happysg.radar.api.radar.RadarDisplaySource;
import com.happysg.radar.api.radar.rwr.RadarRwrEmitter;
import com.happysg.radar.api.radar.rwr.RadarRwrTypes;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.UUID;

public interface IRadar extends RadarSource, NetworkRadarSource, RadarDisplaySource, RadarRwrEmitter, DebugInspectable {
    /** Raw, uncorrupted observations produced by this sensor. */
    Collection<RadarTrack> getTracks();

    /** Server-authoritative observations after directional jamming effects. */
    default Collection<RadarTrack> getReportedTracks() {
        if (this instanceof BlockEntity blockEntity
                && blockEntity.getLevel() instanceof ServerLevel serverLevel) {
            return com.happysg.radar.block.arad.jammer.DirectionalJammingService
                    .reportedTracks(serverLevel, this, getTracks());
        }
        return getTracks();
    }

    @Override
    default Collection<? extends RadarContact> getContacts() {
        return getReportedTracks();
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

    @Override
    default UUID getRwrEmitterId() { return getEmitterId(); }

    @Override
    default BlockPos getRwrEmitterPosition() { return getWorldPos(); }

    @Override
    default float getRwrRange() { return getRange(); }

    @Override
    default boolean isRwrEmitting() { return isRunning(); }


    default float getInputRpm() {
        return 0f;
    }

    //todo better name and/or plan to handle different types of radars
    default boolean renderRelativeToMonitor() {
        return true;
    }

    @Override
    default BlockPos getRadarPosition() {
        return getWorldPos();
    }

    @Override
    default RadarDisplayProfile getRadarDisplayProfile() {
        return switch (getRadarType()) {
            case "sky" -> RadarDisplayProfiles.SKY;
            case "nonspinning" -> RadarDisplayProfiles.AIRBORNE;
            case "sonar" -> RadarDisplayProfiles.SONAR;
            case "spinning" -> RadarDisplayProfiles.GROUND;
            default -> RadarDisplayProfiles.GENERIC;
        };
    }

    @Override
    default ResourceLocation getRwrEmitterTypeId() {
        return switch (getRadarType()) {
            case "sky" -> RadarRwrTypes.SKY;
            case "nonspinning" -> RadarRwrTypes.AIRBORNE;
            case "spinning" -> RadarRwrTypes.GROUND;
            default -> RadarRwrTypes.GENERIC;
        };
    }

    @Override
    default float getDisplayAngleDegrees() {
        return getGlobalAngle();
    }

    @Override
    default float getDisplayAngularSpeedDegreesPerTick() {
        return getSweepAngularSpeedDegreesPerTick();
    }

    @Override
    default float getDisplayFovDegrees() {
        return getFovDegrees();
    }

    @Override
    default Direction getDisplayDirection() {
        return getradarDirection();
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
