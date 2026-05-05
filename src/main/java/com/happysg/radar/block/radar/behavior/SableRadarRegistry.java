package com.happysg.radar.block.radar.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global registry that lets monitors find radar block-entities that live
 * inside Sable sub-levels (ships), where a normal level.getBlockEntity()
 * lookup from the parent world returns null.
 *
 * Radars register themselves on initialise/tick and unregister on removal.
 */
public class SableRadarRegistry {

    private static final Map<ResourceLocation, Map<BlockPos, IRadar>> REGISTRY = new ConcurrentHashMap<>();

    public static void register(ResourceLocation dim, BlockPos localPos, IRadar radar) {
        REGISTRY.computeIfAbsent(dim, k -> new ConcurrentHashMap<>()).put(localPos, radar);
    }

    public static void unregister(ResourceLocation dim, BlockPos localPos) {
        Map<BlockPos, IRadar> shipRegistry = REGISTRY.get(dim);
        if (shipRegistry != null) {
            shipRegistry.remove(localPos);
            if (shipRegistry.isEmpty()) {
                REGISTRY.remove(dim);
            }
        }
    }

    /**
     * Legacy unregister for backward compatibility if needed, 
     * but we should prefer the dimension-scoped one.
     */
    public static void unregister(BlockPos localPos) {
        REGISTRY.values().forEach(map -> map.remove(localPos));
    }

    /**
     * Returns the IRadar registered at the given local sub-level position in the specified dimension.
     */
    public static IRadar get(ResourceLocation dim, BlockPos localPos) {
        Map<BlockPos, IRadar> shipRegistry = REGISTRY.get(dim);
        return shipRegistry != null ? shipRegistry.get(localPos) : null;
    }

    /**
     * Legacy get for backward compatibility. Searches all ships.
     */
    public static IRadar get(BlockPos localPos) {
        for (Map<BlockPos, IRadar> map : REGISTRY.values()) {
            IRadar radar = map.get(localPos);
            if (radar != null) return radar;
        }
        return null;
    }
}
