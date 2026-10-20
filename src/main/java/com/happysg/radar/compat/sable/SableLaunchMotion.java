package com.happysg.radar.compat.sable;

import com.happysg.radar.targeting.RigidBodyLaunchMath;
import com.happysg.radar.compat.cbc.AccelerationTracker;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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

            RigidBodyLaunchMath.Motion measured = RigidBodyLaunchMath.fromVelocityProbes(origin, originVelocity, velocityX, velocityY, velocityZ, SAMPLE_DISTANCE);

            if (measured == null) {
                return null;
            }

            String key = ship.getUniqueId() + "|" + level.dimension().location() + "|rigid_motion|";

            UUID linearId = UUID.nameUUIDFromBytes((key + "linear").getBytes(StandardCharsets.UTF_8));
            UUID angularId = UUID.nameUUIDFromBytes((key + "angular").getBytes(StandardCharsets.UTF_8));

            long tick = level.getGameTime();
            Vec3 linearAcceleration = AccelerationTracker.getAccelerationPerTick2(linearId, measured.linearVelocity(), tick);

            Vec3 angularAcceleration = AccelerationTracker.getAccelerationPerTick2(angularId, measured.angularVelocity(), tick);
            double maxAngularAcceleration = 0.02;

            if (angularAcceleration.lengthSqr() > maxAngularAcceleration * maxAngularAcceleration) {
                angularAcceleration = angularAcceleration.normalize().scale(maxAngularAcceleration);
            }

            return new RigidBodyLaunchMath.Motion(
                    measured.origin(),
                    measured.linearVelocity(),
                    measured.angularVelocity(),
                    linearAcceleration,
                    angularAcceleration
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