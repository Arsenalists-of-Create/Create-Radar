package com.happysg.radar.block.behavior.networks;

import com.happysg.radar.block.controller.firing.FireControllerBlockEntity;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.tpitch.TPitchControllerBlock;
import com.happysg.radar.block.controller.tpitch.TPitchControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.happysg.radar.compat.cbc.CannonMountContext;
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

/**
 * Server-only source of truth for weapon endpoint topology.
 *
 * <p>Controller-style Data Links are retained as explicit inputs. Physical
 * controller contact is deliberately transient and is rebuilt into an
 * immutable effective snapshot before the weapon coordinator runs.</p>
 */
public final class WeaponNetworkRuntime {
    private static final Map<ServerLevel, WeaponNetworkRuntime> BY_LEVEL =
            new WeakHashMap<>();
    private static final Comparator<BlockPos> STABLE_POSITION =
            Comparator.comparingInt((BlockPos position) -> position.getX())
                    .thenComparingInt(position -> position.getY())
                    .thenComparingInt(position -> position.getZ());

    private final ServerLevel level;
    private final Map<BlockPos, LinkEntry> linksByDataLink = new HashMap<>();
    private final Map<BlockPos, DataLinkBlockEntity.WeaponEndpointType>
            contactControllers = new HashMap<>();

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
        DataLinkBlockEntity.WeaponEndpointType type =
                endpointType(controller);
        if (type == DataLinkBlockEntity.WeaponEndpointType.NONE
                || controller.getLevel() != level
                || controller.isRemoved()) {
            return;
        }

        BlockPos position = controller.getBlockPos().immutable();
        DataLinkBlockEntity.WeaponEndpointType previous =
                contactControllers.put(position, type);
        if (previous != type) {
            topologyDirty = true;
        }
    }

    public void unregisterContactController(BlockPos controllerPos) {
        if (contactControllers.remove(controllerPos) != null) {
            topologyDirty = true;
        }
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
        DataLinkBlockEntity.WeaponEndpointType type =
                dataLink.getWeaponEndpointType();
        if (type == DataLinkBlockEntity.WeaponEndpointType.NONE) {
            unregister(dataLinkPos);
            return false;
        }

        BlockState state = dataLink.getBlockState();
        if (!(state.getBlock() instanceof DataLinkBlock)
                || !state.hasProperty(DataLinkBlock.LINK_STYLE)
                || state.getValue(DataLinkBlock.LINK_STYLE)
                != DataLinkBlock.LinkStyle.CONTROLLER) {
            unregister(dataLinkPos);
            return false;
        }

        LinkEntry entry = new LinkEntry(
                dataLinkPos,
                dataLink.getSourcePosition().immutable(),
                dataLink.getTargetPosition().immutable(),
                type);
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
        LinkEntry endpointOwner = explicitLinkForEndpoint(
                entry.endpointPos(), previous);
        if (endpointOwner != null) {
            return false;
        }

        for (LinkEntry existing : linksByDataLink.values()) {
            if (existing.equals(previous)) {
                continue;
            }
            if (existing.type() == entry.type()
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

    /**
     * Returns only Controller-style Data Link ownership. Contact assignments
     * never participate in placement validation through this query.
     */
    @Nullable
    public BlockPos getExplicitMountForController(BlockPos controllerPos) {
        pruneStaleInputs();
        LinkEntry entry = explicitLinkForEndpoint(controllerPos, null);
        return entry == null ? null : entry.mountPos();
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
    public WeaponGroupView getWeaponGroupViewFromEndpoint(
            BlockPos endpointPos
    ) {
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
    public WeaponControlView getWeaponControlViewFromPitch(
            BlockPos pitchPos
    ) {
        reconcileIfDirty();
        return snapshot.controlViewsByPitch().get(pitchPos);
    }

    @Nullable
    public WeaponControlView getWeaponControlViewForMount(BlockPos mountPos) {
        reconcileIfDirty();

        Group direct = snapshot.groupsByMount().get(mountPos);
        if (direct != null && direct.pitchPos() != null) {
            WeaponControlView directView =
                    snapshot.controlViewsByPitch().get(direct.pitchPos());
            if (directView != null
                    && directView.channelForMount(mountPos) != null) {
                return directView;
            }
        }

        for (WeaponControlView view
                : snapshot.controlViewsByPitch().values()) {
            if (view.channelForMount(mountPos) != null) {
                return view;
            }
        }
        return null;
    }

    public Collection<WeaponGroupView> getGroups() {
        reconcileIfDirty();
        return snapshot.groupsByMount().values().stream()
                .map(Group::view)
                .toList();
    }

    /**
     * Placement validation intentionally consults explicit ownership only.
     * A Data Link may replace contact-derived ownership or ambiguity.
     */
    public boolean canAttachEndpoint(
            DataLinkBlockEntity.WeaponEndpointType type,
            BlockPos endpointPos,
            BlockPos mountPos
    ) {
        pruneStaleInputs();
        if (type == DataLinkBlockEntity.WeaponEndpointType.NONE) {
            return false;
        }

        LinkEntry endpointOwner = explicitLinkForEndpoint(endpointPos, null);
        if (endpointOwner != null
                && !endpointOwner.mountPos().equals(mountPos)) {
            return false;
        }

        for (LinkEntry existing : linksByDataLink.values()) {
            if (existing.type() == type
                    && existing.mountPos().equals(mountPos)
                    && !existing.endpointPos().equals(endpointPos)) {
                return false;
            }
        }

        LinkEntry candidate = new LinkEntry(
                BlockPos.ZERO,
                endpointPos.immutable(),
                mountPos.immutable(),
                type);
        return !hasAnyPitchReservationConflict(candidate, null);
    }

    public boolean hasPitchReservationConflict(
            BlockPos endpointPos,
            BlockPos mountPos
    ) {
        pruneStaleInputs();
        return hasPitchReservationConflictInternal(
                endpointPos, mountPos, null);
    }

    private boolean hasPitchReservationConflictInternal(
            BlockPos endpointPos,
            BlockPos mountPos,
            @Nullable LinkEntry replaced
    ) {
        Set<BlockPos> acceptedTargets = acceptedDataLinkTargets(replaced);
        acceptedTargets.add(mountPos.immutable());
        Set<BlockPos> candidateClaims =
                pitchClaims(endpointPos, mountPos, acceptedTargets);

        for (LinkEntry existing : linksByDataLink.values()) {
            if (existing.equals(replaced)
                    || existing.type()
                    != DataLinkBlockEntity.WeaponEndpointType.PITCH
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

    private boolean hasAnyPitchReservationConflict(
            LinkEntry candidate,
            @Nullable LinkEntry replaced
    ) {
        Set<BlockPos> acceptedTargets = acceptedDataLinkTargets(replaced);
        acceptedTargets.add(candidate.mountPos());

        List<LinkEntry> pitchLinks = new ArrayList<>();
        for (LinkEntry existing : linksByDataLink.values()) {
            if (!existing.equals(replaced)
                    && existing.type()
                    == DataLinkBlockEntity.WeaponEndpointType.PITCH) {
                pitchLinks.add(existing);
            }
        }
        if (candidate.type()
                == DataLinkBlockEntity.WeaponEndpointType.PITCH) {
            pitchLinks.add(candidate);
        }

        for (int first = 0; first < pitchLinks.size(); first++) {
            LinkEntry firstLink = pitchLinks.get(first);
            Set<BlockPos> firstClaims = pitchClaims(
                    firstLink.endpointPos(),
                    firstLink.mountPos(),
                    acceptedTargets);
            for (int second = first + 1;
                 second < pitchLinks.size(); second++) {
                LinkEntry secondLink = pitchLinks.get(second);
                if (firstLink.endpointPos().equals(
                        secondLink.endpointPos())) {
                    continue;
                }
                Set<BlockPos> secondClaims = pitchClaims(
                        secondLink.endpointPos(),
                        secondLink.mountPos(),
                        acceptedTargets);
                if (!Collections.disjoint(
                        firstClaims, secondClaims)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<BlockPos> pitchClaims(
            BlockPos endpointPos,
            BlockPos mountPos,
            Set<BlockPos> acceptedTargets
    ) {
        LinkedHashSet<BlockPos> claims = new LinkedHashSet<>();
        claims.add(mountPos.immutable());
        if (level.getBlockEntity(endpointPos)
                instanceof TPitchControllerBlockEntity) {
            claims.addAll(eligibleTPitchMounts(
                    endpointPos, acceptedTargets));
        }
        return claims;
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
        Map<BlockPos, LinkEntry> explicitByEndpoint = new HashMap<>();
        Set<BlockPos> targetedMounts = new HashSet<>();
        Map<BlockPos, GroupBuilder> groups = new HashMap<>();

        List<LinkEntry> explicitLinks = linksByDataLink.values().stream()
                .sorted(Comparator.comparing(
                        LinkEntry::dataLinkPos, STABLE_POSITION))
                .toList();
        for (LinkEntry link : explicitLinks) {
            explicitByEndpoint.putIfAbsent(
                    link.endpointPos(), link);
            targetedMounts.add(link.mountPos());
            GroupBuilder builder = group(groups, link.mountPos());
            builder.dataLinks.add(link.dataLinkPos());
            builder.associatedEndpoints.add(link.endpointPos());
            if (link.type()
                    == DataLinkBlockEntity.WeaponEndpointType.FIRING) {
                builder.associatedFiring.add(link.endpointPos());
            }
            if (!isExplicitEndpointAvailable(link)
                    || !isExplicitMountAvailable(link.mountPos())) {
                builder.validTopology = false;
            }
        }

        Map<Slot, LinkedHashSet<BlockPos>> explicitClaims =
                new HashMap<>();
        Map<Slot, LinkedHashSet<BlockPos>> contactClaims =
                new HashMap<>();
        Map<BlockPos, ContactCandidate> contactCandidates =
                new HashMap<>();

        for (LinkEntry link : explicitLinks) {
            Set<BlockPos> mounts;
            if (link.type()
                    == DataLinkBlockEntity.WeaponEndpointType.PITCH
                    && level.getBlockEntity(link.endpointPos())
                    instanceof TPitchControllerBlockEntity) {
                mounts = new LinkedHashSet<>(eligibleTPitchMounts(
                        link.endpointPos(), targetedMounts));
                // Retain the explicit reservation while its physical mount is
                // temporarily absent; no control channel is created for it.
                mounts.add(link.mountPos());
            } else {
                mounts = Set.of(link.mountPos());
            }
            for (BlockPos mount : mounts) {
                addClaim(explicitClaims, groups, mount, link.type(),
                        link.endpointPos());
            }
        }

        List<Map.Entry<BlockPos,
                DataLinkBlockEntity.WeaponEndpointType>> contacts =
                contactControllers.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(STABLE_POSITION))
                        .toList();
        for (Map.Entry<BlockPos,
                DataLinkBlockEntity.WeaponEndpointType> registered
                : contacts) {
            BlockPos endpointPos = registered.getKey();
            DataLinkBlockEntity.WeaponEndpointType type =
                    registered.getValue();
            if (explicitByEndpoint.containsKey(endpointPos)) {
                continue;
            }

            List<BlockPos> discovered = discoverContactMounts(
                    endpointPos, type, targetedMounts);
            if (discovered.isEmpty()) {
                continue;
            }

            List<BlockPos> available = discovered.stream()
                    .filter(mount -> !explicitClaims.containsKey(
                            new Slot(mount, type)))
                    .toList();
            ContactCandidate candidate = new ContactCandidate(
                    type, List.copyOf(discovered),
                    List.copyOf(available));
            contactCandidates.put(endpointPos, candidate);

            if (type != DataLinkBlockEntity.WeaponEndpointType.PITCH
                    || !(level.getBlockEntity(endpointPos)
                    instanceof TPitchControllerBlockEntity)) {
                if (available.size() > 1) {
                    for (BlockPos mount : available) {
                        GroupBuilder builder = group(groups, mount);
                        builder.validTopology = false;
                        builder.associatedEndpoints.add(endpointPos);
                        if (type == DataLinkBlockEntity
                                .WeaponEndpointType.FIRING) {
                            builder.associatedFiring.add(endpointPos);
                        }
                    }
                    continue;
                }
            }

            for (BlockPos mount : available) {
                addClaim(contactClaims, groups, mount, type, endpointPos);
            }
        }

        Set<Slot> allSlots = new HashSet<>(explicitClaims.keySet());
        allSlots.addAll(contactClaims.keySet());
        for (Slot slot : allSlots) {
            GroupBuilder builder = group(groups, slot.mountPos());
            LinkedHashSet<BlockPos> explicit =
                    explicitClaims.getOrDefault(
                            slot, new LinkedHashSet<>());
            LinkedHashSet<BlockPos> contact =
                    contactClaims.getOrDefault(
                            slot, new LinkedHashSet<>());

            BlockPos selected = null;
            if (explicit.size() > 1) {
                builder.validTopology = false;
                selected = stableFirst(explicit);
            } else if (explicit.size() == 1) {
                selected = explicit.iterator().next();
            } else if (contact.size() > 1) {
                builder.validTopology = false;
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
            if (!(level.getBlockEntity(link.endpointPos())
                    instanceof TPitchControllerBlockEntity)
                    && isExplicitEndpointAvailable(link)
                    && isExplicitMountAvailable(link.mountPos())) {
                controllerToMount.put(
                        link.endpointPos(), link.mountPos());
                endpointToMount.put(
                        link.endpointPos(), link.mountPos());
                endpointOrigins.put(
                        link.endpointPos(), EndpointOrigin.DATALINK);
            }
        }

        for (Map.Entry<BlockPos, ContactCandidate> entry
                : contactCandidates.entrySet()) {
            BlockPos endpoint = entry.getKey();
            ContactCandidate candidate = entry.getValue();
            if (candidate.availableMounts().size() == 1
                    && !(level.getBlockEntity(endpoint)
                    instanceof TPitchControllerBlockEntity)) {
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
        for (Map.Entry<BlockPos,
                DataLinkBlockEntity.WeaponEndpointType> registered
                : contacts) {
            if (registered.getValue()
                    != DataLinkBlockEntity.WeaponEndpointType.PITCH) {
                continue;
            }
            BlockPos pitchPos = registered.getKey();
            if (level.getBlockEntity(pitchPos)
                    instanceof TPitchControllerBlockEntity) {
                buildTPitchControlView(
                        pitchPos,
                        explicitByEndpoint.get(pitchPos),
                        targetedMounts,
                        explicitClaims,
                        immutableGroups,
                        controllerToMount,
                        endpointToMount,
                        endpointOrigins,
                        controlViews);
            } else {
                buildSingleControlView(
                        pitchPos,
                        controllerToMount,
                        immutableGroups,
                        controlViews);
            }
        }

        // A Data Link can register before the endpoint's first block-entity
        // tick advertises contact eligibility.
        for (LinkEntry link : explicitLinks) {
            if (link.type()
                    != DataLinkBlockEntity.WeaponEndpointType.PITCH
                    || controlViews.containsKey(link.endpointPos())) {
                continue;
            }
            if (level.getBlockEntity(link.endpointPos())
                    instanceof TPitchControllerBlockEntity) {
                buildTPitchControlView(
                        link.endpointPos(),
                        link,
                        targetedMounts,
                        explicitClaims,
                        immutableGroups,
                        controllerToMount,
                        endpointToMount,
                        endpointOrigins,
                        controlViews);
            } else {
                buildSingleControlView(
                        link.endpointPos(),
                        controllerToMount,
                        immutableGroups,
                        controlViews);
            }
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

    private void buildTPitchControlView(
            BlockPos pitchPos,
            @Nullable LinkEntry explicitLink,
            Set<BlockPos> targetedMounts,
            Map<Slot, LinkedHashSet<BlockPos>> explicitClaims,
            Map<BlockPos, Group> groups,
            Map<BlockPos, BlockPos> controllerToMount,
            Map<BlockPos, BlockPos> endpointToMount,
            Map<BlockPos, EndpointOrigin> endpointOrigins,
            Map<BlockPos, WeaponControlView> controlViews
    ) {
        List<BlockPos> eligible =
                eligibleTPitchMounts(pitchPos, targetedMounts);
        List<BlockPos> retained = new ArrayList<>(2);
        for (BlockPos mount : eligible) {
            Set<BlockPos> explicitPitch =
                    explicitClaims.getOrDefault(
                            new Slot(
                                    mount,
                                    DataLinkBlockEntity
                                            .WeaponEndpointType.PITCH),
                            new LinkedHashSet<>());
            if (explicitPitch.isEmpty()
                    || explicitPitch.contains(pitchPos)) {
                retained.add(mount);
            }
        }
        if (retained.isEmpty()) {
            return;
        }

        BlockPos dominant = selectDominantMount(
                retained, targetedMounts,
                explicitLink == null ? null : explicitLink.mountPos());
        retained.sort((first, second) -> {
            if (first.equals(dominant)) {
                return second.equals(dominant) ? 0 : -1;
            }
            if (second.equals(dominant)) {
                return 1;
            }
            return STABLE_POSITION.compare(first, second);
        });

        List<MountChannelView> channels = new ArrayList<>(retained.size());
        Set<BlockPos> dataLinks = new HashSet<>();
        boolean valid = true;
        for (BlockPos mount : retained) {
            Group group = groups.get(mount);
            if (group == null) {
                channels.add(new MountChannelView(
                        mount.immutable(), null, null));
                valid = false;
                continue;
            }
            channels.add(channel(group));
            dataLinks.addAll(group.dataLinks());
            valid &= group.validTopology();
        }

        controllerToMount.put(pitchPos, dominant);
        endpointToMount.put(pitchPos, dominant);
        endpointOrigins.put(
                pitchPos,
                explicitLink == null
                        ? EndpointOrigin.CONTACT
                        : EndpointOrigin.DATALINK);
        controlViews.put(pitchPos, new WeaponControlView(
                WeaponNetworkMode.T_PITCH_DUAL,
                pitchPos.immutable(),
                dominant.immutable(),
                List.copyOf(channels),
                Collections.unmodifiableSet(dataLinks),
                valid));
    }

    private BlockPos selectDominantMount(
            List<BlockPos> mounts,
            Set<BlockPos> targetedMounts,
            @Nullable BlockPos tPitchDataLinkMount
    ) {
        List<BlockPos> targeted = mounts.stream()
                .filter(targetedMounts::contains)
                .toList();
        if (targeted.size() == 1) {
            return targeted.getFirst();
        }
        if (targeted.size() > 1
                && tPitchDataLinkMount != null
                && mounts.contains(tPitchDataLinkMount)) {
            return tPitchDataLinkMount;
        }
        return mounts.stream().min(STABLE_POSITION)
                .orElseThrow();
    }

    private List<BlockPos> discoverContactMounts(
            BlockPos endpointPos,
            DataLinkBlockEntity.WeaponEndpointType type,
            Set<BlockPos> targetedMounts
    ) {
        BlockEntity endpoint = level.getBlockEntity(endpointPos);
        if (endpoint == null) {
            return List.of();
        }

        if (endpoint instanceof TPitchControllerBlockEntity
                && type
                == DataLinkBlockEntity.WeaponEndpointType.PITCH) {
            return eligibleTPitchMounts(endpointPos, targetedMounts);
        }

        LinkedHashSet<BlockPos> mounts = new LinkedHashSet<>();
        if (endpoint instanceof AutoPitchControllerBlockEntity
                && type
                == DataLinkBlockEntity.WeaponEndpointType.PITCH) {
            BlockState state = endpoint.getBlockState();
            if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
                addDirectMount(
                        mounts,
                        endpointPos.relative(state.getValue(
                                HorizontalDirectionalBlock.FACING)));
            }
        } else if (endpoint instanceof AutoYawControllerBlockEntity
                && type
                == DataLinkBlockEntity.WeaponEndpointType.YAW) {
            BlockState state = endpoint.getBlockState();
            if (state.hasProperty(DirectionalKineticBlock.FACING)) {
                Direction mountingFace =
                        state.getValue(
                                DirectionalKineticBlock.FACING)
                                .getOpposite();
                if (mountingFace.getAxis()
                        == Direction.Axis.Y) {
                    addDirectMount(
                            mounts,
                            endpointPos.relative(mountingFace));
                }
            }
        } else if (endpoint instanceof FireControllerBlockEntity
                && type
                == DataLinkBlockEntity.WeaponEndpointType.FIRING) {
            for (Direction direction : Direction.values()) {
                addDirectMount(
                        mounts, endpointPos.relative(direction));
            }
        }
        return mounts.stream().sorted(STABLE_POSITION).toList();
    }

    private List<BlockPos> eligibleTPitchMounts(
            BlockPos controllerPos,
            Set<BlockPos> targetedMounts
    ) {
        BlockEntity endpoint = level.getBlockEntity(controllerPos);
        if (!(endpoint instanceof TPitchControllerBlockEntity)) {
            return List.of();
        }
        BlockState state = endpoint.getBlockState();
        if (!state.hasProperty(TPitchControllerBlock.ORIENTATION)) {
            return List.of();
        }

        Direction.Axis axis = state.getValue(
                TPitchControllerBlock.ORIENTATION).crossbarAxis();
        Direction positive = axis == Direction.Axis.X
                ? Direction.EAST : Direction.SOUTH;
        LinkedHashSet<BlockPos> mounts = new LinkedHashSet<>();
        addTPitchSide(
                mounts,
                controllerPos.relative(positive),
                targetedMounts);
        addTPitchSide(
                mounts,
                controllerPos.relative(positive.getOpposite()),
                targetedMounts);
        return mounts.stream().sorted(STABLE_POSITION).toList();
    }

    private void addTPitchSide(
            Set<BlockPos> mounts,
            BlockPos endpointPos,
            Set<BlockPos> targetedMounts
    ) {
        CannonMountContext direct = directMount(endpointPos);
        if (direct != null) {
            mounts.add(direct.getBlockPos().immutable());
            return;
        }

        CannonMountContext extended =
                CannonMountContext.resolveEndpoint(level, endpointPos);
        if (extended != null
                && targetedMounts.contains(extended.getBlockPos())) {
            mounts.add(extended.getBlockPos().immutable());
        }
    }

    private void addDirectMount(
            Set<BlockPos> mounts,
            BlockPos endpointPos
    ) {
        CannonMountContext mount = directMount(endpointPos);
        if (mount != null) {
            mounts.add(mount.getBlockPos().immutable());
        }
    }

    @Nullable
    private CannonMountContext directMount(BlockPos endpointPos) {
        if (!level.hasChunkAt(endpointPos)) {
            return null;
        }
        CannonMountContext mount =
                CannonMountContext.of(
                        level.getBlockEntity(endpointPos));
        return mount != null && mount.isCurrent()
                && mount.getBlockPos().equals(endpointPos)
                ? mount : null;
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
        claims.computeIfAbsent(
                new Slot(immutableMount, type),
                ignored -> new LinkedHashSet<>())
                .add(immutableEndpoint);
        GroupBuilder builder = group(groups, immutableMount);
        builder.associatedEndpoints.add(immutableEndpoint);
        if (type
                == DataLinkBlockEntity.WeaponEndpointType.FIRING) {
            builder.associatedFiring.add(immutableEndpoint);
        }
    }

    private static GroupBuilder group(
            Map<BlockPos, GroupBuilder> groups,
            BlockPos mount
    ) {
        BlockPos immutable = mount.immutable();
        return groups.computeIfAbsent(
                immutable, GroupBuilder::new);
    }

    @Nullable
    private LinkEntry explicitLinkForEndpoint(
            BlockPos endpointPos,
            @Nullable LinkEntry ignored
    ) {
        for (LinkEntry entry : linksByDataLink.values()) {
            if (!entry.equals(ignored)
                    && entry.endpointPos().equals(endpointPos)) {
                return entry;
            }
        }
        return null;
    }

    private Set<BlockPos> acceptedDataLinkTargets(
            @Nullable LinkEntry ignored
    ) {
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
                return true;
            }
            BlockEntity blockEntity =
                    level.getBlockEntity(dataLinkPos);
            if (!(blockEntity
                    instanceof DataLinkBlockEntity dataLink)) {
                return true;
            }
            BlockState state = dataLink.getBlockState();
            return !(state.getBlock() instanceof DataLinkBlock)
                    || !state.hasProperty(DataLinkBlock.LINK_STYLE)
                    || state.getValue(DataLinkBlock.LINK_STYLE)
                    != DataLinkBlock.LinkStyle.CONTROLLER
                    || dataLink.getWeaponEndpointType()
                    != entry.getValue().type()
                    || !dataLink.getSourcePosition().equals(
                    entry.getValue().endpointPos())
                    || !dataLink.getTargetPosition().equals(
                    entry.getValue().mountPos());
        });

        removed |= contactControllers.entrySet().removeIf(entry -> {
            BlockPos position = entry.getKey();
            if (!level.isLoaded(position)) {
                return true;
            }
            BlockEntity controller =
                    level.getBlockEntity(position);
            return controller == null
                    || controller.isRemoved()
                    || endpointType(controller) != entry.getValue();
        });
        if (removed) {
            topologyDirty = true;
        }
    }

    private static DataLinkBlockEntity.WeaponEndpointType endpointType(
            @Nullable BlockEntity controller
    ) {
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
            BlockEntity blockEntity =
                    level.getBlockEntity(endpoint);
            if (blockEntity
                    instanceof AutoPitchControllerBlockEntity pitch) {
                pitch.failClosedRadarAim();
            } else if (blockEntity
                    instanceof AutoYawControllerBlockEntity yaw) {
                yaw.failClosedRadarAim();
            }
        }
        for (BlockPos firing : invalidFiring) {
            if (level.getBlockEntity(firing)
                    instanceof FireControllerBlockEntity fire) {
                fire.setPowered(false);
            }
        }
    }

    private void notifyTopologyChanged(
            RuntimeSnapshot previous,
            RuntimeSnapshot current
    ) {
        Set<BlockPos> endpoints = new HashSet<>();
        addAssociatedEndpoints(previous, endpoints);
        addAssociatedEndpoints(current, endpoints);
        endpoints.addAll(previous.controllerToMount().keySet());
        endpoints.addAll(current.controllerToMount().keySet());

        Set<BlockPos> filterers = new HashSet<>();
        NetworkData networkData = NetworkData.get(level);
        for (BlockPos endpoint : endpoints) {
            BlockEntity blockEntity =
                    level.getBlockEntity(endpoint);
            if (blockEntity
                    instanceof AutoPitchControllerBlockEntity pitch) {
                pitch.markMountDirtyExternal();
                pitch.failClosedRadarAim();
                if (pitch.firingControl != null) {
                    pitch.firingControl.refreshControllers();
                }
            } else if (blockEntity
                    instanceof AutoYawControllerBlockEntity yaw) {
                yaw.markMountDirtyExternal();
                yaw.failClosedRadarAim();
            } else if (blockEntity
                    instanceof FireControllerBlockEntity fire) {
                fire.setPowered(false);
            }

            BlockPos filterer = networkData.getFiltererForEndpoint(
                    level.dimension(), endpoint);
            if (filterer != null) {
                filterers.add(filterer);
            }
        }

        for (BlockPos filtererPos : filterers) {
            if (level.getBlockEntity(filtererPos)
                    instanceof NetworkFiltererBlockEntity filterer) {
                filterer.onWeaponTopologyChanged();
            }
        }
    }

    private static void addAssociatedEndpoints(
            RuntimeSnapshot source,
            Set<BlockPos> destination
    ) {
        for (Group group : source.groupsByMount().values()) {
            destination.addAll(group.associatedEndpoints());
        }
    }

    private boolean isExplicitEndpointAvailable(LinkEntry link) {
        if (!level.isLoaded(link.endpointPos())) {
            return false;
        }
        return endpointType(level.getBlockEntity(link.endpointPos()))
                == link.type();
    }

    private boolean isExplicitMountAvailable(BlockPos mountPos) {
        CannonMountContext mount = directMount(mountPos);
        return mount != null
                && mount.getBlockPos().equals(mountPos);
    }

    /**
     * Moves still-live explicit links from one canonical mount coordinate to
     * another. Contact assignments are rebuilt after the relocation.
     */
    public boolean relocateMountGroup(
            BlockPos oldMount,
            BlockPos newMount,
            DataLinkBlockEntity movedLink,
            BlockPos movedEndpoint
    ) {
        pruneStaleInputs();

        List<DataLinkBlockEntity> linksToMove = new ArrayList<>();
        for (LinkEntry entry
                : new ArrayList<>(linksByDataLink.values())) {
            if (!entry.mountPos().equals(oldMount)) {
                continue;
            }
            BlockEntity blockEntity =
                    level.getBlockEntity(entry.dataLinkPos());
            if (blockEntity instanceof DataLinkBlockEntity dataLink) {
                linksToMove.add(dataLink);
            }
        }
        if (linksToMove.stream().noneMatch(link ->
                link.getBlockPos().equals(movedLink.getBlockPos()))) {
            linksToMove.add(movedLink);
        }

        EnumMap<DataLinkBlockEntity.WeaponEndpointType, BlockPos> desired =
                new EnumMap<>(
                        DataLinkBlockEntity.WeaponEndpointType.class);
        for (LinkEntry entry : linksByDataLink.values()) {
            if (entry.mountPos().equals(newMount)) {
                desired.put(entry.type(), entry.endpointPos());
            }
        }

        for (DataLinkBlockEntity link : linksToMove) {
            DataLinkBlockEntity.WeaponEndpointType type =
                    link.getWeaponEndpointType();
            if (type
                    == DataLinkBlockEntity.WeaponEndpointType.NONE) {
                continue;
            }
            BlockPos endpoint = link.getBlockPos().equals(
                    movedLink.getBlockPos())
                    ? movedEndpoint : link.getSourcePosition();
            BlockPos existing =
                    desired.putIfAbsent(type, endpoint);
            if (existing != null && !existing.equals(endpoint)) {
                unregister(movedLink.getBlockPos());
                return false;
            }
            BlockPos mappedMount =
                    getExplicitMountForController(endpoint);
            if (mappedMount != null
                    && !mappedMount.equals(oldMount)
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
        topologyDirty = true;
        return true;
    }

    private static BlockPos stableFirst(Collection<BlockPos> positions) {
        return positions.stream().min(STABLE_POSITION)
                .orElseThrow();
    }

    private static MountChannelView channel(Group group) {
        return new MountChannelView(
                group.mountPos(),
                group.yawPos(),
                group.firingPos());
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        return Collections.unmodifiableMap(
                new HashMap<>(source));
    }

    private record LinkEntry(
            BlockPos dataLinkPos,
            BlockPos endpointPos,
            BlockPos mountPos,
            DataLinkBlockEntity.WeaponEndpointType type
    ) {
    }

    private record Slot(
            BlockPos mountPos,
            DataLinkBlockEntity.WeaponEndpointType type
    ) {
    }

    private record ContactCandidate(
            DataLinkBlockEntity.WeaponEndpointType type,
            List<BlockPos> discoveredMounts,
            List<BlockPos> availableMounts
    ) {
    }

    private static final class GroupBuilder {
        private final BlockPos mountPos;
        private final EnumMap<
                DataLinkBlockEntity.WeaponEndpointType, BlockPos>
                endpoints = new EnumMap<>(
                DataLinkBlockEntity.WeaponEndpointType.class);
        private final Set<BlockPos> dataLinks = new HashSet<>();
        private final Set<BlockPos> associatedEndpoints =
                new HashSet<>();
        private final Set<BlockPos> associatedFiring =
                new HashSet<>();
        private boolean validTopology = true;

        private GroupBuilder(BlockPos mountPos) {
            this.mountPos = mountPos.immutable();
        }

        private Group build() {
            return new Group(
                    mountPos,
                    endpoints.get(DataLinkBlockEntity
                            .WeaponEndpointType.YAW),
                    endpoints.get(DataLinkBlockEntity
                            .WeaponEndpointType.PITCH),
                    endpoints.get(DataLinkBlockEntity
                            .WeaponEndpointType.FIRING),
                    Collections.unmodifiableSet(
                            new HashSet<>(dataLinks)),
                    Collections.unmodifiableSet(
                            new HashSet<>(associatedEndpoints)),
                    Collections.unmodifiableSet(
                            new HashSet<>(associatedFiring)),
                    validTopology);
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
            return new WeaponGroupView(
                    mountPos, yawPos, pitchPos, firingPos,
                    dataLinks);
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
            return new RuntimeSnapshot(
                    Map.of(), Map.of(), Map.of(),
                    Map.of(), Map.of());
        }
    }

    public enum EndpointOrigin {
        CONTACT,
        DATALINK
    }

    public record WeaponGroupView(
            BlockPos mountPos,
            @Nullable BlockPos yawPos,
            @Nullable BlockPos pitchPos,
            @Nullable BlockPos firingPos,
            Set<BlockPos> dataLinks
    ) {
        public WeaponGroupView {
            dataLinks = Collections.unmodifiableSet(
                    new HashSet<>(dataLinks));
        }

        public Set<BlockPos> endpoints() {
            Set<BlockPos> out = new HashSet<>();
            if (yawPos != null) {
                out.add(yawPos);
            }
            if (pitchPos != null) {
                out.add(pitchPos);
            }
            if (firingPos != null) {
                out.add(firingPos);
            }
            return out;
        }

        public Set<BlockPos> otherEndpoints(BlockPos exclude) {
            Set<BlockPos> out = endpoints();
            out.remove(exclude);
            return out;
        }
    }

    public enum WeaponNetworkMode {
        SINGLE,
        T_PITCH_DUAL
    }

    public record MountChannelView(
            BlockPos mountPos,
            @Nullable BlockPos yawPos,
            @Nullable BlockPos firingPos
    ) {
    }

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
            dataLinks = Collections.unmodifiableSet(
                    new HashSet<>(dataLinks));
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
