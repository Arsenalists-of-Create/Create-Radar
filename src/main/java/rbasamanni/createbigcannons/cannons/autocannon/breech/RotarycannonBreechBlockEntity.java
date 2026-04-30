package rbasamanni.createbigcannons.cannons.autocannon.breech;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import com.happysg.radar.compat.stub.IItemHandler;

public class RotarycannonBreechBlockEntity extends BlockEntity {
    public RotarycannonBreechBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        super(type, pos, state);
    }
    public IItemHandler createItemHandler() { return null; }
}
