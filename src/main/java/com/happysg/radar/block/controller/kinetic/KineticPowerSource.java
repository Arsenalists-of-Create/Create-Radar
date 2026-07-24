package com.happysg.radar.block.controller.kinetic;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Samples input shafts without kinetically joining the controller generator to them. */
public final class KineticPowerSource {
    private KineticPowerSource() {
    }

    public static double strongestAdjacentShaftRpm(KineticBlockEntity controller,
                                                    Direction.Axis axis) {
        Level level = controller.getLevel();
        if (level == null) {
            return 0.0;
        }

        double strongest = 0.0;
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() != axis) {
                continue;
            }
            BlockPos sourcePos = controller.getBlockPos().relative(direction);
            if (!level.hasChunkAt(sourcePos)) {
                continue;
            }
            BlockEntity candidate = level.getBlockEntity(sourcePos);
            if (!(candidate instanceof KineticBlockEntity kinetic) || kinetic == controller) {
                continue;
            }
            BlockState sourceState = level.getBlockState(sourcePos);
            if (!(sourceState.getBlock() instanceof IRotate rotate)
                    || rotate.getRotationAxis(sourceState) != axis
                    || !rotate.hasShaftTowards(level, sourcePos, sourceState,
                    direction.getOpposite())) {
                continue;
            }
            double rpm = kinetic.getSpeed();
            if (Double.isFinite(rpm) && Math.abs(rpm) > Math.abs(strongest)) {
                strongest = rpm;
            }
        }
        return strongest;
    }
}
