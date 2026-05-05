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
import com.happysg.radar.compat.PhysicsHandler;
import com.happysg.radar.block.behavior.networks.config.AutoTargetingHelper;
import com.mojang.logging.LogUtils;
import com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
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
    protected @Nullable net.minecraft.resources.ResourceLocation radarDim;

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
    public final List<com.happysg.radar.block.behavior.networks.config.SafeZone> safeZones = new ArrayList<>();

    public MonitorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void initialize() {
        super.initialize();
        this.lastKnownPos = worldPosition;
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


        if (!level.isClientSide && level instanceof ServerLevel sl) {
            syncFromNetwork(sl);
            updateCacheServerOrClient();
            
            // keep controller's displayed selection consistent with network
            MonitorBlockEntity controllerBe = getController();
            if (controllerBe != null) {
                controllerBe.activetrack = controllerBe.resolveActiveTrackFromCache();
            }

            sendData();
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
        if (g == null) return;


        BlockPos netRadar = g.radarPos;
        if (!Objects.equals(netRadar, radarPos)) {
            radarPos = netRadar;
            radar = null;
        }

        if (!Objects.equals(g.radarDim, radarDim)) {
            radarDim = g.radarDim;
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
        if (track != null && track.trackCategory() == TrackCategory.VS2 && "VS2:ship".equals(track.entityType())) {
            long shipId = 0;
            try {
                shipId = Long.parseLong(track.id());
            } catch (NumberFormatException ignored) {
                track = null;
            }

            if (track != null) {
                if (PhysicsHandler.isShipAlive(sl, track.id())) {
                    reset = false;
                } else {
                    track = null;
                    this.activetrack = null;
                    this.selectedEntity = null;
                    reset = true;
                }
            }
        }



        NetworkData.Group g = controllerBe.getNetworkGroup(sl);
        if (g == null)
            return;




        BlockPos filterpos = g.key.filtererPos();
        if (level.getBlockEntity(filterpos) instanceof NetworkFiltererBlockEntity filtererBe) {
            filtererBe.receiveSelectedTargetFromMonitor(track, safeZones);
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
                radarDim = null;
            }
            return;
        }

        // Server: rebuild and apply filter
        Optional<IRadar> r = getRadar();
        if (r.isEmpty()) {
            cachedTracks = List.of();
            activetrack = null;
            selectedEntity = null;
            cachedRadarRunning = false;
            cachedRadarCenter = null;
            return;
        }

        IRadar radar = r.get();
        DetectionConfig det = this.filter; // already synced from network (or legacy)
        cachedTracks = radar.getTracks().stream().filter(det::test).toList();
        
        cachedRadarRange = radar.getRange();
        cachedRadarAngle = radar.getGlobalAngle();
        cachedRadarSpeed = radar.getSpeed();
        cachedRadarRunning = radar.isRunning();
        cachedRadarRenderRelative = radar.renderRelativeToMonitor();
        cachedRadarCenter = radar.getRadarCenterPos(1.0f);

        if (!level.isClientSide) {
            activetrack = resolveActiveTrack();
        }
    }
    public boolean isLinked() {
        return radarPos != null;
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

    public float cachedRadarRange = 0f;
    public float cachedRadarAngle = 0f;
    public float cachedRadarSpeed = 0f;
    public boolean cachedRadarRunning = false;
    public boolean cachedRadarRenderRelative = true;
    public Vec3 cachedRadarCenter = null;

    public float getStabilizedAngle(float partialTicks) {
        return cachedRadarAngle;
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
        } else {
            // Radar may be inside a Sable sub-level (ship) while monitor is in the parent world
            radar = com.happysg.radar.compat.PhysicsHandler.getRadarInSubLevel(radarDim, radarPos);
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

    public Vec3 getRadarCenterPos(float partialTicks) {
        if (radarPos == null || level == null) return null;
        return getRadar().map(radar -> radar.getRadarCenterPos(partialTicks)).orElseGet(() -> PhysicsHandler.getWorldVec(level, radarPos));
    }

    public Vec3 getRadarCenterPos() {
        return getRadarCenterPos(1.0f);
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
        else if (isInSafeZone(targetPos.get()))
            return null;

        return targetPos.get();
    }

    // Safe zones


    public boolean isInSafeZone(Vec3 pos) {
        if (level == null) return false;
        for (com.happysg.radar.block.behavior.networks.config.SafeZone zone : safeZones) {
            if (zone.contains(pos, level)) return true;
        }
        return false;
    }

    public void addSafeZone(BlockPos startPos, BlockPos endPos) {
        double minX = Math.min(startPos.getX(), endPos.getX());
        double minY = Math.min(startPos.getY(), endPos.getY());
        double minZ = Math.min(startPos.getZ(), endPos.getZ());
        double maxX = Math.max(startPos.getX(), endPos.getX()) + 1;
        double maxY = Math.max(startPos.getY(), endPos.getY()) + 1;
        double maxZ = Math.max(startPos.getZ(), endPos.getZ()) + 1;

        UUID subLevelId = null;
        if (Mods.SABLE.isLoaded()) {
            var subLevel = com.happysg.radar.compat.aeronautics.SableUtils.getSubLevelManagingPos(level, startPos);
            if (subLevel != null) {
                subLevelId = subLevel.getUniqueId();
            }
        }

        getController().safeZones.add(new com.happysg.radar.block.behavior.networks.config.SafeZone(new AABB(minX, minY, minZ, maxX, maxY, maxZ), subLevelId));
    }

    public void showSafeZone() {
        com.happysg.radar.compat.stub.DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Client.showSafeZone(this));
    }

    @OnlyIn(Dist.CLIENT)
    private static final class Client {
        static void showSafeZone(MonitorBlockEntity be) {
            for (com.happysg.radar.block.behavior.networks.config.SafeZone safeZone : be.safeZones) {
                AABB aabb = safeZone.aabb();
                if (safeZone.subLevelId() != null && be.level != null) {
                     aabb = com.happysg.radar.compat.aeronautics.SableUtils.getWorldAABB(be.level, aabb, safeZone.subLevelId());
                }

                net.createmod.catnip.outliner.Outliner.getInstance().showAABB(aabb, aabb)
                        .colored(0x383b42)
                        .withFaceTextures(com.simibubi.create.AllSpecialTextures.CHECKERED, com.simibubi.create.AllSpecialTextures.HIGHLIGHT_CHECKERED)
                        .lineWidth(1 / 16f);
            }
        }
    }

    public boolean tryRemoveAABB(BlockPos pos) {
        return safeZones.removeIf(safeZone -> safeZone.aabb().contains(Vec3.atCenterOf(pos)));
    }

    // -------------------------------------------------
    // NBT sync

    @Override
    protected void read(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);

        if (tag.contains("Controller"))
            controller = net.minecraft.nbt.NbtUtils.readBlockPos(tag, "Controller").orElse(null);

        // if the packet explicitly says "no radar", i clear the cached radarPos
        if (clientPacket && tag.contains("HasRadarPos") && !tag.getBoolean("HasRadarPos")) {
            radarPos = null;
            radar = null;
        } else if (tag.contains("radarPos")) {
            radarPos = net.minecraft.nbt.NbtUtils.readBlockPos(tag, "radarPos").orElse(null);
            if (tag.contains("radarDim")) radarDim = net.minecraft.resources.ResourceLocation.parse(tag.getString("radarDim"));
        }

        selectedEntity = tag.contains("SelectedEntity", Tag.TAG_STRING) ? tag.getString("SelectedEntity") : null;
        hoveredEntity  = tag.contains("HoveredEntity", Tag.TAG_STRING) ? tag.getString("HoveredEntity") : null;

        if (tag.contains("Filter", Tag.TAG_COMPOUND))
            filter = DetectionConfig.fromTag(tag.getCompound("Filter"));
        else
            filter = DetectionConfig.DEFAULT;

        radius = tag.contains("Size", Tag.TAG_INT) ? tag.getInt("Size") : 1;

        if (clientPacket && tag.contains("tracks", Tag.TAG_COMPOUND)) {
            cachedTracks = RadarTrackUtil.deserializeListNBT(tag.getCompound("tracks"));
            cachedRadarRange = tag.getFloat("cachedRadarRange");
            cachedRadarAngle = tag.getFloat("cachedRadarAngle");
            cachedRadarSpeed = tag.getFloat("cachedRadarSpeed");
            cachedRadarRunning = tag.getBoolean("cachedRadarRunning");
            cachedRadarRenderRelative = tag.getBoolean("cachedRadarRenderRelative");
            if (tag.contains("cachedRadarCenterX")) {
                cachedRadarCenter = new Vec3(tag.getDouble("cachedRadarCenterX"), tag.getDouble("cachedRadarCenterY"), tag.getDouble("cachedRadarCenterZ"));
            } else {
                cachedRadarCenter = null;
            }
        }

        readSafeZones(tag);
    }


    private void readSafeZones(CompoundTag tag) {
        safeZones.clear(); // IMPORTANT: avoid duplicates on every packet
        ListTag safeZonesTag = tag.getList("SafeZones", Tag.TAG_COMPOUND);

        for (int i = 0; i < safeZonesTag.size(); i++) {
            safeZones.add(com.happysg.radar.block.behavior.networks.config.SafeZone.fromTag(safeZonesTag.getCompound(i)));
        }
    }

    @Override
    protected void write(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);

        if (controller != null)
            tag.put("Controller", NbtUtils.writeBlockPos(controller));

        if (selectedEntity != null) tag.putString("SelectedEntity", selectedEntity);
        if (hoveredEntity != null) tag.putString("HoveredEntity", hoveredEntity);

        tag.putInt("Size", radius);

        if (clientPacket) {
            tag.putBoolean("HasRadarPos", radarPos != null);

            if (radarPos != null) {
                tag.put("radarPos", NbtUtils.writeBlockPos(radarPos));
                if (radarDim != null) tag.putString("radarDim", radarDim.toString());
            }

            tag.put("Filter", filter.toTag());
            tag.put("tracks", RadarTrackUtil.serializeNBTList(cachedTracks));
            tag.putFloat("cachedRadarRange", cachedRadarRange);
            tag.putFloat("cachedRadarAngle", cachedRadarAngle);
            tag.putFloat("cachedRadarSpeed", cachedRadarSpeed);
            tag.putBoolean("cachedRadarRunning", cachedRadarRunning);
            tag.putBoolean("cachedRadarRenderRelative", cachedRadarRenderRelative);
            if (cachedRadarCenter != null) {
                tag.putDouble("cachedRadarCenterX", cachedRadarCenter.x);
                tag.putDouble("cachedRadarCenterY", cachedRadarCenter.y);
                tag.putDouble("cachedRadarCenterZ", cachedRadarCenter.z);
            }
        } else {
            if (level instanceof ServerLevel slevel) {
                if (getNetworkGroup(slevel) == null) {
                    if (radarPos != null) {
                        tag.put("radarPos", NbtUtils.writeBlockPos(radarPos));
                        if (radarDim != null) tag.putString("radarDim", radarDim.toString());
                    }
                    tag.put("Filter", filter.toTag());
                }
            }
        }

        tag.put("SafeZones", saveSafeZones());
    }


    public Object getShip(){
        return PhysicsHandler.getShipManagingPos(level, worldPosition);
    }



    private @NotNull ListTag saveSafeZones() {
        ListTag safeZonesTag = new ListTag();
        for (com.happysg.radar.block.behavior.networks.config.SafeZone safeZone : safeZones) {
            safeZonesTag.add(safeZone.toTag());
        }
        return safeZonesTag;
    }

    public String getHoveredEntity() { return hoveredEntity; }
    public String getSelectedEntity() { return selectedEntity; }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        write(tag, provider, true);
        return tag;
    }

    public float getShipYawDeg() {
        return PhysicsHandler.getShipYawDeg(this);
    }
}
