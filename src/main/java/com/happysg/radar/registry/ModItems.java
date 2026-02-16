package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.item.binos.Binoculars;
import com.happysg.radar.item.SafeZoneDesignatorItem;
import com.happysg.radar.item.detectionfilter.DetectionFilterItem;
import com.happysg.radar.item.identfilter.IdentFilterItem;
import com.happysg.radar.item.targetfilter.TargetFilterItem;
import com.tterrag.registrate.util.entry.ItemEntry;

import static com.happysg.radar.CreateRadar.REGISTRATE;

public class ModItems {

    public static final ItemEntry<SafeZoneDesignatorItem> SAFE_ZONE_DESIGNATOR = REGISTRATE.item("radar_safe_zone_designator", SafeZoneDesignatorItem::new).register();
    public static final ItemEntry<DetectionFilterItem> RADAR_FILTER_ITEM = REGISTRATE.item("radar_filter_item", DetectionFilterItem::new ).register();
    public static final ItemEntry<IdentFilterItem> IDENT_FILTER_ITEM = REGISTRATE.item("ident_filter_item",IdentFilterItem::new).register();
    public static final ItemEntry<TargetFilterItem> TARGET_FILTER_ITEM = REGISTRATE.item("target_filter_item", TargetFilterItem::new).register();
    public static final ItemEntry<Binoculars> BINOCULARS = REGISTRATE.item("binoculars", Binoculars::new).register();

    public static void register() {
        CreateRadar.getLogger().info("Registering Items!");
    }
}
