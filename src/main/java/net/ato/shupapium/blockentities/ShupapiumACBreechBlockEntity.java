package net.ato.shupapium.blockentities;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Deque;
import java.util.ArrayDeque;
import net.minecraft.world.item.ItemStack;

public class ShupapiumACBreechBlockEntity extends BlockEntity {
    public ShupapiumACBreechBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public Deque<ItemStack> getInputBuffer() { return new ArrayDeque<>(); }
    public ItemStack getMagazine() { return ItemStack.EMPTY; }
}