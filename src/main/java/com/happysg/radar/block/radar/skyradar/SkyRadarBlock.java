package com.happysg.radar.block.radar.skyradar;

import com.simibubi.create.foundation.block.IBE;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.registry.ModBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SkyRadarBlock extends KineticBlock implements IBE<SkyRadarBlockEntity> {
    public SkyRadarBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    public Class<SkyRadarBlockEntity> getBlockEntityClass() {
        return SkyRadarBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SkyRadarBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.SKY_RADAR_BE.get();
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
