package com.happysg.radar.api.radar.rwr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record RadarVirtualRwrEmitter(
        String sourceId,
        Vec3 position,
        Vec3 forward,
        double range,
        double halfAngleDegrees,
        @Nullable UUID targetShipId,
        ThreatStage stage,
        ResourceLocation radarTypeId,
        boolean requireLineOfSight,
        @Nullable SelectionMetadata selectionMetadata
) {
    public enum ThreatStage { IN_RANGE, LOCKED, ENGAGED }
    public record SelectionMetadata(UUID emitterId, float rollingRpm, float rollingRate) {}

    public RadarVirtualRwrEmitter(
            String sourceId,
            Vec3 position,
            Vec3 forward,
            double range,
            double halfAngleDegrees,
            @Nullable UUID targetShipId,
            ThreatStage stage,
            ResourceLocation radarTypeId,
            boolean requireLineOfSight
    ) {
        this(
                sourceId,
                position,
                forward,
                range,
                halfAngleDegrees,
                targetShipId,
                stage,
                radarTypeId,
                requireLineOfSight,
                null
        );
    }
}
