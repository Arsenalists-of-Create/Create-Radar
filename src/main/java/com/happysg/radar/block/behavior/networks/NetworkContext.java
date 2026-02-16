package com.happysg.radar.block.behavior.networks;

import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

public record NetworkContext(ServerLevel level, BlockPos selfPos,
                             WeaponNetworkData weaponData,
                             NetworkData filterData) {

    public static NetworkContext of(ServerLevel level, BlockPos selfPos) {
        return new NetworkContext(
                level,
                selfPos,
                WeaponNetworkData.get(level),
                NetworkData.get(level)
        );
    }

    // ---------- Weapon group resolution ----------
    public @Nullable WeaponNetworkData.Group weaponGroupFromController(BlockPos controllerPos) {
        BlockPos mount = weaponData.getMountForController(level.dimension(), controllerPos);
        return mount == null ? null : weaponData.getGroup(level.dimension(), mount);
    }

    public @Nullable WeaponNetworkData.Group weaponGroupFromMount(BlockPos mountPos) {
        return weaponData.getGroup(level.dimension(), mountPos);
    }

    // ---------- Filter group resolution ----------
    public @Nullable NetworkData.Group filterGroupFromFilterer(BlockPos filtererPos) {
        return filterData.getGroup(level.dimension(), filtererPos);
    }

    public @Nullable NetworkData.Group filterGroupFromEndpoint(BlockPos endpointPos) {
        BlockPos filterer = filterData.getFiltererForEndpoint(level.dimension(), endpointPos);
        return filterer == null ? null : filterData.getGroup(level.dimension(), filterer);
    }

    // Convenience BE fetch
    public <T> @Nullable T be(BlockPos pos, Class<T> type) {
        var be = level.getBlockEntity(pos);
        return type.isInstance(be) ? type.cast(be) : null;
    }
}



