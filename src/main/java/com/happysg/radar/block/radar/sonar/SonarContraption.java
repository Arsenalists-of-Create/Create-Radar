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
import java.util.HashSet;
import java.util.Set;

public class SonarContraption extends BearingContraption {

    private BlockPos bearingPos;
    private BlockPos rootPos;
    private final Set<BlockPos> sensorPositions = new HashSet<>();
    private int panelCount;

    public SonarContraption() {
    }

    @Override
    public boolean assemble(Level level, BlockPos pos) throws AssemblyException {
        bearingPos = pos;
        rootPos = null;

        sensorPositions.clear();
        panelCount = 0;

        if (pos.getY() >= SonarBearingBlockEntity.MAX_BEARING_Y_EXCLUSIVE) {
            throw new AssemblyException(Component.literal("Sonar bearing must be below Y 64"));
        }

        BlockState bearingState = level.getBlockState(pos);

        if (!(bearingState.getBlock() instanceof SonarBearingBlock)) {
            return false;
        }

        facing = bearingState.getValue(SonarBearingBlock.FACING);

        if (facing != Direction.UP && facing != Direction.DOWN) {
            throw new AssemblyException(Component.literal("Sonar bearing must face up or down"));
        }

        rootPos = bearingPos.relative(facing);

        if (!ModBlocks.SONAR_SENSOR.has(level.getBlockState(rootPos))) {
            throw new AssemblyException(Component.literal("Sonar requires a sensor directly above or below the bearing"));
        }

        boolean assembled = super.assemble(level, pos);

        if (!assembled || sensorPositions.isEmpty()) {
            throw new AssemblyException(Component.literal("Sonar requires at least one sensor"));
        }

        if (!sensorPositions.contains(rootPos)) {
            throw new AssemblyException(Component.literal("Sonar root sensor was not assembled"));
        }

        validateSensorPlane();
        panelCount = calculatePanelCount();

        if (!isRootNearSurface(level)) {
            throw new AssemblyException(Component.literal("The sonar root must be near a surface"));
        }

        return true;
    }

    @Override
    protected boolean movementAllowed(BlockState state, Level level, BlockPos pos) {
        return ModBlocks.SONAR_SENSOR.has(state) && super.movementAllowed(state, level, pos);
    }

    @Override
    public void addBlock(Level level, BlockPos pos, Pair<StructureTemplate.StructureBlockInfo, BlockEntity> capture) {
        BlockState state = capture.getKey().state();

        if (!ModBlocks.SONAR_SENSOR.has(state)) {
            return;
        }

        if (bearingPos == null) {
            return;
        }

        BlockPos sensorPos = pos.immutable();

        if (!sensorPositions.add(sensorPos)) {
            return;
        }

        super.addBlock(level, pos, capture);
    }

    private boolean isRootNearSurface(Level level) {
        if (rootPos == null || facing == null) {
            return false;
        }

        Direction contactFace = facing.getOpposite();

        for (int gap = 0;
             gap <= SonarBearingBlockEntity.MAX_GROUND_AIR_GAP;
             gap++) {

            BlockPos surfacePos = rootPos.relative(facing, gap + 1);
            BlockState surfaceState = level.getBlockState(surfacePos);

            if (surfaceState.isFaceSturdy(level, surfacePos, contactFace)) {
                return true;
            }
        }

        return false;
    }

    public Set<BlockPos> getSensorPositions() {
        return Set.copyOf(sensorPositions);
    }

    public int getPanelCount() {
        return panelCount;
    }

    public Direction getFacingDirection() {
        return facing;
    }

    private void validateSensorPlane() throws AssemblyException {
        if (rootPos == null) {
            throw new AssemblyException(
                    Component.literal("Sonar root is missing")
            );
        }

        int rootY = rootPos.getY();

        for (BlockPos sensorPos : sensorPositions) {
            if (sensorPos.getY() != rootY) {
                throw new AssemblyException(
                        Component.literal("All sonar sensors must be on the same horizontal plane")
                );
            }
        }
    }

    private int calculatePanelCount() {
        if (sensorPositions.isEmpty()) {
            return 0;
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos sensorPos : sensorPositions) {
            minX = Math.min(minX, sensorPos.getX());
            maxX = Math.max(maxX, sensorPos.getX());

            minZ = Math.min(minZ, sensorPos.getZ());
            maxZ = Math.max(maxZ, sensorPos.getZ());
        }

        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;

        return Math.min(width, depth);
    }

    @Override
    public CompoundTag writeNBT(HolderLookup.Provider registries, boolean spawnPacket) {
        CompoundTag tag = super.writeNBT(registries, spawnPacket);
        tag.putInt("PanelCount", panelCount);
        long[] sensors = sensorPositions.stream().mapToLong(BlockPos::asLong).toArray();
        tag.putLongArray("SensorPositions", sensors);
        return tag;
    }

    @Override
    public void readNBT(Level level, CompoundTag tag, boolean spawnData) {
        super.readNBT(level, tag, spawnData);
        panelCount = tag.getInt("PanelCount");
        sensorPositions.clear();

        for (long packed : tag.getLongArray("SensorPositions")) {
            sensorPositions.add(BlockPos.of(packed));
        }
    }

    @Override
    public ContraptionType getType() {
        return AllContraptionTypes.BEARING.value();
    }
}