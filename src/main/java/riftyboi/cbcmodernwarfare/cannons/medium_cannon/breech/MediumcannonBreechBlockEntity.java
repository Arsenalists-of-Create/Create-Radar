package riftyboi.cbcmodernwarfare.cannons.medium_cannon.breech;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
public class MediumcannonBreechBlockEntity extends BlockEntity {
    public MediumcannonBreechBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public ItemStack getInputBuffer() { return ItemStack.EMPTY; }
}
