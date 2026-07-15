package com.happysg.radar.targeting;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.config.RadarConfig;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
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
   private static final int OBSTRUCTION_SHORTLIST_SIZE = 16;
   private static final int REFINEMENT_SHORTLIST_SIZE = 6;
   private static final int MAX_REFINEMENT_ITERATIONS = 28;
   private static final double MIN_REFINEMENT_STEP_DEG = 0.001;
   private static final double MAX_REFINEMENT_STEP_DEG = 0.25;
   private static final double REFINEMENT_TARGET_BLOCKS = 0.25;
   private static final double LONG_RANGE_ACCEPTANCE_DISTANCE_BLOCKS = 8000.0;
   private static final double LONG_RANGE_ACCEPTANCE_MISS_BLOCKS = 5.0;
   private static final double MIN_ACCEPTABLE_CONFIDENCE = 0.05;
   private static final int FRACTIONAL_DISTANCE_REFINEMENT_STEPS = 9;
   private static final int HORIZON_MARGIN_TICKS = 80;
   private static final int HORIZON_EDGE_TICKS = 16;
   private static final int WARM_REFINEMENT_ITERATIONS = 10;
   private final ProjectileSimulator projectileSimulator;
   private final TargetPredictor targetPredictor;

   public SimulatedAimSolver(ProjectileSimulator projectileSimulator, TargetPredictor targetPredictor) {
      this.projectileSimulator = projectileSimulator == null ? new ProjectileSimulator() : projectileSimulator;
      this.targetPredictor = targetPredictor == null ? new TargetPredictor() : targetPredictor;
   }

   public TargetingResult solve(TargetingSnapshot snapshot, ProjectileModel projectileModel, ObstructionChecker obstructionChecker) {
      if (snapshot == null || projectileModel == null || !snapshot.isValid()) {
         return TargetingResult.invalid("invalid snapshot");
      }

      if (!snapshot.pitchConstraint().hasReachablePitch()) {
         return TargetingResult.noShot("no reachable pitch", List.of(
                 "solver=simulated_moving_v4",
                 "pitchConstraint=" + snapshot.pitchConstraint().summary(),
                 "selectedArc=none",
                 "arcFallbackReason=empty_pitch_intersection"
         ));
      }

      Vec3 toTarget = snapshot.targetPosition().subtract(snapshot.muzzlePosition());
      if (toTarget.lengthSqr() < 1.0E-8) {
         return TargetingResult.noShot("target too close");
      }

      long startedNanos = System.nanoTime();
      SolveStats stats = new SolveStats();
      InitialGuess initial = this.initialGuess(snapshot, projectileModel);
      int maxHorizon = snapshot.maxFlightTicks();
      int horizon = initialHorizon(snapshot, initial.interceptTicks);

      if (snapshot.preferredYawDeg() != null && snapshot.preferredPitchDeg() != null) {
         EvaluationContext warmContext = new EvaluationContext(snapshot, projectileModel, horizon, stats);
         Candidate warmSeed = this.evaluate(warmContext, snapshot.preferredYawDeg(), snapshot.preferredPitchDeg(), ObstructionResult.clearPath());
         if (warmSeed != null) {
            RefinementResult warmRefinement = this.refineCandidate(warmContext, warmSeed, WARM_REFINEMENT_ITERATIONS);
            Candidate warm = this.applyObstruction(snapshot, projectileModel, obstructionChecker, warmRefinement.candidate());
            boolean warmHighArc = warm.pitchDeg >= highArcPitchFloor(initial.pitchDeg);
            boolean correctArc = !snapshot.preferHighArc() || warmHighArc;
            if (correctArc && isAcceptableShot(snapshot, warm) && !nearHorizon(warm, horizon)) {
               stats.warmStart = true;
               return this.buildResult(snapshot, projectileModel, initial, warm, warmRefinement.iterations(), warmRefinement.finalStepDeg(), warmHighArc ? "high" : "low", "none", stats, startedNanos);
            }
         }
      }

      Candidate best = null;
      RefinementSummary refinement = new RefinementSummary(null, 0, 0.0);
      String selectedArc = "none";
      String fallbackReason = snapshot.preferHighArc() ? "high_arc_unavailable" : "not_requested";
      while(true) {
         EvaluationContext context = new EvaluationContext(snapshot, projectileModel, horizon, stats);
         List<Candidate> shortlist = this.searchCandidates(context, initial);
         RefinementSummary lowRefinement = this.refineShortlist(context, shortlist);
         Candidate lowBest = this.chooseObstructionCheckedBest(context, obstructionChecker, shortlist);
         boolean lowAcceptable = lowBest != null && isAcceptableShot(snapshot, lowBest);

         best = lowBest;
         refinement = lowRefinement;
         selectedArc = lowAcceptable ? "low" : "none";
         fallbackReason = snapshot.preferHighArc() ? "high_arc_unavailable" : "not_requested";

         if (snapshot.preferHighArc()) {
            int rejectedBeforeHighSearch = stats.pitchConstraintRejections;
            List<Candidate> highArcShortlist = this.searchHighArcCandidates(context, initial);
            RefinementSummary highArcRefinement = this.refineShortlist(context, highArcShortlist);
            Candidate highArcBest = this.chooseObstructionCheckedBest(context, obstructionChecker, highArcShortlist);
            if (highArcBest != null && highArcBest.pitchDeg >= highArcPitchFloor(initial.pitchDeg) && isAcceptableShot(snapshot, highArcBest)) {
               best = highArcBest;
               refinement = highArcRefinement;
               selectedArc = "high";
               fallbackReason = "none";
            } else {
               fallbackReason = highArcFailureReason(highArcShortlist, highArcBest, stats.pitchConstraintRejections > rejectedBeforeHighSearch);
               if (lowAcceptable) {
                  best = lowBest;
                  refinement = lowRefinement;
                  selectedArc = "low";
               } else if (betterDiagnosticCandidate(snapshot, highArcBest, best) == highArcBest) {
                  best = highArcBest;
                  refinement = highArcRefinement;
                  selectedArc = "none";
               }
            }
         }

         if (best == null) {
            return this.noCandidateResult(snapshot, fallbackReason, stats, startedNanos);
         }
         if (horizon >= maxHorizon || isAcceptableShot(snapshot, best) && !nearHorizon(best, horizon)) {
            break;
         }

         int expanded = Math.min(maxHorizon, Math.max(horizon + HORIZON_MARGIN_TICKS, horizon * 2));
         if (expanded == horizon) {
            break;
         }
         horizon = expanded;
         ++stats.horizonExpansions;
      }

      return this.buildResult(snapshot, projectileModel, initial, best, refinement.iterations(), refinement.finalStepDeg(), selectedArc, fallbackReason, stats, startedNanos);
   }

   private List<Candidate> searchCandidates(EvaluationContext context, InitialGuess initial) {
      Candidate best = null;
      List<Candidate> shortlist = new ArrayList<>(17);
      for(SearchPass pass : searchPasses()) {
         double centerYaw = best == null ? initial.yawDeg : best.yawDeg;
         double centerPitch = best == null ? initial.pitchDeg : best.pitchDeg;
         for(double yawOffset = -pass.yawRange; yawOffset <= pass.yawRange + 1.0E-9; yawOffset += pass.step) {
            for(double pitchOffset = -pass.pitchDownRange; pitchOffset <= pass.pitchUpRange + 1.0E-9; pitchOffset += pass.step) {
               Candidate candidate = this.evaluate(context, TargetingMath.wrap180(centerYaw + yawOffset), clampPitch(centerPitch + pitchOffset), ObstructionResult.clearPath());
               if (candidate == null) {
                  continue;
               }
               addToShortlist(shortlist, candidate);
               if (best == null || candidate.score < best.score) {
                  best = candidate;
               }
            }
         }
      }
      return shortlist;
   }

   private TargetingResult buildResult(TargetingSnapshot snapshot, ProjectileModel projectileModel, InitialGuess initial, Candidate candidate, int refinementIterations, double finalStepDeg, String selectedArc, String fallbackReason, SolveStats stats, long startedNanos) {
      Candidate best = this.materializeTrajectory(snapshot, projectileModel, candidate);
      long elapsedMicros = (System.nanoTime() - startedNanos) / 1_000L;
      List<String> debug = new ArrayList<>();
      debug.add("solver=simulated_moving_v4");
      debug.add("path=" + (stats.warmStart ? "warm" : "cold"));
      debug.add("initialInterceptTicks=" + initial.interceptTicks);
      debug.add("preferHighArc=" + snapshot.preferHighArc());
      debug.add("selectedHighArc=" + "high".equals(selectedArc));
      debug.add("selectedArc=" + selectedArc);
      debug.add("arcFallbackReason=" + fallbackReason);
      debug.add("pitchConstraint=" + snapshot.pitchConstraint().summary());
      debug.add("pitchConstraintRejections=" + stats.pitchConstraintRejections);
      debug.add("range=" + snapshot.muzzlePosition().distanceTo(snapshot.targetPosition()));
      debug.add("candidateEvaluations=" + stats.candidateEvaluations);
      debug.add("simulatedTicks=" + stats.simulatedTicks);
      debug.add("horizonExpansions=" + stats.horizonExpansions);
      debug.add("solveMicros=" + elapsedMicros);
      debug.add("refinementIterations=" + refinementIterations);
      debug.add("refinementFinalStepDeg=" + finalStepDeg);
      debug.add("bestTick=" + best.flightTick);
      debug.add("bestTime=" + best.flightTime);
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
         LOGGER.debug("TargetingComputer simulated solve yaw={} pitch={} tick={} miss={} score={} confidence={} candidates={} ticks={} micros={} obstruction={}", best.yawDeg, best.pitchDeg, best.flightTick, best.missDistance, best.score, best.confidence, stats.candidateEvaluations, stats.simulatedTicks, elapsedMicros, best.obstruction.blocked() ? "blocked" : "clear");
      }

      AimSolution solution = new AimSolution(best.yawDeg, best.pitchDeg, TargetingMath.directionFromYawPitch(best.yawDeg, best.pitchDeg), best.predictedTargetPosition, best.flightTick, best.missDistance);
      TargetingDebugInfo debugData = debugInfo(snapshot, best);
      return !isAcceptableShot(snapshot, best)
              ? TargetingResult.noShot(best.obstruction.blocked() ? "obstructed" : "low quality shot", debug, debugData)
              : TargetingResult.shot(solution, best.confidence, debug, debugData);
   }

   private TargetingResult noCandidateResult(TargetingSnapshot snapshot, String fallbackReason, SolveStats stats, long startedNanos) {
      long elapsedMicros = (System.nanoTime() - startedNanos) / 1_000L;
      return TargetingResult.noShot("no reachable simulated candidate", List.of(
              "solver=simulated_moving_v4",
              "preferHighArc=" + snapshot.preferHighArc(),
              "selectedArc=none",
              "arcFallbackReason=" + fallbackReason,
              "pitchConstraint=" + snapshot.pitchConstraint().summary(),
              "pitchConstraintRejections=" + stats.pitchConstraintRejections,
              "candidateEvaluations=" + stats.candidateEvaluations,
              "solveMicros=" + elapsedMicros
      ));
   }

   private static String highArcFailureReason(List<Candidate> highArcCandidates, @Nullable Candidate highArcBest, boolean rejectedByPitchConstraint) {
      if (highArcCandidates.isEmpty()) {
         return rejectedByPitchConstraint ? "high_arc_outside_pitch_limits" : "high_arc_unavailable";
      }
      if (highArcBest != null && highArcBest.obstruction.blocked()) {
         return "high_arc_obstructed";
      }
      return "high_arc_below_quality";
   }

   @Nullable
   private static Candidate betterDiagnosticCandidate(TargetingSnapshot snapshot, @Nullable Candidate first, @Nullable Candidate second) {
      if (first == null) {
         return second;
      }
      if (second == null) {
         return first;
      }
      return score(snapshot, first) < score(snapshot, second) ? first : second;
   }

   private static int initialHorizon(TargetingSnapshot snapshot, double interceptTicks) {
      double fallback = snapshot.muzzlePosition().distanceTo(snapshot.targetPosition()) / Math.max(1.0E-6, snapshot.projectileSpeed());
      double base = Double.isFinite(interceptTicks) && interceptTicks > 0.0 ? interceptTicks : fallback;
      return Math.max(1, Math.min(snapshot.maxFlightTicks(), Math.max(40, (int)Math.ceil(base) + HORIZON_MARGIN_TICKS)));
   }

   private static boolean nearHorizon(Candidate candidate, int horizon) {
      return candidate != null && candidate.flightTime >= Math.max(0, horizon - HORIZON_EDGE_TICKS);
   }

   private InitialGuess initialGuess(TargetingSnapshot snapshot, ProjectileModel projectileModel) {
      double interceptTicks = this.estimateInterceptTicks(snapshot, projectileModel);
      Vec3 aimPoint = this.predictTargetPositionTrusted(snapshot, interceptTicks);
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

   @Nullable
   private Candidate evaluate(EvaluationContext context, double yawDeg, double pitchDeg, ObstructionResult obstruction) {
      TargetingSnapshot snapshot = context.snapshot;
      ProjectileModel model = context.projectileModel;
      Vec3 direction = TargetingMath.directionFromYawPitch(yawDeg, pitchDeg);
      if (!snapshot.pitchConstraint().allows(direction)) {
         ++context.stats.pitchConstraintRejections;
         return null;
      }
      Vec3 inherited = snapshot.inheritedVelocity();
      double px = snapshot.muzzlePosition().x;
      double py = snapshot.muzzlePosition().y;
      double pz = snapshot.muzzlePosition().z;
      double vx = inherited.x + direction.x * model.muzzleSpeed();
      double vy = inherited.y + direction.y * model.muzzleSpeed();
      double vz = inherited.z + direction.z * model.muzzleSpeed();

      double bestMissSqr = distanceToTargetSqr(context, 0.0, px, py, pz);
      double bestTime = 0.0;
      double bestPx = px;
      double bestPy = py;
      double bestPz = pz;
      double bestFromX = px;
      double bestFromY = py;
      double bestFromZ = pz;
      double bestToX = px;
      double bestToY = py;
      double bestToZ = pz;
      int bestSegmentTick = 0;

      ++context.stats.candidateEvaluations;
      for(int tick = 0; tick < context.horizonTicks; ++tick) {
         if ((tick & 31) == 0 && Thread.currentThread().isInterrupted()) {
            throw new CancellationException("targeting solve superseded");
         }

         double nextPx;
         double nextPy;
         double nextPz;
         if (model.cbcPhysics()) {
            double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
            double dragForce = 0.0;
            if (speed > 1.0E-8 && model.drag() > 0.0) {
               double density = Math.max(0.0, model.dragDensity());
               if (snapshot.level() != null) {
                  density += rbasamoyai.createbigcannons.munitions.config.FluidDragHandler.getFluidDrag(snapshot.level().getFluidState(BlockPos.containing(px, py, pz)));
               }
               dragForce = model.drag() * density * speed;
               if (model.quadraticDrag()) {
                  dragForce *= speed;
               }
               dragForce = Math.min(dragForce, speed);
            }
            double scale = speed > 1.0E-8 ? dragForce / speed : 0.0;
            double ax = -vx * scale;
            double ay = model.gravity() - vy * scale;
            double az = -vz * scale;
            nextPx = px + vx + ax * 0.5;
            nextPy = py + vy + ay * 0.5;
            nextPz = pz + vz + az * 0.5;
            vx += ax;
            vy += ay;
            vz += az;
         } else {
            nextPx = px + vx;
            nextPy = py + vy;
            nextPz = pz + vz;
            double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
            double dragForce = model.drag() * speed;
            if (model.quadraticDrag()) {
               dragForce *= speed;
            }
            dragForce = Math.min(dragForce, speed);
            if (dragForce > 0.0 && speed > 1.0E-8) {
               double scale = 1.0 - dragForce / speed;
               vx *= scale;
               vy *= scale;
               vz *= scale;
            }
            vy += model.gravity();
         }

         double segmentTime = closestLinearSegmentTime(context, tick, px, py, pz, nextPx, nextPy, nextPz);
         double alpha = segmentTime - tick;
         double samplePx = px + (nextPx - px) * alpha;
         double samplePy = py + (nextPy - py) * alpha;
         double samplePz = pz + (nextPz - pz) * alpha;
         double missSqr = distanceToTargetSqr(context, segmentTime, samplePx, samplePy, samplePz);
         if (missSqr < bestMissSqr) {
            bestMissSqr = missSqr;
            bestTime = segmentTime;
            bestPx = samplePx;
            bestPy = samplePy;
            bestPz = samplePz;
            bestFromX = px;
            bestFromY = py;
            bestFromZ = pz;
            bestToX = nextPx;
            bestToY = nextPy;
            bestToZ = nextPz;
            bestSegmentTick = tick;
         }

         px = nextPx;
         py = nextPy;
         pz = nextPz;
      }
      context.stats.simulatedTicks += context.horizonTicks;

      ClosestApproach refined = this.refineClosestSegment(context, bestSegmentTick, bestFromX, bestFromY, bestFromZ, bestToX, bestToY, bestToZ);
      if (refined.missDistance * refined.missDistance <= bestMissSqr + 1.0E-12) {
         bestTime = refined.tick;
         bestMissSqr = refined.missDistance * refined.missDistance;
         bestPx = refined.projectilePosition.x;
         bestPy = refined.projectilePosition.y;
         bestPz = refined.projectilePosition.z;
      }

      Vec3 bestTargetPos = this.predictTargetPositionTrusted(snapshot, bestTime);
      Vec3 bestProjectilePos = new Vec3(bestPx, bestPy, bestPz);
      Candidate candidate = new Candidate(yawDeg, pitchDeg, (int)Math.max(0.0, Math.round(bestTime)), bestTime, Math.sqrt(Math.max(0.0, bestMissSqr)), bestTargetPos, bestProjectilePos, null, obstruction, 0.0, 0.0);
      double confidence = this.confidence(snapshot, candidate);
      double score = score(snapshot, candidate, confidence);
      return new Candidate(candidate.yawDeg, candidate.pitchDeg, candidate.flightTick, candidate.flightTime, candidate.missDistance, candidate.predictedTargetPosition, candidate.closestProjectilePosition, candidate.trajectory, candidate.obstruction, confidence, score);
   }

   private static void addToShortlist(List<Candidate> shortlist, Candidate candidate) {
      shortlist.add(candidate);
      shortlist.sort(Comparator.comparingDouble(Candidate::score));
      if (shortlist.size() > OBSTRUCTION_SHORTLIST_SIZE) {
         shortlist.remove(shortlist.size() - 1);
      }

   }

   private Candidate chooseObstructionCheckedBest(EvaluationContext context, ObstructionChecker obstructionChecker, List<Candidate> shortlist) {
      if (shortlist.isEmpty()) {
         return null;
      }
      if (obstructionChecker == null || !obstructionChecker.isEnabled()) {
         return shortlist.stream().min(Comparator.comparingDouble(Candidate::score)).orElse(null);
      }

      Candidate best = null;
      for(Candidate candidate : shortlist) {
         Candidate materialized = this.materializeTrajectory(context.snapshot, context.projectileModel, candidate);
         ObstructionResult obstruction = obstructionChecker.check(context.snapshot.level(), materialized.trajectory, materialized.flightTick);
         Candidate checked = this.withObstruction(context.snapshot, materialized, obstruction);
         if (best == null || score(context.snapshot, checked) < score(context.snapshot, best)) {
            best = checked;
         }
      }

      return best;
   }

   private Candidate applyObstruction(TargetingSnapshot snapshot, ProjectileModel model, ObstructionChecker checker, Candidate candidate) {
      if (checker == null || !checker.isEnabled()) {
         return candidate;
      }
      Candidate materialized = this.materializeTrajectory(snapshot, model, candidate);
      ObstructionResult obstruction = checker.check(snapshot.level(), materialized.trajectory, materialized.flightTick);
      return this.withObstruction(snapshot, materialized, obstruction);
   }

   private List<Candidate> searchHighArcCandidates(EvaluationContext context, InitialGuess initial) {
      List<Candidate> shortlist = new ArrayList<>(17);
      Candidate best = null;
      double highArcFloor = highArcPitchFloor(initial.pitchDeg);

      for(SearchPass pass : searchPasses()) {
         double centerYaw = best == null ? initial.yawDeg : best.yawDeg;
         double centerPitch = best == null ? Math.max(45.0, initial.pitchDeg + 25.0) : best.pitchDeg;

         for(double yawOffset = -pass.yawRange; yawOffset <= pass.yawRange + 1.0E-9; yawOffset += pass.step) {
            for(double pitchOffset = -pass.pitchDownRange; pitchOffset <= pass.pitchUpRange + 1.0E-9; pitchOffset += pass.step) {
               double yaw = TargetingMath.wrap180(centerYaw + yawOffset);
               double pitch = clampPitch(centerPitch + pitchOffset);
               if (pitch < highArcFloor) {
                  continue;
               }

               Candidate candidate = this.evaluate(context, yaw, pitch, ObstructionResult.clearPath());
               if (candidate == null) {
                  continue;
               }
               addToShortlist(shortlist, candidate);
               if (best == null || score(context.snapshot, candidate) < score(context.snapshot, best)) {
                  best = candidate;
               }
            }
         }
      }

      return shortlist;
   }

   private static double highArcPitchFloor(double initialPitchDeg) {
      return Math.max(20.0, Math.min(80.0, initialPitchDeg + 10.0));
   }

   private RefinementSummary refineShortlist(EvaluationContext context, List<Candidate> shortlist) {
      if (shortlist.isEmpty()) {
         return new RefinementSummary(null, 0, 0.0);
      }

      shortlist.sort(Comparator.comparingDouble(Candidate::score));
      List<Candidate> refined = new ArrayList<>(Math.min(REFINEMENT_SHORTLIST_SIZE, shortlist.size()));
      Candidate best = null;
      int totalIterations = 0;
      double finalStepDeg = 0.0;

      int count = Math.min(REFINEMENT_SHORTLIST_SIZE, shortlist.size());
      for (int i = 0; i < count; ++i) {
         RefinementResult result = this.refineCandidate(context, shortlist.get(i), MAX_REFINEMENT_ITERATIONS);
         totalIterations += result.iterations();
         finalStepDeg = Math.max(finalStepDeg, result.finalStepDeg());
         refined.add(result.candidate());
         if (best == null || score(context.snapshot, result.candidate()) < score(context.snapshot, best)) {
            best = result.candidate();
         }
      }

      shortlist.clear();
      for (Candidate candidate : refined) {
         addToShortlist(shortlist, candidate);
      }

      return new RefinementSummary(best, totalIterations, finalStepDeg);
   }

   private RefinementResult refineCandidate(EvaluationContext context, Candidate seed, int maxIterations) {
      TargetingSnapshot snapshot = context.snapshot;
      double range = Math.max(1.0, snapshot.muzzlePosition().distanceTo(seed.predictedTargetPosition));
      double targetStep = refinementTargetStepDeg(range);
      double step = Math.max(targetStep, Math.min(MAX_REFINEMENT_STEP_DEG, angularStepForBlocks(range, 4.0)));
      Candidate best = seed;
      int iterations = 0;

      while (step > targetStep && iterations < maxIterations) {
         Candidate iterationBest = best;
         for (int yawIndex = -1; yawIndex <= 1; ++yawIndex) {
            for (int pitchIndex = -1; pitchIndex <= 1; ++pitchIndex) {
               if (yawIndex == 0 && pitchIndex == 0) {
                  continue;
               }

               double yaw = TargetingMath.wrap180(best.yawDeg + (double)yawIndex * step);
               double pitch = clampPitch(best.pitchDeg + (double)pitchIndex * step);
               Candidate candidate = this.evaluate(context, yaw, pitch, ObstructionResult.clearPath());
               if (candidate != null && score(snapshot, candidate) < score(snapshot, iterationBest)) {
                  iterationBest = candidate;
               }
            }
         }

         ++iterations;
         if (iterationBest != best) {
            best = iterationBest;
         } else {
            step *= 0.5;
         }
      }

      return new RefinementResult(best, iterations, step);
   }

   private static double refinementTargetStepDeg(double rangeBlocks) {
      return Math.max(MIN_REFINEMENT_STEP_DEG, angularStepForBlocks(rangeBlocks, REFINEMENT_TARGET_BLOCKS));
   }

   private static double angularStepForBlocks(double rangeBlocks, double blocks) {
      double range = Math.max(1.0, rangeBlocks);
      return Math.toDegrees(Math.atan(Math.max(0.001, blocks) / range));
   }

   private Candidate materializeTrajectory(TargetingSnapshot snapshot, ProjectileModel model, Candidate candidate) {
      if (candidate == null || candidate.trajectory != null) {
         return candidate;
      }
      Vec3 direction = TargetingMath.directionFromYawPitch(candidate.yawDeg, candidate.pitchDeg);
      int materializedTicks = Math.max(1, Math.min(snapshot.maxFlightTicks(), candidate.flightTick + 1));
      ProjectileSimulator.SimulationResult trajectory = this.projectileSimulator.simulate(snapshot.muzzlePosition(), direction, snapshot.inheritedVelocity(), model, materializedTicks, snapshot.level());
      return new Candidate(candidate.yawDeg, candidate.pitchDeg, candidate.flightTick, candidate.flightTime, candidate.missDistance, candidate.predictedTargetPosition, candidate.closestProjectilePosition, trajectory, candidate.obstruction, candidate.confidence, candidate.score);
   }

   private Candidate withObstruction(TargetingSnapshot snapshot, Candidate candidate, ObstructionResult obstruction) {
      Candidate unscored = new Candidate(candidate.yawDeg, candidate.pitchDeg, candidate.flightTick, candidate.flightTime, candidate.missDistance, candidate.predictedTargetPosition, candidate.closestProjectilePosition, candidate.trajectory, obstruction, 0.0, 0.0);
      double confidence = this.confidence(snapshot, unscored);
      return new Candidate(unscored.yawDeg, unscored.pitchDeg, unscored.flightTick, unscored.flightTime, unscored.missDistance, unscored.predictedTargetPosition, unscored.closestProjectilePosition, unscored.trajectory, obstruction, confidence, score(snapshot, unscored, confidence));
   }

   private ClosestApproach refineClosestSegment(EvaluationContext context, int startTick, double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
      TargetingSnapshot snapshot = context.snapshot;
      double left = startTick;
      double right = startTick + 1.0;
      for(int i = 0; i < FRACTIONAL_DISTANCE_REFINEMENT_STEPS; ++i) {
         double m1 = left + (right - left) / 3.0;
         double m2 = right - (right - left) / 3.0;
         double a1 = m1 - startTick;
         double a2 = m2 - startTick;
         double d1 = distanceToTargetSqr(context, m1, fromX + (toX - fromX) * a1, fromY + (toY - fromY) * a1, fromZ + (toZ - fromZ) * a1);
         double d2 = distanceToTargetSqr(context, m2, fromX + (toX - fromX) * a2, fromY + (toY - fromY) * a2, fromZ + (toZ - fromZ) * a2);
         if (d1 <= d2) {
            right = m2;
         } else {
            left = m1;
         }
      }
      double tick = (left + right) * 0.5;
      double alpha = tick - startTick;
      Vec3 projectile = new Vec3(fromX + (toX - fromX) * alpha, fromY + (toY - fromY) * alpha, fromZ + (toZ - fromZ) * alpha);
      Vec3 target = this.predictTargetPositionTrusted(snapshot, tick);
      return new ClosestApproach(tick, Math.sqrt(distanceToTargetSqr(context, tick, projectile.x, projectile.y, projectile.z)), target, projectile);
   }

   private static double closestLinearSegmentTime(EvaluationContext context, int tick, double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
      double bestAlpha = closestLinearAlpha(context, tick, fromX, fromY, fromZ, toX, toY, toZ, false);
      double bestDistance = distanceToTargetSqr(context, tick + bestAlpha, fromX + (toX - fromX) * bestAlpha, fromY + (toY - fromY) * bestAlpha, fromZ + (toZ - fromZ) * bestAlpha);
      if (context.hasAabb) {
         double upperAlpha = closestLinearAlpha(context, tick, fromX, fromY, fromZ, toX, toY, toZ, true);
         double upperDistance = distanceToTargetSqr(context, tick + upperAlpha, fromX + (toX - fromX) * upperAlpha, fromY + (toY - fromY) * upperAlpha, fromZ + (toZ - fromZ) * upperAlpha);
         if (upperDistance < bestDistance) {
            bestAlpha = upperAlpha;
         }
      }
      return tick + bestAlpha;
   }

   private static double closestLinearAlpha(EvaluationContext context, int tick, double fromX, double fromY, double fromZ, double toX, double toY, double toZ, boolean upper) {
      double nextTick = tick + 1.0;
      double target0X = context.targetX(tick);
      double target0Y = context.targetY(tick, upper);
      double target0Z = context.targetZ(tick);
      double target1X = context.targetX(nextTick);
      double target1Y = context.targetY(nextTick, upper);
      double target1Z = context.targetZ(nextTick);
      double rx = fromX - target0X;
      double ry = fromY - target0Y;
      double rz = fromZ - target0Z;
      double dx = (toX - fromX) - (target1X - target0X);
      double dy = (toY - fromY) - (target1Y - target0Y);
      double dz = (toZ - fromZ) - (target1Z - target0Z);
      double denominator = dx * dx + dy * dy + dz * dz;
      return denominator <= 1.0E-12 ? 0.0 : Math.max(0.0, Math.min(1.0, -(rx * dx + ry * dy + rz * dz) / denominator));
   }

   private static double distanceToTargetSqr(EvaluationContext context, double tick, double px, double py, double pz) {
      double dx = px - context.targetX(tick);
      double dy = py - context.targetY(tick, false);
      double dz = pz - context.targetZ(tick);
      double best = dx * dx + dy * dy + dz * dz;
      if (context.hasAabb) {
         dy = py - context.targetY(tick, true);
         best = Math.min(best, dx * dx + dy * dy + dz * dz);
      }
      return best;
   }

   private Vec3 predictTargetPositionTrusted(TargetingSnapshot snapshot, double tick) {
      return this.targetPredictor.predictPosition(snapshot, tick, accelerationTrust(snapshot));
   }

   private static double distanceToTarget(Vec3 projectilePosition, Vec3 targetPosition, @Nullable AABB targetAabb) {
      if (targetAabb == null) {
         return projectilePosition.distanceTo(targetPosition);
      }

      Vec3 center = targetAabb.getCenter();
      Vec3 upperCenter = new Vec3(center.x, targetAabb.minY + targetAabb.getYsize() * 0.75, center.z);
      return Math.min(projectilePosition.distanceTo(center), projectilePosition.distanceTo(upperCenter));
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
         double timePenalty = Math.max((double)0.0F, candidate.flightTime) / (double)120.0F;
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
         double timeFactor = (double)1.0F / ((double)1.0F + candidate.flightTime / (double)120.0F);
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
         if (candidate.confidence < MIN_ACCEPTABLE_CONFIDENCE || candidate.missDistance > acceptableMissDistance(snapshot)) {
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
      double directTolerance = directTargetHitTolerance(snapshot);
      double range = snapshot == null ? 0.0 : snapshot.muzzlePosition().distanceTo(snapshot.targetPosition());
      if (!Double.isFinite(range) || range <= 0.0) {
         return directTolerance;
      }

      double longRangeTolerance = LONG_RANGE_ACCEPTANCE_MISS_BLOCKS * range / LONG_RANGE_ACCEPTANCE_DISTANCE_BLOCKS;
      return Math.max(directTolerance, Math.min(LONG_RANGE_ACCEPTANCE_MISS_BLOCKS, longRangeTolerance));
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
      if (snapshot.targetSublevelId() == null || !candidate.obstruction.blocked() || !Mods.SABLE.isLoaded()) {
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
         double blockedTick = Math.max(0, candidate.obstruction.blockedTick());
         Vec3 targetAtBlock = predictTargetPosition(snapshot, blockedTick);
         AABB targetBoxAtBlock = predictTargetAabb(snapshot, blockedTick);
         double distance = distanceToTarget(candidate.obstruction.blockedPosition(), targetAtBlock, targetBoxAtBlock);
         return distance <= acceptableMissDistance(snapshot);
      } else {
         return false;
      }
   }

   private static double directTargetHitTolerance(TargetingSnapshot snapshot) {
      double speedMargin = snapshot.targetVelocity().length() * (double)2.0F;
      if (snapshot.targetAabb() == null) {
         return Math.max((double)0.75F, Math.min((double)2.5F, (double)0.75F + speedMargin));
      }

      AABB box = snapshot.targetAabb();
      double sizeMargin = Math.max((double)0.25F, Math.min((double)1.5F, Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())) * (double)0.25F));
      return Math.max((double)0.35F, Math.min((double)3.0F, sizeMargin + speedMargin));
   }

   private static Vec3 predictTargetPosition(TargetingSnapshot snapshot, double tick) {
      double safeTick = Double.isFinite(tick) ? Math.max((double)0.0F, tick) : (double)0.0F;
      Vec3 acceleration = clampAcceleration(finiteOrZero(snapshot.targetAcceleration())).scale(accelerationTrust(snapshot));
      return snapshot.targetPosition().add(finiteOrZero(snapshot.targetVelocity()).scale(safeTick)).add(acceleration.scale((double)0.5F * safeTick * safeTick));
   }

   @Nullable
   private static AABB predictTargetAabb(TargetingSnapshot snapshot, double tick) {
      return snapshot.targetAabb() == null ? null : snapshot.targetAabb().move(predictTargetPosition(snapshot, tick).subtract(snapshot.targetPosition()));
   }

   private static double accelerationTrust(TargetingSnapshot snapshot) {
      if (snapshot == null) {
         return (double)0.0F;
      } else if (snapshot.targetMotionClass() == TargetMotionClass.ERRATIC) {
         return 0.25;
      } else if (snapshot.targetMotionClass() == TargetMotionClass.UNKNOWN) {
         return 0.5;
      } else {
         return (double)1.0F;
      }
   }

   private static Vec3 finiteOrZero(Vec3 vec) {
      return vec != null && Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z) ? vec : Vec3.ZERO;
   }

   private static Vec3 clampAcceleration(Vec3 acceleration) {
      double max = (double)0.25F;
      double lenSqr = acceleration.lengthSqr();
      return !(lenSqr <= max * max) && !(lenSqr < 1.0E-12) ? acceleration.normalize().scale(max) : acceleration;
   }

   private static double distanceFromObstructionToTarget(Candidate candidate) {
      Vec3 blockedPosition = candidate.obstruction.blockedPosition();
      return blockedPosition != null && candidate.predictedTargetPosition != null ? blockedPosition.distanceTo(candidate.predictedTargetPosition) : Double.POSITIVE_INFINITY;
   }

   private static TargetingDebugInfo debugInfo(TargetingSnapshot snapshot, Candidate candidate) {
      return new TargetingDebugInfo(snapshot.targetPosition(), candidate.predictedTargetPosition, candidate.closestProjectilePosition, snapshot.targetVelocity(), snapshot.targetAcceleration(), snapshot.inheritedVelocity(), candidate.trajectory.trajectory().samples(), candidate.flightTick, candidate.missDistance, candidate.yawDeg, candidate.pitchDeg, snapshot.currentYawDeg(), snapshot.currentPitchDeg(), candidate.confidence, candidate.obstruction);
   }

   private static List<SearchPass> searchPasses() {
      return List.of(new SearchPass((double)24.0F, (double)20.0F, (double)55.0F, (double)4.0F), new SearchPass((double)6.0F, (double)8.0F, (double)8.0F, (double)1.0F), new SearchPass((double)1.5F, (double)1.5F, (double)1.5F, (double)0.25F), new SearchPass(0.3, 0.3, 0.3, 0.1));
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

   private static record ClosestApproach(double tick, double missDistance, Vec3 targetPosition, Vec3 projectilePosition) {
   }

   private static record Candidate(double yawDeg, double pitchDeg, int flightTick, double flightTime, double missDistance, Vec3 predictedTargetPosition, Vec3 closestProjectilePosition, @Nullable ProjectileSimulator.SimulationResult trajectory, ObstructionResult obstruction, double confidence, double score) {
   }

   private static record RefinementResult(Candidate candidate, int iterations, double finalStepDeg) {
   }

   private static record RefinementSummary(Candidate best, int iterations, double finalStepDeg) {
   }

   private static final class SolveStats {
      int candidateEvaluations;
      long simulatedTicks;
      int horizonExpansions;
      int pitchConstraintRejections;
      boolean warmStart;
   }

   private static final class EvaluationContext {
      final TargetingSnapshot snapshot;
      final ProjectileModel projectileModel;
      final int horizonTicks;
      final SolveStats stats;
      final boolean hasAabb;
      final double targetBaseX;
      final double targetCenterY;
      final double targetUpperY;
      final double targetBaseZ;
      final double velocityX;
      final double velocityY;
      final double velocityZ;
      final double accelerationX;
      final double accelerationY;
      final double accelerationZ;

      EvaluationContext(TargetingSnapshot snapshot, ProjectileModel projectileModel, int horizonTicks, SolveStats stats) {
         this.snapshot = snapshot;
         this.projectileModel = projectileModel;
         this.horizonTicks = horizonTicks;
         this.stats = stats;
         AABB box = snapshot.targetAabb();
         this.hasAabb = box != null;
         this.targetBaseX = box == null ? snapshot.targetPosition().x : (box.minX + box.maxX) * 0.5;
         this.targetCenterY = box == null ? snapshot.targetPosition().y : (box.minY + box.maxY) * 0.5;
         this.targetUpperY = box == null ? this.targetCenterY : box.minY + box.getYsize() * 0.75;
         this.targetBaseZ = box == null ? snapshot.targetPosition().z : (box.minZ + box.maxZ) * 0.5;
         this.velocityX = snapshot.targetVelocity().x;
         this.velocityY = snapshot.targetVelocity().y;
         this.velocityZ = snapshot.targetVelocity().z;
         Vec3 acceleration = clampAcceleration(finiteOrZero(snapshot.targetAcceleration()));
         double trust = accelerationTrust(snapshot);
         this.accelerationX = acceleration.x * trust;
         this.accelerationY = acceleration.y * trust;
         this.accelerationZ = acceleration.z * trust;
      }

      double targetX(double tick) {
         return this.targetBaseX + this.velocityX * tick + this.accelerationX * 0.5 * tick * tick;
      }

      double targetY(double tick, boolean upper) {
         return (upper ? this.targetUpperY : this.targetCenterY) + this.velocityY * tick + this.accelerationY * 0.5 * tick * tick;
      }

      double targetZ(double tick) {
         return this.targetBaseZ + this.velocityZ * tick + this.accelerationZ * 0.5 * tick * tick;
      }
   }
}
