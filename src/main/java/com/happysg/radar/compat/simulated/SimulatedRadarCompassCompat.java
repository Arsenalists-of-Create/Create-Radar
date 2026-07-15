package com.happysg.radar.compat.simulated;

import dev.simulated_team.simulated.index.SimDataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Isolates references to Simulated so radar compasses still work when it is absent.
 */
public final class SimulatedRadarCompassCompat {
    private SimulatedRadarCompassCompat() {
    }

    public static void clearPhysicalLodestoneTracker(ItemStack stack) {
        stack.remove(SimDataComponents.LODESTONE_COMPASS_SUBLEVEL_TRACKER);
    }
}
