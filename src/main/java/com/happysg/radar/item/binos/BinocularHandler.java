package com.happysg.radar.item.binos;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.networking.ModMessages;
import com.happysg.radar.networking.packets.FirePacket;
import com.happysg.radar.networking.packets.RaycastPacket;
import com.happysg.radar.registry.ModKeybinds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = CreateRadar.MODID)
public class BinocularHandler {

    private static boolean wasActionPressed = false;
    private static boolean wasFirePressed = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!(mc.player.getMainHandItem().getItem() instanceof Binoculars)) return;

        boolean isActionPressed = ModKeybinds.SCOPE_ACTION.isDown();
        if (isActionPressed != wasActionPressed) {
            ModMessages.sendToServer(new FirePacket(isActionPressed));
            wasActionPressed = isActionPressed;
        }

        if (isActionPressed) {
            HitResult hit = mc.hitResult;
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = ((BlockHitResult) hit).getBlockPos();
                ModMessages.sendToServer(new RaycastPacket(pos));
            }
        }
    }
}