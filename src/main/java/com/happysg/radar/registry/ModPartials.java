package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;

public class ModPartials {

    public static final PartialModel RADAR_GLOW      = block("data_link/glow");
    public static final PartialModel RADAR_LINK_TUBE = block("data_link/tube");
    public static final PartialModel ROTATING_MOUNT  = block("rotating_mount");
    public static final PartialModel SKY_RADAR_TOP_SHAFT = block("sky_radar_top_shaft");

    private static PartialModel block(String path) {
        return PartialModel.of(CreateRadar.asResource("block/" + path));
    }


    public static void init() { /* load class */ }
}
