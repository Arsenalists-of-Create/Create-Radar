package com.happysg.radar.block.monitor;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import com.happysg.radar.block.controller.id.IDManager;
import com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.config.RadarConfig;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.UUID;

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

    private static final float ALPHA_BACKGROUND = 0.6f;
    private static final float ALPHA_GRID = 0.1f;
    private static final float ALPHA_SWEEP = 0.8f;
    private static final int TARGET_BG =512;
    // i treat 512px as the "design resolution" of the monitor ui
    private static final int TARGET_UI_PX = 900;
    private static final int GRID_MARGIN_PX = 21;

    // i store the current ui size in gui units, and a scale factor relative to the old 512 design
    private int uiSize;
    private float uiScale;

    private final BlockPos controllerPos;

    private int left;
    private int top;

    private String hoveredId;

    public MonitorScreen(BlockPos controllerPos) {
        super(Component.translatable(TITLE_KEY));
        this.controllerPos = controllerPos;
    }

    @Override
    protected void init() {
        super.init();
        recalcUiScale();
        left = (this.width - uiSize) / 2;
        top = (this.height - uiSize) / 2;
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

        uiSize = (int) Math.round(TARGET_UI_PX / s);

        int max = Math.min(this.width, this.height) - 20;
        uiSize = Mth.clamp(uiSize, 120, Math.max(120, max));

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

        MonitorProjection projection = MonitorProjection.create(monitor);
        updateHoverFromMouse(monitor, projection, mouseX, mouseY);

        renderGrid(gg, projection);
        for (MonitorBlockEntity.RadarDisplayInfo radarInfo : monitor.getRunningRadarInfos()) {
            MonitorProjection.DisplayPoint radarCenter = projection.project(radarInfo.center());
            float scale = projection.displayScale(radarInfo.range());
            renderBG(gg, MonitorSprite.RADAR_BG_FILLER, ALPHA_BACKGROUND, radarCenter, scale);
            renderBG(gg, MonitorSprite.RADAR_BG_CIRCLE, ALPHA_BACKGROUND, radarCenter, scale);
            IRadar liveRadar = resolveLiveRadar(monitor, radarInfo);
            renderSweep(gg, monitor, radarInfo, liveRadar, radarCenter, scale, partialTicks);
        }
        renderTracks(gg, monitor, projection);

        gg.drawCenteredString(font, Component.translatable(CLICK_HINT_KEY), width / 2, top + uiSize + 6, 0xA0A0A0);

        super.render(gg, mouseX, mouseY, partialTicks);
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
        float range = projection.halfSpan();

        float cellWorld = 50f;
        int halfCells = Mth.floor(range / cellWorld);
        halfCells = Mth.clamp(halfCells, 2, 24);

        int totalCells = halfCells * 2;

        int margin = Math.round(GRID_MARGIN_PX * uiScale);

        int gridLeft = left + margin;
        int gridTop = top + margin;
        int gridRight = left + uiSize - margin;
        int gridBottom = top + uiSize - margin;

        int gridSizePx = gridRight - gridLeft;
        float spacing = gridSizePx / (float) totalCells;

        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        int a = (int) (ALPHA_GRID * 255f) & 0xFF;
        int argb = (a << 24) | (color.getRGB() & 0xFFFFFF);

        for (int i = 0; i <= totalCells; i++) {
            int x = gridLeft + Math.round(i * spacing);
            gg.fill(x, gridTop, x + 1, gridBottom, argb);
        }
        for (int i = 0; i <= totalCells; i++) {
            int y = gridTop + Math.round(i * spacing);
            gg.fill(gridLeft, y, gridRight, y + 1, argb);
        }

        int cx = gridLeft + gridSizePx / 2;
        int cy = gridTop + gridSizePx / 2;

        gg.fill(cx, gridTop, cx + 1, gridBottom, (a << 24) | (color.getRGB() & 0xFFFFFF));
        gg.fill(gridLeft, cy, gridRight, cy + 1, (a << 24) | (color.getRGB() & 0xFFFFFF));
    }

    private void renderBG(GuiGraphics gg, MonitorSprite sprite, float alpha, MonitorProjection.DisplayPoint center, float scale) {
        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        int drawSize = Math.max(1, Math.round(uiSize * scale));
        int cx = left + Math.round((0.5f + center.xOffset()) * uiSize);
        int cy = top + Math.round((0.5f + center.zOffset()) * uiSize);
        int sx = cx - drawSize / 2;
        int sy = cy - drawSize / 2;

        RenderSystem.enableBlend();
        gg.setColor(color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), alpha);
        gg.blit(sprite.getTexture(), sx, sy, 0, 0, drawSize, drawSize, drawSize, drawSize);
        gg.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
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
                             MonitorProjection.DisplayPoint center, float scale, float partialTicks) {
        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        float a = getRenderGlobalAngle(radar, liveRadar, partialTicks);
        Direction monitorFacing = monitor.getBlockState().getValue(MonitorBlock.FACING);
        Direction radarFacing = Direction.NORTH;
        if (radarFacing == null) return;
        float facingOffset = radarFacingOffsetDeg(monitorFacing, radarFacing);
        float screenAngle = (a + facingOffset) % 360f;
        String radarType = liveRadar != null ? liveRadar.getRadarType() : radar.type();
        boolean renderRelative = liveRadar != null ? liveRadar.renderRelativeToMonitor() : radar.renderRelativeToMonitor();
        Direction liveDirection = liveRadar != null ? liveRadar.getradarDirection() : radar.direction();

        if (monitor.getController().getShip() == null && radarType.equals("spinning")) {
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

        } else if (monitor.getController().getShip() != null && radarType.equals("spinning")) { // spinning radar on a ship
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

        if (renderRelative && monitor.getController().getShip() != null && !radarType.equals("spinning")) {  // plane radar on a ship
            monitorFacing = monitor.getController().getBlockState().getValue(MonitorBlock.FACING);
            screenAngle = alignGlobalAngleToMonitor(monitorFacing, a);
        }

        if (renderRelative && monitor.getController().getShip() != null
                && radarType.equals("spinning")) {
            float shipYawDeg = (float) Math.toDegrees(getShipYawRad(monitor.getController().getShip()));
            screenAngle += -(shipYawDeg + 180f);
        }

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

            int spriteSize = Math.max(8, Math.round(256 * uiScale));
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
                renderLabel(gg, label, px, pz + Math.round(8 * uiScale), alpha,RadarConfig.client().monitorTextScale.getF());
            }
        }
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
        if (button != 0)
            return super.mouseClicked(mouseX, mouseY, button);

        MonitorBlockEntity monitor = getController();
        if (monitor == null)
            return super.mouseClicked(mouseX, mouseY, button);

        if (!isMouseOverRadar((int) mouseX, (int) mouseY))
            return super.mouseClicked(mouseX, mouseY, button);

        if (hoveredId != null) {
            monitor.selectedEntity = hoveredId;
            MonitorSelectionPacket.send(controllerPos, hoveredId);
            return true;
        }else{
            monitor.selectedEntity = null;
            monitor.activetrack = null;
            MonitorSelectionPacket.send(controllerPos, null);
            return true;
        }
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
}
