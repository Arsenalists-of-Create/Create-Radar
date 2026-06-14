package com.happysg.radar.targeting;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.config.RadarConfig;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class SimulatedAimSolver implements AimSolver {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final double MISS_SCORE_WEIGHT = (double)2.0F;
   private static final double CONTINUITY_SCORE_WEIGHT = 0.015;
   private static final double FLIGHT_TIME_SCORE_WEIGHT = (double)0.25F;
   private static final double CONFIDENCE_SCORE_WEIGHT = (double)0.5F;
   private static final double REJECT_SCORE = (double)1000000.0F;
   private static final int OBSTRUCTION_SHORTLIST_SIZE = 8;
   private final ProjectileSimulator projectileSimulator;
   private final TargetPredictor targetPredictor;

   public SimulatedAimSolver(ProjectileSimulator projectileSimulator, TargetPredictor targetPredictor) {
      this.projectileSimulator = projectileSimulator == null ? new ProjectileSimulator() : projectileSimulator;
      this.targetPredictor = targetPredictor == null ? new TargetPredictor() : targetPredictor;
   }

   public TargetingResult solve(TargetingSnapshot snapshot, ProjectileModel projectileModel, ObstructionChecker obstructionChecker) {
      if (snapshot != null && projectileModel != null && snapshot.isValid()) {
         Vec3 toTarget = snapshot.targetPosition().subtract(snapshot.muzzlePosition());
         if (toTarget.lengthSqr() < 1.0E-8) {
            return TargetingResult.noShot("target too close");
         } else {
            InitialGuess initial = this.initialGuess(snapshot, projectileModel);
            Candidate best = null;
            List<Candidate> shortlist = new ArrayList<>(9);

            for(SearchPass pass : searchPasses()) {
               double centerYaw = best == null ? initial.yawDeg : best.yawDeg;
               double centerPitch = best == null ? initial.pitchDeg : best.pitchDeg;

               for(double yawOffset = -pass.yawRange; yawOffset <= pass.yawRange + 1.0E-9; yawOffset += pass.step) {
                  for(double pitchOffset = -pass.pitchDownRange; pitchOffset <= pass.pitchUpRange + 1.0E-9; pitchOffset += pass.step) {
                     double yaw = TargetingMath.wrap180(centerYaw + yawOffset);
                     double pitch = clampPitch(centerPitch + pitchOffset);
                     Candidate candidate = this.evaluate(snapshot, projectileModel, yaw, pitch, ObstructionResult.clearPath());
                     addToShortlist(shortlist, candidate);
                     if (best == null || score(snapshot, candidate) < score(snapshot, best)) {
                        best = candidate;
                     }
                  }
               }
            }

            if (best == null) {
               return TargetingResult.noShot("no simulated candidate");
            } else {
               best = this.chooseObstructionCheckedBest(snapshot, projectileModel, obstructionChecker, shortlist);
               List<String> debug = new ArrayList<>();
               debug.add("solver=simulated_moving_v2");
               debug.add("initialInterceptTicks=" + initial.interceptTicks);
               debug.add("bestTick=" + best.flightTick);
               debug.add("miss=" + best.missDistance);
               debug.add("score=" + best.score);
               debug.add("confidence=" + best.confidence);
               debug.add("motionClass=" + snapshot.targetMotionClass());
               debug.add("obstruction=" + (best.obstruction.blocked() ? "blocked" : "clear"));
               if (best.obstruction.blocked()) {
                  debug.add("blockedTick=" + best.obstruction.blockedTick());
                  debug.add("blockedBlock=" + String.valueOf(best.obstruction.blockPosition()));
                  debug.add("blockedDistanceToTarget=" + distanceFromObstructionToTarget(best));
                  debug.add("blockedTargetSublevel=" + isObstructionInTargetSublevel(snapshot, best));
               }

               if (RadarConfig.DEBUG_BEAMS) {
                  LOGGER.debug("TargetingComputer simulated solve yaw={} pitch={} tick={} miss={} score={} confidence={} obstruction={}", new Object[]{best.yawDeg, best.pitchDeg, best.flightTick, best.missDistance, best.score, best.confidence, best.obstruction.blocked() ? "blocked" : "clear"});
               }

               AimSolution solution = new AimSolution(best.yawDeg, best.pitchDeg, TargetingMath.directionFromYawPitch(best.yawDeg, best.pitchDeg), best.predictedTargetPosition, best.flightTick, best.missDistance);
               TargetingDebugInfo debugData = debugInfo(snapshot, best);
               if (!isAcceptableShot(snapshot, best)) {
                  return TargetingResult.noShot(best.obstruction.blocked() ? "obstructed" : "low quality shot", debug, debugData);
               } else {
                  return TargetingResult.shot(solution, best.confidence, debug, debugData);
               }
            }
         }
      } else {
         return TargetingResult.invalid("invalid snapshot");
      }
   }

   private InitialGuess initialGuess(TargetingSnapshot snapshot, ProjectileModel projectileModel) {
      double interceptTicks = this.estimateInterceptTicks(snapshot, projectileModel);
      Vec3 aimPoint = this.targetPredictor.predictPosition(snapshot, interceptTicks);
      Vec3 aimVector = aimPoint.subtract(snapshot.muzzlePosition());
      if (aimVector.lengthSqr() < 1.0E-8) {
         aimVector = snapshot.targetPosition().subtract(snapshot.muzzlePosition());
      }

      TargetingMath.YawPitch yp = TargetingMath.yawPitchFromDirection(aimVector);
      return new InitialGuess(yp.yawDeg(), yp.pitchDeg(), interceptTicks);
   }

   private double estimateInterceptTicks(TargetingSnapshot snapshot, ProjectileModel projectileModel) {
      Vec3 r = snapshot.targetPosition().subtract(snapshot.muzzlePosition());
      Vec3 v = snapshot.targetVelocity().subtract(snapshot.inheritedVelocity());
      double s = Math.max(1.0E-6, projectileModel.muzzleSpeed());
      double a = v.dot(v) - s * s;
      double b = (double)2.0F * r.dot(v);
      double c = r.dot(r);
      double t = smallestPositiveRoot(a, b, c);
      if (Double.isFinite(t) && t > (double)0.0F) {
         return Math.min((double)snapshot.maxFlightTicks(), t);
      } else {
         double fallback = Math.sqrt(c) / s;
         return Double.isFinite(fallback) ? Math.min((double)snapshot.maxFlightTicks(), Math.max((double)0.0F, fallback)) : (double)0.0F;
      }
   }

   private Candidate evaluate(TargetingSnapshot snapshot, ProjectileModel projectileModel, double yawDeg, double pitchDeg, ObstructionResult obstruction) {
      Vec3 direction = TargetingMath.directionFromYawPitch(yawDeg, pitchDeg);
      ProjectileSimulator.SimulationResult trajectory = this.projectileSimulator.simulate(snapshot.muzzlePosition(), direction, snapshot.inheritedVelocity(), projectileModel, snapshot.maxFlightTicks());
      double bestMiss = Double.POSITIVE_INFINITY;
      int bestTick = 0;
      Vec3 bestTargetPos = snapshot.targetPosition();
      Vec3 bestProjectilePos = snapshot.muzzlePosition();

      for(Trajectory.Sample sample : trajectory.trajectory().samples()) {
         double tick = (double)sample.tick();
         Vec3 predictedTarget = this.targetPredictor.predictPosition(snapshot, tick);
         AABB predictedBox = this.targetPredictor.predictAabb(snapshot, tick);
         double miss = distanceToTarget(sample.position(), predictedTarget, predictedBox);
         if (miss < bestMiss) {
            bestMiss = miss;
            bestTick = sample.tick();
            bestTargetPos = predictedTarget;
            bestProjectilePos = sample.position();
         }
      }

      Candidate candidate = new Candidate(yawDeg, pitchDeg, bestTick, bestMiss, bestTargetPos, bestProjectilePos, trajectory, obstruction, (double)0.0F, (double)0.0F);
      double confidence = this.confidence(snapshot, candidate);
      double score = score(snapshot, candidate, confidence);
      return new Candidate(yawDeg, pitchDeg, bestTick, bestMiss, bestTargetPos, bestProjectilePos, trajectory, obstruction, confidence, score);
   }

   private static void addToShortlist(List<Candidate> shortlist, Candidate candidate) {
      shortlist.add(candidate);
      shortlist.sort(Comparator.comparingDouble(Candidate::score));
      if (shortlist.size() > 8) {
         shortlist.remove(shortlist.size() - 1);
      }

   }

   private Candidate chooseObstructionCheckedBest(TargetingSnapshot snapshot, ProjectileModel projectileModel, ObstructionChecker obstructionChecker, List<Candidate> shortlist) {
      Candidate best = null;

      for(Candidate candidate : shortlist) {
         ObstructionResult obstruction = obstructionChecker == null ? ObstructionResult.clearPath() : obstructionChecker.check(snapshot.level(), candidate.trajectory, candidate.flightTick);
         Candidate checked = this.evaluate(snapshot, projectileModel, candidate.yawDeg, candidate.pitchDeg, obstruction);
         if (best == null || score(snapshot, checked) < score(snapshot, best)) {
            best = checked;
         }
      }

      return best;
   }

   private static double distanceToTarget(Vec3 projectilePosition, Vec3 targetPosition, @Nullable AABB targetAabb) {
      return targetAabb != null ? TargetingMath.distancePointToAabb(projectilePosition, targetAabb) : projectilePosition.distanceTo(targetPosition);
   }

   private static double clampPitch(double pitchDeg) {
      return Math.max((double)-89.0F, Math.min((double)89.0F, pitchDeg));
   }

   private static double score(TargetingSnapshot snapshot, Candidate candidate) {
      return score(snapshot, candidate, candidate.confidence);
   }

   private static double score(TargetingSnapshot snapshot, Candidate candidate, double confidence) {
      if (Double.isFinite(candidate.missDistance) && Double.isFinite(candidate.yawDeg) && Double.isFinite(candidate.pitchDeg)) {
         double hitTolerance = hitTolerance(snapshot);
         double normalizedMiss = candidate.missDistance / Math.max(0.05, hitTolerance);
         double continuity = continuityPenalty(snapshot, candidate.yawDeg, candidate.pitchDeg);
         double timePenalty = Math.max((double)0.0F, (double)candidate.flightTick) / (double)120.0F;
         double obstructionPenalty = obstructionPenalty(snapshot, candidate);
         double confidencePenalty = (double)1.0F - Math.max((double)0.0F, Math.min((double)1.0F, confidence));
         return normalizedMiss * (double)2.0F + continuity * 0.015 + timePenalty * (double)0.25F + confidencePenalty * (double)0.5F + obstructionPenalty;
      } else {
         return (double)1000000.0F;
      }
   }

   private static double continuityPenalty(TargetingSnapshot snapshot, double yawDeg, double pitchDeg) {
      if (snapshot.preferredYawDeg() != null && snapshot.preferredPitchDeg() != null) {
         double yawDelta = TargetingMath.shortestAngleDelta(snapshot.preferredYawDeg(), yawDeg);
         double pitchDelta = pitchDeg - snapshot.preferredPitchDeg();
         double angleDist = Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
         return angleDist < 0.2 ? (double)0.0F : angleDist;
      } else {
         return (double)0.0F;
      }
   }

   private double confidence(TargetingSnapshot snapshot, Candidate candidate) {
      if (Double.isFinite(candidate.missDistance) && Double.isFinite(candidate.yawDeg) && Double.isFinite(candidate.pitchDeg)) {
         double missFactor = (double)1.0F / ((double)1.0F + candidate.missDistance);
         double timeFactor = (double)1.0F / ((double)1.0F + (double)candidate.flightTick / (double)120.0F);
         double accelMag = this.targetPredictor.sanitizeAcceleration(snapshot.targetAcceleration()).length();
         double accelFactor = (double)1.0F / ((double)1.0F + accelMag * (double)8.0F);
         double dataFactor = snapshot.targetAabb() == null ? 0.9 : (double)1.0F;
         double obstructionFactor = obstructionConfidenceFactor(snapshot, candidate);
         return Math.max((double)0.0F, Math.min((double)1.0F, missFactor * timeFactor * accelFactor * dataFactor * obstructionFactor));
      } else {
         return (double)0.0F;
      }
   }

   private static boolean isAcceptableShot(TargetingSnapshot snapshot, Candidate candidate) {
      if (Double.isFinite(candidate.score) && !(candidate.score >= (double)500000.0F)) {
         if (candidate.missDistance > acceptableMissDistance(snapshot)) {
            return false;
         } else if (!candidate.obstruction.blocked()) {
            return true;
         } else {
            return isObstructionAtPredictedTarget(snapshot, candidate) || isObstructionInTargetSublevel(snapshot, candidate);
         }
      } else {
         return false;
      }
   }

   private static double hitTolerance(TargetingSnapshot snapshot) {
      return directTargetHitTolerance(snapshot);
   }

   private static double acceptableMissDistance(TargetingSnapshot snapshot) {
      return directTargetHitTolerance(snapshot);
   }

   private static double obstructionPenalty(TargetingSnapshot snapshot, Candidate candidate) {
      if (!candidate.obstruction.blocked()) {
         return (double)0.0F;
      } else if (isObstructionAtPredictedTarget(snapshot, candidate) || isObstructionInTargetSublevel(snapshot, candidate)) {
         return 0.1;
      } else {
         return (double)1000000.0F;
      }
   }

   private static double obstructionConfidenceFactor(TargetingSnapshot snapshot, Candidate candidate) {
      if (!candidate.obstruction.blocked()) {
         return (double)1.0F;
      } else if (isObstructionAtPredictedTarget(snapshot, candidate) || isObstructionInTargetSublevel(snapshot, candidate)) {
         return 0.95;
      } else {
         return (double)0.0F;
      }
   }

   private static boolean isObstructionInTargetSublevel(TargetingSnapshot snapshot, Candidate candidate) {
      if (!Mods.SABLE.isLoaded() || snapshot.targetSublevelId() == null || !candidate.obstruction.blocked()) {
         return false;
      }

      BlockPos blockPosition = candidate.obstruction.blockPosition();
      if (blockPosition == null || snapshot.level() == null) {
         return false;
      }

      SubLevelAccess hitSublevel = SableCompanion.INSTANCE.getContaining(snapshot.level(), blockPosition);
      return hitSublevel != null && snapshot.targetSublevelId().equals(hitSublevel.getUniqueId());
   }

   private static boolean isObstructionAtPredictedTarget(TargetingSnapshot snapshot, Candidate candidate) {
      if (candidate.obstruction.blocked() && candidate.obstruction.blockedPosition() != null) {
         int blockedTick = Math.max(0, candidate.obstruction.blockedTick());
         Vec3 targetAtBlock = predictTargetPosition(snapshot, (double)blockedTick);
         AABB targetBoxAtBlock = predictTargetAabb(snapshot, (double)blockedTick);
         double distance = distanceToTarget(candidate.obstruction.blockedPosition(), targetAtBlock, targetBoxAtBlock);
         return distance <= directTargetHitTolerance(snapshot);
      } else {
         return false;
      }
   }

   private static double directTargetHitTolerance(TargetingSnapshot snapshot) {
      return snapshot.targetAabb() == null ? (double)0.75F : (double)0.25F;
   }

   private static Vec3 predictTargetPosition(TargetingSnapshot snapshot, double tick) {
      double safeTick = Double.isFinite(tick) ? Math.max((double)0.0F, tick) : (double)0.0F;
      if (snapshot.targetMotionClass() == TargetMotionClass.SPRINT_JUMP) {
         Vec3 velocity = snapshot.targetVelocity();
         double gravity = Double.isFinite(snapshot.gravity()) ? snapshot.gravity() : (double)-0.08F;
         return snapshot.targetPosition().add(velocity.x * safeTick, velocity.y * safeTick + (double)0.5F * gravity * safeTick * safeTick, velocity.z * safeTick);
      }

      return snapshot.targetPosition().add(snapshot.targetVelocity().scale(safeTick)).add(snapshot.targetAcceleration().scale((double)0.5F * safeTick * safeTick));
   }

   @Nullable
   private static AABB predictTargetAabb(TargetingSnapshot snapshot, double tick) {
      return snapshot.targetAabb() == null ? null : snapshot.targetAabb().move(predictTargetPosition(snapshot, tick).subtract(snapshot.targetPosition()));
   }

   private static double distanceFromObstructionToTarget(Candidate candidate) {
      Vec3 blockedPosition = candidate.obstruction.blockedPosition();
      return blockedPosition != null && candidate.predictedTargetPosition != null ? blockedPosition.distanceTo(candidate.predictedTargetPosition) : Double.POSITIVE_INFINITY;
   }

   private static TargetingDebugInfo debugInfo(TargetingSnapshot snapshot, Candidate candidate) {
      return new TargetingDebugInfo(snapshot.targetPosition(), candidate.predictedTargetPosition, candidate.closestProjectilePosition, snapshot.targetVelocity(), snapshot.targetAcceleration(), snapshot.inheritedVelocity(), candidate.trajectory.trajectory().samples(), candidate.flightTick, candidate.missDistance, candidate.yawDeg, candidate.pitchDeg, snapshot.currentYawDeg(), snapshot.currentPitchDeg(), candidate.confidence, candidate.obstruction);
   }

   private static List<SearchPass> searchPasses() {
      return List.of(new SearchPass((double)20.0F, (double)18.0F, (double)50.0F, (double)4.0F), new SearchPass((double)3.0F, (double)5.0F, (double)5.0F, (double)1.0F), new SearchPass((double)1.0F, (double)1.0F, (double)1.0F, (double)0.25F));
   }

   private static double smallestPositiveRoot(double a, double b, double c) {
      double eps = 1.0E-9;
      if (Math.abs(a) < 1.0E-9) {
         if (Math.abs(b) < 1.0E-9) {
            return Double.NaN;
         } else {
            double t = -c / b;
            return t > 1.0E-9 && Double.isFinite(t) ? t : Double.NaN;
         }
      } else {
         double discriminant = b * b - (double)4.0F * a * c;
         if (!(discriminant < (double)0.0F) && Double.isFinite(discriminant)) {
            double sqrt = Math.sqrt(discriminant);
            double t0 = (-b - sqrt) / ((double)2.0F * a);
            double t1 = (-b + sqrt) / ((double)2.0F * a);
            double best = Double.POSITIVE_INFINITY;
            if (t0 > 1.0E-9 && Double.isFinite(t0)) {
               best = Math.min(best, t0);
            }

            if (t1 > 1.0E-9 && Double.isFinite(t1)) {
               best = Math.min(best, t1);
            }

            return Double.isFinite(best) ? best : Double.NaN;
         } else {
            return Double.NaN;
         }
      }
   }

   private static record SearchPass(double yawRange, double pitchDownRange, double pitchUpRange, double step) {
   }

   private static record InitialGuess(double yawDeg, double pitchDeg, double interceptTicks) {
   }

   private static record Candidate(double yawDeg, double pitchDeg, int flightTick, double missDistance, Vec3 predictedTargetPosition, Vec3 closestProjectilePosition, ProjectileSimulator.SimulationResult trajectory, ObstructionResult obstruction, double confidence, double score) {
   }
}
