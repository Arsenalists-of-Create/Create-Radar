package com.happysg.radar.api.monitor.client;

import com.happysg.radar.api.monitor.MonitorRadarSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Objects;

/**
 * Public rendering context for an in-world radar monitor.
 * The pose stack is already transformed onto the monitor face.
 */
public record MonitorRadarWorldRenderContext(
        MonitorRadarSnapshot radar,
        ClientLevel level,
        BlockPos monitorPos,
        Direction monitorFacing,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int monitorSize,
        float centerXOffset,
        float centerZOffset,
        float displayScale,
        float partialTicks
) {

    public MonitorRadarWorldRenderContext {
        radar = Objects.requireNonNull(radar, "radar");
        level = Objects.requireNonNull(level, "level");
        monitorPos = Objects.requireNonNull(monitorPos, "monitorPos").immutable();
        monitorFacing = Objects.requireNonNull(monitorFacing, "monitorFacing");
        poseStack = Objects.requireNonNull(poseStack, "poseStack");
        bufferSource = Objects.requireNonNull(bufferSource, "bufferSource");
        monitorSize = Math.max(1, monitorSize);
        displayScale = Math.max(0.0F, displayScale);
    }
}