package com.happysg.radar.block.monitor;

import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.config.RadarConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Collection;

public class MonitorRenderer implements BlockEntityRenderer<MonitorBlockEntity> {

    public MonitorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MonitorBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource bufferSource, int light, int overlay) {
        if (!be.isController()) return;
        if (!be.isLinked()) return;

        if (!be.cachedRadarRunning) return;

        Direction facing = be.getBlockState().getValue(MonitorBlock.FACING);
        int size = be.getSize();

        ms.pushPose();

        // Center at block position
        ms.translate(0.5, 0.5, 0.5);
        
        // Correcting base rotation for the face
        float yRot = facing.toYRot();
        if (facing.getAxis() == Direction.Axis.X) {
            yRot += 180;
        }
        ms.mulPose(Axis.YP.rotationDegrees(yRot));
        
        // Offset from block surface
        ms.translate(0, 0, 0.465f);
        
        // Adjust for multiblock dimensions
        float offset = (size - 1) / 2.0f;
        ms.translate(-offset, offset, 0);

        float totalSize = (float) size;
        ms.scale(totalSize, totalSize, 1);

        // Normalize coordinate space to [0, 1] relative to the multiblock display
        renderBackground(ms, bufferSource, light, overlay);
        renderGrid(ms, bufferSource, be, light, overlay);
        renderSweep(ms, be, partialTicks, bufferSource, light, overlay);
        renderTracks(ms, be, bufferSource, light, overlay, totalSize, partialTicks);

        ms.popPose();
    }

    private void renderBackground(PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        Matrix4f mat = ms.last().pose();
        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        int a = (int)(0.6f * 255);

        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(MonitorSprite.RADAR_BG_FILLER.getTexture()));
        drawTexturedQuad(vc, mat, -0.5f, -0.5f, 0.5f, 0.5f, r, g, b, a, light, overlay, 0);
        
        vc = buffer.getBuffer(RenderType.entityTranslucent(MonitorSprite.RADAR_BG_CIRCLE.getTexture()));
        drawTexturedQuad(vc, mat, -0.5f, -0.5f, 0.5f, 0.5f, r, g, b, a, light, overlay, 0.001f);
    }

    private void renderGrid(PoseStack ms, MultiBufferSource buffer, MonitorBlockEntity be, int light, int overlay) {
        VertexConsumer vc = buffer.getBuffer(RenderType.lines());
        Matrix4f mat = ms.last().pose();
        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        int a = 10;

        float range = be.cachedRadarRange;
        int halfCells = Math.max(2, Math.min(24, (int)(range / 50f)));
        int totalCells = halfCells * 2;
        float spacing = 1.0f / totalCells;

        for (int i = 0; i <= totalCells; i++) {
            float pos = -0.5f + i * spacing;
            vc.addVertex(mat, pos, -0.5f, 0.002f).setColor(r, g, b, a).setNormal(0, 0, 1);
            vc.addVertex(mat, pos, 0.5f, 0.002f).setColor(r, g, b, a).setNormal(0, 0, 1);
            vc.addVertex(mat, -0.5f, pos, 0.002f).setColor(r, g, b, a).setNormal(0, 0, 1);
            vc.addVertex(mat, 0.5f, pos, 0.002f).setColor(r, g, b, a).setNormal(0, 0, 1);
        }
    }

    private void renderSweep(PoseStack ms, MonitorBlockEntity be, float partialTicks, MultiBufferSource buffer, int light, int overlay) {
        float angle = (be.cachedRadarAngle + 180f) % 360f;

        Direction facing = be.getBlockState().getValue(MonitorBlock.FACING);
        float screenAngle = angle;
        // Adjusted West offset by another 180 degrees (reverting the previous -180 adjustment)
        if (facing == Direction.NORTH) screenAngle += 180;
        else if (facing == Direction.EAST) screenAngle += 0; 
        else if (facing == Direction.WEST) screenAngle += 0; // Rotated by 180 compared to previous -180

        ms.pushPose();
        
        // Rotation sign logic
        float rotationSign = (facing == Direction.NORTH) ? -1.0f : 1.0f;
        ms.mulPose(Axis.ZP.rotationDegrees(rotationSign * screenAngle));

        Matrix4f mat = ms.last().pose();
        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        int a = (int)(0.8f * 255);

        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(MonitorSprite.RADAR_SWEEP.getTexture()));
        drawTexturedQuad(vc, mat, -0.5f, -0.5f, 0.5f, 0.5f, r, g, b, a, light, overlay, 0.003f);
        
        ms.popPose();
    }

    private void renderTracks(PoseStack ms, MonitorBlockEntity be, MultiBufferSource buffer, int light, int overlay, float totalSize, float partialTicks) {
        Collection<RadarTrack> tracks = be.getTracks();
        if (tracks.isEmpty()) return;

        float range = be.cachedRadarRange;
        Vec3 radarPos = be.cachedRadarCenter;
        if (radarPos == null) return;

        long currentTime = be.getLevel().getGameTime();
        Direction facing = be.getBlockState().getValue(MonitorBlock.FACING);

        for (RadarTrack track : tracks) {
            Vec3 rel = track.position().subtract(radarPos);

            // Ship rotation support
            if (be.cachedRadarRenderRelative && be.getShip() != null) {
                float shipYawDeg = be.getShipYawDeg();
                rel = rotateAroundYDeg(rel, -(shipYawDeg + 180f));
            }

            // Map world-space coordinates to screen-space offsets
            float xOff = calculateTrackOffset(rel, facing, range, true);
            float zOff = calculateTrackOffset(rel, facing, range, false);

            // Invert vertical offset to match screen-space Y (Up)
            float screenX = xOff;
            float screenY = -zOff;

            // Clip tracks outside of the display bounds
            if (Math.abs(screenX) > 0.5f || Math.abs(screenY) > 0.5f) continue;

            float age = currentTime - track.scannedTime();
            float fade = 1.0f - Mth.clamp(age / 100f, 0, 1);
            if (fade <= 0.05f) continue;

            ms.pushPose();
            ms.translate(screenX, screenY, 0.003f);
            
            float trackScale = (track.trackCategory() == TrackCategory.AERONAUTICS) ? 1.2f : 0.6f;
            trackScale /= totalSize;
            ms.scale(trackScale, trackScale, 1);

            Color c = be.filter.getColor(track);
            VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(track.getSprite().getTexture()));
            drawTexturedQuad(vc, ms.last().pose(), -0.5f, -0.5f, 0.5f, 0.5f, c.getRed(), c.getGreen(), c.getBlue(), (int)(fade * 255), light, overlay, 0.004f);

            ms.popPose();
        }
    }

    private float calculateTrackOffset(Vec3 relativePos, Direction monitorFacing, float scale, boolean isXOffset) {
        float offset;
        if (isXOffset) {
            offset = monitorFacing.getAxis() == Direction.Axis.Z ?
                    (float)(relativePos.x / scale) / 2f : (float)(relativePos.z / scale) / 2f;

            if (monitorFacing == Direction.NORTH || monitorFacing == Direction.EAST) {
                offset = -offset;
            }
        } else {
            offset = monitorFacing.getAxis() == Direction.Axis.Z ?
                    (float)(relativePos.z / scale) / 2f : (float)(relativePos.x / scale) / 2f;

            if (monitorFacing == Direction.NORTH || monitorFacing == Direction.WEST) {
                offset = -offset;
            }
        }
        return offset;
    }

    private Vec3 rotateAroundYDeg(Vec3 v, float deg) {
        double rad = Math.toRadians(deg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        double x = v.x * cos - v.z * sin;
        double z = v.x * sin + v.z * cos;
        return new Vec3(x, v.y, z);
    }

    private void drawTexturedQuad(VertexConsumer vc, Matrix4f mat, float x0, float y0, float x1, float y1, int r, int g, int b, int a, int light, int overlay, float z) {
        vc.addVertex(mat, x0, y0, z).setColor(r, g, b, a).setUv(0, 0).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
        vc.addVertex(mat, x1, y0, z).setColor(r, g, b, a).setUv(1, 0).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
        vc.addVertex(mat, x1, y1, z).setColor(r, g, b, a).setUv(1, 1).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
        vc.addVertex(mat, x0, y1, z).setColor(r, g, b, a).setUv(0, 1).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
    }

    public enum ConeDir2D {
        NORTH, SOUTH, EAST, WEST, UP, DOWN, LEFT, RIGHT
    }
}