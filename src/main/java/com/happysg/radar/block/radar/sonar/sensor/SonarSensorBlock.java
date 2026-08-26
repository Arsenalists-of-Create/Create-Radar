package com.happysg.radar.block.radar.sonar.sensor;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class SonarSensorBlock extends Block {

    public SonarSensorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        return other.getBlock() instanceof SonarSensorBlock;
    }
}