package com.happysg.radar.item.identfilter;

import com.happysg.radar.networking.NetworkHandler;
import com.happysg.radar.networking.networkhandlers.ListNBTHandler;
import com.happysg.radar.networking.packets.SaveListsPacket;
import com.happysg.radar.registry.ModGuiTextures;
import com.happysg.radar.utils.screenelements.DynamicIconButton;
import com.happysg.radar.utils.screenelements.ScrollInputPage;
import com.happysg.radar.utils.screenelements.SimpleEditBox;
import com.happysg.radar.utils.screenelements.TooltipIcon;
import com.jozufozu.flywheel.util.transform.TransformStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AbstractSimiScreen;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.happysg.radar.CreateRadar.MODID;


public class PlayerListScreen extends AbstractSimiScreen {


    protected ModGuiTextures background;
    protected DynamicIconButton remove;
    protected DynamicIconButton playeradd;
    protected DynamicIconButton add;
    protected SimpleEditBox playerentry;
    protected IconButton confirmButton;
    protected ScrollInputPage scrollbar;
    protected List<String> entries = new ArrayList<>();
    private final List<DynamicIconButton> deleteButtons = new ArrayList<>();
    private static final int MAX_VISIBLE = 3;
    private int startIndex = 0;
    private boolean isAddingNewSlot = false;
    private int currentAddSlotY = -1;
    public PlayerListScreen() {
        this.background = ModGuiTextures.PLAYER_LIST;
    }


    protected void renderWindow(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft;
        int y = guiTop;

        background.render(graphics, x, y);
        MutableComponent header = Component.translatable(MODID + ".player_list.title");
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

        drawList(graphics);

    }



    protected void init() {
        setWindowSize(background.width, background.height);
        super.init();

        ListNBTHandler.LoadedLists loaded = ListNBTHandler.loadFromHeldItem(minecraft.player);
        this.entries      = loaded.entries;

        confirmButton = new IconButton(guiLeft + 192, guiTop + 101, AllIcons.I_CONFIRM);
        confirmButton.withCallback(this::onClose);
        addRenderableWidget(confirmButton);

        scrollbar = new ScrollInputPage(guiLeft+209,guiTop+16,guiTop+16,guiTop+93-20,ModGuiTextures.SCROLL);
        scrollbar.withCallback(this::handleScroll);
        addRenderableWidget(scrollbar);
        rebuildList();
    }

    private void handleScroll(float percent) {
        // System.out.println("Scroll changed: " + percent);
        //System.out.println("New startIndex: " + startIndex);
        //System.out.println("Visible entries: " + entries.subList(startIndex, Math.min(entries.size(), startIndex + MAX_VISIBLE)));


        int scrollThreshold = MAX_VISIBLE - 1; // Scroll when more than 2
        int entryCount = entries.size();

        if (entryCount <= scrollThreshold) {
            startIndex = 0;
        } else {
            int maxStartIndex = entryCount - scrollThreshold;
            startIndex = Math.round(percent * maxStartIndex);
        }

        rebuildList();

    }


    private void rebuildList() {
        if (add != null) {
            removeWidget(add);
            add = null;
        }

        deleteButtons.forEach(this::removeWidget);
        deleteButtons.clear();


        int endIndex = Math.min(entries.size(), startIndex + MAX_VISIBLE);
        for (int idx = startIndex; idx < endIndex; idx++) {
            int displayPos = idx - startIndex;            // 0,1,2
            int y = guiTop + 17 + displayPos * 23;

            // delete button
            DynamicIconButton del = new DynamicIconButton(
                    guiLeft + 182, y + 4,
                    ModGuiTextures.ID_X,
                    Component.translatable(MODID + ".card.remove_entry"),
                    11, 11
            );
            int finalIdx = idx;
            del.withCallback((mx, my) -> {
                removeEntry(finalIdx);
            });
            addRenderableWidget(del);
            deleteButtons.add(del);
        }
    }

    private void addPlusButton(int slotIndex) {
        if (add != null) return;  // already exists
        int y = guiTop + 17 + slotIndex * 23;
        add = new DynamicIconButton(guiLeft + 21, y + 2, ModGuiTextures.ID_ADD,
                Component.translatable(MODID + ".filter_add_new"), 16, 16);
        add.withCallback((mx, my) -> startNewSlot(y));
        //add.withCallback((mx, my) ->removeWidget(add));

        addRenderableWidget(add);
    }


    private void startNewSlot(int y) {
        removeWidget(add);
        if (add != null) {
            add = null;
        }

        isAddingNewSlot = true;
        currentAddSlotY = y;

        playeradd = new DynamicIconButton(guiLeft + 185, y, ModGuiTextures.ID_ADD, ModGuiTextures.ID_ADD,
                Component.translatable(MODID + ".filter_add"),
                Component.translatable(MODID + ".filter_add"),
                16, 16);
        playeradd.withCallback((mx, my) -> addItem());
        addRenderableWidget(playeradd);

        playerentry = new SimpleEditBox(font, guiLeft + 26 , y + 4, 135, 11,
                Component.translatable(MODID + ".filter_insert_player_user"));
        playerentry.setMaxLength(16);
        playerentry.setTextColor(0);
        playerentry.setBordered(false);
        playerentry.setTooltip(Tooltip.create(Component.translatable(MODID + ".enter_username")));
        playerentry.setAllowedCharacters(c->"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_".indexOf(c) != -1 );

        addRenderableWidget(playerentry);


    }


    private void addItem() {
        if (!isAddingNewSlot)
            return;

        if (playerentry == null || playeradd == null) {
            cleanupAddWidgets();
            return;
        }

        String input = playerentry.getValue();
        if (input == null) input = "";
        input = input.trim();

        if (input.isEmpty()) {
            cleanupAddWidgets();
            rebuildList();
            return;
        }


        cleanupAddWidgets();

        entries.add(input);
        rebuildList();
    }

    private void cleanupAddWidgets() {
        if (playerentry != null) {
            removeWidget(playerentry);
            playerentry = null;
        }
        if (playeradd != null) {
            removeWidget(playeradd);
            playeradd = null;
        }

        isAddingNewSlot = false;
        currentAddSlotY = -1;
    }


    protected void drawList(GuiGraphics graphics) {
        int available = Math.max(0, entries.size() - startIndex);
        int drawCount = Math.min(available, MAX_VISIBLE);
        
        for (int i = 0; i < drawCount; i++) {
            int idx = startIndex + i;
            int y   = guiTop + 17 + i * 23;

            ModGuiTextures.ID_CARD.render(graphics, guiLeft + 16, y);
            String label = (idx + 1) + ": " + entries.get(idx);
            graphics.drawString(font, label, guiLeft + 42, y + 6, 0, false);
        }

        if (drawCount < MAX_VISIBLE && !isAddingNewSlot) {
            addPlusButton(drawCount);
        }

        if (isAddingNewSlot && currentAddSlotY >= 0) {
            ModGuiTextures.ID_CARD.render(graphics, guiLeft + 4, currentAddSlotY);
        }
    }
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            scrollUp();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }



    private void scrollUp() {
        startIndex = Math.max(0, startIndex - 1);
        rebuildList();
    }

    private void scrollDown() {
        startIndex = Math.min(Math.max(0, entries.size() - MAX_VISIBLE), startIndex + 1);
        rebuildList();
    }

    protected void removeEntry(int entry){
        DynamicIconButton removeMe =  deleteButtons.remove(entry);
        removeWidget(removeMe);

        entries.remove(entry);
        rebuildList();

    }

    @Override
    public void removed() {
        super.removed();
        
        if (isAddingNewSlot) {
            addItem();
        }

        if (minecraft.player != null && minecraft.level != null && minecraft.level.isClientSide) {
            NetworkHandler.CHANNEL.sendToServer(
                    new SaveListsPacket(this.entries)
            );
        }
    }
}
