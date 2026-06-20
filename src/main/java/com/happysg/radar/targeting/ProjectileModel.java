package com.happysg.radar.targeting;

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
