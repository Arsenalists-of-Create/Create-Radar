package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.monitor.MonitorTargetDisplayBehavior;

import com.simibubi.create.content.redstone.displayLink.AllDisplayBehaviours;
import com.simibubi.create.content.redstone.displayLink.DisplayBehaviour;
import com.simibubi.create.content.redstone.displayLink.source.DisplaySource;
import net.minecraft.world.level.block.entity.BlockEntityType;

import static com.simibubi.create.content.redstone.displayLink.AllDisplayBehaviours.assignBlockEntity;

public class ModDisplayBehaviors {

    public static void register(String id, DisplayBehaviour behaviour, BlockEntityType<?> be) {
        assignBlockEntity(AllDisplayBehaviours.register(CreateRadar.asResource(id), behaviour), be);
    }

    public static void register() {
        CreateRadar.getLogger().info("Registering Display Sources!");
        register("monitor", new MonitorTargetDisplayBehavior(), ModBlockEntityTypes.MONITOR.get());
    }
}
