package org.valkyrienskies.clockwork.content.contraptions.phys.bearing;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import org.valkyrienskies.clockwork.platform.api.ContraptionController;

public class PhysBearingBlockEntity extends BlockEntity {
    public PhysBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
    public double getActualAngle() { return 0; }
    public void setAngle(float angle) {}
    public void notifyUpdate() {}
    public ScrollOptionBehaviour<ContraptionController.LockedMode> getMovementMode() { return null; }
}
