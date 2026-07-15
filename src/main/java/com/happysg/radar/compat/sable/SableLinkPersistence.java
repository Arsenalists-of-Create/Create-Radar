package com.happysg.radar.compat.sable;

import com.happysg.radar.compat.create.CreateSchematicLinkPersistence;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;

import java.util.UUID;

import javax.annotation.Nullable;

public final class SableLinkPersistence {
    private SableLinkPersistence() {
    }

    public static boolean isPlacingSchematic() {
        SubLevelSchematicSerializationContext context = SubLevelSchematicSerializationContext.getCurrentContext();
        return context != null && context.getType() == SubLevelSchematicSerializationContext.Type.PLACE;
    }

    public static boolean isSavingSchematic() {
        SubLevelSchematicSerializationContext context = SubLevelSchematicSerializationContext.getCurrentContext();
        return context != null && context.getType() == SubLevelSchematicSerializationContext.Type.SAVE;
    }

    public static void writeControllerSnapshot(ServerLevel level, BlockPos controllerPos, CompoundTag target) {
        CreateSchematicLinkPersistence.writeControllerSnapshot(level, controllerPos, target);
        if (isSavingSchematic()) {
            CompoundTag snapshot = readControllerSnapshot(target);
            if (snapshot != null) {
                CreateSchematicLinkPersistence.markSchematicCopy(snapshot);
                CreateSchematicLinkPersistence.putControllerSnapshot(target, snapshot);
            }
        }
    }

    @Nullable
    public static CompoundTag readControllerSnapshot(CompoundTag source) {
        return CreateSchematicLinkPersistence.readControllerSnapshot(source);
    }

    public static void restoreControllerSnapshot(ServerLevel level, BlockPos controllerPos, CompoundTag snapshot) {
        if (isPlacingSchematic()) {
            CreateSchematicLinkPersistence.markSchematicCopy(snapshot);
        }
        CreateSchematicLinkPersistence.restoreControllerSnapshot(level, controllerPos, snapshot);
    }

    @Nullable
    public static UUID remapShipId(@Nullable UUID shipId) {
        if (shipId == null) return null;
        SubLevelSchematicSerializationContext context = SubLevelSchematicSerializationContext.getCurrentContext();
        if (context == null) return shipId;
        SubLevelSchematicSerializationContext.SchematicMapping mapping = context.getMapping(shipId);
        return mapping == null ? null : mapping.newUUID();
    }
}
