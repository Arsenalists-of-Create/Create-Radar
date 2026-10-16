package com.happysg.radar.block.controller.kinetic;

import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlock;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlock;
import com.happysg.radar.registry.ModPartials;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

/** Renders a controller shaft from sampled input power rather than output RPM. */
public class ControllerShaftVisual<
        T extends KineticBlockEntity & ControllerInputShaft>
        extends KineticBlockEntityVisual<T>
        implements SimpleTickableVisual {
    private final RotatingInstance shaft;
    private RotatingInstance cog;
    private final boolean directionalHalfShaft;

    public ControllerShaftVisual(VisualizationContext context, T blockEntity,
                                 float partialTick) {
        this(context, blockEntity, partialTick,
                Models.partial(AllPartialModels.SHAFT), false);
    }

    protected ControllerShaftVisual(VisualizationContext context,
                                    T blockEntity, float partialTick,
                                    Model model,
                                    boolean directionalHalfShaft) {
        super(context, blockEntity, partialTick);
        this.directionalHalfShaft = directionalHalfShaft;
        shaft = instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, model)
                .createInstance();
        refreshShaft();
    }

    private void refreshShaft() {
        Direction.Axis axis = rotationAxis();
        double sampledRpm = blockEntity.getAvailableInputSpeed();
        float rpm = Double.isFinite(sampledRpm) ? (float) sampledRpm : 0.0f;

        BlockState state = blockEntity.getBlockState();
        boolean swivelMode = (state.getBlock() instanceof AutoYawControllerBlock
                && state.getValue(AutoYawControllerBlock.SWIVEL_MODE)) || (state.getBlock() instanceof AutoPitchControllerBlock
                && state.getValue(AutoPitchControllerBlock.SWIVEL_MODE));

        // Create or remove the cog only when the appearance changes.
        if (swivelMode && cog == null) {
            cog = instancerProvider()
                    .instancer(AllInstanceTypes.ROTATING,
                            Models.partial(ModPartials.SWIVEL_COG))
                    .createInstance();
        } else if (!swivelMode && cog != null) {
            cog.delete();
            cog = null;
        }

        shaft.rotation.identity();
        if (directionalHalfShaft) {
            shaft.rotateToFace(Direction.SOUTH,
                    blockEntity.getInputShaftDirection());
        } else {
            shaft.rotateToFace(Direction.UP, axis);
        }

        shaft.setup(blockEntity, axis, rpm)
                .setPosition(getVisualPosition())
                .setChanged();

        if (cog != null) {
            cog.rotation.identity();
            cog.rotateToFace(Direction.UP, axis);
            cog.setup(blockEntity, axis, rpm)
                    .setPosition(getVisualPosition())
                    .setChanged();
        }
    }

    @Override
    public void update(float partialTick) {
        refreshShaft();
    }

    @Override
    public void tick(Context context) {
        refreshShaft();
        if (cog != null) {
            applyOverstressEffect(blockEntity, shaft, cog);
        } else {
            applyOverstressEffect(blockEntity, shaft);
        }
    }

    @Override
    public void updateLight(float partialTick) {
        if (cog != null) {
            relight(shaft, cog);
        } else {
            relight(shaft);
        }
    }

    @Override
    protected void _delete() {
        if (cog != null) {
            cog.delete();
        }
        shaft.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        if (cog != null) {
            consumer.accept(cog);
        }
        consumer.accept(shaft);
    }
}
