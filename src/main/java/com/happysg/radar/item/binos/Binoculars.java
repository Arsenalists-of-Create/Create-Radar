package com.happysg.radar.item.binos;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;

public class Binoculars extends SpyglassItem {
    private static final Logger LOGGER = LogUtils.getLogger();
    // how far the ray should go
    private static final double MAX_DISTANCE = 512.0;

    // step size for walking along the ray (smaller = more accurate, slightly more expensive)
    private static final double STEP = 0.15;
    private static final String TAG_LAST_HIT = "LastHitPos";
    private BlockPos targetBlock;

    public Binoculars(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack pStack, @Nullable TooltipContext pContext, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, pContext, pTooltipComponents, pIsAdvanced);
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            pTooltipComponents.add(Component.translatable(CreateRadar.MODID + ".binoculars.base_text"));
        }

        CompoundTag tag = pStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains("filtererPos")) {
            BlockPos monitorPos =
                    NbtUtils.readBlockPos(tag, "filtererPos").orElse(null);
            pTooltipComponents.add(Component.translatable(CreateRadar.MODID + ".binoculars.controller" + monitorPos.toShortString()));
        } else {
            pTooltipComponents.add(Component.translatable(CreateRadar.MODID + ".binoculars.no_controller"));
        }
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext pContext) {
        BlockPos clickedPos = pContext.getClickedPos();
        Player player = pContext.getPlayer();
        if (player == null) return super.useOn(pContext);

        if (pContext.getLevel().getBlockEntity(clickedPos) instanceof NetworkFiltererBlockEntity blockEntity) {
            player.displayClientMessage(
                    Component.translatable(CreateRadar.MODID + ".binoculars.paired").withStyle(ChatFormatting.BLUE),
                    true
            );

            ItemStack stack = pContext.getItemInHand();
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();


            tag.put("filterPos", NbtUtils.writeBlockPos(blockEntity.getBlockPos()));
            tag.put("filtererPos", NbtUtils.writeBlockPos(blockEntity.getBlockPos()));

            return InteractionResult.SUCCESS;
        }
        return super.useOn(pContext);
    }

    public static void setLastHit(ItemStack stack, @Nullable BlockPos pos) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        if (pos == null) {
            tag.remove(TAG_LAST_HIT);
            return;
        }

        CompoundTag hit = new CompoundTag();
        hit.putInt("x", pos.getX());
        hit.putInt("y", pos.getY());
        hit.putInt("z", pos.getZ());

        tag.put(TAG_LAST_HIT, hit);
    }

    @Nullable
    public static BlockPos getLastHit(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null || !custom.contains(TAG_LAST_HIT)) return null;

        CompoundTag tag = custom.getUnsafe();
        if (!tag.contains(TAG_LAST_HIT)) return null;

        CompoundTag hit = tag.getCompound(TAG_LAST_HIT);
        return new BlockPos(
                hit.getInt("x"),
                hit.getInt("y"),
                hit.getInt("z")
        );
    }

    public static boolean hasLastHit(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        return custom != null && custom.contains(TAG_LAST_HIT);
    }

    public static void clearLastHit(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag != null) tag.remove(TAG_LAST_HIT);
    }

}

