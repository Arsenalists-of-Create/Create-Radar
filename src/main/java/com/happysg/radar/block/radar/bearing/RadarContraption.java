package com.happysg.radar.block.radar.bearing;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.radar.receiver.AbstractRadarFrame;
import com.happysg.radar.block.radar.receiver.RadarReceiverBlock;
import com.happysg.radar.registry.ModBlocks;
import com.happysg.radar.registry.ModContraptionTypes;
import com.simibubi.create.content.contraptions.AssemblyException;

import com.simibubi.create.content.contraptions.ContraptionType;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.apache.commons.lang3.tuple.Pair;

public class RadarContraption extends BearingContraption {

    private int dishCount;
    private boolean hasReceiver;
    private boolean creative;
    private Direction receiverFacing;

    public RadarContraption() {
        facing = Direction.UP;
    }

    @Override
    public boolean assemble(Level world, BlockPos pos) throws AssemblyException {
        boolean assembled = super.assemble(world, pos);
        if (!hasReceiver()) {
            throw new AssemblyException(Component.translatable(CreateRadar.MODID + ".radar.no_receiver"));
        }
        return assembled;
    }




    public int getDishCount() {
        return dishCount;
    }

    public boolean hasReceiver() {
        return hasReceiver;
    }

    public Direction getReceiverFacing() {
        return receiverFacing;
    }

    public boolean isCreative() {
        return creative;
    }


    @Override
    public ContraptionType getType() {
        return ModContraptionTypes.RADAR_BEARING;
    }
}
