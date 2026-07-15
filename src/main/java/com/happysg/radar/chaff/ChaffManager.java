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
import net.minecraft.network.chat.Component;
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
import java.util.Locale;
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
        ChaffRollSummary rollSummary = new ChaffRollSummary();
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

            long untilTick = applyToVolley(level, "controller:" + controllerPos.asLong(),
                    selectedTrack.getId(), profile, settings, now);
            if (untilTick > now && controller.beginChaffSuppression(selectedTrack.getId(), untilTick)) {
                successfullySuppressedTargets.add(selectedTrack.getId());
                rollSummary.recordSaved(selectedTrack.getId(), untilTick - now);
            }
        }

        applyToRegisteredEntityLocks(level, rocket.position(), launch, profile, settings, now, rollSummary);

        for (String targetId : successfullySuppressedTargets) {
            clearCoarseRwrLockIfFullySuppressed(level, networkData, targetId);
        }

        printRollToChat(level, fireworks, profile, rollSummary);
    }

    private static void applyToRegisteredEntityLocks(ServerLevel level, Vec3 explosionPosition,
                                                     LaunchContext launch, ChaffProfile profile,
                                                     ChaffSettings settings, long now,
                                                     ChaffRollSummary rollSummary) {
        for (Entity entity : level.getAllEntities()) {
            ChaffLockAdapter<Entity> adapter = ChaffLockRegistry.find(entity);
            if (adapter == null || !entity.isAlive()) {
                continue;
            }

            String targetId = adapter.getTargetId(entity);
            if (targetId == null || targetId.isBlank()) {
                continue;
            }

            boolean launchedFromTarget = launch.sourceTargetIds().contains(targetId);
            if (!launchedFromTarget && !isNearTarget(level, explosionPosition, targetId, settings.radius())) {
                continue;
            }

            long untilTick = applyToVolley(level, "entity:" + entity.getUUID(), targetId,
                    profile, settings, now);
            if (untilTick > now) {
                adapter.applySuppression(entity, targetId, untilTick);
                rollSummary.recordSaved(targetId, untilTick - now);
            }
        }
    }

    private static void printRollToChat(ServerLevel level, Fireworks fireworks, ChaffProfile profile,
                                        ChaffRollSummary summary) {
        int starCount = fireworks.explosions().size();
        String chance = String.format(Locale.ROOT, "%.1f%%", profile.chance() * 100.0D);
        String duration = summary.succeeded()
                ? String.format(Locale.ROOT, "%.2fs (%d ticks)",
                summary.durationTicks() / 20.0D, summary.durationTicks())
                : "n/a";
        Component message = Component.literal(String.format(Locale.ROOT,
                "[Chaff] stars=%d, size=%d | chance=%s | %s | entities saved=%d | duration=%s",
                starCount, profile.weight(), chance, summary.succeeded() ? "SUCCESS" : "FAIL",
                summary.savedCount(), duration));
        level.players().forEach(player -> player.sendSystemMessage(message));
    }

    private static long applyToVolley(ServerLevel level, String sourceId, String targetId,
                                      ChaffProfile profile, ChaffSettings settings, long now) {
        VolleyKey key = new VolleyKey(sourceId, targetId);
        Map<VolleyKey, VolleyState> dimensionVolleys = VOLLEYS.computeIfAbsent(
                level.dimension(), ignored -> new HashMap<>());
        VolleyState volley = dimensionVolleys.get(key);
        if (volley == null || now - volley.lastTick > settings.volleyWindowTicks()) {
            volley = new VolleyState(now, 0.0D, level.random.nextDouble(), 0, false);
            dimensionVolleys.put(key, volley);
        }

        volley.lastTick = now;
        if (!volley.succeeded) {
            double rollChance = diminishedRollChance(
                    profile.chance(), volley.combinedChance, settings.maxVolleyChance());
            double combined = 1.0D - (1.0D - volley.combinedChance) * (1.0D - rollChance);
            volley.combinedChance = Math.min(settings.maxVolleyChance(), combined);
            if (volley.threshold < volley.combinedChance) {
                volley.succeeded = true;
                volley.strongestDurationTicks = profile.durationTicks();
                return now + Math.min(settings.maxDurationTicks(), profile.durationTicks());
            }
        } else if (profile.durationTicks() > volley.strongestDurationTicks) {
            volley.strongestDurationTicks = profile.durationTicks();
            return now + Math.min(settings.maxDurationTicks(), profile.durationTicks());
        }
        return -1L;
    }

    /**
     * Reduces each follow-up roll in proportion to how much of the volley cap has
     * already been used. The first roll receives the firework's full profile chance.
     */
    static double diminishedRollChance(double profileChance, double combinedChance, double volleyCap) {
        if (profileChance <= 0.0D || volleyCap <= 0.0D || combinedChance >= volleyCap) {
            return 0.0D;
        }
        double remainingCapFraction = 1.0D - combinedChance / volleyCap;
        return Math.min(1.0D, profileChance) * remainingCapFraction;
    }

    private static boolean isNearTarget(ServerLevel level, Vec3 explosionPos, RadarTrack track, double radius) {
        AABB bounds = resolveTargetBounds(level, track.getId(), track.position());
        return isNearBounds(explosionPos, bounds, radius);
    }

    private static boolean isNearTarget(ServerLevel level, Vec3 explosionPos, String targetId, double radius) {
        AABB bounds = resolveTargetBounds(level, targetId, null);
        return bounds != null && isNearBounds(explosionPos, bounds, radius);
    }

    private static boolean isNearBounds(Vec3 explosionPos, AABB bounds, double radius) {
        double x = Math.max(bounds.minX, Math.min(explosionPos.x, bounds.maxX));
        double y = Math.max(bounds.minY, Math.min(explosionPos.y, bounds.maxY));
        double z = Math.max(bounds.minZ, Math.min(explosionPos.z, bounds.maxZ));
        return explosionPos.distanceToSqr(x, y, z) <= radius * radius;
    }

    private static AABB resolveTargetBounds(ServerLevel level, String targetId, Vec3 fallbackPosition) {
        UUID targetUuid = parseUuid(targetId);
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

        return fallbackPosition == null ? null : new AABB(fallbackPosition, fallbackPosition);
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

    private record VolleyKey(String sourceId, String targetId) {
    }

    private static final class ChaffRollSummary {
        private final Set<String> savedTargetIds = new HashSet<>();
        private long durationTicks;

        private void recordSaved(String targetId, long durationTicks) {
            savedTargetIds.add(targetId);
            this.durationTicks = Math.max(this.durationTicks, durationTicks);
        }

        private boolean succeeded() {
            return !savedTargetIds.isEmpty();
        }

        private int savedCount() {
            return savedTargetIds.size();
        }

        private long durationTicks() {
            return durationTicks;
        }
    }

    private static final class VolleyState {
        private long lastTick;
        private double combinedChance;
        private final double threshold;
        private int strongestDurationTicks;
        private boolean succeeded;

        private VolleyState(long lastTick, double combinedChance, double threshold,
                            int strongestDurationTicks, boolean succeeded) {
            this.lastTick = lastTick;
            this.combinedChance = combinedChance;
            this.threshold = threshold;
            this.strongestDurationTicks = strongestDurationTicks;
            this.succeeded = succeeded;
        }
    }
}
