package com.happysg.radar.api.monitor;

import com.happysg.radar.api.radar.RadarDisplayProfile;
import com.happysg.radar.api.radar.RadarDisplayProfiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Objects;

public record MonitorRadarSnapshot(
        BlockPos radarPos,
        Vec3 center,
        float range,
        boolean running,
        RadarDisplayProfile displayProfile,
        float globalAngleDegrees,
        float angularSpeedDegreesPerTick,
        long angleSnapshotGameTime,
        float fovDegrees,
        @Nullable Direction direction,
        @Nullable String ownedLockedTargetId,
        @Nullable Vec3 ownedLockedTargetPosition,
        MonitorRadarData data,
        MonitorExtensionState extensionState
) {
    public MonitorRadarSnapshot {
        radarPos = Objects.requireNonNull(radarPos, "radarPos").immutable();
        center = Objects.requireNonNull(center, "center");

        if (displayProfile == null) {
            displayProfile = RadarDisplayProfiles.GENERIC;
        }

        if (data == null) {
            data = MonitorRadarData.empty();
        }

        if (extensionState == null) {
            extensionState = MonitorExtensionState.empty();
        }

        range = Math.max(0.0F, range);
        fovDegrees = Math.max(0.0F, fovDegrees);
    }
}