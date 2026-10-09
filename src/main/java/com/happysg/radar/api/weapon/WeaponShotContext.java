package com.happysg.radar.api.weapon;

import com.happysg.radar.api.mount.RadarMountAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Public context supplied to weapon shot adapters and fire-preparation hooks.
 *
 */
public record WeaponShotContext(ServerLevel level, BlockPos mountPos, @Nullable RadarMountAdapter mount, @Nullable RadarWeaponAdapter weapon) {

    public WeaponShotContext {
        level = Objects.requireNonNull(level, "level");
        mountPos = Objects.requireNonNull(mountPos, "mountPos").immutable();
    }

    public WeaponShotContext(ServerLevel level, RadarWeaponContext weaponContext) {
        this(
                level,
                Objects.requireNonNull(weaponContext, "weaponContext").mountPos(),
                weaponContext.mount(),
                weaponContext.weapon()
        );
    }

    @Nullable
    public RadarWeaponContext weaponContext() {
        if (weapon == null) {
            return null;
        }

        return new RadarWeaponContext(level, mountPos, mount, weapon);
    }

    @Nullable
    public RadarWeaponAdapter radarWeapon() {
        return weapon;
    }

    @Nullable
    public RadarMountAdapter radarMount() {
        return mount;
    }
}