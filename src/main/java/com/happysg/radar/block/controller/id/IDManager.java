package com.happysg.radar.block.controller.id;

import org.valkyrienskies.core.api.ships.Ship;
import java.util.*;

public class IDManager {
    public static final Map<String, IDRecord> ID_RECORDS = new HashMap<>();

    public static void addIDRecord(long idInt, String secretID, String name) {
        ID_RECORDS.put(secretID, new IDRecord(idInt, secretID, name));
    }
    public static IDRecord getIDRecordByShipId(long idInt) { return null; }
    public static IDRecord getIDRecordByShip(Ship ship) { return null; }
    public static void removeIDRecord(Object ship) {}
    public static List<IDRecord> getRecords() { return new ArrayList<>(ID_RECORDS.values()); }
    public static void setRecords(List<IDRecord> records) {
        ID_RECORDS.clear();
        for (IDRecord r : records) ID_RECORDS.put(r.secretID(), r);
    }
    public static void load(net.minecraft.server.MinecraftServer server) {}
}