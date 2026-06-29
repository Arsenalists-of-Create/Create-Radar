package com.happysg.radar.compat.sable;

import com.happysg.radar.compat.create.CreateSchematicLinkPersistence;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;

public final class SableLinkPersistence {
    private SableLinkPersistence() {
    }

    public static boolean isPlacingSchematic() {
        return false;
    }

    public static void writeControllerSnapshot(ServerLevel level, BlockPos controllerPos, CompoundTag target) {
        CreateSchematicLinkPersistence.writeControllerSnapshot(level, controllerPos, target);
    }

    @Nullable
    public static CompoundTag readControllerSnapshot(CompoundTag source) {
        return CreateSchematicLinkPersistence.readControllerSnapshot(source);
    }

    public static void restoreControllerSnapshot(ServerLevel level, CompoundTag snapshot) {
        BlockPos controllerPos = BlockPos.ZERO;
        if (snapshot.contains("FiltererPos", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag pos = snapshot.getCompound("FiltererPos");
            controllerPos = new BlockPos(pos.getInt("X"), pos.getInt("Y"), pos.getInt("Z"));
        }
        CreateSchematicLinkPersistence.restoreControllerSnapshot(level, controllerPos, snapshot);
    }
}
