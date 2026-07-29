package com.happysg.radar.targeting;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public interface ProjectileModel {
   double muzzleSpeed();

   double gravity();

   double drag();

   boolean quadraticDrag();

   default boolean cbcPhysics() {
      return false;
   }

   default double dragDensity() {
      return 1.0;
   }

   /**
    * Custom models own their complete per-tick integration. Existing simple
    * and CBC models keep using the allocation-free built-in solver branches.
    */
   default boolean usesCustomDynamics() {
      return false;
   }

   /**
    * Creates isolated state for one simulated trajectory. Stateful integrations
    * should capture their per-flight values here rather than on the shared model.
    */
   default ProjectileDynamics createDynamics(
           Vec3 startPosition,
           Vec3 aimDirection,
           Vec3 inheritedVelocity
   ) {
      return this::step;
   }

   default void step(
           int tick,
           double positionX,
           double positionY,
           double positionZ,
           double velocityX,
           double velocityY,
           double velocityZ,
           Level level,
           ProjectileStep output
   ) {
      throw new UnsupportedOperationException("Projectile model does not define custom dynamics");
   }

   default double estimateFlightTicks(double distance) {
      double speed = Math.max(1.0E-6, this.muzzleSpeed());
      return Double.isFinite(distance) && distance > 0.0 ? distance / speed : 0.0;
   }

   default Vec3 velocityAfterTick(Vec3 velocity) {
      if (velocity != null && Double.isFinite(velocity.x) && Double.isFinite(velocity.y) && Double.isFinite(velocity.z)) {
         Vec3 next = velocity;
         double speed = next.length();
         double dragForce = this.drag() * speed;
         if (this.quadraticDrag()) {
            dragForce *= speed;
         }

         dragForce = Math.min(dragForce, speed);
         if (dragForce > (double)0.0F && speed > 1.0E-8) {
            next = next.add(next.normalize().scale(-dragForce));
         }

         next = next.add((double)0.0F, this.gravity(), (double)0.0F);

         return next;
      } else {
         return Vec3.ZERO;
      }
   }

   static ProjectileModel simple(double muzzleSpeed, double gravity, double drag) {
      return simple(muzzleSpeed, gravity, drag, false);
   }

   static ProjectileModel simple(double muzzleSpeed, double gravity, double drag, boolean quadraticDrag) {
      return new SimpleProjectileModel(muzzleSpeed, gravity, drag, quadraticDrag, false, 1.0);
   }

   static ProjectileModel cbc(double muzzleSpeed, double gravity, double drag, double dragDensity, boolean quadraticDrag) {
      return new SimpleProjectileModel(muzzleSpeed, gravity, drag, quadraticDrag, true, dragDensity);
   }

   public static record SimpleProjectileModel(double muzzleSpeed, double gravity, double drag, boolean quadraticDrag, boolean cbcPhysics, double dragDensity) implements ProjectileModel {
   }
}
