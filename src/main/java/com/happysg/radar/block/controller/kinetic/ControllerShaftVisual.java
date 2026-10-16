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
    private RotatingInstance shaft;
    private RotatingInstance innerShaft;
    private RotatingInstance cog;

    private final Model normalShaftModel;
    private final boolean directionalHalfShaft;
    private boolean showingSwivel;

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
        this.normalShaftModel = model;
        shaft = instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, model)
                .createInstance();
        refreshShaft();
    }

    private void refreshShaft() {
        Direction.Axis axis = rotationAxis();
        Direction inputDirection = blockEntity.getInputShaftDirection();

        double sampledRpm = blockEntity.getAvailableInputSpeed();
        float inputRpm = Double.isFinite(sampledRpm)
                ? (float) sampledRpm : 0.0f;

        // Controller output drives the Swivel through a -1 kinetic connection.
        float swivelRpm = -blockEntity.getSpeed();

        BlockState state = blockEntity.getBlockState();
        boolean swivelMode =
                (state.getBlock() instanceof AutoYawControllerBlock
                        && state.getValue(AutoYawControllerBlock.SWIVEL_MODE))
                        || (state.getBlock() instanceof AutoPitchControllerBlock
                        && state.getValue(AutoPitchControllerBlock.SWIVEL_MODE));

        // Recreate geometry only when switching between normal and Swivel mode.
        if (swivelMode != showingSwivel) {
            shaft.delete();

            if (innerShaft != null) {
                innerShaft.delete();
                innerShaft = null;
            }

            if (cog != null) {
                cog.delete();
                cog = null;
            }

            shaft = instancerProvider()
                    .instancer(AllInstanceTypes.ROTATING, swivelMode ? Models.partial(AllPartialModels.SHAFT_HALF) : normalShaftModel)
                    .createInstance();

            if (swivelMode) {
                innerShaft = instancerProvider()
                        .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF))
                        .createInstance();

                cog = instancerProvider()
                        .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFTLESS_COGWHEEL))
                        .createInstance();
            }

            showingSwivel = swivelMode;
        }

        shaft.rotation.identity();

        if (swivelMode || directionalHalfShaft) {
            shaft.rotateToFace(Direction.SOUTH, inputDirection);
        } else {
            shaft.rotateToFace(Direction.UP, axis);
        }

        shaft.setup(blockEntity, axis, inputRpm)
                .setPosition(getVisualPosition())
                .setChanged();

        if (swivelMode) {
            innerShaft.rotation.identity();
            innerShaft.rotateToFace(
                    Direction.SOUTH, inputDirection.getOpposite());

            innerShaft.setup(blockEntity, axis, swivelRpm)
                    .setPosition(getVisualPosition())
                    .setChanged();

            cog.rotation.identity();
            cog.rotateToFace(Direction.UP, axis);

            cog.setup(blockEntity, axis, swivelRpm)
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

        if (showingSwivel) {
            applyOverstressEffect(blockEntity, shaft, innerShaft, cog);
        } else {
            applyOverstressEffect(blockEntity, shaft);
        }
    }

    @Override
    public void updateLight(float partialTick) {
        if (showingSwivel) {
            relight(shaft, innerShaft, cog);
        } else {
            relight(shaft);
        }
    }

    @Override
    protected void _delete() {
        shaft.delete();

        if (innerShaft != null) {
            innerShaft.delete();
        }

        if (cog != null) {
            cog.delete();
        }
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(shaft);

        if (innerShaft != null) {
            consumer.accept(innerShaft);
        }

        if (cog != null) {
            consumer.accept(cog);
        }
    }
}
