package com.happysg.radar.item.binos;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.networking.packets.FirePayload;
import com.happysg.radar.networking.packets.RaycastPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import com.happysg.radar.registry.ModKeybinds;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;

@EventBusSubscriber(value = Dist.CLIENT, modid = CreateRadar.MODID)
public class BinocularHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean wasDown = false;
    private static boolean pressWasValid = false;

    private static int updateCooldown = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        boolean isDown = ModKeybinds.BINO_FIRE.isDown();
        while (ModKeybinds.SCOPE_ACTION.consumeClick()) {

            if (!player.isUsingItem()) return;

            if (!(player.getUseItem().getItem() instanceof Binoculars)) return;
            PacketDistributor.sendToServer(new RaycastPayload());
        }

        // ───── key pressed ─────
        if (isDown && !wasDown) {
            pressWasValid = isValid(player);
            if (pressWasValid) {
                PacketDistributor.sendToServer(new FirePayload(true));
                PacketDistributor.sendToServer(new RaycastPayload());
                updateCooldown = 5;
            }
        }

        // ───── key held ─────
        if (isDown && pressWasValid) {
            if (--updateCooldown <= 0) {
                // i refresh both the slave command and the target
                PacketDistributor.sendToServer(new FirePayload(true));
                PacketDistributor.sendToServer(new RaycastPayload());

                updateCooldown = 2;
            }
        }

        // ───── key released ─────
        if (!isDown && wasDown) {
            if (pressWasValid) {
                PacketDistributor.sendToServer(new FirePayload(false));
            }
            pressWasValid = false;
            updateCooldown = 0;
        }

        wasDown = isDown;
    }



    private static boolean isValid(Player player) {
        return (player.getMainHandItem().getItem() instanceof Binoculars || player.getOffhandItem().getItem() instanceof Binoculars );
    }
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        if (player.isUsingItem() && player.getUseItem().getItem() instanceof Binoculars) {
            event.setCanceled(true);
        }
    }
    private static final float BINOCULAR_FOV = .1f; // ~6–7x zoom

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        // i only zoom while actively scoping with my binoculars
        if (!player.isUsingItem()) return;
        if (!(player.getUseItem().getItem() instanceof Binoculars)) return;
        float current = event.getFovModifier();
        float target = BINOCULAR_FOV;
        event.setNewFovModifier(BINOCULAR_FOV);
    }

}

