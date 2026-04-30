package com.dsvv.cbcat.cannon.twin_autocannon.contraption;

import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.cannon_types.ICannonContraptionType;

public class MountedTwinAutocannonContraption extends AbstractMountedCannonContraption {
    @Override public ICannonContraptionType getCannonType() { return null; }

    @Override public net.minecraft.world.phys.Vec3 getInteractionVec(rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity oriented) { return net.minecraft.world.phys.Vec3.ZERO; }

    @Override public float getWeightForStress() { return 1.0f; }

    @Override public void fireShot(net.minecraft.server.level.ServerLevel level, rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity oriented) {}

    @Override public void onRedstoneUpdate(net.minecraft.server.level.ServerLevel level, rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity oriented, boolean powered, int power, rbasamoyai.createbigcannons.cannon_control.ControlPitchContraption control) {}

    @Override public com.simibubi.create.api.contraption.ContraptionType getType() { return null; }

    @Override public boolean assemble(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) { return false; }
}
