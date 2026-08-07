package com.happysg.radar.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.happysg.radar.mixin.diagnostic.EarlyDiagnosticJournal;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.config.RadarConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.internal.versions.neoforge.NeoForgeVersion;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.io.BufferedReader;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class DiagnosticReportWriter {
    public static final String DIRECTORY_NAME = "create_radar_debug";
    private static final int SCHEMA_VERSION = 2;
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT)
                    .withZone(ZoneOffset.UTC);

    private DiagnosticReportWriter() {
    }

    public static Result write(MinecraftServer server, String reportId,
                               String reportKind,
                               @Nullable BlockDiagnosticSnapshot inspected,
                               @Nullable ClientDiagnosticReport clientReport)
            throws IOException {
        return write(prepare(server, reportId, reportKind, inspected),
                clientReport);
    }

    /** Capture mutable game/Mixin state on the calling game thread. */
    public static Capture prepare(MinecraftServer server, String reportId,
                                  String reportKind,
                                  @Nullable BlockDiagnosticSnapshot inspected) {
        Instant generated = Instant.now();
        DiagnosticRecorder.Summary summary = DiagnosticRecorder.summary();
        List<DiagnosticEvent> events = DiagnosticRecorder.snapshot();
        ConflictAnalysisSnapshot conflicts = ConflictAnalyzer.capture(events);
        ConflictTraceRecorder.Snapshot trace = ConflictTraceRecorder.snapshot();
        JsonObject state = stateJson(server, reportId, reportKind, generated,
                summary);
        return new Capture(reportId, reportKind, generated, summary, events,
                state, inspected, conflicts, trace);
    }

    /** Perform filesystem reads and ZIP creation off the game thread. */
    public static Result write(Capture capture,
                               @Nullable ClientDiagnosticReport clientReport)
            throws IOException {
        Instant generated = capture.generated();
        DiagnosticRecorder.Summary summary = capture.summary();
        List<DiagnosticEvent> events = capture.events();
        String reportId = capture.reportId();
        String fileName = "report_" + FILE_TIME.format(generated) + "_"
                + reportId + ".zip";
        Path directory = FMLPaths.GAMEDIR.get().resolve(DIRECTORY_NAME);
        Path target = directory.resolve(fileName);
        Path temporary = directory.resolve("." + fileName + ".tmp");
        Files.createDirectories(directory);

        try (OutputStream output = Files.newOutputStream(temporary);
             ZipOutputStream zip = new ZipOutputStream(output,
                     StandardCharsets.UTF_8)) {
            writeEntry(zip, "summary.txt", summaryText(reportId,
                    capture.reportKind(),
                    generated, summary, events, clientReport,
                    capture.conflicts()), generated);
            writeEntry(zip, "events.json", GSON.toJson(
                    eventsJson(events, "server")), generated);
            writeEntry(zip, "state.json", GSON.toJson(capture.state()),
                    generated);
            writeEntry(zip, "conflict_analysis.json", GSON.toJson(
                    capture.conflicts()), generated);
            writeEntry(zip, "mixin_audit.json", GSON.toJson(Map.of(
                    "schema", SCHEMA_VERSION,
                    "applied_mixins", capture.conflicts().appliedMixins(),
                    "overlaps", capture.conflicts().mixinOverlaps())), generated);
            writeEntry(zip, "class_ownership.json", GSON.toJson(Map.of(
                    "schema", SCHEMA_VERSION,
                    "classes", capture.conflicts().relevantClasses())), generated);
            writeEntry(zip, "trace.json", GSON.toJson(capture.trace()),
                    generated);
            writeEntry(zip, "startup_journal.json", GSON.toJson(
                    startupJournalJson()), generated);
            FilteredLogEvidence.Evidence logEvidence =
                    FilteredLogEvidence.capture();
            writeEntry(zip, "log_evidence.txt", logEvidence.text(), generated);
            if (capture.inspected() != null
                    && capture.inspected().status()
                    == BlockDiagnosticSnapshot.Status.OK) {
                writeEntry(zip, "inspected_block.json", GSON.toJson(
                        snapshotJson(capture.inspected())), generated);
            }
            if (clientReport != null) {
                writeEntry(zip, "client.json", GSON.toJson(
                        clientJson(clientReport)), generated);
                writeEntry(zip, "client_conflict_analysis.json",
                        clientReport.conflictAppendix().analysisJson(), generated);
                writeEntry(zip, "client_trace.json",
                        clientReport.conflictAppendix().traceJson(), generated);
                writeEntry(zip, "client_startup_journal.txt",
                        clientReport.conflictAppendix().startupJournal(), generated);
                writeEntry(zip, "client_log_evidence.txt",
                        clientReport.conflictAppendix().filteredLogEvidence(),
                        generated);
            }
        } catch (IOException failure) {
            Files.deleteIfExists(temporary);
            throw failure;
        }

        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, target);
        }
        return new Result(reportId, target, summary,
                capture.conflicts().candidates().stream().limit(3).toList());
    }

    private static String summaryText(String reportId, String reportKind,
                                      Instant generated,
                                      DiagnosticRecorder.Summary summary,
                                      List<DiagnosticEvent> events,
                                      @Nullable ClientDiagnosticReport client,
                                      ConflictAnalysisSnapshot conflicts) {
        StringBuilder text = new StringBuilder();
        text.append("=== Create Radar Diagnostic Report ===\n")
                .append("Schema: ").append(SCHEMA_VERSION).append('\n')
                .append("Report ID: ").append(reportId).append('\n')
                .append("Kind: ").append(reportKind).append('\n')
                .append("Generated UTC: ").append(generated).append('\n')
                .append("Worst recorded health: ").append(summary.health())
                .append('\n')
                .append("Event groups: ").append(summary.groups()).append('\n')
                .append("Occurrences: info=")
                .append(summary.infoOccurrences()).append(", warn=")
                .append(summary.warningOccurrences()).append(", error=")
                .append(summary.errorOccurrences()).append('\n')
                .append("Invoking-client appendix: ")
                .append(client == null ? "unavailable" : "included")
                .append("\n\n");
        if (conflicts.candidates().isEmpty()) {
            text.append("Conflict candidates: none supported by current evidence.\n\n");
        } else {
            text.append("=== Ranked Conflict Candidates ===\n");
            for (ConflictCandidate candidate : conflicts.candidates().stream()
                    .limit(10).toList()) {
                text.append('[').append(candidate.confidence()).append("] ")
                        .append(candidate.modId()).append(' ')
                        .append(candidate.version()).append(" score=")
                        .append(candidate.score()).append('\n');
                candidate.evidence().stream().limit(3).forEach(evidence ->
                        text.append("  - ").append(evidence.summary())
                                .append('\n'));
            }
            text.append('\n');
        }
        if (events.isEmpty()) {
            text.append("No diagnostic events were recorded this session.\n");
        } else {
            text.append("=== Recorded Events ===\n");
            for (DiagnosticEvent event : events) {
                text.append('[').append(event.severity()).append("] ")
                        .append(event.subsystem()).append(" / ")
                        .append(event.operation()).append(": ")
                        .append(event.reason()).append(" (x")
                        .append(event.occurrences()).append(", fingerprint=")
                        .append(event.stackFingerprint().isBlank() ? "none"
                                : event.stackFingerprint()).append(")\n");
            }
        }
        text.append("\nPrivacy: secrets, credentials, player UUIDs, server ports, ")
                .append("filesystem paths, chat, raw NBT, and general logs are not ")
                .append("included. Coordinates may be present for inspected blocks.\n");
        return text.toString();
    }

    private static JsonObject stateJson(MinecraftServer server,
                                        String reportId, String reportKind,
                                        Instant generated,
                                        DiagnosticRecorder.Summary summary) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        root.addProperty("report_id", reportId);
        root.addProperty("kind", reportKind);
        root.addProperty("generated_utc", generated.toString());
        root.addProperty("minecraft", server.getServerVersion());
        root.addProperty("neoforge", NeoForgeVersion.getVersion());
        root.addProperty("dedicated_server", server.isDedicatedServer());
        root.addProperty("online_mode", server.usesAuthentication());
        root.addProperty("java", System.getProperty("java.version", "unknown")
                + " " + System.getProperty("java.vendor", "unknown"));
        root.addProperty("os", System.getProperty("os.name", "unknown")
                + " " + System.getProperty("os.version", "unknown")
                + " " + System.getProperty("os.arch", "unknown"));
        root.addProperty("worst_recorded_health", summary.health().name());

        JsonArray mods = new JsonArray();
        ModList.get().getMods().stream()
                .sorted(Comparator.comparing(mod -> mod.getModId()))
                .forEach(mod -> {
                    JsonObject value = new JsonObject();
                    value.addProperty("id", mod.getModId());
                    value.addProperty("version", mod.getVersion().toString());
                    mods.add(value);
                });
        root.add("loaded_mods", mods);
        root.addProperty("environment_fingerprint",
                environmentFingerprint(mods));

        JsonArray integrations = new JsonArray();
        for (Mods mod : Mods.values()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", mod.id());
            value.addProperty("loaded", mod.isLoaded());
            integrations.add(value);
        }
        root.add("optional_integrations", integrations);

        JsonArray dimensions = new JsonArray();
        for (ServerLevel level : server.getAllLevels()) {
            JsonObject value = new JsonObject();
            value.addProperty("dimension",
                    level.dimension().location().toString());
            value.addProperty("game_time", level.getGameTime());
            NetworkData data = NetworkData.get(level);
            int monitors = 0;
            int radars = 0;
            int weapons = 0;
            int dataLinks = 0;
            for (NetworkData.Group group : data.getGroups().values()) {
                monitors += group.monitorEndpoints.size();
                radars += group.radarEndpoints.size();
                weapons += group.weaponEndpoints.size();
                dataLinks += group.dataLinks.size();
            }
            value.addProperty("network_groups", data.getGroups().size());
            value.addProperty("monitor_endpoints", monitors);
            value.addProperty("radar_endpoints", radars);
            value.addProperty("weapon_endpoints", weapons);
            value.addProperty("data_links", dataLinks);
            WeaponNetworkRuntime runtime = WeaponNetworkRuntime.peek(level);
            value.addProperty("loaded_weapon_groups", runtime == null ? 0
                    : runtime.getGroups().size());
            dimensions.add(value);
        }
        root.add("loaded_dimensions", dimensions);
        root.add("server_config", serverConfigJson());
        root.add("contributors", DiagnosticContributors.collect(server));
        root.addProperty("collection_policy",
                "read_only; loaded state only; no chunk scans or repairs");
        return root;
    }

    private static JsonObject serverConfigJson() {
        JsonObject config = new JsonObject();
        if (!RadarConfig.isServerConfigLoaded()) {
            config.addProperty("status", "not_loaded");
            return config;
        }
        config.addProperty("status", "loaded");
        config.addProperty("new_targeting_computer",
                RadarConfig.server().useNewTargetingComputer.get());
        config.addProperty("force_legacy_solver",
                RadarConfig.server().forceLegacyCannonLeadSolver.get());
        config.addProperty("target_tracking_lead_ticks",
                RadarConfig.server().targetTrackingLeadTicks.get());
        config.addProperty("firing_delay",
                RadarConfig.server().leadFiringDelay.get());
        config.addProperty("max_radar_range",
                RadarConfig.server().maxRadarRange.get());
        config.addProperty("max_sky_radar_range",
                RadarConfig.server().maxSkyRadarRange.get());
        config.addProperty("radar_occlusion_enabled",
                RadarConfig.server().radarOcclusionEnabled.get());
        config.addProperty("chaff_enabled",
                RadarConfig.server().chaffEnabled.get());
        return config;
    }

    private static JsonObject eventsJson(List<DiagnosticEvent> events,
                                         String source) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        root.addProperty("source", source);
        JsonArray values = new JsonArray();
        for (DiagnosticEvent event : events) values.add(eventJson(event));
        root.add("events", values);
        return root;
    }

    private static JsonObject clientJson(ClientDiagnosticReport report) {
        JsonObject root = eventsJson(report.events(), "invoking_client");
        root.addProperty("trust", "client_provided_untrusted");
        root.addProperty("java", report.javaVersion());
        root.addProperty("os", report.operatingSystem());
        JsonArray mods = new JsonArray();
        report.loadedMods().forEach(mods::add);
        root.add("loaded_mods", mods);
        return root;
    }

    private static JsonObject eventJson(DiagnosticEvent event) {
        JsonObject value = new JsonObject();
        value.addProperty("sequence", event.sequence());
        value.addProperty("severity", event.severity().name());
        value.addProperty("subsystem", event.subsystem());
        value.addProperty("operation", event.operation());
        value.addProperty("reason", event.reason());
        value.addProperty("exception_type", event.exceptionType());
        value.addProperty("exception_message", event.exceptionMessage());
        value.addProperty("stack_fingerprint", event.stackFingerprint());
        value.addProperty("stack_trace", event.stackTrace());
        value.addProperty("side", event.side());
        value.addProperty("dimension", event.dimension());
        value.addProperty("position", event.position());
        JsonArray mods = new JsonArray();
        event.implicatedMods().forEach(mods::add);
        value.add("implicated_mods", mods);
        value.addProperty("first_epoch_ms", event.firstEpochMillis());
        value.addProperty("last_epoch_ms", event.lastEpochMillis());
        value.addProperty("first_tick", event.firstTick());
        value.addProperty("last_tick", event.lastTick());
        value.addProperty("occurrences", event.occurrences());
        return value;
    }

    private static JsonObject snapshotJson(BlockDiagnosticSnapshot snapshot) {
        JsonObject root = new JsonObject();
        root.addProperty("status", snapshot.status().name());
        root.addProperty("health", snapshot.health().name());
        root.addProperty("dimension", snapshot.dimension());
        root.addProperty("position", snapshot.position().toShortString());
        root.addProperty("block", snapshot.blockId());
        root.addProperty("block_entity_type", snapshot.blockEntityType());
        root.addProperty("game_time", snapshot.gameTime());
        JsonArray sections = new JsonArray();
        for (DiagnosticSection section : snapshot.sections()) {
            JsonObject sectionJson = new JsonObject();
            sectionJson.addProperty("title", section.title());
            JsonArray entries = new JsonArray();
            for (DiagnosticEntry entry : section.entries()) {
                JsonObject entryJson = new JsonObject();
                entryJson.addProperty("key", entry.key());
                entryJson.addProperty("value", entry.value());
                entryJson.addProperty("severity", entry.severity().name());
                entries.add(entryJson);
            }
            sectionJson.add("entries", entries);
            sections.add(sectionJson);
        }
        root.add("sections", sections);
        return root;
    }

    private static void writeEntry(ZipOutputStream zip, String name,
                                   String content, Instant generated)
            throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(generated.toEpochMilli());
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static JsonObject startupJournalJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonArray files = new JsonArray();
        for (Path path : EarlyDiagnosticJournal.journalFiles()) {
            if (!Files.isRegularFile(path)) continue;
            JsonObject file = new JsonObject();
            file.addProperty("name", DiagnosticRecorder.sanitize(
                    path.getFileName().toString(), 180));
            JsonArray events = new JsonArray();
            try (BufferedReader reader = Files.newBufferedReader(path,
                    StandardCharsets.UTF_8)) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count++ < 512) {
                    try {
                        JsonElement parsed = JsonParser.parseString(
                                DiagnosticPrivacy.redact(line));
                        events.add(parsed);
                    } catch (RuntimeException malformed) {
                        JsonObject value = new JsonObject();
                        value.addProperty("status", "malformed_line");
                        events.add(value);
                    }
                }
            } catch (IOException failure) {
                file.addProperty("read_error",
                        failure.getClass().getSimpleName());
            }
            file.add("events", events);
            files.add(file);
        }
        root.add("files", files);
        return root;
    }

    private static String environmentFingerprint(JsonArray mods) {
        return DiagnosticRecorder.fingerprintText(mods.toString());
    }

    public record Capture(String reportId, String reportKind,
                          Instant generated,
                          DiagnosticRecorder.Summary summary,
                          List<DiagnosticEvent> events, JsonObject state,
                          @Nullable BlockDiagnosticSnapshot inspected,
                          ConflictAnalysisSnapshot conflicts,
                          ConflictTraceRecorder.Snapshot trace) {
    }

    public record Result(String reportId, Path path,
                         DiagnosticRecorder.Summary summary,
                         List<ConflictCandidate> topCandidates) {
    }
}
