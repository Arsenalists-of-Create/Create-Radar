package com.happysg.radar.block.radar.plane;

import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.behavior.RadarScanningBlockBehavior;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.compat.vs2.VS2Utils;
import com.happysg.radar.config.RadarConfig;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

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
        if (!Mods.VALKYRIENSKIES.isLoaded())
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
        if (!Mods.VALKYRIENSKIES.isLoaded() && !VS2Utils.isBlockInShipyard(level,worldPosition)){
            scanningBehavior.setRunning(false);
            return;
        }else if(!isRunning() && VS2Utils.isBlockInShipyard(level,worldPosition))
            scanningBehavior.setRunning(true);
        Direction facing = getBlockState().getValue(StationaryRadarBlock.FACING).getOpposite();
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
        scanningBehavior.setAngle((getBlockState().getValue(StationaryRadarBlock.FACING).toYRot() + 360) % 360);
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
        if(!Mods.VALKYRIENSKIES.isLoaded())return 0;
        Ship ship = VSGameUtilsKt.getShipManagingPos(level,getBlockPos());
        if(ship == null) return 0;

        // get yaw for rotating correctly for plane radar
        org.joml.Quaterniondc shipRot = ship.getTransform().getShipToWorldRotation();
        org.joml.Vector3d fwd = new org.joml.Vector3d(0, 0, 1);
        shipRot.transform(fwd);
        float rot = (float) -Math.toDegrees(Math.atan2(fwd.x, fwd.z));

        Direction facing = this.getBlockState().getValue(StationaryRadarBlock.FACING);
        int fOffset;
        switch (facing){
            case NORTH -> fOffset = 0;
            case EAST -> fOffset = 90;
            case SOUTH -> fOffset = 180;
            case WEST -> fOffset = 270;
            default -> fOffset = 0;
        }
        return fOffset + rot;
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
}
