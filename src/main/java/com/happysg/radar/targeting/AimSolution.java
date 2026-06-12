package com.happysg.radar.targeting;

import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

public record AimSolution(double yawDeg, double pitchDeg, Vec3 aimDirection, @Nullable Vec3 aimPoint, int flightTicks, double missDistance) {
   public AimSolution(double yawDeg, double pitchDeg, Vec3 aimDirection, @Nullable Vec3 aimPoint, int flightTicks, double missDistance) {
      if (aimDirection != null && !(aimDirection.lengthSqr() < 1.0E-12)) {
         aimDirection = aimDirection.normalize();
      } else {
         aimDirection = Vec3.ZERO;
      }

      if (flightTicks < 0) {
         flightTicks = 0;
      }

      if (!Double.isFinite(missDistance)) {
         missDistance = Double.POSITIVE_INFINITY;
      }

      this.yawDeg = yawDeg;
      this.pitchDeg = pitchDeg;
      this.aimDirection = aimDirection;
      this.aimPoint = aimPoint;
      this.flightTicks = flightTicks;
      this.missDistance = missDistance;
   }
}
