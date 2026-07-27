package com.happysg.radar.block.behavior.networks;

import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class WeaponNetworkRuntime {
    private static final Map<ServerLevel, WeaponNetworkRuntime> BY_LEVEL = new WeakHashMap<>();

    private final ServerLevel level;
    private final Map<BlockPos, LinkEntry> linksByDataLink = new HashMap<>();
    private final Map<BlockPos, BlockPos> controllerToMount = new HashMap<>();
    private final Map<BlockPos, BlockPos> endpointToMount = new HashMap<>();
    private final Map<BlockPos, Group> groupsByMount = new HashMap<>();

    private WeaponNetworkRuntime(ServerLevel level) {
        this.level = level;
    }

    public static WeaponNetworkRuntime get(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level, WeaponNetworkRuntime::new);
    }

    public static void clear(ServerLevel level) {
        WeaponNetworkRuntime runtime = BY_LEVEL.remove(level);
        if (runtime != null) {
            runtime.linksByDataLink.clear();
            runtime.controllerToMount.clear();
            runtime.endpointToMount.clear();
            runtime.groupsByMount.clear();
        }
    }

    public boolean register(DataLinkBlockEntity dataLink) {
        if (!(dataLink.getLevel() instanceof ServerLevel)) {
            return false;
        }

        BlockPos dataLinkPos = dataLink.getBlockPos().immutable();

        DataLinkBlockEntity.WeaponEndpointType type = dataLink.getWeaponEndpointType();
        if (type == DataLinkBlockEntity.WeaponEndpointType.NONE) {
            unregister(dataLinkPos);
            return false;
        }

        BlockState state = dataLink.getBlockState();
        if (!(state.getBlock() instanceof DataLinkBlock)
                || !state.hasProperty(DataLinkBlock.LINK_STYLE)
                || state.getValue(DataLinkBlock.LINK_STYLE) != DataLinkBlock.LinkStyle.CONTROLLER) {
            unregister(dataLinkPos);
            return false;
        }

        BlockPos endpointPos = dataLink.getSourcePosition().immutable();
        BlockPos mountPos = dataLink.getTargetPosition().immutable();
        LinkEntry entry = new LinkEntry(dataLinkPos, endpointPos, mountPos, type);
        LinkEntry previous = linksByDataLink.get(dataLinkPos);
        if (entry.equals(previous)) {
            return true;
        }
        if (!canRegister(entry, previous)) {
            unregister(dataLinkPos);
            return false;
        }

        unregister(dataLinkPos);
        linksByDataLink.put(dataLinkPos, entry);
        controllerToMount.put(endpointPos, mountPos);
        endpointToMount.put(endpointPos, mountPos);

        Group group = groupsByMount.computeIfAbsent(mountPos, Group::new);
        group.dataLinks.add(dataLinkPos);
        group.endpoints.put(type, endpointPos);
        return true;
    }

    private boolean canRegister(LinkEntry entry, @Nullable LinkEntry previous) {
        BlockPos existingMount = controllerToMount.get(entry.endpointPos());
        boolean ownsExistingControllerMapping = previous != null
                && previous.endpointPos().equals(entry.endpointPos())
                && previous.mountPos().equals(existingMount);
        if (existingMount != null && !existingMount.equals(entry.mountPos())
                && !ownsExistingControllerMapping) {
            return false;
        }

        Group destination = groupsByMount.get(entry.mountPos());
        if (destination == null) {
            return true;
        }
        BlockPos existingEndpoint = destination.endpoints.get(entry.type());
        if (existingEndpoint == null || existingEndpoint.equals(entry.endpointPos())) {
            return true;
        }
        return previous != null
                && previous.mountPos().equals(entry.mountPos())
                && previous.type() == entry.type()
                && previous.endpointPos().equals(existingEndpoint);
    }

    public void unregister(BlockPos dataLinkPos) {
        LinkEntry old = linksByDataLink.remove(dataLinkPos);
        if (old == null) {
            return;
        }

        controllerToMount.remove(old.endpointPos());
        endpointToMount.remove(old.endpointPos());

        Group group = groupsByMount.get(old.mountPos());
        if (group == null) {
            return;
        }

        group.dataLinks.remove(old.dataLinkPos());
        if (old.endpointPos().equals(group.endpoints.get(old.type()))) {
            group.endpoints.remove(old.type());
        }
        if (group.dataLinks.isEmpty() && group.endpoints.isEmpty()) {
            groupsByMount.remove(old.mountPos());
        }
    }

    @Nullable
    public BlockPos getMountForController(BlockPos controllerPos) {
        pruneStaleLinks();
        return controllerToMount.get(controllerPos);
    }

    @Nullable
    public WeaponGroupView getWeaponGroupViewFromEndpoint(BlockPos endpointPos) {
        pruneStaleLinks();
        BlockPos mountPos = endpointToMount.get(endpointPos);
        return mountPos == null ? null : getWeaponGroupView(mountPos);
    }

    @Nullable
    public WeaponGroupView getWeaponGroupView(BlockPos mountPos) {
        pruneStaleLinks();
        Group group = groupsByMount.get(mountPos);
        return group == null ? null : group.view();
    }

    public Collection<WeaponGroupView> getGroups() {
        pruneStaleLinks();
        return groupsByMount.values().stream().map(Group::view).toList();
    }

    public boolean canAttachEndpoint(DataLinkBlockEntity.WeaponEndpointType type, BlockPos endpointPos, BlockPos mountPos) {
        pruneStaleLinks();
        if (type == DataLinkBlockEntity.WeaponEndpointType.NONE) {
            return false;
        }
        BlockPos existingMount = controllerToMount.get(endpointPos);
        if (existingMount != null && !existingMount.equals(mountPos)) {
            return false;
        }

        Group group = groupsByMount.get(mountPos);
        if (group == null) {
            return true;
        }

        BlockPos existingEndpoint = group.endpoints.get(type);
        return existingEndpoint == null || existingEndpoint.equals(endpointPos);
    }

    /**
     * Moves all still-live controller links from one mount coordinate to
     * another and inserts the controller link that Sable just relocated.
     * Validation is completed before any link target is changed.
     */
    public boolean relocateMountGroup(BlockPos oldMount, BlockPos newMount,
                                      DataLinkBlockEntity movedLink,
                                      BlockPos movedEndpoint) {
        pruneStaleLinks();

        List<DataLinkBlockEntity> linksToMove = new ArrayList<>();
        Group oldGroup = groupsByMount.get(oldMount);
        if (oldGroup != null) {
            for (BlockPos dataLinkPos : new HashSet<>(oldGroup.dataLinks)) {
                BlockEntity blockEntity = level.getBlockEntity(dataLinkPos);
                if (blockEntity instanceof DataLinkBlockEntity dataLink) {
                    linksToMove.add(dataLink);
                }
            }
        }
        if (linksToMove.stream().noneMatch(link -> link.getBlockPos().equals(movedLink.getBlockPos()))) {
            linksToMove.add(movedLink);
        }

        EnumMap<DataLinkBlockEntity.WeaponEndpointType, BlockPos> desired =
                new EnumMap<>(DataLinkBlockEntity.WeaponEndpointType.class);
        Group destination = groupsByMount.get(newMount);
        if (destination != null) {
            desired.putAll(destination.endpoints);
        }

        for (DataLinkBlockEntity link : linksToMove) {
            DataLinkBlockEntity.WeaponEndpointType type = link.getWeaponEndpointType();
            if (type == DataLinkBlockEntity.WeaponEndpointType.NONE) {
                continue;
            }
            BlockPos endpoint = link.getBlockPos().equals(movedLink.getBlockPos())
                    ? movedEndpoint : link.getSourcePosition();
            BlockPos existing = desired.putIfAbsent(type, endpoint);
            if (existing != null && !existing.equals(endpoint)) {
                unregister(movedLink.getBlockPos());
                return false;
            }
            BlockPos mappedMount = controllerToMount.get(endpoint);
            if (mappedMount != null && !mappedMount.equals(oldMount)
                    && !mappedMount.equals(newMount)) {
                unregister(movedLink.getBlockPos());
                return false;
            }
        }

        for (DataLinkBlockEntity link : linksToMove) {
            link.finishAssemblyRelocation(newMount);
            if (!register(link)) {
                return false;
            }
        }
        pruneStaleLinks();
        return true;
    }

    private void pruneStaleLinks() {
        for (BlockPos dataLinkPos : new HashSet<>(linksByDataLink.keySet())) {
            if (!level.isLoaded(dataLinkPos)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(dataLinkPos);
            if (!(be instanceof DataLinkBlockEntity)) {
                unregister(dataLinkPos);
            }
        }
    }

    private static final class Group {
        private final BlockPos mountPos;
        private final EnumMap<DataLinkBlockEntity.WeaponEndpointType, BlockPos> endpoints =
                new EnumMap<>(DataLinkBlockEntity.WeaponEndpointType.class);
        private final Set<BlockPos> dataLinks = new HashSet<>();

        private Group(BlockPos mountPos) {
            this.mountPos = mountPos;
        }

        private WeaponGroupView view() {
            return new WeaponGroupView(
                    mountPos,
                    endpoints.get(DataLinkBlockEntity.WeaponEndpointType.YAW),
                    endpoints.get(DataLinkBlockEntity.WeaponEndpointType.PITCH),
                    endpoints.get(DataLinkBlockEntity.WeaponEndpointType.FIRING),
                    Collections.unmodifiableSet(new HashSet<>(dataLinks))
            );
        }
    }

    private record LinkEntry(BlockPos dataLinkPos,
                             BlockPos endpointPos,
                             BlockPos mountPos,
                             DataLinkBlockEntity.WeaponEndpointType type) {
    }

    public record WeaponGroupView(BlockPos mountPos,
                                  @Nullable BlockPos yawPos,
                                  @Nullable BlockPos pitchPos,
                                  @Nullable BlockPos firingPos,
                                  Set<BlockPos> dataLinks) {
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
}
