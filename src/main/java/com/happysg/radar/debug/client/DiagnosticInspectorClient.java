package com.happysg.radar.debug.client;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.debug.BlockDiagnosticSnapshot;
import com.happysg.radar.debug.DiagnosticEntry;
import com.happysg.radar.debug.DiagnosticHealth;
import com.happysg.radar.debug.DiagnosticSection;
import com.happysg.radar.debug.DiagnosticSeverity;
import com.happysg.radar.networking.packets.InspectorRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@EventBusSubscriber(value = Dist.CLIENT, modid = CreateRadar.MODID)
public final class DiagnosticInspectorClient {
    private static final int UPDATE_INTERVAL_TICKS = 5;
    private static final int STALE_AFTER_TICKS = 30;
    private static final int MAX_RENDERED_LINES = 16;
    private static boolean enabled;
    private static int clientTick;
    private static int lastResponseTick;
    private static int lastRequestTick = Integer.MIN_VALUE;
    @Nullable
    private static BlockPos lastCrosshairPosition;
    @Nullable
    private static BlockDiagnosticSnapshot snapshot;

    private DiagnosticInspectorClient() {
    }

    public static void setEnabled(boolean newEnabled) {
        enabled = newEnabled;
        lastRequestTick = Integer.MIN_VALUE;
        lastCrosshairPosition = null;
        snapshot = null;
    }

    public static void acceptSnapshot(BlockDiagnosticSnapshot newSnapshot) {
        if (!enabled) return;
        snapshot = newSnapshot.status() == BlockDiagnosticSnapshot.Status.OK
                ? newSnapshot : null;
        lastResponseTick = clientTick;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        clientTick++;
        Minecraft minecraft = Minecraft.getInstance();
        if (!enabled || minecraft.player == null
                || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        BlockPos crosshair = crosshairCreateRadarBlock(minecraft);
        if (crosshair == null) {
            lastCrosshairPosition = null;
            snapshot = null;
            return;
        }
        boolean changed = !crosshair.equals(lastCrosshairPosition);
        if (changed || clientTick - lastRequestTick >= UPDATE_INTERVAL_TICKS) {
            if (changed) snapshot = null;
            lastCrosshairPosition = crosshair.immutable();
            lastRequestTick = clientTick;
            InspectorRequestPacket.send();
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!enabled || snapshot == null) return;
        renderPanel(event.getGuiGraphics(), snapshot,
                clientTick - lastResponseTick > STALE_AFTER_TICKS);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        setEnabled(false);
    }

    @Nullable
    private static BlockPos crosshairCreateRadarBlock(Minecraft minecraft) {
        HitResult hit = minecraft.player == null ? null
                : minecraft.player.pick(16.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos position = blockHit.getBlockPos();
        String namespace = BuiltInRegistries.BLOCK.getKey(
                minecraft.level.getBlockState(position).getBlock())
                .getNamespace();
        return CreateRadar.MODID.equals(namespace) ? position : null;
    }

    private static void renderPanel(GuiGraphics graphics,
                                    BlockDiagnosticSnapshot current,
                                    boolean stale) {
        Minecraft minecraft = Minecraft.getInstance();
        List<Line> lines = buildLines(current, stale);
        int maximumTextWidth = Math.max(112,
                minecraft.getWindow().getGuiScaledWidth() - 32);
        lines = lines.stream()
                .map(line -> new Line(clip(line.text(), maximumTextWidth),
                        line.color()))
                .toList();
        int width = 120;
        for (Line line : lines) {
            width = Math.max(width,
                    minecraft.font.width(line.text()) + 8);
        }
        int height = lines.size() * 10 + 8;
        int x = minecraft.getWindow().getGuiScaledWidth() - width - 8;
        int y = 8;
        graphics.fill(x, y, x + width, y + height, 0xD0101218);
        graphics.fill(x, y, x + 2, y + height,
                healthColor(current.health()));
        int lineY = y + 5;
        for (Line line : lines) {
            graphics.drawString(minecraft.font, line.text(), x + 6, lineY,
                    line.color(), false);
            lineY += 10;
        }
    }

    private static List<Line> buildLines(BlockDiagnosticSnapshot current,
                                         boolean stale) {
        ArrayList<Line> lines = new ArrayList<>();
        lines.add(new Line("Create Radar Inspector", 0xFFFFFF));
        lines.add(new Line(current.blockId() + " @ "
                + current.position().toShortString(), 0xA0D8FF));
        if (stale) {
            lines.add(new Line("Data is stale; waiting for server", 0xFFAA55));
        }

        ArrayList<SectionEntry> entries = new ArrayList<>();
        for (DiagnosticSection section : current.sections()) {
            for (DiagnosticEntry entry : section.entries()) {
                entries.add(new SectionEntry(section.title(), entry));
            }
        }
        entries.sort(Comparator.comparingInt(
                entry -> -entry.entry().severity().ordinal()));
        String lastSection = "";
        for (SectionEntry sectionEntry : entries) {
            if (lines.size() >= MAX_RENDERED_LINES) break;
            if (!sectionEntry.section().equals(lastSection)
                    && lines.size() < MAX_RENDERED_LINES - 1) {
                lines.add(new Line(sectionEntry.section(), 0xB8B8B8));
                lastSection = sectionEntry.section();
            }
            DiagnosticEntry entry = sectionEntry.entry();
            lines.add(new Line("  " + entry.key() + ": " + entry.value(),
                    severityColor(entry.severity())));
        }
        return List.copyOf(lines.subList(0,
                Math.min(MAX_RENDERED_LINES, lines.size())));
    }

    private static int severityColor(DiagnosticSeverity severity) {
        return switch (severity) {
            case INFO -> 0xE0E0E0;
            case WARN -> 0xFFD166;
            case ERROR -> 0xFF6B6B;
        };
    }

    private static int healthColor(DiagnosticHealth health) {
        return switch (health) {
            case HEALTHY -> 0xFF55CC77;
            case DEGRADED -> 0xFFFFB347;
            case FAILED -> 0xFFFF5555;
        };
    }

    private static String clip(String value, int maximumWidth) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.font.width(value) <= maximumWidth) return value;
        String ellipsis = "…";
        return minecraft.font.plainSubstrByWidth(value,
                Math.max(0, maximumWidth - minecraft.font.width(ellipsis)))
                + ellipsis;
    }

    private record Line(String text, int color) {
    }

    private record SectionEntry(String section, DiagnosticEntry entry) {
    }
}
