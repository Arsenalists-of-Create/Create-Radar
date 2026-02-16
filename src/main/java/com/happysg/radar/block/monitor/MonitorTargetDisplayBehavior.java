package com.happysg.radar.block.monitor;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.behavior.networks.config.TargetingConfig;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public class MonitorTargetDisplayBehavior extends DisplaySource {


    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        if (context.getSourceBlockEntity() instanceof MonitorBlockEntity monitor) {
                 Vec3i target = new Vec3i((int) monitor.getTargetPos(TargetingConfig.DEFAULT).x(), (int) monitor.getTargetPos(TargetingConfig.DEFAULT).y(), (int) monitor.getTargetPos(TargetingConfig.DEFAULT).z());
              return List.of(Component.translatable(CreateRadar.MODID + ".monitor.display.target" + " : (" + target.toShortString() + ")"));
        }
        return List.of();
    }

}
