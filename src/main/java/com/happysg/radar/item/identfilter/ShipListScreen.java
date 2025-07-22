package com.happysg.radar.item.identfilter;

import com.happysg.radar.networking.NetworkHandler;
import com.happysg.radar.networking.networkhandlers.ListNBTHandler;
import com.happysg.radar.networking.packets.SaveListsPacket;
import com.happysg.radar.registry.ModGuiTextures;
import com.happysg.radar.utils.screenelements.SimpleEditBox;
import com.jozufozu.flywheel.util.transform.TransformStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AbstractSimiScreen;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import static com.happysg.radar.CreateRadar.MODID;

public class ShipListScreen extends AbstractSimiScreen {
    public ShipListScreen() {
        this.background = ModGuiTextures.SHIP_LIST;
    }

    protected ModGuiTextures background;
    private IconButton confirmButton;
    protected SimpleEditBox shipEntry;
    protected String idCode;
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft;
        int y = guiTop;

        background.render(graphics, x, y);
        MutableComponent header = Component.translatable(MODID + ".ship_list.title");
        graphics.drawString(font, header, x + background.width / 2 - font.width(header) / 2, y + 4, 0, false);

        PoseStack ms = graphics.pose();
        ms.pushPose();
        ms.translate(0, guiTop + 46, 0);
        ms.translate(0, 21, 0);
        ms.popPose();

        ms.pushPose();
        TransformStack.cast(ms)
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

        int x = guiLeft;
        int y = guiTop;

        // 1) instantiate your edit box
        shipEntry = new SimpleEditBox(font, x + 60, y + 29, 93, 10, null);
        shipEntry.setBordered(false);
        shipEntry.setTooltip(Tooltip.create(Component.translatable(MODID + ".ship_list.enter_code")));
        shipEntry.setAllowedCharacters(c -> "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_".indexOf(c) != -1);
        shipEntry.setTextColor(0);
        shipEntry.setMaxLength(8);

        // 2) load the string correctly
        String idCode = ListNBTHandler.loadStringFromHeldItem(minecraft.player);
        shipEntry.setValue(idCode);

        // 3) add it so it actually renders and accepts input
        addRenderableWidget(shipEntry);

        // 4) confirm button
        confirmButton = new IconButton(x + background.width - 33, y + background.height - 24, AllIcons.I_CONFIRM);
        confirmButton.withCallback(this::onClose);
        addRenderableWidget(confirmButton);
    }



    @Override
    public void removed() {
        String ID = shipEntry.getValue();

        // 1) Update the client‐side tag immediately:
        ListNBTHandler.saveStringToHeldItem(minecraft.player, ID);

        // 2) Tell the server so it can persist it and sync back properly
        NetworkHandler.CHANNEL.sendToServer(new SaveListsPacket(ID));
    }

}

