package com.happysg.radar.block.radar.sonar;

import com.happysg.radar.block.radar.sonar.bearing.SonarBearingBlock;
import com.happysg.radar.block.radar.sonar.bearing.SonarBearingBlockEntity;
import com.happysg.radar.registry.ModBlocks;
import com.simibubi.create.AllContraptionTypes;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.apache.commons.lang3.tuple.Pair;

public class SonarContraption extends BearingContraption {

    private BlockPos bearingPos;
    private BlockPos endSensor;
    private int panelCount;

    public SonarContraption() {
    }

    @Override
    public boolean assemble(Level level, BlockPos pos) throws AssemblyException {
        bearingPos = pos;

        if (pos.getY() >= SonarBearingBlockEntity.MAX_BEARING_Y_EXCLUSIVE) {
            throw new AssemblyException(Component.literal("Sonar bearing must be below Y 64"));
        }

        BlockState bearingState = level.getBlockState(pos);

        if (!(bearingState.getBlock() instanceof SonarBearingBlock)) {
            return false;
        }

        facing = bearingState.getValue(SonarBearingBlock.FACING);

        boolean assembled = super.assemble(level, pos);

        if (!assembled || panelCount <= 0) {
            throw new AssemblyException(Component.literal("Sonar requires at least one sensor on the top of the bearing"));
        }

        if (!isNearSurface(level)) {
            throw new AssemblyException(Component.literal("The end sonar sensor must be near a surface"));
        }

        return true;
    }

    @Override
    protected boolean movementAllowed(BlockState state, Level level, BlockPos pos) {
        if (!ModBlocks.SONAR_SENSOR.has(state)) {
            return false;
        }

        if (bearingPos == null) {
            return false;
        }

        if (!isOnSensorLine(pos)) {
            return false;
        }

        return super.movementAllowed(state, level, pos);
    }

    @Override
    public void addBlock(Level level, BlockPos pos, Pair<StructureTemplate.StructureBlockInfo, BlockEntity> capture) {
        BlockState state = capture.getKey().state();

        if (!ModBlocks.SONAR_SENSOR.has(state)) {
            return;
        }

        if (bearingPos == null || !isOnSensorLine(pos)) {
            return;
        }

        super.addBlock(level, pos, capture);
        panelCount++;

        if (endSensor == null || distanceFromBearing(pos) > distanceFromBearing(endSensor)) {
            endSensor = pos.immutable();
        }
    }

    private boolean isOnSensorLine(BlockPos pos) {
        if (bearingPos == null || facing == null) {
            return false;
        }

        int dx = pos.getX() - bearingPos.getX();
        int dy = pos.getY() - bearingPos.getY();
        int dz = pos.getZ() - bearingPos.getZ();

        return switch (facing) {
            case EAST -> dx > 0 && dy == 0 && dz == 0;
            case WEST -> dx < 0 && dy == 0 && dz == 0;
            case UP -> dy > 0 && dx == 0 && dz == 0;
            case DOWN -> dy < 0 && dx == 0 && dz == 0;
            case SOUTH -> dz > 0 && dx == 0 && dy == 0;
            case NORTH -> dz < 0 && dx == 0 && dy == 0;
        };
    }

    private int distanceFromBearing(BlockPos pos) {
        return Math.abs(pos.getX() - bearingPos.getX()) + Math.abs(pos.getY() - bearingPos.getY()) + Math.abs(pos.getZ() - bearingPos.getZ());
    }

    private boolean isNearSurface(Level level) {
        if (endSensor == null || facing == null) {
            return false;
        }

        for (int gap = 0; gap <= SonarBearingBlockEntity.MAX_GROUND_AIR_GAP; gap++) {
            BlockPos surfacePos = endSensor.relative(facing, gap + 1);
            BlockState surfaceState = level.getBlockState(surfacePos);
            Direction contactFace = facing.getOpposite();

            if (surfaceState.isFaceSturdy(level, surfacePos, contactFace)) {
                return true;
            }
        }

        return false;
    }

    public int getPanelCount() {
        return panelCount;
    }

    public Direction getFacingDirection() {
        return facing;
    }

    @Override
    public CompoundTag writeNBT(HolderLookup.Provider registries, boolean spawnPacket) {
        CompoundTag tag = super.writeNBT(registries, spawnPacket);
        tag.putInt("PanelCount", panelCount);
        return tag;
    }

    @Override
    public void readNBT(Level level, CompoundTag tag, boolean spawnData) {
        super.readNBT(level, tag, spawnData);

        panelCount = tag.getInt("PanelCount");
    }

    @Override
    public ContraptionType getType() {
        return AllContraptionTypes.BEARING.value();
    }
}