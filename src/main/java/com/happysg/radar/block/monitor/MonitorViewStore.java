package com.happysg.radar.block.monitor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.happysg.radar.CreateRadar;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public class MonitorViewStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.GAMEDIR.get().resolve("config").resolve("create_radar_monitor_views.json");
    private static final String PRESETS_KEY = "presets";

    private static JsonObject root;

    private MonitorViewStore() {
    }

    public static Optional<MonitorProjection.View> get(Minecraft mc, BlockPos monitorPos) {
        String key = key(mc, monitorPos);
        if (key == null)
            return Optional.empty();

        JsonObject views = load();
        JsonElement element = views.get(key);
        if (element == null || !element.isJsonObject())
            return Optional.empty();

        return readView(element.getAsJsonObject());
    }

    public static void set(Minecraft mc, BlockPos monitorPos, MonitorProjection.View view) {
        String key = key(mc, monitorPos);
        if (key == null)
            return;

        JsonObject entry = getOrCreateEntry(key);
        writeView(entry, view);
        save();
    }

    public static void clear(Minecraft mc, BlockPos monitorPos) {
        String key = key(mc, monitorPos);
        if (key == null)
            return;

        JsonObject views = load();
        JsonElement element = views.get(key);
        if (element != null && element.isJsonObject()) {
            JsonObject entry = element.getAsJsonObject();
            entry.remove("centerX");
            entry.remove("centerZ");
            entry.remove("halfSpan");
            if (entry.size() == 0)
                views.remove(key);
        } else {
            views.remove(key);
        }
        save();
    }

    public static Optional<MonitorProjection.View> getPreset(Minecraft mc, BlockPos monitorPos, int slot) {
        if (!validSlot(slot))
            return Optional.empty();

        String key = key(mc, monitorPos);
        if (key == null)
            return Optional.empty();

        JsonElement element = load().get(key);
        if (element == null || !element.isJsonObject())
            return Optional.empty();

        JsonObject entry = element.getAsJsonObject();
        JsonElement presetsElement = entry.get(PRESETS_KEY);
        if (presetsElement == null || !presetsElement.isJsonObject())
            return Optional.empty();

        JsonElement preset = presetsElement.getAsJsonObject().get(Integer.toString(slot));
        if (preset == null || !preset.isJsonObject())
            return Optional.empty();

        return readView(preset.getAsJsonObject());
    }

    public static void setPreset(Minecraft mc, BlockPos monitorPos, int slot, MonitorProjection.View view) {
        if (!validSlot(slot))
            return;

        String key = key(mc, monitorPos);
        if (key == null)
            return;

        JsonObject entry = getOrCreateEntry(key);
        JsonElement presetsElement = entry.get(PRESETS_KEY);
        JsonObject presets;
        if (presetsElement != null && presetsElement.isJsonObject()) {
            presets = presetsElement.getAsJsonObject();
        } else {
            presets = new JsonObject();
            entry.add(PRESETS_KEY, presets);
        }

        JsonObject preset = new JsonObject();
        writeView(preset, view);
        presets.add(Integer.toString(slot), preset);
        save();
    }

    private static boolean validSlot(int slot) {
        return slot >= 1 && slot <= 10;
    }

    private static JsonObject getOrCreateEntry(String key) {
        JsonObject views = load();
        JsonElement element = views.get(key);
        if (element != null && element.isJsonObject())
            return element.getAsJsonObject();

        JsonObject entry = new JsonObject();
        views.add(key, entry);
        return entry;
    }

    private static Optional<MonitorProjection.View> readView(JsonObject view) {
        if (!view.has("centerX") || !view.has("centerZ") || !view.has("halfSpan"))
            return Optional.empty();

        return Optional.of(new MonitorProjection.View(
                view.get("centerX").getAsDouble(),
                view.get("centerZ").getAsDouble(),
                Math.max(1f, view.get("halfSpan").getAsFloat())
        ));
    }

    private static void writeView(JsonObject entry, MonitorProjection.View view) {
        entry.addProperty("centerX", view.centerX());
        entry.addProperty("centerZ", view.centerZ());
        entry.addProperty("halfSpan", view.halfSpan());
    }

    private static JsonObject load() {
        if (root != null)
            return root;

        root = new JsonObject();
        if (!Files.exists(FILE))
            return root;

        try {
            JsonElement element = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8));
            if (element.isJsonObject())
                root = element.getAsJsonObject();
        } catch (Exception e) {
            CreateRadar.getLogger().warn("Failed to load monitor GUI view state", e);
            root = new JsonObject();
        }

        return root;
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(load()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CreateRadar.getLogger().warn("Failed to save monitor GUI view state", e);
        }
    }

    private static String key(Minecraft mc, BlockPos monitorPos) {
        if (mc.player == null || mc.level == null || monitorPos == null)
            return null;

        UUID playerId = mc.player.getUUID();
        ResourceLocation dimension = mc.level.dimension().location();
        return playerId + "|" + dimension + "|" + monitorPos.getX() + "," + monitorPos.getY() + "," + monitorPos.getZ();
    }
}
