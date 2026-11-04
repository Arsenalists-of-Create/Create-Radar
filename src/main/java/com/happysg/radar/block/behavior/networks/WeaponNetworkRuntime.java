package com.happysg.radar.block.behavior.networks;

import com.happysg.radar.api.mount.RadarMountAdapter;
import com.happysg.radar.api.mount.RadarMountRegistry;
import com.happysg.radar.block.controller.WeaponContactUnlinkState;
import com.happysg.radar.block.controller.firing.FireControllerBlockEntity;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.CannonMountContext;
import com.happysg.radar.compat.simulated.SimulatedSwivelMountAdapter;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.TreeMap;

/**
 * Server-only source of truth for weapon endpoint topology.
 *
 * <p>Controller-style Data Links are retained as explicit inputs. Physical
 * controller contact is deliberately transient and is rebuilt into an
 * immutable effective snapshot before the weapon coordinator runs.</p>
 */
public final class WeaponNetworkRuntime {
    private static final Map<ServerLevel, WeaponNetworkRuntime> BY_LEVEL = new WeakHashMap<>();
    private static final Comparator<BlockPos> STABLE_POSITION =
            Comparator.comparingInt((BlockPos position) -> position.getX())
                    .thenComparingInt(position -> position.getY())
                    .thenComparingInt(position -> position.getZ());

    private final ServerLevel level;
    private final Map<BlockPos, LinkEntry> linksByDataLink = new TreeMap<>(STABLE_POSITION);
    private final Map<BlockPos, DataLinkBlockEntity.WeaponEndpointType> contactControllers = new TreeMap<>(STABLE_POSITION);

    private RuntimeSnapshot snapshot = RuntimeSnapshot.empty();
    private boolean topologyDirty = true;
    private long lastReconciledTick = Long.MIN_VALUE;

    private WeaponNetworkRuntime(ServerLevel level) {
        this.level = level;
    }

    public static WeaponNetworkRuntime get(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level, WeaponNetworkRuntime::new);
    }

    @Nullable
    public static WeaponNetworkRuntime peek(ServerLevel level) {
        return BY_LEVEL.get(level);
    }

    public static void clear(ServerLevel level) {
        WeaponNetworkRuntime runtime = BY_LEVEL.remove(level);
        if (runtime != null) {
            runtime.linksByDataLink.clear();
            runtime.contactControllers.clear();
            runtime.snapshot = RuntimeSnapshot.empty();
            runtime.topologyDirty = true;
            runtime.lastReconciledTick = Long.MIN_VALUE;
        }
    }

    /**
     * Records that a loaded controller is eligible to participate in contact
     * discovery. The actual neighboring mount is intentionally not retained.
     */
    public void advertiseContactController(BlockEntity controller) {
        DataLinkBlockEntity.WeaponEndpointType type = endpointType(controller);
        if (type == DataLinkBlockEntity.WeaponEndpointType.NONE || controller.getLevel() != level || controller.isRemoved()) {
            return;
        }

        BlockPos position = controller.getBlockPos().immutable();
        DataLinkBlockEntity.WeaponEndpointType previous = contactControllers.put(position, type);

        if (previous != type) {
            if (controller instanceof WeaponContactUnlinkState unlink && WeaponControllerRelinkData.get(level).consume(position)) {
                unlink.suppressWeaponContact();
            }

            topologyDirty = true;
        }
    }

    public void unregisterContactController(BlockPos controllerPos) {
        if (contactControllers.remove(controllerPos) != null) {
            topologyDirty = true;
        }
    }

    public void rememberRelinkRequirement(BlockPos controllerPos) {
        if (controllerPos == null) {
            return;
        }

        BlockEntity controller = level.getBlockEntity(controllerPos);

        boolean previouslySuppressed = controller instanceof WeaponContactUnlinkState unlink && unlink.isWeaponContactSuppressed();
        boolean hadExplicitDataLink = !getExplicitMountsForController(controllerPos).isEmpty();

        if (previouslySuppressed || hadExplicitDataLink) {
            WeaponControllerRelinkData.get(level).mark(controllerPos);
        }
    }

    @Nullable
    private BlockPos resolveAvailableMount(BlockPos position) {
        if (position == null || !level.hasChunkAt(position)) {
            return null;
        }

        CannonMountContext cbc = CannonMountContext.resolveEndpoint(level, position);
        if (cbc != null && cbc.isCurrent()) {
            return cbc.getBlockPos().immutable();
        }

        RadarMountAdapter api = RadarMountRegistry.find(level, position);
        if (api == null || !api.isValid()) {
            return null;
        }

        BlockPos mountPos = api.getMountPos();
        if (mountPos == null || !level.hasChunkAt(mountPos)) {
            return null;
        }

        return mountPos.immutable();
    }

    /**
     * Marks live contact geometry dirty after an orientation or neighboring
     * block change. No contact-derived assignment itself is stored.
     */
    public void markContactTopologyDirty() {
        topologyDirty = true;
    }

    public boolean register(DataLinkBlockEntity dataLink) {
        if (dataLink.getLevel() != level) {
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

        LinkEntry entry = new LinkEntry(dataLinkPos, dataLink.getSourcePosition().immutable(), dataLink.getTargetPosition().immutable(), type);
        LinkEntry previous = linksByDataLink.get(dataLinkPos);
        if (entry.equals(previous)) {
            return true;
        }
        if (!canRegister(entry, previous)) {
            if (previous == null) {
                unregister(dataLinkPos);
            }
            return false;
        }

        linksByDataLink.put(dataLinkPos, entry);
        topologyDirty = true;
        return true;
    }

    private boolean canRegister(LinkEntry entry, @Nullable LinkEntry previous) {
        List<LinkEntry> endpointLinks = explicitLinksForEndpoint(entry.endpointPos(), previous);
        if (!canShareExplicitEndpoint(entry, endpointLinks)) {
            return false;
        }

        for (LinkEntry existing : linksByDataLink.values()) {
            if (existing.equals(previous)) {
                continue;
            }
            if (entry.type() != DataLinkBlockEntity.WeaponEndpointType.FIRING
                    && existing.type() == entry.type()
                    && existing.mountPos().equals(entry.mountPos())
                    && !existing.endpointPos().equals(entry.endpointPos())) {
                return false;
            }
        }

        return !hasAnyPitchReservationConflict(entry, previous);
    }

    public void unregister(BlockPos dataLinkPos) {
        if (linksByDataLink.remove(dataLinkPos) != null) {
            topologyDirty = true;
        }
    }

    @Nullable
    public BlockPos getMountForController(BlockPos controllerPos) {
        reconcileIfDirty();
        return snapshot.controllerToMount().get(controllerPos);
    }

    public List<BlockPos> getPendingControllerMountCandidates(BlockPos controllerPos) {
        if (controllerPos == null || !level.hasChunkAt(controllerPos) || !(level.getBlockEntity(controllerPos) instanceof AutoPitchControllerBlockEntity)) {
            return List.of();
        }

        return discoverContactMounts(controllerPos, DataLinkBlockEntity.WeaponEndpointType.PITCH);
    }

    /**
     * Compatibility query for Controller-style Data Link ownership. If a
     * T-Pitch has two explicit targets, this returns the first mount in stable
     * position order. Contact assignments never participate in this query.
     */
    @Nullable
    public BlockPos getExplicitMountForController(BlockPos controllerPos) {
        List<BlockPos> mounts = getExplicitMountsForController(controllerPos);
        return mounts.isEmpty() ? null : mounts.getFirst();
    }

    /**
     * Returns every canonical mount explicitly targeted by Controller-style
     * Data Links attached to this endpoint. Ordinary controllers have at most
     * one entry; a T-Pitch may have one entry for each crossbar side.
     */
    public List<BlockPos> getExplicitMountsForController(
            BlockPos controllerPos
    ) {
        pruneStaleInputs();
        return explicitLinksForEndpoint(controllerPos, null).stream()
                .map(LinkEntry::mountPos)
                .distinct()
                .sorted(STABLE_POSITION)
                .toList();
    }

    @Nullable
    public EndpointOrigin getEndpointOrigin(BlockPos controllerPos) {
        reconcileIfDirty();
        return snapshot.endpointOrigins().get(controllerPos);
    }

    /**
     * Compatibility alias for callers that describe the same value as the
     * controller's effective mount origin.
     */
    @Nullable
    public EndpointOrigin getOriginForController(BlockPos controllerPos) {
        return getEndpointOrigin(controllerPos);
    }

    @Nullable
    public WeaponGroupView getWeaponGroupViewFromEndpoint(BlockPos endpointPos) {
        reconcileIfDirty();
        BlockPos mountPos = snapshot.endpointToMount().get(endpointPos);
        return mountPos == null ? null : getWeaponGroupViewInternal(mountPos);
    }

    @Nullable
    public WeaponGroupView getWeaponGroupView(BlockPos mountPos) {
        reconcileIfDirty();
        return getWeaponGroupViewInternal(mountPos);
    }

    @Nullable
    private WeaponGroupView getWeaponGroupViewInternal(BlockPos mountPos) {
        Group group = snapshot.groupsByMount().get(mountPos);
        return group == null ? null : group.view();
    }

    @Nullable
    public WeaponControlView getWeaponControlViewFromPitch(BlockPos pitchPos) {
        reconcileIfDirty();
        return snapshot.controlViewsByPitch().get(pitchPos);
    }

    @Nullable
    public WeaponControlView getWeaponControlViewForMount(BlockPos mountPos) {
        reconcileIfDirty();

        Group direct = snapshot.groupsByMount().get(mountPos);
        if (direct != null && direct.pitchPos() != null) {
            WeaponControlView directView = snapshot.controlViewsByPitch().get(direct.pitchPos());
            if (directView != null && directView.channelForMount(mountPos) != null) {
                return directView;
            }
        }

        for (WeaponControlView view : snapshot.controlViewsByPitch().values()) {
            if (view.channelForMount(mountPos) != null) {
                return view;
            }
        }
        return null;
    }

    public List<BlockPos> getFiringControllersForMount(BlockPos mountPos) {
        reconcileIfDirty();

        Group group = snapshot.groupsByMount().get(mountPos);
        if (group == null || !group.validTopology()) {
            return List.of();
        }

        return group.associatedFiring().stream()
                .filter(pos -> mountPos.equals(snapshot.controllerToMount().get(pos)))
                .filter(pos -> level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof FireControllerBlockEntity)
                .sorted(STABLE_POSITION)
                .toList();
    }

    public Collection<WeaponGroupView> getGroups() {
        reconcileIfDirty();
        return snapshot.groupsByMount().values().stream().map(Group::view).toList();
    }

    /**
     * Placement validation intentionally consults explicit ownership only.
     * A Data Link may replace contact-derived ownership or ambiguity.
     */
    public boolean canAttachEndpoint(DataLinkBlockEntity.WeaponEndpointType type, BlockPos endpointPos, BlockPos mountPos) {
        pruneStaleInputs();
        if (type == DataLinkBlockEntity.WeaponEndpointType.NONE) {
            return false;
        }

        LinkEntry candidate = new LinkEntry(BlockPos.ZERO, endpointPos.immutable(), mountPos.immutable(), type);
        List<LinkEntry> endpointLinks = explicitLinksForEndpoint(endpointPos, null);
        if (!canShareExplicitEndpoint(candidate, endpointLinks)) {
            return false;
        }

        if (type != DataLinkBlockEntity.WeaponEndpointType.FIRING) {
            for (LinkEntry existing : linksByDataLink.values()) {
                if (existing.type() == type
                        && existing.mountPos().equals(mountPos)
                        && !existing.endpointPos().equals(endpointPos)) {
                    return false;
                }
            }
        }

        return !hasAnyPitchReservationConflict(candidate, null);
    }

    public boolean hasPitchReservationConflict(BlockPos endpointPos, BlockPos mountPos) {
        pruneStaleInputs();
        return hasPitchReservationConflictInternal(endpointPos, mountPos, null);
    }

    private boolean hasPitchReservationConflictInternal(BlockPos endpointPos, BlockPos mountPos, @Nullable LinkEntry replaced) {
        Set<BlockPos> acceptedTargets = acceptedDataLinkTargets(replaced);
        acceptedTargets.add(mountPos.immutable());
        Set<BlockPos> candidateClaims = pitchClaims(endpointPos, mountPos, acceptedTargets);

        for (LinkEntry existing : linksByDataLink.values()) {
            if (existing.equals(replaced)
                    || existing.type() != DataLinkBlockEntity.WeaponEndpointType.PITCH
                    || existing.endpointPos().equals(endpointPos)) {
                continue;
            }
            Set<BlockPos> existingClaims = pitchClaims(
                    existing.endpointPos(),
                    existing.mountPos(),
                    acceptedTargets);
            if (!Collections.disjoint(candidateClaims, existingClaims)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyPitchReservationConflict(LinkEntry candidate, @Nullable LinkEntry replaced) {
        Set<BlockPos> acceptedTargets = acceptedDataLinkTargets(replaced);
        acceptedTargets.add(candidate.mountPos());

        List<LinkEntry> pitchLinks = new ArrayList<>();
        for (LinkEntry existing : linksByDataLink.values()) {
            if (!existing.equals(replaced) && existing.type() == DataLinkBlockEntity.WeaponEndpointType.PITCH) {
                pitchLinks.add(existing);
            }
        }
        if (candidate.type() == DataLinkBlockEntity.WeaponEndpointType.PITCH) {
            pitchLinks.add(candidate);
        }

        for (int first = 0; first < pitchLinks.size(); first++) {
            LinkEntry firstLink = pitchLinks.get(first);
            Set<BlockPos> firstClaims = pitchClaims(
                    firstLink.endpointPos(),
                    firstLink.mountPos(),
                    acceptedTargets);
            for (int second = first + 1; second < pitchLinks.size(); second++) {
                LinkEntry secondLink = pitchLinks.get(second);
                if (firstLink.endpointPos().equals(secondLink.endpointPos())) continue;

                Set<BlockPos> secondClaims = pitchClaims(
                        secondLink.endpointPos(),
                        secondLink.mountPos(),
                        acceptedTargets);
                if (!Collections.disjoint(firstClaims, secondClaims)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<BlockPos> pitchClaims(BlockPos endpointPos, BlockPos mountPos, Set<BlockPos> acceptedTargets) {
        return Set.of(mountPos.immutable());
    }

    /**
     * Rebuilds all contact-derived topology atomically. The coordinator calls
     * this once after endpoint ticks and Sable relocation processing.
     */
    public void reconcile() {
        pruneStaleInputs();
        long gameTime = level.getGameTime();

        RuntimeSnapshot previous = snapshot;
        RuntimeSnapshot rebuilt = buildSnapshot();
        snapshot = rebuilt;
        lastReconciledTick = gameTime;
        topologyDirty = false;

        failClosedInvalidGroups(rebuilt);
        if (!previous.equals(rebuilt)) {
            notifyTopologyChanged(previous, rebuilt);
        }
        // Controller invalidation during notification may mark contact state
        // dirty even though it did not change contact geometry.
        topologyDirty = false;
    }

    private void reconcileIfDirty() {
        if (topologyDirty || lastReconciledTick == Long.MIN_VALUE) {
            reconcile();
        }
    }

    private RuntimeSnapshot buildSnapshot() {
        Map<BlockPos, List<LinkEntry>> explicitByEndpoint = new HashMap<>();
        Map<BlockPos, GroupBuilder> groups = new HashMap<>();
        Map<BlockPos, Boolean> mountAvailability = new HashMap<>();
        Set<LinkEntry> availableExplicitLinks = new HashSet<>();

        List<LinkEntry> explicitLinks = List.copyOf(linksByDataLink.values());
        for (LinkEntry link : explicitLinks) {
            explicitByEndpoint.computeIfAbsent(link.endpointPos(), ignored -> new ArrayList<>()).add(link);
            GroupBuilder builder = group(groups, link.mountPos());
            builder.dataLinks.add(link.dataLinkPos());
            builder.associatedEndpoints.add(link.endpointPos());
            if (link.type() == DataLinkBlockEntity.WeaponEndpointType.FIRING) {
                builder.associatedFiring.add(link.endpointPos());
            }
            boolean available = isExplicitEndpointAvailable(link) && mountAvailability.computeIfAbsent(link.mountPos(), this::isExplicitMountAvailable);

            if (available) {
                availableExplicitLinks.add(link);
            } else {
                builder.validTopology = false;
            }
        }

        Map<Slot, LinkedHashSet<BlockPos>> explicitClaims = new HashMap<>();
        Map<Slot, LinkedHashSet<BlockPos>> contactClaims = new HashMap<>();
        Map<BlockPos, ContactCandidate> contactCandidates = new HashMap<>();

        for (LinkEntry link : explicitLinks) {
            addClaim(explicitClaims, groups, link.mountPos(), link.type(), link.endpointPos());
        }

        List<Map.Entry<BlockPos, DataLinkBlockEntity.WeaponEndpointType>> contacts = List.copyOf(contactControllers.entrySet());
        for (Map.Entry<BlockPos, DataLinkBlockEntity.WeaponEndpointType> registered : contacts) {
            BlockPos endpointPos = registered.getKey();
            DataLinkBlockEntity.WeaponEndpointType type = registered.getValue();
            if (explicitByEndpoint.containsKey(endpointPos)) {
                continue;
            }

            BlockEntity endpointEntity = level.hasChunkAt(endpointPos) ? level.getBlockEntity(endpointPos) : null;
            if (endpointEntity instanceof WeaponContactUnlinkState unlink && unlink.isWeaponContactSuppressed()) {
                continue;
            }

            List<BlockPos> discovered = discoverContactMounts(endpointPos, type);
            if (discovered.isEmpty()) {
                continue;
            }

            List<BlockPos> available = discovered.stream().filter(mount -> type == DataLinkBlockEntity.WeaponEndpointType.FIRING || !explicitClaims.containsKey(new Slot(mount, type))).toList();
            ContactCandidate candidate = new ContactCandidate(type, List.copyOf(discovered), List.copyOf(available));
            contactCandidates.put(endpointPos, candidate);

            if (available.size() > 1) {
                for (BlockPos mount : available) {
                    GroupBuilder builder = group(groups, mount);
                    builder.validTopology = false;
                    builder.associatedEndpoints.add(endpointPos);

                    if (type == DataLinkBlockEntity.WeaponEndpointType.FIRING) {
                        builder.associatedFiring.add(endpointPos);
                    }
                }
                continue;
            }

            for (BlockPos mount : available) {
                addClaim(contactClaims, groups, mount, type, endpointPos);
            }
        }

        Set<Slot> allSlots = new HashSet<>(explicitClaims.keySet());
        allSlots.addAll(contactClaims.keySet());
        for (Slot slot : allSlots) {
            GroupBuilder builder = group(groups, slot.mountPos());

            Set<BlockPos> explicit = explicitClaims.get(slot);
            if (explicit == null) {
                explicit = Set.of();
            }

            Set<BlockPos> contact = contactClaims.get(slot);
            if (contact == null) {
                contact = Set.of();
            }

            BlockPos selected = null;
            if (explicit.size() > 1) {
                if (slot.type() != DataLinkBlockEntity.WeaponEndpointType.FIRING) {
                    builder.validTopology = false;
                }
                selected = stableFirst(explicit);
            } else if (explicit.size() == 1) {
                selected = explicit.iterator().next();
            } else if (contact.size() > 1) {
                if (slot.type() != DataLinkBlockEntity.WeaponEndpointType.FIRING) {
                    builder.validTopology = false;
                }
                selected = stableFirst(contact);
            } else if (contact.size() == 1) {
                selected = contact.iterator().next();
            }
            if (selected != null) {
                builder.endpoints.put(slot.type(), selected);
            }
        }

        Map<BlockPos, BlockPos> controllerToMount = new HashMap<>();
        Map<BlockPos, BlockPos> endpointToMount = new HashMap<>();
        Map<BlockPos, EndpointOrigin> endpointOrigins = new HashMap<>();

        for (LinkEntry link : explicitLinks) {
            if (availableExplicitLinks.contains(link)) {
                controllerToMount.put(link.endpointPos(), link.mountPos());
                endpointToMount.put(link.endpointPos(), link.mountPos());
                endpointOrigins.put(link.endpointPos(), EndpointOrigin.DATALINK);
            }
        }

        for (Map.Entry<BlockPos, ContactCandidate> entry : contactCandidates.entrySet()) {
            BlockPos endpoint = entry.getKey();
            ContactCandidate candidate = entry.getValue();
            if (candidate.availableMounts().size() == 1) {
                BlockPos mount = candidate.availableMounts().getFirst();
                controllerToMount.put(endpoint, mount);
                endpointToMount.put(endpoint, mount);
                endpointOrigins.put(endpoint, EndpointOrigin.CONTACT);
            }
        }

        Map<BlockPos, Group> immutableGroups = new HashMap<>();
        for (Map.Entry<BlockPos, GroupBuilder> entry : groups.entrySet()) {
            Group built = entry.getValue().build();
            if (!built.isEmpty() || !built.validTopology()) {
                immutableGroups.put(entry.getKey(), built);
            }
        }

        Map<BlockPos, WeaponControlView> controlViews = new HashMap<>();
        for (Map.Entry<BlockPos, DataLinkBlockEntity.WeaponEndpointType> registered : contacts) {
            if (registered.getValue() != DataLinkBlockEntity.WeaponEndpointType.PITCH) {
                continue;
            }
            BlockPos pitchPos = registered.getKey();
            buildSingleControlView(pitchPos, controllerToMount, immutableGroups, controlViews);
        }

        // A Data Link can register before the endpoint's first block-entity
        // tick advertises contact eligibility.
        for (LinkEntry link : explicitLinks) {
            if (link.type() != DataLinkBlockEntity.WeaponEndpointType.PITCH || controlViews.containsKey(link.endpointPos())) {
                continue;
            }
            buildSingleControlView(link.endpointPos(), controllerToMount, immutableGroups, controlViews);
        }

        return new RuntimeSnapshot(
                immutableMap(controllerToMount),
                immutableMap(endpointToMount),
                immutableMap(endpointOrigins),
                immutableMap(immutableGroups),
                immutableMap(controlViews));
    }

    private void buildSingleControlView(
            BlockPos pitchPos,
            Map<BlockPos, BlockPos> controllerToMount,
            Map<BlockPos, Group> groups,
            Map<BlockPos, WeaponControlView> controlViews
    ) {
        BlockPos mount = controllerToMount.get(pitchPos);
        Group group = mount == null ? null : groups.get(mount);
        if (group == null) {
            return;
        }
        controlViews.put(pitchPos, new WeaponControlView(
                WeaponNetworkMode.SINGLE,
                pitchPos.immutable(),
                mount.immutable(),
                List.of(channel(group)),
                group.dataLinks(),
                group.validTopology()));
    }

    private List<BlockPos> discoverContactMounts(BlockPos endpointPos, DataLinkBlockEntity.WeaponEndpointType type) {
        BlockEntity endpoint = level.getBlockEntity(endpointPos);
        if (endpoint == null) {
            return List.of();
        }

        LinkedHashSet<BlockPos> mounts = new LinkedHashSet<>();
        if (endpoint instanceof AutoPitchControllerBlockEntity pitch && type == DataLinkBlockEntity.WeaponEndpointType.PITCH) {
            BlockState state = endpoint.getBlockState();

            if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
                Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
                addDirectMount(mounts, endpointPos.relative(facing));

                if (mounts.isEmpty() && Mods.SIMULATED.isLoaded()) {
                    Direction.Axis swivelAxis = facing.getAxis();

                    for (LinkEntry link : linksByDataLink.values()) {
                        if (link.type() != DataLinkBlockEntity.WeaponEndpointType.YAW || !isExplicitEndpointAvailable(link)) {
                            continue;
                        }

                        BlockPos mountPos = resolveAvailableMount(link.mountPos());

                        if (mountPos == null || !mountPos.equals(link.mountPos())) {
                            continue;
                        }

                        if (SimulatedSwivelMountAdapter.isMountOnControllerSwivel(pitch, swivelAxis, mountPos)) {
                            mounts.add(mountPos);
                        }
                    }
                }
            }
        } else if (endpoint instanceof AutoYawControllerBlockEntity && type == DataLinkBlockEntity.WeaponEndpointType.YAW) {
            BlockState state = endpoint.getBlockState();
            if (state.hasProperty(DirectionalKineticBlock.FACING)) {
                Direction mountingFace = state.getValue(DirectionalKineticBlock.FACING).getOpposite();
                if (mountingFace.getAxis() == Direction.Axis.Y) {
                    addDirectMount(mounts, endpointPos.relative(mountingFace));
                }
            }
        } else if (endpoint instanceof FireControllerBlockEntity && type == DataLinkBlockEntity.WeaponEndpointType.FIRING) {
            for (Direction direction : Direction.values()) {
                addDirectMount(mounts, endpointPos.relative(direction));
            }
        }
        return mounts.stream().sorted(STABLE_POSITION).toList();
    }

    private void addDirectMount(Set<BlockPos> mounts, BlockPos endpointPos) {
        BlockPos mountPos = resolveAvailableMount(endpointPos);

        if (mountPos != null) {
            mounts.add(mountPos);
        }
    }

    private static void addClaim(
            Map<Slot, LinkedHashSet<BlockPos>> claims,
            Map<BlockPos, GroupBuilder> groups,
            BlockPos mount,
            DataLinkBlockEntity.WeaponEndpointType type,
            BlockPos endpoint
    ) {
        BlockPos immutableMount = mount.immutable();
        BlockPos immutableEndpoint = endpoint.immutable();
        claims.computeIfAbsent(new Slot(immutableMount, type), ignored -> new LinkedHashSet<>()).add(immutableEndpoint);
        GroupBuilder builder = group(groups, immutableMount);
        builder.associatedEndpoints.add(immutableEndpoint);
        if (type == DataLinkBlockEntity.WeaponEndpointType.FIRING) {
            builder.associatedFiring.add(immutableEndpoint);
        }
    }

    private static GroupBuilder group(Map<BlockPos, GroupBuilder> groups, BlockPos mount) {
        BlockPos immutable = mount.immutable();
        return groups.computeIfAbsent(immutable, GroupBuilder::new);
    }

    private List<LinkEntry> explicitLinksForEndpoint(BlockPos endpointPos, @Nullable LinkEntry ignored) {
        List<LinkEntry> entries = new ArrayList<>();
        for (LinkEntry entry : linksByDataLink.values()) {
            if (!entry.equals(ignored) && entry.endpointPos().equals(endpointPos)) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparing(LinkEntry::mountPos, STABLE_POSITION).thenComparing(LinkEntry::dataLinkPos, STABLE_POSITION));
        return List.copyOf(entries);
    }

    private boolean canShareExplicitEndpoint(LinkEntry candidate, List<LinkEntry> existing) {
        return existing.isEmpty();
    }

    private Set<BlockPos> acceptedDataLinkTargets(@Nullable LinkEntry ignored) {
        Set<BlockPos> targets = new HashSet<>();
        for (LinkEntry entry : linksByDataLink.values()) {
            if (!entry.equals(ignored)) {
                targets.add(entry.mountPos());
            }
        }
        return targets;
    }

    private void pruneStaleInputs() {
        boolean removed = linksByDataLink.entrySet().removeIf(entry -> {
            BlockPos dataLinkPos = entry.getKey();
            if (!level.isLoaded(dataLinkPos)) {
                return false;
            }

            BlockEntity blockEntity = level.getBlockEntity(dataLinkPos);
            if (!(blockEntity instanceof DataLinkBlockEntity dataLink)) {
                return true;
            }

            BlockState state = dataLink.getBlockState();
            return !(state.getBlock() instanceof DataLinkBlock)
                    || !state.hasProperty(DataLinkBlock.LINK_STYLE)
                    || state.getValue(DataLinkBlock.LINK_STYLE) != DataLinkBlock.LinkStyle.CONTROLLER
                    || dataLink.getWeaponEndpointType() != entry.getValue().type()
                    || !dataLink.getSourcePosition().equals(entry.getValue().endpointPos())
                    || !dataLink.getTargetPosition().equals(entry.getValue().mountPos());
        });

        removed |= contactControllers.entrySet().removeIf(entry -> {
            BlockPos position = entry.getKey();
            if (!level.isLoaded(position)) {
                return true;
            }

            BlockEntity controller = level.getBlockEntity(position);
            return controller == null || controller.isRemoved() || endpointType(controller) != entry.getValue();
        });
        if (removed) {
            topologyDirty = true;
        }
    }

    private static DataLinkBlockEntity.WeaponEndpointType endpointType(@Nullable BlockEntity controller) {
        if (controller instanceof AutoPitchControllerBlockEntity) {
            return DataLinkBlockEntity.WeaponEndpointType.PITCH;
        }
        if (controller instanceof AutoYawControllerBlockEntity) {
            return DataLinkBlockEntity.WeaponEndpointType.YAW;
        }
        if (controller instanceof FireControllerBlockEntity) {
            return DataLinkBlockEntity.WeaponEndpointType.FIRING;
        }
        return DataLinkBlockEntity.WeaponEndpointType.NONE;
    }

    private void failClosedInvalidGroups(RuntimeSnapshot current) {
        Set<BlockPos> invalidEndpoints = new HashSet<>();
        Set<BlockPos> invalidFiring = new HashSet<>();
        for (Group group : current.groupsByMount().values()) {
            if (!group.validTopology()) {
                invalidEndpoints.addAll(group.associatedEndpoints());
                invalidFiring.addAll(group.associatedFiring());
            }
        }

        for (BlockPos endpoint : invalidEndpoints) {
            BlockEntity blockEntity = level.getBlockEntity(endpoint);
            if (blockEntity instanceof AutoPitchControllerBlockEntity pitch) {
                pitch.onWeaponTopologyChanged();
            } else if (blockEntity instanceof AutoYawControllerBlockEntity yaw) {
                yaw.onWeaponTopologyChanged();
            }
        }
        for (BlockPos firing : invalidFiring) {
            if (level.getBlockEntity(firing) instanceof FireControllerBlockEntity fire) {
                fire.setPowered(false);
            }
        }
    }

    private void notifyTopologyChanged(RuntimeSnapshot previous, RuntimeSnapshot current) {
        Set<BlockPos> endpoints = new HashSet<>();
        addAssociatedEndpoints(previous, endpoints);
        addAssociatedEndpoints(current, endpoints);
        endpoints.addAll(previous.controllerToMount().keySet());
        endpoints.addAll(current.controllerToMount().keySet());

        Set<BlockPos> filterers = new HashSet<>();
        NetworkData networkData = NetworkData.get(level);
        for (BlockPos endpoint : endpoints) {
            BlockEntity blockEntity = level.getBlockEntity(endpoint);
            if (blockEntity instanceof AutoPitchControllerBlockEntity pitch) {
                pitch.markMountDirtyExternal();
                pitch.onWeaponTopologyChanged();
                if (pitch.firingControl != null) pitch.firingControl.refreshControllers();
            } else if (blockEntity instanceof AutoYawControllerBlockEntity yaw) {
                yaw.markMountDirtyExternal();
                yaw.onWeaponTopologyChanged();
            } else if (blockEntity instanceof FireControllerBlockEntity fire) {
                fire.setPowered(false);
            }

            BlockPos filterer = networkData.getFiltererForEndpoint(level.dimension(), endpoint);
            if (filterer != null) {
                filterers.add(filterer);
            }
        }

        for (BlockPos filtererPos : filterers) {
            if (level.getBlockEntity(filtererPos) instanceof NetworkFiltererBlockEntity filterer) {
                filterer.onWeaponTopologyChanged();
            }
        }
    }

    private static void addAssociatedEndpoints(RuntimeSnapshot source, Set<BlockPos> destination) {
        for (Group group : source.groupsByMount().values()) {
            destination.addAll(group.associatedEndpoints());
        }
    }

    private boolean isExplicitEndpointAvailable(LinkEntry link) {
        if (!level.isLoaded(link.dataLinkPos()) || !level.isLoaded(link.endpointPos())) {
            return false;
        }

        return endpointType(level.getBlockEntity(link.endpointPos())) == link.type();
    }

    private boolean isExplicitMountAvailable(BlockPos mountPos) {
        BlockPos resolved = resolveAvailableMount(mountPos);
        return resolved != null && resolved.equals(mountPos);
    }

    /**
     * Moves still-live explicit links from one canonical mount coordinate to
     * another. Contact assignments are rebuilt after the relocation.
     */
    public boolean relocateMountGroup(BlockPos oldMount, BlockPos newMount, DataLinkBlockEntity movedLink, BlockPos movedEndpoint) {
        return relocateMountGroup(oldMount, newMount, Map.of(movedLink, movedEndpoint));
    }

    /**
     * Atomically relocates every supplied link together with any still-live
     * links that retain the old mount. No block-entity or runtime entry is
     * retained if validation or registration fails.
     */
    public boolean relocateMountGroup(BlockPos oldMount, BlockPos newMount, Map<DataLinkBlockEntity, BlockPos> movedEndpoints) {
        pruneStaleInputs();

        List<DataLinkBlockEntity> linksToMove = new ArrayList<>();
        for (LinkEntry entry : new ArrayList<>(linksByDataLink.values())) {
            if (!entry.mountPos().equals(oldMount)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(entry.dataLinkPos());
            if (blockEntity instanceof DataLinkBlockEntity dataLink) {
                linksToMove.add(dataLink);
            }
        }
        for (DataLinkBlockEntity movedLink : movedEndpoints.keySet()) {
            if (linksToMove.stream().noneMatch(link -> link.getBlockPos().equals(movedLink.getBlockPos()))) {
                linksToMove.add(movedLink);
            }
        }

        EnumMap<DataLinkBlockEntity.WeaponEndpointType, BlockPos> desired = new EnumMap<>(DataLinkBlockEntity.WeaponEndpointType.class);
        for (LinkEntry entry : linksByDataLink.values()) {
            if (entry.mountPos().equals(newMount)) {
                desired.put(entry.type(), entry.endpointPos());
            }
        }

        for (DataLinkBlockEntity link : linksToMove) {
            DataLinkBlockEntity.WeaponEndpointType type = link.getWeaponEndpointType();
            if (type == DataLinkBlockEntity.WeaponEndpointType.NONE) {
                continue;
            }
            BlockPos endpoint = movedEndpoints.getOrDefault(link, link.getSourcePosition());
            BlockPos existing = desired.putIfAbsent(type, endpoint);
            if (existing != null && !existing.equals(endpoint)) {
                return false;
            }
            boolean mappedElsewhere = getExplicitMountsForController(endpoint)
                    .stream()
                    .anyMatch(mappedMount -> !mappedMount.equals(oldMount) && !mappedMount.equals(newMount));
            if (mappedElsewhere) {
                return false;
            }
        }

        Map<BlockPos, LinkEntry> originalEntries = new HashMap<>(linksByDataLink);
        Map<DataLinkBlockEntity, BlockPos> originalTargets = new HashMap<>();
        for (DataLinkBlockEntity link : linksToMove) {
            originalTargets.put(link, link.getTargetPosition().immutable());
        }

        try {
            for (DataLinkBlockEntity link : linksToMove) {
                link.finishAssemblyRelocation(newMount);
                if (!register(link)) {
                    restoreMountRelocation(originalEntries, originalTargets);
                    return false;
                }
            }
        } catch (RuntimeException exception) {
            restoreMountRelocation(originalEntries, originalTargets);
            throw exception;
        }
        topologyDirty = true;
        return true;
    }

    private void restoreMountRelocation(Map<BlockPos, LinkEntry> originalEntries, Map<DataLinkBlockEntity, BlockPos> originalTargets) {
        for (Map.Entry<DataLinkBlockEntity, BlockPos> entry : originalTargets.entrySet()) {
            entry.getKey().finishAssemblyRelocation(entry.getValue());
        }
        linksByDataLink.clear();
        linksByDataLink.putAll(originalEntries);
        topologyDirty = true;
    }

    private static BlockPos stableFirst(Collection<BlockPos> positions) {
        return positions.stream().min(STABLE_POSITION).orElseThrow();
    }

    private static MountChannelView channel(Group group) {
        return new MountChannelView(group.mountPos(), group.yawPos(), group.firingPos());
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new HashMap<>(source));
    }

    private record LinkEntry(BlockPos dataLinkPos, BlockPos endpointPos, BlockPos mountPos, DataLinkBlockEntity.WeaponEndpointType type) {}
    private record Slot(BlockPos mountPos, DataLinkBlockEntity.WeaponEndpointType type) {}
    private record ContactCandidate(DataLinkBlockEntity.WeaponEndpointType type, List<BlockPos> discoveredMounts, List<BlockPos> availableMounts) {}

    private static final class GroupBuilder {
        private final BlockPos mountPos;
        private final EnumMap<DataLinkBlockEntity.WeaponEndpointType, BlockPos> endpoints = new EnumMap<>(DataLinkBlockEntity.WeaponEndpointType.class);
        private final Set<BlockPos> dataLinks = new HashSet<>();
        private final Set<BlockPos> associatedEndpoints = new HashSet<>();
        private final Set<BlockPos> associatedFiring = new HashSet<>();
        private boolean validTopology = true;

        private GroupBuilder(BlockPos mountPos) {
            this.mountPos = mountPos.immutable();
        }

        private Group build() {
            return new Group(
                    mountPos,
                    endpoints.get(DataLinkBlockEntity.WeaponEndpointType.YAW),
                    endpoints.get(DataLinkBlockEntity.WeaponEndpointType.PITCH),
                    endpoints.get(DataLinkBlockEntity.WeaponEndpointType.FIRING),
                    Collections.unmodifiableSet(new HashSet<>(dataLinks)),
                    Collections.unmodifiableSet(new HashSet<>(associatedEndpoints)),
                    Collections.unmodifiableSet(new HashSet<>(associatedFiring)), validTopology);
        }
    }

    private record Group(
            BlockPos mountPos,
            @Nullable BlockPos yawPos,
            @Nullable BlockPos pitchPos,
            @Nullable BlockPos firingPos,
            Set<BlockPos> dataLinks,
            Set<BlockPos> associatedEndpoints,
            Set<BlockPos> associatedFiring,
            boolean validTopology
    ) {
        private WeaponGroupView view() {
            return new WeaponGroupView(mountPos, yawPos, pitchPos, firingPos, dataLinks);
        }

        private boolean isEmpty() {
            return yawPos == null
                    && pitchPos == null
                    && firingPos == null
                    && dataLinks.isEmpty()
                    && associatedEndpoints.isEmpty();
        }
    }

    private record RuntimeSnapshot(
            Map<BlockPos, BlockPos> controllerToMount,
            Map<BlockPos, BlockPos> endpointToMount,
            Map<BlockPos, EndpointOrigin> endpointOrigins,
            Map<BlockPos, Group> groupsByMount,
            Map<BlockPos, WeaponControlView> controlViewsByPitch
    ) {
        private static RuntimeSnapshot empty() {
            return new RuntimeSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    public enum EndpointOrigin { CONTACT, DATALINK }
    public record WeaponGroupView(
            BlockPos mountPos,
            @Nullable BlockPos yawPos,
            @Nullable BlockPos pitchPos,
            @Nullable BlockPos firingPos,
            Set<BlockPos> dataLinks
    ) {
        public WeaponGroupView {
            dataLinks = Collections.unmodifiableSet(new HashSet<>(dataLinks));
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

    public enum WeaponNetworkMode { SINGLE }
    public record MountChannelView(BlockPos mountPos, @Nullable BlockPos yawPos, @Nullable BlockPos firingPos) {}
    public record WeaponControlView(
            WeaponNetworkMode mode,
            BlockPos pitchPos,
            BlockPos preferredMountPos,
            List<MountChannelView> channels,
            Set<BlockPos> dataLinks,
            boolean validTopology
    ) {
        public WeaponControlView {
            channels = List.copyOf(channels);
            dataLinks = Collections.unmodifiableSet(new HashSet<>(dataLinks));
        }

        @Nullable
        public MountChannelView channelForMount(BlockPos mountPos) {
            for (MountChannelView channel : channels) {
                if (channel.mountPos().equals(mountPos)) {
                    return channel;
                }
            }
            return null;
        }
    }
}
