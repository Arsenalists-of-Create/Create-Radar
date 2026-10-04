package com.happysg.radar.api.weapon;

import com.happysg.radar.api.mount.RadarMountAdapter;
import com.happysg.radar.compat.cbc.CannonMountContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import javax.annotation.Nullable;

public record WeaponShotContext(
        ServerLevel level,
        @Nullable RadarWeaponContext weaponContext,
        @Nullable CannonMountContext mount,
        @Nullable PitchOrientedContraptionEntity entity,
        @Nullable AbstractMountedCannonContraption weapon
) {

    public WeaponShotContext(ServerLevel level, RadarWeaponContext weaponContext) {
        this(level, weaponContext, null, null, null);
    }

    public WeaponShotContext(
            ServerLevel level,
            CannonMountContext mount,
            PitchOrientedContraptionEntity entity,
            AbstractMountedCannonContraption weapon
    ) {
        this(level, null, mount, entity, weapon);
    }

    @Nullable
    public RadarWeaponAdapter radarWeapon() {
        return weaponContext == null ? null : weaponContext.weapon();
    }

    @Nullable
    public RadarMountAdapter radarMount() {
        RadarWeaponAdapter weapon = radarWeapon();

        return weapon == null ? null : weapon.getMount();
    }

    @Nullable
    public BlockPos mountPos() {
        if (weaponContext != null) {
            return weaponContext.mountPos();
        }

        return mount != null ? mount.getBlockPos() : null;
    }
}