package riftyboi.cbcmodernwarfare.munitions.munitions_contraption_launcher.guidance;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import riftyboi.cbcmodernwarfare.munitions.munitions_contraption_launcher.guidance.infrared_homing.InfraredSeekerProperties;
public class MunitionsLauncherGuidanceBlock extends Block {
    public MunitionsLauncherGuidanceBlock(Properties properties) { super(properties); }
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty HORIZONTAL_FACING = null;
    public void tickGuidance(Level level, BlockPos pos, BlockState state, BlockEntity be, OrientedContraptionEntity oriented) {}
    public InfraredSeekerProperties getInfraredProperties() { return null; }
    public Object getBallisticProperties() { return null; }
    public Object getDamageProperties() { return null; }
}
