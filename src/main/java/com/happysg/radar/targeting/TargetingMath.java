package com.happysg.radar.targeting;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TargetingMath {
   private TargetingMath() {
   }

   public static Vec3 directionFromYawPitch(double yawDeg, double pitchDeg) {
      if (Double.isFinite(yawDeg) && Double.isFinite(pitchDeg)) {
         double yawRad = Math.toRadians(yawDeg);
         double pitchRad = Math.toRadians(pitchDeg);
         return (new Vec3(Math.cos(pitchRad) * Math.cos(yawRad), Math.sin(pitchRad), Math.cos(pitchRad) * Math.sin(yawRad))).normalize();
      } else {
         return Vec3.ZERO;
      }
   }

   public static YawPitch yawPitchFromDirection(Vec3 direction) {
      if (direction != null && !(direction.lengthSqr() < 1.0E-12)) {
         Vec3 normalized = direction.normalize();
         double yaw = Math.toDegrees(Math.atan2(normalized.z, normalized.x));
         double horizontal = Math.sqrt(normalized.x * normalized.x + normalized.z * normalized.z);
         double pitch = Math.toDegrees(Math.atan2(normalized.y, Math.max(1.0E-6, horizontal)));
         return new YawPitch(wrap180(yaw), pitch);
      } else {
         return new YawPitch((double)0.0F, (double)0.0F);
      }
   }

   public static double wrap360(double deg) {
      if (!Double.isFinite(deg)) {
         return (double)0.0F;
      } else {
         deg %= (double)360.0F;
         if (deg < (double)0.0F) {
            deg += (double)360.0F;
         }

         return deg;
      }
   }

   public static double wrap180(double deg) {
      deg = wrap360(deg);
      if (deg >= (double)180.0F) {
         deg -= (double)360.0F;
      }

      return deg;
   }

   public static double shortestAngleDelta(double fromDeg, double toDeg) {
      return wrap180(toDeg - fromDeg);
   }

   public static double distancePointToAabb(Vec3 point, AABB box) {
      if (point != null && box != null) {
         double dx = axisDistance(point.x, box.minX, box.maxX);
         double dy = axisDistance(point.y, box.minY, box.maxY);
         double dz = axisDistance(point.z, box.minZ, box.maxZ);
         return Math.sqrt(dx * dx + dy * dy + dz * dz);
      } else {
         return Double.POSITIVE_INFINITY;
      }
   }

   private static double axisDistance(double value, double min, double max) {
      if (value < min) {
         return min - value;
      } else {
         return value > max ? value - max : (double)0.0F;
      }
   }

   public static record YawPitch(double yawDeg, double pitchDeg) {
   }
}
