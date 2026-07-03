package com.happysg.radar.compat.sable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class SableSilhouetteClientCache {
    private static final int PROJECTED_CACHE_TICKS = 2;

    private static final Map<UUID, Entry> CACHE = new HashMap<>();
    private static final Map<UUID, Long> LAST_REQUEST_TICK = new HashMap<>();

    private SableSilhouetteClientCache() {
    }

    public static boolean has(UUID id, int revision) {
        Entry entry = CACHE.get(id);
        return entry != null && entry.revision == revision && entry.silhouette != null;
    }

    public static SubLevelSilhouette get(UUID id, int revision) {
        Entry entry = CACHE.get(id);
        if (entry == null || entry.revision != revision) {
            return null;
        }
        return entry.silhouette;
    }

    public static void put(UUID id, int revision, SubLevelSilhouette silhouette) {
        CACHE.put(id, new Entry(revision, silhouette));
    }

    public static SubLevelSilhouette.ProjectedSilhouette getProjected(
            UUID id,
            int revision,
            long gameTime,
            SubLevelSilhouette.ProjectionSettings settings,
            Supplier<SubLevelSilhouette.ProjectedSilhouette> builder
    ) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(builder, "builder");

        Entry entry = CACHE.get(id);
        if (entry == null || entry.revision != revision || entry.silhouette == null) {
            return null;
        }

        ProjectedEntry projected = entry.projected;
        if (projected != null
                && gameTime - projected.gameTime <= PROJECTED_CACHE_TICKS
                && projected.settings.equals(settings)) {
            return projected.silhouette;
        }

        SubLevelSilhouette.ProjectedSilhouette rebuilt = builder.get();
        entry.projected = new ProjectedEntry(settings, gameTime, rebuilt);
        return rebuilt;
    }

    public static boolean shouldRequest(UUID id, int revision, long gameTime) {
        Entry entry = CACHE.get(id);
        if (entry != null && entry.revision == revision) {
            return false;
        }
        Long last = LAST_REQUEST_TICK.get(id);
        if (last != null && gameTime - last < 40) {
            return false;
        }
        LAST_REQUEST_TICK.put(id, gameTime);
        return true;
    }

    public static void clear() {
        CACHE.clear();
        LAST_REQUEST_TICK.clear();
    }

    private static final class Entry {
        private final int revision;
        private final SubLevelSilhouette silhouette;
        private ProjectedEntry projected;

        private Entry(int revision, SubLevelSilhouette silhouette) {
            this.revision = revision;
            this.silhouette = silhouette;
        }
    }

    private record ProjectedEntry(SubLevelSilhouette.ProjectionSettings settings, long gameTime,
                                  SubLevelSilhouette.ProjectedSilhouette silhouette) {
    }
}
