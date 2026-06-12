package com.happysg.radar.targeting;

import net.minecraft.world.phys.Vec3;

public interface ProjectileModel {
   double muzzleSpeed();

   double gravity();

   double drag();

   default Vec3 velocityAfterTick(Vec3 velocity) {
      if (velocity != null && Double.isFinite(velocity.x) && Double.isFinite(velocity.y) && Double.isFinite(velocity.z)) {
         Vec3 next = velocity.add((double)0.0F, this.gravity(), (double)0.0F);
         double drag = this.drag();
         if (drag != (double)0.0F) {
            next = next.scale(Math.max((double)0.0F, (double)1.0F - drag));
         }

         return next;
      } else {
         return Vec3.ZERO;
      }
   }

   static ProjectileModel simple(double muzzleSpeed, double gravity, double drag) {
      return new SimpleProjectileModel(muzzleSpeed, gravity, drag);
   }

   public static record SimpleProjectileModel(double muzzleSpeed, double gravity, double drag) implements ProjectileModel {
   }
}
