package com.happysg.radar.block.controller.id;

import com.happysg.radar.CreateRadar;

import com.happysg.radar.networking.packets.IDRecordRequestPacket;
import com.happysg.radar.networking.packets.IDRecordPacket;
import com.happysg.radar.registry.ModGuiTextures;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.createmod.catnip.gui.AbstractSimiScreen;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.UUID;


//only open on VsShip
public class IDBlockScreen extends AbstractSimiScreen {
    private static final ModGuiTextures BACKGROUND = ModGuiTextures.ID_SCREEN;

    SubLevelAccess ship;
    String id = "";
    String name = "";
    private EditBox nameField;
    private EditBox idField;

    public IDBlockScreen(SubLevelAccess ship) {
        this.ship = ship;
    }

    @Override
    protected void init() {
        setWindowSize(BACKGROUND.width, BACKGROUND.height);
        super.init();
        clearWidgets();
        int x = guiLeft;
        int y = guiTop;
        loadFromClientCache();

        nameField = new EditBox(font, x + 70, y + 25, 100, 18, Component.translatable(CreateRadar.MODID + ".id_block.name_input"));
        nameField.setBordered(false);
        nameField.setValue(name);
        nameField.setMaxLength(20);
        nameField.setResponder(s -> name = s);
        addRenderableWidget(nameField);

        idField = new EditBox(font, x + 85, y + 48, 100, 18, Component.translatable(CreateRadar.MODID + ".id_block.id_input"));
        idField.setBordered(false);
        idField.setValue(id);
        idField.setMaxLength(10);
        idField.setResponder(s -> id = s);
        addRenderableWidget(idField);

        IconButton confirmButton = new IconButton(x + BACKGROUND.width - 33, y + BACKGROUND.height - 23, AllIcons.I_CONFIRM);
        confirmButton.withCallback(this::onClose);
        addRenderableWidget(confirmButton);

       IDRecordRequestPacket.send(ship.getUniqueId());
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft;
        int y = guiTop;
        BACKGROUND.render(graphics, x, y);
    }

    @Override
    public void onClose() {
        IDManager.addIDRecord(ship.getUniqueId(), id, name);
        super.onClose();
        IDRecordPacket.send(ship.getUniqueId(), ship.getUniqueId().toString(), id,name);
    }

    private void loadFromClientCache() {
        IDManager.IDRecord record = IDManager.getIDRecordByShip(ship);
        if (record == null) return;
        this.id = record.secretID();
        this.name = record.name();
    }

    public boolean isForShip(UUID shipId) {
        return ship.getUniqueId() == shipId;
    }

    public void applyLoadedRecord(String loadedName, String loadedId) {
        this.name = loadedName == null ? "" : loadedName;
        this.id = loadedId == null ? "" : loadedId;

        if (nameField != null) {
            nameField.setValue(this.name);
        }
        if (idField != null) {
            idField.setValue(this.id);
        }
    }

}
