package com.happysg.radar.block.behavior.networks.config;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;

public final class AutoTargetingHelper {
    private AutoTargetingHelper() {}
    private static final Logger LOGGER = LogUtils.getLogger();

    public static Vec3 defaultTargetOrigin(BlockPos monitorControllerPos) {
        return Vec3.atCenterOf(monitorControllerPos);
    }

    @Nullable
    public static Vec3 resolveSelectedTargetPos(@Nullable String selectedId, Collection<RadarTrack> tracks) {
        if (selectedId == null || tracks == null) return null;
        for (RadarTrack track : tracks) {
            if (track != null && selectedId.equals(track.id()))
                return track.position();
        }
        return null;
    }

    public static boolean isInSafeZone(Vec3 pos, List<AABB> safeZones) {
        if (safeZones == null || safeZones.isEmpty()) return false;
        for (AABB zone : safeZones) {
            if (zone.contains(pos))
                return true;
        }
        return false;
    }


    @Nullable
    public static RadarTrack pickAutoTarget(
            TargetingConfig targetingConfig,
            Vec3 origin,
            Collection<RadarTrack> tracks,
            List<AABB> safeZones,
            IdentificationConfig identificationConfig,
            @Nullable ServerLevel serverLevel) {

        if (targetingConfig == null) targetingConfig = TargetingConfig.DEFAULT;
        if (!targetingConfig.autoTarget()) return null;

        if (identificationConfig == null) identificationConfig = IdentificationConfig.DEFAULT;

        Set<String> ignoreList = buildIgnoreList(identificationConfig);

        double best = Double.MAX_VALUE;
        RadarTrack bestTrack = null;

        for (RadarTrack track : tracks) {
            if (!targetingConfig.test(track.trackCategory()))
                continue;

            Vec3 pos = track.position();
            if (pos == null)
                continue;

            if (isIgnoredByIdentification(track, serverLevel, ignoreList))
                continue;

            if (isInSafeZone(pos, safeZones))
                continue;


            if (targetingConfig.lineOfSight() && serverLevel != null && origin != null) {
                if (!hasLineOfSight(serverLevel, origin, track)) {
                    continue;
                }
            }

            double d = pos.distanceTo(origin);
            if (d >= best)
                continue;

            if (projectileApproaching(track, origin)) {
                best = d;
                bestTrack = track;
            }
        }

        return bestTrack;
    }

    public static boolean hasLineOfSight(ServerLevel level, Vec3 start, RadarTrack track) {
        if (level == null || start == null || track == null || track.position() == null) return false;

        float height = track.getEnityHeight();
        int blocksHigh = (int) Math.ceil(height);

        Vec3 from = start;

        for (int h = blocksHigh - 1; h >= 0; h--) {
            Vec3 to = track.position().add(0, h + 0.5, 0);

            ClipContext ctx = new ClipContext(
                    from, to,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    CollisionContext.empty()
            );

            HitResult hit = level.clip(ctx);
            if (hit.getType() == HitResult.Type.MISS) {
                return true; // at least one clear point
            }
        }

        return false;
    }

    private static boolean projectileApproaching(RadarTrack track, Vec3 origin) {
        if (track.trackCategory() != TrackCategory.PROJECTILE) return true;

        Vec3 vel = track.velocity();
        if (vel == null || vel.lengthSqr() == 0) return false;

        Vec3 toOrigin = origin.subtract(track.position()).normalize();
        return vel.normalize().dot(toOrigin) > 0;
    }
    private static Set<String> buildIgnoreList(IdentificationConfig config) {
        Set<String> out = new HashSet<>();

        for (String name : config.usernames()) {
            if (name == null || name.isBlank()) continue;
            out.add(name.toLowerCase(Locale.ROOT));
        }
        if(!(config.label() == null || config.label().isBlank())) {
          out.add(config.label().toLowerCase(Locale.ROOT));
        }

        return out;
    }
    public static boolean isIgnoredByIdentification(RadarTrack track, @Nullable ServerLevel sl, Set<String> ignoreList) {
        if (track == null || ignoreList == null || ignoreList.isEmpty()) return false;

        // PLAYER -> username
        if (track.trackCategory() == TrackCategory.PLAYER) {
            if (sl == null) return false;

            try {
                UUID uuid = UUID.fromString(track.getId());
                var p = sl.getPlayerByUUID(uuid);
                if (p == null) return false;
                String name = p.getGameProfile().getName();
                return name != null && ignoreList.contains(name.toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }

        // VS2 -> transponder / name via IDManager
        if (track.trackCategory() == TrackCategory.SABLE) {
            try {
                long shipId = Long.parseLong(track.getId());
                var rec = com.happysg.radar.block.controller.id.IDManager.getIDRecordByShipId(shipId);
                if (rec == null) return false;

                String key = (rec.secretID() != null && !rec.secretID().isBlank())
                        ? rec.secretID()
                        : rec.name();

                return key != null && !key.isBlank() && ignoreList.contains(key.toLowerCase(Locale.ROOT));
            } catch (NumberFormatException ignored) {
                return false;
            }
        }

        return false;
    }

//    private static boolean isIgnoredByIdentification(
//            RadarTrack track,
//            @Nullable ServerLevel serverLevel,
//            Set<String> ignoreList) {
//
//        String key = resolveIdentificationKey(track, serverLevel);
//        if (key == null || key.isBlank())
//            return false; // unknown identity => allowed
//
//        return ignoreList.contains(key.toLowerCase(Locale.ROOT));
//    }
//
//
//    @Nullable
//    private static String resolveIdentificationKey(
//            RadarTrack track,
//            @Nullable ServerLevel serverLevel) {
//
//        // PLAYER -> username
//        if (track.trackCategory() == TrackCategory.PLAYER) {
//            if (serverLevel == null) return null;
//            return resolvePlayerName(serverLevel, track.id());
//        }
//
//        // VS2 -> secretID (transponder)
//        if (track.trackCategory() == TrackCategory.VS2) {
//            IDManager.IDRecord rec = IDManager.getIDRecordByShipId(Long.parseLong(track.id()));
//            if (rec == null) return null;
//
//            if (rec.secretID() != null && !rec.secretID().isBlank()){
//            return rec.secretID();
//        }
//
//            return rec.name(); // optional fallback
//        }
//
//        return null;
//    }
//
//    @Nullable
//    private static String resolvePlayerName(ServerLevel serverLevel, String uuidString) {
//        try {
//            UUID uuid = UUID.fromString(uuidString);
//            Player sp = serverLevel.getPlayerByUUID(uuid);
//            if (sp == null) return null;
//            return sp.getGameProfile().getName();
//        } catch (IllegalArgumentException ignored) {
//            return null;
//        }
//    }


}
