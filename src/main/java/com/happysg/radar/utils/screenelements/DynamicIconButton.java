package com.happysg.radar.utils.screenelements;

import com.happysg.radar.registry.ModGuiTextures;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;


public class DynamicIconButton extends IconButton {

    private final ModGuiTextures iconOn;
    private final ModGuiTextures iconOff;
    private final Component tooltipOn;
    private final Component tooltipOff;
    private boolean isOn;

    public DynamicIconButton(int x, int y, ModGuiTextures iconOn, ModGuiTextures iconOff,
                            Component tooltipOn, Component tooltipOff, int width, int height) {
        super(x, y, iconOff); // start in OFF state
        this.iconOn = iconOn;
        this.iconOff = iconOff;
        this.tooltipOn = tooltipOn;
        this.tooltipOff = tooltipOff;

        this.setWidth(width);
        this.setHeight(height);
        this.isOn = false;

        setToolTip(tooltipOff);

        withCallback((mx, my) -> toggle());
    }
    public DynamicIconButton(int x, int y,ModGuiTextures icon, Component tooltip, int width, int height){
        super(x,y,icon);
        this.tooltipOn = tooltip;
        this.tooltipOff = tooltip;
        this.iconOn =icon;
        this.iconOff= icon;
        this.setWidth(width);
        this.setHeight(height);
        setToolTip(tooltip);

    }

    public void toggle() {
        isOn = !isOn;
        setIcon(isOn ? iconOn : iconOff);
        setToolTip(isOn ? tooltipOn : tooltipOff);
    }

    public boolean getState() {
        return isOn;
    }

    public void setState(boolean state) {
        this.isOn = state;
        setIcon(isOn ? iconOn : iconOff);
        setToolTip(isOn ? tooltipOn : tooltipOff);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        ModGuiTextures icon = isOn ? iconOn : iconOff;

        // Draw only the icon — skip IconButton's default 16x16 background
        graphics.blit(
                icon.location,
                getX(), getY(),
                icon.startX, icon.startY,
                icon.width, icon.height,
                icon.textureWidth, icon.textureHeight
        );

        // Optionally draw hover highlight (translucent overlay)
        if (isHoveredOrFocused()) {
            graphics.fill(getX(), getY(), getX() + icon.width, getY() + icon.height, 0x22FFFFFF);
        }
    }
}