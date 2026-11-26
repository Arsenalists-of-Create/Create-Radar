package com.happysg.radar.block.behavior.networks;

import com.happysg.radar.block.controller.firing.FireControllerBlockEntity;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.api.mount.RadarMountRegistry;
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
                    .thenComparingInt(pos -> pos.getY())
                    .thenComparingInt(pos -> pos.getZ());

    private static final Map<ServerLevel, WeaponNetworkRuntime> BY_LEVEL = new WeakHashMap<>();

    private record LinkRef(BlockPos dataLinkPos, BlockPos endpointPos, BlockPos mountPos,
                           DataLinkBlockEntity.WeaponEndpointType type) { }

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
        if (!level.hasChunkAt(ref.dataLinkPos()) || !level.hasChunkAt(ref.endpointPos()) || !level.hasChunkAt(ref.mountPos())) {
            return false;
        }
        BlockEntity link = level.getBlockEntity(ref.dataLinkPos());
        if (!(link instanceof DataLinkBlockEntity dataLink) || dataLink.isRemoved() || dataLink.getWeaponEndpointType() != ref.type() || !DataLinkBlock.isWeaponLink(dataLink.getBlockState(), ref.type())) {
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
        return mount != null && !mount.isRemoved() || RadarMountRegistry.find(level, ref.mountPos()) != null;
    }

    private RuntimeSnapshot buildSnapshot() {
        ServerLevel level = levelReference.get();
        if (level == null) {
            return RuntimeSnapshot.empty();
        }
        Map<BlockPos, BlockPos> endpointToMount = new HashMap<>();
        Map<BlockPos, List<LinkRef>> grouped = new HashMap<>();

        for (LinkRef ref : linksByDataLink.values()) {
            if (!isAvailable(level, ref)) {
                continue;
            }
            endpointToMount.put(ref.endpointPos(), ref.mountPos());
            grouped.computeIfAbsent(ref.mountPos(), ignored -> new ArrayList<>()).add(ref);
        }

        Map<BlockPos, WeaponGroupView> groups = new HashMap<>();
        Map<BlockPos, List<BlockPos>> firingControllers = new HashMap<>();

        for (Map.Entry<BlockPos, List<LinkRef>> entry : grouped.entrySet()) {
            List<LinkRef> refs = entry.getValue();
            refs.sort(Comparator.comparing(LinkRef::dataLinkPos, STABLE_POSITION));
            BlockPos yaw = null;
            BlockPos pitch = null;
            Set<BlockPos> firing = new LinkedHashSet<>();
            Set<BlockPos> dataLinks = new LinkedHashSet<>();
            boolean ambiguous = false;

            for (LinkRef ref : refs) {
                dataLinks.add(ref.dataLinkPos());
                switch (ref.type()) {
                    case YAW -> {
                        if (yaw != null && !yaw.equals(ref.endpointPos())) ambiguous = true;
                        else yaw = ref.endpointPos();
                    }
                    case PITCH -> {
                        if (pitch != null && !pitch.equals(ref.endpointPos())) ambiguous = true;
                        else pitch = ref.endpointPos();
                    }
                    case FIRING -> firing.add(ref.endpointPos());
                    case NONE -> { }
                }
            }

            if (ambiguous) {
                for (LinkRef ref : refs) {
                    endpointToMount.remove(ref.endpointPos());
                }
                continue;
            }

            List<BlockPos> allFiring = firing.stream().sorted(STABLE_POSITION).toList();
            List<BlockPos> activeFiring = allFiring.stream()
                    .filter(pos -> level.getBlockEntity(pos) instanceof FireControllerBlockEntity)
                    .toList();
            firingControllers.put(entry.getKey(), List.copyOf(activeFiring));
            BlockPos primary = allFiring.isEmpty() ? null : allFiring.get(0);
            groups.put(entry.getKey(), new WeaponGroupView(entry.getKey(), yaw, pitch, primary, dataLinks));
        }

        List<BlockPos> sortedMounts = new ArrayList<>(groups.keySet());
        sortedMounts.sort(STABLE_POSITION);
        Map<BlockPos, WeaponGroupView> stableGroups = new java.util.LinkedHashMap<>();
        for (BlockPos mount : sortedMounts) {
            stableGroups.put(mount, groups.get(mount));
        }

        return new RuntimeSnapshot(Map.copyOf(endpointToMount), Collections.unmodifiableMap(stableGroups), Map.copyOf(firingControllers));
    }
}
