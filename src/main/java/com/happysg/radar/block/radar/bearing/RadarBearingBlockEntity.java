package com.happysg.radar.block.radar.bearing;

import com.happysg.radar.compat.PhysicsHandler;
import net.minecraft.nbt.CompoundTag;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.happysg.radar.block.radar.behavior.RadarScanningBlockBehavior;
import com.happysg.radar.config.RadarConfig;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

public class RadarBearingBlockEntity extends MechanicalBearingBlockEntity implements com.happysg.radar.block.radar.behavior.IRadar {
    private float angle;
    protected RadarScanningBlockBehavior scanningBehavior;

    public RadarBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        scanningBehavior = new RadarScanningBlockBehavior(this);
        scanningBehavior.setRange(256.0); 
        scanningBehavior.setTrackExpiration(100); 
        scanningBehavior.setScanPos(PhysicsHandler.getWorldVec(this));
        behaviours.add(scanningBehavior);
    }

    @Override
    public void initialize() {
        super.initialize();
        if (com.happysg.radar.compat.Mods.SABLE.isLoaded() || com.happysg.radar.compat.Mods.AERONAUTICS.isLoaded() || com.happysg.radar.compat.Mods.SIMULATED.isLoaded()) {
            if (PhysicsHandler.isBlockInShipyard(this)) {
                com.happysg.radar.block.radar.behavior.SableRadarRegistry.register(level.dimension().location(), worldPosition, this);
            }
        }
    }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public float getSpeed() {
        return (float) (super.getSpeed() * RadarConfig.server().radarSpeedMultiplier.get());
    }

    @Override
    public Collection<RadarTrack> getTracks() {
        if (scanningBehavior != null) {
            return scanningBehavior.getRadarTracks();
        }
        return Collections.emptyList();
    }

    @Override
    public float getRange() { 
        return scanningBehavior != null ? (float)scanningBehavior.getRange() : 256f;
    }

    @Override
    public BlockPos getWorldPos() { return PhysicsHandler.getWorldPos(this); }

    @Override
    public net.minecraft.world.phys.Vec3 getRadarCenterPos(float partialTicks) {
        if (movedContraption != null && movedContraption.getContraption() != null) {
            net.minecraft.world.phys.Vec3 sum = net.minecraft.world.phys.Vec3.ZERO;
            int count = 0;
            for (net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo info : movedContraption.getContraption().getBlocks().values()) {
                if (info.state().getBlock() instanceof com.happysg.radar.block.radar.receiver.AbstractRadarFrame) {
                    sum = sum.add(net.minecraft.world.phys.Vec3.atCenterOf(info.pos()));
                    count++;
                }
            }
            if (count > 0) {
                net.minecraft.world.phys.Vec3 localCenter = sum.scale(1.0 / count);
                net.minecraft.world.phys.Vec3 subLevelCenter = movedContraption.toGlobalVector(localCenter, partialTicks);
                return PhysicsHandler.getWorldVec(level, subLevelCenter);
            }
        }
        return PhysicsHandler.getWorldVec(this);
    }

    @Override
    public float getGlobalAngle() { 
        return scanningBehavior != null ? scanningBehavior.getAngle() : angle; 
    }

    @Override
    public String getRadarType() { return "bearing"; }

    @Override
    public net.minecraft.core.Direction getradarDirection() { return net.minecraft.core.Direction.UP; }

    public float getAngle() { return angle; }
    public void setAngle(float angle) { this.angle = angle; }
    public float getAngularSpeed() { return getSpeed() / 20f; } 
    
    public int getDishCount() {
        if (movedContraption != null && movedContraption.getContraption() != null) {
            int count = 0;
            for (net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo info : movedContraption.getContraption().getBlocks().values()) {
                if (info.state().getBlock() instanceof com.happysg.radar.block.radar.receiver.AbstractRadarFrame) {
                    count++;
                }
            }
            return count;
        }
        return 0;
    }

    public boolean isCreative() {
        if (movedContraption != null && movedContraption.getContraption() != null) {
             for (net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo info : movedContraption.getContraption().getBlocks().values()) {
                if (com.happysg.radar.registry.ModBlocks.CREATIVE_RADAR_PLATE_BLOCK.has(info.state())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void write(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putBoolean("Running", running);
        tag.putFloat("Angle", angle);
        if (scanningBehavior != null) {
            tag.putDouble("Range", scanningBehavior.getRange());
        }
    }

    @Override
    public void read(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        running = tag.getBoolean("Running");
        angle = tag.getFloat("Angle");
        if (scanningBehavior != null && tag.contains("Range")) {
            scanningBehavior.setRange(tag.getDouble("Range"));
        }
    }

    @Override
    public void tick() {
        boolean wasRunning = running;
        super.tick();
        if (scanningBehavior != null) {
            angle = getInterpolatedAngle(1.0f);
            float effectiveAngle = (float) (angle * RadarConfig.server().radarRotationMultiplier.get());
            scanningBehavior.setRunning(running);
            scanningBehavior.setAngle(effectiveAngle);
            scanningBehavior.setScanPos(PhysicsHandler.getWorldVec(this));

            // Keep registry up-to-date while on a ship
            if (com.happysg.radar.compat.Mods.SABLE.isLoaded() || com.happysg.radar.compat.Mods.AERONAUTICS.isLoaded() || com.happysg.radar.compat.Mods.SIMULATED.isLoaded()) {
                com.happysg.radar.block.radar.behavior.SableRadarRegistry.register(level.dimension().location(), worldPosition, this);
            }

            // Update range based on dish count
            if (!level.isClientSide) {
                int dishCount = getDishCount();
                boolean creative = isCreative();
                int baseRange = RadarConfig.server().radarBaseRange.get();
                int bonus = RadarConfig.server().dishRangeIncrease.get();
                int maxRange = RadarConfig.server().maxRadarRange.get();
                
                double finalRange = creative ? maxRange : Math.min(maxRange, baseRange + (double) dishCount * bonus);
                
                if (Math.abs(scanningBehavior.getRange() - finalRange) > 0.1) {
                    com.happysg.radar.CreateRadar.getLogger().info("RADAR at {}: dishes={} creative={} range={}", 
                        getBlockPos(), dishCount, creative, finalRange);
                    scanningBehavior.setRange(finalRange);
                    setChanged();
                    sendData();
                }
            }

            if (wasRunning != running && level != null && !level.isClientSide) {
                sendData();
            }

            scanningBehavior.tick();
        }
    }

    @Override
    public boolean isValid() {
        return !isRemoved();
    }

    @Override
    public void attach(com.simibubi.create.content.contraptions.ControlledContraptionEntity entity) {
        super.attach(entity);
    }

    @Override
    public boolean isAttachedTo(AbstractContraptionEntity entity) {
        return movedContraption != null && movedContraption.equals(entity);
    }

    @Override
    public void destroy() {
        super.destroy();
        com.happysg.radar.block.radar.behavior.SableRadarRegistry.unregister(level.dimension().location(), worldPosition);
    }
}
