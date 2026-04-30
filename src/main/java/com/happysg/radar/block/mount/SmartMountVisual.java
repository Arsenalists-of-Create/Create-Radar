package com.happysg.radar.block.mount;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.instance.Instance;
import java.util.function.Consumer;

public class SmartMountVisual implements BlockEntityVisual<SmartMountBlockEntity> {
    public SmartMountVisual(VisualizationContext ctx, SmartMountBlockEntity be, float partialTicks) {}

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
    }

    @Override
    public void delete() {
    }

    @Override
    public void update(float partialTicks) {
    }
}
