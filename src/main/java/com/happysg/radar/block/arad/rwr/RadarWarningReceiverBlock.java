package com.happysg.radar.block.arad.rwr;

import com.happysg.radar.registry.ModBlockEntityTypes;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
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
    public Class<RadarWarningReceiverBlockEntity> getBlockEntityClass() {
        return RadarWarningReceiverBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RadarWarningReceiverBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.RWR_BE.get();
    }

}
