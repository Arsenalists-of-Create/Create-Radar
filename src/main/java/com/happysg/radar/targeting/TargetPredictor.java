package com.happysg.radar.targeting;

import javax.annotation.Nullable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class TargetPredictor {
   private static final double MAX_ACCEL_BLOCKS_PER_TICK2 = (double)0.25F;

   public Vec3 predictPosition(TargetingSnapshot snapshot, double ticksAhead) {
      if (snapshot == null) {
         return Vec3.ZERO;
      } else if (snapshot.targetMotionClass() == TargetMotionClass.SPRINT_JUMP) {
         return this.predictSprintJumpPosition(snapshot, ticksAhead);
      } else {
         return this.predictPosition(snapshot.targetPosition(), snapshot.targetVelocity(), snapshot.targetAcceleration(), ticksAhead);
      }
   }

   public Vec3 predictPosition(Vec3 position, Vec3 velocity, Vec3 acceleration, double ticksAhead) {
      Vec3 safePosition = position == null ? Vec3.ZERO : position;
      Vec3 safeVelocity = finiteOrZero(velocity);
      Vec3 safeAcceleration = clampAcceleration(finiteOrZero(acceleration));
      double safeTicks = Double.isFinite(ticksAhead) ? Math.max((double)0.0F, ticksAhead) : (double)0.0F;
      return safePosition.add(safeVelocity.scale(safeTicks)).add(safeAcceleration.scale((double)0.5F * safeTicks * safeTicks));
   }

   @Nullable
   public AABB predictAabb(TargetingSnapshot snapshot, double ticksAhead) {
      if (snapshot == null || snapshot.targetAabb() == null) {
         return null;
      } else {
         Vec3 offset = this.predictPosition(snapshot, ticksAhead).subtract(snapshot.targetPosition());
         return snapshot.targetAabb().move(offset);
      }
   }

   @Nullable
   public AABB predictAabb(@Nullable AABB box, Vec3 velocity, Vec3 acceleration, double ticksAhead) {
      if (box == null) {
         return null;
      } else {
         Vec3 offset = this.predictPosition(Vec3.ZERO, velocity, acceleration, ticksAhead);
         return box.move(offset);
      }
   }

   public Vec3 sanitizeAcceleration(Vec3 acceleration) {
      return clampAcceleration(finiteOrZero(acceleration));
   }

   private Vec3 predictSprintJumpPosition(TargetingSnapshot snapshot, double ticksAhead) {
      Vec3 safePosition = snapshot.targetPosition() == null ? Vec3.ZERO : snapshot.targetPosition();
      Vec3 safeVelocity = finiteOrZero(snapshot.targetVelocity());
      double safeTicks = Double.isFinite(ticksAhead) ? Math.max((double)0.0F, ticksAhead) : (double)0.0F;
      double gravity = Double.isFinite(snapshot.gravity()) ? snapshot.gravity() : (double)-0.08F;
      Vec3 horizontal = new Vec3(safeVelocity.x, (double)0.0F, safeVelocity.z).scale(safeTicks);
      double y = safeVelocity.y * safeTicks + (double)0.5F * gravity * safeTicks * safeTicks;
      return safePosition.add(horizontal.x, y, horizontal.z);
   }

   private static Vec3 finiteOrZero(Vec3 vec) {
      return vec != null && Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z) ? vec : Vec3.ZERO;
   }

   private static Vec3 clampAcceleration(Vec3 acceleration) {
      double lenSqr = acceleration.lengthSqr();
      double maxSqr = (double)0.0625F;
      return !(lenSqr <= maxSqr) && !(lenSqr < 1.0E-12) ? acceleration.normalize().scale((double)0.25F) : acceleration;
   }
}
