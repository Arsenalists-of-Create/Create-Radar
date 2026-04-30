package com.happysg.radar.compat.computercraft;

import com.happysg.radar.CreateRadar;
import dan200.computercraft.api.ComputerCraftAPI;

public class CCCompatRegister {
    public static void registerPeripherals(){
        CreateRadar.getLogger().info("Registering Peripherals!");
        ComputerCraftAPI.registerGenericSource(new RadarBearingPeripheral());
        ComputerCraftAPI.registerGenericSource(new MonitorPeripheral());
        ComputerCraftAPI.registerGenericSource(new YawControllerPeripheral());
        ComputerCraftAPI.registerGenericSource(new PitchControllerPeripheral());
        ComputerCraftAPI.registerGenericSource(new FireControllerPeripheral());
        ComputerCraftAPI.registerGenericSource(new PlaneRadarPeripheral());

    }
}