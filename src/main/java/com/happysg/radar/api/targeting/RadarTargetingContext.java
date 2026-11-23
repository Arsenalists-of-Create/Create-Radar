package com.happysg.radar.api.targeting;

import com.happysg.radar.api.tracking.RadarContact;
import com.happysg.radar.api.weapon.RadarWeaponContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import javax.annotation.Nullable;
import java.util.Objects;

public record RadarTargetingContext(ServerLevel level, @Nullable RadarContact contact,
        @Nullable RadarWeaponContext weaponContext, @Nullable BlockPos mountPos,
        RadarTargetingSolution defaultSolution) {
    public RadarTargetingContext {
        level = Objects.requireNonNull(level, "level");
        if (mountPos != null) mountPos = mountPos.immutable();
        defaultSolution = Objects.requireNonNull(defaultSolution, "defaultSolution");
    }
}
