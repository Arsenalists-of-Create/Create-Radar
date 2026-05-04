package com.happysg.radar.block.controller.networkcontroller;

import com.happysg.radar.block.arad.aradnetworks.RadarContactRegistry;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.config.AutoTargetingHelper;
import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import com.happysg.radar.block.behavior.networks.config.IdentificationConfig;
import com.happysg.radar.block.behavior.networks.config.TargetingConfig;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.radar.behavior.RadarScanningBlockBehavior;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.PhysicsHandler;
import com.happysg.radar.item.binos.Binoculars;
import com.happysg.radar.block.radar.behavior.IRadar;
import com.happysg.radar.block.radar.track.TrackCategory;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.items.ItemStackHandler;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;

public class NetworkFiltererBlockEntity extends BlockEntity {
    private static final String NBT_INVENTORY = "Inventory";
    private static final String NBT_SLOT_NBT  = "SlotNbt";
    private static final Logger LOGGER = LogUtils.getLogger();

    // Legacy keys (for backwards compatibility with old saves)
    private static final String LEGACY_INV = "inv";
    private static final String LEGACY_SLOT_TAGS = "slotTags";
    private static final int SLOT_DETECTION = 0;
    private static final int SLOT_IDENT     = 1;
    private static final int SLOT_TARGETING = 2;

    private boolean selectedWasAuto = false;

    private long suppressAutoUntilTick = 0L;
    private static final int MANUAL_CLEAR_COOLDOWN_TICKS = 20;

    private long vsLoadedCacheUntilTick = -1;
    private final Map<UUID, Boolean> vsLoadedCache = new HashMap<>();

    private  TargetingConfig targeting = TargetingConfig.DEFAULT;
    private List<AABB> safeZones = new ArrayList<>();
    private BlockPos lastKnownPos = BlockPos.ZERO;
    private RadarTrack currenttrack;
    private @Nullable BlockPos radarPosCache;
    private @Nullable IRadar radarCache;
    private List<RadarTrack> cachedTracks = List.of();
    private DetectionConfig detectionCache = DetectionConfig.DEFAULT;
    public @Nullable RadarTrack activeTrackCache;

    private List<AutoPitchControllerBlockEntity> endpointCache = List.of();
    private long endpointCacheUntilTick = -1;


    private @Nullable String lastPushedTrackId = null;
    private int lastPushedCfgHash = 0;
    private long lastPushedSafeZonesHash = 0;

    public static void tick(Level level, BlockPos pos, BlockState state, NetworkFiltererBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        if (level.isClientSide) return;

        NetworkData data = NetworkData.get(sl);
        NetworkData.Group group = data.getOrCreateGroup(sl.dimension(), pos);

        String selectedId = data.getSelectedTargetId(group);

        if (Mods.SABLE.isLoaded() && selectedId != null && (sl.getGameTime() % 10 == 0)) {
            UUID shipId = UUID.fromString(selectedId);
            SubLevelAccess ship = SubLevelContainer.getContainer(level).getSubLevel(shipId);
            if (ship != null) RadarContactRegistry.markLocked(sl, shipId, 10);
        }

        if (sl.getGameTime() % 5 != 0) return;
        be.headlessTick(sl);
    }

    private boolean isVsShipStillLoaded(ServerLevel sl, @Nullable RadarTrack track) {
        if (!Mods.SABLE.isLoaded()) return true;
        if (track == null) return true;
        if (track.trackCategory() != TrackCategory.SABLE) return true;

        UUID shipId = UUID.fromString(track.getId());
        long now = sl.getGameTime();

        if (now > vsLoadedCacheUntilTick) {
            vsLoadedCache.clear();
            vsLoadedCacheUntilTick = now + 10;
        }

        Boolean cached = vsLoadedCache.get(shipId);
        if (cached != null) return cached;

        boolean loaded = SubLevelContainer.getContainer(sl).getSubLevel(shipId) != null;
        vsLoadedCache.put(shipId, loaded);
        return loaded;
    }

    private void headlessTick(ServerLevel sl) {
        NetworkData data = NetworkData.get(sl);
        NetworkData.Group group = data.getGroup(sl.dimension(), worldPosition);
        if (group == null) return;

        targeting = readTargetingFromSlot();

        // sync radar position + detection
        BlockPos netRadar = group.radarPos;
        if (!Objects.equals(netRadar, radarPosCache)) {
            radarPosCache = netRadar;
            radarCache = null;
            vsLoadedCacheUntilTick = -1;
        }

        detectionCache = DetectionConfig.fromTag(group.detectionTag);

        // resolve radar
        IRadar radar = getRadar(sl);
        if (radar == null || !radar.isRunning()) {
            cachedTracks = List.of();
            activeTrackCache = null;

            if (group.selectedTargetId != null) {
                selectedWasAuto = false;
                applySelectedTarget(sl, data, group, null, false);
            }
            return;
        }

        // rebuild track cache filtered

        cachedTracks = radar.getTracks().stream().filter(detectionCache::test).toList();

        // resolve current selected track from group.selectedTargetId
        RadarTrack selected = resolveSelectedTrack(group.selectedTargetId);
        if (selected != null && !isVsShipStillLoaded(sl, selected)) {
            selectedWasAuto = false;

            applySelectedTarget(sl, data, group, null, false);
            selected = null;
        }

        // If selection id exists but track isn't present anymore, clear it and stop.
        if (group.selectedTargetId != null && selected == null) {
            selectedWasAuto = false;

            applySelectedTarget(sl, data, group, null, false);
            return;
        }

        if (selected != null) {
            TargetingConfig cfg = targeting != null ? targeting : TargetingConfig.DEFAULT;

            if (selectedWasAuto && !cfg.test(selected.trackCategory())) {
                selectedWasAuto = false;
                applySelectedTarget(sl, data, group, null, false);
                return;
            }

            if (selectedWasAuto && !cfg.autoTarget()) {
                selectedWasAuto = false;
                applySelectedTarget(sl, data, group, null, false);
                return;
            }
        }

        if (selected == null) {
            long now = sl.getGameTime();
            if (now < suppressAutoUntilTick) {
                activeTrackCache = null;
                pushToEndpoints(null);
                return;
            }

            RadarTrack picked = pickAutoTarget_PerCannon(sl, cachedTracks, safeZones);
            if (picked != null) {
                selectedWasAuto = true;
                applySelectedTarget(sl, data, group, picked, true);
            }
            return;
        }

        TargetingConfig cfg = targeting != null ? targeting : TargetingConfig.DEFAULT;
        boolean requireLos = cfg.lineOfSight();

        // Only auto-selections should be affected by cannon engagement checks
        if (selectedWasAuto && !anyCannonCanEngage(sl, selected, requireLos)) {
            dropOrReselectAuto(sl, data, group);
            return;
        }

        TargetingConfig cfg2 = targeting != null ? targeting : TargetingConfig.DEFAULT;

        String newId = selected == null ? null : selected.getId();
        int newCfgHash = cfgHash(cfg2);
        long newZonesHash = safeZonesHash(safeZones);

        boolean changed =
                !Objects.equals(lastPushedTrackId, newId) ||
                        lastPushedCfgHash != newCfgHash ||
                        lastPushedSafeZonesHash != newZonesHash;

        activeTrackCache = selected;

        if (changed) {
            lastPushedTrackId = newId;
            lastPushedCfgHash = newCfgHash;
            lastPushedSafeZonesHash = newZonesHash;
            pushToEndpoints(selected);
        }
    }


    private @Nullable IRadar getRadar(ServerLevel sl) {
        if (radarPosCache == null) return null;

        if (radarCache instanceof BlockEntity be && be.getBlockPos().equals(radarPosCache)) {
            return radarCache;
        }

        radarCache = null;
        BlockEntity be = sl.getBlockEntity(radarPosCache);
        if (be instanceof IRadar r) radarCache = r;

        return radarCache;
    }

    private @Nullable RadarTrack resolveSelectedTrack(@Nullable String selectedId) {
        if (selectedId == null) return null;
        for (RadarTrack t : cachedTracks) {
            if (t == null) continue;
            if (selectedId.equals(t.getId())) return t;
        }
        return null;
    }

    private void applySelectedTarget(ServerLevel sl, NetworkData data, NetworkData.Group group,
                                     @Nullable RadarTrack track, boolean wasAuto) {
        String prev = data.getSelectedTargetId(group);

        this.selectedWasAuto = wasAuto;

        data.setSelectedTargetId(group, track == null ? null : track.getId());

        if (Mods.SABLE.isLoaded()) {
            if (prev != null && track == null)
                RadarContactRegistry.unLock(sl, UUID.fromString(prev));

            if (track != null && track.trackCategory() == TrackCategory.SABLE)
                RadarContactRegistry.markLocked(sl, UUID.fromString(track.getId()), 10);
        }

        activeTrackCache = track;
        pushToEndpoints(track);

        data.setDirty();
    }

    private double distSqFromFilterer(Vec3 pos) {
        Vec3 filtererPos = worldPosition.getCenter();
        if (Mods.SABLE.isLoaded() && level != null && PhysicsHandler.isBlockInPlotyard(level, worldPosition)) {
            filtererPos = PhysicsHandler.getWorldVec(level, filtererPos);
        }
        return filtererPos.distanceToSqr(pos);
    }

    private boolean anyCannonCanEngage(ServerLevel sl, RadarTrack track, boolean requireLos) {
        Vec3 target = track != null ? track.position() : null;
        if (target == null) return false;

        for (AutoPitchControllerBlockEntity pitch : getWeaponEndpointsCached(sl)) {
            pitch.getFiringControl();

            Vec3 origin = pitch.getRayStart();
            if (origin == null) continue;

            if (pitch.autoyaw != null && !pitch.autoyaw.canPossiblyAimAt(origin, target)) {
                continue;
            }

            // existing heavier check
            if (pitch.canEngageTrack(track, requireLos)) return true;
        }
        return false;
    }


    private void pushToEndpoints(@Nullable RadarTrack track) {
        TargetingConfig cfg = targeting != null ? targeting : TargetingConfig.DEFAULT;
        this.activeTrackCache = track;
        List<AutoPitchControllerBlockEntity> entities = (level instanceof ServerLevel sl) ? getWeaponEndpointsCached(sl) : List.of();
        for (AutoPitchControllerBlockEntity pitch : entities) {

            pitch.setAndAcquireTrack(track, cfg);
            pitch.setSafeZones(safeZones);
        }
    }

    private final ItemStackHandler inventory = new ItemStackHandler(3) {
        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);

            // Keep slotNbt in sync with actual inserted item tags
            updateSlotNbtFromInventory(slot);
            if (level != null && !level.isClientSide) {
                applyFiltersToNetwork();
            }

            setChanged();

            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack == null || stack.isEmpty()) return stack;

            ItemStack one = stack.copy();
            one.setCount(1);

            ItemStack remainder = super.insertItem(slot, one, simulate);
            if (remainder.isEmpty()) {
                ItemStack out = stack.copy();
                out.shrink(1);
                return out;
            }

            return stack;
        }
    };


    private final CompoundTag[] slotNbt = new CompoundTag[3];

    public NetworkFiltererBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        Arrays.fill(slotNbt, null);
    }

    public void receiveSelectedTargetFromMonitor(@Nullable RadarTrack track, List<AABB> safeZones) {
        if (!(level instanceof ServerLevel sl)) return;

        endpointCacheUntilTick = -1;

        this.safeZones.clear();
        if (safeZones != null) this.safeZones.addAll(safeZones);

        selectedWasAuto = false;

        NetworkData data = NetworkData.get(sl);
        NetworkData.Group group = data.getOrCreateGroup(sl.dimension(), worldPosition);


        // sets canonical selectedTargetId + pushes to endpoints
        applySelectedTarget(sl, data, group, track, false);

        long now = sl.getGameTime();
        if (track == null) {
            suppressAutoUntilTick = now + MANUAL_CLEAR_COOLDOWN_TICKS;
        } else {
            suppressAutoUntilTick = 0L;
        }

        data.setDirty();
    }

    public void onBinocularsTriggered(Player player, ItemStack binos, boolean reset) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockPos targetpos = Binoculars.getLastHit(binos);
        if(targetpos == null) return;
        //RadarTrack faketrack = new RadarTrack("binotarget", targetpos.getCenter(), Vec3.ZERO,10, TrackCategory.MISC,"misc",1);
        TargetingConfig cfg = targeting != null ? targeting : TargetingConfig.DEFAULT;

        List<AutoPitchControllerBlockEntity> entities = getWeaponEndpointBlockEntities();
        for (AutoPitchControllerBlockEntity pitch : entities) {
            pitch.setAndAcquirePos(targetpos, cfg,reset);
            pitch.setSafeZones(safeZones);
        }

        selectedWasAuto = false;
    }


    public void dissolveNetwork(ServerLevel level) {
        NetworkData data = NetworkData.get(level);
        if (data == null) return;

        data.dissolveNetworkForBrokenController(level, worldPosition);
        data.setDirty();
    }
    private void updateSlotNbtFromInventory(int slot) {
        if (slot < 0 || slot >= inventory.getSlots()) return;

        CompoundTag tag = getCustomDataTag(inventory.getStackInSlot(slot));
        slotNbt[slot] = tag == null || tag.isEmpty() ? null : tag.copy();
    }

    // Compact format: SlotNbt: [ {Slot:0, Tag:{...}}, ... ]
    private void loadSlotNbt(CompoundTag nbt) {
        Arrays.fill(slotNbt, null);

        if (!nbt.contains(NBT_SLOT_NBT, Tag.TAG_LIST))
            return;

        ListTag list = nbt.getList(NBT_SLOT_NBT, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getInt("Slot");
            if (slot < 0 || slot >= slotNbt.length) continue;

            CompoundTag t = entry.getCompound("Tag");
            slotNbt[slot] = t.isEmpty() ? null : t.copy();
        }
    }

    private List<AutoPitchControllerBlockEntity> getWeaponEndpointsCached(ServerLevel sl) {
        long now = sl.getGameTime();

        // reuse for 20 ticks (1 second). Tune to 10-40 as desired.
        if (now <= endpointCacheUntilTick && endpointCache != null) {
            return endpointCache;
        }

        endpointCache = getWeaponEndpointBlockEntities();
        endpointCacheUntilTick = now + 20;
        return endpointCache;
    }

    public List<AutoPitchControllerBlockEntity> getWeaponEndpointBlockEntities() {
        if (!(level instanceof ServerLevel serverLevel))
            return List.of();
        NetworkData data = NetworkData.get(serverLevel);
        NetworkData.Group group = data.getGroup(serverLevel.dimension(), worldPosition);
        if (group == null)
            return List.of();
        Set<BlockPos> pos = data.getWeaponEndpoints(group);
        List<AutoPitchControllerBlockEntity> entities = new ArrayList<>();
        for(BlockPos pos1: pos){
            if(serverLevel.getBlockEntity(pos1) instanceof AutoPitchControllerBlockEntity pitch){
                entities.add(pitch);
            }
        }
        return entities;
    }


    private Set<String> buildIgnoreList(IdentificationConfig config) {
        Set<String> out = new HashSet<>();
        if (config == null) return out;

        for (String name : config.usernames()) {
            if (name == null || name.isBlank()) continue;
            out.add(name.toLowerCase(Locale.ROOT));
        }

        if (config.label() != null && !config.label().isBlank()) {
            out.add(config.label().toLowerCase(Locale.ROOT));
        }

        return out;
    }


    @Nullable
    private RadarTrack pickAutoTarget_PerCannon(ServerLevel sl, Collection<RadarTrack> tracks, List<AABB> safeZones) {
        TargetingConfig cfg = targeting != null ? targeting : TargetingConfig.DEFAULT;
        if (!cfg.autoTarget()) return null;

        IdentificationConfig ident = readIdentificationFromSlot();
        if (ident == null) ident = IdentificationConfig.DEFAULT;
        Set<String> ignoreList = buildIgnoreList(ident);

        boolean requireLos = cfg.lineOfSight();

        // Build list of candidates sorted by distance to FILTERER
        ArrayList<RadarTrack> candidates = new ArrayList<>();
        for (RadarTrack track : tracks) {
            if (track == null) continue;

            if (!cfg.test(track.trackCategory())) continue;
            if (!isVsShipStillLoaded(sl, track)) continue;

            Vec3 pos = track.position();
            if (pos == null) continue;

            if (AutoTargetingHelper.isIgnoredByIdentification(track, sl, ignoreList)) continue;
            if (AutoTargetingHelper.isInSafeZone(pos, safeZones)) continue;

            candidates.add(track);
        }

        candidates.sort(Comparator.comparingDouble(t -> distSqFromFilterer(t.position())));

        // per-cannon gating (range + angle + safezone segment + LOS if enabled)
        for (RadarTrack t : candidates) {
            if (anyCannonCanEngage(sl, t, requireLos)) {
                return t;
            }
        }

        return null;
    }


    private void saveSlotNbt(CompoundTag nbt) {
        ListTag list = new ListTag();

        for (int i = 0; i < slotNbt.length; i++) {
            CompoundTag t = slotNbt[i];
            if (t == null || t.isEmpty()) continue;

            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", i);
            entry.put("Tag", t.copy());
            list.add(entry);
        }

        if (!list.isEmpty()) {
            nbt.put(NBT_SLOT_NBT, list);
        }
    }

    // Legacy loader: old worlds had { slotTags: {slot0:{}, slot1:{}, ... } }
    private void loadLegacySlotTags(CompoundTag nbt) {
        if (!nbt.contains(LEGACY_SLOT_TAGS, Tag.TAG_COMPOUND))
            return;

        CompoundTag tagsTag = nbt.getCompound(LEGACY_SLOT_TAGS);
        for (int i = 0; i < slotNbt.length; i++) {
            String k = "slot" + i;
            if (tagsTag.contains(k, Tag.TAG_COMPOUND)) {
                CompoundTag t = tagsTag.getCompound(k);
                slotNbt[i] = t.isEmpty() ? null : t.copy();
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);

        nbt.put(NBT_INVENTORY, inventory.serializeNBT(registries));
        saveSlotNbt(nbt);
        nbt.putLong("LastKnownPos", lastKnownPos.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);

        if (nbt.contains(NBT_INVENTORY, Tag.TAG_COMPOUND)) {
            inventory.deserializeNBT(registries, nbt.getCompound(NBT_INVENTORY));
        } else if (nbt.contains(LEGACY_INV, Tag.TAG_COMPOUND)) {
            inventory.deserializeNBT(registries, nbt.getCompound(LEGACY_INV));
        }

        if (nbt.contains(NBT_SLOT_NBT, Tag.TAG_LIST)) {
            loadSlotNbt(nbt);
        } else {
            loadLegacySlotTags(nbt);
        }

        if (nbt.contains("LastKnownPos", Tag.TAG_LONG)) {
            lastKnownPos = BlockPos.of(nbt.getLong("LastKnownPos"));
        } else {
            lastKnownPos = worldPosition;
        }

        for (int i = 0; i < inventory.getSlots(); i++) {
            updateSlotNbtFromInventory(i);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }
    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    @Nullable
    private static CompoundTag getCustomDataTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) return null;

        return data.copyTag();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        for (int i = 0; i < inventory.getSlots(); i++) updateSlotNbtFromInventory(i);

        if (level instanceof ServerLevel sl) {
            applyFiltersToNetwork();
        }
    }


    public ItemStackHandler getItemHandler() {
        return inventory;
    }
    public void applyFiltersToNetwork() {
        if (level == null || level.isClientSide) return;
        if (!(level instanceof ServerLevel sl)) return;

        endpointCacheUntilTick = -1;

        DetectionConfig detection = readDetectionFromSlot();
        IdentificationConfig ident = readIdentificationFromSlot();
        targeting = readTargetingFromSlot();

        NetworkData data = NetworkData.get(sl);
        NetworkData.Group group = data.getOrCreateGroup(sl.dimension(), worldPosition);

        data.setAllFilters(group, targeting, ident, detection);
        applyDetectionToRadar(sl, group, detection);
    }

    private void applyDetectionToRadar(ServerLevel sl, NetworkData.Group group, DetectionConfig detection) {
        // group needs to know where the radar is
        if (group.radarPos == null) return;

        BlockEntity be = sl.getBlockEntity(group.radarPos);
        if (!(be instanceof SmartBlockEntity sbe)) return;

        RadarScanningBlockBehavior scan = BlockEntityBehaviour.get(sbe, RadarScanningBlockBehavior.TYPE);
        if (scan == null) return;

        scan.applyDetectionConfig(detection);
    }

    private DetectionConfig readDetectionFromSlot() {
        return readDetectionFromItem(inventory.getStackInSlot(SLOT_DETECTION));
    }

    private static DetectionConfig readDetectionFromItem(ItemStack stack) {
        CompoundTag det = extractConfigCompound(stack, "detection");
        if (det == null) return DetectionConfig.DEFAULT;

        boolean player     = det.contains("player", Tag.TAG_BYTE) ? det.getBoolean("player") : DetectionConfig.DEFAULT.player();
        boolean sable      = det.contains("sable", Tag.TAG_BYTE) ? det.getBoolean("sable") : DetectionConfig.DEFAULT.sable();
        boolean contraption= det.contains("contraption", Tag.TAG_BYTE) ? det.getBoolean("contraption") : DetectionConfig.DEFAULT.contraption();
        boolean mob        = det.contains("mob", Tag.TAG_BYTE) ? det.getBoolean("mob") : DetectionConfig.DEFAULT.mob();
        boolean projectile = det.contains("projectile", Tag.TAG_BYTE) ? det.getBoolean("projectile") : DetectionConfig.DEFAULT.projectile();
        boolean animal     = det.contains("animal", Tag.TAG_BYTE) ? det.getBoolean("animal") : DetectionConfig.DEFAULT.animal();
        boolean item       = det.contains("item", Tag.TAG_BYTE) ? det.getBoolean("item") : DetectionConfig.DEFAULT.item();

        return new DetectionConfig(player, sable, contraption, mob, projectile, animal, item);
    }
    private IdentificationConfig readIdentificationFromSlot() {
        return readIdentificationFromItem(inventory.getStackInSlot(SLOT_IDENT));
    }

    private static IdentificationConfig readIdentificationFromItem(ItemStack stack) {
        CompoundTag root = getCustomDataTag(stack);
        if (root == null) return IdentificationConfig.DEFAULT;

        if (root.contains("Filters", Tag.TAG_COMPOUND)) {
            CompoundTag filters = root.getCompound("Filters");
            if (filters.contains("identification", Tag.TAG_COMPOUND)) {
                return IdentificationConfig.fromTag(filters.getCompound("identification"));
            }
        }

        if (root.contains("identification", Tag.TAG_COMPOUND)) {
            return IdentificationConfig.fromTag(root.getCompound("identification"));
        }

        if (!root.contains("EntriesList", Tag.TAG_LIST)) {
            if (root.contains("IDSTRING", Tag.TAG_STRING))
                return new IdentificationConfig(List.of(), root.getString("IDSTRING"));
            return IdentificationConfig.DEFAULT;
        }

        ListTag entriesTag = root.getList("EntriesList", Tag.TAG_STRING);
        ListTag foeTag = root.getList("FriendOrFoeList", Tag.TAG_BYTE);

        int n = Math.min(entriesTag.size(), foeTag.size());
        if (n <= 0 && !root.contains("IDSTRING", Tag.TAG_STRING))
            return IdentificationConfig.DEFAULT;

        List<String> names = new ArrayList<>(n);

        String label = root.getString("IDSTRING");
        return new IdentificationConfig(names, label);
    }

    private TargetingConfig readTargetingFromSlot() {
        return readTargetingFromItem(inventory.getStackInSlot(SLOT_TARGETING));
    }

    private static TargetingConfig readTargetingFromItem(ItemStack stack) {
        CompoundTag inner = extractConfigCompound(stack, "targeting");
        if (inner == null) return TargetingConfig.DEFAULT;

        boolean player      = inner.contains("player", Tag.TAG_BYTE) ? inner.getBoolean("player") : TargetingConfig.DEFAULT.player();
        boolean contraption = inner.contains("contraption", Tag.TAG_BYTE) ? inner.getBoolean("contraption") : TargetingConfig.DEFAULT.contraption();
        boolean mob         = inner.contains("mob", Tag.TAG_BYTE) ? inner.getBoolean("mob") : TargetingConfig.DEFAULT.mob();
        boolean animal      = inner.contains("animal", Tag.TAG_BYTE) ? inner.getBoolean("animal") : TargetingConfig.DEFAULT.animal();
        boolean projectile  = inner.contains("projectile", Tag.TAG_BYTE) ? inner.getBoolean("projectile") : TargetingConfig.DEFAULT.projectile();
        boolean autoTarget  = inner.contains("autoTarget", Tag.TAG_BYTE) ? inner.getBoolean("autoTarget") : TargetingConfig.DEFAULT.autoTarget();
        boolean los         = inner.contains("lineSight", Tag.TAG_BYTE) ? inner.getBoolean("lineSight") : TargetingConfig.DEFAULT.lineOfSight();
        return new TargetingConfig(player, contraption, mob, animal, projectile, autoTarget, true, los);
    }

    @Nullable
    private static CompoundTag extractConfigCompound(ItemStack stack, String key) {
        CompoundTag root = getCustomDataTag(stack);
        if (root == null) return null;

        if (root.contains("Filters", Tag.TAG_COMPOUND)) {
            CompoundTag filters = root.getCompound("Filters");
            if (filters.contains(key, Tag.TAG_COMPOUND)) {
                return filters.getCompound(key);
            }
        }

        if (root.contains(key, Tag.TAG_COMPOUND)) {
            return root.getCompound(key);
        }

        return null;
    }

    private void dropOrReselectAuto(ServerLevel sl, NetworkData data, NetworkData.Group group) {
        RadarTrack picked = pickAutoTarget_PerCannon(sl, cachedTracks, safeZones);
        if (picked != null) {
            selectedWasAuto = true;
            applySelectedTarget(sl, data, group, picked, true);
        } else {
            selectedWasAuto = false;
            applySelectedTarget(sl, data, group, null, false);
        }
    }

    private static int cfgHash(TargetingConfig cfg) {
        return Objects.hash(
                cfg.autoTarget(),
                cfg.lineOfSight(),
                cfg.player(),
                cfg.mob(),
                cfg.animal(),
                cfg.projectile(),
                cfg.contraption()
        );
    }

    private static long safeZonesHash(List<AABB> zones) {
        long h = 1469598103934665603L;
        for (AABB a : zones) {
            h ^= Double.doubleToLongBits(a.minX); h *= 1099511628211L;
            h ^= Double.doubleToLongBits(a.minY); h *= 1099511628211L;
            h ^= Double.doubleToLongBits(a.minZ); h *= 1099511628211L;
            h ^= Double.doubleToLongBits(a.maxX); h *= 1099511628211L;
            h ^= Double.doubleToLongBits(a.maxY); h *= 1099511628211L;
            h ^= Double.doubleToLongBits(a.maxZ); h *= 1099511628211L;
        }
        return h;
    }
}
