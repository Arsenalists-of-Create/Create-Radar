package com.happysg.radar.block.radar.plane;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.behavior.RadarScanningBlockBehavior;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.config.RadarConfig;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

public class StationaryRadarBlockEntity extends SmartBlockEntity implements IRadar {
    private RadarScanningBlockBehavior scanningBehavior;
    private BlockPos lastKnownPos;

    public StationaryRadarBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        lastKnownPos = pos.immutable();
    }

    @Override
    public void initialize() {
        super.initialize();
        scanningBehavior.setScanPos(PhysicsHandler.getWorldVec(this));
        scanningBehavior.setRunning(true);
    }

    @Override
    public BlockPos getWorldPos() {
        return getBlockPos();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;

        scanningBehavior.setRunning(true);
        scanningBehavior.setRange(getRange());
        scanningBehavior.setScanPos(PhysicsHandler.getWorldVec(this));
        scanningBehavior.setAngle(getGlobalAngle());

        if (level instanceof ServerLevel serverLevel && serverLevel.getGameTime() % 40 == 0
                && !lastKnownPos.equals(worldPosition)) {
            NetworkData data = NetworkData.get(serverLevel);
            boolean previousStillPresent = serverLevel.hasChunkAt(lastKnownPos)
                    && serverLevel.getBlockEntity(lastKnownPos) instanceof StationaryRadarBlockEntity;
            if (previousStillPresent
                    || data.getFiltererForEndpoint(serverLevel.dimension(), lastKnownPos) == null
                    || data.getFiltererForEndpoint(serverLevel.dimension(), worldPosition) != null
                    || data.updateRadarPosition(serverLevel.dimension(), lastKnownPos, worldPosition)) {
                lastKnownPos = worldPosition.immutable();
                setChanged();
            }
        }
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        scanningBehavior = new RadarScanningBlockBehavior(this);
        scanningBehavior.setRunning(true);
        scanningBehavior.setRange(RadarConfig.server().planeRadarRange.get());
        scanningBehavior.setAngle(getGlobalAngle());
        scanningBehavior.setScanPos(PhysicsHandler.getWorldVec(this));
        scanningBehavior.setTrackExpiration(1);
        behaviours.add(scanningBehavior);
    }

    @Override
    public Collection<RadarTrack> getTracks() {
        return scanningBehavior.getRadarTracks();
    }

    @Override
    public float getRange() {
        return RadarConfig.server().planeRadarRange.get();
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public float getGlobalAngle() {
        Direction scanFacing = getBlockState().getValue(StationaryRadarBlock.FACING).getOpposite();
        Vec3 facing = new Vec3(scanFacing.getStepX(), scanFacing.getStepY(), scanFacing.getStepZ());
        Vec3 worldFacing = level == null ? facing : PhysicsHandler.getWorldVecDirectionTransform(facing, this);
        return (float) ((Math.toDegrees(Math.atan2(worldFacing.x, worldFacing.z)) + 360) % 360);
    }

    @Override
    public boolean renderRelativeToMonitor() {
        return level != null && PhysicsHandler.isBlockInShipyard(level, worldPosition);
    }

    @Override
    public String getRadarType() {
        return "nonspinning";
    }

    @Override
    public Direction getradarDirection() {
        return getBlockState().getValue(StationaryRadarBlock.FACING);
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        lastKnownPos = compound.contains("LastKnownPos")
                ? BlockPos.of(compound.getLong("LastKnownPos")) : worldPosition.immutable();
    }

    @Override
    public void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        compound.putLong("LastKnownPos", lastKnownPos.asLong());
    }
}
