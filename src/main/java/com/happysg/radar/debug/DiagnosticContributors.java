package com.happysg.radar.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.compat.Mods;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

public final class DiagnosticContributors {
    private static final CopyOnWriteArrayList<DiagnosticContributor>
            CONTRIBUTORS = new CopyOnWriteArrayList<>();

    static {
        register(new WeaponNetworkContributor());
        register(new OptionalIntegrationContributor());
        register(new EventCategoryContributor("targeting_and_weapons",
                name -> containsAny(name, "target", "weapon", "cbc")));
        register(new EventCategoryContributor("radar_contacts_chaff_rwr",
                name -> containsAny(name, "radar", "contact", "chaff",
                        "rwr", "sable")));
        register(new EventCategoryContributor("networking_and_data_links",
                name -> containsAny(name, "network", "packet", "datalink")));
        register(new EventCategoryContributor("diagnostic_system",
                name -> containsAny(name, "inspect", "report")));
    }

    private DiagnosticContributors() {
    }

    public static void register(DiagnosticContributor contributor) {
        if (contributor == null || contributor.id() == null
                || contributor.id().isBlank()) return;
        CONTRIBUTORS.removeIf(existing ->
                existing.id().equals(contributor.id()));
        CONTRIBUTORS.add(contributor);
    }

    public static JsonObject collect(MinecraftServer server) {
        JsonObject sections = new JsonObject();
        for (DiagnosticContributor contributor : CONTRIBUTORS) {
            try {
                sections.add(contributor.id(), contributor.collect(server));
            } catch (RuntimeException | LinkageError failure) {
                DiagnosticRecorder.error("reporting", "contributor",
                        "contributor_failed:" + contributor.id(), failure,
                        null, null);
                JsonObject error = new JsonObject();
                error.addProperty("status", "error");
                error.addProperty("failure",
                        failure.getClass().getSimpleName());
                sections.add(contributor.id(), error);
            }
        }
        return sections;
    }

    private static boolean containsAny(String value, String... tokens) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (normalized.contains(token)) return true;
        }
        return false;
    }

    private static final class WeaponNetworkContributor
            implements DiagnosticContributor {
        @Override
        public String id() {
            return "weapon_networks";
        }

        @Override
        public JsonObject collect(MinecraftServer server) {
            JsonObject result = new JsonObject();
            JsonArray dimensions = new JsonArray();
            for (ServerLevel level : server.getAllLevels()) {
                NetworkData data = NetworkData.get(level);
                WeaponNetworkRuntime runtime = WeaponNetworkRuntime.peek(level);
                JsonObject value = new JsonObject();
                value.addProperty("dimension",
                        level.dimension().location().toString());
                value.addProperty("filter_groups", data.getGroups().size());
                value.addProperty("loaded_weapon_groups", runtime == null
                        ? 0 : runtime.getGroups().size());
                dimensions.add(value);
            }
            result.add("dimensions", dimensions);
            return result;
        }
    }

    private static final class OptionalIntegrationContributor
            implements DiagnosticContributor {
        @Override
        public String id() {
            return "optional_integrations";
        }

        @Override
        public JsonObject collect(MinecraftServer server) {
            JsonObject result = new JsonObject();
            for (Mods mod : Mods.values()) {
                result.addProperty(mod.id(), mod.isLoaded());
            }
            return result;
        }
    }

    private record EventCategoryContributor(
            String id, Predicate<String> subsystemFilter)
            implements DiagnosticContributor {
        @Override
        public JsonObject collect(MinecraftServer server) {
            List<DiagnosticEvent> events = DiagnosticRecorder.snapshot().stream()
                    .filter(event -> subsystemFilter.test(event.subsystem()))
                    .toList();
            long warnings = events.stream()
                    .filter(event -> event.severity()
                            == DiagnosticSeverity.WARN)
                    .mapToLong(DiagnosticEvent::occurrences).sum();
            long errors = events.stream()
                    .filter(event -> event.severity()
                            == DiagnosticSeverity.ERROR)
                    .mapToLong(DiagnosticEvent::occurrences).sum();
            JsonObject result = new JsonObject();
            result.addProperty("event_groups", events.size());
            result.addProperty("warning_occurrences", warnings);
            result.addProperty("error_occurrences", errors);
            result.addProperty("health", errors > 0 ? "FAILED"
                    : warnings > 0 ? "DEGRADED" : "HEALTHY");
            if (!events.isEmpty()) {
                DiagnosticEvent latest = events.getLast();
                result.addProperty("latest_reason", latest.reason());
                result.addProperty("latest_fingerprint",
                        latest.stackFingerprint());
            }
            return result;
        }
    }
}
