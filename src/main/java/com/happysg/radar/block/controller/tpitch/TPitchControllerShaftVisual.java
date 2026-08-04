package com.happysg.radar.block.controller.tpitch;

import com.happysg.radar.block.controller.kinetic.ControllerShaftVisual;
import com.simibubi.create.AllPartialModels;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;

public class TPitchControllerShaftVisual
        extends ControllerShaftVisual<TPitchControllerBlockEntity> {
    public TPitchControllerShaftVisual(VisualizationContext context,
                                       TPitchControllerBlockEntity blockEntity,
                                       float partialTick) {
        super(context, blockEntity, partialTick,
                Models.partial(AllPartialModels.SHAFT_HALF), true);
    }
}
