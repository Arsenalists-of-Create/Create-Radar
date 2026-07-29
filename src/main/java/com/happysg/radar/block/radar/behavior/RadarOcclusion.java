package com.happysg.radar.block.radar.behavior;

import com.happysg.radar.block.arad.rwr.RadarType;
import com.happysg.radar.block.arad.rwr.RwrTargetReference;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.SableUtils;
import com.happysg.radar.config.RadarConfig;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Counts full solid blocks crossed by native radar signals without loading chunks.
 */
public final class RadarOcclusion {
    private static final double MIN_RAY_LENGTH_SQR = 1.0E-8;
    private static final double SUBLEVEL_QUERY_INFLATION = 1.0E-4;

    private RadarOcclusion() {
    }

    public static boolean isOccluded(
            BlockEntity radar,
            Vec3 radarPosition,
            Vec3 targetPosition,
            RadarType radarType
    ) {
        return isOccluded(radar, radarPosition, targetPosition, radarType, null);
    }

    public static boolean isOccluded(
            BlockEntity radar,
            Vec3 radarPosition,
            Entity target,
            RadarType radarType
    ) {
        return isOccluded(radar, radarPosition, target.getBoundingBox().getCenter(), radarType, null);
    }

    public static boolean isOccluded(
            BlockEntity radar,
            Vec3 radarPosition,
            ServerLevel level,
            RwrTargetReference target,
            Vec3 resolvedTargetPosition,
            RadarType radarType
    ) {
        Vec3 targetPosition = resolvedTargetPosition;
        SubLevelAccess targetSublevel = null;

        if (target.kind() == RwrTargetReference.Kind.ENTITY) {
            Entity entity = target.resolveEntity(level);
            if (entity != null) {
                targetPosition = entity.getBoundingBox().getCenter();
            }
        } else if (target.kind() == RwrTargetReference.Kind.SABLE_SHIP) {
            targetSublevel = target.resolveSableShip(level);
        }

        return isOccluded(radar, radarPosition, targetPosition, radarType, targetSublevel);
    }

    public static boolean isOccluded(
            BlockEntity radar,
            Vec3 radarPosition,
            Vec3 targetPosition,
            RadarType radarType,
            @Nullable SubLevelAccess targetSublevel
    ) {
        if (!RadarConfig.server().radarOcclusionEnabled.get()
                || radarPosition.distanceToSqr(targetPosition) <= MIN_RAY_LENGTH_SQR) {
            return false;
        }

        Level level = radar.getLevel();
        if (level == null) {
            return false;
        }

        SolidBlockCounter counter = new SolidBlockCounter(maxSolidBlocks(radarType));
        if (countInLevel(level, radarPosition, targetPosition, counter)) {
            return true;
        }

        if (!Mods.SABLE.isLoaded()) {
            return false;
        }

        UUID targetSublevelId = targetSublevel == null ? null : targetSublevel.getUniqueId();
        AABB rayBounds = rayBounds(radarPosition, targetPosition).inflate(SUBLEVEL_QUERY_INFLATION);
        for (SubLevel sublevel : SableUtils.getLoadedShips(level, rayBounds)) {
            if (targetSublevelId != null && targetSublevelId.equals(sublevel.getUniqueId())) {
                continue;
            }

            Vec3 localFrom = toLocal(sublevel, radarPosition);
            Vec3 localTo = toLocal(sublevel, targetPosition);
            if (countInLevel(sublevel.getLevel(), localFrom, localTo, counter)) {
                return true;
            }
        }

        return false;
    }

    private static int maxSolidBlocks(RadarType radarType) {
        return radarType == RadarType.GROUND
                ? RadarConfig.server().groundRadarMaxSolidBlocks.get()
                : RadarConfig.server().skyPlaneRadarMaxSolidBlocks.get();
    }

    private static boolean countInLevel(Level level, Vec3 from, Vec3 to, SolidBlockCounter counter) {
        BlockPos startBlock = BlockPos.containing(from);
        BlockPos endBlock = BlockPos.containing(to);

        return BlockGetter.traverseBlocks(
                from,
                to,
                counter,
                (currentCounter, pos) -> {
                    if (pos.equals(startBlock) || pos.equals(endBlock) || !level.hasChunkAt(pos)) {
                        return null;
                    }

                    BlockState state = level.getBlockState(pos);
                    if (state.canOcclude() && state.isSolidRender(level, pos)
                            && currentCounter.incrementAndIsExceeded()) {
                        return Boolean.TRUE;
                    }
                    return null;
                },
                ignored -> Boolean.FALSE
        );
    }

    private static Vec3 toLocal(SubLevel sublevel, Vec3 worldPosition) {
        Vector3d local = sublevel.logicalPose().transformPositionInverse(new Vector3d(
                worldPosition.x,
                worldPosition.y,
                worldPosition.z
        ));
        return new Vec3(local.x, local.y, local.z);
    }

    private static AABB rayBounds(Vec3 from, Vec3 to) {
        return new AABB(
                Math.min(from.x, to.x),
                Math.min(from.y, to.y),
                Math.min(from.z, to.z),
                Math.max(from.x, to.x),
                Math.max(from.y, to.y),
                Math.max(from.z, to.z)
        );
    }

    private static final class SolidBlockCounter {
        private final int limit;
        private int count;

        private SolidBlockCounter(int limit) {
            this.limit = Math.max(0, limit);
        }

        private boolean incrementAndIsExceeded() {
            return ++count > limit;
        }
    }
}
