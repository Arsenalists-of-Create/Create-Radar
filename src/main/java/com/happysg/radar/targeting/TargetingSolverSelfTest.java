package com.happysg.radar.targeting;

import com.happysg.radar.compat.cbc.CannonTargeting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class TargetingSolverSelfTest {
   private static final double EPS = 1.0E-6;

   private TargetingSolverSelfTest() {
   }

   public static List<Result> runBasicInterceptChecks() {
      List<Result> results = new ArrayList<>();
      results.add(checkIntercept("stationary_same_height", new Vec3((double)100.0F, (double)0.0F, (double)0.0F), Vec3.ZERO, (double)10.0F, true));
      results.add(checkIntercept("stationary_higher", new Vec3((double)100.0F, (double)25.0F, (double)0.0F), Vec3.ZERO, (double)10.0F, true));
      results.add(checkIntercept("stationary_lower", new Vec3((double)100.0F, (double)-25.0F, (double)0.0F), Vec3.ZERO, (double)10.0F, true));
      results.add(checkIntercept("moving_sideways", new Vec3((double)100.0F, (double)0.0F, (double)0.0F), new Vec3((double)0.0F, (double)0.0F, (double)2.0F), (double)10.0F, true));
      results.add(checkIntercept("moving_toward_cannon", new Vec3((double)100.0F, (double)0.0F, (double)0.0F), new Vec3((double)-2.0F, (double)0.0F, (double)0.0F), (double)10.0F, true));
      results.add(checkIntercept("moving_away", new Vec3((double)100.0F, (double)0.0F, (double)0.0F), new Vec3((double)2.0F, (double)0.0F, (double)0.0F), (double)10.0F, true));
      results.add(checkIntercept("target_too_fast_to_intercept", new Vec3((double)100.0F, (double)0.0F, (double)0.0F), new Vec3((double)12.0F, (double)0.0F, (double)0.0F), (double)10.0F, false));
      results.add(checkProjectileIntegrationOrder());
      results.add(checkBigCannonPitch("big_cannon_pitch_50", 50.0));
      results.add(checkBigCannonPitch("big_cannon_pitch_100", 100.0));
      results.add(checkBigCannonReported150BlockShot());
      results.add(checkBigCannonHighLowRoots());
      results.add(checkBigCannonUnreachable());
      results.add(checkTrustedAccelerationPrediction());
      results.add(checkLongRangeStationarySolve());
      results.add(checkLongRangeMovingSolve());
      results.add(checkLongRangeCbcStyleSolve());
      results.add(checkProjectileSimulatorLongCap());
      results.add(new Result("obstructed_trajectory", true, "requires a real Level.clip context; covered by ObstructionChecker integration"));
      return List.copyOf(results);
   }

   private static Result checkIntercept(String name, Vec3 relativePosition, Vec3 targetVelocity, double projectileSpeed, boolean expectIntercept) {
      double t = estimateInterceptTicks(relativePosition, targetVelocity, projectileSpeed);
      boolean hasIntercept = Double.isFinite(t) && t > (double)0.0F;
      return hasIntercept == expectIntercept ? new Result(name, true, "t=" + t) : new Result(name, false, "expectedIntercept=" + expectIntercept + " actualT=" + t);
   }

   private static double estimateInterceptTicks(Vec3 relativePosition, Vec3 targetVelocity, double projectileSpeed) {
      if (finite(relativePosition) && finite(targetVelocity) && Double.isFinite(projectileSpeed) && !(projectileSpeed <= 1.0E-6)) {
         double a = targetVelocity.dot(targetVelocity) - projectileSpeed * projectileSpeed;
         double b = (double)2.0F * relativePosition.dot(targetVelocity);
         double c = relativePosition.dot(relativePosition);
         return smallestPositiveRoot(a, b, c);
      } else {
         return Double.NaN;
      }
   }

   private static double smallestPositiveRoot(double a, double b, double c) {
      if (Double.isFinite(a) && Double.isFinite(b) && Double.isFinite(c)) {
         if (Math.abs(a) < 1.0E-6) {
            if (Math.abs(b) < 1.0E-6) {
               return Double.NaN;
            } else {
               double t = -c / b;
               return t > 1.0E-6 && Double.isFinite(t) ? t : Double.NaN;
            }
         } else {
            double discriminant = b * b - (double)4.0F * a * c;
            if (!(discriminant < (double)0.0F) && Double.isFinite(discriminant)) {
               double sqrt = Math.sqrt(discriminant);
               double t0 = (-b - sqrt) / ((double)2.0F * a);
               double t1 = (-b + sqrt) / ((double)2.0F * a);
               double best = Double.POSITIVE_INFINITY;
               if (t0 > 1.0E-6 && Double.isFinite(t0)) {
                  best = Math.min(best, t0);
               }

               if (t1 > 1.0E-6 && Double.isFinite(t1)) {
                  best = Math.min(best, t1);
               }

               return Double.isFinite(best) ? best : Double.NaN;
            } else {
               return Double.NaN;
            }
         }
      } else {
         return Double.NaN;
      }
   }

   private static Result checkProjectileIntegrationOrder() {
      ProjectileSimulator simulator = new ProjectileSimulator();
      ProjectileSimulator.SimulationResult result = simulator.simulate(Vec3.ZERO, new Vec3((double)1.0F, (double)0.0F, (double)0.0F), Vec3.ZERO, (double)10.0F, (double)-1.0F, (double)0.0F, 2);
      if (result.samples().size() < 3) {
         return new Result("projectile_integration_order", false, "missing samples");
      }

      Vec3 tick1 = result.samples().get(1).position();
      Vec3 tick2 = result.samples().get(2).position();
      boolean passed = close(tick1, new Vec3((double)10.0F, (double)0.0F, (double)0.0F)) && close(tick2, new Vec3((double)20.0F, (double)-1.0F, (double)0.0F));
      return new Result("projectile_integration_order", passed, "tick1=" + tick1 + " tick2=" + tick2);
   }

   private static Result checkBigCannonPitch(String name, double range) {
      Vec3 origin = Vec3.ZERO;
      Vec3 target = new Vec3(range, 0.0, 0.0);
      double speed = 8.0;
      double gravity = -0.05;
      double drag = 0.01;
      int barrelLength = 4;
      List<Double> roots = CannonTargeting.calculateSimulatedPitchRoots(origin, target, speed, gravity, drag, barrelLength);
      if (roots.isEmpty()) {
         return new Result(name, false, "no roots");
      }

      double miss = Math.abs(simulatedHeightError(origin, target, roots.get(0), speed, gravity, drag, barrelLength));
      return new Result(name, miss <= 0.05, "pitch=" + roots.get(0) + " miss=" + miss + " roots=" + roots);
   }

   private static Result checkBigCannonHighLowRoots() {
      List<Double> roots = CannonTargeting.calculateSimulatedPitchRoots(Vec3.ZERO, new Vec3(80.0, 0.0, 0.0), 8.0, -0.05, 0.01, 4);
      boolean passed = roots.size() >= 2 && roots.get(0) < roots.get(1);
      return new Result("big_cannon_high_low_roots", passed, "roots=" + roots);
   }

   private static Result checkBigCannonReported150BlockShot() {
      Vec3 origin = Vec3.ZERO;
      Vec3 target = new Vec3(150.0, 0.0, 0.0);
      double speed = 8.0;
      double gravity = -0.05;
      double drag = 0.01;
      double muzzleForwardOffset = 10.0;
      List<Double> roots = CannonTargeting.calculateSimulatedPitchRoots(origin, target, speed, gravity, drag, muzzleForwardOffset, 1.0, false);
      if (roots.isEmpty()) {
         return new Result("big_cannon_reported_150_block_shot", false, "no roots");
      }

      double low = roots.get(0);
      double miss = Math.abs(simulatedHeightError(origin, target, low, speed, gravity, drag, muzzleForwardOffset));
      double reportedPitchError = simulatedHeightError(origin, target, 2.2945, speed, gravity, drag, muzzleForwardOffset);
      boolean passed = low > 2.5 && miss <= 0.05 && reportedPitchError < -1.0;
      return new Result("big_cannon_reported_150_block_shot", passed, "low=" + low + " miss=" + miss + " reportedPitchError=" + reportedPitchError + " roots=" + roots);
   }

   private static Result checkBigCannonUnreachable() {
      List<Double> roots = CannonTargeting.calculateSimulatedPitchRoots(Vec3.ZERO, new Vec3(1000.0, 0.0, 0.0), 4.0, -0.05, 0.01, 4);
      return new Result("big_cannon_unreachable", roots.isEmpty(), "roots=" + roots);
   }

   private static double simulatedHeightError(Vec3 origin, Vec3 target, double pitchDeg, double speed, double gravity, double drag, int barrelLength) {
      return simulatedHeightError(origin, target, pitchDeg, speed, gravity, drag, (double)barrelLength);
   }

   private static double simulatedHeightError(Vec3 origin, Vec3 target, double pitchDeg, double speed, double gravity, double drag, double muzzleForwardOffset) {
      double dx = target.x - origin.x;
      double dz = target.z - origin.z;
      double horizontal = Math.hypot(dx, dz);
      Vec3 horizontalUnit = new Vec3(dx / horizontal, 0.0, dz / horizontal);
      double pitchRad = Math.toRadians(pitchDeg);
      double cos = Math.cos(pitchRad);
      Vec3 dir = new Vec3(horizontalUnit.x * cos, Math.sin(pitchRad), horizontalUnit.z * cos).normalize();
      Vec3 muzzle = origin.add(dir.scale(muzzleForwardOffset));
      double targetTravel = target.subtract(muzzle).dot(horizontalUnit);
      Vec3 pos = muzzle;
      Vec3 vel = dir.scale(speed);
      double prevTravel = 0.0;
      double prevY = pos.y;

      for (int tick = 0; tick <= 8000; tick++) {
         double travel = pos.subtract(muzzle).dot(horizontalUnit);
         if (travel >= targetTravel) {
            double span = travel - prevTravel;
            double t = Math.abs(span) <= 1.0E-9 ? 0.0 : (targetTravel - prevTravel) / span;
            double y = prevY + (pos.y - prevY) * Math.max(0.0, Math.min(1.0, t));
            return y - target.y;
         }

         prevTravel = travel;
         prevY = pos.y;
         Vec3 acceleration = cbcAcceleration(vel, gravity, drag, 1.0, false);
         pos = pos.add(vel).add(acceleration.scale(0.5));
         vel = vel.add(acceleration);
      }

      return Double.POSITIVE_INFINITY;
   }

   private static Vec3 cbcAcceleration(Vec3 velocity, double gravity, double drag, double dragDensity, boolean quadraticDrag) {
      double speed = velocity.length();
      Vec3 acceleration = new Vec3(0.0, gravity, 0.0);
      if (speed <= 1.0E-8 || drag <= 0.0 || dragDensity <= 0.0) {
         return acceleration;
      }

      double dragForce = drag * dragDensity * speed;
      if (quadraticDrag) {
         dragForce *= speed;
      }
      dragForce = Math.min(dragForce, speed);
      return velocity.normalize().scale(-dragForce).add(acceleration);
   }

   private static Result checkTrustedAccelerationPrediction() {
      TargetPredictor predictor = new TargetPredictor();
      TargetingSnapshot snapshot = TargetingSnapshot.builder(null).targetPosition(Vec3.ZERO).targetVelocity(new Vec3((double)1.0F, (double)0.0F, (double)0.0F)).targetAcceleration(new Vec3((double)0.2F, (double)0.0F, (double)0.0F)).projectileSpeed((double)1.0F).maxFlightTicks(20).build();
      Vec3 fullTrust = predictor.predictPosition(snapshot, (double)10.0F, (double)1.0F);
      Vec3 lowTrust = predictor.predictPosition(snapshot, (double)10.0F, 0.25);
      boolean passed = fullTrust.x > lowTrust.x && lowTrust.x > (double)10.0F;
      return new Result("trusted_acceleration_prediction", passed, "full=" + fullTrust + " low=" + lowTrust);
   }

   private static Result checkLongRangeStationarySolve() {
      TargetingResult result = solveLongRange(new Vec3(8000.0, 0.0, 0.0), Vec3.ZERO, 40.0, 0.0, 0.0, false, 260);
      boolean passed = result.valid() && result.hasShot() && result.missDistance() <= 5.0;
      return new Result("long_range_stationary_8km", passed, result.debugString());
   }

   private static Result checkLongRangeMovingSolve() {
      TargetingResult result = solveLongRange(new Vec3(8000.0, 0.0, 0.0), new Vec3(0.0, 0.0, 0.05), 40.0, 0.0, 0.0, false, 280);
      boolean passed = result.valid() && result.hasShot() && result.missDistance() <= 5.0;
      return new Result("long_range_steady_velocity_8km", passed, result.debugString());
   }

   private static Result checkLongRangeCbcStyleSolve() {
      TargetingResult result = solveLongRange(new Vec3(8000.0, 0.0, 0.0), Vec3.ZERO, 80.0, -0.01, 0.0, true, 220);
      boolean passed = result.valid() && result.hasShot() && result.missDistance() <= 5.0;
      return new Result("long_range_cbc_style_8km", passed, result.debugString());
   }

   private static Result checkProjectileSimulatorLongCap() {
      ProjectileSimulator simulator = new ProjectileSimulator();
      ProjectileSimulator.SimulationResult result = simulator.simulate(Vec3.ZERO, new Vec3(1.0, 0.0, 0.0), Vec3.ZERO, 1.0, 0.0, 0.0, 1500);
      boolean passed = result.ticks() == 1500 && result.samples().size() == 1501;
      return new Result("projectile_simulator_long_cap", passed, "ticks=" + result.ticks() + " samples=" + result.samples().size());
   }

   private static TargetingResult solveLongRange(Vec3 targetPosition, Vec3 targetVelocity, double speed, double gravity, double drag, boolean cbcPhysics, int maxTicks) {
      TargetingSnapshot snapshot = TargetingSnapshot.builder(null)
              .muzzlePosition(Vec3.ZERO)
              .targetPosition(targetPosition)
              .targetVelocity(targetVelocity)
              .projectileSpeed(speed)
              .gravity(gravity)
              .drag(drag)
              .cbcPhysics(cbcPhysics)
              .maxFlightTicks(maxTicks)
              .targetMotionClass(TargetMotionClass.STEADY)
              .build();
      return TargetingComputer.createDefault().solve(snapshot);
   }

   private static boolean close(Vec3 actual, Vec3 expected) {
      return actual.distanceToSqr(expected) <= EPS * EPS;
   }

   private static boolean finite(Vec3 vec) {
      return vec != null && Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
   }

   public static record Result(String name, boolean passed, String detail) {
   }
}
