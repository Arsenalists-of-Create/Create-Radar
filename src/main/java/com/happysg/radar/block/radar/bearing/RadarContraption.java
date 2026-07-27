package com.happysg.radar.block.radar.bearing;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.radar.receiver.AbstractRadarFrame;
import com.happysg.radar.block.radar.receiver.RadarReceiverBlock;
import com.happysg.radar.registry.ModBlocks;
import com.simibubi.create.AllContraptionTypes;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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

    @Override
    protected boolean movementAllowed(BlockState state, Level world, BlockPos pos) {
        return isRadarPart(state) && super.movementAllowed(state, world, pos);
    }

    @Override
    public void addBlock(Level level, BlockPos pos, Pair<StructureTemplate.StructureBlockInfo, BlockEntity> capture) {
        BlockState state = capture.getKey().state();

        if (state.getBlock() instanceof DataLinkBlock || state.getBlock() instanceof DisplayLinkBlock) {
            return;
        }

        if (!isRadarPart(state)) {
            return;
        }

        super.addBlock(level, pos, capture);

        if (ModBlocks.CREATIVE_RADAR_PLATE_BLOCK.has(state)) {
            creative = true;
        }

        // todo replace with tag instead of block instance check
        if (state.getBlock() instanceof AbstractRadarFrame) {
            dishCount++;
        }

        if (state.getBlock() instanceof RadarReceiverBlock) {
            hasReceiver = true;
            receiverFacing = state.getValue(RadarReceiverBlock.FACING);
        }
    }

    private static boolean isRadarPart(BlockState state) {
        return state.getBlock() instanceof AbstractRadarFrame || state.getBlock() instanceof RadarReceiverBlock;
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
        return AllContraptionTypes.BEARING.value();
    }
}
