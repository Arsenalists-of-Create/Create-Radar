package com.happysg.radar.targeting;

import net.minecraft.world.phys.Vec3;

public record MountState(Vec3 mountPosition, Vec3 muzzlePosition, Vec3 inheritedVelocity, double currentYawDeg, double currentPitchDeg) {
   public MountState(Vec3 mountPosition, Vec3 muzzlePosition, Vec3 inheritedVelocity, double currentYawDeg, double currentPitchDeg) {
      if (mountPosition == null) {
         mountPosition = Vec3.ZERO;
      }

      if (muzzlePosition == null) {
         muzzlePosition = mountPosition;
      }

      if (inheritedVelocity == null) {
         inheritedVelocity = Vec3.ZERO;
      }

      this.mountPosition = mountPosition;
      this.muzzlePosition = muzzlePosition;
      this.inheritedVelocity = inheritedVelocity;
      this.currentYawDeg = currentYawDeg;
      this.currentPitchDeg = currentPitchDeg;
   }

   public static MountState empty() {
      return new MountState(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, (double)0.0F, (double)0.0F);
   }
}
