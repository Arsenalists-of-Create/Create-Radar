package com.happysg.radar.compat.cbcwpf;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import java.util.Deque;

public class ShupapiumACBreechBlockEntity extends BlockEntity {
    public ShupapiumACBreechBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        super(type, pos, state);
    }
    public Deque<ItemStack> getInputBuffer() { return null; }
    public ItemStack getMagazine() { return ItemStack.EMPTY; }
}
