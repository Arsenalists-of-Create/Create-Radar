package com.happysg.radar.targeting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public class ProjectileSimulator {
   public SimulationResult simulate(Vec3 startPosition, Vec3 aimDirection, Vec3 inheritedVelocity, ProjectileModel model, int maxTicks) {
      return model == null ? ProjectileSimulator.SimulationResult.empty(startPosition) : this.simulate(startPosition, aimDirection, inheritedVelocity, model.muzzleSpeed(), model.gravity(), model.drag(), maxTicks);
   }

   public SimulationResult simulate(Vec3 startPosition, Vec3 aimDirection, Vec3 inheritedVelocity, double muzzleSpeed, double gravity, double drag, int maxTicks) {
      if (startPosition == null) {
         startPosition = Vec3.ZERO;
      }

      if (!finite(startPosition)) {
         startPosition = Vec3.ZERO;
      }

      if (aimDirection != null && finite(aimDirection) && !(aimDirection.lengthSqr() < 1.0E-12)) {
         if (!finite(inheritedVelocity)) {
            inheritedVelocity = Vec3.ZERO;
         }

         if (Double.isFinite(muzzleSpeed) && !(muzzleSpeed <= (double)0.0F)) {
            if (!Double.isFinite(gravity)) {
               gravity = (double)0.0F;
            }

            if (!Double.isFinite(drag)) {
               drag = (double)0.0F;
            }

            Vec3 direction = aimDirection.normalize();
            Vec3 position = startPosition;
            Vec3 velocity = inheritedVelocity.add(direction.scale(muzzleSpeed));
            int ticks = Math.max(0, Math.min(1000, maxTicks));
            List<Trajectory.Sample> samples = new ArrayList<>(ticks + 1);

            for(int tick = 0; tick <= ticks; ++tick) {
               samples.add(new Trajectory.Sample(tick, position, velocity));
               position = position.add(velocity);
               velocity = velocity.add((double)0.0F, gravity, (double)0.0F);
               if (drag != (double)0.0F) {
                  velocity = velocity.scale(Math.max((double)0.0F, (double)1.0F - drag));
               }
            }

            Trajectory trajectory = new Trajectory(samples);
            return new SimulationResult(trajectory, ticks, trajectory.endPosition(), trajectory.endVelocity());
         } else {
            return ProjectileSimulator.SimulationResult.empty(startPosition);
         }
      } else {
         return ProjectileSimulator.SimulationResult.empty(startPosition);
      }
   }

   private static boolean finite(Vec3 vec) {
      return vec != null && Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
   }

   public static record SimulationResult(Trajectory trajectory, int ticks, Vec3 endPosition, Vec3 endVelocity) {
      public List<Trajectory.Sample> samples() {
         return this.trajectory.samples();
      }

      static SimulationResult empty(Vec3 position) {
         Vec3 safePosition = position == null ? Vec3.ZERO : position;
         Trajectory trajectory = new Trajectory(List.of(new Trajectory.Sample(0, safePosition, Vec3.ZERO)));
         return new SimulationResult(trajectory, 0, safePosition, Vec3.ZERO);
      }
   }
}
