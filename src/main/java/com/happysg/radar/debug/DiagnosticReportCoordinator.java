package com.happysg.radar.debug;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.networking.packets.ClientDiagnosticRequestPacket;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@EventBusSubscriber(modid = CreateRadar.MODID)
public final class DiagnosticReportCoordinator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CLIENT_TIMEOUT_TICKS = 40;
    private static final AtomicInteger NEXT_REQUEST = new AtomicInteger(1);
    private static final Map<Integer, PendingReport> PENDING =
            new ConcurrentHashMap<>();
    private static final ExecutorService REPORT_WRITER =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable,
                        "CreateRadar-DiagnosticReportWriter");
                thread.setDaemon(true);
                return thread;
            });

    private DiagnosticReportCoordinator() {
    }

    public static int startOverall(CommandSourceStack source) {
        DiagnosticRecorder.Summary summary = DiagnosticRecorder.summary();
        String reportId = reportId();
        source.sendSuccess(() -> summaryComponent(summary, reportId), false);
        ServerPlayer player = source.getPlayer();
        BlockDiagnosticSnapshot inspected = player == null ? null
                : BlockDiagnosticService.inspect(player);
        if (player == null) {
            finish(source.getServer(), null, reportId, "overall", inspected,
                    null);
            return 1;
        }

        int requestId = NEXT_REQUEST.getAndIncrement();
        long deadline = source.getServer().getTickCount()
                + CLIENT_TIMEOUT_TICKS;
        PENDING.put(requestId, new PendingReport(source.getServer(),
                player.getUUID(), reportId, "overall", inspected, deadline));
        ClientDiagnosticRequestPacket.send(player, requestId);
        source.sendSuccess(() -> Component.literal(
                "Collecting the invoking client's bounded diagnostic appendix…")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    public static int dumpInspectedBlock(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal(
                    "A player is required to inspect a block."));
            return 0;
        }
        BlockDiagnosticSnapshot inspected =
                BlockDiagnosticService.inspect(player);
        if (inspected.status() != BlockDiagnosticSnapshot.Status.OK) {
            source.sendFailure(Component.literal(inspected.message()));
            return 0;
        }
        String reportId = reportId();
        finish(source.getServer(), player.getUUID(), reportId,
                "block_inspector", inspected, null);
        return 1;
    }

    public static void acceptClientReport(ServerPlayer player, int requestId,
                                          ClientDiagnosticReport report) {
        PendingReport pending = PENDING.get(requestId);
        if (pending == null || !pending.playerId().equals(player.getUUID())
                || pending.server() != player.getServer()) {
            return;
        }
        PENDING.remove(requestId);
        finish(pending.server(), pending.playerId(), pending.reportId(),
                pending.reportKind(), pending.inspected(), report);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().getTickCount();
        ArrayList<Integer> expired = new ArrayList<>();
        for (Map.Entry<Integer, PendingReport> entry : PENDING.entrySet()) {
            PendingReport pending = entry.getValue();
            if (pending.server() == event.getServer()
                    && now >= pending.deadlineTick()) {
                expired.add(entry.getKey());
            }
        }
        for (Integer requestId : expired) {
            PendingReport pending = PENDING.remove(requestId);
            if (pending != null) {
                finish(pending.server(), pending.playerId(),
                        pending.reportId(), pending.reportKind(),
                        pending.inspected(), null);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.entrySet().removeIf(entry ->
                entry.getValue().server() == event.getServer());
    }

    private static void finish(MinecraftServer server,
                               @Nullable UUID playerId, String reportId,
                               String reportKind,
                               @Nullable BlockDiagnosticSnapshot inspected,
                               @Nullable ClientDiagnosticReport client) {
        DiagnosticReportWriter.Capture capture;
        try {
            capture = DiagnosticReportWriter.prepare(server, reportId,
                    reportKind, inspected);
        } catch (RuntimeException | LinkageError failure) {
            reportFailure(server, playerId, reportId, failure);
            return;
        }
        REPORT_WRITER.execute(() -> {
            try {
                DiagnosticReportWriter.Result result =
                        DiagnosticReportWriter.write(capture, client);
                server.execute(() -> send(server, playerId,
                        successComponent(result)));
            } catch (IOException | RuntimeException failure) {
                server.execute(() -> reportFailure(server, playerId,
                        reportId, failure));
            }
        });
    }

    private static void reportFailure(MinecraftServer server,
                                      @Nullable UUID playerId,
                                      String reportId, Throwable failure) {
            DiagnosticRecorder.error("reporting", "write_bundle",
                    "report_write_failed", failure, null, null);
            LOGGER.error("Failed to write Create Radar diagnostic report {}",
                    reportId, failure);
            send(server, playerId, Component.literal(
                    "Failed to write Create Radar report " + reportId + ": "
                            + DiagnosticRecorder.sanitize(
                            failure.getMessage(), 160))
                    .withStyle(ChatFormatting.RED));
    }

    private static void send(MinecraftServer server, @Nullable UUID playerId,
                             Component component) {
        if (playerId != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.sendSystemMessage(component);
                return;
            }
        }
        server.sendSystemMessage(component);
    }

    private static Component summaryComponent(
            DiagnosticRecorder.Summary summary, String reportId) {
        ChatFormatting color = switch (summary.health()) {
            case HEALTHY -> ChatFormatting.GREEN;
            case DEGRADED -> ChatFormatting.YELLOW;
            case FAILED -> ChatFormatting.RED;
        };
        return Component.literal("Create Radar health: ")
                .append(Component.literal(summary.health().name())
                        .withStyle(color))
                .append(Component.literal(" | groups=" + summary.groups()
                        + ", warnings=" + summary.warningOccurrences()
                        + ", errors=" + summary.errorOccurrences()
                        + " | report=" + reportId));
    }

    private static Component successComponent(
            DiagnosticReportWriter.Result result) {
        Path path = result.path();
        String reportId = result.reportId();
        String absolute = path.toAbsolutePath().normalize().toString();
        Component message = Component.literal(
                        "Create Radar report " + reportId + ": ")
                .append(Component.literal(path.getFileName().toString())
                        .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent(
                                        ClickEvent.Action.COPY_TO_CLIPBOARD,
                                        absolute))
                                .withHoverEvent(new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        Component.literal(
                                                "Click to copy report path")))));
        if (!result.topCandidates().isEmpty()) {
            String candidates = result.topCandidates().stream()
                    .map(candidate -> candidate.modId() + "["
                            + candidate.confidence() + "]")
                    .collect(java.util.stream.Collectors.joining(", "));
            message = message.copy().append(Component.literal(
                    " | candidates: " + candidates)
                    .withStyle(ChatFormatting.YELLOW));
        }
        return message;
    }

    private static String reportId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record PendingReport(MinecraftServer server, UUID playerId,
                                 String reportId, String reportKind,
                                 @Nullable BlockDiagnosticSnapshot inspected,
                                 long deadlineTick) {
    }
}
