package com.happysg.radar.block.controller.kinetic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Exposes a virtual shaft connection to Create's placement helpers without
 * adding that connection to the live kinetic network.
 */
public interface PlacementShaftTarget {
    boolean hasPlacementShaftTowards(LevelReader world, BlockPos pos,
                                     BlockState state, Direction face);
}
