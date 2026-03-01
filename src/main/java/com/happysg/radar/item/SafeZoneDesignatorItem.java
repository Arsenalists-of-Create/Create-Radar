package com.happysg.radar.item;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class SafeZoneDesignatorItem extends Item {

    public SafeZoneDesignatorItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public void inventoryTick(ItemStack pStack, Level pLevel, Entity pEntity, int pSlotId, boolean pIsSelected) {
        super.inventoryTick(pStack, pLevel, pEntity, pSlotId, pIsSelected);
        if (pIsSelected) {
            CompoundTag data = pStack.getOrCreateTag();
            if (data.contains("monitorPos")) {
                BlockPos monitorPos = NbtUtils.readBlockPos(data.getCompound("monitorPos"));
                if (pLevel.getBlockEntity(monitorPos) instanceof NetworkFiltererBlockEntity nbe && pLevel.isClientSide) {
                    nbe.showSafeZone();
                }
            }
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        BlockPos pos = pContext.getClickedPos();
        Level level = pContext.getLevel();
        ItemStack stack = pContext.getItemInHand();
        CompoundTag data = stack.getOrCreateTag();
        Player player = pContext.getPlayer();

        if (player == null) {
            return InteractionResult.FAIL;
        }
        boolean isCrouching = player.isCrouching();
        if (level.getBlockEntity(pos) instanceof NetworkFiltererBlockEntity networkFiltererBlockEntity) {
            data.put("monitorPos", NbtUtils.writeBlockPos(networkFiltererBlockEntity.getBlockPos()));
            displayMessage(player, CreateRadar.MODID + ".item.safe_zone_designator.set", ChatFormatting.GREEN);
            return InteractionResult.SUCCESS;
        }

        if (!data.contains("monitorPos")) {
            displayMessage(player, CreateRadar.MODID + ".item.safe_zone_designator.no_monitor", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        BlockPos monitorPos = NbtUtils.readBlockPos(data.getCompound("monitorPos"));

        if (!data.contains("startPos")) {
            if (level.getBlockEntity(monitorPos) instanceof NetworkFiltererBlockEntity networkFiltererBlockEntity) {
                if (networkFiltererBlockEntity.tryRemoveAABB(pos)) {
                    displayMessage(player, CreateRadar.MODID + ".item.safe_zone_designator.remove", ChatFormatting.RED);
                    return InteractionResult.SUCCESS;
                }
            }
            data.put("startPos", NbtUtils.writeBlockPos(pos));
            displayMessage(player, CreateRadar.MODID + ".item.safe_zone_designator.start", ChatFormatting.GREEN);
        } else {
            if (player.isCrouching()) {
                data.remove("startPos");
                displayMessage(player, CreateRadar.MODID + ".item.safe_zone_designator.reset", ChatFormatting.RED);
                return InteractionResult.SUCCESS;
            }

            BlockPos startPos = NbtUtils.readBlockPos(data.getCompound("startPos"));

            if (level.getBlockEntity(monitorPos) instanceof NetworkFiltererBlockEntity networkFiltererBlockEntity) {
                networkFiltererBlockEntity.addSafeZone(startPos, pos);
                displayMessage(player, CreateRadar.MODID + ".item.safe_zone_designator.end", ChatFormatting.GREEN);
                data.remove("startPos");
            } else {
                displayMessage(player, CreateRadar.MODID + ".item.safe_zone_designator.no_monitor", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
        }

        return InteractionResult.SUCCESS;
    }

    private void displayMessage(Player player, String messageKey, ChatFormatting color) {
        player.displayClientMessage(Component.translatable(messageKey).withStyle(color), true);
    }

    @Override
    public void appendHoverText(ItemStack pStack, Level pLevel, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced);
        if (pStack.getOrCreateTag().contains("monitorPos")) {
            BlockPos monitorPos = NbtUtils.readBlockPos(pStack.getOrCreateTag().getCompound("monitorPos"));
            pTooltipComponents.add(Component.translatable(CreateRadar.MODID + ".guided_fuze.linked_monitor", monitorPos));
        } else
            pTooltipComponents.add(Component.translatable(CreateRadar.MODID + ".guided_fuze.no_monitor"));
    }

    @Nullable
    public BlockPos getMonitorPos(ItemStack stack) {
        CompoundTag data = stack.getOrCreateTag();
        if (data.contains("monitorPos")) {
            return NbtUtils.readBlockPos(data.getCompound("monitorPos"));
        }
        return null;
    }
}
