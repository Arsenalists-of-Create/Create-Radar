package com.happysg.radar.block.controller.id;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.networking.ModMessages;
import com.happysg.radar.networking.packets.IDRecordRequestPacket;
import com.happysg.radar.networking.packets.IDRecordPacket;
import com.happysg.radar.registry.ModGuiTextures;
import net.createmod.catnip.gui.AbstractSimiScreen;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.valkyrienskies.core.api.ships.Ship;


//only open on VsShip
public class IDBlockScreen extends AbstractSimiScreen {
    private static final ModGuiTextures BACKGROUND = ModGuiTextures.ID_SCREEN;

    Ship ship;
    String id = "";
    String name = "";
    private EditBox nameField;
    private EditBox idField;

    public IDBlockScreen(Ship ship) {
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

        ModMessages.sendToServer(new IDRecordRequestPacket(ship.getId()));
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft;
        int y = guiTop;
        BACKGROUND.render(graphics, x, y);
    }

    @Override
    public void onClose() {
        IDManager.addIDRecord(ship.getId(), id, name);
        super.onClose();
        ModMessages.sendToServer(new IDRecordPacket(ship.getId(), ship.getSlug(), id,name));
    }

    private void loadFromClientCache() {
        IDRecord record = IDManager.getIDRecordByShip(ship);
        if (record == null) return;
        this.id = record.secretID();
        this.name = record.name();
    }

    public boolean isForShip(long shipId) {
        return ship.getId() == shipId;
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