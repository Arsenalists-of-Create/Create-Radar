package com.happysg.radar.block.radar.sonar.bearing;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.core.Direction;

public class SonarBearingShaftVisual extends OrientedRotatingVisual<SonarBearingBlockEntity> {
    public SonarBearingShaftVisual(VisualizationContext context, SonarBearingBlockEntity be, float partialTick) {
        super(context, be, partialTick, Direction.SOUTH, be.getBlockState().getValue(BearingBlock.FACING).getOpposite(), Models.partial(AllPartialModels.SHAFT_HALF));
    }
}