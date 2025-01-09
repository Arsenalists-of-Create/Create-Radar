package com.happysg.radar.block.monitor;

import com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity;
import com.happysg.radar.block.radar.bearing.RadarTrack;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.util.MonitorUtils;
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
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;


public class MonitorBlockEntity extends SmartBlockEntity implements IHaveHoveringInformation {
    public static final int MAX_RADIUS = 5;
    protected BlockPos controller;
    protected int radius = 1;
    private int ticksSinceLastUpdate = 0;
    protected BlockPos radarPos;
    RadarBearingBlockEntity radar;
    protected String hoveredEntity;
    protected String selectedEntity;
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
        if (ticksSinceLastUpdate > 60)
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
            selectedEntity = tag.getString("SelectedEntity");
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
            tag.putString("SelectedEntity", selectedEntity);
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
        Vec3 selected = pPlayer.pick(5, 0.0F, false).getLocation();
        if (pPlayer.isShiftKeyDown()) {
            selectedEntity = null;
        } else {
            setSelectedEntity(pHit.getLocation(), facing);
        }
        return InteractionResult.SUCCESS;
    }

    private void setSelectedEntity(Vec3 location, Direction monitorFacing) {
        if (level.isClientSide() || radarPos == null) {
            return;
        }

        Direction facing = level.getBlockState(getControllerPos()).getValue(MonitorBlock.FACING).getClockWise();
        int size = getSize();
        Vec3 center = MonitorUtils.calculateCenter(level, this, facing, size);
        Vec3 relative;
        if (Mods.VALKYRIENSKIES.isLoaded()) {
            relative = VSGameUtilsKt.toWorldCoordinates(level, location).subtract(center);
        } else {
            relative = location.subtract(center);
        }
        relative = adjustRelativeVectorForFacing(relative, monitorFacing);

        Vec3 selected = MonitorUtils.calculateSelectedPosition(relative, radarPos.getCenter(),
                getRadar().map(RadarBearingBlockEntity::getRange).orElse(0f), size);

        getRadar().ifPresent(radar -> {
            selectedEntity = MonitorUtils.findClosestEntity(selected, radar.getRange(),
                    radar.getEntityPositions(), radar.getVS2Positions()).orElse(null);
        });

        notifyUpdate();
    }

    Vec3 adjustRelativeVectorForFacing(Vec3 relative, Direction monitorFacing) {
        return switch (monitorFacing) {
            case NORTH -> new Vec3(relative.x(), 0, relative.y());
            case SOUTH -> new Vec3(relative.x(), 0, -relative.y());
            case WEST -> new Vec3(relative.y(), 0, relative.z());
            case EAST -> new Vec3(-relative.y(), 0, relative.z());
            default -> relative;
        };
    }

    public MonitorBlockEntity getController() {
        if (isController())
            return this;
        if (level.getBlockEntity(controller) instanceof MonitorBlockEntity controller)
            return controller;
        return null;
    }

    public Vec3 getTargetPos() {
        AtomicReference<Vec3> targetPos = new AtomicReference<>();
        getRadar().ifPresent(
                radar -> {
                    if (selectedEntity == null)
                        return;
                    for (RadarTrack track : radar.getEntityPositions()) {
                        if (track.entityId().equals(selectedEntity))
                            targetPos.set(track.position());
                    }
                }
        );
        return targetPos.get();
    }

    public void setFilter(MonitorFilter filter) {
        this.filter = filter;
    }
}
