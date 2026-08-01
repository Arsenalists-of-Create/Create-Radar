package com.happysg.radar.item.binos;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.networking.packets.FirePacket;
import com.happysg.radar.networking.packets.RaycastPacket;
import com.happysg.radar.registry.ModKeybinds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = CreateRadar.MODID)
public class BinocularHandler {
    private static boolean wasDown = false;
    private static boolean pressWasValid = false;
    private static final float SENS_MULTIPLIER = 0.25f;
    private static final float BINOCULAR_FOV = 0.1f;
    private static int updateCooldown = 0;

    private static Double savedSensitivity = null;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }

        // i slow look while the player is actively using binoculars, and restore it when they stop
        boolean usingBinos = player.isUsingItem() && player.getUseItem().getItem() instanceof Binoculars;

        var sensOpt = mc.options.sensitivity();
        if (usingBinos) {
            if (savedSensitivity == null) {
                savedSensitivity = sensOpt.get();
            }

            sensOpt.set(savedSensitivity * SENS_MULTIPLIER);
        } else if (savedSensitivity != null) {
            sensOpt.set(savedSensitivity);
            savedSensitivity = null;
        }

        boolean isDown = ModKeybinds.BINO_FIRE.isDown();

        while (ModKeybinds.SCOPE_ACTION.consumeClick()) {
            if (!player.isUsingItem()) {
                return;
            }

            if (!(player.getUseItem().getItem() instanceof Binoculars)) {
                return;
            }

            RaycastPacket.send();
        }

        if (isDown && !wasDown) {
            pressWasValid = isValid(player);

            if (pressWasValid) {
                FirePacket.send(true);
                RaycastPacket.send();
                // This block also runs through the held-key update below, so starting
                // at 2 prevents a duplicate refresh on the initial press. The next
                // client tick refreshes normally.
                updateCooldown = 2;
            }
        }

        if (isDown && pressWasValid) {
            if (--updateCooldown <= 0) {
                // i refresh both the slave command and the target
                FirePacket.send(true);
                RaycastPacket.send();

                updateCooldown = 1;
            }
        }

        if (!isDown && wasDown) {
            if (pressWasValid) {
                FirePacket.send(false);
            }

            pressWasValid = false;
            updateCooldown = 0;
        }

        wasDown = isDown;
    }

    private static boolean isValid(Player player) {
        return player.getMainHandItem().getItem() instanceof Binoculars
                || player.getOffhandItem().getItem() instanceof Binoculars;
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }

        if (player.isUsingItem() && player.getUseItem().getItem() instanceof Binoculars) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        // i only zoom while actively scoping with my binoculars
        if (!player.isUsingItem()) {
            return;
        }

        if (!(player.getUseItem().getItem() instanceof Binoculars)) {
            return;
        }

        event.setNewFovModifier(BINOCULAR_FOV);
    }
}
