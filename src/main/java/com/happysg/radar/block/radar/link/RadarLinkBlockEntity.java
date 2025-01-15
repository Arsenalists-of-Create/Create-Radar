package com.happysg.radar.block.radar.link;

import com.happysg.radar.block.radar.link.screens.AbstractRadarLinkScreen;
import com.happysg.radar.registry.AllRadarBehaviors;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

public class RadarLinkBlockEntity extends SmartBlockEntity {

    protected BlockPos targetOffset = BlockPos.ZERO;

    public RadarSource activeSource;
    public RadarTarget activeTarget;

    private CompoundTag sourceConfig;
    private boolean ledState = false;

    public RadarLinkBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {

    }

    public void updateGatheredData() {
        BlockPos sourcePosition = getSourcePosition();
        BlockPos targetPosition = getTargetPosition();

        if (!level.isLoaded(targetPosition) || !level.isLoaded(sourcePosition))
            return;

        RadarTarget target = AllRadarBehaviors.targetOf(level, targetPosition);
        List<RadarSource> sources = AllRadarBehaviors.sourcesOf(level, sourcePosition);
        boolean notify = false;

        if (activeTarget != target) {
            activeTarget = target;
            notify = true;
        }

        if (activeSource != null && !sources.contains(activeSource)) {
            activeSource = null;
            sourceConfig = new CompoundTag();
            notify = true;
        }

        if (notify)
            notifyUpdate();
        if (activeSource == null || activeTarget == null)
            return;

        activeSource.transferData(this, activeTarget);
        sendData();

        award(AllAdvancements.DISPLAY_LINK);
    }

    @Override
    public void writeSafe(CompoundTag tag) {
        super.writeSafe(tag);
        writeGatheredData(tag);
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        writeGatheredData(tag);
        if (clientPacket && activeTarget != null)
            tag.putString("TargetType", activeTarget.id.toString());
        tag.putBoolean("LedState", ledState);
    }

    private void writeGatheredData(CompoundTag tag) {
        tag.put("TargetOffset", NbtUtils.writeBlockPos(targetOffset));

        if (activeSource != null) {
            CompoundTag data = sourceConfig.copy();
            data.putString("Id", activeSource.id.toString());
            tag.put("Source", data);
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        targetOffset = NbtUtils.readBlockPos(tag.getCompound("TargetOffset"));
        ledState = tag.getBoolean("LedState");
        if (clientPacket && tag.contains("TargetType"))
            activeTarget = AllRadarBehaviors.getTarget(new ResourceLocation(tag.getString("TargetType")));


        if (!tag.contains("Source"))
            return;

        CompoundTag data = tag.getCompound("Source");
        activeSource = AllRadarBehaviors.getSource(new ResourceLocation(data.getString("Id")));
        sourceConfig = new CompoundTag();
        if (activeSource != null)
            sourceConfig = data.copy();
    }

    Optional<AbstractRadarLinkScreen> getScreen() {
        return activeSource == null ? Optional.empty() : Optional.ofNullable(activeSource.getScreen(this));
    }

    public void target(BlockPos targetPosition) {
        this.targetOffset = targetPosition.subtract(worldPosition);
    }

    public BlockPos getSourcePosition() {
        return worldPosition.relative(getDirection());
    }

    public CompoundTag getSourceConfig() {
        return sourceConfig;
    }

    public void setSourceConfig(CompoundTag sourceConfig) {
        this.sourceConfig = sourceConfig;
    }

    public Direction getDirection() {
        return getBlockState().getOptionalValue(RadarLinkBlock.FACING)
                .orElse(Direction.UP)
                .getOpposite();
    }

    public BlockPos getTargetPosition() {
        return worldPosition.offset(targetOffset);
    }


}
