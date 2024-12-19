package com.happysg.radar.block.monitor;

import com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity;
import com.happysg.radar.block.radar.bearing.RadarTrack;
import com.simibubi.create.content.equipment.goggles.IHaveHoveringInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


public class MonitorBlockEntity extends SmartBlockEntity implements IHaveHoveringInformation {
    public static final int MAX_RADIUS = 5;
    protected BlockPos controller;
    protected int radius = 1;
    private int ticksSinceLastUpdate = 0;
    protected BlockPos radarPos;
    RadarBearingBlockEntity radar;
    protected UUID hoveredEntity;
    protected UUID selectedEntity;
    MonitorFilter filter = MonitorFilter.ALL_ENTITIES;

    public MonitorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }


    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public BlockPos getControllerPos() {
        if (controller == null)
            return getBlockPos();
        return controller;
    }

    public int getSize() {
        return radius;
    }

    @Override
    public void tick() {
        super.tick();
        if (ticksSinceLastUpdate > 200)
            setRadarPos(null);
        ticksSinceLastUpdate++;
    }

    public void setControllerPos(BlockPos pPos, int size) {
        controller = pPos;
        radius = size;
        notifyUpdate();
    }

    public void setRadarPos(BlockPos pPos) {
        if (level.isClientSide())
            return;
        if (level.getBlockEntity(getControllerPos()) instanceof MonitorBlockEntity monitor) {
            if (pPos == null) {
                radarPos = null;
                radar = null;
                notifyUpdate();
                return;
            }
            monitor.radarPos = pPos;
            monitor.ticksSinceLastUpdate = 0;
            monitor.notifyUpdate();
        }
    }


    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        controller = null;
        radarPos = null;
        radar = null;

        super.read(tag, clientPacket);
        if (tag.contains("Controller"))
            controller = NbtUtils.readBlockPos(tag.getCompound("Controller"));
        if (tag.contains("radarPos"))
            radarPos = NbtUtils.readBlockPos(tag.getCompound("radarPos"));
        if (tag.contains("SelectedEntity"))
            selectedEntity = tag.getUUID("SelectedEntity");
        filter = MonitorFilter.values()[tag.getInt("Filter")];
        radius = tag.getInt("Size");
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        if (controller != null)
            tag.put("Controller", NbtUtils.writeBlockPos(controller));
        if (radarPos != null)
            tag.put("radarPos", NbtUtils.writeBlockPos(radarPos));
        if (selectedEntity != null)
            tag.putUUID("SelectedEntity", selectedEntity);
        tag.putInt("Filter", filter.ordinal());
        tag.putInt("Size", radius);
    }


    public boolean isController() {
        return getBlockPos().equals(controller) || controller == null;
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return super.createRenderBoundingBox().inflate(MAX_RADIUS);
    }


    //messy caching radar reference
    public Optional<RadarBearingBlockEntity> getRadar() {
        if (radar != null)
            return Optional.of(radar);
        if (radarPos == null)
            return Optional.empty();
        if (level.getBlockEntity(radarPos) instanceof RadarBearingBlockEntity radar) {
            this.radar = radar;
        }
        return Optional.ofNullable(radar);
    }

    public AABB getMultiblockBounds(LevelAccessor level, BlockPos pos) {
        //extra safety check due to contrapation moving bug
        if (getControllerPos() == null)
            return new AABB(pos);
        if (!level.getBlockState(getControllerPos()).hasProperty(MonitorBlock.FACING))
            return new AABB(pos);
        Direction facing = level.getBlockState(getControllerPos())
                .getValue(MonitorBlock.FACING).getClockWise();
        VoxelShape shape = level.getBlockState(getControllerPos())
                .getShape(level, getControllerPos());
        return shape.bounds()
                .move(getControllerPos()).expandTowards(facing.getStepX() * (radius - 1), radius - 1, facing.getStepZ() * (radius - 1));

    }

    public InteractionResult onUse(Player pPlayer, InteractionHand pHand, BlockHitResult pHit, Direction facing) {
        if (pPlayer.isShiftKeyDown()) {
            selectedEntity = null;
        } else {
            setSelectedEntity(pHit.getLocation(), facing);
        }
        return InteractionResult.SUCCESS;
    }

    private void setSelectedEntity(Vec3 location, Direction monitorFacing) {
        if (level.isClientSide())
            return;
        if (radarPos == null)
            return;
        Direction facing = level.getBlockState(getControllerPos())
                .getValue(MonitorBlock.FACING).getClockWise();
        int size = getSize();
        Vec3 center = Vec3.atCenterOf(getControllerPos())
                .add(facing.getStepX() * (size - 1) / 2.0, (size - 1) / 2.0, facing.getStepZ() * (size - 1) / 2.0);
        Vec3 relative = location.subtract(center);
        relative = adjustRelativeVectorForFacing(relative, monitorFacing);
        Vec3 RadarPos = radarPos.getCenter();
        float range = getRadar().map(RadarBearingBlockEntity::getRange).orElse(0f);
        Vec3 selected = RadarPos.add(relative.scale(range));
        getRadar().map(RadarBearingBlockEntity::getEntityPositions)
                .ifPresent(entityPositions -> {
                    double distance = .1f * range;
                    for (RadarTrack track : entityPositions) {
                        Vec3 entityPos = track.position();
                        entityPos = entityPos.multiply(1, 0, 1);
                        Vec3 selectedNew = selected.multiply(1, 0, 1);
                        double newDistance = entityPos.distanceTo(selectedNew);

                        if (newDistance < distance) {
                            distance = newDistance;
                            selectedEntity = track.entityId();
                            notifyUpdate();
                        }
                    }
                });
    }

    Vec3 adjustRelativeVectorForFacing(Vec3 relative, Direction monitorFacing) {
        switch (monitorFacing) {
            case NORTH:
                return new Vec3(relative.x(), 0, relative.y());
            case SOUTH:
                return new Vec3(relative.x(), 0, -relative.y());
            case WEST:
                return new Vec3(relative.y(), 0, relative.z());
            case EAST:
                return new Vec3(-relative.y(), 0, relative.z());
            default:
                return relative;
        }
    }

    public MonitorBlockEntity getController() {
        if (isController())
            return this;
        if (level.getBlockEntity(controller) instanceof MonitorBlockEntity controller)
            return controller;
        return null;
    }

    public Vec3 getTargetPos() {
        return selectedEntity == null ? null : getRadar().map(radar -> radar.getEntityPositions().stream()
                .filter(track -> track.entityId().equals(selectedEntity))
                .map(RadarTrack::position)
                .findFirst().orElse(null)).orElse(null);
    }

    public void setFilter(MonitorFilter filter) {
        this.filter = filter;
    }
}
