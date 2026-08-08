package com.happysg.radar.block.radar.skyradar;

import com.happysg.radar.registry.ModPartials;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
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
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

public class SkyRadarRenderer extends SafeBlockEntityRenderer<SkyRadarBlockEntity> {

    public SkyRadarRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(
            SkyRadarBlockEntity be,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            int overlay
    ) {
        if (be.getLevel() == null)
            return;

        // Let the Flywheel visual handle normal in-world rendering.
        // Ponder does not provide that visual path, so this renderer is used there.
        if (VisualizationManager.supportsVisualization(be.getLevel()))
            return;

        boolean unlocked = be.isVisualUnlocked();
        float yaw = be.getInterpolatedVisualYaw(partialTick);
        float speed = unlocked ? be.getSpeed() : 0.0f;

        // Mirrors the usual Create kinetic angular-speed convention.
        float renderTime = AnimationTickHolder.getRenderTime(be.getLevel());
        float shaftAngle = ((renderTime * speed * 0.3f) % 360.0f) * Mth.DEG_TO_RAD;

        BlockState state = be.getBlockState();
        var solid = buffer.getBuffer(RenderType.solid());

        renderMount(state, yaw, poseStack, solid, light);

        // Same position as:
        // verticalShaft.setPosition(x, y, z)
        renderShaft(
                poseStack, solid, light,
                state,
                0, 0, 0,
                new Quaternionf(),
                shaftAngle,
                false
        );

        // Same axis derived in SkyRadarVisual.
        float yawRad = yaw * Mth.DEG_TO_RAD;
        float axisX = Mth.cos(yawRad);
        float axisZ = -Mth.sin(yawRad);

        // Same position as:
        // horizontalShaft.setPosition(x, y + 2, z)
        Quaternionf horizontalOrientation = new Quaternionf()
                .rotationTo(0, 1, 0, axisX, 0, axisZ);

        renderShaft(
                poseStack, solid, light,
                state,
                0, 2, 0,
                horizontalOrientation,
                shaftAngle,
                true
        );
    }

    private static void renderMount(
            BlockState state,
            float yaw,
            PoseStack poseStack,
            com.mojang.blaze3d.vertex.VertexConsumer buffer,
            int light
    ) {
        // Matches:
        // translate(x, y + 1, z)
        // rotateYCentered(yaw)
        CachedBuffers.partial(ModPartials.ROTATING_MOUNT, state)
                .translate(0, 1, 0)
                .center()
                .rotateYDegrees(yaw)
                .uncenter()
                .light(light)
                .renderInto(poseStack, buffer);
    }

    private static void renderShaft(
            PoseStack poseStack,
            com.mojang.blaze3d.vertex.VertexConsumer buffer,
            int light,
            BlockState state,
            float x, float y, float z,
            Quaternionf orientation,
            float rotation,
            boolean topShaft
    ) {
        poseStack.pushPose();

        // Rotate around the shaft's center, then place it locally.
        poseStack.translate(x + 0.5f, y + 0.5f, z + 0.5f);
        poseStack.mulPose(orientation);
        poseStack.mulPose(Axis.YP.rotation(rotation));
        poseStack.translate(-0.5f, -0.5f, -0.5f);

        SuperByteBuffer shaft = topShaft
                ? CachedBuffers.partial(ModPartials.SKY_RADAR_TOP_SHAFT, state)
                : CachedBuffers.block(KineticBlockEntityRenderer.shaft(Direction.Axis.Y));

        shaft.light(light).renderInto(poseStack, buffer);
        poseStack.popPose();
    }
}
