package com.happysg.radar.block.mount;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.IRotate;
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
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Quaternionf;
import rbasamoyai.createbigcannons.index.CBCBlockPartials;

public class SmartMountRenderer extends SafeBlockEntityRenderer<SmartMountBlockEntity> {
    public SmartMountRenderer(BlockEntityRendererProvider.Context context) {
    }

    public SmartMountRenderer() {

    }

    protected void renderSafe(SmartMountBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (!VisualizationManager.supportsVisualization(be.getLevel())) {
            BlockState state = be.getBlockState();
            Direction vertical = (Direction)state.getValue(BlockStateProperties.VERTICAL_DIRECTION);
            boolean upsideDown = vertical == Direction.UP;
            VertexConsumer solidBuf = buffer.getBuffer(RenderType.solid());
            ms.pushPose();
            SuperByteBuffer yawShaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, vertical);
            KineticBlockEntity yawInterface = be.getYawInterface();
            KineticBlockEntityRenderer.kineticRotationTransform(yawShaft, yawInterface, Axis.Y, KineticBlockEntityRenderer.getAngleForBe(yawInterface, be.getBlockPos(), Axis.Y), light).renderInto(ms, solidBuf);
            float yaw = getMountYaw(be);
            Quaternionf qyaw = upsideDown ? com.mojang.math.Axis.ZP.rotationDegrees(180.0F).mul(com.mojang.math.Axis.YP.rotationDegrees(yaw)) : com.mojang.math.Axis.YP.rotationDegrees(-yaw);
            ((SuperByteBuffer)((SuperByteBuffer)CachedBuffers.partial(CBCBlockPartials.ROTATING_MOUNT, state).translate((double)0.0F, upsideDown ? (double)-1.0F : (double)1.0F, (double)0.0F)).light(light).rotateCentered(qyaw)).renderInto(ms, solidBuf);

            Quaternionf qyaw1 = new Quaternionf(qyaw);
            ((SuperByteBuffer)((SuperByteBuffer)CachedBuffers.partialFacing(CBCBlockPartials.CANNON_CARRIAGE_AXLE, state, Direction.NORTH).translate((double)0.0F, upsideDown ? (double)-2.0F : (double)2.0F, (double)0.0F)).rotateCentered(qyaw1)).light(light).renderInto(ms, solidBuf);
            ms.popPose();
        }
    }

    private static float getMountYaw(SmartMountBlockEntity cmbe) {
        float time = AnimationTickHolder.getPartialTicks(cmbe.getLevel());
        return cmbe.getYawOffset(time);
    }
}