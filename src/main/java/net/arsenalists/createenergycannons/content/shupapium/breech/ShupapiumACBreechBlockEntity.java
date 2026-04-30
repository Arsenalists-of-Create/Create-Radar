package net.arsenalists.createenergycannons.content.shupapium.breech;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Deque;
import net.minecraft.world.item.ItemStack;

public class ShupapiumACBreechBlockEntity extends BlockEntity {
    public ShupapiumACBreechBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public Deque<ItemStack> getInputBuffer() { return new java.util.ArrayDeque<>(); }
    public ItemStack getMagazine() { return ItemStack.EMPTY; }
}
