package com.happysg.radar.targeting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public class TargetingComputer {
   public static final boolean USE_NEW_SOLVER_FOR_GAMEPLAY = true;
   private final AimSolver solver;
   private final ProjectileSimulator projectileSimulator;
   private final TargetPredictor targetPredictor;
   private final ObstructionChecker obstructionChecker;

   public TargetingComputer(AimSolver solver, ProjectileSimulator projectileSimulator, TargetPredictor targetPredictor, ObstructionChecker obstructionChecker) {
      this.projectileSimulator = projectileSimulator == null ? new ProjectileSimulator() : projectileSimulator;
      this.targetPredictor = targetPredictor == null ? new TargetPredictor() : targetPredictor;
      this.obstructionChecker = obstructionChecker == null ? ObstructionChecker.NONE : obstructionChecker;
      this.solver = solver == null ? new SimulatedAimSolver(this.projectileSimulator, this.targetPredictor) : solver;
   }

   public static TargetingComputer createDefault() {
      return new TargetingComputer(null, new ProjectileSimulator(), new TargetPredictor(), new ObstructionChecker());
   }

   public TargetingResult solve(TargetingSnapshot snapshot) {
      if (snapshot != null && snapshot.isValid()) {
         ProjectileModel model = snapshot.cbcPhysics()
                 ? ProjectileModel.cbc(snapshot.projectileSpeed(), snapshot.gravity(), snapshot.drag(), snapshot.dragDensity(), snapshot.quadraticDrag())
                 : ProjectileModel.simple(snapshot.projectileSpeed(), snapshot.gravity(), snapshot.drag(), snapshot.quadraticDrag());
         return this.solver.solve(snapshot, model, this.obstructionChecker);
      } else {
         return TargetingResult.invalid("invalid snapshot");
      }
   }

   public ProjectileSimulator projectileSimulator() {
      return this.projectileSimulator;
   }

   public TargetPredictor targetPredictor() {
      return this.targetPredictor;
   }

   private final class PlaceholderAimSolver implements AimSolver {
      public TargetingResult solve(TargetingSnapshot snapshot, ProjectileModel projectileModel, ObstructionChecker obstructionChecker) {
         Vec3 toTarget = snapshot.targetPosition().subtract(snapshot.muzzlePosition());
         double distance = toTarget.length();
         if (!(distance < 1.0E-6) && !(snapshot.projectileSpeed() <= (double)0.0F)) {
            Vec3 direction = toTarget.normalize();
            double yawDeg = Math.toDegrees(Math.atan2(direction.z, direction.x)) + (double)90.0F;
            double horizontal = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
            double pitchDeg = Math.toDegrees(Math.atan2(toTarget.y, Math.max(1.0E-6, horizontal)));
            int estimatedTicks = (int)Math.ceil(distance / Math.max(1.0E-6, snapshot.projectileSpeed()));
            estimatedTicks = Math.max(0, Math.min(snapshot.maxFlightTicks(), estimatedTicks));
            ProjectileSimulator.SimulationResult trajectory = TargetingComputer.this.projectileSimulator.simulate(snapshot.muzzlePosition(), direction, snapshot.inheritedVelocity(), projectileModel, estimatedTicks);
            Vec3 predictedTarget = TargetingComputer.this.targetPredictor.predictPosition(snapshot, (double)estimatedTicks);
            double missDistance = trajectory.endPosition().distanceTo(predictedTarget);
            ObstructionResult obstruction = obstructionChecker.check(snapshot.level(), trajectory, estimatedTicks);
            TargetingDebugInfo debugData = new TargetingDebugInfo(snapshot.targetPosition(), predictedTarget, trajectory.endPosition(), snapshot.targetVelocity(), snapshot.targetAcceleration(), snapshot.inheritedVelocity(), trajectory.trajectory().samples(), estimatedTicks, missDistance, yawDeg, pitchDeg, snapshot.currentYawDeg(), snapshot.currentPitchDeg(), this.confidenceFromMiss(missDistance), obstruction);
            List<String> debug = new ArrayList<>();
            debug.add("solver=placeholder_direct");
            debug.add("obstruction=" + (obstruction.blocked() ? "blocked" : "clear"));
            AimSolution aim = new AimSolution(yawDeg, pitchDeg, direction, predictedTarget, estimatedTicks, missDistance);
            return obstruction.clear() ? TargetingResult.shot(aim, this.confidenceFromMiss(missDistance), debug, debugData) : TargetingResult.noShot("obstructed", debug, debugData);
         } else {
            return TargetingResult.noShot("target too close or projectile speed invalid");
         }
      }

      private double confidenceFromMiss(double missDistance) {
         return !Double.isFinite(missDistance) ? (double)0.0F : Math.max((double)0.0F, Math.min((double)1.0F, (double)1.0F / ((double)1.0F + missDistance)));
      }
   }
}
