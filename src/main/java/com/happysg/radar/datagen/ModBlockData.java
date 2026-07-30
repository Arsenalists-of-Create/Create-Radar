package com.happysg.radar.datagen;

import com.happysg.radar.registry.ModBlocks;
import com.tterrag.registrate.util.entry.BlockEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Block;

public final class ModBlockData {
    public static List<BlockEntry<? extends Block>> toolAndLootBlocks() {
        List<BlockEntry<? extends Block>> blocks = new ArrayList<>(List.of(
                ModBlocks.MONITOR,
                ModBlocks.RADAR_LINK,
                ModBlocks.RADAR_BEARING_BLOCK,
                ModBlocks.RADAR_RECEIVER_BLOCK,
                ModBlocks.RADAR_DISH_BLOCK,
                ModBlocks.RADAR_PLATE_BLOCK,
                ModBlocks.CREATIVE_RADAR_PLATE_BLOCK,
                ModBlocks.AUTO_YAW_CONTROLLER_BLOCK,
                ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK,
                ModBlocks.T_PITCH,
                ModBlocks.FIRE_CONTROLLER_BLOCK,
                ModBlocks.NETWORK_FILTERER_BLOCK,
                ModBlocks.STATIONARY_RADAR,
                ModBlocks.ID_BLOCK,
                ModBlocks.RWR_BLOCK
        ));

        return blocks;
    }

    private ModBlockData() {
    }
}
