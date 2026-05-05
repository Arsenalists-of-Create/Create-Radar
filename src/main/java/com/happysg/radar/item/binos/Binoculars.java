package com.happysg.radar.item.binos;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
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
    public void appendHoverText(ItemStack pStack, net.minecraft.world.item.Item.TooltipContext context, List<Component> pTooltipComponents, net.minecraft.world.item.TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, context, pTooltipComponents, pIsAdvanced);
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            pTooltipComponents.add(Component.translatable(CreateRadar.MODID + ".binoculars.base_text"));
        }
        if (pStack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag().contains("filtererPos")) {
            BlockPos monitorPos = net.minecraft.nbt.NbtUtils.readBlockPos(pStack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag(), "filtererPos").orElse(net.minecraft.core.BlockPos.ZERO);
            pTooltipComponents.add(Component.translatable(CreateRadar.MODID + ".binoculars.controller").append(": " + monitorPos.toShortString()));
        } else {
            pTooltipComponents.add(Component.translatable(CreateRadar.MODID + ".binoculars.no_controller"));
        }


    }
    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        BlockPos clickedPos = pContext.getClickedPos();
        Player player = pContext.getPlayer();
        if (player == null) return super.useOn(pContext);

        if (pContext.getLevel().getBlockEntity(clickedPos) instanceof NetworkFiltererBlockEntity blockEntity) {
            player.displayClientMessage(
                    Component.translatable(CreateRadar.MODID + ".binoculars.paired").withStyle(ChatFormatting.BLUE),
                    true
            );

            ItemStack stack = pContext.getItemInHand();
            stack.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY, customData -> customData.update(tag -> {
                tag.put("filterPos", NbtUtils.writeBlockPos(blockEntity.getBlockPos()));
                tag.put("filtererPos", NbtUtils.writeBlockPos(blockEntity.getBlockPos()));
            }));

            return InteractionResult.SUCCESS;
        }
        return super.useOn(pContext);
    }

    public static void setLastHit(ItemStack stack, @Nullable BlockPos pos, @Nullable java.util.UUID subLevelId) {
        stack.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY, customData -> customData.update(tag -> {
            if (pos == null) {
                tag.remove(TAG_LAST_HIT);
                return;
            }

            CompoundTag hit = new CompoundTag();
            hit.putInt("x", pos.getX());
            hit.putInt("y", pos.getY());
            hit.putInt("z", pos.getZ());
            if (subLevelId != null) {
                hit.putUUID("subLevelId", subLevelId);
            }

            tag.put(TAG_LAST_HIT, hit);
        }));
    }

    public static void setLastHit(ItemStack stack, @Nullable BlockPos pos) {
        setLastHit(stack, pos, null);
    }

    @Nullable
    public static BlockPos getLastHit(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        if (tag == null || !tag.contains(TAG_LAST_HIT)) return null;

        CompoundTag hit = tag.getCompound(TAG_LAST_HIT);
        return new BlockPos(
                hit.getInt("x"),
                hit.getInt("y"),
                hit.getInt("z")
        );
    }

    @Nullable
    public static java.util.UUID getLastHitSubLevelId(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        if (tag == null || !tag.contains(TAG_LAST_HIT)) return null;

        CompoundTag hit = tag.getCompound(TAG_LAST_HIT);
        if (hit.hasUUID("subLevelId")) {
            return hit.getUUID("subLevelId");
        }
        return null;
    }

    public static boolean hasLastHit(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        return tag != null && tag.contains(TAG_LAST_HIT);
    }

    public static void clearLastHit(ItemStack stack) {
        stack.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY, customData -> customData.update(tag -> {
            tag.remove(TAG_LAST_HIT);
        }));
    }

}
