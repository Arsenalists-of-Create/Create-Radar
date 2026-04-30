package com.happysg.radar.compat.cbcwpf;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.compat.Mods;

public class CBCWPFCompatRegister {
    public static void registerCBCWPF() {
        if (!Mods.SHUPAPIUM.isLoaded()) return;
        CreateRadar.getLogger().info("Registering CBC WPF");
    }
}