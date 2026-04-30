package com.happysg.radar.block.arad.aradnetworks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.HolderLookup;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class RadarContactRegistryData extends SavedData {

    public static final int DEFAULT_IN_RANGE_TTL = 20; // 1s
    public static final int DEFAULT_LOCK_TTL = 10;     // 0.5s

    private final Map<Long, Entry> entries = new HashMap<>();

    public RadarContactRegistryData() {}

    public static RadarContactRegistryData get(net.minecraft.server.level.ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        RadarContactRegistryData::new,
                        RadarContactRegistryData::load,
                        net.minecraft.util.datafix.DataFixTypes.LEVEL
                ),
                "create_radar_contact_registry"
        );
    }

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

    public void markInRange(long shipId, int ttlTicks) {
        if (ttlTicks <= 0) ttlTicks = DEFAULT_IN_RANGE_TTL;
        Entry e = entries.get(shipId);
        if (e == null) {
            entries.put(shipId, new Entry(ttlTicks, 0));
        } else {
            e.inRangeTtl = Math.max(e.inRangeTtl, ttlTicks);
        }
        setDirty();
    }

    public void markLocked(long shipId, int ttlTicks) {
        if (ttlTicks <= 0) ttlTicks = DEFAULT_LOCK_TTL;
        Entry e = entries.get(shipId);
        if (e == null) {
            entries.put(shipId, new Entry(0, ttlTicks));
        } else {
            e.lockedTtl = Math.max(e.lockedTtl, ttlTicks);
        }
        setDirty();
    }

    public boolean isInRange(long shipId) {
        Entry e = entries.get(shipId);
        return e != null && e.inRangeTtl > 0;
    }

    public boolean isLocked(long shipId) {
        Entry e = entries.get(shipId);
        return e != null && e.lockedTtl > 0;
    }

    public RadarContactState getState(long shipId) {
        if (isLocked(shipId)) return RadarContactState.LOCKED;
        if (isInRange(shipId)) return RadarContactState.IN_RANGE;
        return null;
    }

    public void tickDecay() {
        if (entries.isEmpty()) return;
        boolean changed = false;
        Iterator<Map.Entry<Long, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Entry> me = it.next();
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

    public static final int DEFAULT_TTL_TICKS = DEFAULT_LOCK_TTL;

    public void lockShip(long shipId, int ttlTicks) { markLocked(shipId, ttlTicks); }

    public void unlockShip(long shipId) {
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

    public boolean isShipLocked(long shipId) { return isLocked(shipId); }

    public static RadarContactRegistryData load(CompoundTag tag, HolderLookup.Provider provider) {
        RadarContactRegistryData data = new RadarContactRegistryData();
        CompoundTag shipsTag = tag.getCompound("Ships");
        for (String key : shipsTag.getAllKeys()) {
            try {
                long shipId = Long.parseLong(key);
                CompoundTag eTag = shipsTag.getCompound(key);
                int inRange = eTag.getInt("InRange");
                int locked = eTag.getInt("Locked");
                if (inRange > 0 || locked > 0) {
                    data.entries.put(shipId, new Entry(inRange, locked));
                }
            } catch (NumberFormatException ignored) {}
        }
        if (tag.contains("LockedShips")) {
            CompoundTag lockedShips = tag.getCompound("LockedShips");
            for (String key : lockedShips.getAllKeys()) {
                try {
                    long shipId = Long.parseLong(key);
                    int ttl = lockedShips.getInt(key);
                    if (ttl > 0) {
                        Entry e = data.entries.get(shipId);
                        if (e == null) {
                            data.entries.put(shipId, new Entry(0, ttl));
                        } else {
                            e.lockedTtl = Math.max(e.lockedTtl, ttl);
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        CompoundTag shipsTag = new CompoundTag();
        for (var e : entries.entrySet()) {
            Entry entry = e.getValue();
            if (entry.inRangeTtl <= 0 && entry.lockedTtl <= 0) continue;
            CompoundTag eTag = new CompoundTag();
            eTag.putInt("InRange", entry.inRangeTtl);
            eTag.putInt("Locked", entry.lockedTtl);
            shipsTag.put(Long.toString(e.getKey()), eTag);
        }
        tag.put("Ships", shipsTag);
        return tag;
    }
}