package com.happysg.radar.block.guidance;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.compat.cbcmw.CBCMWCompatRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RadarGuidanceBlockItem extends BlockItem {
    public RadarGuidanceBlockItem(Block pBlock, Properties pProperties) {
        super(pBlock, pProperties);
    }

    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        BlockPos clickedPos = pContext.getClickedPos();
        ItemStack itemStack = pContext.getItemInHand();
        if (pContext.getLevel().getBlockEntity(clickedPos) instanceof MonitorBlockEntity blockEntity) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("monitorPos", blockEntity.getController().getBlockPos().asLong());
            BlockItem.setBlockEntityData(itemStack, CBCMWCompatRegister.RADAR_GUIDANCE_BLOCK_ENTITY.get(), tag);
            return InteractionResult.SUCCESS;
        }
        return super.useOn(pContext);
    }

    @Override
    public void appendHoverText(ItemStack pStack, @Nullable TooltipContext pContext, List<Component> pTooltip, TooltipFlag pFlag) {
        CustomData blockData = pStack.get(DataComponents.BLOCK_ENTITY_DATA);
        CompoundTag tag = blockData != null ? blockData.getUnsafe() : null;

        if (tag != null && tag.contains("monitorPos")) {
            BlockPos monitorPos = BlockPos.of(tag.getLong("monitorPos"));
            pTooltip.add(Component.translatable(CreateRadar.MODID + ".guided_fuze.linked_monitor", monitorPos));
        } else {
            pTooltip.add(Component.translatable(CreateRadar.MODID + ".guided_fuze.no_monitor"));
        }
        super.appendHoverText(pStack, pContext, pTooltip, pFlag);
    }
}
