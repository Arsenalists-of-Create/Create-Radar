package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.jozufozu.flywheel.core.PartialModel;


public class ModPartials {

    public static final PartialModel RADAR_GLOW      = block("data_link/glow");
    public static final PartialModel RADAR_LINK_TUBE = block("data_link/tube");

    private static PartialModel block(String path) {
        return new PartialModel(CreateRadar.asResource("block/" + path));
    }



    public static void init() { /* load class */ }
}
