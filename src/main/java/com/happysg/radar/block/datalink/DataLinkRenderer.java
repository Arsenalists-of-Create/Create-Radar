package com.happysg.radar.block.datalink;

import com.happysg.radar.registry.ModPartials;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import com.simibubi.create.foundation.render.RenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

//public class DataLinkRenderer extends SafeBlockEntityRenderer<DataLinkBlockEntity> {
//
//    public DataLinkRenderer(BlockEntityRendererProvider.Context context) {
//    }

//    @Override
//    protected void renderSafe(DataLinkBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
//                              int light, int overlay) {
//        long gameTime = be.getLevel().getGameTime();
//        float glow = be.ledState ? (float) (0.5 * (1 + Math.sin(gameTime * 0.1))) : 0;
//        if (glow == 0)
//            return;
//
//        glow = (float) (1 - (2 * Math.pow(glow - .75f, 2)));
//        glow = Mth.clamp(glow, 0.2f, 1.0f);
//        int color = (int) (200 * glow);
//
//        BlockState blockState = be.getBlockState();
//        Direction face = blockState.getValue(DataLinkBlock.FACING);
//
//        ms.pushPose();
//        TransformStack<?> msr = TransformStack.of(ms)
//                .translate(0.5, 0.5, 0.5);
//
//        switch (face) {
//            case UP:
//                // Default orientation
//                break;
//            case DOWN:
//                msr.rotateX(180);
//                break;
//            case NORTH:
//                msr.rotateX(-90);
//                break;
//            case SOUTH:
//                msr.rotateX(90);
//                break;
//            case WEST:
//                msr.rotateZ(90);
//                break;
//            case EAST:
//                msr.rotateZ(-90);
//                break;
//        }
//
//        msr.translate(-0.5, -0.5, -0.5);
//
//        // Render the link tube
//        CachedBuffers.partial(ModPartials.RADAR_LINK_TUBE, blockState)
//                .light(LightTexture.FULL_BRIGHT)
//                .renderInto(ms, buffer.getBuffer(RenderType.translucent()));

        // Render the glowing cube
//        CachedBuffers.partial(ModPartials.RADAR_GLOW, blockState)
//                .light(LightTexture.FULL_BRIGHT)
//                .color(color, color, color, 255)
//                .disableDiffuse()
//                .renderInto(ms, buffer.getBuffer(RenderTypes.additive()));

//        ms.popPose();
//    }
//}
