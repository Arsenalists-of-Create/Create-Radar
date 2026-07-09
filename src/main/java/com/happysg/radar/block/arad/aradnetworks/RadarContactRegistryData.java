package com.happysg.radar.block.arad.aradnetworks;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RadarContactRegistryData extends SavedData {

    public static final int DEFAULT_IN_RANGE_TTL = 20; // 1s
    public static final int DEFAULT_LOCK_TTL = 10;     // 0.5s
    public static final int DEFAULT_ENGAGED_TTL = 20;  // 1s

    private static final String DATA_NAME = "create_radar_contact_registry";
    private static final String LEGACY_IN_RANGE_SOURCE = "legacy";

    private final Map<UUID, Entry> entries = new HashMap<>();

    // ===== access =====

    public static RadarContactRegistryData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(
                        RadarContactRegistryData::new,
                        RadarContactRegistryData::load,
                        null
                ),
                DATA_NAME
        );
    }

    // ===== model =====

    public enum RadarContactState {
        ENGAGED,
        IN_RANGE,
        LOCKED
    }

    public static class Entry {
        public final Map<String, Integer> inRangeSources = new HashMap<>();
        public final Map<String, Integer> inRangeSignals = new HashMap<>();
        public int lockedTtl;
        public int engagedTtl;

        public Entry(int inRangeTtl, int lockedTtl) {
            this.lockedTtl = lockedTtl;
            if (inRangeTtl > 0) {
                this.inRangeSources.put(LEGACY_IN_RANGE_SOURCE, inRangeTtl);
                this.inRangeSignals.put(LEGACY_IN_RANGE_SOURCE, 1);
            }
        }
    }

    // ===== core API (range/lock) =====

    // i call this any tick a target is within detection range
    public void markInRange(UUID shipId, int ttlTicks) {
        markInRange(shipId, LEGACY_IN_RANGE_SOURCE, ttlTicks, 1);
    }

    public void markInRange(UUID shipId, String sourceId, int ttlTicks) {
        markInRange(shipId, sourceId, ttlTicks, 1);
    }

    public void markInRange(UUID shipId, String sourceId, int ttlTicks, int signalStrength) {
        if (ttlTicks <= 0) ttlTicks = DEFAULT_IN_RANGE_TTL;
        if (sourceId == null || sourceId.isBlank()) sourceId = LEGACY_IN_RANGE_SOURCE;
        signalStrength = Math.max(1, Math.min(14, signalStrength));

        Entry e = entries.get(shipId);
        if (e == null) {
            e = new Entry(0, 0);
            entries.put(shipId, e);
        }

        int currentTtl = e.inRangeSources.getOrDefault(sourceId, 0);
        Integer currentSignal = e.inRangeSignals.get(sourceId);
        if (ttlTicks > currentTtl) {
            e.inRangeSources.put(sourceId, ttlTicks);
            e.inRangeSignals.put(sourceId, signalStrength);
            setDirty();
        } else if (currentSignal == null || currentSignal != signalStrength) {
            e.inRangeSignals.put(sourceId, signalStrength);
            setDirty();
        }
    }

    public Set<String> getInRangeSources(UUID shipId) {
        Entry e = entries.get(shipId);
        if (e == null || e.inRangeSources.isEmpty()) {
            return Set.of();
        }

        return new HashSet<>(e.inRangeSources.keySet());
    }

    public void removeInRangeSource(UUID shipId, String sourceId) {
        Entry e = entries.get(shipId);
        if (e == null || sourceId == null) {
            return;
        }

        boolean removed = e.inRangeSources.remove(sourceId) != null;
        e.inRangeSignals.remove(sourceId);

        if (!removed) {
            return;
        }

        if (e.inRangeSources.isEmpty() && e.lockedTtl <= 0 && e.engagedTtl <= 0) {
            entries.remove(shipId);
        }
        setDirty();
    }

    public int getInRangeSourceCount(UUID shipId) {
        Entry e = entries.get(shipId);
        return e == null ? 0 : e.inRangeSources.size();
    }

    public int getInRangeSignal(UUID shipId) {
        Entry e = entries.get(shipId);
        if (e == null || e.inRangeSources.isEmpty()) {
            return 0;
        }

        int strongest = 0;
        for (String sourceId : e.inRangeSources.keySet()) {
            strongest = Math.max(strongest, e.inRangeSignals.getOrDefault(sourceId, 1));
        }
        return strongest;
    }

    public boolean hasInRangeSources(UUID shipId) {
        Entry e = entries.get(shipId);
        return e != null && !e.inRangeSources.isEmpty();
    }

    // i call this any tick a target is actively locked
    public void markLocked(UUID shipId, int ttlTicks) {
        if (ttlTicks <= 0) ttlTicks = DEFAULT_LOCK_TTL;

        Entry e = entries.get(shipId);
        if (e == null) {
            entries.put(shipId, new Entry(0, ttlTicks));
        } else {
            e.lockedTtl = Math.max(e.lockedTtl, ttlTicks);
        }

        setDirty();
    }

    public void markEngaged(UUID shipId, int ttlTicks) {
        if (ttlTicks <= 0) ttlTicks = DEFAULT_ENGAGED_TTL;

        Entry e = entries.get(shipId);
        if (e == null) {
            e = new Entry(0, 0);
            entries.put(shipId, e);
        }

        if (ttlTicks > e.engagedTtl) {
            e.engagedTtl = ttlTicks;
            setDirty();
        }
    }

    public boolean isInRange(UUID shipId) {
        return hasInRangeSources(shipId);
    }

    public boolean isLocked(UUID shipId) {
        Entry e = entries.get(shipId);
        return e != null && e.lockedTtl > 0;
    }

    public boolean isEngaged(UUID shipId) {
        Entry e = entries.get(shipId);
        return e != null && e.engagedTtl > 0;
    }

    // highest state wins
    public RadarContactState getState(UUID shipId) {
        if (isEngaged(shipId)) return RadarContactState.ENGAGED;
        if (isLocked(shipId)) return RadarContactState.LOCKED;
        if (isInRange(shipId)) return RadarContactState.IN_RANGE;
        return null;
    }

    public void tickDecay() {
        if (entries.isEmpty()) return;

        boolean changed = false;
        Iterator<Map.Entry<UUID, Entry>> it = entries.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, Entry> me = it.next();
            Entry e = me.getValue();

            Iterator<Map.Entry<String, Integer>> sourceIt = e.inRangeSources.entrySet().iterator();
            while (sourceIt.hasNext()) {
                Map.Entry<String, Integer> source = sourceIt.next();
                int ttl = source.getValue() - 1;
                if (ttl <= 0) {
                    sourceIt.remove();
                    e.inRangeSignals.remove(source.getKey());
                } else {
                    source.setValue(ttl);
                }
            }
            if (e.lockedTtl > 0) e.lockedTtl--;
            if (e.engagedTtl > 0) e.engagedTtl--;

            if (e.inRangeSources.isEmpty() && e.lockedTtl <= 0 && e.engagedTtl <= 0) {
                it.remove();
            }

            changed = true;
        }

        if (changed) setDirty();
    }

    // ===== LockRegistryData compatibility API =====

    public static final int DEFAULT_TTL_TICKS = DEFAULT_LOCK_TTL;

    public void lockShip(UUID shipId, int ttlTicks) {
        markLocked(shipId, ttlTicks);
    }

    public void unlockShip(UUID shipId) {
        Entry e = entries.get(shipId);
        if (e == null) return;

        if (e.lockedTtl != 0) {
            e.lockedTtl = 0;
            if (e.inRangeSources.isEmpty() && e.engagedTtl <= 0) {
                entries.remove(shipId);
            }
            setDirty();
        }
    }

    public boolean isShipLocked(UUID shipId) {
        return isLocked(shipId);
    }

    // ===== persistence =====

    public static RadarContactRegistryData load(CompoundTag tag, HolderLookup.Provider registries) {
        RadarContactRegistryData data = new RadarContactRegistryData();

        CompoundTag shipsTag = tag.getCompound("Ships");
        for (String key : shipsTag.getAllKeys()) {
            try {
                UUID shipId = UUID.fromString(key);
                CompoundTag eTag = shipsTag.getCompound(key);
                int inRange = eTag.getInt("InRange");
                int locked = eTag.getInt("Locked");
                int engaged = eTag.getInt("Engaged");

                if (inRange > 0 || locked > 0 || engaged > 0) {
                    Entry entry = new Entry(0, locked);
                    entry.engagedTtl = engaged;
                    CompoundTag sourcesTag = eTag.getCompound("InRangeSources");
                    CompoundTag signalsTag = eTag.getCompound("InRangeSignals");
                    for (String sourceId : sourcesTag.getAllKeys()) {
                        int sourceTtl = sourcesTag.getInt(sourceId);
                        if (sourceTtl > 0) {
                            entry.inRangeSources.put(sourceId, sourceTtl);
                            int signal = signalsTag.contains(sourceId) ? signalsTag.getInt(sourceId) : 1;
                            entry.inRangeSignals.put(sourceId, Math.max(1, Math.min(14, signal)));
                        }
                    }
                    if (entry.inRangeSources.isEmpty() && inRange > 0) {
                        entry.inRangeSources.put(LEGACY_IN_RANGE_SOURCE, inRange);
                        entry.inRangeSignals.put(LEGACY_IN_RANGE_SOURCE, 1);
                    }
                    data.entries.put(shipId, entry);
                }
            } catch (NumberFormatException ignored) {
                // i ignore invalid keys
            }
        }

        if (tag.contains("LockedShips")) {
            CompoundTag lockedShips = tag.getCompound("LockedShips");
            for (String key : lockedShips.getAllKeys()) {
                try {
                    UUID shipId = UUID.fromString(key);
                    int ttl = lockedShips.getInt(key);
                    if (ttl > 0) {
                        Entry e = data.entries.get(shipId);
                        if (e == null) {
                            data.entries.put(shipId, new Entry(0, ttl));
                        } else {
                            e.lockedTtl = Math.max(e.lockedTtl, ttl);
                        }
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag shipsTag = new CompoundTag();

        for (var e : entries.entrySet()) {
            Entry entry = e.getValue();
            if (entry.inRangeSources.isEmpty() && entry.lockedTtl <= 0 && entry.engagedTtl <= 0) continue;

            CompoundTag eTag = new CompoundTag();
            eTag.putInt("InRange", entry.inRangeSources.values().stream().mapToInt(Integer::intValue).max().orElse(0));
            CompoundTag sourcesTag = new CompoundTag();
            CompoundTag signalsTag = new CompoundTag();
            for (Map.Entry<String, Integer> source : entry.inRangeSources.entrySet()) {
                if (source.getValue() > 0) {
                    sourcesTag.putInt(source.getKey(), source.getValue());
                    signalsTag.putInt(source.getKey(), entry.inRangeSignals.getOrDefault(source.getKey(), 1));
                }
            }
            eTag.put("InRangeSources", sourcesTag);
            eTag.put("InRangeSignals", signalsTag);
            eTag.putInt("Locked", entry.lockedTtl);
            eTag.putInt("Engaged", entry.engagedTtl);
            shipsTag.put(e.getKey().toString(), eTag);
        }

        tag.put("Ships", shipsTag);

        return tag;
    }
}
