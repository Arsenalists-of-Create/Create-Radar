package com.happysg.radar.compat.cbc;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbcmw.CBCMWMountCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import rbasamoyai.createbigcannons.cannon_control.ControlPitchContraption;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import javax.annotation.Nullable;

/**
 * Common view of CBC's regular mount and CBC Modern Warfare's compact mount.
 * Compact mounts accept external yaw controllers for structural swivels, but
 * are never driven directly by those controllers.
 */
public final class CannonMountContext {
    private enum Kind {
        CBC,
        CBCMW_COMPACT
    }

    private final BlockEntity blockEntity;
    private final Kind kind;

    private CannonMountContext(BlockEntity blockEntity, Kind kind) {
        this.blockEntity = blockEntity;
        this.kind = kind;
    }

    @Nullable
    public static CannonMountContext of(@Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof CannonMountBlockEntity) {
            return new CannonMountContext(blockEntity, Kind.CBC);
        }
        if (Mods.CBCMODERNWARFARE.isLoaded() && CBCMWMountCompat.isCompactMount(blockEntity)) {
            return new CannonMountContext(blockEntity, Kind.CBCMW_COMPACT);
        }
        return null;
    }

    public static boolean isCompactMount(@Nullable BlockEntity blockEntity, @Nullable BlockState state) {
        if (!Mods.CBCMODERNWARFARE.isLoaded()) {
            return false;
        }
        return CBCMWMountCompat.isCompactMount(blockEntity)
                || state != null && CBCMWMountCompat.isCompactMount(state);
    }

    public BlockEntity blockEntity() {
        return blockEntity;
    }

    @Nullable
    public Level getLevel() {
        return blockEntity.getLevel();
    }

    public BlockPos getBlockPos() {
        return blockEntity.getBlockPos();
    }

    @Nullable
    public PitchOrientedContraptionEntity getContraption() {
        if (kind == Kind.CBC) {
            return ((CannonMountBlockEntity) blockEntity).getContraption();
        }
        return CBCMWMountCompat.getContraption(blockEntity);
    }

    public void setPitch(float pitch) {
        if (kind == Kind.CBC) {
            ((CannonMountBlockEntity) blockEntity).setPitch(pitch);
        } else {
            CBCMWMountCompat.setPitch(blockEntity, pitch);
        }
    }

    public void notifyUpdate() {
        if (kind == Kind.CBC) {
            ((CannonMountBlockEntity) blockEntity).notifyUpdate();
        } else {
            CBCMWMountCompat.notifyUpdate(blockEntity);
        }
    }

    public boolean isRemoved() {
        return blockEntity.isRemoved();
    }

    public ControlPitchContraption.Block controller() {
        if (kind == Kind.CBC) {
            return (CannonMountBlockEntity) blockEntity;
        }
        return CBCMWMountCompat.getController(blockEntity);
    }

    public BlockPos getControllerBlockPos() {
        if (kind == Kind.CBC) {
            return ((CannonMountBlockEntity) blockEntity).getControllerBlockPos();
        }
        return CBCMWMountCompat.getControllerBlockPos(blockEntity);
    }

    /**
     * Whether an auto-yaw controller may rotate this mount block directly.
     * Compact mounts can still use a yaw controller through an external swivel.
     */
    public boolean supportsDirectYawControl() {
        return kind != Kind.CBCMW_COMPACT;
    }

    public boolean isCompact() {
        return kind == Kind.CBCMW_COMPACT;
    }
}
