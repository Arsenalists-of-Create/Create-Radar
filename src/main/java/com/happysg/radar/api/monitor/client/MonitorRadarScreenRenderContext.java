package com.happysg.radar.api.monitor.client;

import com.happysg.radar.api.monitor.MonitorRadarSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Objects;

/**
 * Public rendering context for the full-screen radar monitor GUI.
 */
public record MonitorRadarScreenRenderContext(
        MonitorRadarSnapshot radar,
        ClientLevel level,
        BlockPos monitorPos,
        Direction monitorFacing,
        GuiGraphics graphics,
        int left,
        int top,
        int uiSize,
        float centerXOffset,
        float centerZOffset,
        float displayScale,
        float viewRotationDegrees,
        float partialTicks
) {

    public MonitorRadarScreenRenderContext {
        radar = Objects.requireNonNull(radar, "radar");
        level = Objects.requireNonNull(level, "level");
        monitorPos = Objects.requireNonNull(monitorPos, "monitorPos").immutable();
        monitorFacing = Objects.requireNonNull(monitorFacing, "monitorFacing");
        graphics = Objects.requireNonNull(graphics, "graphics");
        uiSize = Math.max(1, uiSize);
        displayScale = Math.max(0.0F, displayScale);
    }

    /**
     * Radar center X coordinate in GUI pixels.
     */
    public int centerX() {
        return left + Math.round((0.5F + centerXOffset) * uiSize);
    }

    /**
     * Radar center Y coordinate in GUI pixels.
     */
    public int centerY() {
        return top + Math.round((0.5F + centerZOffset) * uiSize);
    }

    /**
     * Radar display diameter in GUI pixels.
     */
    public int displaySize() {
        return Math.max(1, Math.round(uiSize * displayScale));
    }
}