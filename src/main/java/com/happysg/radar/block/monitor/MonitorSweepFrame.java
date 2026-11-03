package com.happysg.radar.block.monitor;

import com.happysg.radar.api.monitor.MonitorRadarSnapshot;
import com.happysg.radar.compat.Mods;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public final class MonitorSweepFrame {
    private MonitorSweepFrame() {}

    public static Pose3dc renderPose(SubLevelAccess frame, float partialTicks) {
        return frame instanceof ClientSubLevelAccess client ? client.renderPose(partialTicks) : frame.logicalPose();
    }

    private static SubLevelAccess sourceFrame(MonitorRadarSnapshot radar) {
        if (!Mods.SABLE.isLoaded() || radar.sourceSublevelId() == null || Minecraft.getInstance().level == null) {
            return null;
        }
        var container = SubLevelContainer.getContainer(Minecraft.getInstance().level);
        return container == null ? null : container.getSubLevel(radar.sourceSublevelId());
    }

    public static Vec3 worldCenter(MonitorRadarSnapshot radar, float partialTicks) {
        SubLevelAccess frame = sourceFrame(radar);
        if (frame == null) return radar.center();
        Vector3d center = renderPose(frame, partialTicks).transformPosition(new Vector3d(radar.radarPos().getX() + 0.5, radar.radarPos().getY() + 0.5, radar.radarPos().getZ() + 0.5));
        return new Vec3(center.x(), center.y(), center.z());
    }

    public static float screenAngle(MonitorRadarSnapshot radar, MonitorProjection projection, float partialTicks) {
        var level = Minecraft.getInstance().level;
        long elapsed = level == null ? 0L : level.getGameTime() - radar.angleSnapshotGameTime();
        double dt = Math.max(0L, Math.min(8L, elapsed)) + partialTicks;
        float speed = Float.isFinite(radar.angularSpeedDegreesPerTick()) ? radar.angularSpeedDegreesPerTick() : 0.0f;

        SubLevelAccess frame = sourceFrame(radar);
        if (frame != null && Float.isFinite(radar.localAngleDegrees())) {
            double localAngle = Math.toRadians(radar.localAngleDegrees() + speed * dt);
            Vector3d world = renderPose(frame, partialTicks).transformNormal(new Vector3d(Math.sin(localAngle), 0.0, Math.cos(localAngle)));
            MonitorProjection.DisplayPoint point = projection.projectWorldVector(new Vec3(world.x(), world.y(), world.z()));
            double lengthSqr = point.xOffset() * point.xOffset() + point.zOffset() * point.zOffset();
            if (lengthSqr > 1.0E-10) {
                return normalizeDegrees((float)Math.toDegrees(Math.atan2(point.xOffset(), -point.zOffset())));
            }
        }

        return projection.projectWorldAngle(radar.globalAngleDegrees() + (float)(speed * dt));
    }

    private static float normalizeDegrees(float angle) {
        angle %= 360.0f;
        return angle < 0.0f ? angle + 360.0f : angle;
    }
}
