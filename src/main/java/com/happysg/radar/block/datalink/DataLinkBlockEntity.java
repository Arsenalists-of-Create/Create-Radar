package com.happysg.radar.block.datalink;

import com.happysg.radar.registry.AllDataBehaviors;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class DataLinkBlockEntity extends SmartBlockEntity {

    protected BlockPos targetOffset = BlockPos.ZERO;
    @Nullable
    private UUID linkedShipId = null;
    private BlockPos targetOffsetShip = BlockPos.ZERO;

    public DataPeripheral activeSource;
    public DataController activeTarget;

    private CompoundTag sourceConfig;
    boolean ledState = false;

    private BlockPos linkedMonitorPos;

    public DataLinkBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {

    }

    @Override
    public void tick() {
        super.tick();
        updateGatheredData();
    }

    public void updateGatheredData() {
        BlockPos sourcePosition = getSourcePosition();
        BlockPos targetPosition = getTargetPosition();

        if (!level.isLoaded(targetPosition) || !level.isLoaded(sourcePosition))
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
        //TODO implement advancement
    }



    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries,clientPacket);
        writeGatheredData(tag);
        if (clientPacket && activeTarget != null)
            tag.putString("TargetType", activeTarget.id.toString());
        tag.putBoolean("LedState", ledState);
    }

    private void writeGatheredData(CompoundTag tag) {
        tag.put("TargetOffset", NbtUtils.writeBlockPos(targetOffset));

        if (linkedShipId != null)
            tag.putUUID("LinkedShipId", linkedShipId);
        tag.put("TargetOffsetShip", NbtUtils.writeBlockPos(targetOffsetShip));

        if (activeSource != null) {
            CompoundTag data = sourceConfig.copy();
            data.putString("Id", activeSource.id.toString());
            tag.put("Source", data);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);

        targetOffset = NbtUtils.readBlockPos(tag, "TargetOffset").orElse(BlockPos.ZERO);
        ledState = tag.getBoolean("LedState");

        linkedShipId = tag.hasUUID("LinkedShipId") ? tag.getUUID("LinkedShipId") : null;

        targetOffsetShip = NbtUtils.readBlockPos(tag, "TargetOffsetShip").orElse(BlockPos.ZERO);

        if (clientPacket && tag.contains("TargetType", Tag.TAG_STRING)) {
            activeTarget = AllDataBehaviors.getTarget(ResourceLocation.parse(tag.getString("TargetType")));
        }

        if (!tag.contains("Source", Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag data = tag.getCompound("Source");

        if (data.contains("Id", Tag.TAG_STRING)) {
            activeSource = AllDataBehaviors.getSource(ResourceLocation.parse(data.getString("Id")));
        } else {
            activeSource = null;
        }

        sourceConfig = new CompoundTag();
        if (activeSource != null) {
            sourceConfig = data.copy();
        }
    }



    public void target(BlockPos targetPosition) {
        if (!(level instanceof ServerLevel sl)) {
            this.targetOffset = targetPosition.subtract(worldPosition);
            return;
        }

        var ship = SableCompanion.INSTANCE.getContaining(sl, worldPosition);
        var targetShip = SableCompanion.INSTANCE.getContaining(sl, targetPosition);

        if (ship != null && targetShip != null && ship.getUniqueId() == targetShip.getUniqueId()) {
            linkedShipId = ship.getUniqueId();

            BlockPos selfShipPos   = toShipBlockPos(ship, worldPosition);
            BlockPos targetShipPos = toShipBlockPos(ship, targetPosition);

            targetOffsetShip = targetShipPos.subtract(selfShipPos);

            targetOffset = targetPosition.subtract(worldPosition);
            setChanged();
            return;
        }

        linkedShipId = null;
        targetOffsetShip = BlockPos.ZERO;
        this.targetOffset = targetPosition.subtract(worldPosition);
        setChanged();
    }

    public BlockPos getSourcePosition() {
        if (!(level instanceof ServerLevel sl) || linkedShipId == null)
            return worldPosition.relative(getDirection());

        var ship = SableCompanion.INSTANCE.getContaining(sl, worldPosition);
        if (ship == null || !ship.getUniqueId().equals(linkedShipId)) {
            linkedShipId = null;
            return worldPosition.relative(getDirection());
        }

        BlockPos selfShipPos = toShipBlockPos(ship, worldPosition);
        BlockPos sourceShipPos = selfShipPos.relative(getDirection());
        return toWorldBlockPos(ship, sourceShipPos);
    }

    public CompoundTag getSourceConfig() {
        return sourceConfig;
    }

    public void setSourceConfig(CompoundTag sourceConfig) {
        this.sourceConfig = sourceConfig;
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

        var ship = SableCompanion.INSTANCE.getContaining(sl, worldPosition);
        if (ship == null || !ship.getUniqueId().equals(linkedShipId)) {
            linkedShipId = null;
            return worldPosition.offset(targetOffset);
        }

        BlockPos selfShipPos = toShipBlockPos(ship, worldPosition);
        BlockPos targetShipPos = selfShipPos.offset(targetOffsetShip);

        return toWorldBlockPos(ship, targetShipPos);
    }

    private static BlockPos toShipBlockPos(SubLevelAccess ship, BlockPos worldPos) {
        Vector3d v = ship.logicalPose().transformPositionInverse(
                new Vector3d(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5));
        return BlockPos.containing(v.x(), v.y(), v.z());
    }

    private static BlockPos toWorldBlockPos(SubLevelAccess ship, BlockPos shipPos) {
        Vector3d v = ship.logicalPose().transformPosition(
                new Vector3d(shipPos.getX() + 0.5, shipPos.getY() + 0.5, shipPos.getZ() + 0.5));
        return BlockPos.containing(v.x(), v.y(), v.z());
    }
}