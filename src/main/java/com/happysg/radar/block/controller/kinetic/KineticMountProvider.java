package com.happysg.radar.block.controller.kinetic;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.Direction;

@FunctionalInterface
public interface KineticMountProvider {
    KineticMountAdapterResolution resolve(KineticBlockEntity controller, Direction.Axis axis);
}
