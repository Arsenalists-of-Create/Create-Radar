package com.happysg.radar.block.monitor;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import com.happysg.radar.block.controller.id.IDManager;
import com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.skyradar.SkyRadarBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.compat.vs2.SableUtils;
import com.happysg.radar.config.RadarConfig;

import com.happysg.radar.utils.screenelements.MonitorButton;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.createmod.catnip.theme.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import dev.ryanhcode.sable.companion.SubLevelAccess;
import java.util.Collection;
import java.util.UUID;
import org.lwjgl.glfw.GLFW;

/**
 * A UI screen version of the MonitorRenderer. Draws the radar in 2D and lets the player hover/click tracks.
 */
public class MonitorScreen extends Screen {

    private static final float TRACK_POSITION_SCALE = 0.75f;
    private static final String MONITOR_I18N_PREFIX = CreateRadar.MODID + ".monitor.";
    private static final String NO_MONITOR_KEY = MONITOR_I18N_PREFIX + "no_monitor";
    private static final String NOT_LINKED_CONTROLLER_KEY = MONITOR_I18N_PREFIX + "not_linked_controller";
    private static final String OFFLINE_KEY = MONITOR_I18N_PREFIX + "offline";
    private static final String CLICK_HINT_KEY = MONITOR_I18N_PREFIX + "click_hint";
    private static final String TITLE_KEY = MONITOR_I18N_PREFIX + "title";
    private static final String PRESET_SAVED_KEY = MONITOR_I18N_PREFIX + "preset_saved";
    private static final String PRESET_LOADED_KEY = MONITOR_I18N_PREFIX + "preset_loaded";
    private static final String PRESET_EMPTY_KEY = MONITOR_I18N_PREFIX + "preset_empty";

    private static final float ALPHA_BACKGROUND = 0.6f;
    private static final float ALPHA_GRID = 0.1f;
    private static final float ALPHA_SWEEP = 0.8f;
    private static final int PLANE_SWEEP_CYCLE_TICKS = 20;
    private static final float PLANE_SWEEP_RADIUS_SCALE = 0.81f;
    private static final int TARGET_BG =512;
    // i treat 512px as the "design resolution" of the monitor ui
    private static final int TARGET_UI_PX = 900;
    private static final int GRID_MARGIN_PX = 21;
    private static final int DRAG_THRESHOLD_PX = 3;
    private static final float ROTATION_DEGREES_PER_PIXEL = 0.35f;
    private static final float ZOOM_STEP = 0.85f;
    private static final float MIN_HALF_SPAN = 8f;
    private static final float MAX_AUTOFIT_MULTIPLIER = 8f;
    private static final float MIN_MAX_HALF_SPAN = 1024f;
    private static final long PRESET_STATUS_MS = 2000L;
    private static final int TOOLBAR_GAP_PX = 5;
    private static final int TOOLBAR_BUTTON_WIDTH_PX = 72;
    private static final int TOOLBAR_BUTTON_HEIGHT_PX = 22;
    private static final int TOOLBAR_BUTTON_SPACING_PX = 6;

    private int toolbarLeft;
    private int toolbarWidth;

    // i store the current ui size in gui units, and a scale factor relative to the old 512 design
    private int uiSize;
    private float uiScale;

    private final BlockPos controllerPos;

    private int left;
    private int top;

    private String hoveredId;
    private MonitorProjection.View manualView;
    private boolean loadedManualView;
    private boolean pendingMonitorClick;
    private boolean draggingMonitor;
    private boolean dragStartedOnTrack;
    private boolean dragViewDirty;
    private int activeDragButton = -1;
    private double pressMouseX;
    private double pressMouseY;
    private Component presetStatus;
    private long presetStatusUntilMs;

    public MonitorScreen(BlockPos controllerPos) {
        super(Component.translatable(TITLE_KEY));
        this.controllerPos = controllerPos;
    }

    @Override
    protected void init() {
        super.init();

        recalcUiScale();
        layoutUi();
        addToolbarButtons();
    }

    @Override
    public void resize(Minecraft mc, int w, int h) {
        super.resize(mc, w, h);
        recalcUiScale();
        left = (this.width - uiSize) / 2;
        top = (this.height - uiSize) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics gg, int mouseX, int mouseY, float partialTicks) {
        // Keep the world visible behind the radar GUI without Minecraft's screen blur.
    }

    private void recalcUiScale() {
        Minecraft mc = Minecraft.getInstance();

        double s = mc.getWindow().getGuiScale();
        if (s <= 0) s = 1;

        int desiredSize = (int) Math.round(TARGET_UI_PX / s);

        int maxByHeight = this.height - 20;

        // Reserve room for the right-side toolbar.
        int toolbarDesignWidth = TOOLBAR_GAP_PX + TOOLBAR_BUTTON_WIDTH_PX;
        int maxByWidth = Math.round(
                (this.width - 20) * 512f / (512f + toolbarDesignWidth)
        );

        int maxSize = Math.min(maxByHeight, maxByWidth);

        uiSize = Mth.clamp(desiredSize, 120, Math.max(120, maxSize));
        uiScale = uiSize / 512f;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTicks) {
        drawPanelBackground(gg);

        MonitorBlockEntity monitor = getController();
        if (monitor == null) {
            gg.drawCenteredString(font, Component.translatable(NO_MONITOR_KEY), width / 2, height / 2 - 4, 0xFFFFFF);
            super.render(gg, mouseX, mouseY, partialTicks);
            return;
        }

        if (!monitor.isLinked() || !monitor.isController()) {
            gg.drawCenteredString(font, Component.translatable(NOT_LINKED_CONTROLLER_KEY), width / 2, height / 2 - 4, 0xFFFFFF);
            super.render(gg, mouseX, mouseY, partialTicks);
            return;
        }

        if (monitor.getRunningRadarInfos().isEmpty()) {
            gg.drawCenteredString(font, Component.translatable(OFFLINE_KEY), width / 2, height / 2 - 4, 0xFFFFFF);
            super.render(gg, mouseX, mouseY, partialTicks);
            return;
        }

        MonitorProjection projection = currentProjection(monitor);
        updateHoverFromMouse(monitor, projection, mouseX, mouseY);

        int clipMargin = Math.round(GRID_MARGIN_PX * uiScale);
        gg.enableScissor(left + clipMargin, top + clipMargin, left + uiSize - clipMargin, top + uiSize - clipMargin);
        try {
            renderGrid(gg, projection);
            for (MonitorBlockEntity.RadarDisplayInfo radarInfo : monitor.getRunningRadarInfos()) {
                MonitorProjection.DisplayPoint radarCenter = projection.project(radarInfo.center());
                float scale = projection.displayScale(radarInfo.range());
                IRadar liveRadar = resolveLiveRadar(monitor, radarInfo);
                if (isPlaneRadar(liveRadar != null ? liveRadar.getRadarType() : radarInfo.type())) {
                    renderPlaneSweepConeBackground(gg, monitor, radarInfo, liveRadar, projection, radarCenter, scale, partialTicks);
                    renderPlaneRadarArc(gg, monitor, radarInfo, liveRadar, projection, radarCenter, scale, partialTicks);
                } else {
                    //renderBG(gg, MonitorSprite.RADAR_BG_FILLER, ALPHA_BACKGROUND, radarCenter, scale);
                    renderBG(gg, MonitorSprite.RADAR_BG_CIRCLE, ALPHA_BACKGROUND, radarCenter, scale, projection.view().rotationDeg());
                }
                renderOwnedLockLine(gg, monitor, projection, radarInfo);
                renderSweep(gg, monitor, radarInfo, liveRadar, projection, radarCenter, scale, partialTicks);
            }
            renderTracks(gg, monitor, projection);
        } finally {
            gg.disableScissor();
        }

        gg.drawCenteredString(font, Component.translatable(CLICK_HINT_KEY), width / 2, top + uiSize + 6, 0xA0A0A0);
        renderPresetStatus(gg);

        super.render(gg, mouseX, mouseY, partialTicks);
    }

    private void renderPresetStatus(GuiGraphics gg) {
        if (presetStatus == null || System.currentTimeMillis() > presetStatusUntilMs)
            return;

        gg.drawCenteredString(font, presetStatus, width / 2, top + uiSize + 18, 0xD0D0D0);
    }

    private void drawPanelBackground(GuiGraphics gg) {
        RenderSystem.enableBlend();

        // i draw the background using the same uiSize the rest of the screen uses
        gg.blit(
                CreateRadar.asResource("textures/gui/monitor_gui.png"),
                left,
                top,
                0, 0,
                uiSize,   // destination width
                uiSize,   // destination height
                uiSize,uiSize  // actual texture size in pixels
        );

        RenderSystem.disableBlend();
    }

    private void renderGrid(GuiGraphics gg, MonitorProjection projection) {
        MonitorProjection.View view = projection.view();
        float range = projection.halfSpan() / MonitorProjection.FIT_SCALE * 1.5f;

        float cellWorld = 50f;
        while ((range * 2f) / cellWorld > 48f) {
            cellWorld *= 2f;
        }
        int margin = Math.round(GRID_MARGIN_PX * uiScale);

        int gridLeft = left + margin;
        int gridTop = top + margin;
        int gridRight = left + uiSize - margin;
        int gridBottom = top + uiSize - margin;

        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        int a = (int) (ALPHA_GRID * 255f) & 0xFF;
        int argb = (a << 24) | (color.getRGB() & 0xFFFFFF);

        double minX = Math.floor((view.centerX() - range) / cellWorld) * cellWorld;
        double maxX = Math.ceil((view.centerX() + range) / cellWorld) * cellWorld;
        double minZ = Math.floor((view.centerZ() - range) / cellWorld) * cellWorld;
        double maxZ = Math.ceil((view.centerZ() + range) / cellWorld) * cellWorld;
        RenderSystem.enableBlend();
        for (double x = minX; x <= maxX; x += cellWorld) {
            drawProjectedGridLine(gg,
                    projection.projectFramePosition(x, minZ),
                    projection.projectFramePosition(x, maxZ),
                    gridLeft, gridTop, gridRight, gridBottom, argb);
        }
        for (double z = minZ; z <= maxZ; z += cellWorld) {
            drawProjectedGridLine(gg,
                    projection.projectFramePosition(minX, z),
                    projection.projectFramePosition(maxX, z),
                    gridLeft, gridTop, gridRight, gridBottom, argb);
        }
        RenderSystem.disableBlend();
    }

    private void drawProjectedGridLine(GuiGraphics gg, MonitorProjection.DisplayPoint start, MonitorProjection.DisplayPoint end,
                                       int gridLeft, int gridTop, int gridRight, int gridBottom, int argb) {
        float x1 = left + (0.5f + start.xOffset()) * uiSize;
        float y1 = top + (0.5f + start.zOffset()) * uiSize;
        float x2 = left + (0.5f + end.xOffset()) * uiSize;
        float y2 = top + (0.5f + end.zOffset()) * uiSize;

        if ((x1 < gridLeft && x2 < gridLeft) || (x1 > gridRight && x2 > gridRight)
                || (y1 < gridTop && y2 < gridTop) || (y1 > gridBottom && y2 > gridBottom)) {
            return;
        }

        drawSolidLine(gg, x1, y1, x2, y2, 1, argb);
    }

    private void renderBG(GuiGraphics gg, MonitorSprite sprite, float alpha, MonitorProjection.DisplayPoint center, float scale) {
        renderBG(gg, sprite, alpha, center, scale, 0f);
    }

    private void renderBG(GuiGraphics gg, MonitorSprite sprite, float alpha, MonitorProjection.DisplayPoint center, float scale,
                          float rotationDeg) {
        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        int drawSize = Math.max(1, Math.round(uiSize * scale));
        int cx = left + Math.round((0.5f + center.xOffset()) * uiSize);
        int cy = top + Math.round((0.5f + center.zOffset()) * uiSize);
        int sx = cx - drawSize / 2;
        int sy = cy - drawSize / 2;

        RenderSystem.enableBlend();
        gg.setColor(color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), alpha);
        gg.pose().pushPose();
        gg.pose().translate(cx, cy, 0);
        gg.pose().mulPose(Axis.ZP.rotationDegrees(rotationDeg));
        gg.pose().translate(-cx, -cy, 0);
        gg.blit(sprite.getTexture(), sx, sy, 0, 0, drawSize, drawSize, drawSize, drawSize);
        gg.pose().popPose();
        gg.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private MonitorProjection currentProjection(MonitorBlockEntity monitor) {
        ensureManualViewLoaded();
        return MonitorProjection.create(monitor, manualView);
    }

    private void ensureManualViewLoaded() {
        if (loadedManualView)
            return;

        manualView = MonitorViewStore.get(Minecraft.getInstance(), controllerPos).orElse(null);
        loadedManualView = true;
    }

    private void setManualView(MonitorProjection.View view, boolean saveNow) {
        manualView = view;
        loadedManualView = true;
        if (saveNow) {
            MonitorViewStore.set(Minecraft.getInstance(), controllerPos, view);
            dragViewDirty = false;
        } else {
            dragViewDirty = true;
        }
    }

    public void resetMonitorViewToAutofit() {
        manualView = null;
        loadedManualView = true;
        dragViewDirty = false;
        MonitorViewStore.clear(Minecraft.getInstance(), controllerPos);
    }

    public void centerOnMonitorAtDefaultZoom() {
        MonitorBlockEntity monitor = getController();
        if (monitor == null)
            return;

        MonitorProjection autofitProjection = MonitorProjection.create(monitor);
        Vec3 monitorCenter = monitorWorldCenter(monitor);
        setManualView(autofitProjection.viewCenteredOn(monitorCenter, autofitProjection.halfSpan()), true);
    }

    public boolean tryLockViewToMonitorSublevel() {
        MonitorBlockEntity monitor = getController();
        if (monitor == null || monitor.getLevel() == null || !Mods.SABLE.isLoaded()) {
            return false;
        }

        SubLevelAccess ship = monitor.getShip();
        if (ship == null || ship.getUniqueId() == null) {
            return false;
        }

        MonitorProjection projection = currentProjection(monitor);
        Vec3 monitorCenter = monitorWorldCenter(monitor);
        Vec3 frameCenter = PhysicsHandler.getShipVec(monitorCenter, monitor);
        MonitorProjection.View lockedView = new MonitorProjection.View(
                frameCenter.x,
                frameCenter.z,
                projection.halfSpan(),
                projection.view().rotationDeg(),
                true,
                ship.getUniqueId()
        );
        setManualView(lockedView, true);
        return true;
    }

    private Vec3 monitorWorldCenter(MonitorBlockEntity monitor) {
        if (monitor.getLevel() == null) {
            return Vec3.atCenterOf(monitor.getBlockPos());
        }
        return PhysicsHandler.getWorldVec(monitor.getLevel(), monitor.getBlockPos());
    }

    private float clampHalfSpan(MonitorBlockEntity monitor, float halfSpan) {
        float autofitHalfSpan = MonitorProjection.create(monitor).halfSpan();
        float maxHalfSpan = Math.max(MIN_MAX_HALF_SPAN, autofitHalfSpan * MAX_AUTOFIT_MULTIPLIER);
        return Mth.clamp(halfSpan, MIN_HALF_SPAN, maxHalfSpan);
    }

    private Vec3 rotateAroundYDeg(Vec3 v, float deg) {
        double rad = Math.toRadians(deg);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        // i rotate around world up so the 2D projection matches the sweep orientation
        double x = v.x * cos - v.z * sin;
        double z = v.x * sin + v.z * cos;
        return new Vec3(x, v.y, z);
    }

    private void renderSweep(GuiGraphics gg, MonitorBlockEntity monitor, MonitorBlockEntity.RadarDisplayInfo radar, IRadar liveRadar,
                             MonitorProjection projection, MonitorProjection.DisplayPoint center, float scale, float partialTicks) {
        String radarType = liveRadar != null ? liveRadar.getRadarType() : radar.type();
        if (isOwnedLock(radar, liveRadar)) {
            return;
        }
        if (isPlaneRadar(radarType)) {
            renderPlaneSweepLine(gg, monitor, radar, liveRadar, projection, center, scale, partialTicks);
            return;
        }

        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        float a = getRenderGlobalAngle(radar, liveRadar, partialTicks);
        Direction monitorFacing = monitor.getBlockState().getValue(MonitorBlock.FACING);
        Direction radarFacing = Direction.NORTH;
        if (radarFacing == null) return;
        float facingOffset = radarFacingOffsetDeg(monitorFacing, radarFacing);
        float screenAngle = (a + facingOffset) % 360f;
        boolean renderRelative = liveRadar != null ? liveRadar.renderRelativeToMonitor() : radar.renderRelativeToMonitor();
        Direction liveDirection = liveRadar != null ? liveRadar.getradarDirection() : radar.direction();
        boolean spinningLike = radarType.equals("spinning") || radarType.equals("sky");

        if (monitor.getController().getShip() == null && spinningLike) {
            monitorFacing = monitor.getBlockState().getValue(MonitorBlock.FACING);
            radarFacing = Direction.NORTH;
            if (radarFacing == null) return;
            MonitorRenderer.ConeDir2D cone = getConeDirectionOnMonitor(monitorFacing, radarFacing);
            switch (cone) {
                case NORTH -> screenAngle = 0 + a;
                case DOWN -> screenAngle = 180 + a;
                case LEFT -> screenAngle = 90 + a;
                case RIGHT -> screenAngle = 270 + a;
                default -> screenAngle = 30;
            }

        } else if (monitor.getController().getShip() != null && spinningLike) { // spinning radar on a ship
            // Calculate the current angle
            monitorFacing = monitor.getController().getBlockState().getValue(MonitorBlock.FACING);
            Vec3 facingVec = new Vec3(monitorFacing.getStepX(), monitorFacing.getStepY(), monitorFacing.getStepZ());
            Vec3 angleVec = PhysicsHandler.getWorldVecDirectionTransform(facingVec, monitor.getController());
            screenAngle = (float) Math.toDegrees(Math.atan2(angleVec.x, angleVec.z));
                screenAngle = screenAngle + a;
            if (monitorFacing == Direction.NORTH || monitorFacing == Direction.SOUTH) {
                screenAngle = (screenAngle + 180) % 360;
            }

            // Normalize to positive angles
            screenAngle = (screenAngle + 360 + 180) % 360;
        }

        if (renderRelative && monitor.getController().getShip() != null && !spinningLike) {  // plane radar on a ship
            monitorFacing = monitor.getController().getBlockState().getValue(MonitorBlock.FACING);
            screenAngle = alignGlobalAngleToMonitor(monitorFacing, a);
        }

        if (renderRelative && monitor.getController().getShip() != null
                && spinningLike) {
            float shipYawDeg = (float) Math.toDegrees(getShipYawRad(monitor.getController().getShip()));
            screenAngle += -(shipYawDeg + 180f);
        }
        screenAngle = normalizeDegrees(screenAngle - projection.view().rotationDeg());

        int drawSize = Math.max(1, Math.round(uiSize * scale));
        int cx = left + Math.round((0.5f + center.xOffset()) * uiSize);
        int cy = top + Math.round((0.5f + center.zOffset()) * uiSize);
        int sx = cx - drawSize / 2;
        int sy = cy - drawSize / 2;

        RenderSystem.enableBlend();
        gg.setColor(color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), ALPHA_SWEEP);

        gg.pose().pushPose();
        gg.pose().translate(cx, cy, 0);
        // negative because GUI rotation direction is inverted relative to typical math
        gg.pose().mulPose(Axis.ZP.rotationDegrees(-screenAngle));
        gg.pose().translate(-cx, -cy, 0);

        gg.blit(MonitorSprite.RADAR_SWEEP.getTexture(), sx, sy, 0, 0, drawSize, drawSize, drawSize, drawSize);

        gg.pose().popPose();

        gg.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private boolean isOwnedLock(MonitorBlockEntity.RadarDisplayInfo radar, IRadar liveRadar) {
        String radarType = liveRadar != null ? liveRadar.getRadarType() : radar.type();
        return isLockCapableRadar(radarType) && radar.ownedLockedTargetPos() != null;
    }

    private void renderOwnedLockLine(GuiGraphics gg, MonitorBlockEntity monitor, MonitorProjection projection,
                                     MonitorBlockEntity.RadarDisplayInfo radar) {
        if (!isLockCapableRadar(radar.type()) || radar.ownedLockedTargetPos() == null) {
            return;
        }

        MonitorProjection.DisplayPoint start = projection.project(radar.center());
        MonitorProjection.DisplayPoint end = projection.project(radar.ownedLockedTargetPos());
        int x1 = left + Math.round((0.5f + start.xOffset()) * uiSize);
        int y1 = top + Math.round((0.5f + start.zOffset()) * uiSize);
        int x2 = left + Math.round((0.5f + end.xOffset()) * uiSize);
        int y2 = top + Math.round((0.5f + end.zOffset()) * uiSize);
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length <= 1.0f) {
            return;
        }

        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        int alpha = 0xD0;
        int argb = (alpha << 24) | (color.getRGB() & 0xFFFFFF);
        float zoomScale = getLabelZoomScale(monitor, projection);
        int thickness = Math.max(2, Math.round(1 * uiScale * zoomScale));

        RenderSystem.enableBlend();
        gg.pose().pushPose();
        gg.pose().translate(x1, y1, 0);
        gg.pose().mulPose(Axis.ZP.rotationDegrees((float) Math.toDegrees(Math.atan2(dy, dx))));
        gg.fill(0, -thickness / 2, Math.round(length), Math.max(1, thickness / 2 + 1), argb);
        gg.pose().popPose();
        RenderSystem.disableBlend();
    }

    private void renderPlaneRadarArc(GuiGraphics gg, MonitorBlockEntity monitor, MonitorBlockEntity.RadarDisplayInfo radar,
                                     IRadar liveRadar, MonitorProjection projection, MonitorProjection.DisplayPoint center,
                                     float scale, float partialTicks) {
        float radius = uiSize * scale * 0.5f * PLANE_SWEEP_RADIUS_SCALE;
        if (radius <= 0.5f) {
            return;
        }

        int cx = left + Math.round((0.5f + center.xOffset()) * uiSize);
        int cy = top + Math.round((0.5f + center.zOffset()) * uiSize);
        float baseAngle = getRotatedPlaneScreenAngle(radar, liveRadar, monitor, projection, partialTicks);
        float fov = getRadarFov(radar, liveRadar);
        int segments = Math.max(4, (int)Math.ceil(fov / 8.0f));
        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        int alpha = Mth.clamp((int)(ALPHA_BACKGROUND * 255.0f), 0, 255);
        int argb = (alpha << 24) | (color.getRGB() & 0xFFFFFF);
        int thickness = Math.max(1, Math.round(1.0f * uiScale));

        float previousAngle = baseAngle - fov * 0.5f;
        float previousX = cx + angleX(previousAngle) * radius;
        float previousY = cy + angleY(previousAngle) * radius;
        RenderSystem.enableBlend();
        for (int i = 1; i <= segments; i++) {
            float angle = baseAngle - fov * 0.5f + fov * i / segments;
            float x = cx + angleX(angle) * radius;
            float y = cy + angleY(angle) * radius;
            drawSolidLine(gg, previousX, previousY, x, y, thickness, argb);
            previousX = x;
            previousY = y;
        }
        RenderSystem.disableBlend();
    }

    private void renderPlaneSweepConeBackground(GuiGraphics gg, MonitorBlockEntity monitor, MonitorBlockEntity.RadarDisplayInfo radar,
                                                IRadar liveRadar, MonitorProjection projection, MonitorProjection.DisplayPoint center,
        float scale, float partialTicks) {
        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        float screenAngle = getTextureRotatedPlaneScreenAngle(radar, liveRadar, monitor, projection, partialTicks);
        int drawSize = Math.max(1, Math.round(uiSize * scale));
        int cx = left + Math.round((0.5f + center.xOffset()) * uiSize);
        int cy = top + Math.round((0.5f + center.zOffset()) * uiSize);
        int sx = cx - drawSize / 2;
        int sy = cy - drawSize / 2;

        RenderSystem.enableBlend();
        gg.setColor(color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), ALPHA_BACKGROUND);
        gg.pose().pushPose();
        gg.pose().translate(cx, cy, 0);
        gg.pose().mulPose(Axis.ZP.rotationDegrees(-screenAngle));
        gg.pose().translate(-cx, -cy, 0);
        gg.blit(MonitorSprite.RADAR_SWEEP.getTexture(), sx, sy, 0, 0, drawSize, drawSize, drawSize, drawSize);
        gg.pose().popPose();
        gg.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private void renderPlaneSweepLine(GuiGraphics gg, MonitorBlockEntity monitor, MonitorBlockEntity.RadarDisplayInfo radar,
                                      IRadar liveRadar, MonitorProjection projection, MonitorProjection.DisplayPoint center,
                                      float scale, float partialTicks) {
        float radius = uiSize * scale * 0.5f * PLANE_SWEEP_RADIUS_SCALE;
        if (radius <= 0.5f) {
            return;
        }

        float cx = left + (0.5f + center.xOffset()) * uiSize;
        float cy = top + (0.5f + center.zOffset()) * uiSize;
        float sweepAngle = getRotatedPlaneSweepAngle(radar, liveRadar, monitor, projection, partialTicks);
        float x2 = cx + angleX(sweepAngle) * radius;
        float y2 = cy + angleY(sweepAngle) * radius;
        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        int alpha = Mth.clamp((int)(ALPHA_SWEEP * 255.0f), 0, 255);
        int argb = (alpha << 24) | (color.getRGB() & 0xFFFFFF);
        int thickness = Math.max(2, Math.round(1.0f * uiScale * getLabelZoomScale(monitor, currentProjection(monitor))));

        RenderSystem.enableBlend();
        drawSolidLine(gg, cx, cy, x2, y2, thickness, argb);
        RenderSystem.disableBlend();
    }

    private void drawSolidLine(GuiGraphics gg, float x1, float y1, float x2, float y2, int thickness, int argb) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length <= 0.5f) {
            return;
        }

        gg.pose().pushPose();
        gg.pose().translate(x1, y1, 0);
        gg.pose().mulPose(Axis.ZP.rotationDegrees((float)Math.toDegrees(Math.atan2(dy, dx))));
        gg.fill(0, -thickness / 2, Math.round(length), Math.max(1, thickness / 2 + 1), argb);
        gg.pose().popPose();
    }

    private float getPlaneSweepAngle(MonitorBlockEntity.RadarDisplayInfo radar, IRadar liveRadar,
                                     MonitorBlockEntity monitor, float partialTicks) {
        float baseAngle = getPlaneScreenAngle(radar, liveRadar, monitor, partialTicks);
        float fov = getRadarFov(radar, liveRadar);
        float t = 0f;
        if (monitor.getLevel() != null) {
            t = ((monitor.getLevel().getGameTime() % PLANE_SWEEP_CYCLE_TICKS) + partialTicks) / PLANE_SWEEP_CYCLE_TICKS;
        }
        float sweep = t < 0.5f ? t * 2.0f : (1.0f - t) * 2.0f;
        return baseAngle - fov * 0.5f + fov * sweep;
    }

    private float getRotatedPlaneSweepAngle(MonitorBlockEntity.RadarDisplayInfo radar, IRadar liveRadar,
                                            MonitorBlockEntity monitor, MonitorProjection projection, float partialTicks) {
        return normalizeDegrees(getPlaneSweepAngle(radar, liveRadar, monitor, partialTicks) + projection.view().rotationDeg());
    }

    private float getRotatedPlaneScreenAngle(MonitorBlockEntity.RadarDisplayInfo radar, IRadar liveRadar,
                                             MonitorBlockEntity monitor, MonitorProjection projection, float partialTicks) {
        return normalizeDegrees(getPlaneScreenAngle(radar, liveRadar, monitor, partialTicks) + projection.view().rotationDeg());
    }

    private float getTextureRotatedPlaneScreenAngle(MonitorBlockEntity.RadarDisplayInfo radar, IRadar liveRadar,
                                                    MonitorBlockEntity monitor, MonitorProjection projection, float partialTicks) {
        return normalizeDegrees(getPlaneScreenAngle(radar, liveRadar, monitor, partialTicks) - projection.view().rotationDeg());
    }

    private float getPlaneScreenAngle(MonitorBlockEntity.RadarDisplayInfo radar, IRadar liveRadar,
                                      MonitorBlockEntity monitor, float partialTicks) {
        float globalAngle = getRenderGlobalAngle(radar, liveRadar, partialTicks);
        boolean renderRelative = liveRadar != null ? liveRadar.renderRelativeToMonitor() : radar.renderRelativeToMonitor();
        Direction monitorFacing = monitor.getController().getBlockState().getValue(MonitorBlock.FACING);
        if (renderRelative && monitor.getController().getShip() != null) {
            return normalizeDegrees(alignGlobalAngleToMonitor(monitorFacing, globalAngle));
        }

        MonitorRenderer.ConeDir2D cone = getConeDirectionOnMonitor(monitorFacing, Direction.NORTH);
        return normalizeDegrees(switch (cone) {
            case NORTH -> globalAngle;
            case DOWN -> 180 + globalAngle;
            case LEFT -> 90 + globalAngle;
            case RIGHT -> 270 + globalAngle;
            default -> globalAngle;
        });
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

    private static float angleY(float degrees) {
        return (float)-Math.cos(Math.toRadians(degrees));
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
        MonitorRenderer.ConeDir2D cone = getConeDirectionOnMonitor(monitorFacing, Direction.NORTH);
        return switch (cone) {
            case NORTH -> globalAngle;
            case DOWN -> 180 + globalAngle;
            case LEFT -> 90 + globalAngle;
            case RIGHT -> 270 + globalAngle;
            default -> globalAngle;
        };
    }

    public enum ConeDir2D {UP, RIGHT, DOWN, LEFT, NORTH}

    public MonitorRenderer.ConeDir2D getConeDirectionOnMonitor(Direction monitorFacing, Direction radarFacing) {
        int steps = cwStepsBetween(monitorFacing, radarFacing);
        return switch (steps) {
            case 0 -> MonitorRenderer.ConeDir2D.NORTH;
            case 1 -> MonitorRenderer.ConeDir2D.RIGHT;
            case 2 -> MonitorRenderer.ConeDir2D.DOWN;
            case 3 -> MonitorRenderer.ConeDir2D.LEFT;
            default -> MonitorRenderer.ConeDir2D.UP;
        };
    }

    private int cwStepsBetween(Direction from, Direction to) {
        int a = dirIndex(from);
        int b = dirIndex(to);
        int steps = b - a;
        steps %= 4;
        if (steps < 0) steps += 4;
        return steps;
    }

    private int dirIndex(Direction d) {
        return switch (d) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    public float radarFacingOffsetDeg(Direction monitorFacing, Direction radarFacing) {
        if (monitorFacing.getAxis().isVertical() || radarFacing.getAxis().isVertical())
            return 0f;

        int m = monitorFacing.get2DDataValue();
        int r = radarFacing.get2DDataValue();
        if (m == r) return -90;

        // i compute clockwise steps from monitor -> radar
        int stepsCW = (r - m) & 3;

        return (stepsCW * 90f + 90) % 360f;
    }

    private Vec3 rotateAroundY(Vec3 v, double angleRad) {
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        double x = v.x * cos - v.z * sin;
        double z = v.x * sin + v.z * cos;
        return new Vec3(x, v.y, z);
    }

    private double getShipYawRad(dev.ryanhcode.sable.companion.SubLevelAccess ship) {
        org.joml.Vector3d fwd = ship.logicalPose().transformNormal(new org.joml.Vector3d(0, 0, 1));
        return Math.atan2(fwd.x(), -fwd.z());
    }

    private void renderTracks(GuiGraphics gg, MonitorBlockEntity monitor, MonitorProjection projection) {
        Collection<RadarTrack> tracks = monitor.getTracks();
        if (tracks == null || tracks.isEmpty())
            return;

        DetectionConfig filter = monitor.filter;
        float labelZoomScale = getLabelZoomScale(monitor, projection);
        float trackZoomScale = labelZoomScale;

        for (RadarTrack track : tracks) {

            MonitorProjection.DisplayPoint point = projection.project(track.position());
            if (point.outside())
                continue;

            int px = (int) (left + (0.5f + point.xOffset()) * uiSize);
            int pz = (int) (top + (0.5f + point.zOffset()) * uiSize);

            long currentTime = monitor.getLevel().getGameTime();
            float age = currentTime - track.scannedTime();
            float fadeTime = 100f;
            float fade = Mth.clamp(age / fadeTime, 0f, 1f);
            float alpha = 1f - fade;
            if (alpha <= 0.02f)
                continue;

            Color c = filter.getColor(track);

            int spriteSize = Math.max(8, Math.round(256 * uiScale * trackZoomScale));
            int sx = px - spriteSize / 2;
            int sy = pz - spriteSize / 2;

            RenderSystem.enableBlend();
            gg.setColor(c.getRedAsFloat(), c.getGreenAsFloat(), c.getBlueAsFloat(), alpha);
            gg.blit(track.getSprite().getTexture(), sx, sy, 0, 0, spriteSize, spriteSize, spriteSize, spriteSize);

            if (track.id().equals(hoveredId)) {
                gg.setColor(1f, 1f, 0f, alpha);
                gg.blit(MonitorSprite.TARGET_HOVERED.getTexture(), sx, sy, 0, 0, spriteSize, spriteSize, spriteSize, spriteSize);
            }
            if (track.id().equals(monitor.selectedEntity)) {
                gg.setColor(1f, 0f, 0f, alpha);
                gg.blit(MonitorSprite.TARGET_SELECTED.getTexture(), sx, sy, 0, 0, spriteSize, spriteSize, spriteSize, spriteSize);
            }

            gg.setColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();

            String label = getLabelForTrack(track, monitor);
            if (label != null && !label.isBlank()) {
                renderLabel(gg, label, px, pz + Math.round(8 * uiScale), alpha,
                        RadarConfig.client().monitorTextScale.getF() * labelZoomScale);
            }
        }
    }

    private float getLabelZoomScale(MonitorBlockEntity monitor, MonitorProjection projection) {
        float autofitHalfSpan = MonitorProjection.create(monitor).halfSpan();
        if (autofitHalfSpan <= 0f)
            return 1f;

        return Mth.clamp(autofitHalfSpan / projection.halfSpan(), 0.35f, 4f);
    }

    private void renderLabel(GuiGraphics gg, String text, int x, int y, float alpha, float scale) {
        Font f = Minecraft.getInstance().font;
        int a = Mth.clamp((int) (alpha * 255f), 0, 255);
        int argb = (a << 24) | 0xFFFFFF;

        gg.pose().pushPose();

        // Scale around the text position
        gg.pose().translate(x, y, 0);
        gg.pose().scale(scale, scale, 1f);

        // Since we translated, draw at 0,0
        gg.drawCenteredString(f, text, 0, 0, argb);

        gg.pose().popPose();
    }

    private void updateHoverFromMouse(MonitorBlockEntity monitor, MonitorProjection projection, int mouseX, int mouseY) {
        if (mouseX < left || mouseX >= left + uiSize || mouseY < top || mouseY >= top + uiSize) {
            hoveredId = null;
            return;
        }

        int spriteSize = Math.max(6, Math.round(20 * uiScale));
        float pickRadius = spriteSize * 0.75f;
        float bestDist2 = pickRadius * pickRadius;

        String bestId = null;

        for (RadarTrack track : monitor.cachedTracks) {
            MonitorProjection.DisplayPoint point = projection.project(track.position());
            if (point.outside())
                continue;

            int px = (int) (left + (0.5f + point.xOffset()) * uiSize);
            int py = (int) (top + (0.5f + point.zOffset()) * uiSize);

            float dx = mouseX - px;
            float dy = mouseY - py;
            float d2 = dx * dx + dy * dy;

            if (d2 < bestDist2) {
                bestDist2 = d2;
                bestId = track.id();
            }
        }

        hoveredId = bestId;
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1)
            return super.mouseClicked(mouseX, mouseY, button);

        MonitorBlockEntity monitor = getController();
        if (monitor == null)
            return super.mouseClicked(mouseX, mouseY, button);

        if (!isMouseOverRadar((int) mouseX, (int) mouseY))
            return super.mouseClicked(mouseX, mouseY, button);

        MonitorProjection projection = currentProjection(monitor);
        updateHoverFromMouse(monitor, projection, (int) mouseX, (int) mouseY);
        pendingMonitorClick = true;
        draggingMonitor = false;
        dragViewDirty = false;
        activeDragButton = button;
        dragStartedOnTrack = button == 0 && hoveredId != null;
        pressMouseX = mouseX;
        pressMouseY = mouseY;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != activeDragButton || !pendingMonitorClick)
            return super.mouseReleased(mouseX, mouseY, button);

        MonitorBlockEntity monitor = getController();
        pendingMonitorClick = false;
        activeDragButton = -1;

        if (draggingMonitor) {
            draggingMonitor = false;
            if (dragViewDirty && manualView != null)
                MonitorViewStore.set(Minecraft.getInstance(), controllerPos, manualView);
            dragViewDirty = false;
            return true;
        }

        if (button != 0 || monitor == null || !isMouseOverRadar((int) mouseX, (int) mouseY))
            return true;

        MonitorProjection projection = currentProjection(monitor);
        updateHoverFromMouse(monitor, projection, (int) mouseX, (int) mouseY);
        if (hoveredId != null) {
            monitor.selectedEntity = hoveredId;
            MonitorSelectionPacket.send(controllerPos, hoveredId);
            return true;
        }

        monitor.selectedEntity = null;
        monitor.activetrack = null;
        MonitorSelectionPacket.send(controllerPos, null);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != activeDragButton || !pendingMonitorClick || dragStartedOnTrack)
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);

        MonitorBlockEntity monitor = getController();
        if (monitor == null)
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);

        double totalDx = mouseX - pressMouseX;
        double totalDy = mouseY - pressMouseY;
        if (!draggingMonitor && totalDx * totalDx + totalDy * totalDy < DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX)
            return true;

        draggingMonitor = true;
        MonitorProjection projection = currentProjection(monitor);
        if (button == 1) {
            setManualView(projection.rotateBy((float) dragX * ROTATION_DEGREES_PER_PIXEL), false);
        } else {
            setManualView(projection.panByDisplayDelta((float) (dragX / uiSize), (float) (dragY / uiSize)).unlocked(), false);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        MonitorBlockEntity monitor = getController();
        if (monitor == null || !isMouseOverRadar((int) mouseX, (int) mouseY))
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

        MonitorProjection projection = currentProjection(monitor);
        MonitorProjection.DisplayPoint anchor = projection.displayPointFromUi(mouseX, mouseY, left, top, uiSize);
        float factor = (float) Math.pow(ZOOM_STEP, scrollY);
        float halfSpan = clampHalfSpan(monitor, projection.halfSpan() * factor);
        setManualView(projection.zoomAround(anchor, halfSpan), true);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int slot = presetSlotForKey(keyCode);
        if (slot == 0)
            return super.keyPressed(keyCode, scanCode, modifiers);

        MonitorBlockEntity monitor = getController();
        if (monitor == null)
            return super.keyPressed(keyCode, scanCode, modifiers);

        Minecraft mc = Minecraft.getInstance();
        if (Screen.hasControlDown()) {
            MonitorProjection.View view = currentProjection(monitor).view();
            MonitorViewStore.setPreset(mc, controllerPos, slot, view);
            showPresetStatus(Component.translatable(PRESET_SAVED_KEY, slot));
            return true;
        }

        return MonitorViewStore.getPreset(mc, controllerPos, slot)
                .map(view -> {
                    setManualView(view, true);
                    showPresetStatus(Component.translatable(PRESET_LOADED_KEY, slot));
                    return true;
                })
                .orElseGet(() -> {
                    showPresetStatus(Component.translatable(PRESET_EMPTY_KEY, slot));
                    return true;
                });
    }

    private void showPresetStatus(Component status) {
        presetStatus = status;
        presetStatusUntilMs = System.currentTimeMillis() + PRESET_STATUS_MS;
    }

    private int presetSlotForKey(int keyCode) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9)
            return keyCode - GLFW.GLFW_KEY_1 + 1;
        if (keyCode == GLFW.GLFW_KEY_0)
            return 10;
        if (keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode <= GLFW.GLFW_KEY_KP_9)
            return keyCode - GLFW.GLFW_KEY_KP_1 + 1;
        if (keyCode == GLFW.GLFW_KEY_KP_0)
            return 10;
        return 0;
    }

    private boolean isMouseOverRadar(int mx, int my) {
        return mx >= left && mx < left + uiSize && my >= top && my < top + uiSize;
    }

    private MonitorBlockEntity getController() {
        if (Minecraft.getInstance().level == null)
            return null;

        if (!(Minecraft.getInstance().level.getBlockEntity(controllerPos) instanceof MonitorBlockEntity be))
            return null;

        if (be.isController())
            return be;

        BlockPos ctrl = be.getControllerPos();
        if (ctrl == null)
            return be;

        if (Minecraft.getInstance().level.getBlockEntity(ctrl) instanceof MonitorBlockEntity ctrlBe)
            return ctrlBe;

        return be;
    }

    private float calculateTrackOffset(Vec3 relativePos, Direction monitorFacing, float scale, boolean isXOffset) {
        float offset;

        if (isXOffset) {
            offset = monitorFacing.getAxis() == Direction.Axis.Z ?
                    getOffset(relativePos.x(), scale) : getOffset(relativePos.z(), scale);

            if (monitorFacing == Direction.NORTH || monitorFacing == Direction.EAST) {
                offset = -offset;
            }
        } else {
            offset = monitorFacing.getAxis() == Direction.Axis.Z ?
                    getOffset(relativePos.z(), scale) : getOffset(relativePos.x(), scale);

            if (monitorFacing == Direction.NORTH || monitorFacing == Direction.WEST) {
                offset = -offset;
            }
        }

        return offset;
    }

    private float getOffset(double coordinate, float scale) {
        return (float) (coordinate / scale) / 2f;
    }

    private String getLabelForTrack(RadarTrack track, MonitorBlockEntity mon) {
        if (mon.getLevel() == null) return null;

        if ("Sable:ship".equals(track.entityType())) {
            IDManager.IDRecord rec = IDManager.getIDRecordByShipId(UUID.fromString(track.id()));
            if (rec != null && rec.name() != null && !rec.name().isBlank())
                return rec.name();
        }

        if (track.trackCategory() == TrackCategory.PLAYER) {
            try {
                UUID uuid = UUID.fromString(track.getId());
                Player p = mon.getLevel().getPlayerByUUID(uuid);
                return p != null ? p.getName().getString() : null;
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        return null;
    }
    private void layoutUi() {
        int gap = scaled(TOOLBAR_GAP_PX);
        toolbarWidth = scaled(TOOLBAR_BUTTON_WIDTH_PX);

        // Center the radar and its right-side controls as one combined UI.
        int totalWidth = uiSize + gap + toolbarWidth;

        left = (this.width - totalWidth) / 2;
        top = (this.height - uiSize) / 2;

        toolbarLeft = left + uiSize + gap;
    }
    private Tooltip labeledTooltip(String titleKey, String descriptionKey) {
        return Tooltip.create(
                Component.empty()
                        .append(Component.translatable(titleKey)
                                .withStyle(ChatFormatting.GOLD))
                        .append(Component.literal("\n"))
                        .append(Component.translatable(descriptionKey)
                                .withStyle(ChatFormatting.GRAY))
        );
    }

    private int scaled(int designPixels) {
        return Math.max(1, Math.round(designPixels * uiScale));
    }

    private void addToolbarButtons() {
        int x = toolbarLeft;
        int y = top + scaled(435);
        int spacing = scaled(5);
        MonitorButton ship_lock = new MonitorButton(
                x,
                y,
                uiScale,
                Component.translatable(MONITOR_I18N_PREFIX + "button.ship_lock"),
                button -> tryLockViewToMonitorSublevel()
        );

        ship_lock.setTooltip(labeledTooltip(
                MONITOR_I18N_PREFIX + "button.ship_lock",
                MONITOR_I18N_PREFIX + "button.ship_lock.tooltip"
        ));
        y += ship_lock.getHeight() + spacing;

        MonitorButton autoFit = new MonitorButton(
                x,
                y,
                uiScale,
                Component.translatable(MONITOR_I18N_PREFIX + "button.autofit"),
                button -> resetMonitorViewToAutofit()
        );

        autoFit.setTooltip(labeledTooltip(
                MONITOR_I18N_PREFIX + "button.autofit",
                MONITOR_I18N_PREFIX + "button.autofit.tooltip"
        ));

        addRenderableWidget(autoFit);

        y += autoFit.getHeight() + spacing;

        MonitorButton center = new MonitorButton(
                x,
                y,
                uiScale,
                Component.translatable(MONITOR_I18N_PREFIX + "button.center"),
                button -> centerOnMonitorAtDefaultZoom()
        );
        center.setTooltip(labeledTooltip(
                MONITOR_I18N_PREFIX + "button.center",
                MONITOR_I18N_PREFIX + "button.center.tooltip"
        ));
        addRenderableWidget(center);


        MonitorBlockEntity monitor = getController();
        if(monitor==null) return;
        if(Mods.SABLE.isLoaded() && SableUtils.isBlockInShipyard(monitor.getLevel(),controllerPos)) {
            addRenderableWidget(ship_lock);
        }
    }
}
