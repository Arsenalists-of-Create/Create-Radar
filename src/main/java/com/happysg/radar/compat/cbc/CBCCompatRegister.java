package com.happysg.radar.compat.cbc;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.item.GuidedFuzeItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public class CBCCompatRegister {
    public static ItemEntry<? extends Item> GUIDED_FUZE;

    public static void registerCBC() {
        CreateRadar.getLogger().info("Registering CBC Compat Items!");
        GUIDED_FUZE = CreateRadar.REGISTRATE
                .item("guided_fuze", GuidedFuzeItem::new)
                .properties(properties -> properties.rarity(Rarity.EPIC))
                .register();
    }
}