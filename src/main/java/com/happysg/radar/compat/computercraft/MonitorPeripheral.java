package com.happysg.radar.compat.computercraft;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.GenericPeripheral;

import java.util.*;

public class MonitorPeripheral implements GenericPeripheral {
    @Override
    public String id() {
        return CreateRadar.asResource("monitor").toString();
    }
    @LuaFunction(mainThread = true)
    public static String getSelectedTrackId(MonitorBlockEntity monitorEntity){
        MonitorBlockEntity controller = monitorEntity.getController();
        if (controller == null) return "";
        String id = controller.getSelectedEntity();
        return id == null ? "" : id;
    }

    @LuaFunction(mainThread = true)
    public static List<Map<? super String, Object>> getTracks(MonitorBlockEntity monitorEntity){
        MonitorBlockEntity controller = monitorEntity.getController();
        if (controller == null) return new ArrayList<>();

        List<Map<? super String, Object>> tracks = new ArrayList<>();
        var controllerTracks = controller.getTracks();
        if (controllerTracks == null) return tracks;

        for (RadarTrack track : controllerTracks) {
            if (track == null) continue;

            HashMap<? super String, Object> map = new HashMap<>();
            map.put("position", RadarBearingPeripheral.getMapFromVector(track.position()));
            map.put("velocity", RadarBearingPeripheral.getMapFromVector(track.velocity()));
            map.put("category", track.trackCategory() == null ? "" : track.trackCategory().toString());
            map.put("id", track.id() == null ? "" : track.id());
            map.put("scannedTime", track.scannedTime());
            map.put("entityType", track.entityType() == null ? "" : track.entityType());
            tracks.add(map);
        }
        return tracks;
    }

    @LuaFunction(mainThread = true)
    public static Map<? super String, Object> getSelectedTrack(MonitorBlockEntity monitorEntity) {
        MonitorBlockEntity controller = monitorEntity.getController();
        if (controller == null) return new HashMap<>();

        var controllerTracks = controller.getTracks();
        if (controllerTracks == null) return new HashMap<>();

        String selectedId = controller.getSelectedEntity();
        if (selectedId == null || selectedId.isEmpty()) return new HashMap<>();

        RadarTrack selectedTrack = null;
        for (RadarTrack track : controllerTracks) {
            if (track == null) continue;
            if (Objects.equals(track.id(), selectedId)) {
                selectedTrack = track;
                break;
            }
        }

        if (selectedTrack == null) return new HashMap<>();

        HashMap<? super String, Object> map = new HashMap<>();
        map.put("position", RadarBearingPeripheral.getMapFromVector(selectedTrack.position()));
        map.put("velocity", RadarBearingPeripheral.getMapFromVector(selectedTrack.velocity()));
        map.put("category", selectedTrack.trackCategory() == null ? "" : selectedTrack.trackCategory().toString());
        map.put("id", selectedTrack.id() == null ? "" : selectedTrack.id());
        map.put("scannedTime", selectedTrack.scannedTime());
        map.put("entityType", selectedTrack.entityType() == null ? "" : selectedTrack.entityType());
        return map;
    }

    public static List<String> optStringList(Map<? super String, ?> map, String key) {
        if (!map.containsKey(key)) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        Map<?, ?> list = (Map<?, ?>) map.get(key);
        for (Object k: list.values()) {
            if (k instanceof String) {
                out.add((String) k);
            }
        }
        return out;
    }


}