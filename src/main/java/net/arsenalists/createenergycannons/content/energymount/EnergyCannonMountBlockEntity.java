package net.arsenalists.createenergycannons.content.energymount;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
public class EnergyCannonMountBlockEntity extends CannonMountBlockEntity {
    public EnergyCannonMountBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    // removed isReadyToFire override
}
