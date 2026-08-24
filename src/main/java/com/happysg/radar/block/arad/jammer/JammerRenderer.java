package com.happysg.radar.block.arad.jammer;

import com.happysg.radar.registry.ModPartials;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

/** Non-Flywheel renderer for the jammer shaft and aimed upper assembly. */
public class JammerRenderer
        extends KineticBlockEntityRenderer<JammerBlockEntity> {
    private static final float PIVOT_OFFSET = 7.0f / 16.0f;

    public JammerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(JammerBlockEntity blockEntity, float partialTick,
                              PoseStack poseStack, MultiBufferSource buffer,
                              int light, int overlay) {
        if (blockEntity.getLevel() == null
                || VisualizationManager.supportsVisualization(
                blockEntity.getLevel())) {
            return;
        }

        super.renderSafe(blockEntity, partialTick, poseStack, buffer, light,
                overlay);

        Direction mountFacing = blockEntity.getMountFacing();
        Quaternionf rotation = JammerOrientation.rotation(mountFacing,
                blockEntity.getInterpolatedYaw(partialTick),
                blockEntity.getInterpolatedPitch(partialTick));

        poseStack.pushPose();
        poseStack.translate(
                0.5f + mountFacing.getStepX() * PIVOT_OFFSET,
                0.5f + mountFacing.getStepY() * PIVOT_OFFSET,
                0.5f + mountFacing.getStepZ() * PIVOT_OFFSET);
        poseStack.mulPose(rotation);
        poseStack.translate(-0.5f, 0.0f, -0.5f);

        CachedBuffers.partial(ModPartials.ROTATING_JAMMER,
                        blockEntity.getBlockState())
                .light(light)
                .renderInto(poseStack,
                        buffer.getBuffer(RenderType.cutoutMipped()));
        poseStack.popPose();
    }

    @Override
    protected SuperByteBuffer getRotatedModel(JammerBlockEntity blockEntity,
                                               BlockState state) {
        return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state,
                blockEntity.getInputShaftDirection());
    }

    @Override
    public AABB getRenderBoundingBox(JammerBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(2.0);
    }
}
