package com.happysg.radar.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Offline working-vs-broken support-bundle comparator. */
public final class DiagnosticReportComparator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
            .disableHtmlEscaping().create();
    private static final long MAX_ENTRY_BYTES = 2L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> READ_ENTRIES = Set.of(
            "state.json", "events.json", "conflict_analysis.json",
            "mixin_audit.json", "trace.json");

    private DiagnosticReportComparator() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 2 || arguments.length > 3) {
            throw new IllegalArgumentException(
                    "Usage: DiagnosticReportComparator <working.zip> "
                            + "<broken.zip> [comparison-output.json]");
        }
        Path baseline = Path.of(arguments[0]).toAbsolutePath().normalize();
        Path problem = Path.of(arguments[1]).toAbsolutePath().normalize();
        Path output = arguments.length == 3
                ? Path.of(arguments[2]).toAbsolutePath().normalize()
                : problem.resolveSibling("comparison_"
                + stripExtension(problem.getFileName().toString()) + ".json");
        Comparison comparison = compare(baseline, problem);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(comparison.json()),
                StandardCharsets.UTF_8);
        Path textOutput = replaceExtension(output, ".txt");
        Files.writeString(textOutput, comparison.text(),
                StandardCharsets.UTF_8);
        System.out.println("PASS diagnostic comparison: " + output);
        System.out.println("Text summary: " + textOutput);
    }

    public static Comparison compare(Path baseline, Path problem)
            throws IOException {
        ReportData before = ReportData.read(baseline);
        ReportData after = ReportData.read(problem);
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        root.addProperty("baseline", baseline.getFileName().toString());
        root.addProperty("problem", problem.getFileName().toString());

        ArrayList<String> compatibilityWarnings = new ArrayList<>();
        comparePlatform(before.state, after.state, compatibilityWarnings);
        root.add("compatibility_warnings", strings(compatibilityWarnings));

        JsonObject modDelta = modDelta(before.mods(), after.mods());
        root.add("mod_delta", modDelta);
        Set<String> newOverlaps = difference(after.overlaps(), before.overlaps());
        Set<String> newEvents = difference(after.eventFingerprints(),
                before.eventFingerprints());
        root.add("new_mixin_overlaps", strings(newOverlaps));
        root.add("new_event_fingerprints", strings(newEvents));

        List<DifferentialCandidate> candidates = differentialCandidates(
                before, after, modDelta, newOverlaps);
        root.add("ranked_candidates", GSON.toJsonTree(candidates));
        JsonObject timing = timingDelta(before.timings(), after.timings());
        root.add("timing_delta", timing);

        String text = textSummary(before, after, compatibilityWarnings,
                modDelta, newOverlaps, newEvents, candidates, timing);
        return new Comparison(root, text);
    }

    private static List<DifferentialCandidate> differentialCandidates(
            ReportData before, ReportData after, JsonObject modDelta,
            Set<String> newOverlaps) {
        HashMap<String, DifferentialBuilder> values = new HashMap<>();
        for (JsonElement element : modDelta.getAsJsonArray("added")) {
            String mod = element.getAsString();
            values.computeIfAbsent(mod, DifferentialBuilder::new)
                    .add(20, "present only in the broken-pack report");
        }
        for (JsonElement element : modDelta.getAsJsonArray("version_changed")) {
            String value = element.getAsString();
            String mod = value.substring(0, value.indexOf(' '));
            values.computeIfAbsent(mod, DifferentialBuilder::new)
                    .add(20, value);
        }
        after.candidateScores().forEach((mod, score) -> {
            int baselineScore = before.candidateScores()
                    .getOrDefault(mod, 0);
            if (score > baselineScore) {
                values.computeIfAbsent(mod, DifferentialBuilder::new)
                        .add(Math.min(80, score),
                                "conflict evidence score increased from "
                                        + baselineScore + " to " + score);
            }
        });
        for (String overlap : newOverlaps) {
            int separator = overlap.indexOf('|');
            if (separator <= 0) continue;
            String mod = overlap.substring(0, separator);
            int weight = overlap.contains("|HIGH|") ? 60
                    : overlap.contains("|MEDIUM|") ? 45 : 20;
            values.computeIfAbsent(mod, DifferentialBuilder::new)
                    .add(weight, "new applied Mixin overlap: " + overlap);
        }
        return values.values().stream().map(DifferentialBuilder::build)
                .sorted(Comparator.comparingInt(DifferentialCandidate::score)
                        .reversed().thenComparing(DifferentialCandidate::modId))
                .toList();
    }

    private static JsonObject modDelta(Map<String, String> before,
                                       Map<String, String> after) {
        ArrayList<String> added = new ArrayList<>();
        ArrayList<String> removed = new ArrayList<>();
        ArrayList<String> changed = new ArrayList<>();
        after.forEach((id, version) -> {
            if (!before.containsKey(id)) added.add(id);
            else if (!before.get(id).equals(version)) {
                changed.add(id + " " + before.get(id) + " -> " + version);
            }
        });
        before.keySet().stream().filter(id -> !after.containsKey(id))
                .forEach(removed::add);
        added.sort(String::compareTo);
        removed.sort(String::compareTo);
        changed.sort(String::compareTo);
        JsonObject result = new JsonObject();
        result.add("added", strings(added));
        result.add("removed", strings(removed));
        result.add("version_changed", strings(changed));
        return result;
    }

    private static JsonObject timingDelta(Map<String, Long> before,
                                          Map<String, Long> after) {
        JsonObject result = new JsonObject();
        after.forEach((operation, duration) -> {
            long baseline = before.getOrDefault(operation, 0L);
            if (duration > baseline) {
                JsonObject value = new JsonObject();
                value.addProperty("baseline_max_us", baseline);
                value.addProperty("problem_max_us", duration);
                value.addProperty("increase_us", duration - baseline);
                result.add(operation, value);
            }
        });
        return result;
    }

    private static void comparePlatform(JsonObject before, JsonObject after,
                                        List<String> warnings) {
        for (String field : List.of("minecraft", "neoforge")) {
            String oldValue = string(before, field);
            String newValue = string(after, field);
            if (!oldValue.equals(newValue)) {
                warnings.add(field + " differs: " + oldValue + " -> "
                        + newValue);
            }
        }
        String oldRadar = mods(before).getOrDefault("create_radar", "unknown");
        String newRadar = mods(after).getOrDefault("create_radar", "unknown");
        if (!oldRadar.equals(newRadar)) {
            warnings.add("create_radar differs: " + oldRadar + " -> "
                    + newRadar);
        }
    }

    private static String textSummary(ReportData before, ReportData after,
                                      List<String> warnings,
                                      JsonObject modDelta,
                                      Set<String> overlaps,
                                      Set<String> events,
                                      List<DifferentialCandidate> candidates,
                                      JsonObject timing) {
        StringBuilder text = new StringBuilder(
                "=== Create Radar Diagnostic Comparison ===\n");
        if (!warnings.isEmpty()) {
            text.append("Comparability warnings:\n");
            warnings.forEach(value -> text.append("- ").append(value)
                    .append('\n'));
        }
        text.append("Added mods: ").append(modDelta.get("added")).append('\n')
                .append("Removed mods: ").append(modDelta.get("removed"))
                .append('\n').append("Changed versions: ")
                .append(modDelta.get("version_changed")).append('\n')
                .append("New Mixin overlaps: ").append(overlaps.size())
                .append('\n').append("New failure fingerprints: ")
                .append(events.size()).append("\n\nRanked candidates:\n");
        if (candidates.isEmpty()) text.append("- none supported\n");
        for (DifferentialCandidate candidate : candidates) {
            text.append("- [").append(candidate.confidence()).append("] ")
                    .append(candidate.modId()).append(" score=")
                    .append(candidate.score()).append('\n');
            candidate.evidence().forEach(value -> text.append("  - ")
                    .append(value).append('\n'));
        }
        text.append("\nOperations with increased maximum duration: ")
                .append(timing.size()).append('\n');
        return text.toString();
    }

    private static Set<String> difference(Set<String> first,
                                          Set<String> second) {
        LinkedHashSet<String> result = new LinkedHashSet<>(first);
        result.removeAll(second);
        return result;
    }

    private static JsonArray strings(Iterable<String> values) {
        JsonArray result = new JsonArray();
        values.forEach(result::add);
        return result;
    }

    private static Map<String, String> mods(JsonObject state) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        JsonArray values = state.has("loaded_mods")
                ? state.getAsJsonArray("loaded_mods") : new JsonArray();
        for (JsonElement element : values) {
            if (!element.isJsonObject()) continue;
            JsonObject mod = element.getAsJsonObject();
            result.put(string(mod, "id"), string(mod, "version"));
        }
        return result;
    }

    private static String string(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull()
                ? object.get(name).getAsString() : "unknown";
    }

    private static Path replaceExtension(Path path, String extension) {
        return path.resolveSibling(stripExtension(path.getFileName().toString())
                + extension);
    }

    private static String stripExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot <= 0 ? value : value.substring(0, dot);
    }

    public record Comparison(JsonObject json, String text) {
    }

    public record DifferentialCandidate(String modId, int score,
                                        ConflictConfidence confidence,
                                        List<String> evidence) {
    }

    private static final class DifferentialBuilder {
        private final String modId;
        private final LinkedHashMap<String, Integer> evidence =
                new LinkedHashMap<>();

        private DifferentialBuilder(String modId) {
            this.modId = modId;
        }

        private void add(int weight, String reason) {
            evidence.merge(reason, weight, Math::max);
        }

        private DifferentialCandidate build() {
            int score = Math.min(100, evidence.values().stream()
                    .mapToInt(Integer::intValue).sum());
            return new DifferentialCandidate(modId, score,
                    ConflictConfidence.fromScore(score),
                    List.copyOf(evidence.keySet()));
        }
    }

    private record ReportData(JsonObject state, JsonObject events,
                              JsonObject conflicts, JsonObject mixins,
                              JsonObject trace) {
        private static ReportData read(Path path) throws IOException {
            if (!Files.isRegularFile(path)) {
                throw new IOException("Report does not exist: " + path);
            }
            HashMap<String, JsonObject> entries = new HashMap<>();
            long total = 0L;
            try (ZipFile zip = new ZipFile(path.toFile(),
                    StandardCharsets.UTF_8)) {
                for (String name : READ_ENTRIES) {
                    ZipEntry entry = zip.getEntry(name);
                    if (entry == null) continue;
                    if (entry.getSize() > MAX_ENTRY_BYTES) {
                        throw new IOException("Oversized report entry " + name);
                    }
                    try (InputStream input = zip.getInputStream(entry)) {
                        byte[] bytes = input.readNBytes(
                                Math.toIntExact(MAX_ENTRY_BYTES + 1));
                        if (bytes.length > MAX_ENTRY_BYTES) {
                            throw new IOException("Oversized report entry "
                                    + name);
                        }
                        total += bytes.length;
                        if (total > MAX_TOTAL_BYTES) {
                            throw new IOException("Report exceeds size limit");
                        }
                        JsonElement parsed = JsonParser.parseString(
                                new String(bytes, StandardCharsets.UTF_8));
                        entries.put(name, parsed.getAsJsonObject());
                    } catch (RuntimeException malformed) {
                        throw new IOException("Malformed report entry " + name,
                                malformed);
                    }
                }
            }
            JsonObject state = entries.get("state.json");
            if (state == null) throw new IOException("state.json is required");
            return new ReportData(state,
                    entries.getOrDefault("events.json", new JsonObject()),
                    entries.getOrDefault("conflict_analysis.json",
                            new JsonObject()),
                    entries.getOrDefault("mixin_audit.json", new JsonObject()),
                    entries.getOrDefault("trace.json", new JsonObject()));
        }

        private Map<String, String> mods() {
            return DiagnosticReportComparator.mods(state);
        }

        private Set<String> eventFingerprints() {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            JsonArray values = events.has("events")
                    ? events.getAsJsonArray("events") : new JsonArray();
            for (JsonElement element : values) {
                if (!element.isJsonObject()) continue;
                String value = string(element.getAsJsonObject(),
                        "stack_fingerprint");
                if (!value.equals("unknown") && !value.isBlank()) result.add(value);
            }
            return result;
        }

        private Set<String> overlaps() {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            JsonArray values = mixins.has("overlaps")
                    ? mixins.getAsJsonArray("overlaps") : new JsonArray();
            for (JsonElement element : values) {
                if (!element.isJsonObject()) continue;
                JsonObject overlap = element.getAsJsonObject();
                JsonArray mods = overlap.has("foreignMods")
                        ? overlap.getAsJsonArray("foreignMods") : new JsonArray();
                for (JsonElement mod : mods) {
                    result.add(mod.getAsString() + "|"
                            + string(overlap, "risk") + "|"
                            + string(overlap, "targetClass") + "|"
                            + string(overlap, "foreignMixin"));
                }
            }
            return result;
        }

        private Map<String, Integer> candidateScores() {
            HashMap<String, Integer> result = new HashMap<>();
            JsonArray values = conflicts.has("candidates")
                    ? conflicts.getAsJsonArray("candidates") : new JsonArray();
            for (JsonElement element : values) {
                if (!element.isJsonObject()) continue;
                JsonObject candidate = element.getAsJsonObject();
                result.put(string(candidate, "modId"),
                        candidate.has("score") ? candidate.get("score").getAsInt()
                                : 0);
            }
            return result;
        }

        private Map<String, Long> timings() {
            HashMap<String, Long> result = new HashMap<>();
            JsonArray values = trace.has("events")
                    ? trace.getAsJsonArray("events") : new JsonArray();
            for (JsonElement element : values) {
                if (!element.isJsonObject()) continue;
                JsonObject event = element.getAsJsonObject();
                if (!event.has("context") || !event.get("context").isJsonObject())
                    continue;
                JsonObject context = event.getAsJsonObject("context");
                if (!context.has("duration_us")) continue;
                try {
                    long duration = Long.parseLong(
                            context.get("duration_us").getAsString());
                    String operation = string(event, "subsystem") + "/"
                            + string(event, "operation");
                    result.merge(operation, duration, Math::max);
                } catch (NumberFormatException ignored) {
                }
            }
            return result;
        }
    }
}
