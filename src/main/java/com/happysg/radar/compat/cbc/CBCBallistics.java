package com.happysg.radar.compat.cbc;


import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;


/**
 * Lightweight CBC-ish ballistics approximations.
 * Runs in tick units (Minecraft projectile update cadence).
 *
 * Goal: provide TOF + drop to feed into existing lead equations.
 */
public final class CBCBallistics {


    private CBCBallistics() {}


    public record Result(int tofTicks, double dropY, double horizontalTraveled) {}


    /**
     * Estimate time-of-flight (ticks) and drop for a projectile traveling toward a point.
     *
     * @param props CBC ballistic props (gravity, drag, quadratic flag)
     * @param muzzlePos world-space muzzle
     * @param aimDir normalized direction you currently aim (from your existing equations)
     * @param muzzleSpeedBlocksPerTick initial speed in blocks/tick (IMPORTANT: match your existing units)
     * @param targetPos world-space target position you want to reach
     * @param maxTicks cap to avoid heavy loops (e.g. 200-400)
     */
    public static Result estimateTofAndDrop(BallisticPropertiesComponent props,
                                            Vec3 muzzlePos,
                                            Vec3 aimDir,
                                            double muzzleSpeedBlocksPerTick,
                                            Vec3 targetPos,
                                            int maxTicks) {


// Horizontal range we need to cover (XZ)
        double dx = targetPos.x - muzzlePos.x;
        double dz = targetPos.z - muzzlePos.z;
        double targetRangeXZ = Math.sqrt(dx * dx + dz * dz);
        if (targetRangeXZ < 1e-6) return new Result(0, 0.0, 0.0);


        Vec3 vel = aimDir.scale(muzzleSpeedBlocksPerTick);
        double gravity = props.gravity(); // CBC defaults negative (e.g. -0.05)
        double drag = props.drag();
        boolean quad = props.isQuadraticDrag();


        double x = 0.0;
        double y = 0.0;
        double z = 0.0;


        double traveledXZ = 0.0;
        double lastXZ = 0.0;


        int t = 0;
        for (; t < maxTicks; t++) {
// integrate position
            x += vel.x;
            y += vel.y;
            z += vel.z;


// compute horizontal traveled
            double nowXZ = Math.sqrt(x * x + z * z);
            traveledXZ = nowXZ;


// stop once we've reached the desired horizontal range
            if (nowXZ >= targetRangeXZ) break;
            lastXZ = nowXZ;


// gravity
            vel = vel.add(0.0, gravity, 0.0);


// drag (approx; stable)
            vel = applyDrag(vel, drag, quad);
        }


// y here is vertical displacement (drop is negative if gravity is negative and aimDir isn't compensating)
// We return "drop" as -y if you want "how far it fell", but keeping sign is often useful.
// If you want "drop amount", use -y.
        return new Result(t, y, traveledXZ);
    }


    /** Drag approximation in tick space. */
    public static Vec3 applyDrag(Vec3 vel, double drag, boolean quadratic) {
        if (drag <= 0) return vel;


        if (!quadratic) {
// linear-ish damping
            double f = Mth.clamp(1.0 - drag, 0.0, 1.0);
            return vel.scale(f);
        } else {
// quadratic-ish: stronger effect at higher speeds
            double speed = vel.length();
            if (speed < 1e-9) return vel;
            double f = 1.0 / (1.0 + drag * speed);
            return vel.scale(f);
        }
    }
}