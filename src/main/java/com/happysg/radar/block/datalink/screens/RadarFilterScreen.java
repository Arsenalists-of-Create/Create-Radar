package com.happysg.radar.block.datalink.screens;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import com.happysg.radar.registry.ModGuiTextures;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.Indicator;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public class RadarFilterScreen extends AbstractDataLinkScreen {

    boolean player;
    boolean vs2;
    boolean contraption;
    boolean mob;
    boolean projectile;
    boolean animal;
    boolean item;

    protected IconButton playerButton;
    protected Indicator playerIndicator;
    protected IconButton vs2Button;
    protected Indicator vs2Indicator;
    protected IconButton contraptionButton;
    protected Indicator contraptionIndicator;
    protected IconButton mobButton;
    protected Indicator mobIndicator;
    protected IconButton animalButton;
    protected Indicator animalIndicator;
    protected IconButton projectileButton;
    protected Indicator projectileIndicator;
    protected IconButton itemButton;
    protected Indicator itemIndicator;

    List<String> playerBlacklist;
    List<String> playerWhitelist;
    List<String> vs2Whitelist;

    public RadarFilterScreen(DataLinkBlockEntity be) {
        super(be);
        this.background = ModGuiTextures.DETECTION_FILTER;
        DetectionConfig detectionConfig = DetectionConfig.DEFAULT;
        if (be.getSourceConfig().contains("filter")) {
            detectionConfig = DetectionConfig.fromTag(be.getSourceConfig().getCompound("filter"));
        }
        player = detectionConfig.player();
        vs2 = detectionConfig.vs2();
        contraption = detectionConfig.contraption();
        mob = detectionConfig.mob();
        projectile = detectionConfig.projectile();

        animal = detectionConfig.animal();
        item = detectionConfig.item();
    }


    @Override
    protected void init() {
        super.init();
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

        vs2Button = new IconButton(guiLeft + 60, guiTop + 38, ModGuiTextures.VS2_BUTTON);
        vs2Button.setToolTip(Component.translatable(CreateRadar.MODID + ".radar_button.vs2"));
        vs2Indicator = new Indicator(guiLeft + 60, guiTop + 31, Component.empty());
        vs2Indicator.state = vs2 ? Indicator.State.GREEN : Indicator.State.RED;
        vs2Button.withCallback((x, y) -> {
            vs2 = !vs2;
            vs2Indicator.state = vs2 ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(vs2Button);
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
    public void onClose(CompoundTag tag) {
        super.onClose(tag);
        DetectionConfig detectionConfig = new DetectionConfig(player, vs2, contraption, mob, projectile, animal, item);
        tag.put("filter", detectionConfig.toTag());
    }
}
