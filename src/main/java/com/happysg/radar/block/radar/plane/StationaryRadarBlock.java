package com.happysg.radar.block.radar.plane;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.VSAssemblySuppression;
import com.happysg.radar.compat.vs2.VS2CompatRegister;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public class StationaryRadarBlock extends HorizontalDirectionalBlock implements IBE<StationaryRadarBlockEntity> {

    public StationaryRadarBlock(Properties pProperties) {
        super(pProperties);
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
        return VS2CompatRegister.STATIONARY_RADAR_BE.get();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !isMoving && level instanceof ServerLevel sl && !(Mods.VALKYRIENSKIES.isLoaded() && VSAssemblySuppression.isSuppressed(sl))) {
            NetworkData.get(sl).onEndpointRemoved(sl, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
