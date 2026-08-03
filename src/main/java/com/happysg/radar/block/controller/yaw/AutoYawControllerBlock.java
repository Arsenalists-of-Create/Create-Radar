package com.happysg.radar.block.controller.yaw;

import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.block.controller.kinetic.CannonMountPlacement;
import com.happysg.radar.block.controller.kinetic.PlacementShaftTarget;
import com.happysg.radar.networking.packets.OpenControllerLimitsScreenPacket;
import com.happysg.radar.registry.ModBlockEntityTypes;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;

public class AutoYawControllerBlock extends DirectionalKineticBlock
        implements IBE<AutoYawControllerBlockEntity>, ICogWheel,
        PlacementShaftTarget {
    private static final Direction[] MOUNT_DIRECTIONS = {
            Direction.UP,
            Direction.DOWN
    };

    public AutoYawControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide) {
            OpenControllerLimitsScreenPacket.openIfAssembled(
                    level, pos, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return false;
    }

    @Override
    public boolean hasPlacementShaftTowards(
            LevelReader world, BlockPos pos, BlockState state, Direction face
    ) {
        return face == state.getValue(FACING);
    }

    @Override
    public boolean isDedicatedCogWheel() {
        // The controller participates in ordinary small-cog propagation, but its
        // item is not a standard cogwheel item: this block uses FACING instead of
        // the AXIS property required by Create's cog placement helper.
        return false;
    }
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean crouching = context.getPlayer() != null && context.getPlayer().isCrouching();
        if (!crouching) {
            Direction mountDirection = CannonMountPlacement.findPreferredMount(
                    context, MOUNT_DIRECTIONS);
            if (mountDirection != null) {
                return defaultBlockState()
                        .setValue(FACING, mountDirection.getOpposite());
            }
        }

        Direction vertical = context.getPlayer() != null && context.getPlayer().getXRot() > 0
                ? Direction.UP : Direction.DOWN ;

        return defaultBlockState()
                .setValue(FACING, crouching ? vertical : vertical.getOpposite());
    }

    @Override
    public Class<AutoYawControllerBlockEntity> getBlockEntityClass() {
        return AutoYawControllerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AutoYawControllerBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.AUTO_YAW_CONTROLLER.get();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock() ) {
            if (level.getBlockEntity(pos) instanceof AutoYawControllerBlockEntity yaw) {
                yaw.releaseKineticActuator();
            }
            breakAttachedDataLinks(level, pos);
            if (level instanceof net.minecraft.server.level.ServerLevel
                    serverLevel) {
                WeaponNetworkRuntime.get(serverLevel)
                        .unregisterContactController(pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);

    }
    private static void breakAttachedDataLinks(Level level, BlockPos controllerPos) {
        for (Direction dir : Direction.values()) {
            BlockPos linkPos = controllerPos.relative(dir);
            BlockState linkState = level.getBlockState(linkPos);

            if (!(linkState.getBlock() instanceof DataLinkBlock))
                continue;

            if (linkState.hasProperty(DataLinkBlock.LINK_STYLE)
                    && linkState.getValue(DataLinkBlock.LINK_STYLE) != DataLinkBlock.LinkStyle.CONTROLLER)
                continue;

            if (linkState.hasProperty(DataLinkBlock.FACING)) {
                Direction facing = linkState.getValue(DataLinkBlock.FACING);
                if (!linkPos.relative(facing.getOpposite()).equals(controllerPos))
                    continue;
            }

            level.destroyBlock(linkPos, true);
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);

        if (level.isClientSide) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AutoYawControllerBlockEntity yaw) {
            yaw.markMountDirtyExternal();
            yaw.invalidateKineticActuator();
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);

        if (level.isClientSide) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AutoYawControllerBlockEntity yaw) {
            yaw.onRelevantNeighborChanged(fromPos);
        }
    }

}
