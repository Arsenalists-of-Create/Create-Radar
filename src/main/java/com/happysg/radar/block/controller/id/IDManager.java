package com.happysg.radar.block.controller.id;

import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IDManager extends SavedData {

    private static final String DATA_NAME = "create_radar_vs2_ids";

    public static final IDManager INSTANCE = new IDManager();

    // i key records by ship ID (long)
    public static final Map<UUID, IDRecord> ID_RECORDS = new HashMap<>();

    public record IDRecord(String name, String secretID) {}

    // i save secretID to shipId, and i store the slug as the name
    public static void addIDRecord(UUID shipId, String secretID, String shipSlugAsName) {
        ID_RECORDS.put(shipId, new IDRecord(shipSlugAsName, secretID));
        INSTANCE.setDirty();
    }

    public static void registerIDRecord(SubLevelAccess ship, String secretID) {
        addIDRecord(ship.getUniqueId(), secretID, ship.getUniqueId().toString());
    }

    public static void removeIDRecord(SubLevelAccess ship) {
        ID_RECORDS.remove(ship.getUniqueId());
        INSTANCE.setDirty();
    }

    public static IDRecord getIDRecordByShip(SubLevelAccess ship) {
        return ID_RECORDS.get(ship.getUniqueId());
    }

    public static IDRecord getIDRecordByShipId(UUID shipId) {
        return ID_RECORDS.get(shipId);
    }

    public static IDManager load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("idRecords")) return INSTANCE;

        ListTag list = tag.getList("idRecords", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            UUID shipId = c.getUUID("shipId");
            String name = c.getString("name");       // now slug
            String secretID = c.getString("secretID");
            ID_RECORDS.put(shipId, new IDRecord(name, secretID));
        }

        return INSTANCE;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();

        for (Map.Entry<UUID, IDRecord> e : ID_RECORDS.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("shipId", e.getKey());
            c.putString("name", e.getValue().name());         // slug stored here
            c.putString("secretID", e.getValue().secretID());
            list.add(c);
        }

        tag.put("idRecords", list);
        return tag;
    }

    public static void load(MinecraftServer server) {
        server.overworld()
                .getDataStorage()
                .computeIfAbsent(
                        new SavedData.Factory<>(
                                () -> INSTANCE,
                                IDManager::load,
                                null
                        ),
                        DATA_NAME
                );
    }
}
