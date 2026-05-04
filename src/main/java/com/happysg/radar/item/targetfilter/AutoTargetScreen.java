package com.happysg.radar.item.targetfilter;

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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class AutoTargetScreen extends AbstractSimiScreen  {
    private static final String KEY = "TargetBools";
    private static final int COUNT = 7;

    boolean player =true;
    boolean contraption=true;
    boolean mob=true;
    boolean animal=true;
    boolean projectile=true;
    boolean autoTarget= true;
    boolean artilleryMode = true;
    boolean lineofSight = true;
    //  boolean[] bools = new boolean[]{player,contraption,mob,animal,projectile,autoTarget,lineofSight};

    protected IconButton playerButton;
    protected Indicator playerIndicator;
    protected IconButton contraptionButton;
    protected Indicator contraptionIndicator;
    protected IconButton mobButton;
    protected Indicator mobIndicator;
    protected IconButton animalButton;
    protected Indicator animalIndicator;
    protected IconButton projectileButton;
    protected Indicator projectileIndicator;
    protected IconButton autoTargetButton;
    protected Indicator autoTargetIndicator;
    protected IconButton autoFireButton;
    protected IconButton lineofSightButton;
    protected Indicator lineofSightIndicator;
    protected IconButton confirmButton;

    protected ModGuiTextures background;
    public AutoTargetScreen() {
        this.background = ModGuiTextures.TARGETING_FILTER;

    }
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft;
        int y = guiTop;

        background.render(graphics, x, y);
        MutableComponent header = Component.translatable(CreateRadar.MODID + ".target_filter.title");
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
        int X = guiLeft;
        int Y = guiTop;
        loadFlagsFromHeldItem();
        playerButton = new IconButton(guiLeft + 22, guiTop + 43, ModGuiTextures.PLAYER_BUTTON);
        playerButton.setToolTip(Component.translatable(CreateRadar.MODID + ".radar_button.player"));
        playerIndicator = new Indicator(guiLeft + 22, guiTop + 36, Component.empty());
        playerIndicator.state = player ? Indicator.State.GREEN : Indicator.State.RED;
        playerButton.withCallback((x, y) -> {
            player = !player;
            playerIndicator.state = player ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(playerButton);
        addRenderableWidget(playerIndicator);
        contraptionButton = new IconButton(guiLeft + 42, guiTop + 43, ModGuiTextures.SABLE_BUTTON);
        contraptionButton.setToolTip(Component.translatable(CreateRadar.MODID + ".radar_button.contraption"));
        contraptionIndicator = new Indicator(guiLeft + 42, guiTop + 36, Component.empty());
        contraptionIndicator.state = contraption ? Indicator.State.GREEN : Indicator.State.RED;
        contraptionButton.withCallback((x, y) -> {
            contraption = !contraption;
            contraptionIndicator.state = contraption ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(contraptionButton);
        addRenderableWidget(contraptionIndicator);

        mobButton = new IconButton(guiLeft + 62, guiTop + 43, ModGuiTextures.MOB_BUTTON);
        mobButton.setToolTip(Component.translatable(CreateRadar.MODID + ".radar_button.hostile"));
        mobIndicator = new Indicator(guiLeft + 62, guiTop + 36, Component.empty());
        mobIndicator.state = mob ? Indicator.State.GREEN : Indicator.State.RED;
        mobButton.withCallback((x, y) -> {
            mob = !mob;
            mobIndicator.state = mob ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(mobButton);
        addRenderableWidget(mobIndicator);

        animalButton = new IconButton(guiLeft + 82, guiTop + 43, ModGuiTextures.ANIMAL_BUTTON);
        animalButton.setToolTip(Component.translatable(CreateRadar.MODID + ".radar_button.animal"));
        animalIndicator = new Indicator(guiLeft + 82, guiTop + 36, Component.empty());
        animalIndicator.state = animal ? Indicator.State.GREEN : Indicator.State.RED;
        animalButton.withCallback((x, y) -> {
            animal = !animal;
            animalIndicator.state = animal ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(animalButton);
        addRenderableWidget(animalIndicator);

        projectileButton = new IconButton(guiLeft + 102, guiTop + 43, ModGuiTextures.PROJECTILE_BUTTON);
        projectileButton.setToolTip(Component.translatable(CreateRadar.MODID + ".radar_button.projectile"));
        projectileIndicator = new Indicator(guiLeft + 102, guiTop + 36, Component.empty());
        projectileIndicator.state = projectile ? Indicator.State.GREEN : Indicator.State.RED;
        projectileButton.withCallback((x, y) -> {
            projectile = !projectile;
            projectileIndicator.state = projectile ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(projectileButton);
        addRenderableWidget(projectileIndicator);

        lineofSightButton = new IconButton(guiLeft + 122, guiTop + 43, ModGuiTextures.LOS_BUTTON);
        lineofSightButton.setToolTip(Component.translatable(CreateRadar.MODID +".radar_button.lineofsight"));
        lineofSightIndicator = new Indicator(guiLeft + 122, guiTop + 36,Component.empty());
        lineofSightIndicator.state = lineofSight ? Indicator.State.GREEN : Indicator.State.RED;
        lineofSightButton.withCallback((x,y) -> {
            lineofSight = !lineofSight;
            lineofSightIndicator.state = lineofSight ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(lineofSightButton);
        addRenderableWidget(lineofSightIndicator);

        autoTargetButton = new IconButton(guiLeft + 170, guiTop + 42, ModGuiTextures.AUTO_TARGET);
        autoTargetButton.setToolTip(Component.translatable(CreateRadar.MODID + ".radar_button.auto_target"));
        autoTargetIndicator = new Indicator(guiLeft + 170, guiTop + 35, Component.empty());
        autoTargetIndicator.state = autoTarget ? Indicator.State.GREEN : Indicator.State.RED;
        autoTargetButton.withCallback((x, y) -> {
            autoTarget = !autoTarget;
            autoTargetIndicator.state = autoTarget ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(autoTargetButton);
        addRenderableWidget(autoTargetIndicator);

        confirmButton = new IconButton(guiLeft+191,guiTop+83, AllIcons.I_CONFIRM);
        confirmButton.withCallback(this::onClose);
        addRenderableWidget(confirmButton);


    }
    // call this when constructing the screen or in init()
    private void loadFlagsFromHeldItem() {
        ItemStack stack = net.minecraft.client.Minecraft.getInstance().player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!stack.isEmpty()) {
            boolean[] arr = BoolNBThelper.loadBooleansFromBytes(stack, KEY, COUNT);
            if (arr.length >= COUNT) {
                player = arr[0];
                contraption = arr[1];
                mob = arr[2];
                animal = arr[3];
                projectile = arr[4];
                lineofSight = arr[5];
                autoTarget  = arr[6];
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
        ItemStack stack = net.minecraft.client.Minecraft.getInstance().player.getItemInHand(InteractionHand.MAIN_HAND);
        flags[0] = player;
        flags[1] = contraption;
        flags[2] = mob;
        flags[3] = animal;
        flags[4] = projectile;
        flags[5] = lineofSight;
        flags[6] = autoTarget;
        BoolNBThelper.saveBooleansAsBytes(stack,flags, KEY);
        BoolListPacket.send(true, flags, KEY);

    }

}
