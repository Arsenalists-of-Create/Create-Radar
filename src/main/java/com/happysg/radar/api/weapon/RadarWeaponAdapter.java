package com.happysg.radar.api.weapon;

import com.happysg.radar.api.mount.RadarMountAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;

/**
 * Public weapon endpoint controlled by Create Radar.
 *
 * <p>The mount adapter owns physical aiming. The weapon adapter owns
 * weapon identity, shot information, readiness, and final fire state.</p>
 */
public interface RadarWeaponAdapter {

    /**
     * @return true while this adapter still represents a live weapon
     */
    boolean isValid();

    /**
     * @return true when a weapon is currently assembled/present
     */
    boolean isAssembled();

    /**
     * Position used by WeaponNetworkRuntime.
     */
    BlockPos getMountPos();

    /**
     * Corresponding public mount adapter.
     */
    @Nullable
    RadarMountAdapter getMount();

    /**
     * Final fire-state output from Radar.
     *
     * <p>false must always fail closed and stop firing immediately.</p>
     */
    void setFiring(boolean firing);

    /**
     * Stable identity used to determine whether an existing firing-control
     * instance still refers to the same weapon.
     */
    default Object getWeaponIdentity() {
        return getMountPos();
    }

    default RadarWeaponContext createContext(ServerLevel level) {
        return new RadarWeaponContext(level, getMountPos(), getMount(), this);
    }
}