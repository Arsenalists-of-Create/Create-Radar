package com.happysg.radar.block.mount;

import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountInterfaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class SmartMountBlockEntity extends CannonMountBlockEntity {
    public SmartMountBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
    public CannonMountInterfaceBlockEntity getYawInterface() { return null; }
    public float getYawSpeed() { return 0; }
    public float getYawOffset(float partialTicks) { return 0; }
    public float getPitchOffset(float partialTicks) { return 0; }
}
