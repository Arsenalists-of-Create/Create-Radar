package com.happysg.radar.block.controller.kinetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
    protected void renderSafe(T blockEntity, float partialTick,
                              PoseStack poseStack, MultiBufferSource buffer,
                              int light, int overlay) {
        if (blockEntity.getLevel() == null
                || VisualizationManager.supportsVisualization(
                blockEntity.getLevel())) {
            return;
        }

        Direction.Axis axis = KineticBlockEntityRenderer
                .getRotationAxisOf(blockEntity);
        double sampledRpm = blockEntity.getAvailableInputSpeed();
        float rpm = Double.isFinite(sampledRpm) ? (float) sampledRpm : 0.0f;
        float renderTime = AnimationTickHolder.getRenderTime(
                blockEntity.getLevel());
        float offset = KineticBlockEntityRenderer
                .getRotationOffsetForPosition(blockEntity,
                        blockEntity.getBlockPos(), axis);
        float angle = ((renderTime * rpm * 0.3f + offset) % 360.0f)
                * Mth.DEG_TO_RAD;

        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(axisRotation(axis).rotation(angle));
        if (directionalHalfShaft) {
            Direction inputDirection = blockEntity.getInputShaftDirection();
            poseStack.mulPose(new Quaternionf().rotationTo(
                    0, 0, 1,
                    inputDirection.getStepX(),
                    inputDirection.getStepY(),
                    inputDirection.getStepZ()));
        }
        poseStack.translate(-0.5f, -0.5f, -0.5f);

        SuperByteBuffer shaft = directionalHalfShaft
                ? CachedBuffers.partial(AllPartialModels.SHAFT_HALF,
                blockEntity.getBlockState())
                : CachedBuffers.block(KineticBlockEntityRenderer.shaft(axis));
        shaft.light(light)
                .renderInto(poseStack, buffer.getBuffer(RenderType.solid()));
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
