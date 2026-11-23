package com.happysg.radar.targeting;

public interface AimSolver {
   TargetingResult solve(TargetingSnapshot snapshot, ProjectileModel projectileModel, ObstructionChecker obstructionChecker);
}
