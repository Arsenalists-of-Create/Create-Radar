package com.happysg.radar.block.monitor;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import com.happysg.radar.block.controller.id.IDManager;
import com.happysg.radar.block.controller.id.IDRecord;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.PhysicsHandler;
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
import org.lwjgl.opengl.GL11;

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
    private static final int TARGET_BG = 128;
    // Native design resolution is 128x128
    private static final int TARGET_UI_PX = 128;
    private static final float GRID_MARGIN_PX = 3.0f; // Adjusted for 128px scale

    // UI state for dynamic scaling and positioning
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

    private void recalcUiScale() {
        int max = Math.min(this.width, this.height) - 100;
        uiSize = max;
        uiScale = uiSize / (float) TARGET_UI_PX;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTicks) {
        super.render(gg, mouseX, mouseY, partialTicks);
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

        IRadar radar = monitor.getRadar().orElse(null);
        if (radar == null || !radar.isRunning()) {
            gg.drawCenteredString(font, Component.translatable(OFFLINE_KEY), width / 2, height / 2 - 4, 0xFFFFFF);
            super.render(gg, mouseX, mouseY, partialTicks);
            return;
        }

        updateHoverFromMouse(monitor, radar, mouseX, mouseY);

        // --- SHARP RENDERING START ---
        gg.pose().pushPose();
        gg.pose().translate(left, top, 0);
        gg.pose().scale(uiScale, uiScale, 1.0f);

        // Now we are in a 128x128 local space
        renderGrid(gg, monitor, monitor.radar);
        renderBG(gg, monitor, MonitorSprite.RADAR_BG_FILLER, ALPHA_BACKGROUND);
        renderBG(gg, monitor, MonitorSprite.RADAR_BG_CIRCLE, ALPHA_BACKGROUND);
        renderSweep(gg, monitor, radar, partialTicks);
        renderTracks(gg, monitor, radar);

        gg.pose().popPose();
        // --- SHARP RENDERING END ---

        gg.drawCenteredString(font, Component.translatable(CLICK_HINT_KEY), width / 2, top + uiSize + 6, 0xA0A0A0);
    }

    private void drawPanelBackground(GuiGraphics gg) {
        RenderSystem.enableBlend();
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

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

    private void renderGrid(GuiGraphics gg, MonitorBlockEntity monitor, IRadar radar) {
        float alpha = ALPHA_GRID;
        if (alpha <= 0) return;
        Color color = new Color(RadarConfig.client().groundRadarColor.get());

        float margin = GRID_MARGIN_PX;
        float size = 128 - margin * 2;
        
        float range = radar.getRange();
        float cellWorld = 50f;
        int halfCells = Mth.floor(range / cellWorld);
        halfCells = Mth.clamp(halfCells, 2, 24);
        int totalCells = halfCells * 2;
        
        float step = size / totalCells;

        for (int i = 0; i <= totalCells; i++) {
            float offset = margin + i * step;
            // horizontal line
            gg.fill((int) margin, (int) offset, (int) (128 - margin), (int) (offset + 1), color.getRGB() & 0x00FFFFFF | ((int) (alpha * 255) << 24));
            // vertical line
            gg.fill((int) offset, (int) margin, (int) (offset + 1), (int) (128 - margin), color.getRGB() & 0x00FFFFFF | ((int) (alpha * 255) << 24));
        }
        
        // crosshair
        gg.fill(64, (int) margin, 65, (int) (128 - margin), color.getRGB() & 0x00FFFFFF | ((int) (alpha * 255) << 24));
        gg.fill((int) margin, 64, (int) (128 - margin), 65, color.getRGB() & 0x00FFFFFF | ((int) (alpha * 255) << 24));
    }

    private void renderBG(GuiGraphics gg, MonitorBlockEntity monitor, MonitorSprite sprite, float alpha) {
        Color color = new Color(RadarConfig.client().groundRadarColor.get());

        RenderSystem.enableBlend();
        RenderSystem.setShaderTexture(0, sprite.getTexture());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        gg.setColor(color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), alpha);
        // We are already scaled, so draw at 128x128 native size
        gg.blit(sprite.getTexture(), 0, 0, 128, 128, 0, 0, 128, 128, 128, 128);
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

    private void renderSweep(GuiGraphics gg, MonitorBlockEntity monitor, IRadar radar, float partialTicks) {
        Color color = new Color(RadarConfig.client().groundRadarColor.get());
        float a = (radar.getGlobalAngle() + 360f) % 360f;
        Direction monitorFacing = monitor.getBlockState().getValue(MonitorBlock.FACING);
        Direction radarFacing = Direction.NORTH;
        if (radarFacing == null) return;
        float facingOffset = radarFacingOffsetDeg(monitorFacing, radarFacing);
        float screenAngle = (a + facingOffset) % 360f;

        if (monitor.getController().getShip() == null && radar.getRadarType().equals("spinning")) {
            monitorFacing = monitor.getBlockState().getValue(MonitorBlock.FACING);
            radarFacing = Direction.NORTH;
            if (radarFacing == null) return;
            MonitorRenderer.ConeDir2D cone = MonitorRenderer.ConeDir2D.NORTH;
            switch (cone) {
                case NORTH -> screenAngle = 0 + radar.getGlobalAngle();
                case DOWN -> screenAngle = 180 + radar.getGlobalAngle();
                case LEFT -> screenAngle = 90 + radar.getGlobalAngle();
                case RIGHT -> screenAngle = 270 + radar.getGlobalAngle();
                default -> screenAngle = 30;
            }

        } else if (monitor.getController().getShip() != null && radar.getRadarType().equals("spinning")) { // spinning radar on a ship
            // Calculate the current angle
            monitorFacing = monitor.getController().getBlockState().getValue(MonitorBlock.FACING);
            Vec3 facingVec = new Vec3(monitorFacing.getStepX(), monitorFacing.getStepY(), monitorFacing.getStepZ());
            Vec3 angleVec = PhysicsHandler.getWorldVecDirectionTransform(facingVec, monitor.getController());
            screenAngle = (float) Math.toDegrees(Math.atan2(angleVec.x, angleVec.z));
                screenAngle = screenAngle + radar.getGlobalAngle();
            if (monitorFacing == Direction.NORTH || monitorFacing == Direction.SOUTH) {
                screenAngle = (screenAngle + 180) % 360;
            }

            // Normalize to positive angles
            screenAngle = (screenAngle + 360 + 180) % 360;
        }

        if (radar.renderRelativeToMonitor() && monitor.getController().getShip() != null && !radar.getRadarType().equals("spinning")) {  // plane radar on a ship
            // Plane radar on ship - cone stays fixed, tracks rotate inside
            monitorFacing = monitor.getController().getBlockState().getValue(MonitorBlock.FACING);
            radarFacing = radar.getradarDirection();
            if (radarFacing == null) return;

            MonitorRenderer.ConeDir2D cone = MonitorRenderer.ConeDir2D.NORTH;
            switch (cone) {
                case NORTH -> screenAngle = 0;
                case DOWN -> screenAngle = 180;
                case LEFT -> screenAngle = 90;
                case RIGHT -> screenAngle = 270;
                default -> screenAngle = 30;
            }
        }

        if (radar.renderRelativeToMonitor() && monitor.getController().getShip() != null
                && radar.getRadarType().equals("spinning")) {
            float shipYawDeg = monitor.getController().getShipYawDeg();
            screenAngle += -(shipYawDeg + 180f);
        }

        int cx = left + uiSize / 2;
        int cy = top + uiSize / 2;

        RenderSystem.enableBlend();
        gg.setColor(color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), ALPHA_SWEEP);

        gg.pose().pushPose();
        // Since the whole screen is already translated and scaled to 128x128:
        // center is 64, 64
        gg.pose().translate(64, 64, 0);
        gg.pose().mulPose(Axis.ZP.rotationDegrees(-screenAngle));
        gg.pose().translate(-64, -64, 0);

        RenderSystem.setShaderTexture(0, MonitorSprite.RADAR_SWEEP.getTexture());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        gg.blit(MonitorSprite.RADAR_SWEEP.getTexture(), 0, 0, 128, 128, 0, 0, 128, 128, 128, 128);

        gg.pose().popPose();

        gg.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
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


    private void renderTracks(GuiGraphics gg, MonitorBlockEntity monitor, IRadar radar) {
        Collection<RadarTrack> tracks = monitor.getTracks();
        if (tracks == null || tracks.isEmpty())
            return;

        float range = radar.getRange();

        DetectionConfig filter = monitor.filter;

        for (RadarTrack track : tracks) {

            Vec3 radarPos = monitor.getRadarCenterPos();
            if (radarPos == null)
                continue;

            Vec3 rel = track.position().subtract(radarPos);
            if (radar.renderRelativeToMonitor() && monitor.getController().getShip() != null) {
                float shipYawDeg = monitor.getController().getShipYawDeg();
                rel = rotateAroundYDeg(rel, -(shipYawDeg + 180f));
            }

            float xOff = calculateTrackOffset(rel, monitor.getBlockState().getValue(MonitorBlock.FACING), range, true);
            float zOff = calculateTrackOffset(rel, monitor.getBlockState().getValue(MonitorBlock.FACING), range, false);

            if (Math.abs(xOff) > 0.5f || Math.abs(zOff) > 0.5f)
                continue;

            xOff *= TRACK_POSITION_SCALE;
            zOff *= TRACK_POSITION_SCALE;

            // Coordinates are in 128x128 space
            int px = (int) ((0.5f + xOff) * 128);
            int pz = (int) ((0.5f + zOff) * 128);

            long currentTime = monitor.getLevel().getGameTime();
            float age = currentTime - track.scannedTime();
            float fadeTime = 100f;
            float fade = Mth.clamp(age / fadeTime, 0f, 1f);
            float alpha = 1f - fade;
            if (alpha <= 0.02f)
                continue;

            Color c = filter.getColor(track);

            // Sprite size in 128px local space
            int iconSizeInLocal = track.trackCategory() == TrackCategory.AERONAUTICS ? 128 : 64;
            int sx = px - iconSizeInLocal / 2;
            int sy = pz - iconSizeInLocal / 2;

            RenderSystem.enableBlend();
            RenderSystem.setShaderTexture(0, track.getSprite().getTexture());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            
            gg.setColor(c.getRedAsFloat(), c.getGreenAsFloat(), c.getBlueAsFloat(), alpha);
            // Icons are 256x256, but we draw them small in local space
            gg.blit(track.getSprite().getTexture(), sx, sy, iconSizeInLocal, iconSizeInLocal, 0, 0, 256, 256, 256, 256);

            if (track.id().equals(hoveredId)) {
                RenderSystem.setShaderTexture(0, MonitorSprite.TARGET_HOVERED.getTexture());
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                gg.setColor(1f, 1f, 0f, alpha);
                gg.blit(MonitorSprite.TARGET_HOVERED.getTexture(), sx, sy, iconSizeInLocal, iconSizeInLocal, 0, 0, 128, 128, 128, 128);
            }
            if (track.id().equals(monitor.selectedEntity)) {
                RenderSystem.setShaderTexture(0, MonitorSprite.TARGET_SELECTED.getTexture());
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                gg.setColor(1f, 0f, 0f, alpha);
                gg.blit(MonitorSprite.TARGET_SELECTED.getTexture(), sx, sy, iconSizeInLocal, iconSizeInLocal, 0, 0, 128, 128, 128, 128);
            }

            gg.setColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();

            String label = getLabelForTrack(track, monitor);
            if (label != null && !label.isBlank()) {
                // Adjust label position for local 128px space
                float textScale = RadarConfig.client().monitorTextScale.getF() * 2.0f;
                renderLabel(gg, label, px, pz + 10, alpha, textScale);
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

    private void updateHoverFromMouse(MonitorBlockEntity monitor, IRadar radar, int mouseX, int mouseY) {
        if (mouseX < left || mouseX >= left + uiSize || mouseY < top || mouseY >= top + uiSize) {
            hoveredId = null;
            return;
        }

        Vec3 radarPos = monitor.getRadarCenterPos();
        if (radarPos == null) {
            hoveredId = null;
            return;
        }

        float range = radar.getRange();
        var facing = monitor.getBlockState().getValue(MonitorBlock.FACING);

        float pickRadius = (32 * uiScale) * 0.75f;
        float bestDist2 = pickRadius * pickRadius;

        String bestId = null;

        for (RadarTrack track : monitor.cachedTracks) {
            Vec3 rel = track.position().subtract(radarPos);
            if (radar.renderRelativeToMonitor() && monitor.getController().getShip() != null) {
                float shipYawDeg = monitor.getController().getShipYawDeg();
                rel = rotateAroundYDeg(rel, -(shipYawDeg + 180f));
            }

            float xOff = calculateTrackOffset(rel, facing, range, true);
            float zOff = calculateTrackOffset(rel, facing, range, false);

            if (Math.abs(xOff) > 0.5f || Math.abs(zOff) > 0.5f)
                continue;

            xOff *= TRACK_POSITION_SCALE;
            zOff *= TRACK_POSITION_SCALE;

            int px = (int) (left + (0.5f + xOff) * uiSize);
            int py = (int) (top + (0.5f + zOff) * uiSize);

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

        if ("VS2:ship".equals(track.entityType())) {
            try {
                long shipId = Long.parseLong(track.id());
                IDRecord rec = IDManager.getIDRecordByShipId(shipId);
                if (rec != null && rec.name() != null && !rec.name().isBlank())
                    return rec.name();
            } catch (NumberFormatException ignored) {
                return null;
            }
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