package com.happysg.radar.utils.screenelements;

import com.happysg.radar.registry.ModGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class TooltipIcon extends AbstractWidget {
    private final ModGuiTextures icon;
    private final Component tooltip;

    public TooltipIcon(int x, int y, ModGuiTextures icon, Component tooltip) {
        super(x, y, icon.width, icon.height, tooltip);
        this.icon = icon;
        this.tooltip = tooltip;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.blit(
                icon.location,
                getX(), getY(),
                icon.startX, icon.startY,
                icon.width, icon.height,
                icon.textureWidth, icon.textureHeight
        );
    }


    @Override
    public void onClick(double mouseX, double mouseY) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {

    }
}