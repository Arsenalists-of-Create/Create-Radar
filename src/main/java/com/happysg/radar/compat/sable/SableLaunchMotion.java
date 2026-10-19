package com.happysg.radar.compat.sable;

import com.happysg.radar.targeting.RigidBodyLaunchMath;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class SableLaunchMotion {
    private static final double SAMPLE_DISTANCE = 8.0;
    private SableLaunchMotion() {}

    @Nullable
    public static RigidBodyLaunchMath.Motion capture(ServerLevel level, @Nullable SubLevelAccess ship) {
        if (level == null || ship == null) {
            return null;
        }

        try {
            Vector3dc center = ship.logicalPose().position();
            Vec3 origin = new Vec3(center.x(), center.y(), center.z());
            Vec3 originVelocity = velocityAt(level, ship, origin);
            Vec3 velocityX = velocityAt(level, ship, origin.add(SAMPLE_DISTANCE, 0, 0));
            Vec3 velocityY = velocityAt(level, ship, origin.add(0, SAMPLE_DISTANCE, 0));
            Vec3 velocityZ = velocityAt(level, ship, origin.add(0, 0, SAMPLE_DISTANCE));

            return RigidBodyLaunchMath.fromVelocityProbes(
                    origin,
                    originVelocity,
                    velocityX,
                    velocityY,
                    velocityZ,
                    SAMPLE_DISTANCE
            );

        } catch (RuntimeException e) {
            if ("Body has been removed".equals(e.getMessage())) {
                return null;
            }

            throw e;
        }
    }

    private static Vec3 velocityAt(ServerLevel level, SubLevelAccess ship, Vec3 worldPoint) {
        Vector3d local = ship.logicalPose().transformPositionInverse(new Vector3d(worldPoint.x, worldPoint.y, worldPoint.z));
        Vector3d velocity = SableCompanion.INSTANCE.getVelocity(level, ship, local, new Vector3d());
        return new Vec3(velocity.x, velocity.y, velocity.z).scale(1.0 / 20.0);
    }
}