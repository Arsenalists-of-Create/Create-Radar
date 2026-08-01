package com.happysg.radar.block.controller.tpitch;

import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.block.controller.kinetic.KineticPowerSource;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.CannonMountContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import java.util.ArrayList;
import java.util.List;

public class TPitchControllerBlockEntity
        extends AutoPitchControllerBlockEntity {
    private boolean adjacentMountsDirty = true;
    private Direction.Axis cachedCrossbarAxis;
    private List<CannonMountContext> cachedAdjacentMounts = List.of();
    private long adjacentMountResolutionTick = Long.MIN_VALUE;

    public TPitchControllerBlockEntity(BlockEntityType<?> type, BlockPos pos,
                                       BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected List<CannonMountContext> resolveControlledCbcMounts() {
        if (!(level instanceof ServerLevel serverLevel)
                || !Mods.CREATEBIGCANNONS.isLoaded()) {
            return List.of();
        }

        TPitchControllerBlock.Orientation orientation = orientation();
        if (orientation == null) {
            return List.of();
        }

        ensureAdjacentMountsCurrent(orientation.crossbarAxis());
        WeaponNetworkRuntime.WeaponControlView view =
                WeaponNetworkRuntime.get(serverLevel)
                        .getWeaponControlViewFromPitch(worldPosition);
        if (view == null || !view.validTopology()) {
            return List.of();
        }
        List<CannonMountContext> active = new ArrayList<>(2);
        for (WeaponNetworkRuntime.MountChannelView channel
                : view.channels()) {
            addActiveMountAt(active, channel.mountPos());
        }
        return List.copyOf(active);
    }

    @Override
    protected CannonMountContext resolvePrimaryCbcMount() {
        TPitchControllerBlock.Orientation orientation = orientation();
        if (orientation == null
                || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        ensureAdjacentMountsCurrent(orientation.crossbarAxis());

        WeaponNetworkRuntime.WeaponControlView view =
                WeaponNetworkRuntime.get(serverLevel)
                        .getWeaponControlViewFromPitch(worldPosition);
        if (view == null || !view.validTopology()) {
            return null;
        }
        BlockPos dominant = view.preferredMountPos();
        if (dominant != null) {
            for (CannonMountContext mount : cachedAdjacentMounts) {
                if (mount.getBlockPos().equals(dominant)
                        && isActiveCannonMount(mount)) {
                    return mount;
                }
            }
        }

        List<CannonMountContext> mounts = resolveControlledCbcMounts();
        return mounts.isEmpty() ? null : mounts.getFirst();
    }

    /**
     * Returns the live canonical CBC mount positions on the two crossbar
     * endpoints. The Data Link-selected mount is ordered first when present;
     * the other side follows. An assembled cannon is not required, so idle
     * mounts can still be reserved for this shared-pitch network.
     */
    public List<BlockPos> resolveNetworkMountPositions() {
        TPitchControllerBlock.Orientation orientation = orientation();
        if (orientation == null) {
            return List.of();
        }
        ensureAdjacentMountsCurrent(orientation.crossbarAxis());

        WeaponNetworkRuntime.WeaponControlView view =
                level instanceof ServerLevel serverLevel
                        ? WeaponNetworkRuntime.get(serverLevel)
                        .getWeaponControlViewFromPitch(worldPosition)
                        : null;
        if (view != null) {
            return view.channels().stream()
                    .map(WeaponNetworkRuntime.MountChannelView::mountPos)
                    .toList();
        }

        List<BlockPos> positions = new ArrayList<>(2);
        for (CannonMountContext mount : cachedAdjacentMounts) {
            BlockPos position = mount.getBlockPos().immutable();
            if (mount.isCurrent() && !positions.contains(position)) {
                positions.add(position);
            }
        }
        return List.copyOf(positions);
    }

    @Override
    public List<CannonMountContext> resolveCollisionCbcMounts() {
        TPitchControllerBlock.Orientation orientation = orientation();
        if (orientation == null || level == null
                || !Mods.CREATEBIGCANNONS.isLoaded()) {
            return List.of();
        }
        ensureAdjacentMountsCurrent(orientation.crossbarAxis());
        return cachedAdjacentMounts.stream()
                .filter(CannonMountContext::isCurrent)
                .toList();
    }

    @Override
    public List<BlockPos> resolveCollisionMountPositions() {
        return resolveNetworkMountPositions();
    }

    @Override
    protected boolean isFiringControlMount(
            WeaponNetworkRuntime.WeaponGroupView view,
            CannonMountContext mount
    ) {
        if (view == null || mount == null) {
            return false;
        }
        BlockPos mountPos = mount.getBlockPos();
        if (level instanceof ServerLevel serverLevel) {
            WeaponNetworkRuntime.WeaponControlView controlView =
                    WeaponNetworkRuntime.get(serverLevel)
                            .getWeaponControlViewFromPitch(worldPosition);
            return controlView != null
                    && controlView.validTopology()
                    && controlView.channelForMount(mountPos) != null;
        }
        return false;
    }

    private BlockPos linkedCrossbarMount() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        BlockPos candidate = WeaponNetworkRuntime.get(serverLevel)
                .getMountForController(worldPosition);
        if (candidate == null) {
            return null;
        }
        for (CannonMountContext mount : cachedAdjacentMounts) {
            if (mount.getBlockPos().equals(candidate) && mount.isCurrent()) {
                return candidate;
            }
        }
        return null;
    }

    private void addActiveMountAt(List<CannonMountContext> mounts,
                                  BlockPos mountPos) {
        if (mountPos == null) {
            return;
        }
        for (CannonMountContext mount : cachedAdjacentMounts) {
            if (mount.getBlockPos().equals(mountPos)
                    && isActiveCannonMount(mount)) {
                mounts.add(mount);
                return;
            }
        }
    }

    private static boolean isActiveCannonMount(CannonMountContext mount) {
        if (mount == null || mount.isRemoved()) {
            return false;
        }
        PitchOrientedContraptionEntity contraption = mount.getContraption();
        return contraption != null
                && contraption.isAlive()
                && contraption.getContraption()
                instanceof AbstractMountedCannonContraption;
    }

    private void refreshAdjacentMounts(Direction.Axis crossbarAxis) {
        if (level == null) {
            cachedAdjacentMounts = List.of();
            cachedCrossbarAxis = crossbarAxis;
            adjacentMountResolutionTick = Long.MIN_VALUE;
            adjacentMountsDirty = false;
            return;
        }

        Direction positive = positiveDirection(crossbarAxis);
        List<CannonMountContext> mounts = new ArrayList<>(2);
        addMountAt(mounts, worldPosition.relative(positive));
        addMountAt(mounts, worldPosition.relative(positive.getOpposite()));
        cachedAdjacentMounts = List.copyOf(mounts);
        cachedCrossbarAxis = crossbarAxis;
        adjacentMountResolutionTick = level.getGameTime();
        adjacentMountsDirty = false;
    }

    private void ensureAdjacentMountsCurrent(Direction.Axis crossbarAxis) {
        if (level == null) {
            refreshAdjacentMounts(crossbarAxis);
            return;
        }
        if (adjacentMountsDirty
                || cachedCrossbarAxis != crossbarAxis
                || adjacentMountResolutionTick != level.getGameTime()
                || cachedAdjacentMounts.stream()
                    .anyMatch(mount -> !mount.isCurrent())) {
            refreshAdjacentMounts(crossbarAxis);
        }
    }

    private void addMountAt(List<CannonMountContext> mounts,
                            BlockPos endpointPos) {
        if (level == null || !level.hasChunkAt(endpointPos)) {
            return;
        }
        CannonMountContext mount =
                CannonMountContext.resolveEndpoint(level, endpointPos);
        if (mount == null) {
            return;
        }
        for (CannonMountContext existing : mounts) {
            if (existing.sameMount(mount)) {
                return;
            }
        }
        mounts.add(mount);
    }

    public boolean canLinkMount(BlockPos mountPos) {
        if (level == null || mountPos == null || !level.hasChunkAt(mountPos)) {
            return false;
        }
        TPitchControllerBlock.Orientation orientation = orientation();
        if (orientation == null) {
            return false;
        }
        CannonMountContext selected =
                CannonMountContext.resolveEndpoint(level, mountPos);
        if (selected == null) {
            return false;
        }
        ensureAdjacentMountsCurrent(orientation.crossbarAxis());
        for (CannonMountContext mount : cachedAdjacentMounts) {
            if (mount.sameMount(selected)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double getAvailableInputSpeed() {
        TPitchControllerBlock.Orientation orientation = orientation();
        return orientation == null
                ? 0.0
                : KineticPowerSource.adjacentShaftRpm(
                this, orientation.branchDirection());
    }

    @Override
    protected boolean supportsPhysBearingMounts() {
        return false;
    }

    @Override
    protected boolean supportsStructuralKineticMounts() {
        return false;
    }

    @Override
    public void onRelevantNeighborChanged(BlockPos fromPos) {
        TPitchControllerBlock.Orientation orientation = orientation();
        if (orientation != null
                && TPitchControllerBlock.isCrossbarEndpointPosition(
                worldPosition, fromPos, orientation.crossbarAxis())) {
            markMountDirtyExternal();
        }
    }

    @Override
    public void markMountDirtyExternal() {
        super.markMountDirtyExternal();
        adjacentMountsDirty = true;
    }

    @Override
    public void onLoad() {
        adjacentMountsDirty = true;
        adjacentMountResolutionTick = Long.MIN_VALUE;
        super.onLoad();
    }

    private TPitchControllerBlock.Orientation orientation() {
        BlockState state = getBlockState();
        return state.hasProperty(TPitchControllerBlock.ORIENTATION)
                ? state.getValue(TPitchControllerBlock.ORIENTATION)
                : null;
    }

    private static Direction positiveDirection(Direction.Axis axis) {
        return axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
    }
}
