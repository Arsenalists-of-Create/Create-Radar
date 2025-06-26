package com.happysg.radar.block.controller.yaw;

import com.happysg.radar.registry.ModBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.utility.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;

public class AutoYawControllerBlock extends DirectionalKineticBlock implements IBE<AutoYawControllerBlockEntity> {

    public AutoYawControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    public Direction getPreferredHorizontalFacing(BlockPlaceContext context) {
        Direction prefferedSide = null;
        for (Direction side : Iterate.directionsInAxis(Direction.Axis.Y)) {
            BlockPos blockPos = context.getClickedPos().relative(side);
            BlockState blockState = context.getLevel().getBlockState(blockPos);
            if (blockState.getBlock() instanceof IRotate r && context.getLevel().getBlockEntity(blockPos) instanceof CannonMountBlockEntity) {
                prefferedSide = side;
            }
        }

        return prefferedSide;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction preferred = getPreferredHorizontalFacing(context);
        if (preferred == null) {
            if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
                return this.defaultBlockState().setValue(FACING, Direction.DOWN);
            }
            return this.defaultBlockState().setValue(FACING, Direction.UP);
        }

        return this.defaultBlockState().setValue(FACING, preferred);
    }

    @Override
    public Class<AutoYawControllerBlockEntity> getBlockEntityClass() {
        return AutoYawControllerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AutoYawControllerBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.AUTO_YAW_CONTROLLER.get();
    }
}
