package com.happysg.radar;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class NbtHelper {
    public static CompoundTag getTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static void setTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void updateTag(ItemStack stack, java.util.function.Consumer<CompoundTag> consumer) {
        CompoundTag tag = getTag(stack);
        consumer.accept(tag);
        setTag(stack, tag);
    }
}