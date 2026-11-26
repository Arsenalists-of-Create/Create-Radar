package com.happysg.radar.compat.cbc;

import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;

public class CBCCompatRegister {
    public static ItemEntry<? extends Item> GUIDED_FUZE;

    public static void registerCBC() {
        CbcRadarWeaponRegistration.register();
        CbcShotProfileRegistration.register();
    }
}
