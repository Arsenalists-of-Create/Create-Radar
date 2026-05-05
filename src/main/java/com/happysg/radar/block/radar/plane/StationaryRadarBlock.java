package com.happysg.radar.block.radar.plane;

import com.happysg.radar.registry.ModBlockEntityTypes;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import com.mojang.serialization.MapCodec;

public class StationaryRadarBlock extends HorizontalDirectionalBlock implements IBE<StationaryRadarBlockEntity> {
    public static final MapCodec<StationaryRadarBlock> CODEC = simpleCodec(StationaryRadarBlock::new);

    public StationaryRadarBlock(Properties pProperties) {
        super(pProperties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder);
        pBuilder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.isSecondaryUseActive() ? context.getHorizontalDirection().getOpposite() : context.getHorizontalDirection();
        return this.defaultBlockState()
                .setValue(FACING, direction);
    }


    @Override
    public Class<StationaryRadarBlockEntity> getBlockEntityClass() {
        return StationaryRadarBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends StationaryRadarBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.STATIONARY_RADAR_BE.get();
    }
}