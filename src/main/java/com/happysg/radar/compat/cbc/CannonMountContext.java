package com.happysg.radar.compat.cbc;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbcmw.CBCMWMountCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import rbasamoyai.createbigcannons.cannon_control.ControlPitchContraption;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountExtensionBlock;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountExtensionBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
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

    /**
     * Resolves either a mount itself or one CBC mount extension that points
     * directly into a valid mount. The returned context is always anchored to
     * the real mount block entity, never to the extension.
     */
    @Nullable
    public static CannonMountContext resolveEndpoint(@Nullable BlockEntity endpoint) {
        if (endpoint == null) {
            return null;
        }
        Level level = endpoint.getLevel();
        return level == null
                ? null
                : resolveEndpoint(level, endpoint.getBlockPos());
    }

    /**
     * Chunk-safe position overload for controller and Data Link endpoint
     * resolution.
     */
    @Nullable
    public static CannonMountContext resolveEndpoint(@Nullable Level level,
                                                     @Nullable BlockPos endpointPos) {
        if (level == null || endpointPos == null || !level.hasChunkAt(endpointPos)) {
            return null;
        }

        BlockEntity endpoint = level.getBlockEntity(endpointPos);
        CannonMountContext direct = of(endpoint);
        if (direct != null) {
            return direct.isCurrent() ? direct : null;
        }
        if (!(endpoint instanceof CannonMountExtensionBlockEntity extension)
                || !endpoint.getBlockState()
                .hasProperty(CannonMountExtensionBlock.FACING)) {
            return null;
        }

        // CBC's extension resolver reads the block it points into. Guard that
        // lookup explicitly so a controller at a chunk edge cannot force-load
        // the neighboring chunk.
        Direction direction = endpoint.getBlockState()
                .getValue(CannonMountExtensionBlock.FACING);
        if (!level.hasChunkAt(endpointPos.relative(direction))) {
            return null;
        }

        CannonMountBlockEntity mount = extension.getCannonMount();
        if (mount == null || mount.getLevel() != level || mount.isRemoved()
                || !level.hasChunkAt(mount.getBlockPos())
                || level.getBlockEntity(endpointPos) != endpoint
                || level.getBlockEntity(mount.getBlockPos()) != mount) {
            return null;
        }
        return of(mount);
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

    /**
     * Returns the assembled cannon's neutral horizontal facing. This is the
     * common zero-point needed by external structural yaw actuators.
     */
    @Nullable
    public Direction initialOrientation() {
        PitchOrientedContraptionEntity entity = getContraption();
        if (entity == null
                || !(entity.getContraption() instanceof AbstractMountedCannonContraption cannon)) {
            return null;
        }
        Direction initial = cannon.initialOrientation();
        return initial != null && initial.getAxis().isHorizontal() ? initial : null;
    }

    public void setPitch(float pitch) {
        if (kind == Kind.CBC) {
            ((CannonMountBlockEntity) blockEntity).setPitch(pitch);
        } else {
            CBCMWMountCompat.setPitch(blockEntity, pitch);
        }
    }

    /**
     * Attempts a direct yaw write. Compact mounts intentionally reject this;
     * they may only be yawed through an external structural actuator.
     */
    public boolean trySetYaw(float yaw) {
        if (kind != Kind.CBC) {
            return false;
        }
        ((CannonMountBlockEntity) blockEntity).setYaw(yaw);
        return true;
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

    /**
     * Returns whether this context still names the live block entity currently
     * present at the canonical mount position.
     */
    public boolean isCurrent() {
        Level level = getLevel();
        BlockPos pos = getBlockPos();
        return level != null
                && !blockEntity.isRemoved()
                && level.hasChunkAt(pos)
                && level.getBlockEntity(pos) == blockEntity;
    }

    public boolean sameMount(@Nullable CannonMountContext other) {
        return other != null && blockEntity == other.blockEntity;
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
