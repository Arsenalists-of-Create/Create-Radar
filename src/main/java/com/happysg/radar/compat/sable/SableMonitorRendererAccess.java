
package com.happysg.radar.compat.sable;

import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.RadarTrackUtil;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.UUID;

public final class SableMonitorRendererAccess {
    private SableMonitorRendererAccess() {}
    public record ProjectionResult(SubLevelSilhouette.ProjectedSilhouette projected, Vec3 reportedOffset) {}

    @Nullable
    public static ProjectionResult projectClientShip(
            RadarTrack track,
            UUID silhouetteId,
            int revision,
            long gameTime,
            SubLevelSilhouette silhouette,
            SubLevelSilhouette.ProjectionSettings projectionSettings,
            float partialTicks
    ) {
        if (Minecraft.getInstance().level == null) {
            return null;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(Minecraft.getInstance().level);
        SubLevelAccess subLevel = container == null ? null : container.getSubLevel(silhouetteId);

        if (subLevel == null) {
            return null;
        }

        Pose3dc pose = subLevel instanceof ClientSubLevelAccess clientSubLevel ? clientSubLevel.renderPose(partialTicks) : subLevel.logicalPose();

        SubLevelSilhouette.ProjectedSilhouette projected =
                SableSilhouetteClientCache.getProjected(
                        silhouetteId,
                        revision,
                        gameTime,
                        projectionSettings,
                        () -> {
                            Vector3d scratch = new Vector3d();
                            return silhouette.project(
                                    (localX, localY, localZ, destination) -> {
                                        scratch.set(localX, localY, localZ);
                                        Vector3d transformed = pose.transformPosition(scratch);
                                        destination.set(transformed.x(), transformed.y(), transformed.z());
                                    },
                                    projectionSettings
                            );
                        }
                );

        Vec3 reportedOffset = RadarTrackUtil.getReportedPositionOffset(track, subLevel);
        return new ProjectionResult(projected, reportedOffset);
    }

    public static boolean isMonitorOnShip(MonitorBlockEntity monitor) {
        return monitor.getShip() != null;
    }

    @Nullable
    public static UUID getMonitorShipId(MonitorBlockEntity monitor) {
        SubLevelAccess ship = monitor.getShip();
        return ship == null ? null : ship.getUniqueId();
    }

    public static double getMonitorShipYawRad(MonitorBlockEntity monitor) {
        SubLevelAccess ship = monitor.getShip();

        if (ship == null) {
            return 0.0D;
        }

        Vector3d forward = ship.logicalPose().transformNormal(new Vector3d(0, 0, 1));
        return Math.atan2(forward.x(), -forward.z());
    }
}
