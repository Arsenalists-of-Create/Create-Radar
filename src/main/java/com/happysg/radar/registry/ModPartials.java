package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;

public class ModPartials {

    public static final PartialModel RADAR_GLOW      = block("data_link/glow");
    public static final PartialModel RADAR_LINK_TUBE = block("data_link/tube");

    private static PartialModel block(String path) {
        return PartialModel.of(CreateRadar.asResource("block/" + path));
    }


    public static void init() { /* load class */ }
}