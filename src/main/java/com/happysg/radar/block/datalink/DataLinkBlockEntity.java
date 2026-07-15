package com.happysg.radar.block.datalink;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.block.arad.aradnetworks.ARADData;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.sable.SableLinkPersistence;
import com.happysg.radar.registry.AllDataBehaviors;
import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class DataLinkBlockEntity extends SmartBlockEntity implements TransformableBlockEntity {
    public enum WeaponEndpointType {
        NONE,
        YAW,
        PITCH,
        FIRING
    }

    protected BlockPos targetOffset = BlockPos.ZERO;
    @Nullable
    private UUID linkedShipId = null;
    private BlockPos targetOffsetShip = BlockPos.ZERO;

    public DataPeripheral activeSource;
    public DataController activeTarget;

    private CompoundTag sourceConfig;
    boolean ledState = false;
    private BlockPos lastKnownPos = BlockPos.ZERO;
    private WeaponEndpointType weaponEndpointType = WeaponEndpointType.NONE;
    @Nullable
    private BlockPos targetWorldPosFallback = null;
    @Nullable
    private BlockPos filtererOffset = null;
    @Nullable
    private BlockPos filtererWorldPosFallback = null;

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
        repairSavedDataPosition();
        refreshWeaponRuntime();
        restoreWeaponFilterLinkFallback();
        updateGatheredData();
    }

    private void repairSavedDataPosition() {
        if (!(level instanceof ServerLevel serverLevel) || level.getGameTime() % 40 != 0 || lastKnownPos.equals(worldPosition))
            return;

        NetworkData networkData = NetworkData.get(serverLevel);
        if (networkData.getFiltererForDataLink(serverLevel.dimension(), worldPosition) != null) {
            lastKnownPos = worldPosition;
            setChanged();
            return;
        }

        if (!serverLevel.isLoaded(lastKnownPos)
                || serverLevel.getBlockEntity(lastKnownPos) instanceof DataLinkBlockEntity
                || serverLevel.getBlockState(lastKnownPos).getBlock() == getBlockState().getBlock()) {
            // The old location is either still a real source or cannot be verified. Treat
            // this block as a copy and rebuild only from its local fallback/snapshot data.
            lastKnownPos = worldPosition;
            restoreWeaponFilterLinkFallback();
            setChanged();
            return;
        }

        boolean radarUpdated = networkData.updateDataLinkPosition(serverLevel.dimension(), lastKnownPos, worldPosition);
        if (radarUpdated) {
            lastKnownPos = worldPosition;
            setChanged();
            return;
        }

        boolean aradUpdated = ARADData.get(serverLevel).updateDataLinkPosition(serverLevel.dimension(), lastKnownPos, worldPosition);
        if (aradUpdated) {
            lastKnownPos = worldPosition;
            setChanged();
        }
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
        tag.putLong("LastKnownPos", lastKnownPos.asLong());
        tag.putString("WeaponEndpointType", weaponEndpointType.name());
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        writeGatheredData(tag);
    }

    private void writeGatheredData(CompoundTag tag) {
        tag.put("TargetOffset", NbtUtils.writeBlockPos(targetOffset));
        tag.put("TargetPos", NbtUtils.writeBlockPos(getTargetPosition()));

        UUID serializedShipId = Mods.SABLE.isLoaded()
                ? SableLinkPersistence.remapShipId(linkedShipId)
                : linkedShipId;
        if (serializedShipId != null)
            tag.putUUID("LinkedShipId", serializedShipId);
        tag.put("TargetOffsetShip", NbtUtils.writeBlockPos(targetOffsetShip));
        if (filtererOffset != null) {
            tag.put("FiltererOffset", NbtUtils.writeBlockPos(filtererOffset));
        }
        if (filtererWorldPosFallback != null) {
            tag.put("FiltererPos", NbtUtils.writeBlockPos(filtererWorldPosFallback));
        }

        if (activeSource != null) {
            CompoundTag data = sourceConfig.copy();
            data.putString("Id", activeSource.id.toString());
            tag.put("Source", data);
        }
        tag.putString("WeaponEndpointType", weaponEndpointType.name());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);

        targetWorldPosFallback = NbtUtils.readBlockPos(tag, "TargetPos").orElse(null);
        targetOffset = NbtUtils.readBlockPos(tag, "TargetOffset")
                .orElseGet(() -> targetWorldPosFallback == null ? BlockPos.ZERO : targetWorldPosFallback.subtract(worldPosition));
        ledState = tag.getBoolean("LedState");
        if (tag.contains("WeaponEndpointType", Tag.TAG_STRING)) {
            try {
                weaponEndpointType = WeaponEndpointType.valueOf(tag.getString("WeaponEndpointType"));
            } catch (IllegalArgumentException ignored) {
                weaponEndpointType = WeaponEndpointType.NONE;
            }
        } else {
            weaponEndpointType = WeaponEndpointType.NONE;
        }
        if (Mods.SABLE.isLoaded() && SableLinkPersistence.isPlacingSchematic()) {
            lastKnownPos = worldPosition;
        } else if (tag.contains("LastKnownPos", Tag.TAG_LONG)) {
            lastKnownPos = BlockPos.of(tag.getLong("LastKnownPos"));
        } else {
            lastKnownPos = worldPosition;
        }

        UUID serializedShipId = tag.hasUUID("LinkedShipId") ? tag.getUUID("LinkedShipId") : null;
        linkedShipId = Mods.SABLE.isLoaded()
                ? SableLinkPersistence.remapShipId(serializedShipId)
                : serializedShipId;

        targetOffsetShip = NbtUtils.readBlockPos(tag, "TargetOffsetShip").orElse(BlockPos.ZERO);
        filtererOffset = NbtUtils.readBlockPos(tag, "FiltererOffset").orElse(null);
        filtererWorldPosFallback = NbtUtils.readBlockPos(tag, "FiltererPos").orElse(null);

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

    @Override
    public void transform(BlockEntity blockEntity, StructureTransform transform) {
        targetOffset = transform.applyWithoutOffset(targetOffset);
        targetOffsetShip = transform.applyWithoutOffset(targetOffsetShip);
        if (filtererOffset != null) {
            filtererOffset = transform.applyWithoutOffset(filtererOffset);
        }
        notifyUpdate();
        refreshWeaponRuntime();
    }



    public void target(BlockPos targetPosition) {
        if (!(level instanceof ServerLevel sl)) {
            this.targetOffset = targetPosition.subtract(worldPosition);
            this.targetWorldPosFallback = targetPosition.immutable();
            refreshWeaponRuntime();
            return;
        }

        var ship = SableCompanion.INSTANCE.getContaining(sl, worldPosition);
        var targetShip = SableCompanion.INSTANCE.getContaining(sl, targetPosition);

        if (ship != null && targetShip != null && ship.getUniqueId().equals(targetShip.getUniqueId())) {
            linkedShipId = ship.getUniqueId();

            BlockPos selfShipPos   = toShipBlockPos(ship, worldPosition);
            BlockPos targetShipPos = toShipBlockPos(ship, targetPosition);

            targetOffsetShip = targetShipPos.subtract(selfShipPos);

            targetOffset = targetPosition.subtract(worldPosition);
            targetWorldPosFallback = targetPosition.immutable();
            setChanged();
            refreshWeaponRuntime();
            return;
        }

        linkedShipId = null;
        targetOffsetShip = BlockPos.ZERO;
        this.targetOffset = targetPosition.subtract(worldPosition);
        this.targetWorldPosFallback = targetPosition.immutable();
        setChanged();
        refreshWeaponRuntime();
    }

    public WeaponEndpointType getWeaponEndpointType() {
        return weaponEndpointType;
    }

    public void setWeaponEndpointType(WeaponEndpointType weaponEndpointType) {
        this.weaponEndpointType = weaponEndpointType == null ? WeaponEndpointType.NONE : weaponEndpointType;
        setChanged();
        refreshWeaponRuntime();
    }

    public void setFiltererPosition(BlockPos filtererPos) {
        filtererOffset = filtererPos.subtract(worldPosition);
        filtererWorldPosFallback = filtererPos.immutable();
        setChanged();
    }

    private void refreshWeaponRuntime() {
        if (level instanceof ServerLevel serverLevel) {
            WeaponNetworkRuntime.get(serverLevel).register(this);
        }
    }

    private void restoreWeaponFilterLinkFallback() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!getBlockState().hasProperty(DataLinkBlock.LINK_STYLE)
                || getBlockState().getValue(DataLinkBlock.LINK_STYLE) != DataLinkBlock.LinkStyle.RADAR) {
            return;
        }
        BlockPos filtererPos = resolveFiltererPosition();
        if (filtererPos == null) {
            return;
        }

        NetworkData data = NetworkData.get(serverLevel);
        BlockPos existingFilterer = data.getFiltererForDataLink(serverLevel.dimension(), worldPosition);
        if (existingFilterer != null) {
            return;
        }

        BlockPos endpointPos = getTargetPosition();
        BlockPos mountPos = WeaponNetworkRuntime.get(serverLevel).getMountForController(endpointPos);
        if (mountPos == null) {
            return;
        }

        NetworkData.Group group = data.getOrCreateGroup(serverLevel.dimension(), filtererPos);
        if (!data.canAttachWeaponEndpoint(group, endpointPos, mountPos)) {
            return;
        }

        data.attachWeaponEndpoint(group, endpointPos, mountPos);
        data.addDataLinkToGroup(group, worldPosition, endpointPos);
    }

    @Nullable
    private BlockPos resolveFiltererPosition() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        if (filtererOffset != null) {
            BlockPos relativeFilterer = worldPosition.offset(filtererOffset);
            if (serverLevel.getBlockEntity(relativeFilterer) instanceof NetworkFiltererBlockEntity) {
                return relativeFilterer;
            }
        }
        if (filtererWorldPosFallback != null
                && serverLevel.getBlockEntity(filtererWorldPosFallback) instanceof NetworkFiltererBlockEntity) {
            return filtererWorldPosFallback;
        }
        return null;
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
            BlockPos relativeTarget = worldPosition.offset(targetOffset);
            if (targetWorldPosFallback != null && shouldUseWorldTargetFallback(relativeTarget)) {
                return targetWorldPosFallback;
            }
            return relativeTarget;
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

    private boolean shouldUseWorldTargetFallback(BlockPos relativeTarget) {
        if (!(level instanceof ServerLevel serverLevel) || targetWorldPosFallback == null || targetWorldPosFallback.equals(relativeTarget)) {
            return false;
        }
        if (!serverLevel.hasChunkAt(relativeTarget)) {
            return true;
        }
        if (getBlockState().hasProperty(DataLinkBlock.LINK_STYLE)
                && getBlockState().getValue(DataLinkBlock.LINK_STYLE) == DataLinkBlock.LinkStyle.CONTROLLER) {
            return serverLevel.getBlockEntity(relativeTarget) == null && serverLevel.getBlockEntity(targetWorldPosFallback) != null;
        }
        return AllDataBehaviors.targetOf(serverLevel, relativeTarget) == null
                && AllDataBehaviors.targetOf(serverLevel, targetWorldPosFallback) != null;
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

    @Override
    public void onLoad() {
        super.onLoad();
        refreshWeaponRuntime();
    }

}
