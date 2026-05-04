package com.happysg.radar.item.detectionfilter;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.networking.NetworkHandler;
import com.happysg.radar.networking.networkhandlers.BoolNBThelper;
import com.happysg.radar.networking.packets.BoolListPacket;
import com.happysg.radar.registry.ModGuiTextures;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.Indicator;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class RadarFilterScreen extends AbstractSimiScreen {

    private static final String KEY = "detectBools";
    private static final int COUNT = 7; // set to number of booleans you use

    boolean player;
    boolean vs2;
    boolean contraption;
    boolean mob;
    boolean projectile;
    boolean animal;
    boolean item;

    protected IconButton playerButton;
    protected Indicator playerIndicator;
    protected IconButton sableButton;
    protected Indicator vs2Indicator;
    protected IconButton contraptionButton;
    protected Indicator contraptionIndicator;
    protected IconButton mobButton;
    protected Indicator mobIndicator;
    protected IconButton projectileButton;
    protected Indicator projectileIndicator;
    protected IconButton animalButton;
    protected Indicator animalIndicator;
    protected IconButton itemButton;
    protected Indicator itemIndicator;
    protected ModGuiTextures background;
    protected IconButton confirmButton;
    public RadarFilterScreen() {
        this.background = ModGuiTextures.DETECTION_FILTER;

    }
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft;
        int y = guiTop;

        background.render(graphics, x, y);
        MutableComponent header = Component.translatable(CreateRadar.MODID + ".detection_filter.title");
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
        loadFlagsFromHeldItem();
        super.init();
        clearWidgets();
        int Y = guiLeft;
        int X = guiTop;
        playerButton = new IconButton(guiLeft + 32, guiTop + 38, ModGuiTextures.PLAYER_BUTTON);
        playerButton.setToolTip(Component.translatable(CreateRadar.MODID + ".radar_button.player"));
        playerIndicator = new Indicator(guiLeft + 32, guiTop + 31, Component.empty());
        playerIndicator.state = player ? Indicator.State.GREEN : Indicator.State.RED;
        playerButton.withCallback((x, y) -> {
            player = !player;
            playerIndicator.state = player ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(playerButton);
        addRenderableWidget(playerIndicator);

        sableButton = new IconButton(guiLeft + 60, guiTop + 38, ModGuiTextures.SABLE_BUTTON);
        sableButton.setToolTip(Component.translatable(CreateRadar.MODID + ".radar_button.vs2"));
        vs2Indicator = new Indicator(guiLeft + 60, guiTop + 31, Component.empty());
        vs2Indicator.state = vs2 ? Indicator.State.GREEN : Indicator.State.RED;
        sableButton.withCallback((x, y) -> {
            vs2 = !vs2;
            vs2Indicator.state = vs2 ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(sableButton);
        addRenderableWidget(vs2Indicator);

        contraptionButton = new IconButton(guiLeft + 88, guiTop + 38, ModGuiTextures.CONTRAPTION_BUTTON);
        contraptionButton.setToolTip(Component.translatable(CreateRadar.MODID + ".radar_button.contraption"));
        contraptionIndicator = new Indicator(guiLeft + 88, guiTop + 31, Component.empty());
        contraptionIndicator.state = contraption ? Indicator.State.GREEN : Indicator.State.RED;
        contraptionButton.withCallback((x, y) -> {
            contraption = !contraption;
            contraptionIndicator.state = contraption ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(contraptionButton);
        addRenderableWidget(contraptionIndicator);

        mobButton = new IconButton(guiLeft + 116, guiTop + 38, ModGuiTextures.MOB_BUTTON);
        mobButton.setToolTip(Component.translatable(CreateRadar.MODID + ".radar_button.mob"));
        mobIndicator = new Indicator(guiLeft + 116, guiTop + 31, Component.empty());
        mobIndicator.state = mob ? Indicator.State.GREEN : Indicator.State.RED;
        mobButton.withCallback((x, y) -> {
            mob = !mob;
            mobIndicator.state = mob ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(mobButton);
        addRenderableWidget(mobIndicator);

        animalButton = new IconButton(guiLeft + 144, guiTop + 38, ModGuiTextures.ANIMAL_BUTTON);
        animalButton.setToolTip(Component.translatable(CreateRadar.MODID + ".radar_button.animal"));
        animalIndicator = new Indicator(guiLeft + 144, guiTop + 31, Component.empty());
        animalIndicator.state = animal ? Indicator.State.GREEN : Indicator.State.RED;
        animalButton.withCallback((x, y) -> {
            animal = !animal;
            animalIndicator.state = animal ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(animalButton);
        addRenderableWidget(animalIndicator);

        projectileButton = new IconButton(guiLeft + 172, guiTop + 38, ModGuiTextures.PROJECTILE_BUTTON);
        projectileButton.setToolTip(Component.translatable(CreateRadar.MODID + ".radar_button.projectile"));
        projectileIndicator = new Indicator(guiLeft + 172, guiTop + 31, Component.empty());
        projectileIndicator.state = projectile ? Indicator.State.GREEN : Indicator.State.RED;
        projectileButton.withCallback((x, y) -> {
            projectile = !projectile;
            projectileIndicator.state = projectile ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(projectileButton);
        addRenderableWidget(projectileIndicator);

        itemButton = new IconButton(guiLeft + 200, guiTop + 38, ModGuiTextures.ITEM_BUTTON);
        itemButton.setToolTip(Component.translatable(CreateRadar.MODID + ".radar_button.item"));
        itemIndicator = new Indicator(guiLeft + 200, guiTop + 31, Component.empty());
        itemIndicator.state = item ? Indicator.State.GREEN : Indicator.State.RED;
        itemButton.withCallback((x, y) -> {
            item = !item;
            itemIndicator.state = item ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(itemButton);
        addRenderableWidget(itemIndicator);

        confirmButton = new IconButton(guiLeft+223,guiTop+72, AllIcons.I_CONFIRM);
        confirmButton.withCallback(this::onClose);
        addRenderableWidget(confirmButton);
    }

    private void loadFlagsFromHeldItem() {
        ItemStack stack = Minecraft.getInstance().player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!stack.isEmpty()) {
            boolean[] arr = BoolNBThelper.loadBooleansFromBytes(stack, KEY, COUNT);
            if (arr.length >= COUNT) {
                player = arr[0];
                vs2 = arr[1];
                contraption = arr[2];
                mob = arr[3];
                animal = arr[4];
                projectile = arr[5];
                item = arr[6];
            }

        }
    }

    @Override
    public void removed() {
        super.removed();
        sendFlagsToServerAndSave();
    }

    private void sendFlagsToServerAndSave() {
        boolean[] flags = new boolean[COUNT];
        ItemStack stack = Minecraft.getInstance().player.getItemInHand(InteractionHand.MAIN_HAND);
        flags[0] = player;
        flags[1] = vs2;
        flags[2] = contraption;
        flags[3] = mob;
        flags[4] = animal;
        flags[5] = projectile;
        flags[6] = item;
        BoolNBThelper.saveBooleansAsBytes(stack,flags, KEY);

        BoolListPacket.send(true, flags, KEY);

    }

}
