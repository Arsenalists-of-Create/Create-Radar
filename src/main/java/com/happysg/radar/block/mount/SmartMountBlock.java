package com.happysg.radar.block.mount;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlock;

public class SmartMountBlock extends CannonMountBlock {

    public SmartMountBlock(Properties properties) {
        super(properties);

    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(VERTICAL_DIRECTION);
    }

}