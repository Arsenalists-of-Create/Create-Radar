package com.happysg.radar.block.monitor;

import com.happysg.radar.block.behavior.networks.INetworkNode;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import com.happysg.radar.block.behavior.networks.config.IdentificationConfig;
import com.happysg.radar.block.behavior.networks.config.TargetingConfig;
import com.happysg.radar.block.arad.aradnetworks.ARADData;
import com.happysg.radar.block.arad.rwr.RadarType;
import com.happysg.radar.block.arad.rwr.RadarWarningReceiverBlockEntity;
import com.happysg.radar.block.arad.rwr.ExternalRwrEmitterRegistry;
import com.happysg.radar.block.arad.rwr.RwrRadarContact;
import com.happysg.radar.block.controller.id.IDManager;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.skyradar.SkyRadarBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.RadarTrackUtil;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.sable.SableSilhouetteServerCache;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.block.behavior.networks.config.AutoTargetingHelper;
import com.happysg.radar.compat.vs2.SableUtils;
import com.mojang.logging.LogUtils;
import com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class MonitorBlockEntity extends SmartBlockEntity implements IHaveHoveringInformation, INetworkNode  {

    protected BlockPos controller;
    protected int radius = 1;

    /** Client must rely on this field (synced). Server also keeps it as cache. */
    protected @Nullable BlockPos radarPos;

    protected @Nullable IRadar radar;
    protected final Map<BlockPos, IRadar> radarCache = new HashMap<>();
    protected List<RadarDisplayInfo> radarInfos = List.of();
    protected List<RwrDisplayInfo> rwrInfos = List.of();
    private final Map<String, Integer> rwrLockHoldTicks = new HashMap<>();
    private final Map<String, RwrBearingNoiseState> rwrBearingNoise = new HashMap<>();
    protected String hoveredEntity;
    public String selectedEntity;
    private @Nullable String hoveredRwrSource;
    private @Nullable String selectedRwrSource;
    private @Nullable BlockPos selectedRwrRadarPos;
    private @Nullable BlockPos selectedRwrPos;
    public RadarTrack activetrack;
    boolean reset = false;
    protected BlockPos mountBlock;
    private boolean aradLinked = false;

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Client renders from this list (synced via packet). */
    protected Collection<RadarTrack> cachedTracks = new ArrayList<>();

    /** Keep as field because renderer uses it (coloring). */
    protected DetectionConfig filter = DetectionConfig.DEFAULT;
    private BlockPos lastKnownPos = BlockPos.ZERO;
    public final List<AABB> safeZones = new ArrayList<>();

    public MonitorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public record RadarDisplayInfo(
            BlockPos pos,
            Vec3 center,
            float range,
            boolean running,
            String type,
            float globalAngle,
            float angularSpeed,
            long angleSnapshotTime,
            float fovDegrees,
            @Nullable net.minecraft.core.Direction direction,
            boolean renderRelativeToMonitor,
            @Nullable String ownedLockedTargetId,
            @Nullable Vec3 ownedLockedTargetPos
    ) {}

    public record RwrDisplayInfo(
            String sourceId,
            RadarType radarType,
            float bearingDegrees,
            float radiusOffset,
            boolean withinRadarRange,
            boolean exactLocked,
            boolean primaryThreat,
            boolean engaged,
            boolean friendly
    ) {}

    private record PendingRwrDisplayInfo(
            String sourceId,
            RadarType radarType,
            float trueBearingDegrees,
            float displayBearingDegrees,
            int ring,
            boolean withinRadarRange,
            boolean exactLocked,
            boolean primaryThreat,
            boolean engaged,
            boolean friendly
    ) {}

    private static final class RwrBearingNoiseState {
        private float currentOffset;
        private float targetOffset;
        private long lastUpdateTick;
        private long nextRetargetTick;
        private int retargetCounter;
    }

    private static final int RWR_LOCK_HOLD_TICKS = 20;
    private static final int RWR_ENGAGEMENT_FLASH_TICKS = 10;
    private static final float RWR_STACK_RADIUS_STEP = 0.028f;
    private static final int RWR_BEARING_RETARGET_TICKS = 100;
    private static final float RWR_BEARING_EASE_DEGREES_PER_TICK = 0.04f;
    private static final float RWR_EXACT_LOCK_BEARING_ERROR_DEGREES = 5.0f;
    private static final float RWR_OUTER_BEARING_ERROR_DEGREES = 25.0f;
    private static final float RWR_MIDDLE_WEAK_BEARING_ERROR_DEGREES = 12.0f;
    private static final float RWR_MIDDLE_STRONG_BEARING_ERROR_DEGREES = 6.0f;

    public static boolean shouldRenderRwrContact(RwrDisplayInfo contact, long gameTime) {
        return !contact.engaged() || (gameTime / RWR_ENGAGEMENT_FLASH_TICKS & 1L) == 0L;
    }

    @Override
    public void initialize() {
        super.initialize();
        updateCacheServerOrClient();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    // -------------------------------------------------
    // Tick / sync
    // -------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        if (level == null)
            return;

//        if(activetrack != null){
//            setSelectedTargetServer(activetrack);
//        }

        if (!level.isClientSide && level instanceof ServerLevel sl) {
            if (level.getGameTime() % 5 == 0) {
                syncFromNetwork(sl);
                refreshAradLinkState();
                syncFromArad(sl);
                updateCacheServerOrClient();
                if (isController()) {
                    ARADTargetDesignationHandler.validateSelection(sl, this);
                }

                // keep controller's displayed selection consistent with network
                MonitorBlockEntity controllerBe = getController();
                if (controllerBe != null) {
                    controllerBe.activetrack = controllerBe.resolveActiveTrackFromCache();
                }

                sendData();
            }
        }
        if (!level.isClientSide && level.getGameTime() % 40 == 0) {
            if (level instanceof ServerLevel serverLevel) {

                // nothing to do if we didnt move
                if (lastKnownPos.equals(worldPosition))
                    return;

                ResourceKey<Level> dim = serverLevel.dimension();
                NetworkData data = NetworkData.get(serverLevel);
                ARADData aradData = ARADData.get(serverLevel);
                if (aradData.isMonitorLinked(dim, worldPosition)) {
                    lastKnownPos = worldPosition;
                    setChanged();
                    return;
                }
                if (aradData.updateMonitorPosition(dim, lastKnownPos, worldPosition)) {
                    lastKnownPos = worldPosition;
                    setChanged();
                    return;
                }
                if (data.isEndpointLinked(dim, worldPosition)) {
                    lastKnownPos = worldPosition;
                    setChanged();
                    return;
                }

                boolean updated = data.updateMonitorPosition(
                        dim,
                        lastKnownPos,
                        worldPosition
                );

                // only commit the new position if the network accepted it
                if (updated) {
                    lastKnownPos = worldPosition;
                    setChanged();
                }
            }
        }


    }

    public void onDataLinkRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            ARADTargetDesignationHandler.clear(serverLevel, this);
        }
        // clear any cached network state
        this.activetrack = null;
        this.radarPos = null;
        this.radar = null;
        this.radarCache.clear();
        this.radarInfos = List.of();
        this.rwrInfos = List.of();
        this.controller = null;
        this.aradLinked = false;


        //LOGGER.debug("Reset " + controller +" " +radar + radarPos);
        // force client + server refresh
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
    public void onNetworkDisconnected(){
        onDataLinkRemoved();
    }

    public void refreshAradLinkState() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean linked = ARADData.get(serverLevel).isMonitorLinked(serverLevel.dimension(), getControllerPos());
        if (aradLinked == linked) {
            return;
        }
        if (!linked) {
            ARADTargetDesignationHandler.clear(serverLevel, this);
        }
        aradLinked = linked;
        setChanged();
        sendData();
    }

    private void syncFromArad(ServerLevel sl) {
        if (!aradLinked) {
            rwrInfos = List.of();
            rwrLockHoldTicks.clear();
            rwrBearingNoise.clear();
            return;
        }

        BlockPos rwrPos = ARADData.get(sl).getRwrForMonitor(sl.dimension(), getControllerPos());
        if (rwrPos == null || !(sl.getBlockEntity(rwrPos) instanceof RadarWarningReceiverBlockEntity rwr)) {
            rwrInfos = List.of();
            rwrLockHoldTicks.clear();
            rwrBearingNoise.clear();
            return;
        }

        List<PendingRwrDisplayInfo> pendingInfos = new ArrayList<>();
        List<RwrRadarContact> contacts = rwr.getRadarContacts(sl);
        String primaryThreatSource = primaryThreatSource(contacts);
        Set<String> liveSources = new HashSet<>();
        long gameTime = sl.getGameTime();
        for (RwrRadarContact contact : contacts) {
            liveSources.add(contact.sourceId());
            boolean exactLocked = stabilizeRwrLock(contact);
            int ring = rwrRing(contact.withinRadarRange(), exactLocked);
            float displayBearingDegrees = fuzzedRwrBearing(contact, exactLocked, gameTime);
            pendingInfos.add(new PendingRwrDisplayInfo(
                    contact.sourceId(),
                    contact.radarType(),
                    contact.bearingDegrees(),
                    displayBearingDegrees,
                    ring,
                    contact.withinRadarRange(),
                    exactLocked,
                    Objects.equals(contact.sourceId(), primaryThreatSource),
                    contact.engaged(),
                    contact.friendly()
            ));
        }
        rwrLockHoldTicks.keySet().removeIf(source -> !liveSources.contains(source));
        rwrBearingNoise.keySet().removeIf(source -> !liveSources.contains(source));
        rwrInfos = spreadOverlappingRwrInfos(pendingInfos);
    }

    private float fuzzedRwrBearing(RwrRadarContact contact, boolean exactLocked, long gameTime) {
        String sourceId = contact.sourceId();
        if (sourceId == null || sourceId.isBlank()) {
            return contact.bearingDegrees();
        }

        float maxError = rwrBearingMaxError(contact, exactLocked);
        RwrBearingNoiseState state = rwrBearingNoise.computeIfAbsent(sourceId, ignored -> {
            RwrBearingNoiseState newState = new RwrBearingNoiseState();
            newState.lastUpdateTick = gameTime;
            newState.nextRetargetTick = gameTime;
            newState.targetOffset = deterministicRwrTargetOffset(sourceId, newState.retargetCounter++, maxError);
            newState.currentOffset = newState.targetOffset;
            return newState;
        });

        state.currentOffset = clamp(state.currentOffset, -maxError, maxError);
        state.targetOffset = clamp(state.targetOffset, -maxError, maxError);

        if (gameTime >= state.nextRetargetTick) {
            state.targetOffset = deterministicRwrTargetOffset(sourceId, state.retargetCounter++, maxError);
            state.nextRetargetTick = gameTime + RWR_BEARING_RETARGET_TICKS;
        }

        long elapsedTicks = Math.max(0L, gameTime - state.lastUpdateTick);
        state.lastUpdateTick = gameTime;
        float maxStep = RWR_BEARING_EASE_DEGREES_PER_TICK * elapsedTicks;
        state.currentOffset = approach(state.currentOffset, state.targetOffset, maxStep);
        state.currentOffset = clamp(state.currentOffset, -maxError, maxError);

        return wrapDegrees360(contact.bearingDegrees() + state.currentOffset);
    }

    private static float rwrBearingMaxError(RwrRadarContact contact, boolean exactLocked) {
        if (exactLocked) {
            return RWR_EXACT_LOCK_BEARING_ERROR_DEGREES;
        }
        if (!contact.withinRadarRange()) {
            return RWR_OUTER_BEARING_ERROR_DEGREES;
        }
        float signal = clamp(contact.signalStrength(), 1.0f, 14.0f);
        float signalScale = (signal - 1.0f) / 13.0f;
        return clamp(
                RWR_MIDDLE_WEAK_BEARING_ERROR_DEGREES
                        - signalScale * (RWR_MIDDLE_WEAK_BEARING_ERROR_DEGREES - RWR_MIDDLE_STRONG_BEARING_ERROR_DEGREES),
                RWR_MIDDLE_STRONG_BEARING_ERROR_DEGREES,
                RWR_MIDDLE_WEAK_BEARING_ERROR_DEGREES
        );
    }

    private static float deterministicRwrTargetOffset(String sourceId, int retargetCounter, float maxError) {
        if (maxError <= 0.0f) {
            return 0.0f;
        }
        long seed = 0x9E3779B97F4A7C15L;
        seed ^= sourceId.hashCode();
        seed = Long.rotateLeft(seed, 27) * 0x94D049BB133111EBL;
        seed ^= retargetCounter * 0xBF58476D1CE4E5B9L;
        Random random = new Random(seed);
        return (random.nextFloat() * 2.0f - 1.0f) * maxError;
    }

    private static float approach(float current, float target, float maxStep) {
        if (current < target) {
            return Math.min(target, current + maxStep);
        }
        if (current > target) {
            return Math.max(target, current - maxStep);
        }
        return current;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static @Nullable String primaryThreatSource(List<RwrRadarContact> contacts) {
        String primarySource = null;
        float strongestSignal = Float.NEGATIVE_INFINITY;

        for (RwrRadarContact contact : contacts) {
            if (!contact.withinRadarRange()) {
                continue;
            }
            if (contact.friendly() && !contact.exactLocked()) {
                continue;
            }
            if (primarySource == null || contact.signalStrength() > strongestSignal) {
                primarySource = contact.sourceId();
                strongestSignal = contact.signalStrength();
            }
        }

        return primarySource;
    }

    private boolean stabilizeRwrLock(RwrRadarContact contact) {
        String sourceId = contact.sourceId();
        if (sourceId == null || sourceId.isBlank()) {
            return contact.exactLocked();
        }

        if (contact.exactLocked()) {
            rwrLockHoldTicks.put(sourceId, RWR_LOCK_HOLD_TICKS);
            return true;
        }

        if (level instanceof ServerLevel serverLevel
                && ExternalRwrEmitterRegistry.isActiveSource(serverLevel, sourceId)) {
            rwrLockHoldTicks.remove(sourceId);
            return false;
        }

        int holdTicks = rwrLockHoldTicks.getOrDefault(sourceId, 0);
        if (holdTicks <= 0) {
            rwrLockHoldTicks.remove(sourceId);
            return false;
        }

        holdTicks = Math.max(0, holdTicks - 5);
        if (holdTicks == 0) {
            rwrLockHoldTicks.remove(sourceId);
            return false;
        }

        rwrLockHoldTicks.put(sourceId, holdTicks);
        return true;
    }

    private static List<RwrDisplayInfo> spreadOverlappingRwrInfos(List<PendingRwrDisplayInfo> pendingInfos) {
        Map<String, Integer> groupSizes = new HashMap<>();
        Map<String, Integer> groupIndexes = new HashMap<>();

        for (PendingRwrDisplayInfo info : pendingInfos) {
            groupSizes.merge(rwrStackKey(info), 1, Integer::sum);
        }

        List<RwrDisplayInfo> infos = new ArrayList<>();
        for (PendingRwrDisplayInfo info : pendingInfos) {
            String key = rwrStackKey(info);
            int count = groupSizes.getOrDefault(key, 1);
            int index = groupIndexes.merge(key, 1, Integer::sum) - 1;
            float radiusOffset = count <= 1 ? 0.0f : (index - (count - 1) * 0.5f) * RWR_STACK_RADIUS_STEP;
            infos.add(new RwrDisplayInfo(
                    info.sourceId(),
                    info.radarType(),
                    info.displayBearingDegrees(),
                    radiusOffset,
                    info.withinRadarRange(),
                    info.exactLocked(),
                    info.primaryThreat(),
                    info.engaged(),
                    info.friendly()
            ));
        }
        return List.copyOf(infos);
    }

    private static String rwrStackKey(PendingRwrDisplayInfo info) {
        return info.ring() + ":" + Math.round(wrapDegrees360(info.trueBearingDegrees()) * 100.0f);
    }

    private static int rwrRing(boolean withinRadarRange, boolean exactLocked) {
        if (exactLocked) {
            return 0;
        }
        return withinRadarRange ? 1 : 2;
    }

    private static float wrapDegrees360(float angle) {
        angle %= 360.0f;
        if (angle < 0.0f) {
            angle += 360.0f;
        }
        return angle;
    }



    private void syncFromNetwork(ServerLevel sl) {
        NetworkData.Group g = getNetworkGroup(sl);
        if (g == null) {
            return;
        }


        List<RadarDisplayInfo> newRadarInfos = buildRadarInfos(sl, g);
        BlockPos netRadar = newRadarInfos.isEmpty() ? null : newRadarInfos.get(0).pos();
        if (!Objects.equals(netRadar, radarPos)) radar = null;
        radarPos = netRadar;
        radarInfos = newRadarInfos;

        filter = DetectionConfig.fromTag(g.detectionTag);
        selectedEntity = g.selectedTargetId;
    }

    private List<RadarDisplayInfo> buildRadarInfos(ServerLevel sl, NetworkData.Group g) {
        List<RadarDisplayInfo> infos = new ArrayList<>();
        Map<BlockPos, RadarDisplayInfo> previousInfos = new HashMap<>();
        for (RadarDisplayInfo info : radarInfos) {
            previousInfos.put(info.pos(), info);
        }

        OwnedLock ownedLock = findOwnedLock(sl, g);
        for (NetworkData.RadarEndpoint endpoint : g.getRadarEndpoints()) {
            BlockEntity be = sl.getBlockEntity(endpoint.pos());
            if (!(be instanceof IRadar radar)) {
                RadarDisplayInfo previousInfo = previousInfos.get(endpoint.pos());
                if (previousInfo != null && !sl.hasChunkAt(endpoint.pos())) {
                    infos.add(previousInfo);
                }
                continue;
            }
            infos.add(new RadarDisplayInfo(
                    endpoint.pos(),
                    PhysicsHandler.getWorldVec(sl, endpoint.pos()),
                    radar.getRange(),
                    radar.isRunning(),
                    radar.getRadarType(),
                    radar.getGlobalAngle(),
                    getRadarAngularSpeed(radar),
                    sl.getGameTime(),
                    radar.getFovDegrees(),
                    radar.getradarDirection(),
                    radar.renderRelativeToMonitor(),
                    ownedLock != null && ownedLock.radarPos().equals(endpoint.pos()) ? ownedLock.targetId() : null,
                    ownedLock != null && ownedLock.radarPos().equals(endpoint.pos()) ? ownedLock.targetPos() : null
            ));
        }
        return List.copyOf(infos);
    }

    private static float getRadarAngularSpeed(IRadar radar) {
        if (radar instanceof RadarBearingBlockEntity bearing) {
            return bearing.getAngularSpeed();
        }
        if (radar instanceof SkyRadarBlockEntity skyRadar) {
            return skyRadar.getEffectiveAngularSpeed();
        }
        return 0f;
    }

    private @Nullable OwnedLock findOwnedLock(ServerLevel sl, NetworkData.Group g) {
        String selectedId = g.selectedTargetId;
        if (selectedId == null || selectedId.isBlank()) {
            return null;
        }

        BlockPos closestRadarPos = null;
        RadarTrack closestTrack = null;
        double closestDistance = Double.MAX_VALUE;

        for (NetworkData.RadarEndpoint endpoint : g.getRadarEndpoints()) {
            BlockEntity be = sl.getBlockEntity(endpoint.pos());
            if (!(be instanceof IRadar radar) || !radar.isRunning()) {
                continue;
            }
            if (!isLockCapableRadar(radar.getRadarType())) {
                continue;
            }

            RadarTrack track = findTrack(radar, selectedId);
            if (track == null || track.position() == null) {
                continue;
            }

            Vec3 radarPos = PhysicsHandler.getWorldVec(sl, endpoint.pos());
            double distance = radarPos.distanceToSqr(track.position());
            if (distance < closestDistance) {
                closestDistance = distance;
                closestRadarPos = endpoint.pos();
                closestTrack = track;
            }
        }

        if (closestRadarPos == null || closestTrack == null) {
            return null;
        }

        return new OwnedLock(closestRadarPos, selectedId, closestTrack.position());
    }

    private static boolean isLockCapableRadar(String radarType) {
        return "sky".equals(radarType) || "nonspinning".equals(radarType);
    }

    private static @Nullable RadarTrack findTrack(IRadar radar, String selectedId) {
        for (RadarTrack track : radar.getTracks()) {
            if (track == null) {
                continue;
            }
            if (selectedId.equals(track.getId()) || selectedId.equals(track.id())) {
                return track;
            }
        }
        return null;
    }

    private record OwnedLock(BlockPos radarPos, String targetId, Vec3 targetPos) {}

    public void setSelectedTargetServer(@Nullable RadarTrack track) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (!(level instanceof ServerLevel sl))
            return;
        MonitorBlockEntity controllerBe = getController();
        if (controllerBe == null)
            return;
        if (track != null && track.trackCategory() == TrackCategory.SABLE && "Sable:ship".equals(track.entityType())) {
            UUID shipId = UUID.fromString(track.id());
            SubLevelAccess subLevel = SubLevelContainer.getContainer(sl).getSubLevel(shipId);
            if (subLevel == null) {
                track = null;
                this.activetrack = null;
                this.selectedEntity = null;
                reset = true;
            } else {
                reset = false;
            }
        }

        LOGGER.debug("MONITOR setSelectedTargetServer: track={}, controllerPos={}", track == null ? "null" : track.getId(), controllerBe.getBlockPos());

        NetworkData.Group g = controllerBe.getNetworkGroup(sl);
        if (g == null)
            return;

        NetworkData data = NetworkData.get(sl);
        data.setSelectedTargetId(g, track == null ? null : track.getId());

        if (track == null) {
            controllerBe.selectedEntity = null;
            controllerBe.activetrack = null;
        } else {
            controllerBe.selectedEntity = track.getId();
            controllerBe.activetrack = track;
        }



        // Forward selection to the filterer BE at the group's filterer position
        BlockPos filterpos = g.key.filtererPos();
        LOGGER.debug("MONITOR forwarding to filterer: filterPos={}, groupKey={}", filterpos, g.key);

        if (level.getBlockEntity(filterpos) instanceof NetworkFiltererBlockEntity filtererBe) {
            LOGGER.debug("MONITOR found filterer BE: calling receiveSelectedTargetFromMonitor track={}", track == null ? "null" : track.getId());
            LOGGER.debug("Ping");
            filtererBe.receiveSelectedTargetFromMonitor(track,safeZones);
        } else {
            LOGGER.debug("MONITOR could NOT find NetworkFiltererBlockEntity at {}. Found={}", filterpos, level.getBlockEntity(filterpos) == null ? "null" : level.getBlockEntity(filterpos).getClass().getName());
        }

        controllerBe.setChanged();
        controllerBe.sendData();
    }


    // -------------------------------------------------
    // Network helpers (server only)
    // -------------------------------------------------

    @Nullable
    private NetworkData.Group getNetworkGroup(ServerLevel sl) {
        NetworkData data = NetworkData.get(sl);

        BlockPos endpointPos = getControllerPos();
        BlockPos filtererPos = data.getFiltererForEndpoint(sl.dimension(), endpointPos);
        if (filtererPos == null)
            return null;

        NetworkData.Group g = data.getGroup(sl.dimension(), filtererPos);
        if (g == null)
            return null;

        if (g.monitorEndpoints.isEmpty()) return null;
        if (!g.monitorEndpoints.contains(endpointPos)) return null;

        return g;
    }


    // -------------------------------------------------
    // Cache / radar resolve
    // -------------------------------------------------

    @Nullable
    private RadarTrack resolveActiveTrack() {
        if (selectedEntity == null)
            return null;

        for (RadarTrack track : cachedTracks) {
            if (selectedEntity.equals(track.getId()) || selectedEntity.equals(track.id())) {
                return track;
            }
        }
        return null;
    }

    /** Updates cachedTracks. Server uses real radar tracks; client uses packet-populated cachedTracks. */
    public void updateCacheServerOrClient() {
        if (level == null) return;

        // Client: DO NOT rebuild cachedTracks; it should come from packets.
        if (level.isClientSide) {
            // If radarPos got cleared, clean up selection state.
            if (radarInfos.isEmpty()) {
                cachedTracks = List.of();
                activetrack = null;
                selectedEntity = null;
            }
            return;
        }

        // Server: rebuild and apply filter
        List<IRadar> radars = getRunningRadars();
        if (radars.isEmpty()) {
            cachedTracks = List.of();
            activetrack = null;
            selectedEntity = null;
            return;
        }

        DetectionConfig det = this.filter; // already synced from network (or legacy)
        LinkedHashMap<String, RadarTrack> merged = new LinkedHashMap<>();
        for (IRadar radar : radars) {
            for (RadarTrack track : radar.getTracks()) {
                if (track == null || !det.test(track)) continue;
                String id = track.getId();
                if (id == null || id.isBlank()) id = track.id();
                if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
                merged.merge(id, track.copy(), MonitorBlockEntity::newerTrack);
            }
        }
        if (Mods.SABLE.isLoaded() && level instanceof ServerLevel serverLevel) {
            String networkSecret = monitorNetworkSecret(serverLevel);
            for (RadarTrack track : merged.values()) {
                if (track.trackCategory() == TrackCategory.SABLE) {
                    track.setFriendly(isFriendlySublevel(track, networkSecret));
                    SableSilhouetteServerCache.attachMetadata(serverLevel, track);
                } else {
                    track.setFriendly(false);
                    track.clearSilhouette();
                }
            }
        }
        cachedTracks = List.copyOf(merged.values());

        if (!level.isClientSide) {
            activetrack = resolveActiveTrack();
        }
    }
    public boolean isLinked() {
        return aradLinked || !radarInfos.isEmpty() || getRadarCenterPos() != null;
    }

    public boolean isAradLinked() {
        return aradLinked;
    }

    public List<RwrDisplayInfo> getRwrInfos() {
        return rwrInfos;
    }

    private static RadarTrack newerTrack(RadarTrack first, RadarTrack second) {
        return second.scannedTime() >= first.scannedTime() ? second : first;
    }

    private String monitorNetworkSecret(ServerLevel level) {
        NetworkData.Group group = getNetworkGroup(level);
        if (group == null) {
            return "";
        }
        return normalizeSecret(IdentificationConfig.fromTag(group.identificationTag).label());
    }

    private static boolean isFriendlySublevel(RadarTrack track, String networkSecret) {
        if (networkSecret.isBlank() || !"Sable:ship".equals(track.entityType())) {
            return false;
        }

        try {
            IDManager.IDRecord record = IDManager.getIDRecordByShipId(UUID.fromString(track.id()));
            return record != null && networkSecret.equals(normalizeSecret(record.secretID()));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String normalizeSecret(String secret) {
        return secret == null ? "" : secret.trim().toLowerCase(Locale.ROOT);
    }

    @Nullable
    private RadarTrack resolveActiveTrackFromCache() {
        if (selectedEntity == null) return null;
        for (RadarTrack t : cachedTracks) {
            if (t == null) continue;
            if (selectedEntity.equals(t.getId()) || selectedEntity.equals(t.id()))
                return t;
        }
        return null;
    }

    public Optional<IRadar> getRadar() {
        if (level == null) return Optional.empty();
        if (!isLinked()) return Optional.empty();

        // Client: can't read SavedData, so use synced radarPos
        if (level.isClientSide) {
            if (radarPos == null) return Optional.empty();

            if (radar instanceof BlockEntity be
                    && be.getBlockPos().equals(radarPos)) {
                return Optional.of(radar);
            }

            radar = null;
            if (level.getBlockEntity(radarPos) instanceof IRadar r) radar = r;
            return Optional.ofNullable(radar);
        }

        // Server: radarPos will be set from network (or legacy) already
        if (radarPos == null) {
            radar = null;
            return Optional.empty();
        }

        if (radar instanceof BlockEntity be
                && be.getBlockPos().equals(radarPos)) {
            return Optional.of(radar);
        }

        radar = null;
        if (level.getBlockEntity(radarPos) instanceof IRadar r) {
            radar = r;
        }
        return Optional.ofNullable(radar);
    }

    public List<IRadar> getRadars() {
        if (level == null) return List.of();
        List<IRadar> radars = new ArrayList<>();
        for (RadarDisplayInfo info : radarInfos) {
            IRadar radar = resolveRadar(info.pos());
            if (radar != null) radars.add(radar);
        }
        return List.copyOf(radars);
    }

    private List<IRadar> getRunningRadars() {
        List<IRadar> radars = new ArrayList<>();
        for (IRadar radar : getRadars()) {
            if (radar.isRunning()) radars.add(radar);
        }
        return radars;
    }

    private @Nullable IRadar resolveRadar(BlockPos pos) {
        if (level == null || pos == null) return null;

        IRadar cached = radarCache.get(pos);
        if (cached instanceof BlockEntity be && be.getBlockPos().equals(pos)) {
            return cached;
        }

        if (level.getBlockEntity(pos) instanceof IRadar radar) {
            radarCache.put(pos, radar);
            return radar;
        }

        radarCache.remove(pos);
        return null;
    }

    public List<RadarDisplayInfo> getRadarInfos() {
        return radarInfos;
    }

    public List<RadarDisplayInfo> getRunningRadarInfos() {
        if (radarInfos.isEmpty()) return List.of();
        List<RadarDisplayInfo> running = new ArrayList<>();
        for (RadarDisplayInfo info : radarInfos) {
            if (shouldDisplayRadarInfo(info)) running.add(info);
        }
        return List.copyOf(running);
    }

    private boolean shouldDisplayRadarInfo(RadarDisplayInfo info) {
        if (info.running()) {
            return true;
        }
        if (!"sky".equals(info.type())) {
            return false;
        }
        IRadar radar = resolveRadar(info.pos());
        return radar instanceof SkyRadarBlockEntity skyRadar && skyRadar.isAssembled();
    }

    // Basics

    public BlockPos getControllerPos() {

        if (controller == null) return worldPosition;
        return controller;
    }


    public int getSize() {
        return radius;
    }

    public void setControllerPos(BlockPos newController, int size) {
        if (level instanceof ServerLevel sl) {
            BlockPos oldController = this.controller == null ? worldPosition : this.controller;
            NetworkData data = NetworkData.get(sl);
            data.retargetEndpoint(sl.dimension(), oldController, newController);
        }

        this.controller = newController;
        this.radius = size;
        setChanged();
        sendData();
    }


    public boolean isController() {
        return worldPosition.equals(getControllerPos());
    }


    public MonitorBlockEntity getController() {
        if (isController()) return this;
        if (level != null && level.getBlockEntity(controller) instanceof MonitorBlockEntity controllerBe)
            return controllerBe;
        return this;
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return super.createRenderBoundingBox().inflate(10);
    }

    public Collection<RadarTrack> getTracks() {
        return cachedTracks;
    }

    public float getRange() {
        float range = 0f;
        for (RadarDisplayInfo info : radarInfos) {
            range = Math.max(range, info.range());
        }
        return range;
    }

    @Nullable
    public Vec3 getRadarCenterPos() {
        if (radarPos == null || level == null) return null;
        return PhysicsHandler.getWorldVec(level, radarPos);
    }


    // Targeting


    public Vec3 getTargetPos(TargetingConfig targetingConfig) {
        AtomicReference<Vec3> targetPos = new AtomicReference<>();

        if (selectedEntity != null) {
            targetPos.set(AutoTargetingHelper.resolveSelectedTargetPos(selectedEntity, getController().cachedTracks));
        }

        if (targetPos.get() == null)
            selectedEntity = null;
        else if (AutoTargetingHelper.isInSafeZone(targetPos.get(), safeZones))
            return null;

        return targetPos.get();
    }

    // Safe zones


    public boolean isInSafeZone(Vec3 pos) {
        return AutoTargetingHelper.isInSafeZone(pos, safeZones);
    }

    public void addSafeZone(BlockPos startPos, BlockPos endPos) {
        double minX = Math.min(startPos.getX(), endPos.getX());
        double minY = Math.min(startPos.getY(), endPos.getY());
        double minZ = Math.min(startPos.getZ(), endPos.getZ());
        double maxX = Math.max(startPos.getX(), endPos.getX()) + 1;
        double maxY = Math.max(startPos.getY(), endPos.getY()) + 1;
        double maxZ = Math.max(startPos.getZ(), endPos.getZ()) + 1;

        getController().safeZones.add(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
    }

    public void showSafeZone() {
        if (level == null || !level.isClientSide) {
            return;
        }

        Client.showSafeZone(this);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class Client {
        static void showSafeZone(MonitorBlockEntity be) {
            for (AABB safeZone : be.safeZones) {
                net.createmod.catnip.outliner.Outliner.getInstance().showAABB(safeZone, safeZone)
                        .colored(0x383b42)
                        .withFaceTextures(com.simibubi.create.AllSpecialTextures.CHECKERED, com.simibubi.create.AllSpecialTextures.HIGHLIGHT_CHECKERED)
                        .lineWidth(1 / 16f);
            }
        }
    }

    public boolean tryRemoveAABB(BlockPos pos) {
        return safeZones.removeIf(safeZone -> safeZone.contains(Vec3.atCenterOf(pos)));
    }

    // -------------------------------------------------
    // NBT sync

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);

        if (tag.contains("Controller")) {
            controller = NbtUtils.readBlockPos(tag, "Controller").orElse(null);
        }

        // if the packet explicitly says "no radar", i clear the cached radarPos
        if (clientPacket && tag.contains("HasRadarPos", Tag.TAG_BYTE) && !tag.getBoolean("HasRadarPos")) {
            radarPos = null;
            radar = null;
            radarCache.clear();
            radarInfos = List.of();
        } else if (tag.contains("radarPos")) {
            radarPos = NbtUtils.readBlockPos(tag, "radarPos").orElse(null);
        }

        if (clientPacket && tag.contains("Radars", Tag.TAG_LIST)) {
            radarInfos = readRadarInfos(tag.getList("Radars", Tag.TAG_COMPOUND));
            radarPos = radarInfos.isEmpty() ? null : radarInfos.get(0).pos();
            radar = null;
            radarCache.clear();
        }

        if (clientPacket && tag.contains("RwrContacts", Tag.TAG_LIST)) {
            rwrInfos = readRwrInfos(tag.getList("RwrContacts", Tag.TAG_COMPOUND));
        }

        selectedRwrSource = tag.contains("SelectedRwrSource", Tag.TAG_STRING)
                ? tag.getString("SelectedRwrSource")
                : null;
        if (!clientPacket) {
            selectedRwrRadarPos = NbtUtils.readBlockPos(tag, "SelectedRwrRadarPos").orElse(null);
            selectedRwrPos = NbtUtils.readBlockPos(tag, "SelectedRwrPos").orElse(null);
        }

        selectedEntity = tag.contains("SelectedEntity", Tag.TAG_STRING)
                ? tag.getString("SelectedEntity")
                : null;

        aradLinked = tag.getBoolean("AradLinked");

        hoveredEntity = tag.contains("HoveredEntity", Tag.TAG_STRING)
                ? tag.getString("HoveredEntity")
                : null;

        filter = tag.contains("Filter", Tag.TAG_COMPOUND)
                ? DetectionConfig.fromTag(tag.getCompound("Filter"))
                : DetectionConfig.DEFAULT;

        radius = tag.contains("Size", Tag.TAG_INT)
                ? tag.getInt("Size")
                : 1;

        if (clientPacket && tag.contains("tracks", Tag.TAG_COMPOUND)) {
            cachedTracks = RadarTrackUtil.deserializeListNBT(tag.getCompound("tracks"));
        }

        readSafeZones(tag);
    }


    private void readSafeZones(CompoundTag tag) {
        safeZones.clear(); // IMPORTANT: avoid duplicates on every packet
        ListTag safeZonesTag = tag.getList("SafeZones", Tag.TAG_COMPOUND);

        for (int i = 0; i < safeZonesTag.size(); i++) {
            CompoundTag safeZoneTag = safeZonesTag.getCompound(i);
            AABB safeZone = new AABB(
                    safeZoneTag.getDouble("minX"),
                    safeZoneTag.getDouble("minY"),
                    safeZoneTag.getDouble("minZ"),
                    safeZoneTag.getDouble("maxX"),
                    safeZoneTag.getDouble("maxY"),
                    safeZoneTag.getDouble("maxZ")
            );
            safeZones.add(safeZone);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries,clientPacket);

        if (controller != null)
            tag.put("Controller", NbtUtils.writeBlockPos(controller));

        if (selectedEntity != null) tag.putString("SelectedEntity", selectedEntity);
        if (hoveredEntity != null) tag.putString("HoveredEntity", hoveredEntity);
        if (selectedRwrSource != null) tag.putString("SelectedRwrSource", selectedRwrSource);
        tag.putBoolean("AradLinked", aradLinked);

        tag.putInt("Size", radius);

        if (clientPacket) {
            tag.putBoolean("HasRadarPos", radarPos != null);

            if (radarPos != null)
                tag.put("radarPos", NbtUtils.writeBlockPos(radarPos));

            tag.put("Radars", writeRadarInfos());
            tag.put("RwrContacts", writeRwrInfos());

            tag.put("Filter", filter.toTag());
            tag.put("tracks", RadarTrackUtil.serializeNBTList(cachedTracks));
        } else {
            if (selectedRwrRadarPos != null) {
                tag.put("SelectedRwrRadarPos", NbtUtils.writeBlockPos(selectedRwrRadarPos));
            }
            if (selectedRwrPos != null) {
                tag.put("SelectedRwrPos", NbtUtils.writeBlockPos(selectedRwrPos));
            }
            if (level instanceof ServerLevel slevel) {
                if (getNetworkGroup(slevel) == null) {
                    if (radarPos != null)
                        tag.put("radarPos", NbtUtils.writeBlockPos(radarPos));
                    tag.put("Filter", filter.toTag());
                }
            }
        }

        tag.put("SafeZones", saveSafeZones());
    }

    private ListTag writeRadarInfos() {
        ListTag list = new ListTag();
        for (RadarDisplayInfo info : radarInfos) {
            CompoundTag tag = new CompoundTag();
            tag.put("Pos", NbtUtils.writeBlockPos(info.pos()));
            tag.putDouble("CenterX", info.center().x);
            tag.putDouble("CenterY", info.center().y);
            tag.putDouble("CenterZ", info.center().z);
            tag.putFloat("Range", info.range());
            tag.putBoolean("Running", info.running());
            tag.putString("Type", info.type() == null ? "" : info.type());
            tag.putFloat("GlobalAngle", info.globalAngle());
            tag.putFloat("AngularSpeed", info.angularSpeed());
            tag.putLong("AngleSnapshotTime", info.angleSnapshotTime());
            tag.putFloat("FovDegrees", info.fovDegrees());
            if (info.direction() != null) tag.putString("Direction", info.direction().getName());
            tag.putBoolean("RenderRelative", info.renderRelativeToMonitor());
            if (info.ownedLockedTargetId() != null && info.ownedLockedTargetPos() != null) {
                tag.putString("OwnedLockedTargetId", info.ownedLockedTargetId());
                tag.putDouble("OwnedTargetX", info.ownedLockedTargetPos().x);
                tag.putDouble("OwnedTargetY", info.ownedLockedTargetPos().y);
                tag.putDouble("OwnedTargetZ", info.ownedLockedTargetPos().z);
            }
            list.add(tag);
        }
        return list;
    }

    private List<RadarDisplayInfo> readRadarInfos(ListTag list) {
        List<RadarDisplayInfo> infos = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            BlockPos pos = NbtUtils.readBlockPos(tag, "Pos").orElse(null);
            if (pos == null) continue;
            net.minecraft.core.Direction direction = null;
            if (tag.contains("Direction", Tag.TAG_STRING)) {
                direction = net.minecraft.core.Direction.byName(tag.getString("Direction"));
            }
            infos.add(new RadarDisplayInfo(
                    pos,
                    new Vec3(tag.getDouble("CenterX"), tag.getDouble("CenterY"), tag.getDouble("CenterZ")),
                    tag.getFloat("Range"),
                    tag.getBoolean("Running"),
                    tag.getString("Type"),
                    tag.getFloat("GlobalAngle"),
                    tag.getFloat("AngularSpeed"),
                    tag.getLong("AngleSnapshotTime"),
                    tag.contains("FovDegrees", Tag.TAG_FLOAT) ? tag.getFloat("FovDegrees") : 360.0F,
                    direction,
                    tag.getBoolean("RenderRelative"),
                    tag.contains("OwnedLockedTargetId", Tag.TAG_STRING) ? tag.getString("OwnedLockedTargetId") : null,
                    tag.contains("OwnedTargetX", Tag.TAG_DOUBLE)
                            ? new Vec3(tag.getDouble("OwnedTargetX"), tag.getDouble("OwnedTargetY"), tag.getDouble("OwnedTargetZ"))
                            : null
            ));
        }
        return List.copyOf(infos);
    }

    private ListTag writeRwrInfos() {
        ListTag list = new ListTag();
        for (RwrDisplayInfo info : rwrInfos) {
            CompoundTag tag = new CompoundTag();
            tag.putString("SourceId", info.sourceId() == null ? "" : info.sourceId());
            tag.putString("RadarType", info.radarType().name());
            tag.putFloat("BearingDegrees", info.bearingDegrees());
            tag.putFloat("RadiusOffset", info.radiusOffset());
            tag.putBoolean("WithinRadarRange", info.withinRadarRange());
            tag.putBoolean("ExactLocked", info.exactLocked());
            tag.putBoolean("PrimaryThreat", info.primaryThreat());
            tag.putBoolean("Engaged", info.engaged());
            tag.putBoolean("Friendly", info.friendly());
            list.add(tag);
        }
        return list;
    }

    private List<RwrDisplayInfo> readRwrInfos(ListTag list) {
        List<RwrDisplayInfo> infos = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            RadarType radarType;
            try {
                radarType = RadarType.valueOf(tag.getString("RadarType"));
            } catch (IllegalArgumentException ignored) {
                radarType = RadarType.GROUND;
            }
            infos.add(new RwrDisplayInfo(
                    tag.getString("SourceId"),
                    radarType,
                    tag.getFloat("BearingDegrees"),
                    tag.getFloat("RadiusOffset"),
                    tag.getBoolean("WithinRadarRange"),
                    tag.getBoolean("ExactLocked"),
                    tag.getBoolean("PrimaryThreat"),
                    tag.getBoolean("Engaged"),
                    tag.getBoolean("Friendly")
            ));
        }
        return List.copyOf(infos);
    }


    public SubLevelAccess getShip(){
        return SableUtils.getShipManagingPos(level, worldPosition);
    }



    private @NotNull ListTag saveSafeZones() {
        ListTag safeZonesTag = new ListTag();
        for (AABB safeZone : safeZones) {
            CompoundTag safeZoneTag = new CompoundTag();
            safeZoneTag.putDouble("minX", safeZone.minX);
            safeZoneTag.putDouble("minY", safeZone.minY);
            safeZoneTag.putDouble("minZ", safeZone.minZ);
            safeZoneTag.putDouble("maxX", safeZone.maxX);
            safeZoneTag.putDouble("maxY", safeZone.maxY);
            safeZoneTag.putDouble("maxZ", safeZone.maxZ);
            safeZonesTag.add(safeZoneTag);
        }
        return safeZonesTag;
    }

    public String getHoveredEntity() { return hoveredEntity; }
    public String getSelectedEntity() { return selectedEntity; }

    public @Nullable String getHoveredRwrSource() {
        return hoveredRwrSource;
    }

    public void setHoveredRwrSource(@Nullable String sourceId) {
        hoveredRwrSource = sourceId;
    }

    public @Nullable String getSelectedRwrSource() {
        return selectedRwrSource;
    }

    @Nullable BlockPos getSelectedRwrRadarPos() {
        return selectedRwrRadarPos;
    }

    @Nullable BlockPos getSelectedRwrPos() {
        return selectedRwrPos;
    }

    void setRwrSelectionState(String sourceId, BlockPos radarPos, BlockPos rwrPos) {
        selectedRwrSource = sourceId;
        selectedRwrRadarPos = radarPos.immutable();
        selectedRwrPos = rwrPos.immutable();
    }

    void clearRwrSelectionState() {
        selectedRwrSource = null;
        selectedRwrRadarPos = null;
        selectedRwrPos = null;
    }
}
