package com.happysg.radar.block.controller.tpitch;

import com.happysg.radar.block.controller.kinetic.ControllerShaftRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class TPitchControllerShaftRenderer
        extends ControllerShaftRenderer<TPitchControllerBlockEntity> {
    public TPitchControllerShaftRenderer(
            BlockEntityRendererProvider.Context context) {
        super(context, true);
    }
}
