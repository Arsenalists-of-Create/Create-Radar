package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class ModKeybinds {

    public static final String CATEGORY = "key.categories." + CreateRadar.MODID;

    public static final KeyMapping SCOPE_ACTION = new KeyMapping(
            "key." + CreateRadar.MODID + ".binocular.use",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    );

    public static final KeyMapping BINO_FIRE = new KeyMapping(
            "key." + CreateRadar.MODID + ".binocular.fire",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_LEFT,
            CATEGORY
    );

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(SCOPE_ACTION);
        event.register(BINO_FIRE);
    }
}