package com.happysg.radar.block.controller.kinetic;

import com.simibubi.create.content.kinetics.base.IRotate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared predicate used only while Create chooses a placed block's shaft
 * orientation.
 */
public final class KineticPlacement {
    private KineticPlacement() {
    }

    public static boolean hasShaftOrPlacementHint(
            IRotate rotate, LevelReader world, BlockPos pos,
            BlockState state, Direction face
    ) {
        return rotate.hasShaftTowards(world, pos, state, face)
                || rotate instanceof PlacementShaftTarget target
                && target.hasPlacementShaftTowards(world, pos, state, face);
    }
}
