package com.happysg.radar.compat.cbcmw;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.cannon_control.ControlPitchContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import riftyboi.cbcmodernwarfare.cannon_control.compact_mount.CompactCannonMountBlock;
import riftyboi.cbcmodernwarfare.cannon_control.compact_mount.CompactCannonMountBlockEntity;

import javax.annotation.Nullable;

/**
 * Direct CBC Modern Warfare compact-mount access. Callers must guard entry
 * into this class with {@code Mods.CBCMODERNWARFARE.isLoaded()}.
 */
public final class CBCMWMountCompat {
    private CBCMWMountCompat() {
    }

    public static boolean isCompactMount(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof CompactCannonMountBlockEntity;
    }

    public static boolean isCompactMount(BlockState state) {
        return state != null && state.getBlock() instanceof CompactCannonMountBlock;
    }

    @Nullable
    public static PitchOrientedContraptionEntity getContraption(BlockEntity blockEntity) {
        return blockEntity instanceof CompactCannonMountBlockEntity compact
                ? compact.getContraption()
                : null;
    }

    public static void setPitch(BlockEntity blockEntity, float pitch) {
        if (blockEntity instanceof CompactCannonMountBlockEntity compact) {
            compact.setPitch(pitch);
        }
    }

    public static void notifyUpdate(BlockEntity blockEntity) {
        if (blockEntity instanceof CompactCannonMountBlockEntity compact) {
            compact.notifyUpdate();
        }
    }

    public static ControlPitchContraption.Block getController(BlockEntity blockEntity) {
        return (CompactCannonMountBlockEntity) blockEntity;
    }

    public static BlockPos getControllerBlockPos(BlockEntity blockEntity) {
        return ((CompactCannonMountBlockEntity) blockEntity).getControllerBlockPos();
    }

    public static Vec3 getMountOffset(BlockEntity blockEntity) {
        if (!(blockEntity instanceof CompactCannonMountBlockEntity compact)) {
            return Vec3.ZERO;
        }

        BlockState state = compact.getBlockState();
        if (!state.hasProperty(CompactCannonMountBlock.HORIZONTAL_FACING)) {
            return Vec3.ZERO;
        }

        Direction direction = state.getValue(CompactCannonMountBlock.HORIZONTAL_FACING);
        return switch (direction) {
            case EAST -> new Vec3(0, 0, 1);
            case SOUTH -> new Vec3(-1, 0, 0);
            case WEST -> new Vec3(0, 0, -1);
            case NORTH -> new Vec3(1, 0, 0);
            default -> Vec3.ZERO;
        };
    }
}
