package com.happysg.radar.api.weapon;

import com.happysg.radar.compat.cbc.CannonMountContext;
import net.minecraft.server.level.ServerLevel;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

public record WeaponShotContext(
        ServerLevel level,
        CannonMountContext mount,
        PitchOrientedContraptionEntity entity,
        AbstractMountedCannonContraption weapon
) {
}
