
package com.happysg.radar.compat.sable;

import com.happysg.radar.block.radar.track.RadarTrackUtil;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

public final class SableAradTargetingAccess {
    private SableAradTargetingAccess() {}

    @Nullable
    public static Vec3 getSublevelWorldPosition(ServerLevel level, UUID sublevelId) {
        SubLevelAccess sublevel = resolveSublevel(level, sublevelId);

        if (sublevel == null) {
            return null;
        }

        return RadarTrackUtil.getPosition(sublevel);
    }

    @Nullable
    public static UUID getContainingSublevelId(ServerLevel level, BlockPos radarPos) {
        SubLevelAccess sublevel = SableCompanion.INSTANCE.getContaining(level, radarPos);

        return sublevel == null ? null : sublevel.getUniqueId();
    }

    @Nullable
    public static Vec3 localToWorld(ServerLevel level, UUID sublevelId, Vec3 localPosition) {
        SubLevelAccess sublevel = resolveSublevel(level, sublevelId);

        if (sublevel == null) {
            return null;
        }

        Vector3d transformed = sublevel.logicalPose().transformPosition(new Vector3d(localPosition.x, localPosition.y, localPosition.z));
        return new Vec3(transformed.x(), transformed.y(), transformed.z());
    }

    @Nullable
    private static SubLevelAccess resolveSublevel(ServerLevel level, UUID sublevelId) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container == null ? null : container.getSubLevel(sublevelId);
    }
}
