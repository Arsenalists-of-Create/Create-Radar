package com.happysg.radar.compat.computercraft;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.GenericPeripheral;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RadarBearingPeripheral implements GenericPeripheral {
    @Override
    public String id() {
        return CreateRadar.asResource("radar").toString();
    }

    @LuaFunction(mainThread = true)
    public static List<Map<? super String, Object>> getTracks(RadarBearingBlockEntity radarEntity){
        List<Map<? super String, Object>> tracks = new ArrayList<>();
        for (RadarTrack track : radarEntity.getTracks()) {
            HashMap<? super String, Object> map = new HashMap<>();
            map.put("position", getMapFromVector(track.position()));
            map.put("velocity", getMapFromVector(track.velocity()));
            map.put("category", track.trackCategory().toString());
            map.put("id", track.id());
            map.put("scannedTime", track.scannedTime());
            map.put("entityType", track.entityType());
            tracks.add(map);
        }
        return tracks;
    }

    @LuaFunction(mainThread = true)
    public static HashMap<String, Double> getPosition(RadarBearingBlockEntity radarEntity){
        return getMapFromVector(
                radarEntity.getWorldPos().getCenter()
        );
    }

    @LuaFunction(mainThread = true)
    public static double getRotation(RadarBearingBlockEntity radarEntity){
        return radarEntity.getAngle();
    }

    @LuaFunction(mainThread = true)
    public static double getRotationSpeed(RadarBearingBlockEntity radarEntity){
        return radarEntity.getAngularSpeed();
    }

    @LuaFunction(mainThread = true)
    public static double getRange(RadarBearingBlockEntity radarEntity) {
        return radarEntity.getRange();
    }

    @LuaFunction(mainThread = true)
    public static int getDishCount(RadarBearingBlockEntity radarEntity) {
        return radarEntity.getDishCount();
    }

    public static HashMap<String, Double> getMapFromVector(Vec3 vector) {
        HashMap<String, Double> vectorMap = new HashMap<>();
        if (vector == null) {
            vectorMap.put("x", 0.0);
            vectorMap.put("y", 0.0);
            vectorMap.put("z", 0.0);
            return vectorMap;
        }
        vectorMap.put("x", vector.x);
        vectorMap.put("y", vector.y);
        vectorMap.put("z", vector.z);
        return vectorMap;
    }
}