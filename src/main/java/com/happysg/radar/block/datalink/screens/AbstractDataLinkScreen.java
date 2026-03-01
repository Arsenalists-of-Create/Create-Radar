package com.happysg.radar.block.datalink.screens;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.datalink.DataController;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.happysg.radar.block.datalink.DataPeripheral;
import com.happysg.radar.networking.ModMessages;
import com.happysg.radar.networking.packets.RadarLinkConfigurationPacket;
import com.happysg.radar.registry.AllDataBehaviors;
import com.happysg.radar.registry.ModGuiTextures;

import com.jozufozu.flywheel.util.transform.TransformStack;
import com.mojang.blaze3d.vertex.PoseStack;

import com.simibubi.create.foundation.gui.AbstractSimiScreen;
import com.simibubi.create.foundation.gui.AllIcons;

import com.simibubi.create.foundation.gui.element.GuiGameElement;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

public class AbstractDataLinkScreen extends AbstractSimiScreen {

    private static final ItemStack FALLBACK = new ItemStack(Items.BARRIER);

    protected ModGuiTextures background;
    private final DataLinkBlockEntity blockEntity;
    private IconButton confirmButton;

    BlockState sourceState;
    BlockState targetState;
    DataPeripheral source;
    DataController target;

    public AbstractDataLinkScreen(DataLinkBlockEntity be) {
        this.blockEntity = be;
    }

    @Override
    protected void init() {
        setWindowSize(background.width, background.height);
        super.init();
        clearWidgets();

        int x = guiLeft;
        int y = guiTop;

        initGathererOptions();

        confirmButton = new IconButton(x + background.width - 33, y + background.height - 24, AllIcons.I_CONFIRM);
        confirmButton.withCallback(this::onClose);
        addRenderableWidget(confirmButton);
    }

    @SuppressWarnings("deprecation")
    private void initGathererOptions() {
        ClientLevel level = minecraft.level;
        sourceState = level.getBlockState(blockEntity.getSourcePosition());
        targetState = level.getBlockState(blockEntity.getTargetPosition());

        int x = guiLeft;
        int y = guiTop;

        source = AllDataBehaviors.sourcesOf(level, blockEntity.getSourcePosition());
        target = AllDataBehaviors.targetOf(level, blockEntity.getTargetPosition());
    }


    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft;
        int y = guiTop;

        background.render(graphics, x, y);
        MutableComponent header = Component.translatable(CreateRadar.MODID + ".data_link.title");
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
        GuiGameElement.of(blockEntity.getBlockState()
                        .setValue(DataLinkBlock.FACING, Direction.DOWN)) //UP
                .render(graphics);
        ms.popPose();
    }

    @Override
    public void onClose() {
        super.onClose();
        CompoundTag sourceData = new CompoundTag();
        if (source != null) {
            sourceData.putString("Id", source.id.toString());
            onClose(sourceData);
        }

        ModMessages.sendToServer(new RadarLinkConfigurationPacket(blockEntity.getBlockPos(), sourceData));
    }

    public void onClose(CompoundTag tag) {
    }
}
