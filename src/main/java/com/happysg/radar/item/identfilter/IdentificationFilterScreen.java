package com.happysg.radar.item.identfilter;

import com.happysg.radar.registry.ModGuiTextures;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;

import static com.happysg.radar.CreateRadar.MODID;

public class IdentificationFilterScreen extends AbstractSimiScreen {

    protected IconButton  playerFilter;

    protected IconButton shipFilter;
    protected IconButton confirmButton;
    protected ModGuiTextures background;
    public IdentificationFilterScreen() {
        this.background = ModGuiTextures.IDENT_FILTER;
    }
    protected void renderWindow(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft;
        int y = guiTop;

        background.render(graphics, x, y);
        MutableComponent header = Component.translatable(MODID + ".identification_filter.title");
        graphics.drawString(font, header, x + background.width / 2 - font.width(header) / 2, y + 4, 0, false);

        PoseStack ms = graphics.pose();
        ms.pushPose();
        ms.translate(0, guiTop + 46, 0);
        ms.translate(0, 21, 0);
        ms.popPose();

        ms.pushPose();
        TransformStack.of(ms)
                .pushPose()
                .translate(x + background.width + 4, y + background.height + 4, 100)
                .scale(40)
                .rotateX(-22)
                .rotateY(63);
        ms.popPose();

    }
    @Override
    protected void init() {
        setWindowSize(background.width, background.height);
        super.init();
        clearWidgets();

        int x = guiLeft;
        int y = guiTop;
        playerFilter = new IconButton(guiLeft + 41, guiTop + 25, ModGuiTextures.FILTER_BUTTON);
        playerFilter.setToolTip(Component.translatable(MODID + ".radar_button.player"));
        playerFilter.withCallback(() -> Minecraft.getInstance().setScreen(new PlayerListScreen()));
        addRenderableWidget(playerFilter);


        shipFilter = new IconButton(guiLeft + 108, guiTop + 25, ModGuiTextures.FILTER_BUTTON);
        shipFilter.setToolTip(Component.translatable(MODID + ".radar_button.ship"));
        shipFilter.withCallback(() -> Minecraft.getInstance().setScreen(new ShipListScreen()));
        addRenderableWidget(shipFilter);
        confirmButton = new IconButton(x + background.width - 33, y + background.height - 24, AllIcons.I_CONFIRM);
        confirmButton.withCallback(this::onClose);
        addRenderableWidget(confirmButton);
    }


        //TODO add state save


}
