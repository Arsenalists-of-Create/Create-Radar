package com.happysg.radar.compat.cbc;

import com.happysg.radar.api.mount.RadarMountAdapter;
import com.happysg.radar.api.mount.RadarMountRegistry;
import com.happysg.radar.api.weapon.RadarWeaponAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;

public final class CbcRadarWeaponAdapter implements RadarWeaponAdapter {

    private final ServerLevel level;
    private final CannonMountContext cannonMount;

    public CbcRadarWeaponAdapter(ServerLevel level, CannonMountContext cannonMount) {
        this.level = level;
        this.cannonMount = cannonMount;
    }

    @Override
    public boolean isValid() {
        return cannonMount.isCurrent();
    }

    @Override
    public boolean isAssembled() {
        return cannonMount.hasAssembledCannon();
    }

    @Override
    public BlockPos getMountPos() {
        return cannonMount.getBlockPos();
    }

    @Override
    @Nullable
    public RadarMountAdapter getMount() {
        return RadarMountRegistry.find(level, cannonMount.getBlockPos());
    }

    @Override
    public void setFiring(boolean firing) {}

    @Override
    public Object getWeaponIdentity() {
        return cannonMount.blockEntity();
    }

    public CannonMountContext legacyCbcContext() {
        return cannonMount;
    }
}