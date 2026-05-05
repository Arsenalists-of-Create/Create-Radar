package com.happysg.radar.block.radar.bearing;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.radar.receiver.AbstractRadarFrame;
import com.happysg.radar.block.radar.receiver.RadarReceiverBlock;
import com.happysg.radar.registry.ModBlocks;
import com.happysg.radar.registry.ModContraptionTypes;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.api.contraption.ContraptionType;
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
        this(Direction.UP);
    }

    public RadarContraption(Direction facing) {
        this.facing = facing;
    }

    public void setFacing(Direction facing) {
        this.facing = facing;
    }

    @Override
    public boolean assemble(Level world, BlockPos pos) throws AssemblyException {
        boolean assembled = super.assemble(world, pos);
        if (!hasReceiver()) {
            throw new AssemblyException(Component.translatable(CreateRadar.MODID + ".radar.no_receiver"));
        }
        return assembled;
    }


    @Override
    public void addBlock(Level level, BlockPos pos, Pair<StructureTemplate.StructureBlockInfo, BlockEntity> capture) {
        net.minecraft.world.level.block.Block block = capture.getKey().state().getBlock();
        if (!(block instanceof AbstractRadarFrame || block instanceof RadarReceiverBlock)) {
            return;
        }

        if (capture.getKey().state().getBlock() instanceof DataLinkBlock)
            return;

        if (capture.getKey().state().getBlock() instanceof DisplayLinkBlock)
            return;

        super.addBlock(level, pos, capture);

        if (ModBlocks.CREATIVE_RADAR_PLATE_BLOCK.has(capture.getKey().state()))
            creative = true;

        if (capture.getKey().state().getBlock() instanceof AbstractRadarFrame) {
            dishCount++;
            com.happysg.radar.CreateRadar.getLogger().info("RADAR CONTRAPTION {}: Added dish block (total={})", pos, dishCount);
        }

        if (capture.getKey().state().getBlock() instanceof RadarReceiverBlock) {
            hasReceiver = true;
            receiverFacing = capture.getKey().state().getValue(RadarReceiverBlock.FACING);
            com.happysg.radar.CreateRadar.getLogger().info("RADAR CONTRAPTION {}: Added receiver", pos);
        }
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

    public java.util.List<com.happysg.radar.block.radar.track.RadarTrack> getTracks() {
        return java.util.Collections.emptyList();
    }
}
