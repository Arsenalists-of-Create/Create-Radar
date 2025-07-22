package com.happysg.radar.block.monitor;

import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.registry.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import com.simibubi.create.foundation.utility.Color;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Renders radar monitor displays with tracks, grids, sweeps and other visual elements.
 */
public class MonitorRenderer extends SmartBlockEntityRenderer<MonitorBlockEntity> {

    // Constants for rendering depths to prevent Z-fighting
    private static final float DEPTH_BACKGROUND = 0.94f;
    private static final float DEPTH_GRID = 0.945f;
    private static final float DEPTH_SWEEP = 0.947f;
    private static final float DEPTH_TRACK_BASE = 0.95f;
    private static final float DEPTH_TRACK_INCREMENT = 0.0001f;

    // Alpha values for different elements
    private static final float ALPHA_BACKGROUND = 0.6f;
    private static final float ALPHA_GRID = 0.5f;
    private static final float ALPHA_SWEEP = 0.8f;

    // Track scaling factors
    private static final float TRACK_POSITION_SCALE = 0.75f;

    public MonitorRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(MonitorBlockEntity blockEntity, float partialTicks, PoseStack ms, MultiBufferSource bufferSource, int light, int overlay) {
        super.renderSafe(blockEntity, partialTicks, ms, bufferSource, light, overlay);

        // Skip rendering if this isn't the controller block
        if (!blockEntity.isController()) {
            return;
        }

        // Set up transformation matrix for the monitor face
        setupMonitorTransform(ms, blockEntity.getBlockState().getValue(MonitorBlock.FACING));

        // Get radar and render if it's running
        blockEntity.getRadar().ifPresent(radar -> {
            if (!radar.isRunning()) {
                return;
            }

            // Render all radar display elements
            renderRadarDisplay(radar, blockEntity, ms, bufferSource, partialTicks);
        });
    }

    /**
     * Sets up the transformation matrix to properly orient the display on the monitor face
     */
    private void setupMonitorTransform(PoseStack ms, Direction direction) {
        // Center, rotate to face direction, then rotate to be flat against the face
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YN.rotationDegrees(direction.toYRot()));
        ms.translate(-0.5, -0.5, -0.5);
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.XP.rotationDegrees(90));
        ms.translate(-0.5, -0.5, -0.5);
    }

    /**
     * Main method for rendering all radar display elements
     */
    private void renderRadarDisplay(IRadar radar, MonitorBlockEntity blockEntity, PoseStack ms,
                                    MultiBufferSource bufferSource, float partialTicks) {
        // Render in order from back to front to prevent z-fighting
        renderGrid(radar, blockEntity, ms, bufferSource);
        renderSafeZones(radar, blockEntity, ms, bufferSource);
        renderBG(blockEntity, ms, bufferSource, MonitorSprite.RADAR_BG_FILLER);
        renderBG(blockEntity, ms, bufferSource, MonitorSprite.RADAR_BG_CIRCLE);
        renderSweep(radar, blockEntity, ms, bufferSource, partialTicks);
        renderRadarTracks(radar, blockEntity, ms, bufferSource);
    }

    /**
     * Renders safety zones on the radar display
     */
    private void renderSafeZones(IRadar radar, MonitorBlockEntity blockEntity, PoseStack ms, MultiBufferSource bufferSource) {
        List<AABB> safeZones = blockEntity.safeZones;
        if (safeZones == null || safeZones.isEmpty()) {
            return;
        }
        
        int size = blockEntity.getSize();
        float range = radar.getRange();
        Direction facing = blockEntity.getBlockState().getValue(MonitorBlock.FACING);
        
        Matrix4f m = ms.last().pose();
        Matrix3f n = ms.last().normal();
        Color color = new Color(0x383b42);
        float alpha = 0.4f;

        // Render each safe zone
        for (AABB zone : safeZones) {
            // Transform zone coordinates to display coordinates
            Vec3 zoneMin = transformWorldToRadar(zone.minX, zone.minY, zone.minZ, radar, blockEntity, facing, range, size);
            Vec3 zoneMax = transformWorldToRadar(zone.maxX, zone.maxY, zone.maxZ, radar, blockEntity, facing, range, size);

            // Skip zones that are outside the display
            if (isOutsideDisplay(zoneMin) && isOutsideDisplay(zoneMax)) {
                continue;
            }

            // Render zone outline
            VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());
            renderZoneOutline(buffer, m, n, zoneMin, zoneMax, color, alpha);
        }
    }

    /**
     * Renders a zone outline with the given parameters
     */
    private void renderZoneOutline(VertexConsumer buffer, Matrix4f m, Matrix3f n,
                                   Vec3 min, Vec3 max, Color color, float alpha) {
        // Render lines for the zone boundaries
        float r = color.getRedAsFloat();
        float g = color.getGreenAsFloat();
        float b = color.getBlueAsFloat();

        // Bottom rectangle
        renderLine(buffer, m, n, (float) min.x, DEPTH_GRID, (float) min.z, (float) max.x, DEPTH_GRID, (float) min.z, r, g, b, alpha);
        renderLine(buffer, m, n, (float) max.x, DEPTH_GRID, (float) min.z, (float) max.x, DEPTH_GRID, (float) max.z, r, g, b, alpha);
        renderLine(buffer, m, n, (float) max.x, DEPTH_GRID, (float) max.z, (float) min.x, DEPTH_GRID, (float) max.z, r, g, b, alpha);
        renderLine(buffer, m, n, (float) min.x, DEPTH_GRID, (float) max.z, (float) min.x, DEPTH_GRID, (float) min.z, r, g, b, alpha);
    }

    /**
     * Helper method to render a single line
     */
    private void renderLine(VertexConsumer buffer, Matrix4f matrix, Matrix3f normal,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            float r, float g, float b, float alpha) {
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, alpha).normal(normal, 0, 1, 0).endVertex();
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, alpha).normal(normal, 0, 1, 0).endVertex();
    }

    /**
     * Renders the grid pattern on the radar display
     */
    private void renderGrid(IRadar radar, MonitorBlockEntity blockEntity, PoseStack ms, MultiBufferSource bufferSource) {
        int size = blockEntity.getSize();
        float range = radar.getRange();
        float gridSpacing = range * 2 / RadarConfig.client().gridBoxScale.get();

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucent(MonitorSprite.GRID_SQUARE.getTexture()));
        Matrix4f m = ms.last().pose();
        Matrix3f n = ms.last().normal();

        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        float xmin = 1 - size;
        float zmin = 1 - size;
        float xmax = 1;
        float zmax = 1;

        // Adjust UV coordinates based on grid spacing
        float u0 = -0.5f * gridSpacing, v0 = -0.5f * gridSpacing;
        float u1 = 0.5f * gridSpacing, v1 = -0.5f * gridSpacing;
        float u2 = 0.5f * gridSpacing, v2 = 0.5f * gridSpacing;
        float u3 = -0.5f * gridSpacing, v3 = 0.5f * gridSpacing;

        buffer.vertex(m, xmin, DEPTH_GRID, zmin)
                .color(color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), ALPHA_GRID)
                .uv(u0, v0)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(255)
                .normal(n, 0, 1, 0)
                .endVertex();

        buffer.vertex(m, xmax, DEPTH_GRID, zmin)
                .color(color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), ALPHA_GRID)
                .uv(u1, v1)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(255)
                .normal(n, 0, 1, 0)
                .endVertex();

        buffer.vertex(m, xmax, DEPTH_GRID, zmax)
                .color(color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), ALPHA_GRID)
                .uv(u2, v2)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(255)
                .normal(n, 0, 1, 0)
                .endVertex();

        buffer.vertex(m, xmin, DEPTH_GRID, zmax)
                .color(color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), ALPHA_GRID)
                .uv(u3, v3)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(255)
                .normal(n, 0, 1, 0)
                .endVertex();
    }


    private void renderRadarTracks(IRadar radar, MonitorBlockEntity monitor, PoseStack ms, MultiBufferSource bufferSource) {
        AtomicInteger depthCounter = new AtomicInteger(0);
        for (RadarTrack track : monitor.getTracks()) {
            renderTrack(track, monitor, radar, ms, bufferSource, depthCounter.getAndIncrement());
        }
    }

    /**
     * Renders a single radar track
     */
    private void renderTrack(RadarTrack track, MonitorBlockEntity monitor, IRadar radar,
                             PoseStack ms, MultiBufferSource bufferSource,
                             int depthMultiplier) {
        Direction monitorFacing = monitor.getBlockState().getValue(MonitorBlock.FACING);
        float scale = radar.getRange();
        int size = monitor.getSize();

        // Calculate track position relative to radar
        Vec3 radarPos = PhysicsHandler.getWorldPos(monitor.getLevel(), radar.getWorldPos()).getCenter();
        Vec3 relativePos = track.position().subtract(radarPos);
        if (radar.renderRelativeToMonitor()) {
            //todo change for plane radar
        }
        // Transform to display coordinates
        //todo handle direction on vs2 ships
        float xOff = calculateTrackOffset(relativePos, monitorFacing, scale, true);
        float zOff = calculateTrackOffset(relativePos, monitorFacing, scale, false);

        // Skip tracks that are outside the display range
        if (Math.abs(xOff) > 0.5f || Math.abs(zOff) > 0.5f) {
            return;
        }

        // Scale positions to fit within display
        xOff = xOff * TRACK_POSITION_SCALE;
        zOff = zOff * TRACK_POSITION_SCALE;

        // Calculate final display coordinates
        float xmin = 1 - size + (xOff * size);
        float zmin = 1 - size + (zOff * size);
        float xmax = xOff * size + 1;
        float zmax = zOff * size + 1;

        // Calculate depth to prevent z-fighting between tracks
        float depth = DEPTH_TRACK_BASE + (depthMultiplier * DEPTH_TRACK_INCREMENT);

        // Calculate fade based on track age
        long currentTime = monitor.getLevel().getGameTime();
        float trackAge = currentTime - track.scannedTime();
        float fadeTime = 100f; // Time in ticks for track to fade out
        float fade = Math.min(1.0f, trackAge / fadeTime);
        float alpha = 1.0f - fade;

        // Get track color from filter
        MonitorFilter filter = monitor.filter;
        Color color = filter.getColor(track);

        // Render base track
        VertexConsumer buffer = getBuffer(bufferSource, track.getSprite());
        Matrix4f m = ms.last().pose();
        Matrix3f n = ms.last().normal();
        renderVertices(buffer, m, n, color, alpha, depth, xmin, zmin, xmax, zmax);

        // Render selection indicators if needed
        if (track.id().equals(monitor.hoveredEntity)) {
            renderVertices(getBuffer(bufferSource, MonitorSprite.TARGET_HOVERED),
                    m, n, new Color(255, 255, 0), alpha, depth - 0.0001f,
                    xmin, zmin, xmax, zmax);
        }

        if (track.id().equals(monitor.selectedEntity)) {
            renderVertices(getBuffer(bufferSource, MonitorSprite.TARGET_SELECTED),
                    m, n, new Color(255, 0, 0), alpha, depth - 0.0002f,
                    xmin, zmin, xmax, zmax);
        }
    }

    /**
     * Calculates the offset for a track on the display
     */
    private float calculateTrackOffset(Vec3 relativePos, Direction monitorFacing, float scale, boolean isXOffset) {
        float offset;

        if (isXOffset) {
            offset = monitorFacing.getAxis() == Direction.Axis.Z ?
                    getOffset(relativePos.x(), scale) : getOffset(relativePos.z(), scale);

            // Flip offset based on facing direction
            if (monitorFacing == Direction.NORTH || monitorFacing == Direction.EAST) {
                offset = -offset;
            }
        } else {
            offset = monitorFacing.getAxis() == Direction.Axis.Z ?
                    getOffset(relativePos.z(), scale) : getOffset(relativePos.x(), scale);

            // Flip offset based on facing direction
            if (monitorFacing == Direction.NORTH || monitorFacing == Direction.WEST) {
                offset = -offset;
            }
        }

        return offset;
    }

    /**
     * Converts a world coordinate to a proportional offset on the display
     */
    private float getOffset(double coordinate, float scale) {
        return (float) (coordinate / scale) / 2f;
    }

    /**
     * Checks if a point is outside the display bounds
     */
    private boolean isOutsideDisplay(Vec3 point) {
        return Math.abs(point.x) > 0.5 || Math.abs(point.z) > 0.5;
    }

    /**
     * Transforms world coordinates to radar display coordinates
     */
    private Vec3 transformWorldToRadar(double x, double y, double z, IRadar radar,
                                       MonitorBlockEntity monitor, Direction facing,
                                       float range, int size) {
        Vec3 radarPos = PhysicsHandler.getWorldPos(monitor.getLevel(), radar.getWorldPos()).getCenter();
        Vec3 relativePos = new Vec3(x, y, z).subtract(radarPos);

        float xOff = calculateTrackOffset(relativePos, facing, range, true) * TRACK_POSITION_SCALE;
        float zOff = calculateTrackOffset(relativePos, facing, range, false) * TRACK_POSITION_SCALE;

        float displayX = 1 - size + (xOff * size);
        float displayZ = 1 - size + (zOff * size);

        return new Vec3(displayX, DEPTH_GRID, displayZ);
    }

    /**
     * Gets the appropriate buffer for a given sprite
     */
    private VertexConsumer getBuffer(MultiBufferSource bufferSource, MonitorSprite sprite) {
        return bufferSource.getBuffer(ModRenderTypes.polygonOffset(sprite.getTexture()));
    }

    /**
     * Renders vertices for a quad with the given parameters
     */
    private void renderVertices(VertexConsumer buffer, Matrix4f m, Matrix3f n,
                                Color color, float alpha, float depth,
                                float xmin, float zmin, float xmax, float zmax) {
        float u0 = 0, v0 = 0, u1 = 1, v1 = 0, u2 = 1, v2 = 1, u3 = 0, v3 = 1;
        float r = color.getRedAsFloat();
        float g = color.getGreenAsFloat();
        float b = color.getBlueAsFloat();

        buffer.vertex(m, xmin, depth, zmin)
                .color(r, g, b, alpha)
                .uv(u0, v0)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(255)
                .normal(n, 0, 1, 0)
                .endVertex();

        buffer.vertex(m, xmax, depth, zmin)
                .color(r, g, b, alpha)
                .uv(u1, v1)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(255)
                .normal(n, 0, 1, 0)
                .endVertex();

        buffer.vertex(m, xmax, depth, zmax)
                .color(r, g, b, alpha)
                .uv(u2, v2)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(255)
                .normal(n, 0, 1, 0)
                .endVertex();

        buffer.vertex(m, xmin, depth, zmax)
                .color(r, g, b, alpha)
                .uv(u3, v3)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(255)
                .normal(n, 0, 1, 0)
                .endVertex();
    }

    /**
     * Renders a background element on the display
     */
    private void renderBG(MonitorBlockEntity blockEntity, PoseStack ms,
                          MultiBufferSource bufferSource, MonitorSprite monitorSprite) {
        int size = blockEntity.getSize();
        Matrix4f m = ms.last().pose();
        Matrix3f n = ms.last().normal();
        Color color = new Color(RadarConfig.client().groundRadarColor.get());

        float minX = 1f - size;
        float minZ = 1f - size;
        float maxX = 1;
        float maxZ = 1;

        renderVertices(getBuffer(bufferSource, monitorSprite), m, n, color, ALPHA_BACKGROUND, DEPTH_BACKGROUND,
                minX, minZ, maxX, maxZ);
    }

    /**
     * Renders the radar sweep animation
     */
    public void renderSweep(IRadar radar, MonitorBlockEntity controller, PoseStack ms,
                            MultiBufferSource bufferSource, float partialTicks) {
        if (!radar.isRunning())
            return;

        VertexConsumer buffer = bufferSource.getBuffer(ModRenderTypes.polygonOffset(MonitorSprite.RADAR_SWEEP.getTexture()));
        Matrix4f m = ms.last().pose();
        Matrix3f n = ms.last().normal();
        Color color = new Color(RadarConfig.client().groundRadarColor.get());

        float monitorAngle = 0;
        if (radar.renderRelativeToMonitor()) {
            // Calculate the current angle
            Direction monitorFacing = controller.getBlockState().getValue(MonitorBlock.FACING);
            Vec3 facingVec = new Vec3(monitorFacing.getStepX(), monitorFacing.getStepY(), monitorFacing.getStepZ());
            Vec3 angleVec = PhysicsHandler.getWorldVecDirectionTransform(facingVec, controller);
            monitorAngle = (float) Math.toDegrees(Math.atan2(angleVec.x, angleVec.z));

            if (monitorFacing == Direction.NORTH || monitorFacing == Direction.SOUTH) {
                monitorAngle = (monitorAngle + 180) % 360; //fixme not sure why this is needed, but off by 180
            }

            // Normalize to positive angles
            monitorAngle = (monitorAngle + 360) % 360;
        }

        float currentAngle = radar.getGlobalAngle();
        // Make sure we're working with normalized angles
        currentAngle = (currentAngle + 360) % 360;

        // Calculate the relative angle between monitor and radar
        // This is the key fix - ensure we're getting the shortest angle difference
        float angleDiff = monitorAngle + currentAngle;
        // Normalize to -180 to 180 for rotation calculation
        if (angleDiff > 180) angleDiff -= 360;
        if (angleDiff < -180) angleDiff += 360;

        // Convert to radians for sin/cos
        float angleRad = angleDiff * (float) Math.PI / 180.0f;
        float cos = (float) Math.cos(angleRad);
        float sin = (float) Math.sin(angleRad);

        // Center coordinates for rotation calculation
        float centerX = 0.5f;
        float centerY = 0.5f;
        int size = controller.getSize();

        // Calculate UV coordinates for rotating sweep
        float u0 = centerX + (0 - centerX) * cos - (0 - centerY) * sin;
        float v0 = centerY + (0 - centerX) * sin + (0 - centerY) * cos;
        float u1 = centerX + (1 - centerX) * cos - (0 - centerY) * sin;
        float v1 = centerY + (1 - centerX) * sin + (0 - centerY) * cos;
        float u2 = centerX + (1 - centerX) * cos - (1 - centerY) * sin;
        float v2 = centerY + (1 - centerX) * sin + (1 - centerY) * cos;
        float u3 = centerX + (0 - centerX) * cos - (1 - centerY) * sin;
        float v3 = centerY + (0 - centerX) * sin + (1 - centerY) * cos;

        // Render sweep quad
        buffer.vertex(m, 1f - size, DEPTH_SWEEP, 1f - size)
                .color(color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), ALPHA_SWEEP)
                .uv(u0, v0)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(255)
                .normal(n, 0, 1, 0)
                .endVertex();

        buffer.vertex(m, 1, DEPTH_SWEEP, 1f - size)
                .color(color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), ALPHA_SWEEP)
                .uv(u1, v1)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(255)
                .normal(n, 0, 1, 0)
                .endVertex();

        buffer.vertex(m, 1, DEPTH_SWEEP, 1f)
                .color(color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), ALPHA_SWEEP)
                .uv(u2, v2)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(255)
                .normal(n, 0, 1, 0)
                .endVertex();

        buffer.vertex(m, 1f - size, DEPTH_SWEEP, 1f)
                .color(color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), ALPHA_SWEEP)
                .uv(u3, v3)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(255)
                .normal(n, 0, 1, 0)
                .endVertex();
    }
}
