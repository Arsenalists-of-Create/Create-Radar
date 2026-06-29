package com.happysg.radar.utils.screenelements;

import com.happysg.radar.CreateRadar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

public class MonitorButton extends Button {

    private static final ResourceLocation TEXTURE =
            CreateRadar.asResource("textures/gui/radar_button.png");

    // Actual PNG dimensions.
    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 64;

    // Each button-state tile is 15x15.
    private static final int FRAME_SIZE = 15;

    private final float renderScale;

    public MonitorButton(
            int x,
            int y,
            float renderScale,
            Component narration,
            OnPress onPress
    ) {
        super(
                x,
                y,
                Math.round(FRAME_SIZE * renderScale),
                Math.round(FRAME_SIZE * renderScale),
                narration,
                onPress,
                DEFAULT_NARRATION
        );

        this.renderScale = renderScale;
    }

    @Override
    protected void renderWidget(GuiGraphics gg, int mouseX, int mouseY, float partialTicks) {
        boolean heldDown =
                active
                        && isMouseOver(mouseX, mouseY)
                        && GLFW.glfwGetMouseButton(
                        Minecraft.getInstance().getWindow().getWindow(),
                        GLFW.GLFW_MOUSE_BUTTON_LEFT
                ) == GLFW.GLFW_PRESS;

        // Left tile = normal. Right tile = held/pressed.
        int sourceU = heldDown ? FRAME_SIZE : 0;

        gg.pose().pushPose();
        gg.pose().translate(getX(), getY(), 0);
        gg.pose().scale(renderScale, renderScale, 1.0f);

        // Draw at native 15x15 resolution, then scale the pose.
        gg.blit(
                TEXTURE,
                0,
                0,
                sourceU,
                0,
                FRAME_SIZE,
                FRAME_SIZE,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT
        );

        gg.pose().popPose();
    }
}