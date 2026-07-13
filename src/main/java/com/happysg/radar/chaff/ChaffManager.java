package com.happysg.radar.chaff;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.arad.aradnetworks.RadarContactRegistry;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.SableUtils;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = CreateRadar.MODID)
public final class ChaffManager {
    private static final double LAUNCH_VOLUME_TOLERANCE = 2.0D;
    private static final long MAX_LAUNCH_CONTEXT_AGE = 1200L;

    private static final Map<ResourceKey<Level>, Map<UUID, LaunchContext>> LAUNCHES = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<VolleyKey, VolleyState>> VOLLEYS = new HashMap<>();

    private ChaffManager() {
    }

    public static void captureLaunch(FireworkRocketEntity rocket) {
        if (!(rocket.level() instanceof ServerLevel level)) {
            return;
        }

        Map<UUID, LaunchContext> launches = LAUNCHES.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
        if (launches.containsKey(rocket.getUUID())) {
            return;
        }

        Set<String> sourceTargetIds = new HashSet<>();
        Entity owner = rocket.getOwner();
        if (owner != null) {
            sourceTargetIds.add(owner.getUUID().toString());
        }

        AABB launchVolume = new AABB(rocket.position(), rocket.position()).inflate(LAUNCH_VOLUME_TOLERANCE);
        for (Entity entity : level.getEntities(rocket, launchVolume, entity -> entity != rocket)) {
            sourceTargetIds.add(entity.getUUID().toString());
        }

        if (Mods.SABLE.isLoaded()) {
            for (SubLevel subLevel : SableUtils.getLoadedShips(level, launchVolume)) {
                sourceTargetIds.add(subLevel.getUniqueId().toString());
            }
        }

        launches.put(rocket.getUUID(), new LaunchContext(Set.copyOf(sourceTargetIds), level.getGameTime()));
    }

    public static void onFireworkExplode(FireworkRocketEntity rocket) {
        if (!(rocket.level() instanceof ServerLevel level)) {
            return;
        }

        ChaffSettings settings = ChaffSettings.current();
        LaunchContext launch = removeLaunchContext(level, rocket.getUUID());
        if (!settings.enabled()) {
            return;
        }

        Fireworks fireworks = rocket.getItem().get(DataComponents.FIREWORKS);
        ChaffProfile profile = ChaffProfile.from(fireworks, settings);
        if (profile == null) {
            return;
        }

        if (launch == null) {
            Set<String> ownerOnly = new HashSet<>();
            if (rocket.getOwner() != null) {
                ownerOnly.add(rocket.getOwner().getUUID().toString());
            }
            launch = new LaunchContext(Set.copyOf(ownerOnly), level.getGameTime());
        }

        NetworkData networkData = NetworkData.get(level);
        Set<String> successfullySuppressedTargets = new HashSet<>();
        long now = level.getGameTime();

        for (NetworkData.Group group : networkData.getGroups().values()) {
            if (!group.key.dim().equals(level.dimension()) || group.selectedTargetId == null) {
                continue;
            }

            BlockPos controllerPos = group.key.filtererPos();
            if (!level.hasChunkAt(controllerPos)
                    || !(level.getBlockEntity(controllerPos) instanceof NetworkFiltererBlockEntity controller)) {
                continue;
            }

            RadarTrack selectedTrack = controller.getSelectedTrackForChaff(group.selectedTargetId);
            if (selectedTrack == null) {
                continue;
            }

            boolean launchedFromTarget = launch.sourceTargetIds().contains(selectedTrack.getId());
            if (!launchedFromTarget && !isNearTarget(level, rocket.position(), selectedTrack, settings.radius())) {
                continue;
            }

            VolleyKey key = new VolleyKey(controllerPos, selectedTrack.getId());
            Map<VolleyKey, VolleyState> dimensionVolleys = VOLLEYS.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
            VolleyState volley = dimensionVolleys.get(key);
            if (volley == null || now - volley.lastTick > settings.volleyWindowTicks()) {
                volley = new VolleyState(now, now, 0.0D, level.random.nextDouble(), 0, false);
                dimensionVolleys.put(key, volley);
            }

            volley.lastTick = now;
            if (!volley.succeeded) {
                double combined = 1.0D - (1.0D - volley.combinedChance) * (1.0D - profile.chance());
                volley.combinedChance = Math.min(settings.maxVolleyChance(), combined);
                if (volley.threshold < volley.combinedChance) {
                    volley.succeeded = true;
                    volley.strongestDurationTicks = profile.durationTicks();
                    long untilTick = Math.min(volley.firstTick + settings.maxDurationTicks(), now + profile.durationTicks());
                    if (controller.beginChaffSuppression(selectedTrack.getId(), untilTick)) {
                        successfullySuppressedTargets.add(selectedTrack.getId());
                    }
                }
            } else if (profile.durationTicks() > volley.strongestDurationTicks) {
                volley.strongestDurationTicks = profile.durationTicks();
                long untilTick = Math.min(volley.firstTick + settings.maxDurationTicks(), now + profile.durationTicks());
                if (controller.beginChaffSuppression(selectedTrack.getId(), untilTick)) {
                    successfullySuppressedTargets.add(selectedTrack.getId());
                }
            }
        }

        for (String targetId : successfullySuppressedTargets) {
            clearCoarseRwrLockIfFullySuppressed(level, networkData, targetId);
        }
    }

    private static boolean isNearTarget(ServerLevel level, Vec3 explosionPos, RadarTrack track, double radius) {
        AABB bounds = resolveTargetBounds(level, track);
        double x = Math.max(bounds.minX, Math.min(explosionPos.x, bounds.maxX));
        double y = Math.max(bounds.minY, Math.min(explosionPos.y, bounds.maxY));
        double z = Math.max(bounds.minZ, Math.min(explosionPos.z, bounds.maxZ));
        return explosionPos.distanceToSqr(x, y, z) <= radius * radius;
    }

    private static AABB resolveTargetBounds(ServerLevel level, RadarTrack track) {
        UUID targetUuid = parseUuid(track.getId());
        if (targetUuid != null) {
            if (Mods.SABLE.isLoaded()) {
                SubLevelContainer container = SubLevelContainer.getContainer(level);
                SubLevelAccess subLevel = container == null ? null : container.getSubLevel(targetUuid);
                AABB subLevelBounds = toAabb(subLevel);
                if (subLevelBounds != null) {
                    return subLevelBounds;
                }
            }

            Entity entity = level.getEntity(targetUuid);
            if (entity != null && entity.isAlive()) {
                return entity.getBoundingBox();
            }
        }

        return new AABB(track.position(), track.position());
    }

    private static AABB toAabb(SubLevelAccess subLevel) {
        if (subLevel == null || subLevel.boundingBox() == null) {
            return null;
        }
        BoundingBox3dc box = subLevel.boundingBox();
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    private static void clearCoarseRwrLockIfFullySuppressed(ServerLevel level, NetworkData data, String targetId) {
        UUID targetUuid = parseUuid(targetId);
        if (targetUuid == null || !Mods.SABLE.isLoaded()) {
            return;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || container.getSubLevel(targetUuid) == null) {
            return;
        }

        for (NetworkData.Group group : data.getGroups().values()) {
            if (!targetId.equals(group.selectedTargetId) || !group.key.dim().equals(level.dimension())) {
                continue;
            }
            BlockPos controllerPos = group.key.filtererPos();
            if (level.hasChunkAt(controllerPos)
                    && level.getBlockEntity(controllerPos) instanceof NetworkFiltererBlockEntity controller
                    && !controller.isChaffSuppressed(targetId)) {
                return;
            }
        }

        RadarContactRegistry.unLock(level, targetUuid);
    }

    private static LaunchContext removeLaunchContext(ServerLevel level, UUID rocketId) {
        Map<UUID, LaunchContext> launches = LAUNCHES.get(level.dimension());
        return launches == null ? null : launches.remove(rocketId);
    }

    private static UUID parseUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20 != 0) {
            return;
        }

        long now = level.getGameTime();
        Map<UUID, LaunchContext> launches = LAUNCHES.get(level.dimension());
        if (launches != null) {
            launches.values().removeIf(context -> now - context.createdTick() > MAX_LAUNCH_CONTEXT_AGE);
            if (launches.isEmpty()) {
                LAUNCHES.remove(level.dimension());
            }
        }

        ChaffSettings settings = ChaffSettings.current();
        Map<VolleyKey, VolleyState> volleys = VOLLEYS.get(level.dimension());
        if (volleys != null) {
            volleys.values().removeIf(volley -> now - volley.lastTick > settings.volleyWindowTicks());
            if (volleys.isEmpty()) {
                VOLLEYS.remove(level.dimension());
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LAUNCHES.remove(level.dimension());
            VOLLEYS.remove(level.dimension());
        }
    }

    private record LaunchContext(Set<String> sourceTargetIds, long createdTick) {
    }

    private record VolleyKey(BlockPos controllerPos, String targetId) {
    }

    private static final class VolleyState {
        private final long firstTick;
        private long lastTick;
        private double combinedChance;
        private final double threshold;
        private int strongestDurationTicks;
        private boolean succeeded;

        private VolleyState(long firstTick, long lastTick, double combinedChance, double threshold,
                            int strongestDurationTicks, boolean succeeded) {
            this.firstTick = firstTick;
            this.lastTick = lastTick;
            this.combinedChance = combinedChance;
            this.threshold = threshold;
            this.strongestDurationTicks = strongestDurationTicks;
            this.succeeded = succeeded;
        }
    }
}
