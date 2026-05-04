package com.happysg.radar.block.arad.aradnetworks;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class RadarContactRegistryData extends SavedData {

    public static final int DEFAULT_IN_RANGE_TTL = 20; // 1s
    public static final int DEFAULT_LOCK_TTL = 10;     // 0.5s

    private static final String DATA_NAME = "create_radar_contact_registry";

    private final Map<UUID, Entry> entries = new HashMap<>();

    // ===== access =====

    public static RadarContactRegistryData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        RadarContactRegistryData::new,
                        RadarContactRegistryData::load,
                        null
                ),
                DATA_NAME
        );
    }

    // ===== model =====

    public enum RadarContactState {
        IN_RANGE,
        LOCKED
    }

    public static class Entry {
        public int inRangeTtl;
        public int lockedTtl;

        public Entry(int inRangeTtl, int lockedTtl) {
            this.inRangeTtl = inRangeTtl;
            this.lockedTtl = lockedTtl;
        }
    }

    // ===== core API (range/lock) =====

    // i call this any tick a target is within detection range
    public void markInRange(UUID shipId, int ttlTicks) {
        if (ttlTicks <= 0) ttlTicks = DEFAULT_IN_RANGE_TTL;

        Entry e = entries.get(shipId);
        if (e == null) {
            entries.put(shipId, new Entry(ttlTicks, 0));
        } else {
            e.inRangeTtl = Math.max(e.inRangeTtl, ttlTicks);
        }

        setDirty();
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

    public boolean isInRange(UUID shipId) {
        Entry e = entries.get(shipId);
        return e != null && e.inRangeTtl > 0;
    }

    public boolean isLocked(UUID shipId) {
        Entry e = entries.get(shipId);
        return e != null && e.lockedTtl > 0;
    }

    // highest state wins
    public RadarContactState getState(UUID shipId) {
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

            if (e.inRangeTtl > 0) e.inRangeTtl--;
            if (e.lockedTtl > 0) e.lockedTtl--;

            if (e.inRangeTtl <= 0 && e.lockedTtl <= 0) {
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
            if (e.inRangeTtl <= 0) {
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

                if (inRange > 0 || locked > 0) {
                    data.entries.put(shipId, new Entry(inRange, locked));
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
            if (entry.inRangeTtl <= 0 && entry.lockedTtl <= 0) continue;

            CompoundTag eTag = new CompoundTag();
            eTag.putInt("InRange", entry.inRangeTtl);
            eTag.putInt("Locked", entry.lockedTtl);
            shipsTag.put(e.getKey().toString(), eTag);
        }

        tag.put("Ships", shipsTag);

        return tag;
    }
}