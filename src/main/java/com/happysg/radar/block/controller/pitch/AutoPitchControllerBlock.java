package com.happysg.radar.block.controller.pitch;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.block.controller.kinetic.CannonMountPlacement;
import com.happysg.radar.block.controller.kinetic.PlacementShaftTarget;
import com.happysg.radar.block.controller.limits.ControllerLimitsScreen;
import com.happysg.radar.registry.ModBlockEntityTypes;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import org.jetbrains.annotations.NotNull;

public class AutoPitchControllerBlock extends HorizontalKineticBlock
        implements IBE<AutoPitchControllerBlockEntity>, ICogWheel,
        PlacementShaftTarget {
    private static final Direction[] MOUNT_DIRECTIONS = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };

    public AutoPitchControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(
            @NotNull BlockState state, @NotNull Level level,
            @NotNull BlockPos pos, @NotNull Player player,
            @NotNull BlockHitResult hit
    ) {
        if (level.isClientSide) {
            Client.open(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return false;
    }

    @Override
    public boolean hasPlacementShaftTowards(
            LevelReader world, BlockPos pos, BlockState state, Direction face
    ) {
        return face == state.getValue(HORIZONTAL_FACING).getOpposite();
    }

    @Override
    public boolean isDedicatedCogWheel() {
        // The controller participates in ordinary small-cog propagation, but its
        // item is not a standard cogwheel item: this block uses HORIZONTAL_FACING
        // instead of the AXIS property required by Create's cog placement helper.
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
                        .setValue(HORIZONTAL_FACING, mountDirection);
            }
        }
        return this.defaultBlockState()
                .setValue(HORIZONTAL_FACING, crouching ? context.getHorizontalDirection().getOpposite() : context.getHorizontalDirection());
    }

    @Override
    public Class<AutoPitchControllerBlockEntity> getBlockEntityClass() {
        return AutoPitchControllerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AutoPitchControllerBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.AUTO_PITCH_CONTROLLER.get();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (level instanceof ServerLevel sl && state.getBlock() != newState.getBlock() ) {
            if (level.getBlockEntity(pos) instanceof AutoPitchControllerBlockEntity pitch) {
                pitch.releaseKineticActuator();
            }
            breakAttachedDataLinks(level, pos);
            WeaponNetworkRuntime.get(sl)
                    .unregisterContactController(pos);
            NetworkData.get(sl).onEndpointRemoved(sl, pos);
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
        if (be instanceof AutoPitchControllerBlockEntity pitch) {
            pitch.markMountDirtyExternal();
            pitch.invalidateKineticActuator();
        }
    }
    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);

        if (level.isClientSide) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AutoPitchControllerBlockEntity pitch) {
            pitch.onRelevantNeighborChanged(fromPos);
        }
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static final class Client {
        private static void open(BlockPos pos) {
            net.minecraft.client.Minecraft.getInstance()
                    .setScreen(new ControllerLimitsScreen(pos));
        }
    }
}
