package rbasamanni.createbigcannons.cannons.autocannon.breech;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;

public class MediumcannonBreechBlockEntity extends BlockEntity {
    public MediumcannonBreechBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        super(type, pos, state);
    }
    public ItemStack getInputBuffer() { return ItemStack.EMPTY; }
}
