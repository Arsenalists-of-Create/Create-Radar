package com.happysg.radar.block.controller.id;

import org.valkyrienskies.core.api.ships.Ship;
import java.util.*;

public class IDManager {
    public static final Map<String, IDRecord> ID_RECORDS_BY_SECRET = new HashMap<>();
    public static final Map<Long, IDRecord> ID_RECORDS_BY_SHIP_ID = new HashMap<>();

    public static void addIDRecord(long idInt, String secretID, String name) {
        IDRecord record = new IDRecord(idInt, secretID, name);
        ID_RECORDS_BY_SECRET.put(secretID, record);
        ID_RECORDS_BY_SHIP_ID.put(idInt, record);
    }

    public static IDRecord getIDRecordByShipId(long idInt) {
        return ID_RECORDS_BY_SHIP_ID.get(idInt);
    }

    public static IDRecord getIDRecordByShip(Ship ship) {
        if (ship == null) return null;
        return getIDRecordByShipId(ship.getId());
    }

    public static void removeIDRecord(long shipId) {
        IDRecord record = ID_RECORDS_BY_SHIP_ID.remove(shipId);
        if (record != null) {
            ID_RECORDS_BY_SECRET.remove(record.secretID());
        }
    }

    public static List<IDRecord> getRecords() {
        return new ArrayList<>(ID_RECORDS_BY_SECRET.values());
    }

    public static void setRecords(List<IDRecord> records) {
        ID_RECORDS_BY_SECRET.clear();
        ID_RECORDS_BY_SHIP_ID.clear();
        for (IDRecord r : records) {
            ID_RECORDS_BY_SECRET.put(r.secretID(), r);
            ID_RECORDS_BY_SHIP_ID.put(r.idInt(), r);
        }
    }

    public static void load(net.minecraft.server.MinecraftServer server) {
        // Future: Load from world save data if needed
    }
}