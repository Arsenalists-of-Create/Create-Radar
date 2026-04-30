package com.happysg.radar.block.datalink;

import com.happysg.radar.registry.AllDataBehaviors;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public class DataLinkBlockEntity extends SmartBlockEntity {

    protected BlockPos targetOffset = BlockPos.ZERO;
    @Nullable
    private Long linkedShipId = null;
    private BlockPos targetOffsetShip = BlockPos.ZERO;

    public DataPeripheral activeSource;
    public DataController activeTarget;

    private CompoundTag sourceConfig = new CompoundTag();
    boolean ledState = false;

    private BlockPos linkedMonitorPos;

    public DataLinkBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Implement behaviors if needed
    }

    @Override
    public void tick() {
        super.tick();
        updateGatheredData();
    }

    public void updateGatheredData() {
        BlockPos sourcePosition = getSourcePosition();
        BlockPos targetPosition = getTargetPosition();

        if (level == null || !level.isLoaded(targetPosition) || !level.isLoaded(sourcePosition))
            return;

        DataController target = AllDataBehaviors.targetOf(level, targetPosition);
        DataPeripheral source = AllDataBehaviors.sourcesOf(level, sourcePosition);
        boolean notify = false;

        if (activeTarget != target) {
            activeTarget = target;
            notify = true;
        }

        if (activeSource != source) {
            activeSource = source;
            sourceConfig = new CompoundTag();
            notify = true;
        }

        if (notify)
            notifyUpdate();
        if (activeSource == null || activeTarget == null) {
            ledState = false;
            return;
        }

        ledState = true;
        activeSource.transferData(new DataLinkContext(level, this), activeTarget);
        sendData();
    }

    public CompoundTag getSourceConfig() { return sourceConfig; }
    public void setSourceConfig(CompoundTag sourceConfig) { this.sourceConfig = sourceConfig; }

    public void writeSafe(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.writeSafe(tag, provider);
        writeGatheredData(tag);
    }

    @Override
    protected void write(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        writeGatheredData(tag);
        if (clientPacket && activeTarget != null)
            tag.putString("TargetType", activeTarget.id.toString());
        tag.putBoolean("LedState", ledState);
    }

    private void writeGatheredData(CompoundTag tag) {
        tag.put("TargetOffset", NbtUtils.writeBlockPos(targetOffset));

        if (linkedShipId != null)
            tag.putLong("LinkedShipId", linkedShipId);
        tag.put("TargetOffsetShip", NbtUtils.writeBlockPos(targetOffsetShip));

        if (activeSource != null) {
            CompoundTag data = sourceConfig.copy();
            data.putString("Id", activeSource.id.toString());
            tag.put("Source", data);
        }
    }

    @Override
    protected void read(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);

        targetOffset = net.minecraft.nbt.NbtUtils.readBlockPos(tag, "TargetOffset").orElse(net.minecraft.core.BlockPos.ZERO);
        ledState = tag.getBoolean("LedState");

        linkedShipId = tag.contains("LinkedShipId") ? tag.getLong("LinkedShipId") : null;
        targetOffsetShip = tag.contains("TargetOffsetShip")
                ? net.minecraft.nbt.NbtUtils.readBlockPos(tag, "TargetOffsetShip").orElse(net.minecraft.core.BlockPos.ZERO)
                : BlockPos.ZERO;

        if (clientPacket && tag.contains("TargetType"))
            activeTarget = AllDataBehaviors.getTarget(net.minecraft.resources.ResourceLocation.parse(tag.getString("TargetType")));

        if (!tag.contains("Source"))
            return;

        CompoundTag data = tag.getCompound("Source");
        activeSource = AllDataBehaviors.getSource(net.minecraft.resources.ResourceLocation.parse(data.getString("Id")));
        sourceConfig = new CompoundTag();
        if (activeSource != null)
            sourceConfig = data.copy();
    }

    public BlockPos getSourcePosition() {
        if (!(level instanceof ServerLevel sl) || linkedShipId == null)
            return worldPosition.relative(getDirection());

        var ship = org.valkyrienskies.mod.common.VSGameUtilsKt.getShipManagingPos(sl, worldPosition);
        if (ship == null || ship.getId() != linkedShipId) {
            linkedShipId = null;
            return worldPosition.relative(getDirection());
        }

        BlockPos selfShipPos = toShipBlockPos(ship, worldPosition);
        BlockPos sourceShipPos = selfShipPos.relative(getDirection());
        return toWorldBlockPos(ship, sourceShipPos);
    }

    public Direction getDirection() {
        return getBlockState().getOptionalValue(DataLinkBlock.FACING)
                .orElse(Direction.UP)
                .getOpposite();
    }

    public BlockPos getTargetPosition() {
        if (!(level instanceof ServerLevel sl) || linkedShipId == null) {
            return worldPosition.offset(targetOffset);
        }

        var ship = org.valkyrienskies.mod.common.VSGameUtilsKt.getShipManagingPos(sl, worldPosition);
        if (ship == null || ship.getId() != linkedShipId) {
            linkedShipId = null;
            return worldPosition.offset(targetOffset);
        }

        BlockPos selfShipPos = toShipBlockPos(ship, worldPosition);
        BlockPos targetShipPos = selfShipPos.offset(targetOffsetShip);

        return toWorldBlockPos(ship, targetShipPos);
    }

    private static BlockPos toShipBlockPos(org.valkyrienskies.core.api.ships.Ship ship, BlockPos worldPos) {
        org.joml.Vector3d v = new org.joml.Vector3d(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5);
        ship.getWorldToShip().transformPosition(v);
        return BlockPos.containing(v.x, v.y, v.z);
    }

    private static BlockPos toWorldBlockPos(org.valkyrienskies.core.api.ships.Ship ship, BlockPos shipPos) {
        org.joml.Vector3d v = new org.joml.Vector3d(shipPos.getX() + 0.5, shipPos.getY() + 0.5, shipPos.getZ() + 0.5);
        ship.getShipToWorld().transformPosition(v);
        return BlockPos.containing(v.x, v.y, v.z);
    }

    public static class Track {
        public Vec3 position() { return Vec3.ZERO; }
    }
}
