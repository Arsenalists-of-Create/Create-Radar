package com.happysg.radar.block.radar.plane;

import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.behavior.RadarScanningBlockBehavior;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.config.RadarConfig;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;

import java.util.Collection;
import java.util.List;

public class StationaryRadarBlockEntity extends SmartBlockEntity implements IRadar {
    private RadarScanningBlockBehavior scanningBehavior;


    public StationaryRadarBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void initialize() {
        super.initialize();
        if (!Mods.SABLE.isLoaded())
            return;
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
        if (level == null) {
            return;
        }

        scanningBehavior.setRunning(true);
        scanningBehavior.setScanPos(PhysicsHandler.getWorldVec(this));

        Direction facing = getScanFacing();
        Vec3 facingVec = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        Vec3 shipVec = PhysicsHandler.getWorldVecDirectionTransform(facingVec, this);
        double angle = Math.toDegrees(Math.atan2(shipVec.x, shipVec.z));
        scanningBehavior.setAngle((angle + 360) % 360);
    }


    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        scanningBehavior = new RadarScanningBlockBehavior(this);
        scanningBehavior.setRunning(true);
        scanningBehavior.setRange(RadarConfig.server().planeRadarRange.get());
        scanningBehavior.setAngle((getScanFacing().toYRot() + 360) % 360);
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
        Direction scanFacing = getScanFacing();
        if(!Mods.SABLE.isLoaded())return getAngleForDirection(scanFacing);
        SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(level, getBlockPos());
        if(ship == null) return getAngleForDirection(scanFacing);

        Vec3 facingVec = new Vec3(scanFacing.getStepX(), scanFacing.getStepY(), scanFacing.getStepZ());
        Vec3 worldVec = PhysicsHandler.getWorldVecDirectionTransform(facingVec, this);
        return (float) ((Math.toDegrees(Math.atan2(worldVec.x, worldVec.z)) + 360) % 360);
    }

    @Override
    public boolean renderRelativeToMonitor() {
        return true;
    }
    @Override
    public String getRadarType(){
        return "nonspinning";
    }

    @Override
    public Direction getradarDirection() {
        return getBlockState().getValue(StationaryRadarBlock.FACING);
    }

    private Direction getScanFacing() {
        return getBlockState().getValue(StationaryRadarBlock.FACING).getOpposite();
    }

    private float getAngleForDirection(Direction direction) {
        Vec3 vec = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        return (float) ((Math.toDegrees(Math.atan2(vec.x, vec.z)) + 360) % 360);
    }
}
