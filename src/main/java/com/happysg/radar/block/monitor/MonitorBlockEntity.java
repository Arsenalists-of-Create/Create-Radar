package com.happysg.radar.block.monitor;

import com.happysg.radar.block.behavior.networks.INetworkNode;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import com.happysg.radar.block.behavior.networks.config.TargetingConfig;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.RadarTrackUtil;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.Mods;
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
    protected String hoveredEntity;
    public String selectedEntity;
    public RadarTrack activetrack;
    boolean reset = false;
    protected BlockPos mountBlock;

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
                updateCacheServerOrClient();

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
        // clear any cached network state
        this.activetrack = null;
        this.radarPos = null;
        this.radar = null;
        this.controller = null;


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



    private void syncFromNetwork(ServerLevel sl) {
        NetworkData.Group g = getNetworkGroup(sl);
        if (g == null) {
            return;
        }


        BlockPos netRadar = g.radarPos;
        if (!Objects.equals(netRadar, radarPos)) {
            radarPos = netRadar;
            radar = null;
        }

        filter = DetectionConfig.fromTag(g.detectionTag);
        selectedEntity = g.selectedTargetId;
    }

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

        //NetworkData data = NetworkData.get(sl);
        //data.setSelectedTargetId(g, track == null ? null : track.getId());

        // // Update monitor-side state
//        if (track == null) {
//            controllerBe.selectedEntity = null;
//            controllerBe.activetrack = null;
//        } else {
//            controllerBe.selectedEntity = track.getId();
//            controllerBe.activetrack = track;
//        }



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
            if (radarPos == null) {
                cachedTracks = List.of();
                activetrack = null;
                selectedEntity = null;
            }
            return;
        }

        // Server: rebuild and apply filter
        Optional<IRadar> r = getRadar();
        if (r.isEmpty()) {
            cachedTracks = List.of();
            activetrack = null;
            selectedEntity = null;
            return;
        }

        IRadar radar = r.get();
        DetectionConfig det = this.filter; // already synced from network (or legacy)
        cachedTracks = radar.getTracks().stream().filter(det::test).toList();

        if (!level.isClientSide) {
            activetrack = resolveActiveTrack();
        }
    }
    public boolean isLinked() {
        return getRadarCenterPos() != null;
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
        return getRadar().map(IRadar::getRange).orElse(0f);
    }

    @Nullable
    public Vec3 getRadarCenterPos() {
        if (radarPos == null || level == null) return null;
        return PhysicsHandler.getWorldVec(level, radarPos);
    }


    // Targeting


    public Vec3 getTargetPos(TargetingConfig targetingConfig) {
        AtomicReference<Vec3> targetPos = new AtomicReference<>();

        getRadar().ifPresent(radar -> {
            if (selectedEntity == null)
                return;

            targetPos.set(AutoTargetingHelper.resolveSelectedTargetPos(selectedEntity, getController().cachedTracks));
        });

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
        } else if (tag.contains("radarPos")) {
            radarPos = NbtUtils.readBlockPos(tag, "radarPos").orElse(null);
        }

        selectedEntity = tag.contains("SelectedEntity", Tag.TAG_STRING)
                ? tag.getString("SelectedEntity")
                : null;

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

        tag.putInt("Size", radius);

        if (clientPacket) {
            tag.putBoolean("HasRadarPos", radarPos != null);

            if (radarPos != null)
                tag.put("radarPos", NbtUtils.writeBlockPos(radarPos));

            tag.put("Filter", filter.toTag());
            tag.put("tracks", RadarTrackUtil.serializeNBTList(cachedTracks));
        } else {
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
}
