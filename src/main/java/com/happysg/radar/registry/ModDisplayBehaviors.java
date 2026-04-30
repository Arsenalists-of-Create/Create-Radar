package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.monitor.MonitorTargetDisplayBehavior;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModDisplayBehaviors {

    public static void register(String id, DisplaySource source, BlockEntityType<?> be) {
        CreateRadar.REGISTRATE
                .displaySource(id, () -> source)
                .associate(be)
                .register();
    }

    public static void register() {
        CreateRadar.getLogger().info("Registering Display Sources!");
        register("monitor", new MonitorTargetDisplayBehavior(), ModBlockEntityTypes.MONITOR.get());
    }
}