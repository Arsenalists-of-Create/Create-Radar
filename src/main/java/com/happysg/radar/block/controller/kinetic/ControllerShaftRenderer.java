package com.happysg.radar.block.controller.kinetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlock;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlock;
import com.happysg.radar.registry.ModPartials;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/** Non-Flywheel fallback for input-driven controller shafts. */
public class ControllerShaftRenderer<
        T extends KineticBlockEntity & ControllerInputShaft>
        extends SafeBlockEntityRenderer<T> {
    private final boolean directionalHalfShaft;

    public ControllerShaftRenderer(BlockEntityRendererProvider.Context context) {
        this(context, false);
    }

    protected ControllerShaftRenderer(
            BlockEntityRendererProvider.Context context,
            boolean directionalHalfShaft) {
        this.directionalHalfShaft = directionalHalfShaft;
    }

    @Override
    protected void renderSafe(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        if (blockEntity.getLevel() == null || VisualizationManager.supportsVisualization(blockEntity.getLevel())) {
            return;
        }

        BlockState state = blockEntity.getBlockState();
        Direction.Axis axis = KineticBlockEntityRenderer.getRotationAxisOf(blockEntity);
        Direction inputDirection = blockEntity.getInputShaftDirection();

        double sampledRpm = blockEntity.getAvailableInputSpeed();
        float inputRpm = Double.isFinite(sampledRpm) ? (float) sampledRpm : 0.0f;
        float swivelRpm = -blockEntity.getSpeed();

        float renderTime = AnimationTickHolder.getRenderTime(blockEntity.getLevel());
        float offset = KineticBlockEntityRenderer.getRotationOffsetForPosition(blockEntity, blockEntity.getBlockPos(), axis);

        boolean swivelMode =
                (state.getBlock() instanceof AutoYawControllerBlock
                        && state.getValue(AutoYawControllerBlock.SWIVEL_MODE))
                        || (state.getBlock() instanceof AutoPitchControllerBlock
                        && state.getValue(AutoPitchControllerBlock.SWIVEL_MODE));

        if (!swivelMode) {
            SuperByteBuffer shaft = directionalHalfShaft ? CachedBuffers.partial(AllPartialModels.SHAFT_HALF, state) : CachedBuffers.block(KineticBlockEntityRenderer.shaft(axis));
            renderRotatingPart(poseStack, buffer, shaft, light, axis, inputRpm, renderTime, offset, directionalHalfShaft ? Direction.SOUTH : null, directionalHalfShaft ? inputDirection : null);
            return;
        }


        renderRotatingPart(
                poseStack, buffer,
                CachedBuffers.partial(AllPartialModels.SHAFT_HALF, state),
                light, axis, inputRpm, renderTime, offset,
                Direction.SOUTH, inputDirection);


        renderRotatingPart(
                poseStack, buffer,
                CachedBuffers.partial(AllPartialModels.SHAFT_HALF, state),
                light, axis, swivelRpm, renderTime, offset,
                Direction.SOUTH, inputDirection.getOpposite());


        renderRotatingPart(
                poseStack, buffer,
                CachedBuffers.partial(AllPartialModels.SHAFTLESS_COGWHEEL, state),
                light, axis, swivelRpm, renderTime, offset,
                Direction.UP,
                Direction.get(Direction.AxisDirection.POSITIVE, axis));
    }

    private static void renderRotatingPart(
            PoseStack poseStack,
            MultiBufferSource buffer,
            SuperByteBuffer model,
            int light,
            Direction.Axis axis,
            float rpm,
            float renderTime,
            float offset,
            Direction modelFrom,
            Direction modelTo) {

        float angle = ((renderTime * rpm * 0.3f + offset) % 360.0f) * Mth.DEG_TO_RAD;

        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(axisRotation(axis).rotation(angle));

        if (modelFrom != null && modelTo != null) {
            poseStack.mulPose(new Quaternionf().rotationTo(
                    modelFrom.getStepX(),
                    modelFrom.getStepY(),
                    modelFrom.getStepZ(),
                    modelTo.getStepX(),
                    modelTo.getStepY(),
                    modelTo.getStepZ()));
        }

        poseStack.translate(-0.5f, -0.5f, -0.5f);

        model.light(light).renderInto(poseStack, buffer.getBuffer(RenderType.solid()));

        poseStack.popPose();
    }

    private static Axis axisRotation(Direction.Axis axis) {
        return switch (axis) {
            case X -> Axis.XP;
            case Y -> Axis.YP;
            case Z -> Axis.ZP;
        };
    }
}
