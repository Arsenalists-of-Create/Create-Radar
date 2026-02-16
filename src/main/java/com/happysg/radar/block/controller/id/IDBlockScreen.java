package com.happysg.radar.block.controller.id;

import com.happysg.radar.CreateRadar;
import net.neoforged.neoforge.network.PacketDistributor;
import com.happysg.radar.networking.packets.IDRecordPayload;
import com.happysg.radar.registry.ModGuiTextures;
import net.createmod.catnip.gui.AbstractSimiScreen;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.valkyrienskies.core.api.ships.Ship;


//only open on VsShip
public class IDBlockScreen extends AbstractSimiScreen {
    private static final ModGuiTextures BACKGROUND = ModGuiTextures.ID_SCREEN;

    Ship ship;
    String id = "";
    String name = "";

    public IDBlockScreen(Ship ship) {
        this.ship = ship;
        IDManager.IDRecord record = IDManager.getIDRecordByShip(ship);
        if (record != null) {
            this.id = record.secretID();
            this.name = record.name();// this will now be the slug
        }

    }

    @Override
    protected void init() {
        setWindowSize(BACKGROUND.width, BACKGROUND.height);
        super.init();
        clearWidgets();
        int x = guiLeft;
        int y = guiTop;

        EditBox nameField = new EditBox(font, x + 70, y + 25, 100, 18, Component.translatable(CreateRadar.MODID + ".id_block.name_input"));
        nameField.setBordered(false);
        nameField.setValue(name);
        nameField.setMaxLength(20);
        nameField.setResponder(s -> name = s);
        addRenderableWidget(nameField);

        EditBox idField = new EditBox(font, x + 85, y + 48, 100, 18, Component.translatable(CreateRadar.MODID + ".id_block.id_input"));
        idField.setBordered(false);
        idField.setValue(id);
        idField.setMaxLength(10);
        idField.setResponder(s -> id = s);
        addRenderableWidget(idField);

        IconButton confirmButton = new IconButton(x + BACKGROUND.width - 33, y + BACKGROUND.height - 23, AllIcons.I_CONFIRM);
        confirmButton.withCallback(this::onClose);
        addRenderableWidget(confirmButton);
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft;
        int y = guiTop;
        BACKGROUND.render(graphics, x, y);
    }

    @Override
    public void onClose() {
        super.onClose();
        PacketDistributor.sendToServer(new IDRecordPayload(ship.getId(), ship.getSlug(), id, name));
    }


}
