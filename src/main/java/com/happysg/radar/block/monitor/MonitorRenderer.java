package com.happysg.radar.block.monitor;

import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import com.happysg.radar.block.controller.id.IDManager;
import com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.skyradar.SkyRadarBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.sable.SableSilhouetteClientCache;
import com.happysg.radar.compat.sable.SableSilhouetteStatus;
import com.happysg.radar.compat.sable.SubLevelSilhouette;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.networking.packets.SableSilhouetteRequestPacket;
import com.happysg.radar.registry.ModRenderTypes;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
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
    private static final float DEPTH_LOCK_LINE = 0.946f;
    private static final float DEPTH_SWEEP = 0.947f;
    private static final float DEPTH_ARAD_CONTACT = 0.948f;
    private static final float DEPTH_TRACK_BASE = 0.95f;
    private static final float DEPTH_TRACK_INCREMENT = 0.0001f;
    private static final float LABEL_SCALE = 0.003f;
    private static final float LABEL_Z_OFFSET = 0.03f;
    private static final float LABEL_DEPTH_NUDGE = 0.00025f;
    // Alpha values for different elements
    private static final float ALPHA_BACKGROUND = 0.6f;
    private static final float ARAD_RING_ALPHA = 0.45f;
    private static final float ALPHA_GRID = 0.5f;
    private static final float ALPHA_SWEEP = 0.8f;
    private static final int PLANE_SWEEP_CYCLE_TICKS = 20;
    private static final float PLANE_SWEEP_RADIUS_SCALE = 0.81f;
    private static final float ARAD_OUTER_RING_RADIUS = 0.40625f;
    private static final float ARAD_MIDDLE_RING_RADIUS = 0.27083334f;
    private static final float ARAD_INNER_RING_RADIUS = 0.1015625f;
    private static final float ARAD_CONTACT_SCALE = 0.18f;
    private static final float ARAD_PRIMARY_THREAT_SCALE = 0.135f;
    private static final float SINGLE_BLOCK_MONITOR_RENDER_SCALE = 0.9f;
    private static final Logger LOGGER = LogUtils.getLogger();
    // Track scaling factors
    private static final float TRACK_POSITION_SCALE = 0.75f;

    public MonitorRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(MonitorBlockEntity blockEntity, float partialTicks, PoseStack ms, MultiBufferSource bufferSource, int light, int overlay) {
        if(!RadarConfig.client().disableMonitorRendering.get()) {
            if (blockEntity.isAradLinked()) {
                if (!blockEntity.isController()) {
                    return;
                }
                super.renderSafe(blockEntity, partialTicks, ms, bufferSource, light, overlay);
                setupMonitorTransform(ms, blockEntity.getBlockState().getValue(MonitorBlock.FACING));
                applySingleBlockMonitorScale(ms, blockEntity);
                renderAradDisplay(blockEntity, ms, bufferSource);
                return;
            }
            if (!blockEntity.isLinked() || !blockEntity.isController()) {
                return;
            }

            super.renderSafe(blockEntity, partialTicks, ms, bufferSource, light, overlay);

            // Set up transformation matrix for the monitor face
            setupMonitorTransform(ms, blockEntity.getBlockState().getValue(MonitorBlock.FACING));
            applySingleBlockMonitorScale(ms, blockEntity);

            if (blockEntity.getRunningRadarInfos().isEmpty()) {
                return;
            }

            renderRadarDisplay(blockEntity, ms, bufferSource, partialTicks);
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

    private void applySingleBlockMonitorScale(PoseStack ms, MonitorBlockEntity blockEntity) {
        if (blockEntity.getSize() != 1) {
            return;
        }
        ms.translate(0.5, 0.0, 0.5);
        ms.scale(SINGLE_BLOCK_MONITOR_RENDER_SCALE, 1.0f, SINGLE_BLOCK_MONITOR_RENDER_SCALE);
        ms.translate(-0.5, 0.0, -0.5);
    }

    /**
     * Main method for rendering all radar display elements
     */
    private void renderRadarDisplay(MonitorBlockEntity blockEntity, PoseStack ms,
                                    MultiBufferSource bufferSource, float partialTicks) {
        // Render in order from back to front to prevent z-fighting
        MonitorProjection projection = MonitorProjection.create(blockEntity);

        renderGrid(projection, blockEntity, ms, bufferSource);
        renderSafeZones(projection, blockEntity, ms, bufferSource);

        for (MonitorBlockEntity.RadarDisplayInfo radarInfo : blockEntity.getRunningRadarInfos()) {
            MonitorProjection.DisplayPoint radarCenter = projection.project(radarInfo.center());
            float scale = projection.displayScale(radarInfo.range());
            IRadar liveRadar = resolveLiveRadar(blockEntity, radarInfo);
            if (isPlaneRadar(liveRadar != null ? liveRadar.getRadarType() : radarInfo.type())) {
                renderPlaneSweepConeBackground(radarInfo, liveRadar, blockEntity, projection, ms, bufferSource, radarCenter, scale, partialTicks);
                renderPlaneRadarArc(radarInfo, liveRadar, blockEntity, projection, ms, bufferSource, radarCenter, scale, partialTicks);
            } else {
                // renderBG(blockEntity, ms, bufferSource, MonitorSprite.RADAR_BG_FILLER, radarCenter, scale);
                renderBG(blockEntity, ms, bufferSource, MonitorSprite.RADAR_BG_CIRCLE, radarCenter, scale);
            }
            renderOwnedLockLine(radarInfo, blockEntity, projection, ms, bufferSource);
            renderSweep(radarInfo, liveRadar, blockEntity, projection, ms, bufferSource, radarCenter, scale, partialTicks);
        }

        renderRadarTracks(projection, blockEntity, ms, bufferSource);
    }

    private void renderAradDisplay(MonitorBlockEntity blockEntity, PoseStack ms, MultiBufferSource bufferSource) {
        MonitorProjection.DisplayPoint center = new MonitorProjection.DisplayPoint(0f, 0f);
        renderAradCircle(blockEntity, ms, bufferSource, center, 1.0f, DEPTH_BACKGROUND);
        renderAradCircle(blockEntity, ms, bufferSource, center, 2f / 3f, DEPTH_BACKGROUND + 0.0002f);
        renderAradCircle(blockEntity, ms, bufferSource, center, 0.25f, DEPTH_BACKGROUND + 0.0004f);
        renderAradContacts(blockEntity, ms, bufferSource);
    }

    private void renderAradContacts(MonitorBlockEntity blockEntity, PoseStack ms, MultiBufferSource bufferSource) {
        int size = blockEntity.getSize();
        Matrix4f m = ms.last().pose();
        Matrix3f n = ms.last().normal();

        for (MonitorBlockEntity.RwrDisplayInfo contact : blockEntity.getRwrInfos()) {
            Color color = aradContactColor(contact);
            float radius = contact.exactLocked()
                    ? ARAD_INNER_RING_RADIUS
                    : contact.withinRadarRange() ? ARAD_MIDDLE_RING_RADIUS : ARAD_OUTER_RING_RADIUS;
            radius += contact.radiusOffset();
            MonitorProjection.DisplayPoint point = aradPoint(contact.bearingDegrees(), monitorForwardBearing(blockEntity), radius);
            MonitorProjection.Quad quad = centeredAradContactQuad(point, size);
            renderVertices(getBuffer(bufferSource, spriteFor(contact)), m, n, color, 1.0f, DEPTH_ARAD_CONTACT,
                    quad.minX(), quad.minZ(), quad.maxX(), quad.maxZ());
            if (hasThreatOverlay(contact)) {
                MonitorProjection.Quad threatQuad = centeredAradContactQuad(point, size, ARAD_PRIMARY_THREAT_SCALE);
                renderVertices(getBuffer(bufferSource, MonitorSprite.RWR_PRIMARY_THREAT), m, n, color, 1.0f,
                        DEPTH_ARAD_CONTACT + DEPTH_TRACK_INCREMENT,
                        threatQuad.minX(), threatQuad.minZ(), threatQuad.maxX(), threatQuad.maxZ());
            }
        }
    }

    private static Color aradContactColor(MonitorBlockEntity.RwrDisplayInfo contact) {
        if (contact.exactLocked()) {
            return new Color(0xff0000);
        }
        if (contact.friendly()) {
            return new Color(0x3399ff);
        }
        return new Color(RadarConfig.client().groundRadarColor.get());
    }

    private static boolean hasThreatOverlay(MonitorBlockEntity.RwrDisplayInfo contact) {
        return contact.exactLocked() || (contact.primaryThreat() && !contact.friendly());
    }

    private static MonitorProjection.Quad centeredAradContactQuad(MonitorProjection.DisplayPoint point, int monitorSize) {
        return centeredAradContactQuad(point, monitorSize, ARAD_CONTACT_SCALE);
    }

    private static MonitorProjection.Quad centeredAradContactQuad(MonitorProjection.DisplayPoint point, int monitorSize, float scale) {
        float centerX = 1f - monitorSize / 2f + point.xOffset() * monitorSize;
        float centerZ = 1f - monitorSize / 2f + point.zOffset() * monitorSize;
        float half = monitorSize * scale * 0.5f;
        return new MonitorProjection.Quad(centerX - half, centerZ - half, centerX + half, centerZ + half);
    }

    private static MonitorProjection.DisplayPoint aradPoint(float bearingDegrees, float forwardBearingDegrees, float radius) {
        double radians = Math.toRadians(bearingDegrees - forwardBearingDegrees + 180.0f);
        return new MonitorProjection.DisplayPoint(
                (float) (-Math.sin(radians) * radius),
                (float) (-Math.cos(radians) * radius)
        );
    }

    private static float monitorForwardBearing(MonitorBlockEntity blockEntity) {
        Direction monitorFacing = blockEntity.getBlockState().getValue(MonitorBlock.FACING);
        Vec3 forward = new Vec3(monitorFacing.getStepX(), monitorFacing.getStepY(), monitorFacing.getStepZ());

        if (blockEntity.getShip() != null) {
            forward = PhysicsHandler.getWorldVecDirectionTransform(forward, blockEntity);
        }

        double horizontalLengthSqr = forward.x * forward.x + forward.z * forward.z;
        if (horizontalLengthSqr < 1.0E-6) {
            return 0.0f;
        }

        double angle = Math.toDegrees(Math.atan2(forward.x, forward.z));
        angle %= 360.0;
        if (angle < 0.0) {
            angle += 360.0;
        }
        return (float) angle;
    }

    private static MonitorSprite spriteFor(MonitorBlockEntity.RwrDisplayInfo contact) {
        return switch (contact.radarType()) {
            case SKY -> MonitorSprite.SKY_RADAR_SYMBOL;
            case AIRBORNE -> MonitorSprite.PLANE_RADAR_SYMBOL;
            case GROUND -> MonitorSprite.RADAR_SYMBOL;
        };
    }

    private void renderAradCircle(MonitorBlockEntity blockEntity, PoseStack ms, MultiBufferSource bufferSource,
                                  MonitorProjection.DisplayPoint center, float scale, float depth) {
        int size = blockEntity.getSize();
        Matrix4f m = ms.last().pose();
        Matrix3f n = ms.last().normal();
        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        MonitorProjection.Quad quad = MonitorProjection.scaledQuad(center, size, scale);

        renderVertices(getBuffer(bufferSource, MonitorSprite.RADAR_BG_CIRCLE), m, n, color, ARAD_RING_ALPHA, depth,
                quad.minX(), quad.minZ(), quad.maxX(), quad.maxZ());
    }

    /**
     * Renders safety zones on the radar display
     */
    private void renderSafeZones(MonitorProjection projection, MonitorBlockEntity blockEntity, PoseStack ms, MultiBufferSource bufferSource) {
        List<AABB> safeZones = blockEntity.safeZones;
        if (safeZones == null || safeZones.isEmpty()) {
            return;
        }

        Matrix4f m = ms.last().pose();
        Matrix3f n = ms.last().normal();
        Color color = new Color(0x383b42);
        float alpha = 0.4f;

        // Render each safe zone
        for (AABB zone : safeZones) {
            // Transform zone coordinates to display coordinates
            Vec3 zoneMin = transformWorldToRadar(zone.minX, zone.minY, zone.minZ, projection, blockEntity.getSize());
            Vec3 zoneMax = transformWorldToRadar(zone.maxX, zone.maxY, zone.maxZ, projection, blockEntity.getSize());

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
    private void renderGrid(MonitorProjection projection, MonitorBlockEntity blockEntity, PoseStack ms, MultiBufferSource bufferSource) {
        int size = blockEntity.getSize();
        float range = projection.halfSpan();
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


    private void renderRadarTracks(MonitorProjection projection, MonitorBlockEntity monitor, PoseStack ms, MultiBufferSource bufferSource) {
        AtomicInteger depthCounter = new AtomicInteger(0);
        for (RadarTrack track : monitor.getTracks()) {
            renderTrack(track, monitor, projection, ms, bufferSource, depthCounter.getAndIncrement());
        }
    }

    private void renderTrack(RadarTrack track, MonitorBlockEntity monitor, MonitorProjection projection,
                             PoseStack ms, MultiBufferSource bufferSource,
                             int depthMultiplier) {
        int size = monitor.getSize();

        MonitorProjection.DisplayPoint point = projection.project(track.position());

        // Skip tracks that are outside the display range
        if (point.outside()) {
            return;
        }

        MonitorProjection.Quad quad = MonitorProjection.fullSizeQuad(point, size);

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
        renderVertices(buffer, m, n, color, alpha, depth, quad.minX(), quad.minZ(), quad.maxX(), quad.maxZ());
        renderSableSilhouette(track, monitor, projection, ms, bufferSource, depth - 0.0003f, alpha, partialTicksFromMinecraft());

        // Render selection indicators if needed
        if (track.id().equals(monitor.hoveredEntity)) {
            renderVertices(getBuffer(bufferSource, MonitorSprite.TARGET_HOVERED),
                    m, n, new Color(255, 255, 0), alpha, depth - 0.0001f,
                    quad.minX(), quad.minZ(), quad.maxX(), quad.maxZ());
        }
        if (track.id().equals(monitor.selectedEntity)) {
            renderVertices(getBuffer(bufferSource, MonitorSprite.TARGET_SELECTED),
                    m, n, new Color(255, 0, 0), alpha, depth - 0.0002f,
                    quad.minX(), quad.minZ(), quad.maxX(), quad.maxZ());
        }

        String slug = getSlugForTrack(track, monitor);
        if (slug != null) {
            // i anchor the label to the center of the track quad
            float xCenter = (quad.minX() + quad.maxX()) * 0.5f;
            float zCenter = (quad.minZ() + quad.maxZ()) * 0.5f;

            // i nudge it "down" the screen ( +Z on your monitor plane )
            float zBelow = zCenter + LABEL_Z_OFFSET;

            // i clamp so it stays visible
            zBelow = Mth.clamp(zBelow, (1f - size) + 0.04f, 1f - 0.04f);

            renderTrackLabel(ms, bufferSource, slug, xCenter, zBelow, depth, alpha);
        }
    }

    private float partialTicksFromMinecraft() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
    }

    private void renderSableSilhouette(RadarTrack track, MonitorBlockEntity monitor, MonitorProjection projection,
                                       PoseStack ms, MultiBufferSource bufferSource, float depth, float alpha, float partialTicks) {
        if (!RadarConfig.client().renderSableSilhouettes.get()
                || !Mods.SABLE.isLoaded()
                || track.trackCategory() != TrackCategory.SABLE
                || track.getSilhouetteId() == null
                || !SableSilhouetteStatus.drawable(track.getSilhouetteStatus())
                || monitor.getLevel() == null) {
            return;
        }

        Vec3 center = monitor.getRadarCenterPos();
        if (center != null && center.distanceTo(track.position()) > RadarConfig.client().sableSilhouetteMaxRenderDistance.get()) {
            return;
        }

        UUID silhouetteId = track.getSilhouetteId();
        int revision = track.getSilhouetteRevision();
        SubLevelSilhouette silhouette = SableSilhouetteClientCache.get(silhouetteId, revision);
        if (silhouette == null) {
            long gameTime = monitor.getLevel().getGameTime();
            if (SableSilhouetteClientCache.shouldRequest(silhouetteId, revision, gameTime)) {
                SableSilhouetteRequestPacket.send(monitor.getControllerPos(), silhouetteId, revision);
            }
            if (RadarConfig.client().sableSilhouetteDebugFallbackRectangle.get()) {
                renderSilhouetteFallbackRectangle(track, monitor, projection, ms, bufferSource, depth, alpha);
            }
            return;
        }

        SubLevelAccess subLevel = getClientSubLevel(silhouetteId);
        if (subLevel == null) {
            return;
        }

        long gameTime = monitor.getLevel().getGameTime();
        Pose3dc pose = subLevel instanceof ClientSubLevelAccess clientSubLevel
                ? clientSubLevel.renderPose(partialTicks)
                : subLevel.logicalPose();
        SubLevelSilhouette.ProjectionSettings projectionSettings = silhouetteProjectionSettings();
        SubLevelSilhouette.ProjectedSilhouette projected = SableSilhouetteClientCache.getProjected(
                silhouetteId,
                revision,
                gameTime,
                projectionSettings,
                () -> {
                    Vector3d scratch = new Vector3d();
                    return silhouette.project(
                            (localX, localY, localZ, destination) -> {
                                scratch.set(localX, localY, localZ);
                                Vector3d transformed = pose.transformPosition(scratch);
                                destination.set(transformed.x(), transformed.y(), transformed.z());
                            },
                            projectionSettings
                    );
                }
        );
        if (projected == null || projected.isEmpty()) {
            return;
        }

        Color lineColor = RadarConfig.client().sableSilhouetteDebugOverlay.get()
                ? new Color(0x00ffff)
                : sableTrackColor(track);
        float r = lineColor.getRedAsFloat();
        float g = lineColor.getGreenAsFloat();
        float b = lineColor.getBlueAsFloat();
        VertexConsumer lineBuffer = bufferSource.getBuffer(RenderType.lines());
        Matrix4f m = ms.last().pose();
        Matrix3f n = ms.last().normal();
        int rendered = 0;
        int maxSegments = RadarConfig.client().sableSilhouetteMaxRenderedSegments.get();
        double projectY = track.position().y;

        for (SubLevelSilhouette.LineSegment segment : projected.boundarySegments()) {
            if (rendered++ >= maxSegments) {
                break;
            }
            Vec3 start = transformWorldToRadar(segment.start().x(), projectY, segment.start().z(), projection, monitor.getSize());
            Vec3 end = transformWorldToRadar(segment.end().x(), projectY, segment.end().z(), projection, monitor.getSize());
            if (isOutsideDisplay(start) && isOutsideDisplay(end)) {
                continue;
            }
            renderLine(lineBuffer, m, n, (float) start.x, depth, (float) start.z, (float) end.x, depth, (float) end.z, r, g, b, alpha * 0.85f);
        }
    }

    private SubLevelAccess getClientSubLevel(UUID id) {
        if (Minecraft.getInstance().level == null) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(Minecraft.getInstance().level);
        return container == null ? null : container.getSubLevel(id);
    }

    private static Color sableTrackColor(RadarTrack track) {
        return track.isFriendly()
                ? new Color(RadarTrack.FRIENDLY_RADAR_COLOR)
                : new Color(RadarConfig.client().SableColor.get());
    }

    private SubLevelSilhouette.ProjectionSettings silhouetteProjectionSettings() {
        return new SubLevelSilhouette.ProjectionSettings(
                RadarConfig.client().sableSilhouetteCellSize.getF(),
                384,
                120_000,
                RadarConfig.client().sableSilhouetteMaxRenderedSegments.get()
        );
    }

    private void renderSilhouetteFallbackRectangle(RadarTrack track, MonitorBlockEntity monitor, MonitorProjection projection,
                                                   PoseStack ms, MultiBufferSource bufferSource, float depth, float alpha) {
        MonitorProjection.DisplayPoint point = projection.project(track.position());
        if (point.outside()) {
            return;
        }
        MonitorProjection.Quad quad = MonitorProjection.fullSizeQuad(point, monitor.getSize());
        VertexConsumer lineBuffer = bufferSource.getBuffer(RenderType.lines());
        Matrix4f m = ms.last().pose();
        Matrix3f n = ms.last().normal();
        Color color = new Color(0xff00ff);
        float r = color.getRedAsFloat();
        float g = color.getGreenAsFloat();
        float b = color.getBlueAsFloat();
        renderLine(lineBuffer, m, n, quad.minX(), depth, quad.minZ(), quad.maxX(), depth, quad.minZ(), r, g, b, alpha);
        renderLine(lineBuffer, m, n, quad.maxX(), depth, quad.minZ(), quad.maxX(), depth, quad.maxZ(), r, g, b, alpha);
        renderLine(lineBuffer, m, n, quad.maxX(), depth, quad.maxZ(), quad.minX(), depth, quad.maxZ(), r, g, b, alpha);
        renderLine(lineBuffer, m, n, quad.minX(), depth, quad.maxZ(), quad.minX(), depth, quad.minZ(), r, g, b, alpha);
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
    private Vec3 transformWorldToRadar(double x, double y, double z, MonitorProjection projection, int size) {
        MonitorProjection.DisplayPoint point = projection.project(new Vec3(x, y, z));
        float displayX = 1 - size / 2f + (point.xOffset() * size);
        float displayZ = 1 - size / 2f + (point.zOffset() * size);

        return new Vec3(displayX, DEPTH_GRID, displayZ);
    }

    /**
     * Gets the appropriate buffer for a given sprite
     */
    private VertexConsumer getBuffer(MultiBufferSource bufferSource, MonitorSprite sprite) {
        return bufferSource.getBuffer(ModRenderTypes.polygonOffset(sprite.getTexture()));
    }

    private VertexConsumer getSolidBuffer(MultiBufferSource bufferSource) {
        return bufferSource.getBuffer(ModRenderTypes.solidPolygonOffset());
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

    private void renderVertices(VertexConsumer buffer, Matrix4f m, Matrix3f n,
                                Color color, float alpha, float depth,
                                float x0, float z0, float x1, float z1, float x2, float z2, float x3, float z3) {
        float r = color.getRedAsFloat();
        float g = color.getGreenAsFloat();
        float b = color.getBlueAsFloat();

        addVertex(buffer, m, x0, depth, z0, r, g, b, alpha, 0, 0);
        addVertex(buffer, m, x1, depth, z1, r, g, b, alpha, 1, 0);
        addVertex(buffer, m, x2, depth, z2, r, g, b, alpha, 1, 1);
        addVertex(buffer, m, x3, depth, z3, r, g, b, alpha, 0, 1);
    }

    private void renderSolidVertices(VertexConsumer buffer, Matrix4f m,
                                     Color color, float alpha, float depth,
                                     float x0, float z0, float x1, float z1, float x2, float z2, float x3, float z3) {
        float r = color.getRedAsFloat();
        float g = color.getGreenAsFloat();
        float b = color.getBlueAsFloat();

        buffer.addVertex(m, x0, depth, z0).setColor(r, g, b, alpha);
        buffer.addVertex(m, x1, depth, z1).setColor(r, g, b, alpha);
        buffer.addVertex(m, x2, depth, z2).setColor(r, g, b, alpha);
        buffer.addVertex(m, x3, depth, z3).setColor(r, g, b, alpha);
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
                          MultiBufferSource bufferSource, MonitorSprite monitorSprite,
                          MonitorProjection.DisplayPoint center, float scale) {
        int size = blockEntity.getSize();
        Matrix4f m = ms.last().pose();
        Matrix3f n = ms.last().normal();
        Color color = new Color(RadarConfig.client().groundRadarColor.get());

        MonitorProjection.Quad quad = MonitorProjection.scaledQuad(center, size, scale);

        renderVertices(getBuffer(bufferSource, monitorSprite), m, n, color, ALPHA_BACKGROUND, DEPTH_BACKGROUND,
                quad.minX(), quad.minZ(), quad.maxX(), quad.maxZ());
    }

    /**
     * Renders the radar sweep animation
     */
    public void renderSweep(MonitorBlockEntity.RadarDisplayInfo radar, IRadar liveRadar, MonitorBlockEntity controller,
                            MonitorProjection projection, PoseStack ms,
                            MultiBufferSource bufferSource, MonitorProjection.DisplayPoint center, float scale, float partialTicks) {
        if (!radar.running())
            return;
        String radarType = liveRadar != null ? liveRadar.getRadarType() : radar.type();
        if (isOwnedLock(radar, liveRadar))
            return;
        if (isPlaneRadar(radarType)) {
            renderPlaneSweepLine(radar, liveRadar, controller, projection, ms, bufferSource, center, scale, partialTicks);
            return;
        }

        VertexConsumer buffer = bufferSource.getBuffer(ModRenderTypes.polygonOffset(MonitorSprite.RADAR_SWEEP.getTexture()));
        Matrix4f m = ms.last().pose();
        Matrix3f n = ms.last().normal();
        Color color = new Color(RadarConfig.client().groundRadarColor.get());

        float monitorAngle = 0;
        boolean renderRelative = liveRadar != null ? liveRadar.renderRelativeToMonitor() : radar.renderRelativeToMonitor();
        Direction liveDirection = liveRadar != null ? liveRadar.getradarDirection() : radar.direction();
        float globalAngle = getRenderGlobalAngle(radar, liveRadar, partialTicks);
        boolean spinningLike = radarType.equals("spinning") || radarType.equals("sky");

        if (controller.getShip() != null && spinningLike) { // spinning radar on a ship
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
        if(renderRelative && controller.getShip() != null && !spinningLike){  // plane radar on a ship
            Direction monitorFacing = controller.getBlockState().getValue(MonitorBlock.FACING);
            currentAngle = alignGlobalAngleToMonitor(monitorFacing, globalAngle);

        }else{ // ground based spinning radar
            Direction monitorFacing = controller.getBlockState().getValue(MonitorBlock.FACING);
            // Global angle is already in world space; only align world-north to monitor.
            Direction radarFacing   = Direction.NORTH;
            ConeDir2D cone = getConeDirectionOnMonitor(monitorFacing, radarFacing);
            switch (cone){
                case NORTH -> currentAngle = 0 + globalAngle;
                case DOWN -> currentAngle = 180 + globalAngle;
                case LEFT -> currentAngle = 90 + globalAngle;
                case RIGHT -> currentAngle = 270 + globalAngle;
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
        MonitorProjection.Quad quad = MonitorProjection.scaledQuad(center, size, scale);

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

        addSweepVertex(buffer, m, quad.minX(), DEPTH_SWEEP, quad.minZ(), r, g, b, ALPHA_SWEEP, u0, v0);
        addSweepVertex(buffer, m, quad.maxX(), DEPTH_SWEEP, quad.minZ(), r, g, b, ALPHA_SWEEP, u1, v1);
        addSweepVertex(buffer, m, quad.maxX(), DEPTH_SWEEP, quad.maxZ(), r, g, b, ALPHA_SWEEP, u2, v2);
        addSweepVertex(buffer, m, quad.minX(), DEPTH_SWEEP, quad.maxZ(), r, g, b, ALPHA_SWEEP, u3, v3);
    }

    private IRadar resolveLiveRadar(MonitorBlockEntity monitor, MonitorBlockEntity.RadarDisplayInfo info) {
        if (monitor.getLevel() == null) return null;
        if (monitor.getLevel().getBlockEntity(info.pos()) instanceof IRadar radar) {
            return radar;
        }
        return null;
    }

    private float getRenderGlobalAngle(MonitorBlockEntity.RadarDisplayInfo info, IRadar liveRadar, float partialTicks) {
        float angle = liveRadar != null ? liveRadar.getGlobalAngle() : info.globalAngle();
        if (liveRadar instanceof RadarBearingBlockEntity bearing) {
            angle += bearing.getAngularSpeed() * partialTicks;
        } else if (liveRadar instanceof SkyRadarBlockEntity skyRadar) {
            angle += skyRadar.getEffectiveAngularSpeed() * partialTicks;
        } else if (liveRadar == null && info.angularSpeed() != 0f && Minecraft.getInstance().level != null) {
            long elapsed = Minecraft.getInstance().level.getGameTime() - info.angleSnapshotTime();
            angle += info.angularSpeed() * (elapsed + partialTicks);
        }
        return (angle + 360f) % 360f;
    }

    private float alignGlobalAngleToMonitor(Direction monitorFacing, float globalAngle) {
        ConeDir2D cone = getConeDirectionOnMonitor(monitorFacing, Direction.NORTH);
        return switch (cone) {
            case NORTH -> globalAngle;
            case DOWN -> 180 + globalAngle;
            case LEFT -> 90 + globalAngle;
            case RIGHT -> 270 + globalAngle;
            default -> globalAngle;
        };
    }

    private boolean isOwnedLock(MonitorBlockEntity.RadarDisplayInfo radar, IRadar liveRadar) {
        String radarType = liveRadar != null ? liveRadar.getRadarType() : radar.type();
        return isLockCapableRadar(radarType) && radar.ownedLockedTargetPos() != null;
    }

    private void renderOwnedLockLine(MonitorBlockEntity.RadarDisplayInfo radar, MonitorBlockEntity monitor,
                                     MonitorProjection projection, PoseStack ms, MultiBufferSource bufferSource) {
        if (!isLockCapableRadar(radar.type()) || radar.ownedLockedTargetPos() == null) {
            return;
        }

        MonitorProjection.DisplayPoint start = projection.project(radar.center());
        MonitorProjection.DisplayPoint end = projection.project(radar.ownedLockedTargetPos());
        int size = monitor.getSize();
        float x1 = 1f - size / 2f + start.xOffset() * size;
        float z1 = 1f - size / 2f + start.zOffset() * size;
        float x2 = 1f - size / 2f + end.xOffset() * size;
        float z2 = 1f - size / 2f + end.zOffset() * size;
        float dx = x2 - x1;
        float dz = z2 - z1;
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length <= 0.001f) {
            return;
        }

        float halfWidth = Math.max(0.015f, size * 0.01f);
        float nx = -dz / length * halfWidth;
        float nz = dx / length * halfWidth;
        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        Matrix4f m = ms.last().pose();
        renderSolidVertices(getSolidBuffer(bufferSource), m, color, 0.9f, DEPTH_LOCK_LINE,
                x1 + nx, z1 + nz,
                x2 + nx, z2 + nz,
                x2 - nx, z2 - nz,
                x1 - nx, z1 - nz);
    }

    private void renderPlaneRadarArc(MonitorBlockEntity.RadarDisplayInfo radar, IRadar liveRadar, MonitorBlockEntity controller,
                                     MonitorProjection projection,
                                     PoseStack ms, MultiBufferSource bufferSource, MonitorProjection.DisplayPoint center,
                                     float scale, float partialTicks) {
        int size = controller.getSize();
        float radius = size * scale * 0.5f * PLANE_SWEEP_RADIUS_SCALE;
        if (radius <= 0.001f) {
            return;
        }

        float centerX = 1f - size / 2f + center.xOffset() * size;
        float centerZ = 1f - size / 2f + center.zOffset() * size;
        float baseAngle = getPlaneScreenAngle(radar, liveRadar, projection, partialTicks);
        float fov = getRadarFov(radar, liveRadar);
        int segments = Math.max(4, (int)Math.ceil(fov / 8.0f));
        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        float r = color.getRedAsFloat();
        float g = color.getGreenAsFloat();
        float b = color.getBlueAsFloat();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());
        Matrix4f m = ms.last().pose();
        Matrix3f n = ms.last().normal();

        float previousAngle = baseAngle - fov * 0.5f;
        float previousX = centerX + angleX(previousAngle) * radius;
        float previousZ = centerZ + angleZ(previousAngle) * radius;
        for (int i = 1; i <= segments; i++) {
            float angle = baseAngle - fov * 0.5f + fov * i / segments;
            float x = centerX + angleX(angle) * radius;
            float z = centerZ + angleZ(angle) * radius;
            renderLine(buffer, m, n, previousX, DEPTH_BACKGROUND, previousZ, x, DEPTH_BACKGROUND, z, r, g, b, ALPHA_BACKGROUND);
            previousX = x;
            previousZ = z;
        }
    }

    private void renderPlaneSweepConeBackground(MonitorBlockEntity.RadarDisplayInfo radar, IRadar liveRadar, MonitorBlockEntity controller,
                                                MonitorProjection projection,
                                                PoseStack ms, MultiBufferSource bufferSource, MonitorProjection.DisplayPoint center,
                                                float scale, float partialTicks) {
        VertexConsumer buffer = bufferSource.getBuffer(ModRenderTypes.polygonOffset(MonitorSprite.RADAR_SWEEP.getTexture()));
        Matrix4f m = ms.last().pose();
        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        float angleRad = -getPlaneScreenAngle(radar, liveRadar, projection, partialTicks) * (float)Math.PI / 180.0f;
        float cos = (float)Math.cos(angleRad);
        float sin = (float)Math.sin(angleRad);
        float centerX = 0.5f;
        float centerY = 0.5f;
        int size = controller.getSize();
        MonitorProjection.Quad quad = MonitorProjection.scaledQuad(center, size, scale);

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
        addSweepVertex(buffer, m, quad.minX(), DEPTH_BACKGROUND, quad.minZ(), r, g, b, ALPHA_BACKGROUND, u0, v0);
        addSweepVertex(buffer, m, quad.maxX(), DEPTH_BACKGROUND, quad.minZ(), r, g, b, ALPHA_BACKGROUND, u1, v1);
        addSweepVertex(buffer, m, quad.maxX(), DEPTH_BACKGROUND, quad.maxZ(), r, g, b, ALPHA_BACKGROUND, u2, v2);
        addSweepVertex(buffer, m, quad.minX(), DEPTH_BACKGROUND, quad.maxZ(), r, g, b, ALPHA_BACKGROUND, u3, v3);
    }

    private void renderPlaneSweepLine(MonitorBlockEntity.RadarDisplayInfo radar, IRadar liveRadar, MonitorBlockEntity controller,
                                      MonitorProjection projection,
                                      PoseStack ms, MultiBufferSource bufferSource, MonitorProjection.DisplayPoint center,
                                      float scale, float partialTicks) {
        int size = controller.getSize();
        float radius = size * scale * 0.5f * PLANE_SWEEP_RADIUS_SCALE;
        if (radius <= 0.001f) {
            return;
        }

        float centerX = 1f - size / 2f + center.xOffset() * size;
        float centerZ = 1f - size / 2f + center.zOffset() * size;
        float sweepAngle = getPlaneSweepAngle(radar, liveRadar, controller, projection, partialTicks);
        float endX = centerX + angleX(sweepAngle) * radius;
        float endZ = centerZ + angleZ(sweepAngle) * radius;
        renderSolidLine(ms.last().pose(), bufferSource, centerX, centerZ, endX, endZ,
                Math.max(0.015f, size * 0.01f), ALPHA_SWEEP, DEPTH_SWEEP);
    }

    private void renderSolidLine(Matrix4f m, MultiBufferSource bufferSource, float x1, float z1, float x2, float z2,
                                 float halfWidth, float alpha, float depth) {
        float dx = x2 - x1;
        float dz = z2 - z1;
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length <= 0.001f) {
            return;
        }

        float nx = -dz / length * halfWidth;
        float nz = dx / length * halfWidth;
        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        renderSolidVertices(getSolidBuffer(bufferSource), m, color, alpha, depth,
                x1 + nx, z1 + nz,
                x2 + nx, z2 + nz,
                x2 - nx, z2 - nz,
                x1 - nx, z1 - nz);
    }

    private float getPlaneSweepAngle(MonitorBlockEntity.RadarDisplayInfo radar, IRadar liveRadar,
                                     MonitorBlockEntity controller, MonitorProjection projection, float partialTicks) {
        float baseAngle = getPlaneScreenAngle(radar, liveRadar, projection, partialTicks);
        float fov = getRadarFov(radar, liveRadar);
        float t = 0f;
        if (controller.getLevel() != null) {
            t = ((controller.getLevel().getGameTime() % PLANE_SWEEP_CYCLE_TICKS) + partialTicks) / PLANE_SWEEP_CYCLE_TICKS;
        }
        float sweep = t < 0.5f ? t * 2.0f : (1.0f - t) * 2.0f;
        return baseAngle - fov * 0.5f + fov * sweep;
    }

    private float getPlaneScreenAngle(MonitorBlockEntity.RadarDisplayInfo radar, IRadar liveRadar,
                                      MonitorProjection projection, float partialTicks) {
        float globalAngle = getRenderGlobalAngle(radar, liveRadar, partialTicks);
        return projection.projectWorldAngle(globalAngle);
    }

    private float getRadarFov(MonitorBlockEntity.RadarDisplayInfo radar, IRadar liveRadar) {
        float fov = liveRadar != null ? liveRadar.getFovDegrees() : radar.fovDegrees();
        return Mth.clamp(Float.isFinite(fov) ? fov : 360.0f, 1.0f, 360.0f);
    }

    private static boolean isPlaneRadar(String radarType) {
        return "nonspinning".equals(radarType);
    }

    private static boolean isLockCapableRadar(String radarType) {
        return "sky".equals(radarType) || isPlaneRadar(radarType);
    }

    private static float normalizeDegrees(float degrees) {
        degrees %= 360.0f;
        if (degrees < 0.0f) {
            degrees += 360.0f;
        }
        return degrees;
    }

    private static float angleX(float degrees) {
        return (float)Math.sin(Math.toRadians(degrees));
    }

    private static float angleZ(float degrees) {
        return (float)-Math.cos(Math.toRadians(degrees));
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
