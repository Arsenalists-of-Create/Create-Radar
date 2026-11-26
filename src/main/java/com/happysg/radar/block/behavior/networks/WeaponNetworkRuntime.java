package com.happysg.radar.block.behavior.networks;

import com.happysg.radar.block.controller.firing.FireControllerBlockEntity;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.api.mount.RadarMountAdapter;
import com.happysg.radar.api.mount.RadarMountRegistry;
import com.happysg.radar.compat.Mods;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.PhysBearingBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class WeaponNetworkRuntime {

    private static final Comparator<BlockPos> STABLE_POSITION =
            Comparator.comparingInt((BlockPos pos) -> pos.getX())
                    .thenComparingInt(BlockPos::getY)
                    .thenComparingInt(BlockPos::getZ);

    private static final Map<ServerLevel, WeaponNetworkRuntime> BY_LEVEL = new WeakHashMap<>();

    private record LinkRef(BlockPos dataLinkPos, BlockPos endpointPos, BlockPos mountPos,
                           DataLinkBlockEntity.WeaponEndpointType type) { }

    private record ContactRef(BlockPos endpointPos, BlockPos mountPos,
                              DataLinkBlockEntity.WeaponEndpointType type) { }

    private record Slot(BlockPos mountPos, DataLinkBlockEntity.WeaponEndpointType type) { }

    public record WeaponGroupView(BlockPos mountPos,
                                  @Nullable BlockPos yawPos,
                                  @Nullable BlockPos pitchPos,
                                  @Nullable BlockPos firingPos,
                                  Set<BlockPos> dataLinks) {
        public WeaponGroupView {
            dataLinks = Collections.unmodifiableSet(new LinkedHashSet<>(dataLinks));
        }

        public Set<BlockPos> endpoints() {
            Set<BlockPos> out = new HashSet<>();
            if (yawPos != null) out.add(yawPos);
            if (pitchPos != null) out.add(pitchPos);
            if (firingPos != null) out.add(firingPos);
            return out;
        }

        public Set<BlockPos> otherEndpoints(BlockPos exclude) {
            Set<BlockPos> out = endpoints();
            out.remove(exclude);
            return out;
        }
    }

    private record RuntimeSnapshot(Map<BlockPos, BlockPos> controllerToMount,
                                   Map<BlockPos, WeaponGroupView> groupsByMount,
                                   Map<BlockPos, List<BlockPos>> firingByMount) {
        private static RuntimeSnapshot empty() {
            return new RuntimeSnapshot(Map.of(), Map.of(), Map.of());
        }
    }

    private final WeakReference<ServerLevel> levelReference;
    private final Map<BlockPos, LinkRef> linksByDataLink = new HashMap<>();
    private final Map<BlockPos, DataLinkBlockEntity.WeaponEndpointType> contactControllers = new HashMap<>();
    private RuntimeSnapshot snapshot = RuntimeSnapshot.empty();
    private boolean dirty = true;
    private long snapshotTick = Long.MIN_VALUE;

    private WeaponNetworkRuntime(ServerLevel level) {
        this.levelReference = new WeakReference<>(level);
    }

    public static WeaponNetworkRuntime get(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level, WeaponNetworkRuntime::new);
    }

    @Nullable
    public static WeaponNetworkRuntime peek(ServerLevel level) {
        return BY_LEVEL.get(level);
    }

    public static void clear(ServerLevel level) {
        BY_LEVEL.remove(level);
    }

    public void advertiseContactController(BlockEntity controller) {
        ServerLevel level = levelReference.get();
        if (controller == null || level == null || controller.getLevel() != level || controller.isRemoved()) {
            return;
        }
        DataLinkBlockEntity.WeaponEndpointType type = endpointType(controller);
        if (type == DataLinkBlockEntity.WeaponEndpointType.NONE) {
            return;
        }
        BlockPos pos = controller.getBlockPos().immutable();
        if (contactControllers.put(pos, type) != type) {
            dirty = true;
        }
    }

    public void unregisterContactController(BlockPos controllerPos) {
        if (controllerPos != null && contactControllers.remove(controllerPos) != null) {
            dirty = true;
        }
    }

    public void markContactTopologyDirty() {
        dirty = true;
    }

    public static void register(ServerLevel level, DataLinkBlockEntity link) {
        get(level).registerLink(link);
    }

    private void registerLink(DataLinkBlockEntity link) {
        ServerLevel level = levelReference.get();
        if (level == null || link == null || link.getLevel() != level || link.isRemoved()) {
            return;
        }

        BlockPos linkPos = link.getBlockPos().immutable();
        DataLinkBlockEntity.WeaponEndpointType type = link.getWeaponEndpointType();
        if (type == DataLinkBlockEntity.WeaponEndpointType.NONE
                || !DataLinkBlock.isWeaponLink(link.getBlockState(), type)) {
            unregisterLink(linkPos);
            return;
        }

        BlockPos endpoint = link.getSourcePosition();
        BlockPos mount = link.getTargetPosition();
        if (endpoint == null || mount == null
                || endpoint.equals(mount) || !level.hasChunkAt(linkPos)) {
            unregisterLink(linkPos);
            return;
        }

        LinkRef candidate = new LinkRef(linkPos, endpoint.immutable(), mount.immutable(), type);
        LinkRef previous = linksByDataLink.get(linkPos);
        if (candidate.equals(previous)) {
            return;
        }

        if (!canRegister(candidate, linkPos)) {
            unregisterLink(linkPos);
            return;
        }

        linksByDataLink.put(linkPos, candidate);
        dirty = true;
    }

    private boolean canRegister(LinkRef candidate, @Nullable BlockPos ignoredLinkPos) {
        for (LinkRef existing : linksByDataLink.values()) {
            if (ignoredLinkPos != null && existing.dataLinkPos().equals(ignoredLinkPos)) {
                continue;
            }
            if (existing.endpointPos().equals(candidate.endpointPos())
                    && (!existing.mountPos().equals(candidate.mountPos())
                    || existing.type() != candidate.type())) {
                return false;
            }
            if (candidate.type() != DataLinkBlockEntity.WeaponEndpointType.FIRING
                    && existing.type() == candidate.type()
                    && existing.mountPos().equals(candidate.mountPos())
                    && !existing.endpointPos().equals(candidate.endpointPos())) {
                return false;
            }
        }
        return true;
    }

    public static void unregister(ServerLevel level, BlockPos dataLinkPos) {
        if (dataLinkPos == null) {
            return;
        }
        WeaponNetworkRuntime runtime = peek(level);
        if (runtime != null) {
            runtime.unregisterLink(dataLinkPos);
        }
    }

    private void unregisterLink(BlockPos linkPos) {
        if (linksByDataLink.remove(linkPos) != null) {
            dirty = true;
        }
    }

    public static boolean canAttachEndpoint(ServerLevel level, BlockPos mountPos,
                                            BlockPos endpointPos,
                                            DataLinkBlockEntity.WeaponEndpointType type) {
        if (mountPos == null || endpointPos == null
                || type == null || type == DataLinkBlockEntity.WeaponEndpointType.NONE
                || mountPos.equals(endpointPos)) {
            return false;
        }
        LinkRef candidate = new LinkRef(BlockPos.ZERO, endpointPos, mountPos, type);
        return get(level).canRegister(candidate, null);
    }

    @Nullable
    public static BlockPos getMountForController(ServerLevel level, BlockPos controllerPos) {
        if (controllerPos == null) {
            return null;
        }
        return get(level).snapshot().controllerToMount().get(controllerPos);
    }

    @Nullable
    public static WeaponGroupView getWeaponGroupViewFromEndpoint(ServerLevel level,
                                                                 BlockPos endpointPos) {
        BlockPos mount = getMountForController(level, endpointPos);
        return mount == null ? null : get(level).snapshot().groupsByMount().get(mount);
    }

    @Nullable
    public static WeaponGroupView getWeaponGroupViewFromMount(ServerLevel level,
                                                              BlockPos mountPos) {
        if (mountPos == null) {
            return null;
        }
        return get(level).snapshot().groupsByMount().get(mountPos);
    }

    public static Collection<WeaponGroupView> getGroups(ServerLevel level) {
        return List.copyOf(get(level).snapshot().groupsByMount().values());
    }

    public static List<BlockPos> getFiringControllersForMount(ServerLevel level,
                                                              BlockPos mountPos) {
        if (mountPos == null) {
            return List.of();
        }
        return get(level).snapshot().firingByMount().getOrDefault(mountPos, List.of());
    }

    public static boolean hasValidWeaponGroup(ServerLevel level, BlockPos mountPos) {
        return getWeaponGroupViewFromMount(level, mountPos) != null;
    }

    private RuntimeSnapshot snapshot() {
        ServerLevel level = levelReference.get();
        if (level == null) {
            return RuntimeSnapshot.empty();
        }
        long tick = level.getGameTime();
        if (dirty || tick != snapshotTick) {
            snapshot = buildSnapshot();
            snapshotTick = tick;
            dirty = false;
        }
        return snapshot;
    }

    private boolean isAvailable(ServerLevel level, LinkRef ref) {
        if (!level.hasChunkAt(ref.dataLinkPos())
                || !level.hasChunkAt(ref.endpointPos())
                || !level.hasChunkAt(ref.mountPos())) {
            return false;
        }
        BlockEntity link = level.getBlockEntity(ref.dataLinkPos());
        if (!(link instanceof DataLinkBlockEntity dataLink)
                || dataLink.isRemoved()
                || dataLink.getWeaponEndpointType() != ref.type()
                || !DataLinkBlock.isWeaponLink(dataLink.getBlockState(), ref.type())) {
            return false;
        }
        BlockEntity endpoint = level.getBlockEntity(ref.endpointPos());
        if (endpoint == null || endpoint.isRemoved()) {
            return false;
        }
        if (switch (ref.type()) {
            case YAW -> !(endpoint instanceof AutoYawControllerBlockEntity);
            case PITCH -> !(endpoint instanceof AutoPitchControllerBlockEntity);
            case FIRING -> !(endpoint instanceof FireControllerBlockEntity);
            case NONE -> true;
        }) {
            return false;
        }
        BlockEntity mount = level.getBlockEntity(ref.mountPos());
        return mount != null && !mount.isRemoved()
                || RadarMountRegistry.find(level, ref.mountPos()) != null;
    }

    private static DataLinkBlockEntity.WeaponEndpointType endpointType(BlockEntity endpoint) {
        if (endpoint instanceof AutoYawControllerBlockEntity) {
            return DataLinkBlockEntity.WeaponEndpointType.YAW;
        }
        if (endpoint instanceof AutoPitchControllerBlockEntity) {
            return DataLinkBlockEntity.WeaponEndpointType.PITCH;
        }
        if (endpoint instanceof FireControllerBlockEntity) {
            return DataLinkBlockEntity.WeaponEndpointType.FIRING;
        }
        return DataLinkBlockEntity.WeaponEndpointType.NONE;
    }

    private boolean isContactMount(ServerLevel level, BlockPos mountPos,
                                   DataLinkBlockEntity.WeaponEndpointType type) {
        if (!level.hasChunkAt(mountPos)) {
            return false;
        }
        BlockEntity mount = level.getBlockEntity(mountPos);
        if (mount instanceof CannonMountBlockEntity && !mount.isRemoved()) {
            return true;
        }
        if (Mods.VS_CLOCKWORK.isLoaded() && mount instanceof PhysBearingBlockEntity && !mount.isRemoved()) {
            return true;
        }
        RadarMountAdapter api = RadarMountRegistry.find(level, mountPos);
        if (api == null || !api.isValid() || !mountPos.equals(api.getMountPos())) {
            return false;
        }
        return switch (type) {
            case YAW -> api.supportsYaw();
            case PITCH -> api.supportsPitch();
            case FIRING -> true;
            case NONE -> false;
        };
    }

    private List<BlockPos> discoverContactMounts(ServerLevel level, BlockPos endpointPos,
                                                   DataLinkBlockEntity.WeaponEndpointType type) {
        if (!level.hasChunkAt(endpointPos)) {
            return List.of();
        }
        BlockEntity entity = level.getBlockEntity(endpointPos);
        if (entity == null || entity.isRemoved() || endpointType(entity) != type) {
            return List.of();
        }
        Set<BlockPos> candidates = new HashSet<>();
        BlockState state = entity.getBlockState();
        switch (type) {
            case PITCH -> {
                if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
                    Direction direction = state.getValue(HorizontalDirectionalBlock.FACING);
                    BlockPos candidate = endpointPos.relative(direction);
                    if (isContactMount(level, candidate, type)) {
                        candidates.add(candidate.immutable());
                    }
                }
            }
            case YAW -> {
                if (state.hasProperty(DirectionalKineticBlock.FACING)) {
                    Direction direction = state.getValue(DirectionalKineticBlock.FACING).getOpposite();
                    if (direction.getAxis() == Direction.Axis.Y) {
                        BlockPos candidate = endpointPos.relative(direction);
                        if (isContactMount(level, candidate, type)) {
                            candidates.add(candidate.immutable());
                        }
                    }
                }
            }
            case FIRING -> {
                for (Direction direction : Direction.values()) {
                    BlockPos candidate = endpointPos.relative(direction);
                    if (isContactMount(level, candidate, type)) {
                        candidates.add(candidate.immutable());
                    }
                }
            }
            case NONE -> { }
        }
        return candidates.stream().sorted(STABLE_POSITION).toList();
    }

    private RuntimeSnapshot buildSnapshot() {
        ServerLevel level = levelReference.get();
        if (level == null) {
            return RuntimeSnapshot.empty();
        }
        Map<BlockPos, List<LinkRef>> explicitByMount = new HashMap<>();
        Set<BlockPos> endpointsWithExplicitLinks = new HashSet<>();
        Set<Slot> reservedSlots = new HashSet<>();
        for (LinkRef ref : linksByDataLink.values()) {
            endpointsWithExplicitLinks.add(ref.endpointPos());
            if (ref.type() != DataLinkBlockEntity.WeaponEndpointType.FIRING) {
                reservedSlots.add(new Slot(ref.mountPos(), ref.type()));
            }
            if (isAvailable(level, ref)) {
                explicitByMount.computeIfAbsent(ref.mountPos(), ignored -> new ArrayList<>()).add(ref);
            }
        }

        Map<BlockPos, List<ContactRef>> contactsByMount = new HashMap<>();
        Set<BlockPos> ambiguousMounts = new HashSet<>();
        for (Map.Entry<BlockPos, DataLinkBlockEntity.WeaponEndpointType> entry : contactControllers.entrySet()) {
            BlockPos endpointPos = entry.getKey();
            DataLinkBlockEntity.WeaponEndpointType type = entry.getValue();
            if (endpointsWithExplicitLinks.contains(endpointPos)) {
                continue;
            }
            List<BlockPos> discovered = discoverContactMounts(level, endpointPos, type);
            if (discovered.size() > 1) {
                ambiguousMounts.addAll(discovered);
                continue;
            }
            if (discovered.size() != 1) {
                continue;
            }
            BlockPos mountPos = discovered.get(0);
            if (type != DataLinkBlockEntity.WeaponEndpointType.FIRING
                    && reservedSlots.contains(new Slot(mountPos, type))) {
                continue;
            }
            contactsByMount.computeIfAbsent(mountPos, ignored -> new ArrayList<>())
                    .add(new ContactRef(endpointPos, mountPos, type));
        }

        Set<BlockPos> allMounts = new HashSet<>(explicitByMount.keySet());
        allMounts.addAll(contactsByMount.keySet());
        allMounts.addAll(ambiguousMounts);
        List<BlockPos> sortedMounts = allMounts.stream().sorted(STABLE_POSITION).toList();

        Map<BlockPos, BlockPos> endpointToMount = new HashMap<>();
        Map<BlockPos, WeaponGroupView> groups = new java.util.LinkedHashMap<>();
        Map<BlockPos, List<BlockPos>> firingByMount = new HashMap<>();

        for (BlockPos mountPos : sortedMounts) {
            List<LinkRef> explicit = explicitByMount.getOrDefault(mountPos, List.of());
            List<ContactRef> contacts = contactsByMount.getOrDefault(mountPos, List.of());
            Map<DataLinkBlockEntity.WeaponEndpointType, Set<BlockPos>> explicitClaims = new java.util.EnumMap<>(DataLinkBlockEntity.WeaponEndpointType.class);
            Map<DataLinkBlockEntity.WeaponEndpointType, Set<BlockPos>> contactClaims = new java.util.EnumMap<>(DataLinkBlockEntity.WeaponEndpointType.class);
            Set<BlockPos> dataLinks = new LinkedHashSet<>();
            for (LinkRef ref : explicit) {
                dataLinks.add(ref.dataLinkPos());
                explicitClaims.computeIfAbsent(ref.type(), ignored -> new HashSet<>()).add(ref.endpointPos());
            }
            for (ContactRef ref : contacts) {
                contactClaims.computeIfAbsent(ref.type(), ignored -> new HashSet<>()).add(ref.endpointPos());
            }

            Set<BlockPos> yaw = explicitClaims.getOrDefault(DataLinkBlockEntity.WeaponEndpointType.YAW, Set.of());
            Set<BlockPos> pitch = explicitClaims.getOrDefault(DataLinkBlockEntity.WeaponEndpointType.PITCH, Set.of());
            if (yaw.isEmpty()) {
                yaw = contactClaims.getOrDefault(DataLinkBlockEntity.WeaponEndpointType.YAW, Set.of());
            }
            if (pitch.isEmpty()) {
                pitch = contactClaims.getOrDefault(DataLinkBlockEntity.WeaponEndpointType.PITCH, Set.of());
            }
            if (ambiguousMounts.contains(mountPos) || yaw.size() > 1 || pitch.size() > 1) {
                continue;
            }

            Set<BlockPos> firing = new HashSet<>(explicitClaims.getOrDefault(DataLinkBlockEntity.WeaponEndpointType.FIRING, Set.of()));
            firing.addAll(contactClaims.getOrDefault(DataLinkBlockEntity.WeaponEndpointType.FIRING, Set.of()));
            List<BlockPos> orderedFiring = firing.stream().sorted(STABLE_POSITION).toList();
            BlockPos yawPos = yaw.isEmpty() ? null : yaw.iterator().next();
            BlockPos pitchPos = pitch.isEmpty() ? null : pitch.iterator().next();
            BlockPos firingPos = orderedFiring.isEmpty() ? null : orderedFiring.get(0);
            if (yawPos == null && pitchPos == null && firingPos == null) {
                continue;
            }
            if (yawPos != null) endpointToMount.put(yawPos, mountPos);
            if (pitchPos != null) endpointToMount.put(pitchPos, mountPos);
            for (BlockPos pos : orderedFiring) endpointToMount.put(pos, mountPos);
            firingByMount.put(mountPos, List.copyOf(orderedFiring));
            groups.put(mountPos, new WeaponGroupView(mountPos, yawPos, pitchPos, firingPos, dataLinks));
        }

        return new RuntimeSnapshot(Map.copyOf(endpointToMount),
                Collections.unmodifiableMap(groups), Map.copyOf(firingByMount));
    }
}
