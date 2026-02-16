package com.happysg.radar.block.datalink;

import com.happysg.radar.block.monitor.MonitorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

public class DataLinkContext {

    private Level level;
    private DataLinkBlockEntity blockEntity;

    public DataLinkContext(Level level, DataLinkBlockEntity blockEntity) {
        this.level = level;
        this.blockEntity = blockEntity;
    }

    public Level level() {
        return level;
    }

    public DataLinkBlockEntity blockEntity() {
        return blockEntity;
    }

    public BlockEntity getSourceBlockEntity() {
        return level.getBlockEntity(getSourcePos());
    }

    public BlockPos getSourcePos() {
        return blockEntity.getSourcePosition();
    }

    @Nullable
    public MonitorBlockEntity getMonitorBlockEntity() {
        return level.getBlockEntity(getTargetPos()) instanceof MonitorBlockEntity monitorBlockEntity ? monitorBlockEntity.getController() : null;
    }

    public BlockPos getTargetPos() {
        return blockEntity.getTargetPosition();
    }

    public CompoundTag sourceConfig() {
        return blockEntity.getSourceConfig();
    }

}
