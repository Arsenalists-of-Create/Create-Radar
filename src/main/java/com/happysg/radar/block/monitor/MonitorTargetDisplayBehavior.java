package com.happysg.radar.block.monitor;

import com.happysg.radar.CreateRadar;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public class MonitorTargetDisplayBehavior extends DisplaySource {


    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        if (context.getSourceBlockEntity() instanceof MonitorBlockEntity monitor) {
            Vec3i target = new Vec3i((int) monitor.getTargetPos().x(), (int) monitor.getTargetPos().y(), (int) monitor.getTargetPos().z());
            return List.of(Component.translatable(CreateRadar.MODID + ".monitor.display.target" + " : (" + target.toShortString() + ")"));
        }
        return List.of();
    }

}
