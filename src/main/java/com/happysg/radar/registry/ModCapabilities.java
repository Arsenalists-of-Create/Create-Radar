package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.registry.ModBlocks;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = CreateRadar.MODID, value = Dist.CLIENT)
public final class ModCapabilities {
    private ModCapabilities() {}

    @SubscribeEvent
    public static void registerCaps(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                com.happysg.radar.registry.ModBlockEntityTypes.NETWORK_FILTER_BLOCK_ENTITY.get(),
                (NetworkFiltererBlockEntity be, @Nullable Direction side) -> be.getItemHandler()
        );
    }
}