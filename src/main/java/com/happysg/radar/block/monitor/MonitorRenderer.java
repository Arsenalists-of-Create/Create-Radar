package com.happysg.radar.block.monitor;

import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import com.happysg.radar.block.controller.id.IDManager;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.registry.ModRenderTypes;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.createmod.catnip.theme.Color;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;
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
    private static final float LABEL_SCALE = 0.003f;
    private static final float LABEL_Z_OFFSET = 0.03f;
    private static final float LABEL_DEPTH_NUDGE = 0.00025f;
    // Alpha values for different elements
    private static final float ALPHA_BACKGROUND = 0.6f;
    private static final float ALPHA_GRID = 0.5f;
    private static final float ALPHA_SWEEP = 0.8f;
    private static final Logger LOGGER = LogUtils.getLogger();
    // Track scaling factors
    private static final float TRACK_POSITION_SCALE = 0.75f;

    public MonitorRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(MonitorBlockEntity blockEntity, float partialTicks, PoseStack ms, MultiBufferSource bufferSource, int light, int overlay) {
        if(!RadarConfig.client().disableMonitorRendering.get()) {
            if (!blockEntity.isLinked() || !blockEntity.isController()) {
                return;
            }

            super.renderSafe(blockEntity, partialTicks, ms, bufferSource, light, overlay);

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
        buffer.addVertex(matrix, x1, y1, z1)
                .setColor(r, g, b, alpha)
                .setNormal(0, 1, 0);

        buffer.addVertex(matrix, x2, y2, z2)
                .setColor(r, g, b, alpha)
                .setNormal(0, 1, 0);
    }

    /**
     * Renders the grid pattern on the radar display
     */
    private void renderGrid(IRadar radar, MonitorBlockEntity blockEntity, PoseStack ms, MultiBufferSource bufferSource) {
        int size = blockEntity.getSize();
        float range = radar.getRange();
        float gridSpacing = range * 2 / RadarConfig.client().gridBoxScale.get();

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucent(MonitorSprite.GRID_SQUARE.getTexture()));
        PoseStack.Pose pose = ms.last();
        Matrix4f matrix = pose.pose();

        Color color = new Color(RadarConfig.client().groundRadarColor.get());

        float r = color.getRedAsFloat();
        float g = color.getGreenAsFloat();
        float b = color.getBlueAsFloat();

        float xmin = 1 - size;
        float zmin = 1 - size;
        float xmax = 1;
        float zmax = 1;

        float u0 = -0.5f * gridSpacing;
        float v0 = -0.5f * gridSpacing;
        float u1 = 0.5f * gridSpacing;
        float v1 = -0.5f * gridSpacing;
        float u2 = 0.5f * gridSpacing;
        float v2 = 0.5f * gridSpacing;
        float u3 = -0.5f * gridSpacing;
        float v3 = 0.5f * gridSpacing;

        addGridVertex(buffer, pose, matrix, xmin, DEPTH_GRID, zmin, r, g, b, ALPHA_GRID, u0, v0);
        addGridVertex(buffer, pose, matrix, xmax, DEPTH_GRID, zmin, r, g, b, ALPHA_GRID, u1, v1);
        addGridVertex(buffer, pose, matrix, xmax, DEPTH_GRID, zmax, r, g, b, ALPHA_GRID, u2, v2);
        addGridVertex(buffer, pose, matrix, xmin, DEPTH_GRID, zmax, r, g, b, ALPHA_GRID, u3, v3);
    }

    private void addGridVertex(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f matrix,
                               float x, float y, float z,
                               float r, float g, float b, float alpha,
                               float u, float v) {
        buffer.addVertex(matrix, x, y, z)
                .setColor(r, g, b, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(pose, 0, 1, 0);
    }


    private void renderRadarTracks(IRadar radar, MonitorBlockEntity monitor, PoseStack ms, MultiBufferSource bufferSource) {
        AtomicInteger depthCounter = new AtomicInteger(0);
        for (RadarTrack track : monitor.getTracks()) {
            renderTrack(track, monitor, radar, ms, bufferSource, depthCounter.getAndIncrement());
        }
    }

    private void renderTrack(RadarTrack track, MonitorBlockEntity monitor, IRadar radar,
                             PoseStack ms, MultiBufferSource bufferSource,
                             int depthMultiplier) {
        Direction monitorFacing = monitor.getBlockState().getValue(MonitorBlock.FACING);
        float scale = radar.getRange();
        int size = monitor.getSize();

        // i treat both positions as world positions here
        Vec3 radarPos = PhysicsHandler.getWorldPos(monitor.getLevel(), radar.getWorldPos()).getCenter();
        Vec3 relativePos = track.position().subtract(radarPos);

        // if we're rendering relative to the monitor, and the monitor is on a ship,
        // rotate the relative vector into ship-local axes so the screen rotates with the ship
        if (radar.renderRelativeToMonitor()) {
            if(!Mods.SABLE.isLoaded())return;
            SubLevelAccess ship = monitor.getShip();
            if (ship != null) {
                // i keep the cone "north-up" by counter-rotating track vectors by the ship yaw
                double shipYawRad = getShipYawRad(ship);
                relativePos = rotateAroundY(relativePos, -(shipYawRad + Math.PI));

            }
        }
        // Transform to display coordinates
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
        DetectionConfig filter = monitor.filter;
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

        String slug = getSlugForTrack(track, monitor);
        if (slug != null) {
            // i anchor the label to the center of the track quad
            float xCenter = (xmin + xmax) * 0.5f;
            float zCenter = (zmin + zmax) * 0.5f;

            // i nudge it "down" the screen ( +Z on your monitor plane )
            float zBelow = zCenter + LABEL_Z_OFFSET;

            // i clamp so it stays visible
            zBelow = Mth.clamp(zBelow, (1f - size) + 0.04f, 1f - 0.04f);

            renderTrackLabel(ms, bufferSource, slug, xCenter, zBelow, depth, alpha);
        }
    }
    private  Vec3 rotateAroundY(Vec3 v, double angleRad) {
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);

        // i rotate around world up (Y). this makes tracks orbit when the ship turns
        double x = v.x * cos - v.z * sin;
        double z = v.x * sin + v.z * cos;

        return new Vec3(x, v.y, z);
    }

    /**
     * i compute ship yaw only (around world Y) relative to world NORTH (-Z).
     * result is radians, where 0 means ship forward points toward north ( -Z ).
     */
    private double getShipYawRad(SubLevelAccess ship) {
        Vector3d fwd = ship.logicalPose().transformNormal(new Vector3d(0, 0, 1));
        return Math.atan2(fwd.x(), -fwd.z());
    }

    private Vec3 rotateWorldVecIntoShipFrame(SubLevelAccess ship, Vec3 worldVec) {
        Vector3d v = ship.logicalPose().transformNormalInverse(new Vector3d(worldVec.x, worldVec.y, worldVec.z));
        return new Vec3(v.x(), v.y(), v.z());
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
        float u0 = 0;
        float v0 = 0;
        float u1 = 1;
        float v1 = 0;
        float u2 = 1;
        float v2 = 1;
        float u3 = 0;
        float v3 = 1;

        float r = color.getRedAsFloat();
        float g = color.getGreenAsFloat();
        float b = color.getBlueAsFloat();

        addVertex(buffer, m, xmin, depth, zmin, r, g, b, alpha, u0, v0);
        addVertex(buffer, m, xmax, depth, zmin, r, g, b, alpha, u1, v1);
        addVertex(buffer, m, xmax, depth, zmax, r, g, b, alpha, u2, v2);
        addVertex(buffer, m, xmin, depth, zmax, r, g, b, alpha, u3, v3);
    }

    private void addVertex(VertexConsumer buffer, Matrix4f matrix,
                           float x, float y, float z,
                           float r, float g, float b, float alpha,
                           float u, float v) {
        buffer.addVertex(matrix, x, y, z)
                .setColor(r, g, b, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(0, 1, 0);
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
        if (controller.getShip() != null && radar.getRadarType().equals("spinning")) { // spinning radar on a ship
            // Calculate the current angle
            Direction monitorFacing = controller.getBlockState().getValue(MonitorBlock.FACING);
            Vec3 facingVec = new Vec3(monitorFacing.getStepX(), monitorFacing.getStepY(), monitorFacing.getStepZ());
            Vec3 angleVec = PhysicsHandler.getWorldVecDirectionTransform(facingVec, controller);
            monitorAngle = (float) Math.toDegrees(Math.atan2(angleVec.x, angleVec.z));

            if (monitorFacing == Direction.NORTH || monitorFacing == Direction.SOUTH) {
                monitorAngle = (monitorAngle + 180) % 360;
            }

            // Normalize to positive angles
            monitorAngle = (monitorAngle + 360 + 180) % 360;
        }
        float currentAngle;
        if(radar.renderRelativeToMonitor() && controller.getShip() != null && !radar.getRadarType().equals("spinning")){  // plane radar on a ship
            // Plane radar on ship - cone stays fixed, tracks rotate inside
            Direction monitorFacing = controller.getBlockState().getValue(MonitorBlock.FACING);
            Direction radarFacing   = radar.getradarDirection();
            if(radarFacing == null)return;

            ConeDir2D cone = getConeDirectionOnMonitor(monitorFacing, radarFacing);
            switch (cone){
                case NORTH -> currentAngle = 0;
                case DOWN -> currentAngle = 180;
                case LEFT -> currentAngle = 90;
                case RIGHT -> currentAngle = 270;
                default -> currentAngle = 30;
            }

        }else{ // ground based spinning radar
            Direction monitorFacing = controller.getBlockState().getValue(MonitorBlock.FACING);
            // Global angle is already in world space; only align world-north to monitor.
            Direction radarFacing   = Direction.NORTH;
            ConeDir2D cone = getConeDirectionOnMonitor(monitorFacing, radarFacing);
            switch (cone){
                case NORTH -> currentAngle = 0 + radar.getGlobalAngle();
                case DOWN -> currentAngle = 180 + radar.getGlobalAngle();
                case LEFT -> currentAngle = 90 + radar.getGlobalAngle();
                case RIGHT -> currentAngle = 270 + radar.getGlobalAngle();
                default -> currentAngle = 30;
            }

        }

        // Make sure we're working with normalized angles
        currentAngle = (currentAngle + 360) % 360;


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

        float r = color.getRedAsFloat();
        float g = color.getGreenAsFloat();
        float b = color.getBlueAsFloat();

        addSweepVertex(buffer, m, 1f - size, DEPTH_SWEEP, 1f - size, r, g, b, ALPHA_SWEEP, u0, v0);
        addSweepVertex(buffer, m, 1,        DEPTH_SWEEP, 1f - size, r, g, b, ALPHA_SWEEP, u1, v1);
        addSweepVertex(buffer, m, 1,        DEPTH_SWEEP, 1f,        r, g, b, ALPHA_SWEEP, u2, v2);
        addSweepVertex(buffer, m, 1f - size, DEPTH_SWEEP, 1f,       r, g, b, ALPHA_SWEEP, u3, v3);
    }
    private void addSweepVertex(VertexConsumer buffer, Matrix4f matrix,
                                float x, float y, float z,
                                float r, float g, float b, float alpha,
                                float u, float v) {
        buffer.addVertex(matrix, x, y, z)
                .setColor(r, g, b, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(0, 1, 0);
    }


    public enum ConeDir2D { UP, RIGHT, DOWN, LEFT,NORTH }

    public ConeDir2D getConeDirectionOnMonitor(Direction monitorFacing, Direction radarFacing) {
        int steps = cwStepsBetween(monitorFacing, radarFacing);
        return switch (steps) {
            case 0 -> ConeDir2D.NORTH;
            case 1 -> ConeDir2D.RIGHT;
            case 2 -> ConeDir2D.DOWN;
            case 3 -> ConeDir2D.LEFT;
            default -> ConeDir2D.UP;
        };
    }


    private  int cwStepsBetween(Direction from, Direction to) {
        int a = dirIndex(from);
        int b = dirIndex(to);

        // i take (b - a) mod 4 to get clockwise steps
        int steps = b - a;
        steps %= 4;
        if (steps < 0) steps += 4;
        return steps;
    }

    private int dirIndex(Direction d) {
        // i define indices in clockwise order: N=0, E=1, S=2, W=3
        return switch (d) {
            case NORTH -> 0;
            case EAST  -> 1;
            case SOUTH -> 2;
            case WEST  -> 3;
            default -> 0;
        };
    }

    private String getSlugForTrack(RadarTrack track, MonitorBlockEntity mon) {
        if (mon.getLevel() == null) return null;

        if ("Sable:ship".equals(track.entityType())) {
            IDManager.IDRecord rec = IDManager.getIDRecordByShipId(UUID.fromString(track.id()));
            if (rec != null) {
                String storedName = rec.name();
                if (storedName != null && !storedName.isBlank())
                    return storedName;
            }
        }

        // Players: null-safe
        if (track.trackCategory() == TrackCategory.PLAYER) {
            UUID uuid;
            try {
                uuid = UUID.fromString(track.getId());
            } catch (IllegalArgumentException ignored) {
                return null;
            }

            Player sp = mon.getLevel().getPlayerByUUID(uuid);

             return sp != null ? sp.getName().getString() : null;
        }

        return null;
    }


    private void renderTrackLabel(PoseStack ms, MultiBufferSource bufferSource,
                                  String text, float xCenter, float zBelow, float depth,
                                  float alpha) {

        if (alpha <= 0.02f) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        ms.pushPose();

        ms.translate(xCenter, depth + LABEL_DEPTH_NUDGE, zBelow);
        ms.mulPose(Axis.XP.rotationDegrees(90));
        ms.scale(LABEL_SCALE, LABEL_SCALE, LABEL_SCALE);

        int width = font.width(text);
        float x = -width / 2.0f;

        int a = Mth.clamp((int) (alpha * 255f), 0, 255);
        int argb = (a << 24) | 0xFFFFFF;

        int packedLight = 0xF000F0;

        font.drawInBatch(
                text,
                x, 0,
                argb,
                false,
                ms.last().pose(),
                bufferSource,
                Font.DisplayMode.NORMAL,
                0,
                packedLight
        );

        ms.popPose();
    }

}
