package com.happysg.radar.block.arad.rwr;

import com.happysg.radar.block.arad.aradnetworks.ARADData;
import com.happysg.radar.registry.ModBlockEntityTypes;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class RadarWarningReceiverBlock extends Block implements IBE<RadarWarningReceiverBlockEntity> {

    public static final BooleanProperty ON_SHIP = BooleanProperty.create("on_ship");

    public RadarWarningReceiverBlock(Properties props) {
        super(props);
        registerDefaultState(defaultBlockState().setValue(ON_SHIP, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ON_SHIP);
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof RadarWarningReceiverBlockEntity rwr) {
            return rwr.getRedstoneSignal();
        }
        return 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getSignal(level, pos, side);
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            ARADData.get(serverLevel).dissolveRwr(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public Class<RadarWarningReceiverBlockEntity> getBlockEntityClass() {
        return RadarWarningReceiverBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RadarWarningReceiverBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.RWR_BE.get();
    }

}
