package com.happysg.radar.targeting;

import com.happysg.radar.compat.cbc.CannonTargeting;
import com.happysg.radar.compat.cbc_at.CBCATLaunchMath;
import com.happysg.radar.compat.cbc_at.CBCATRocketAimSolver;
import com.happysg.radar.compat.cbc_at.CBCATRocketProjectileModel;
import com.happysg.radar.compat.cbcmoreshells.CBCMSTorpedoProjectileModel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class TargetingSolverSelfTest {
   private static final double EPS = 1.0E-6;

   private TargetingSolverSelfTest() {
   }

   public static void main(String[] args) {
      List<Result> results = runBasicInterceptChecks();
      boolean failed = false;
      for(Result result : results) {
         String detail = result.detail();
         if (detail != null && detail.length() > 300) {
            detail = detail.substring(0, 300) + "...";
         }
         System.out.println((result.passed() ? "PASS " : "FAIL ") + result.name() + ": " + detail);
         failed |= !result.passed();
      }
      if (failed) {
         throw new IllegalStateException("One or more targeting solver checks failed");
      }
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
      results.add(checkCBCATLaunchFormulas());
      results.add(checkCBCATSmallRocketPoweredStep());
      results.add(checkCBCATMediumRocketPoweredStep());
      results.add(checkCBCATRocketFuelBoundary());
      results.add(checkCBCATRocketStationarySolve());
      results.add(checkCBCATRocketMovingSolve());
      results.add(checkCBCMSBigCannonTorpedoMedia());
      results.add(checkCBCMSTorpedoTerminalVelocity());
      results.add(checkCBCMSLifetimeBoundaries());
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
      results.add(checkWarmStartSolve());
      results.add(checkWarmStartStationarySequence());
      results.add(checkWarmStartDirectionReversal());
      results.add(checkHighArcWithinLimits());
      results.add(checkHighArcWithinTypicalCbcLimits());
      results.add(checkHighArcLimitFallback());
      results.add(checkHighArcObstructionFallback());
      results.add(checkNeitherArcWithinLimits());
      results.add(checkWarmStartOutsideLimits());
      results.add(checkPitchConstraintIntersections());
      results.add(checkRotatedMountPitchConstraint());
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

   private static Result checkCBCATLaunchFormulas() {
      double heavy = CBCATLaunchMath.initialSpeed(4.0F, 0.5F, 3, 2, false, false);
      double strongHeavy = CBCATLaunchMath.initialSpeed(4.0F, 0.5F, 3, 2, false, true);
      double rocket = CBCATLaunchMath.initialSpeed(4.0F, 0.5F, 3, 2, true, false);
      int heavyLife = CBCATLaunchMath.flightLifetime(100, 0, false, true);
      int twinLife = CBCATLaunchMath.flightLifetime(100, 0, false, false);
      int rocketLife = CBCATLaunchMath.flightLifetime(100, 18, true, false);
      boolean passed = close(heavy, 5.0)
              && close(strongHeavy, 7.5)
              && close(rocket, 2.25)
              && heavyLife == 300
              && twinLife == 100
              && rocketLife == 118;
      return new Result("cbc_at_launch_formulas", passed,
              "heavy=" + heavy + " strong=" + strongHeavy + " rocket=" + rocket
                      + " lifetimes=" + heavyLife + "/" + twinLife + "/" + rocketLife);
   }

   private static Result checkCBCATSmallRocketPoweredStep() {
      CBCATRocketProjectileModel model = new CBCATRocketProjectileModel(
              1.0, -0.1, 0.0, false, 1.0, 2, 10, 0.025, 2.5
      );
      ProjectileStep step = new ProjectileStep();
      model.step(0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, null, step);
      boolean passed = close(step.positionX, 1.025)
              && close(step.positionY, -0.04375)
              && close(step.velocityX, 1.05)
              && close(step.velocityY, -0.0875);
      return new Result("cbc_at_small_rocket_powered_step", passed,
              "pos=" + step.positionX + "," + step.positionY
                      + " vel=" + step.velocityX + "," + step.velocityY);
   }

   private static Result checkCBCATMediumRocketPoweredStep() {
      CBCATRocketProjectileModel model = new CBCATRocketProjectileModel(
              1.0, 0.0, 0.0, false, 1.0, 2, 10, 0.05, 3.75
      );
      ProjectileStep step = new ProjectileStep();
      model.step(0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, null, step);
      boolean passed = close(step.positionX, 1.05) && close(step.velocityX, 1.1);
      return new Result("cbc_at_medium_rocket_powered_step", passed,
              "positionX=" + step.positionX + " velocityX=" + step.velocityX);
   }

   private static Result checkCBCATRocketFuelBoundary() {
      CBCATRocketProjectileModel model = new CBCATRocketProjectileModel(
              1.0, -0.1, 0.0, false, 1.0, 1, 10, 0.025, 2.5
      );
      ProjectileStep step = new ProjectileStep();
      model.step(1, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, null, step);
      boolean passed = close(step.positionX, 1.0)
              && close(step.positionY, -0.065)
              && close(step.velocityX, 1.0)
              && close(step.velocityY, -0.13);
      return new Result("cbc_at_rocket_fuel_boundary", passed,
              "pos=" + step.positionX + "," + step.positionY
                      + " vel=" + step.velocityX + "," + step.velocityY);
   }

   private static Result checkCBCATRocketStationarySolve() {
      return checkCBCATRocketSolve("cbc_at_rocket_stationary_intercept", Vec3.ZERO);
   }

   private static Result checkCBCATRocketMovingSolve() {
      return checkCBCATRocketSolve("cbc_at_rocket_moving_intercept", new Vec3(0.0, 0.0, 0.1));
   }

   private static Result checkCBCATRocketSolve(String name, Vec3 targetVelocity) {
      CBCATRocketProjectileModel model = new CBCATRocketProjectileModel(
              1.5, -0.04, 0.002, false, 1.0, 18, 140, 0.025, 2.5
      );
      TargetingSnapshot snapshot = TargetingSnapshot.builder(null)
              .muzzlePosition(Vec3.ZERO)
              .targetPosition(new Vec3(80.0, 4.0, 0.0))
              .targetVelocity(targetVelocity)
              .projectileSpeed(model.muzzleSpeed())
              .gravity(model.gravity())
              .drag(model.drag())
              .quadraticDrag(model.quadraticDrag())
              .cbcPhysics(true)
              .dragDensity(model.dragDensity())
              .maxFlightTicks(model.maxFlightTicks())
              .targetMotionClass(TargetMotionClass.STEADY)
              .build();
      TargetingComputer computer = CBCATRocketAimSolver.createComputer(ObstructionChecker.NONE);
      TargetingResult result = computer.solve(snapshot, model);
      boolean passed = result != null && result.valid() && result.hasShot() && result.missDistance() < 1.0;
      return new Result(name, passed, result == null ? "<null>" : result.debugString());
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
      List<Double> roots = CannonTargeting.calculateSimulatedPitchRoots(Vec3.ZERO, new Vec3(40.0, 0.0, 0.0), 4.0, -0.2, 0.0, 2);
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

   private static Result checkCBCMSBigCannonTorpedoMedia() {
      CBCMSTorpedoProjectileModel model =
              new CBCMSTorpedoProjectileModel(10.0, -0.05, 0.2, false, 1.0,
                      0.5, 8.0, 20);
      ProjectileStep air = new ProjectileStep();
      ProjectileStep water = new ProjectileStep();
      model.stepForMedium(false, 0, 0, 0, 10, 2, 0, air);
      model.stepForMedium(true, 0, 0, 0, 10, 2, 0, water);
      boolean passed = air.velocityX < 10.0 && water.velocityX < 10.0
              && water.velocityY < air.velocityY;
      return new Result("cbcms_big_cannon_torpedo_media", passed,
              "air=" + air.velocityX + "," + air.velocityY
                      + " water=" + water.velocityX + "," + water.velocityY);
   }

   private static Result checkCBCMSTorpedoTerminalVelocity() {
      CBCMSTorpedoProjectileModel model =
              new CBCMSTorpedoProjectileModel(4.0, 0.0, 0.1, false, 1.0,
                      0.0, 8.0, 200);
      ProjectileStep step = new ProjectileStep();
      double velocity = 4.0;
      double position = 0.0;
      for (int i = 0; i < 100; i++) {
         model.stepForMedium(true, position, 0, 0, velocity, 0, 0, step);
         position = step.positionX;
         velocity = step.velocityX;
      }
      boolean passed = Math.abs(velocity - 8.0) < 0.01;
      return new Result("cbcms_big_cannon_torpedo_terminal_velocity", passed, "velocity=" + velocity);
   }

   private static Result checkCBCMSLifetimeBoundaries() {
      CBCMSTorpedoProjectileModel torpedo =
              new CBCMSTorpedoProjectileModel(2.0, 0.0, 0.0, false, 1.0,
                      0.0, 2.0, 6);
      ProjectileSimulator simulator = new ProjectileSimulator();
      ProjectileSimulator.SimulationResult result =
              simulator.simulate(Vec3.ZERO, new Vec3(1, 0, 0), Vec3.ZERO,
                      torpedo, torpedo.maxFlightTicks(), null);
      boolean passed = result.ticks() == 6 && result.samples().size() == 7;
      return new Result("cbcms_big_cannon_torpedo_lifetime", passed,
              "integrations=" + result.ticks() + " samples=" + result.samples().size());
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

   private static Result checkWarmStartSolve() {
      TargetingComputer computer = new TargetingComputer(null, new ProjectileSimulator(), new TargetPredictor(), ObstructionChecker.NONE);
      Vec3 velocity = new Vec3(0.0, 0.0, 0.08);
      double maxEquivalentError = 0.0;
      int maxEvaluations = 0;
      for (double range : new double[]{50.0, 300.0, 1200.0}) {
         Vec3 position = new Vec3(range, 0.0, 25.0);
         TargetingResult previous = computer.solve(warmTrackingSnapshot(position, velocity, null));
         if (!previous.valid() || !previous.hasShot()) {
            return new Result("warm_start_tracking", false, "range=" + range + " cold solve failed: " + previous.debugString());
         }

         for (int tick = 1; tick <= 12; ++tick) {
            position = position.add(velocity);
            TargetingResult warm = computer.solve(warmTrackingSnapshot(position, velocity, previous));
            int evaluations = debugInt(warm, "candidateEvaluations=");
            double equivalentError = noDragTrackingError(position, velocity, 12.0, warm);
            maxEquivalentError = Math.max(maxEquivalentError, equivalentError);
            maxEvaluations = Math.max(maxEvaluations, evaluations);
            boolean passed = validConvergedWarmShot(warm, evaluations)
                    && warm.desiredYawDeg() > previous.desiredYawDeg() + 1.0E-9
                    && equivalentError <= 0.05;
            if (!passed) {
               return new Result("warm_start_tracking", false,
                       "range=" + range + " tick=" + tick + " evaluations=" + evaluations + " equivalentError=" + equivalentError + " previousYaw=" + previous.desiredYawDeg() + " " + warm.debugString());
            }
            previous = warm;
         }
      }
      return new Result("warm_start_tracking", true,
              "ranges=3 ticksPerRange=12 maxEvaluations=" + maxEvaluations + " maxEquivalentError=" + maxEquivalentError);
   }

   private static Result checkWarmStartStationarySequence() {
      TargetingComputer computer = new TargetingComputer(null, new ProjectileSimulator(), new TargetPredictor(), ObstructionChecker.NONE);
      Vec3 position = new Vec3(300.0, 0.0, 25.0);
      TargetingResult previous = computer.solve(warmTrackingSnapshot(position, Vec3.ZERO, null));
      if (!previous.valid() || !previous.hasShot()) {
         return new Result("warm_start_stationary", false, "cold solve failed: " + previous.debugString());
      }

      double initialYaw = previous.desiredYawDeg();
      double initialPitch = previous.desiredPitchDeg();
      int maxEvaluations = 0;
      for (int tick = 1; tick <= 6; ++tick) {
         TargetingResult warm = computer.solve(warmTrackingSnapshot(position, Vec3.ZERO, previous));
         int evaluations = debugInt(warm, "candidateEvaluations=");
         maxEvaluations = Math.max(maxEvaluations, evaluations);
         boolean stable = Math.abs(warm.desiredYawDeg() - initialYaw) <= 1.0E-12
                 && Math.abs(warm.desiredPitchDeg() - initialPitch) <= 1.0E-12;
         if (!validConvergedWarmShot(warm, evaluations) || !stable) {
            return new Result("warm_start_stationary", false,
                    "tick=" + tick + " evaluations=" + evaluations + " initialYaw=" + initialYaw + " initialPitch=" + initialPitch + " " + warm.debugString());
         }
         previous = warm;
      }
      return new Result("warm_start_stationary", true, "ticks=6 maxEvaluations=" + maxEvaluations);
   }

   private static Result checkWarmStartDirectionReversal() {
      TargetingComputer computer = new TargetingComputer(null, new ProjectileSimulator(), new TargetPredictor(), ObstructionChecker.NONE);
      Vec3 position = new Vec3(300.0, 0.0, 10.0);
      Vec3 velocity = new Vec3(0.0, 0.0, 0.08);
      TargetingResult previous = computer.solve(warmTrackingSnapshot(position, velocity, null));
      if (!previous.valid() || !previous.hasShot()) {
         return new Result("warm_start_direction_reversal", false, "cold solve failed: " + previous.debugString());
      }

      int maxEvaluations = 0;
      for (int tick = 1; tick <= 12; ++tick) {
         if (tick == 6) {
            velocity = velocity.scale(-1.0);
         }
         position = position.add(velocity);
         TargetingResult warm = computer.solve(warmTrackingSnapshot(position, velocity, previous));
         int evaluations = debugInt(warm, "candidateEvaluations=");
         maxEvaluations = Math.max(maxEvaluations, evaluations);
         double yawDelta = TargetingMath.shortestAngleDelta(previous.desiredYawDeg(), warm.desiredYawDeg());
         boolean expectedDirection = tick < 6 ? yawDelta > 1.0E-9 : yawDelta < -1.0E-9;
         boolean lowArc = "low".equals(debugValue(warm, "selectedArc="));
         if (!validConvergedWarmShot(warm, evaluations) || !expectedDirection || !lowArc) {
            return new Result("warm_start_direction_reversal", false,
                    "tick=" + tick + " evaluations=" + evaluations + " yawDelta=" + yawDelta + " " + warm.debugString());
         }
         previous = warm;
      }
      return new Result("warm_start_direction_reversal", true, "ticks=12 maxEvaluations=" + maxEvaluations);
   }

   private static TargetingSnapshot warmTrackingSnapshot(Vec3 position, Vec3 velocity, TargetingResult preferred) {
      return TargetingSnapshot.builder(null)
              .muzzlePosition(Vec3.ZERO)
              .targetPosition(position)
              .targetVelocity(velocity)
              .projectileSpeed(12.0)
              .gravity(0.0)
              .drag(0.0)
              .maxFlightTicks(400)
              .preferredYawDeg(preferred == null ? null : preferred.desiredYawDeg())
              .preferredPitchDeg(preferred == null ? null : preferred.desiredPitchDeg())
              .targetMotionClass(TargetMotionClass.STEADY)
              .build();
   }

   private static boolean validConvergedWarmShot(TargetingResult result, int evaluations) {
      return result.valid()
              && result.hasShot()
              && "warm".equals(debugValue(result, "path="))
              && "true".equals(debugValue(result, "warmConverged="))
              && "0.05".equals(debugValue(result, "refinementPrecisionBlocks="))
              && evaluations > 0
              && evaluations <= 113;
   }

   private static double noDragTrackingError(Vec3 targetPosition, Vec3 targetVelocity, double projectileSpeed, TargetingResult result) {
      double interceptTicks = estimateInterceptTicks(targetPosition, targetVelocity, projectileSpeed);
      if (!Double.isFinite(interceptTicks) || result == null || !result.hasShot()) {
         return Double.POSITIVE_INFINITY;
      }
      Vec3 analyticIntercept = targetPosition.add(targetVelocity.scale(interceptTicks));
      Vec3 solvedProjectile = TargetingMath.directionFromYawPitch(result.desiredYawDeg(), result.desiredPitchDeg()).scale(projectileSpeed * interceptTicks);
      return solvedProjectile.distanceTo(analyticIntercept);
   }

   private static Result checkHighArcWithinLimits() {
      TargetingResult result = solveArtillery(PitchConstraint.unconstrained(), ObstructionChecker.NONE, null);
      boolean passed = result.valid()
              && result.hasShot()
              && result.desiredPitchDeg() > 45.0
              && "high".equals(debugValue(result, "selectedArc="));
      return new Result("artillery_high_arc_within_limits", passed, result.debugString());
   }

   private static Result checkHighArcWithinTypicalCbcLimits() {
      TargetingSnapshot snapshot = TargetingSnapshot.builder(null)
              .muzzlePosition(Vec3.ZERO)
              .targetPosition(new Vec3(70.0, 0.0, 0.0))
              .projectileSpeed(4.0)
              .gravity(-0.2)
              .drag(0.0)
              .cbcPhysics(true)
              .maxFlightTicks(120)
              .pitchConstraint(PitchConstraint.world(-30.0, 60.0))
              .preferHighArc(true)
              .targetMotionClass(TargetMotionClass.STEADY)
              .build();
      TargetingResult result = new TargetingComputer(null, new ProjectileSimulator(), new TargetPredictor(), ObstructionChecker.NONE).solve(snapshot);
      boolean passed = result.valid()
              && result.hasShot()
              && result.desiredPitchDeg() > 45.0
              && result.desiredPitchDeg() <= 60.0
              && "high".equals(debugValue(result, "selectedArc="));
      return new Result("artillery_high_arc_typical_cbc_limits", passed, result.debugString());
   }

   private static Result checkHighArcLimitFallback() {
      TargetingResult result = solveArtillery(PitchConstraint.world(-10.0, 45.0), ObstructionChecker.NONE, null);
      String fallback = debugValue(result, "arcFallbackReason=");
      boolean passed = result.valid()
              && result.hasShot()
              && result.desiredPitchDeg() >= -10.0
              && result.desiredPitchDeg() <= 45.0
              && "low".equals(debugValue(result, "selectedArc="))
              && fallback != null
              && fallback.startsWith("high_arc_");
      return new Result("artillery_high_arc_limit_fallback", passed, result.debugString());
   }

   private static Result checkHighArcObstructionFallback() {
      ObstructionChecker checker = new ObstructionChecker() {
         @Override
         public ObstructionResult check(net.minecraft.world.level.Level level, ProjectileSimulator.SimulationResult trajectory, int maxTick) {
            List<Trajectory.Sample> samples = trajectory == null ? List.of() : trajectory.trajectory().samples();
            if (samples.size() >= 2) {
               Vec3 step = samples.get(1).position().subtract(samples.get(0).position());
               double launchPitch = Math.toDegrees(Math.atan2(step.y, Math.hypot(step.x, step.z)));
               if (launchPitch > 17.0) {
                  return ObstructionResult.blocked(1, new Vec3(0.0, 1000.0, 0.0), BlockPos.ZERO);
               }
            }
            return ObstructionResult.clearPath();
         }
      };
      TargetingResult result = solveArtillery(PitchConstraint.unconstrained(), checker, null);
      boolean passed = result.valid()
              && result.hasShot()
              && result.desiredPitchDeg() < 45.0
              && "low".equals(debugValue(result, "selectedArc="))
              && "high_arc_obstructed".equals(debugValue(result, "arcFallbackReason="));
      return new Result("artillery_high_arc_obstruction_fallback", passed, result.debugString());
   }

   private static Result checkNeitherArcWithinLimits() {
      TargetingResult result = solveArtillery(PitchConstraint.world(30.0, 45.0), ObstructionChecker.NONE, null);
      boolean passed = result.valid()
              && !result.hasShot()
              && "none".equals(debugValue(result, "selectedArc="));
      return new Result("artillery_neither_arc_within_limits", passed, result.debugString());
   }

   private static Result checkWarmStartOutsideLimits() {
      TargetingResult result = solveArtillery(PitchConstraint.world(-10.0, 45.0), ObstructionChecker.NONE, 75.0);
      int rejections = debugInt(result, "pitchConstraintRejections=");
      boolean passed = result.valid()
              && result.hasShot()
              && result.desiredPitchDeg() <= 45.0
              && "cold".equals(debugValue(result, "path="))
              && rejections > 0;
      return new Result("warm_start_outside_pitch_limits", passed, "rejections=" + rejections + " " + result.debugString());
   }

   private static Result checkPitchConstraintIntersections() {
      PitchConstraint controllerNarrower = PitchConstraint.intersect(-10.0, 20.0, -30.0, 50.0);
      PitchConstraint cannonNarrower = PitchConstraint.intersect(-30.0, 50.0, -10.0, 20.0);
      boolean passed = close(controllerNarrower.minPitchDeg(), -10.0)
              && close(controllerNarrower.maxPitchDeg(), 20.0)
              && close(cannonNarrower.minPitchDeg(), -10.0)
              && close(cannonNarrower.maxPitchDeg(), 20.0);
      return new Result("effective_pitch_limit_intersections", passed,
              "controllerNarrower=" + controllerNarrower.summary() + " cannonNarrower=" + cannonNarrower.summary());
   }

   private static Result checkRotatedMountPitchConstraint() {
      Vec3 right = new Vec3(0.0, 1.0, 0.0);
      Vec3 up = new Vec3(-1.0, 0.0, 0.0);
      Vec3 forward = new Vec3(0.0, 0.0, 1.0);
      PitchConstraint constraint = PitchConstraint.intersect(-25.0, 25.0, -40.0, 40.0, right, up, forward);

      Vec3 validLocal = TargetingMath.directionFromYawPitch(30.0, 20.0);
      Vec3 validWorld = fromMountDirection(validLocal, right, up, forward);
      TargetingMath.YawPitch recovered = constraint.mountYawPitch(validWorld);
      Vec3 invalidLocal = TargetingMath.directionFromYawPitch(30.0, 30.0);
      Vec3 invalidWorld = fromMountDirection(invalidLocal, right, up, forward);
      boolean passed = constraint.allows(validWorld)
              && !constraint.allows(invalidWorld)
              && close(recovered.yawDeg(), 30.0)
              && close(recovered.pitchDeg(), 20.0);
      return new Result("rotated_mount_pitch_frame", passed,
              "recoveredYaw=" + recovered.yawDeg() + " recoveredPitch=" + recovered.pitchDeg() + " " + constraint.summary());
   }

   private static TargetingResult solveArtillery(PitchConstraint constraint, ObstructionChecker checker, Double preferredPitchDeg) {
      TargetingSnapshot snapshot = TargetingSnapshot.builder(null)
              .muzzlePosition(Vec3.ZERO)
              .targetPosition(new Vec3(40.0, 0.0, 0.0))
              .projectileSpeed(4.0)
              .gravity(-0.2)
              .drag(0.0)
              .cbcPhysics(true)
              .maxFlightTicks(120)
              .preferredYawDeg(preferredPitchDeg == null ? null : 0.0)
              .preferredPitchDeg(preferredPitchDeg)
              .pitchConstraint(constraint)
              .preferHighArc(true)
              .targetMotionClass(TargetMotionClass.STEADY)
              .build();
      TargetingComputer computer = new TargetingComputer(null, new ProjectileSimulator(), new TargetPredictor(), checker);
      return computer.solve(snapshot);
   }

   private static Vec3 fromMountDirection(Vec3 localDirection, Vec3 right, Vec3 up, Vec3 forward) {
      return right.scale(localDirection.x)
              .add(up.scale(localDirection.y))
              .add(forward.scale(localDirection.z));
   }

   private static String debugValue(TargetingResult result, String prefix) {
      if (result != null) {
         for(String line : result.debugInfo()) {
            if (line.startsWith(prefix)) {
               return line.substring(prefix.length());
            }
         }
      }
      return null;
   }

   private static int debugInt(TargetingResult result, String prefix) {
      if (result != null) {
         for(String line : result.debugInfo()) {
            if (line.startsWith(prefix)) {
               try {
                  return Integer.parseInt(line.substring(prefix.length()));
               } catch (NumberFormatException ignored) {
                  return -1;
               }
            }
         }
      }
      return -1;
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

   private static boolean close(double actual, double expected) {
      return Math.abs(actual - expected) <= EPS;
   }

   private static boolean finite(Vec3 vec) {
      return vec != null && Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
   }

   public static record Result(String name, boolean passed, String detail) {
   }
}
