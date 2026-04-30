package riftyboi.cbcmodernwarfare.munitions.munitions_contraption_launcher.guidance;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import riftyboi.cbcmodernwarfare.munitions.contraptions.MunitionsPhysicsContraptionEntity;
public class GuidanceBlockEntity extends BlockEntity {
    public GuidanceBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public void tickMissileGuidance(MunitionsPhysicsContraptionEntity missile) {}
}
