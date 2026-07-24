package com.happysg.radar.compat.cbcmoreshells;

import com.happysg.radar.targeting.ObstructionChecker;
import com.happysg.radar.targeting.ProjectileModel;
import com.happysg.radar.targeting.ProjectileSimulator;
import com.happysg.radar.targeting.SimulatedAimSolver;
import com.happysg.radar.targeting.TargetPredictor;
import com.happysg.radar.targeting.TargetingComputer;
import com.happysg.radar.targeting.TargetingResult;
import com.happysg.radar.targeting.TargetingSnapshot;

/** Dedicated server-thread solver for CBCMS environment/guidance dynamics. */
public final class CBCMSAimSolver extends SimulatedAimSolver {
    public CBCMSAimSolver(ProjectileSimulator simulator, TargetPredictor predictor) {
        super(simulator, predictor);
    }

    @Override
    public TargetingResult solve(TargetingSnapshot snapshot, ProjectileModel projectileModel,
                                 ObstructionChecker obstructionChecker) {
        if (!(projectileModel instanceof CBCMSProjectileModel)) {
            return TargetingResult.invalid("CBCMS solver requires a CBCMS projectile model");
        }
        return super.solve(snapshot, projectileModel, obstructionChecker);
    }

    public static TargetingComputer createComputer(ObstructionChecker obstructionChecker) {
        ProjectileSimulator simulator = new ProjectileSimulator();
        TargetPredictor predictor = new TargetPredictor();
        return new TargetingComputer(
                new CBCMSAimSolver(simulator, predictor),
                simulator,
                predictor,
                obstructionChecker
        );
    }
}
