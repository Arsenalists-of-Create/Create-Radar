package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.item.GuidedFuzeItem;
import com.happysg.radar.item.binos.Binoculars;
import com.happysg.radar.item.SafeZoneDesignatorItem;
import com.happysg.radar.item.detectionfilter.DetectionFilterItem;
import com.happysg.radar.item.identfilter.IdentFilterItem;
import com.happysg.radar.item.radarproxfuze.AdvancedProximityFuze;
import com.happysg.radar.item.targetfilter.TargetFilterItem;
import com.tterrag.registrate.util.entry.ItemEntry;

import static com.happysg.radar.CreateRadar.REGISTRATE;

public class ModItems {

    public static final ItemEntry<SafeZoneDesignatorItem> SAFE_ZONE_DESIGNATOR = REGISTRATE.item("radar_safe_zone_designator", SafeZoneDesignatorItem::new)
            .register();
    public static final ItemEntry<DetectionFilterItem> RADAR_FILTER_ITEM = REGISTRATE.item("radar_filter_item", DetectionFilterItem::new )
            .model((c, p) -> p.generated(c, CreateRadar.asResource("item/detection_filter_item")))
            .register();
    public static final ItemEntry<IdentFilterItem> IDENT_FILTER_ITEM = REGISTRATE.item("ident_filter_item",IdentFilterItem::new)
            .model((c, p) -> p.generated(c, CreateRadar.asResource("item/identification_filter")))
            .register();
    public static final ItemEntry<TargetFilterItem> TARGET_FILTER_ITEM = REGISTRATE.item("target_filter_item", TargetFilterItem::new)
            .model((c, p) -> p.generated(c, CreateRadar.asResource("item/targeting_filter_item")))
            .register();
    public static final ItemEntry<Binoculars> BINOCULARS =
            REGISTRATE.item("binoculars", p -> new Binoculars(p.stacksTo(1)))
                    .register();
    public static final ItemEntry<GuidedFuzeItem> GUIDED_FUZE = REGISTRATE.item("guided_fuze", GuidedFuzeItem::new)
            .register();
    public static final ItemEntry<AdvancedProximityFuze> ADVANCED_PROXY_FUZE = REGISTRATE.item("advanced_proxy_fuze", AdvancedProximityFuze::new)
            .register();

    public static void register() {
        CreateRadar.getLogger().info("Registering Items!");
    }
}
