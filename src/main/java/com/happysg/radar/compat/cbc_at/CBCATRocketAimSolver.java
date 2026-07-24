package com.happysg.radar.compat.cbc_at;

import com.happysg.radar.targeting.ObstructionChecker;
import com.happysg.radar.targeting.PitchConstraint;
import com.happysg.radar.targeting.ProjectileModel;
import com.happysg.radar.targeting.ProjectileSimulator;
import com.happysg.radar.targeting.SimulatedAimSolver;
import com.happysg.radar.targeting.TargetPredictor;
import com.happysg.radar.targeting.TargetingComputer;
import com.happysg.radar.targeting.TargetingResult;
import com.happysg.radar.targeting.TargetingSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Dedicated CBC:AT powered-rocket solver. It deliberately shares the mature
 * candidate/refinement implementation while requiring a powered projectile
 * model, so rockets cannot silently fall back to conventional ballistics.
 */
public final class CBCATRocketAimSolver extends SimulatedAimSolver {
    public CBCATRocketAimSolver(ProjectileSimulator simulator, TargetPredictor predictor) {
        super(simulator, predictor);
    }

    @Override
    public TargetingResult solve(TargetingSnapshot snapshot, ProjectileModel projectileModel, ObstructionChecker obstructionChecker) {
        if (!(projectileModel instanceof CBCATRocketProjectileModel)) {
            return TargetingResult.invalid("cbc_at rocket solver requires powered rocket model");
        }
        return super.solve(snapshot, projectileModel, obstructionChecker);
    }

    public static TargetingComputer createComputer(ObstructionChecker obstructionChecker) {
        ProjectileSimulator simulator = new ProjectileSimulator();
        TargetPredictor predictor = new TargetPredictor();
        return new TargetingComputer(
                new CBCATRocketAimSolver(simulator, predictor),
                simulator,
                predictor,
                obstructionChecker
        );
    }

    @Nullable
    public static TargetingResult solveStationary(
            ServerLevel level,
            Vec3 muzzlePosition,
            Vec3 targetPosition,
            CBCATRocketProjectileModel model,
            boolean preferHighArc,
            @Nullable Double preferredPitchDeg,
            @Nullable Double preferredYawDeg,
            PitchConstraint pitchConstraint
    ) {
        if (level == null || muzzlePosition == null || targetPosition == null || model == null) {
            return null;
        }
        TargetingSnapshot snapshot = TargetingSnapshot.builder(level)
                .muzzlePosition(muzzlePosition)
                .targetPosition(targetPosition)
                .projectileSpeed(model.muzzleSpeed())
                .gravity(model.gravity())
                .drag(model.drag())
                .quadraticDrag(model.quadraticDrag())
                .cbcPhysics(true)
                .dragDensity(model.dragDensity())
                .maxFlightTicks(model.maxFlightTicks())
                .preferredPitchDeg(preferredPitchDeg)
                .preferredYawDeg(preferredYawDeg)
                .pitchConstraint(pitchConstraint == null ? PitchConstraint.unconstrained() : pitchConstraint)
                .preferHighArc(preferHighArc)
                .build();
        TargetingComputer computer = createComputer(ObstructionChecker.NONE);
        return computer.solve(snapshot, model);
    }

    public static List<Double> solveStationaryPitchRoots(
            ServerLevel level,
            Vec3 muzzlePosition,
            Vec3 targetPosition,
            CBCATRocketProjectileModel model
    ) {
        List<Double> roots = new ArrayList<>(2);
        addPitch(roots, solveStationary(level, muzzlePosition, targetPosition, model, false, null, null, PitchConstraint.unconstrained()));
        addPitch(roots, solveStationary(level, muzzlePosition, targetPosition, model, true, null, null, PitchConstraint.unconstrained()));
        roots.sort(Comparator.naturalOrder());
        return List.copyOf(roots);
    }

    private static void addPitch(List<Double> roots, @Nullable TargetingResult result) {
        if (result == null || !result.valid() || !result.hasShot() || result.aimSolution() == null) {
            return;
        }
        double pitch = result.desiredPitchDeg();
        for (double existing : roots) {
            if (Math.abs(existing - pitch) < 0.05) {
                return;
            }
        }
        roots.add(pitch);
    }
}
