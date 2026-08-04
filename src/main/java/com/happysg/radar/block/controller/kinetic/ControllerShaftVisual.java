package com.happysg.radar.block.controller.kinetic;

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

import java.util.function.Consumer;

/** Renders a controller shaft from sampled input power rather than output RPM. */
public class ControllerShaftVisual<
        T extends KineticBlockEntity & ControllerInputShaft>
        extends KineticBlockEntityVisual<T>
        implements SimpleTickableVisual {
    private final RotatingInstance shaft;
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
    }

    @Override
    public void update(float partialTick) {
        refreshShaft();
    }

    @Override
    public void tick(Context context) {
        refreshShaft();
        applyOverstressEffect(blockEntity, shaft);
    }

    @Override
    public void updateLight(float partialTick) {
        relight(shaft);
    }

    @Override
    protected void _delete() {
        shaft.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(shaft);
    }
}
