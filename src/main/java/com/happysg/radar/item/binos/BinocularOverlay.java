package com.happysg.radar.item.binos;

import com.happysg.radar.CreateRadar;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(value = Dist.CLIENT, modid = CreateRadar.MODID)
public class BinocularOverlay {

    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            if (isScoping()) {
                event.setCanceled(true);
            }
        }
    }

    private static boolean isScoping() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getMainHandItem().getItem() instanceof Binoculars && mc.options.getCameraType().isFirstPerson();
    }
}