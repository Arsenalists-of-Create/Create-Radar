package com.happysg.radar.item;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

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
            CompoundTag data = pStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (data.contains("monitorPos")) {
                BlockPos monitorPos = NbtUtils.readBlockPos(data, "monitorPos").orElse(null);
                if (pLevel.getBlockEntity(monitorPos) instanceof MonitorBlockEntity monitorBlockEntity && pLevel.isClientSide) {
                    monitorBlockEntity.showSafeZone();
                }
            }
        }
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext pContext) {
        BlockPos pos = pContext.getClickedPos();
        Level level = pContext.getLevel();
        ItemStack stack = pContext.getItemInHand();
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        Player player = pContext.getPlayer();

        if (player == null) {
            return InteractionResult.FAIL;
        }
        boolean isCrouching = player.isCrouching();
        if (level.getBlockEntity(pos) instanceof MonitorBlockEntity monitorBlockEntity) {
            data.put("monitorPos", NbtUtils.writeBlockPos(monitorBlockEntity.getControllerPos()));
            displayMessage(player, CreateRadar.MODID + ".item.safe_zone_designator.set", ChatFormatting.GREEN);
            return InteractionResult.SUCCESS;
        }

        if (!data.contains("monitorPos")) {
            displayMessage(player, CreateRadar.MODID + ".item.safe_zone_designator.no_monitor", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        BlockPos monitorPos = NbtUtils.readBlockPos(data, "monitorPos").orElse(null);

        if (!data.contains("startPos")) {
            if (level.getBlockEntity(monitorPos) instanceof MonitorBlockEntity monitorBlockEntity) {
                if (monitorBlockEntity.getController().tryRemoveAABB(pos)) {
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

            BlockPos startPos = NbtUtils.readBlockPos(data, "startPos").orElse(null);

            if (level.getBlockEntity(monitorPos) instanceof MonitorBlockEntity monitorBlockEntity) {
                monitorBlockEntity.addSafeZone(startPos, pos);
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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);

        if (custom != null) {
            CompoundTag tag = custom.getUnsafe();
            BlockPos monitorPos = NbtUtils.readBlockPos(tag, "monitorPos").orElse(null);

            if (monitorPos != null) {
                tooltip.add(Component.translatable(CreateRadar.MODID + ".guided_fuze.linked_monitor", monitorPos));
                return;
            }
        }

        tooltip.add(Component.translatable(CreateRadar.MODID + ".guided_fuze.no_monitor"));
    }

    @Nullable
    public BlockPos getMonitorPos(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data.contains("monitorPos")) {
            return NbtUtils.readBlockPos(data, "monitorPos").orElse(null);
        }
        return null;
    }
}
