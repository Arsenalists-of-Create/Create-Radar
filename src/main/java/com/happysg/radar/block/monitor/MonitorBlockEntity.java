package com.happysg.radar.block.monitor;

import com.happysg.radar.block.datalink.screens.TargetingConfig;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.RadarTrackUtil;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.simibubi.create.AllSpecialTextures;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.equipment.goggles.IHaveHoveringInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;


public class MonitorBlockEntity extends SmartBlockEntity implements IHaveHoveringInformation {

    protected BlockPos controller;
    protected int radius = 1;
    private int ticksSinceLastUpdate = 0;
    protected BlockPos radarPos;
    IRadar radar;
    protected String hoveredEntity;
    protected String selectedEntity;
    public long timeOfLastSelect = 0;

    Collection<RadarTrack> cachedTracks = List.of();
    MonitorFilter filter = MonitorFilter.DEFAULT;
    public List<AABB> safeZones = new ArrayList<>();

    public MonitorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void initialize() {
        super.initialize();
        updateCache();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public void updateCache() {
        getRadar().ifPresent(radar -> cachedTracks = radar.getTracks().stream().filter(filter::test).toList());
    }

    public BlockPos getControllerPos() {
        if (controller == null)
            return getBlockPos();
        return controller;
    }

    public int getSize() {
        return radius;
    }

    @Override
    public void tick() {
        super.tick();

        if (!level.isClientSide) {
            if (level.getGameTime() % 20 == 0) {
                updateCache();
                sendData();
            }
        }

        if (ticksSinceLastUpdate > 20)
            setRadarPos(null);

        ticksSinceLastUpdate++;
    }

    public void setControllerPos(BlockPos pPos, int size) {
        controller = pPos;
        radius = size;
        notifyUpdate();
    }

    public void setRadarPos(BlockPos pPos) {
        if (level.isClientSide())
            return;

        if (level.getBlockEntity(getControllerPos()) instanceof MonitorBlockEntity monitor) {
            if (pPos == null) {
                monitor.radarPos = null;
                monitor.radar = null;
                monitor.notifyUpdate();
                return;
            }
            monitor.radarPos = pPos;
            monitor.ticksSinceLastUpdate = 0;
            monitor.notifyUpdate();
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        controller = null;
        radarPos = null;
        radar = null;

        super.read(tag, clientPacket);
        if (tag.contains("Controller"))
            controller = NbtUtils.readBlockPos(tag.getCompound("Controller"));
        if (tag.contains("radarPos"))
            radarPos = NbtUtils.readBlockPos(tag.getCompound("radarPos"));
        if (tag.contains("SelectedEntity"))
            selectedEntity = tag.getString("SelectedEntity");
        else
            selectedEntity = null;
        if (tag.contains("HoveredEntity"))
            hoveredEntity = tag.getString("HoveredEntity");
        else
            hoveredEntity = null;
        filter = MonitorFilter.fromTag(tag.getCompound("Filter"));
        radius = tag.getInt("Size");
        if (clientPacket)
            cachedTracks = RadarTrackUtil.deserializeListNBT(tag.getCompound("tracks"));

        readSafeZones(tag);
    }

    private void readSafeZones(CompoundTag tag) {
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
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        if (controller != null)
            tag.put("Controller", NbtUtils.writeBlockPos(controller));
        if (radarPos != null)
            tag.put("radarPos", NbtUtils.writeBlockPos(radarPos));
        if (selectedEntity != null)
            tag.putString("SelectedEntity", selectedEntity);
        if (hoveredEntity != null)
            tag.putString("HoveredEntity", hoveredEntity);
        tag.put("Filter", filter.toTag());
        tag.putInt("Size", radius);
        if (clientPacket)
            tag.put("tracks", RadarTrackUtil.serializeNBTList(cachedTracks));
        tag.put("SafeZones", saveSafeZones());
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

    public boolean isController() {
        return getBlockPos().equals(controller) || controller == null;
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return super.createRenderBoundingBox().inflate(10);
    }

    public Optional<IRadar> getRadar() {
        if (radar != null)
            return Optional.of(radar);
        if (radarPos == null)
            return Optional.empty();
        if (level.getBlockEntity(radarPos) instanceof IRadar radar) {
            this.radar = radar;
        }
        return Optional.ofNullable(radar);
    }

    public MonitorBlockEntity getController() {
        if (isController())
            return this;
        if (level.getBlockEntity(controller) instanceof MonitorBlockEntity controller)
            return controller;
        return this;
    }

    public Vec3 getTargetPos(TargetingConfig targetingConfig) {
        AtomicReference<Vec3> targetPos = new AtomicReference<>();
        getRadar().ifPresent(
                radar -> {
                    if (selectedEntity == null)
                        tryFindAutoTarget(targetingConfig);
                    if (selectedEntity == null)
                        return;
                    for (RadarTrack track : getController().cachedTracks) {
                        if (track.id().equals(selectedEntity))
                            targetPos.set(track.position());
                    }

                }
        );
        if (targetPos.get() == null)
            selectedEntity = null;
        else if (isInSafeZone(targetPos.get()))
            return null;

        return targetPos.get();
    }

    private void tryFindAutoTarget(TargetingConfig targetingConfig) {
        if (!targetingConfig.autoTarget())
            return;
        final double[] distance = {Double.MAX_VALUE};
        getRadar().ifPresent(
                radar -> {
                    for (RadarTrack track : getController().cachedTracks) {
                        if (targetingConfig.test(track.trackCategory()) &&
                                track.position().distanceTo(Vec3.atCenterOf(getControllerPos())) < distance[0]
                                && !isInSafeZone(track.position())
                        ) {
                            selectedEntity = track.id();
                            distance[0] = track.position().distanceTo(Vec3.atCenterOf(getControllerPos()));
                        }
                    }

                }
        );
        if (selectedEntity != null)
            notifyUpdate();
    }

    public void setFilter(MonitorFilter filter) {
        this.getController().filter = filter;
        this.filter = filter;
    }

    public Collection<RadarTrack> getTracks() {
        return cachedTracks;
    }

    @Nullable
    public Vec3 getRadarCenterPos() {
        if (radarPos == null)
            return null;
        return PhysicsHandler.getWorldVec(level, radarPos);
    }

    public float getRange() {
        return getRadar().map(IRadar::getRange).orElse(0f);
    }

    public boolean isInSafeZone(Vec3 pos) {
        for (AABB safeZone : safeZones) {
            if (safeZone.contains(pos))
                return true;
        }
        return false;
    }

    public void addSafeZone(BlockPos startPos, BlockPos endPos) {
        double minX = Math.min(startPos.getX(), endPos.getX());
        double minY = Math.min(startPos.getY(), endPos.getY());
        double minZ = Math.min(startPos.getZ(), endPos.getZ());
        double maxX = Math.max(startPos.getX(), endPos.getX());
        double maxY = Math.max(startPos.getY(), endPos.getY());
        double maxZ = Math.max(startPos.getZ(), endPos.getZ());
        maxX += 1;
        maxY += 1;
        maxZ += 1;
        getController().safeZones.add(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
    }

    @OnlyIn(Dist.CLIENT)
    public void showSafeZone() {
        for (AABB safeZone : safeZones) {
            CreateClient.OUTLINER.showAABB(safeZone, safeZone)
                    .colored(0x383b42)
                    .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.HIGHLIGHT_CHECKERED)
                    .lineWidth(1 / 16f);
        }
    }

    public boolean tryRemoveAABB(BlockPos pos) {
        return safeZones.removeIf(safeZone -> safeZone.contains(Vec3.atCenterOf(pos)));
    }

    public String getHoveredEntity() {
        return hoveredEntity;
    }

    public String getSelectedEntity() {
        return selectedEntity;
    }
    public void setSelectedEntity(String selectedEntity) {
        this.selectedEntity = selectedEntity;
    }

}