package com.happysg.radar.mixin;

import com.happysg.radar.compat.simulated.SimulatedSwivelMountAdapter;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Simulated's extra Swivel cogwheel exposes an asymmetric custom connection.
 * Create evaluates the connection in both directions, so the zero reverse
 * ratio can make an isolated controller generator source itself from its own
 * endpoint. Supply the matching reverse ratio only for the endpoint selected
 * by one of our adjacent controllers.
 */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.swivel_bearing."
        + "SwivelBearingBlockEntity$SwivelBearingCogwheelBlockEntity", remap = false)
public abstract class SimulatedSwivelCogwheelBlockEntityMixin
        extends KineticBlockEntity {
    protected SimulatedSwivelCogwheelBlockEntityMixin(
            BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public float propagateRotationTo(
            KineticBlockEntity target,
            BlockState stateFrom,
            BlockState stateTo,
            BlockPos diff,
            boolean connectedViaAxes,
            boolean connectedViaCogs) {
        KineticBlockEntity endpoint = (KineticBlockEntity) (Object) this;
        if (SimulatedSwivelMountAdapter.isSelectedEndpoint(target, endpoint)) {
            return -1.0f;
        }
        return super.propagateRotationTo(target, stateFrom, stateTo, diff,
                connectedViaAxes, connectedViaCogs);
    }
}
