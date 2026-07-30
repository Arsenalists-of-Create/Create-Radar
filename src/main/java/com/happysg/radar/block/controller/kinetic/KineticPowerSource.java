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
            double rpm = adjacentShaftRpm(controller, direction);
            if (Double.isFinite(rpm) && Math.abs(rpm) > Math.abs(strongest)) {
                strongest = rpm;
            }
        }
        return strongest;
    }

    /**
     * Samples a single directed shaft input without kinetically joining the
     * controller's isolated generator to the source network.
     */
    public static double adjacentShaftRpm(KineticBlockEntity controller,
                                          Direction direction) {
        Level level = controller.getLevel();
        if (level == null) {
            return 0.0;
        }

        BlockPos sourcePos = controller.getBlockPos().relative(direction);
        if (!level.hasChunkAt(sourcePos)) {
            return 0.0;
        }

        BlockEntity candidate = level.getBlockEntity(sourcePos);
        if (!(candidate instanceof KineticBlockEntity kinetic)
                || kinetic == controller) {
            return 0.0;
        }

        BlockState sourceState = level.getBlockState(sourcePos);
        if (!(sourceState.getBlock() instanceof IRotate rotate)
                || rotate.getRotationAxis(sourceState) != direction.getAxis()
                || !rotate.hasShaftTowards(level, sourcePos, sourceState,
                direction.getOpposite())) {
            return 0.0;
        }

        double rpm = kinetic.getSpeed();
        return Double.isFinite(rpm) ? rpm : 0.0;
    }
}
