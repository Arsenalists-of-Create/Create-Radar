package com.happysg.radar.utils.screenelements;
import com.happysg.radar.registry.ModGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class ScrollInputPage extends AbstractWidget {
    private final int minY;
    private final int maxY;
    private boolean dragging = false;
    private int dragOffsetY = 0;
    private final ModGuiTextures icon;
    private Consumer<Float> callback = f -> {};
    public ScrollInputPage(int x, int y, int minY, int maxY, ModGuiTextures icon) {
        super(x, y, icon.width, icon.height, Component.empty());
        this.minY = minY;
        this.maxY = maxY;
        this.icon = icon;
    }

    public ScrollInputPage withCallback(Consumer<Float> callback) {
        this.callback = callback;
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {

        graphics.blit(
                icon.location,
                getX(), getY(),
                icon.startX, icon.startY,
                icon.width, icon.height,
                icon.textureWidth, icon.textureHeight
        );// Adjust atlas size if needed
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.isMouseOver(mouseX, mouseY)) {
            dragging = true;
            dragOffsetY = (int) mouseY - this.getY();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            int newY = (int) mouseY - dragOffsetY;
            newY = Math.max(minY, Math.min(maxY, newY));
            if (newY != this.getY()) {
                this.setY(newY);
                if (callback != null) {
                    callback.accept(getScrollPercent());
                }
            }
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {

    }

    public float getScrollPercent() {
        return (float)(getY() - minY) / (float)(maxY - minY);
    }
}


