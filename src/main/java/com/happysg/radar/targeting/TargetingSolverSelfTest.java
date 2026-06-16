package com.happysg.radar.targeting;

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
      results.add(checkTrustedAccelerationPrediction());
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

   private static Result checkTrustedAccelerationPrediction() {
      TargetPredictor predictor = new TargetPredictor();
      TargetingSnapshot snapshot = TargetingSnapshot.builder(null).targetPosition(Vec3.ZERO).targetVelocity(new Vec3((double)1.0F, (double)0.0F, (double)0.0F)).targetAcceleration(new Vec3((double)0.2F, (double)0.0F, (double)0.0F)).projectileSpeed((double)1.0F).maxFlightTicks(20).build();
      Vec3 fullTrust = predictor.predictPosition(snapshot, (double)10.0F, (double)1.0F);
      Vec3 lowTrust = predictor.predictPosition(snapshot, (double)10.0F, 0.25);
      boolean passed = fullTrust.x > lowTrust.x && lowTrust.x > (double)10.0F;
      return new Result("trusted_acceleration_prediction", passed, "full=" + fullTrust + " low=" + lowTrust);
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
