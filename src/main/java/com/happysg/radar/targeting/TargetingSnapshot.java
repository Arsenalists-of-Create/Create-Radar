package com.happysg.radar.targeting;

import javax.annotation.Nullable;
import java.util.UUID;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record TargetingSnapshot(Level level, Vec3 muzzlePosition, Vec3 inheritedVelocity, Vec3 targetPosition, Vec3 targetVelocity, Vec3 targetAcceleration, @Nullable AABB targetAabb, double projectileSpeed, double gravity, double drag, int maxFlightTicks, long gameTime, @Nullable Double preferredYawDeg, @Nullable Double preferredPitchDeg, @Nullable Double currentYawDeg, @Nullable Double currentPitchDeg, ProjectileEffect projectileEffect, double splashRadius, @Nullable UUID targetSublevelId, TargetMotionClass targetMotionClass) {
   public TargetingSnapshot(Level level, Vec3 muzzlePosition, Vec3 inheritedVelocity, Vec3 targetPosition, Vec3 targetVelocity, Vec3 targetAcceleration, @Nullable AABB targetAabb, double projectileSpeed, double gravity, double drag, int maxFlightTicks, long gameTime, @Nullable Double preferredYawDeg, @Nullable Double preferredPitchDeg, @Nullable Double currentYawDeg, @Nullable Double currentPitchDeg, ProjectileEffect projectileEffect, double splashRadius, @Nullable UUID targetSublevelId, TargetMotionClass targetMotionClass) {
      if (muzzlePosition == null) {
         muzzlePosition = Vec3.ZERO;
      }

      if (inheritedVelocity == null) {
         inheritedVelocity = Vec3.ZERO;
      }

      if (targetPosition == null) {
         targetPosition = Vec3.ZERO;
      }

      if (targetVelocity == null) {
         targetVelocity = Vec3.ZERO;
      }

      if (targetAcceleration == null) {
         targetAcceleration = Vec3.ZERO;
      }

      if (maxFlightTicks < 0) {
         maxFlightTicks = 0;
      }

      if (preferredYawDeg != null && !Double.isFinite(preferredYawDeg)) {
         preferredYawDeg = null;
      }

      if (preferredPitchDeg != null && !Double.isFinite(preferredPitchDeg)) {
         preferredPitchDeg = null;
      }

      if (currentYawDeg != null && !Double.isFinite(currentYawDeg)) {
         currentYawDeg = null;
      }

      if (currentPitchDeg != null && !Double.isFinite(currentPitchDeg)) {
         currentPitchDeg = null;
      }

      if (projectileEffect == null) {
         projectileEffect = ProjectileEffect.UNKNOWN;
      }

      if (!Double.isFinite(splashRadius) || splashRadius < (double)0.0F) {
         splashRadius = (double)0.0F;
      }

      if (targetMotionClass == null) {
         targetMotionClass = TargetMotionClass.UNKNOWN;
      }

      this.level = level;
      this.muzzlePosition = muzzlePosition;
      this.inheritedVelocity = inheritedVelocity;
      this.targetPosition = targetPosition;
      this.targetVelocity = targetVelocity;
      this.targetAcceleration = targetAcceleration;
      this.targetAabb = targetAabb;
      this.projectileSpeed = projectileSpeed;
      this.gravity = gravity;
      this.drag = drag;
      this.maxFlightTicks = maxFlightTicks;
      this.gameTime = gameTime;
      this.preferredYawDeg = preferredYawDeg;
      this.preferredPitchDeg = preferredPitchDeg;
      this.currentYawDeg = currentYawDeg;
      this.currentPitchDeg = currentPitchDeg;
      this.projectileEffect = projectileEffect;
      this.splashRadius = splashRadius;
      this.targetSublevelId = targetSublevelId;
      this.targetMotionClass = targetMotionClass;
   }

   public boolean isValid() {
      return this.level != null && this.projectileSpeed > (double)0.0F && this.maxFlightTicks > 0 && finite(this.muzzlePosition) && finite(this.targetPosition) && finite(this.inheritedVelocity) && finite(this.targetVelocity) && finite(this.targetAcceleration);
   }

   public MountState mountState() {
      return new MountState(this.muzzlePosition, this.muzzlePosition, this.inheritedVelocity, (double)0.0F, (double)0.0F);
   }

   private static boolean finite(Vec3 vec) {
      return vec != null && Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
   }

   public static Builder builder(Level level) {
      return new Builder(level);
   }

   public static class Builder {
      private final Level level;
      private Vec3 muzzlePosition;
      private Vec3 inheritedVelocity;
      private Vec3 targetPosition;
      private Vec3 targetVelocity;
      private Vec3 targetAcceleration;
      @Nullable
      private AABB targetAabb;
      private double projectileSpeed;
      private double gravity;
      private double drag;
      private int maxFlightTicks;
      private long gameTime;
      @Nullable
      private Double preferredYawDeg;
      @Nullable
      private Double preferredPitchDeg;
      @Nullable
      private Double currentYawDeg;
      @Nullable
      private Double currentPitchDeg;
      private ProjectileEffect projectileEffect;
      private double splashRadius;
      @Nullable
      private UUID targetSublevelId;
      private TargetMotionClass targetMotionClass;

      private Builder(Level level) {
         this.muzzlePosition = Vec3.ZERO;
         this.inheritedVelocity = Vec3.ZERO;
         this.targetPosition = Vec3.ZERO;
         this.targetVelocity = Vec3.ZERO;
         this.targetAcceleration = Vec3.ZERO;
         this.maxFlightTicks = 200;
         this.projectileEffect = ProjectileEffect.UNKNOWN;
         this.splashRadius = (double)0.0F;
         this.targetMotionClass = TargetMotionClass.UNKNOWN;
         this.level = level;
         this.gameTime = level == null ? 0L : level.getGameTime();
      }

      public Builder muzzlePosition(Vec3 muzzlePosition) {
         this.muzzlePosition = muzzlePosition;
         return this;
      }

      public Builder inheritedVelocity(Vec3 inheritedVelocity) {
         this.inheritedVelocity = inheritedVelocity;
         return this;
      }

      public Builder targetPosition(Vec3 targetPosition) {
         this.targetPosition = targetPosition;
         return this;
      }

      public Builder targetVelocity(Vec3 targetVelocity) {
         this.targetVelocity = targetVelocity;
         return this;
      }

      public Builder targetAcceleration(Vec3 targetAcceleration) {
         this.targetAcceleration = targetAcceleration;
         return this;
      }

      public Builder targetAabb(@Nullable AABB targetAabb) {
         this.targetAabb = targetAabb;
         return this;
      }

      public Builder projectileSpeed(double projectileSpeed) {
         this.projectileSpeed = projectileSpeed;
         return this;
      }

      public Builder gravity(double gravity) {
         this.gravity = gravity;
         return this;
      }

      public Builder drag(double drag) {
         this.drag = drag;
         return this;
      }

      public Builder maxFlightTicks(int maxFlightTicks) {
         this.maxFlightTicks = maxFlightTicks;
         return this;
      }

      public Builder gameTime(long gameTime) {
         this.gameTime = gameTime;
         return this;
      }

      public Builder preferredYawDeg(@Nullable Double preferredYawDeg) {
         this.preferredYawDeg = preferredYawDeg;
         return this;
      }

      public Builder preferredPitchDeg(@Nullable Double preferredPitchDeg) {
         this.preferredPitchDeg = preferredPitchDeg;
         return this;
      }

      public Builder currentYawDeg(@Nullable Double currentYawDeg) {
         this.currentYawDeg = currentYawDeg;
         return this;
      }

      public Builder currentPitchDeg(@Nullable Double currentPitchDeg) {
         this.currentPitchDeg = currentPitchDeg;
         return this;
      }

      public Builder projectileEffect(ProjectileEffect projectileEffect) {
         this.projectileEffect = projectileEffect;
         return this;
      }

      public Builder splashRadius(double splashRadius) {
         this.splashRadius = splashRadius;
         return this;
      }

      public Builder targetSublevelId(@Nullable UUID targetSublevelId) {
         this.targetSublevelId = targetSublevelId;
         return this;
      }

      public Builder targetMotionClass(TargetMotionClass targetMotionClass) {
         this.targetMotionClass = targetMotionClass;
         return this;
      }

      public TargetingSnapshot build() {
         return new TargetingSnapshot(this.level, this.muzzlePosition, this.inheritedVelocity, this.targetPosition, this.targetVelocity, this.targetAcceleration, this.targetAabb, this.projectileSpeed, this.gravity, this.drag, this.maxFlightTicks, this.gameTime, this.preferredYawDeg, this.preferredPitchDeg, this.currentYawDeg, this.currentPitchDeg, this.projectileEffect, this.splashRadius, this.targetSublevelId, this.targetMotionClass);
      }
   }
}
