
package com.happysg.radar.compat.sable;

import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.RadarTrackUtil;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

        List<SubLevelAccess> members = new ArrayList<>();
        for (UUID memberId : SableSilhouetteClientCache.getAssemblyMemberIds(silhouetteId)) {
            SubLevelAccess member = container.getSubLevel(memberId);

            if (member != null) {
                members.add(member);
            }
        }

        members.sort(Comparator.comparing(member -> member.getUniqueId().toString()));

        List<SubLevelSilhouette.ProjectionPart> parts = new ArrayList<>();
        for (SubLevelAccess member : members) {
            SubLevelSilhouette memberSilhouette = member.getUniqueId().equals(silhouetteId) ? silhouette : SableSilhouetteClientCache.getLatest(member.getUniqueId());

            if (memberSilhouette == null || memberSilhouette.isEmpty()) {
                continue;
            }

            Pose3dc pose = member instanceof ClientSubLevelAccess client ? client.renderPose(partialTicks) : member.logicalPose();
            parts.add(new SubLevelSilhouette.ProjectionPart(
                    memberSilhouette,
                    (localX, localY, localZ, destination) -> {
                        Vector3d transformed = pose.transformPosition(new Vector3d(localX, localY, localZ));
                        destination.set(transformed.x(), transformed.y(), transformed.z());
                    }
            ));
        }

        SubLevelSilhouette.ProjectedSilhouette projected =
                SableSilhouetteClientCache.getProjected(
                        silhouetteId,
                        revision,
                        gameTime,
                        projectionSettings,
                        () -> SubLevelSilhouette.projectCombined(parts, projectionSettings)
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
