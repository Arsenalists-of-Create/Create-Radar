package com.happysg.radar.block.radar.sonar.bearing;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.registry.ModBlockEntityTypes;
import com.happysg.radar.registry.ModBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class SonarBearingBlock extends BearingBlock implements IBE<SonarBearingBlockEntity> {

    public SonarBearingBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());

        boolean sensorAbove = ModBlocks.SONAR_SENSOR.has(level.getBlockState(pos.above()));
        boolean sensorBelow = ModBlocks.SONAR_SENSOR.has(level.getBlockState(pos.below()));

        Direction facing;

        if (sensorAbove && !sensorBelow) {
            facing = Direction.UP;
        } else if (sensorBelow && !sensorAbove) {
            facing = Direction.DOWN;
        } else {
            // Fallback when there are no panels yet
            facing = context.getClickedFace();

            if (facing.getAxis() != Direction.Axis.Y) {
                facing = Direction.UP;
            }
        }

        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
        return super.areStatesKineticallyEquivalent(oldState, newState) && oldState.getValue(FACING) == newState.getValue(FACING);
    }

    @Override
    public Class<SonarBearingBlockEntity> getBlockEntityClass() {
        return SonarBearingBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SonarBearingBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.SONAR_BEARING_BE.get();
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof SonarBearingBlockEntity sonar && sonar.isAssembled()) {
            sonar.disassemble();
        }

        BlockState currentState = level.getBlockState(pos);

        if (!currentState.is(this)) {
            return InteractionResult.FAIL;
        }

        Direction current = currentState.getValue(FACING);
        Direction flipped = current == Direction.UP ? Direction.DOWN : Direction.UP;
        KineticBlockEntity.switchToBlockState(level, pos, currentState.setValue(FACING, flipped));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);

            if (be instanceof SonarBearingBlockEntity sonar) {
                if (sonar.isAssembled()) {
                    sonar.disassemble();
                } else {
                    sonar.assemble();
                }
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (AllItems.WRENCH.isIn(stack)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.getItem() instanceof BlockItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, isMoving);
            return;
        }

        if (!level.isClientSide && level instanceof ServerLevel sl) {
            NetworkData.get(sl).onEndpointRemoved(sl, pos);
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }
}