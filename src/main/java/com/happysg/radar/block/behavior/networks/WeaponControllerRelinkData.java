package com.happysg.radar.block.behavior.networks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

public final class WeaponControllerRelinkData extends SavedData {
    private static final String DATA_NAME = "create_radar_weapon_controller_relink";
    private final Set<Long> pendingPositions = new HashSet<>();

    public static WeaponControllerRelinkData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(WeaponControllerRelinkData::new, WeaponControllerRelinkData::load, null), DATA_NAME);
    }

    private static WeaponControllerRelinkData load(CompoundTag tag, HolderLookup.Provider registries) {
        WeaponControllerRelinkData data =
                new WeaponControllerRelinkData();

        for (long packed : tag.getLongArray("Positions")) {
            data.pendingPositions.add(packed);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("Positions", pendingPositions.stream().mapToLong(Long::longValue).toArray());

        return tag;
    }

    public void mark(BlockPos pos) {
        if (pendingPositions.add(pos.asLong())) {
            setDirty();
        }
    }

    public boolean consume(BlockPos pos) {
        boolean removed = pendingPositions.remove(pos.asLong());

        if (removed) {
            setDirty();
        }

        return removed;
    }
}