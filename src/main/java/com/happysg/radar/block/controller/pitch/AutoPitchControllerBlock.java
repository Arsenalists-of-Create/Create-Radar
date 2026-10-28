package com.happysg.radar.block.controller.pitch;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.block.controller.kinetic.CannonMountPlacement;
import com.happysg.radar.block.controller.kinetic.PlacementShaftTarget;
import com.happysg.radar.compat.sable.SableDataLinkRelocation;
import com.happysg.radar.networking.packets.OpenControllerLimitsScreenPacket;
import com.happysg.radar.registry.ModBlockEntityTypes;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

public class AutoPitchControllerBlock extends HorizontalKineticBlock implements IBE<AutoPitchControllerBlockEntity>, PlacementShaftTarget {
    public static final BooleanProperty SWIVEL_MODE = BooleanProperty.create("swivel_mode");
    private static final Direction[] MOUNT_DIRECTIONS = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };

    public AutoPitchControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SWIVEL_MODE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SWIVEL_MODE);
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(
            @NotNull BlockState state, @NotNull Level level,
            @NotNull BlockPos pos, @NotNull Player player,
            @NotNull BlockHitResult hit
    ) {
        if (!player.getMainHandItem().isEmpty()
                || !player.getOffhandItem().isEmpty()
                || hasPlacementShaftTowards(
                        level, pos, state, hit.getDirection())) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            OpenControllerLimitsScreenPacket.openIfAssembled(
                    level, pos, player);
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
                if (pitch.firingControl != null) {
                    pitch.firingControl.resetTarget();
                }

                pitch.releaseKineticActuator();
            }

            boolean assemblyMove = SableDataLinkRelocation.isRelocatingEndpoint(sl, pos);
            if (!assemblyMove) {
                WeaponNetworkRuntime.get(sl).rememberRelinkRequirement(pos);

                if (!isMoving) {
                    DataLinkBlock.disconnectAttachedEndpointLinks(sl, pos);
                    DataLinkBlock.disconnectAttachedControllerLinks(sl, pos);
                }
            }
            WeaponNetworkRuntime.get(sl).unregisterContactController(pos);
            if (!assemblyMove) {
                NetworkData.get(sl).onEndpointRemoved(sl, pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);

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

}
