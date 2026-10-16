package com.happysg.radar.targeting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class ProjectileSimulator {
   private static final int MAX_SIMULATION_TICKS = 4096;

   public SimulationResult simulate(Vec3 startPosition, Vec3 aimDirection, Vec3 inheritedVelocity, ProjectileModel model, int maxTicks) {
      return simulate(startPosition, aimDirection, inheritedVelocity, model, maxTicks, null);
   }

   public SimulationResult simulate(Vec3 startPosition, Vec3 aimDirection, Vec3 inheritedVelocity, ProjectileModel model, int maxTicks, Level level) {
      return simulate(startPosition, aimDirection, inheritedVelocity, model, maxTicks, level, null);
   }

   public SimulationResult simulate(Vec3 startPosition, Vec3 aimDirection, Vec3 inheritedVelocity, ProjectileModel model, int maxTicks, Level level, @Nullable BiPredicate<Trajectory.Sample, Trajectory.Sample> stopOnSegment) {
      if (model == null) {
         return SimulationResult.empty(startPosition);
      }

      if (model.usesCustomDynamics()) {
         return simulateCustom(startPosition, aimDirection, inheritedVelocity, model, maxTicks, level, stopOnSegment);
      }

      return simulate(startPosition, aimDirection, inheritedVelocity, model.muzzleSpeed(), model.gravity(), model.drag(), model.quadraticDrag(), model.cbcPhysics(), model.dragDensity(), maxTicks, level, stopOnSegment);
   }

   private SimulationResult simulateCustom(Vec3 startPosition, Vec3 aimDirection, Vec3 inheritedVelocity, ProjectileModel model, int maxTicks, Level level, @Nullable BiPredicate<Trajectory.Sample, Trajectory.Sample> stopOnSegment) {
      Vec3 safeStart = finite(startPosition) ? startPosition : Vec3.ZERO;
      Vec3 safeInherited = finite(inheritedVelocity) ? inheritedVelocity : Vec3.ZERO;
      if (aimDirection == null || !finite(aimDirection) || aimDirection.lengthSqr() < 1.0E-12
              || !Double.isFinite(model.muzzleSpeed()) || model.muzzleSpeed() <= 0.0) {
         return SimulationResult.empty(safeStart);
      }

      Vec3 direction = aimDirection.normalize();
      ProjectileDynamics dynamics = model.createDynamics(safeStart, direction, safeInherited);
      if (dynamics == null) {
         return SimulationResult.empty(safeStart);
      }
      double px = safeStart.x;
      double py = safeStart.y;
      double pz = safeStart.z;
      double vx = safeInherited.x + direction.x * model.muzzleSpeed();
      double vy = safeInherited.y + direction.y * model.muzzleSpeed();
      double vz = safeInherited.z + direction.z * model.muzzleSpeed();
      int ticks = Math.max(0, Math.min(MAX_SIMULATION_TICKS, maxTicks));
      List<Trajectory.Sample> samples = new ArrayList<>(ticks + 1);
      ProjectileStep step = new ProjectileStep();
      Trajectory.Sample previous = null;

      for (int tick = 0; tick <= ticks; tick++) {
         Trajectory.Sample current = new Trajectory.Sample(tick, new Vec3(px, py, pz), new Vec3(vx, vy, vz));
         samples.add(current);

         if (previous != null && stopOnSegment != null && stopOnSegment.test(previous, current)) {
            break;
         }

         if (tick == ticks) {
            break;
         }

         previous = current;
         dynamics.step(tick, px, py, pz, vx, vy, vz, level, step);

         px = step.positionX;
         py = step.positionY;
         pz = step.positionZ;
         vx = step.velocityX;
         vy = step.velocityY;
         vz = step.velocityZ;
      }

      Trajectory trajectory = new Trajectory(samples);
      return new SimulationResult(trajectory, trajectory.endTick(), trajectory.endPosition(), trajectory.endVelocity());
   }

   public SimulationResult simulate(Vec3 startPosition, Vec3 aimDirection, Vec3 inheritedVelocity, double muzzleSpeed, double gravity, double drag, int maxTicks) {
      return this.simulate(startPosition, aimDirection, inheritedVelocity, muzzleSpeed, gravity, drag, false, maxTicks);
   }

   public SimulationResult simulate(Vec3 startPosition, Vec3 aimDirection, Vec3 inheritedVelocity, double muzzleSpeed, double gravity, double drag, boolean quadraticDrag, int maxTicks) {
      return this.simulate(startPosition, aimDirection, inheritedVelocity, muzzleSpeed, gravity, drag, quadraticDrag, false, 1.0, maxTicks, null);
   }

   public SimulationResult simulate(Vec3 startPosition, Vec3 aimDirection, Vec3 inheritedVelocity, double muzzleSpeed, double gravity, double drag, boolean quadraticDrag, boolean cbcPhysics, double dragDensity, int maxTicks, Level level) {
      return simulate(startPosition, aimDirection, inheritedVelocity, muzzleSpeed, gravity, drag, quadraticDrag, cbcPhysics, dragDensity, maxTicks, level, null);
   }

   public SimulationResult simulate(Vec3 startPosition, Vec3 aimDirection, Vec3 inheritedVelocity, double muzzleSpeed, double gravity, double drag, boolean quadraticDrag, boolean cbcPhysics, double dragDensity, int maxTicks, Level level, @Nullable BiPredicate<Trajectory.Sample, Trajectory.Sample> stopOnSegment) {
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
            int ticks = Math.max(0, Math.min(MAX_SIMULATION_TICKS, maxTicks));
            List<Trajectory.Sample> samples = new ArrayList<>(ticks + 1);

            Trajectory.Sample previous = null;

            for (int tick = 0; tick <= ticks; ++tick) {
               Trajectory.Sample current = new Trajectory.Sample(tick, position, velocity);
               samples.add(current);

               if (previous != null && stopOnSegment != null
                       && stopOnSegment.test(previous, current)) {
                  break;
               }

               if (tick == ticks) {
                  break;
               }

               previous = current;

               if (cbcPhysics) {
                  Vec3 acceleration = cbcAcceleration(position, velocity, gravity, drag, dragDensity, quadraticDrag, level);
                  position = position.add(velocity).add(acceleration.scale(0.5));
                  velocity = velocity.add(acceleration);
               } else {
                  position = position.add(velocity);
                  double speed = velocity.length();
                  double dragForce = drag * speed;
                  if (quadraticDrag) {
                     dragForce *= speed;
                  }

                  dragForce = Math.min(dragForce, speed);
                  if (dragForce > (double)0.0F && speed > 1.0E-8) {
                     velocity = velocity.add(velocity.normalize().scale(-dragForce));
                  }

                  velocity = velocity.add((double)0.0F, gravity, (double)0.0F);
               }
            }

            Trajectory trajectory = new Trajectory(samples);
            return new SimulationResult(trajectory, trajectory.endTick(), trajectory.endPosition(), trajectory.endVelocity());
         } else {
            return ProjectileSimulator.SimulationResult.empty(startPosition);
         }
      } else {
         return ProjectileSimulator.SimulationResult.empty(startPosition);
      }
   }

   private static Vec3 cbcAcceleration(Vec3 position, Vec3 velocity, double gravity, double drag, double dragDensity, boolean quadraticDrag, Level level) {
      double speed = velocity.length();
      Vec3 acceleration = new Vec3((double)0.0F, gravity, (double)0.0F);
      if (speed <= 1.0E-8 || drag <= (double)0.0F) {
         return acceleration;
      }

      double density = !Double.isFinite(dragDensity) || dragDensity < 0.0F ? 1.0F : dragDensity;
      if (level != null) {
         density += LoadedFluidDrag.sample(level, BlockPos.containing(position));
      }

      if (density <= (double)0.0F) {
         return acceleration;
      }

      double dragForce = drag * density * speed;
      if (quadraticDrag) {
         dragForce *= speed;
      }
      dragForce = Math.min(dragForce, speed);
      return velocity.normalize().scale(-dragForce).add(acceleration);
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
