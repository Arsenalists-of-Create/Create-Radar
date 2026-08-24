package com.happysg.radar.block.arad.jammer;

import com.happysg.radar.registry.ModPartials;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;

import java.util.function.Consumer;

/** Flywheel visual for the jammer shaft and aimed upper assembly. */
public class JammerVisual extends KineticBlockEntityVisual<JammerBlockEntity>
        implements SimpleDynamicVisual, SimpleTickableVisual {
    private static final float PIVOT_OFFSET = 7.0f / 16.0f;

    private final RotatingInstance shaft;
    private final TransformedInstance upper;

    public JammerVisual(VisualizationContext context,
                        JammerBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        shaft = instancerProvider()
                .instancer(AllInstanceTypes.ROTATING,
                        Models.partial(AllPartialModels.SHAFT_HALF))
                .createInstance();
        upper = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED,
                        Models.partial(ModPartials.ROTATING_JAMMER))
                .createInstance();
        refreshShaft();
        applyAim(partialTick);
    }

    @Override
    public void beginFrame(DynamicVisual.Context context) {
        if (doDistanceLimitThisFrame(context)) {
            return;
        }
        applyAim(context.partialTick());
    }

    @Override
    public void tick(TickableVisual.Context context) {
        refreshShaft();
        applyOverstressEffect(blockEntity, shaft);
    }

    private void refreshShaft() {
        Direction input = blockEntity.getInputShaftDirection();
        shaft.rotation.identity();
        shaft.rotateToFace(Direction.SOUTH, input)
                .setup(blockEntity, input.getAxis())
                .setPosition(getVisualPosition())
                .setChanged();
    }

    private void applyAim(float partialTick) {
        Direction mountFacing = blockEntity.getMountFacing();
        Quaternionf rotation = JammerOrientation.rotation(mountFacing,
                blockEntity.getInterpolatedYaw(partialTick),
                blockEntity.getInterpolatedPitch(partialTick));

        upper.setIdentityTransform()
                .translate(
                        visualPos.getX() + 0.5f
                                + mountFacing.getStepX() * PIVOT_OFFSET,
                        visualPos.getY() + 0.5f
                                + mountFacing.getStepY() * PIVOT_OFFSET,
                        visualPos.getZ() + 0.5f
                                + mountFacing.getStepZ() * PIVOT_OFFSET)
                .rotate(rotation)
                .translate(-0.5f, 0.0f, -0.5f)
                .setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        relight(shaft, upper);
    }

    @Override
    protected void _delete() {
        shaft.delete();
        upper.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(shaft);
        consumer.accept(upper);
    }
}
