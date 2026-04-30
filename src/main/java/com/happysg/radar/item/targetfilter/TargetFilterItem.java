package com.happysg.radar.item.targetfilter;

import com.happysg.radar.CreateRadar;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

public class TargetFilterItem extends Item {

    public TargetFilterItem(Properties properties) {
        super(properties);
    }

    @OnlyIn(Dist.CLIENT)
    private InteractionResultHolder<ItemStack> clientFunc(net.minecraft.world.level.Level level, Player player, InteractionHand hand) {
        Minecraft.getInstance().setScreen(new AutoTargetScreen());
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide)
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        return clientFunc(level, player, hand);
    }


}