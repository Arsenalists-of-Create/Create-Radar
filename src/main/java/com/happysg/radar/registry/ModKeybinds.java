package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;



@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = CreateRadar.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModKeybinds {

    public static final String CATEGORY = String.valueOf(Component.translatable(CreateRadar.MODID + ".key.categories.create_radar"));

    public static final KeyMapping SCOPE_ACTION = new KeyMapping(
            CreateRadar.MODID+ ".key.binocular.use",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    );
    public static final KeyMapping BINO_FIRE = new KeyMapping(
            CreateRadar.MODID + ".key.binocular.fire",
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
