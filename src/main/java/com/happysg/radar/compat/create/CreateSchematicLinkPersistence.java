package com.happysg.radar.compat.create;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class CreateSchematicLinkPersistence {
    private static final String SNAPSHOT_KEY = "CreateRadarNetworkSnapshot";
    private static final String COPY_MARKER = "SchematicCopy";
    private static final Logger LOGGER = LogUtils.getLogger();

    private CreateSchematicLinkPersistence() {
    }

    public static void writeControllerSnapshot(ServerLevel level, BlockPos controllerPos, CompoundTag target) {
        NetworkData data = NetworkData.get(level);
        NetworkData.Group group = data.getGroup(level.dimension(), controllerPos);
        if (group == null) {
            return;
        }

        putControllerSnapshot(target, data.writeSchematicSnapshot(group, pos -> writeRelativePos(controllerPos, pos)));
    }

    public static void putControllerSnapshot(CompoundTag target, CompoundTag snapshot) {
        target.put(SNAPSHOT_KEY, snapshot.copy());
    }

    @Nullable
    public static CompoundTag readControllerSnapshot(CompoundTag source) {
        if (!source.contains(SNAPSHOT_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }
        return source.getCompound(SNAPSHOT_KEY).copy();
    }

    public static void restoreControllerSnapshot(ServerLevel level, BlockPos controllerPos, CompoundTag snapshot) {
        NetworkData.RestorationReport report = NetworkData.get(level).restoreSchematicSnapshot(
                level, controllerPos, snapshot, tag -> readRelativePos(controllerPos, tag), restorationMode(snapshot));
        if (report.hasWarnings()) {
            LOGGER.warn("Restored weapon network schematic at {} with {} entries restored and {} skipped",
                    controllerPos, report.restored(), report.skipped());
        }
    }

    public static void markSchematicCopy(CompoundTag snapshot) {
        snapshot.putBoolean(COPY_MARKER, true);
    }

    public static NetworkData.RestorationMode restorationMode(CompoundTag snapshot) {
        return snapshot.getBoolean(COPY_MARKER)
                ? NetworkData.RestorationMode.SCHEMATIC_COPY
                : NetworkData.RestorationMode.WORLD_RECOVERY;
    }

    public static void transformControllerSnapshot(CompoundTag tag, StructureTransform transform) {
        CompoundTag snapshot = readControllerSnapshot(tag);
        if (snapshot == null) {
            return;
        }

        transformSnapshotPositions(snapshot, transform);
        markSchematicCopy(snapshot);
        putControllerSnapshot(tag, snapshot);
    }

    private static CompoundTag writeRelativePos(BlockPos origin, BlockPos pos) {
        BlockPos relative = pos.subtract(origin);
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", relative.getX());
        tag.putInt("Y", relative.getY());
        tag.putInt("Z", relative.getZ());
        return tag;
    }

    private static BlockPos readRelativePos(BlockPos origin, CompoundTag tag) {
        if (!isPositionTag(tag)) return null;
        return origin.offset(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
    }

    private static void transformSnapshotPositions(CompoundTag tag, StructureTransform transform) {
        for (String key : tag.getAllKeys()) {
            if ("WorldPos".equals(key)) {
                continue;
            }
            Tag value = tag.get(key);
            if (value instanceof CompoundTag compound) {
                if (isPositionTag(compound)) {
                    writePos(compound, transform.applyWithoutOffset(readPos(compound)));
                } else {
                    transformSnapshotPositions(compound, transform);
                }
            } else if (value instanceof ListTag list) {
                transformSnapshotPositions(list, transform);
            }
        }
    }

    private static void transformSnapshotPositions(ListTag list, StructureTransform transform) {
        for (Tag value : list) {
            if (value instanceof CompoundTag compound) {
                if (isPositionTag(compound)) {
                    writePos(compound, transform.applyWithoutOffset(readPos(compound)));
                } else {
                    transformSnapshotPositions(compound, transform);
                }
            }
        }
    }

    private static boolean isPositionTag(CompoundTag tag) {
        return tag.contains("X", Tag.TAG_INT)
                && tag.contains("Y", Tag.TAG_INT)
                && tag.contains("Z", Tag.TAG_INT);
    }

    private static BlockPos readPos(CompoundTag tag) {
        return new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
    }

    private static void writePos(CompoundTag tag, BlockPos pos) {
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
    }

}
