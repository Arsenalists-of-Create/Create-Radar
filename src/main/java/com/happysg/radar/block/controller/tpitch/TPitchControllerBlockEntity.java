package com.happysg.radar.block.controller.tpitch;

import com.happysg.radar.api.mount.RadarMountAdapter;
import com.happysg.radar.api.mount.RadarMountRegistry;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.block.behavior.networks.WeaponFiringControl;
import com.happysg.radar.block.controller.kinetic.CannonAxis;
import com.happysg.radar.block.controller.kinetic.KineticMountAdapterResolution;
import com.happysg.radar.block.controller.kinetic.KineticPowerSource;
import com.happysg.radar.block.controller.limits.ControllerMovementLimits;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.CannonMountContext;
import com.happysg.radar.compat.simulated.SimulatedSwivelMountAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class TPitchControllerBlockEntity extends AutoPitchControllerBlockEntity {
    private boolean adjacentMountsDirty = true;
    private Direction.Axis cachedCrossbarAxis;
    private List<CannonMountContext> cachedAdjacentMounts = List.of();
    private long adjacentMountResolutionTick = Long.MIN_VALUE;

    public TPitchControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected boolean tickSpecializedPitchMounts() {
        if (!(level instanceof ServerLevel serverLevel) || !isControllerRunning()) {
            return false;
        }

        TPitchControllerBlock.Orientation orientation = orientation();
        if (orientation == null) {
            return false;
        }

        WeaponNetworkRuntime.WeaponControlView view = WeaponNetworkRuntime.get(serverLevel).getWeaponControlViewFromPitch(worldPosition);

        if (view == null || !view.validTopology()) {
            return false;
        }

        Direction positive = positiveDirection(orientation.crossbarAxis());
        List<RadarMountAdapter> apiMounts = new ArrayList<>(2);

        for (Direction direction : List.of(positive, positive.getOpposite())) {

            BlockPos endpointPos = worldPosition.relative(direction);

            if (CannonMountContext.resolveEndpoint(level, endpointPos) != null) {
                continue;
            }

            RadarMountAdapter adapter = RadarMountRegistry.find(level, endpointPos);
            if (adapter == null || !adapter.isValid() || !adapter.supportsPitch()) {
                continue;
            }

            BlockPos mountPos = adapter.getMountPos();

            if (mountPos == null || view.channelForMount(mountPos) == null) {
                continue;
            }

            apiMounts.add(adapter);
        }

        List<CannonMountContext> cbcMounts = Mods.CREATEBIGCANNONS.isLoaded() ? resolveControlledCbcMounts() : List.of();
        if (apiMounts.isEmpty() && cbcMounts.isEmpty()) {
            return view.channels().stream().anyMatch(channel -> isSwivelOwnedMount(channel.mountPos()));
        }

        for (CannonMountContext mount : cbcMounts) {
            tickCbcMount(mount);
        }

        for (RadarMountAdapter mount : apiMounts) {
            tickApiMount(mount);
        }

        return true;
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
        for (WeaponNetworkRuntime.MountChannelView channel : view.channels()) {
            if (isSwivelOwnedMount(channel.mountPos())) {
                continue;
            }

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
        List<CannonMountContext> mounts = new ArrayList<>(resolveControlledCbcMounts());

        if (Mods.SIMULATED.isLoaded()) {
            for (WeaponNetworkRuntime.MountChannelView channel : view.channels()) {
                BlockPos mountPos = channel.mountPos();

                if (SimulatedSwivelMountAdapter.findTPitchSwivelSide(this, mountPos) == null) {
                    continue;
                }

                CannonMountContext swivelMount = CannonMountContext.resolveEndpoint(level, mountPos);
                if (swivelMount == null || !swivelMount.isCurrent() || !isActiveCannonMount(swivelMount)) {
                    continue;
                }

                boolean duplicate = false;

                for (CannonMountContext existing : mounts) {
                    if (existing.sameMount(swivelMount)) {
                        duplicate = true;
                        break;
                    }
                }

                if (!duplicate) {
                    mounts.add(swivelMount);
                }
            }
        }

        if (mounts.isEmpty()) {
            return null;
        }

        // Ammunition owns source selection; cooldowns and an open breech only
        // affect readiness and must not make the shared pitch hunt sides.
        CannonMountContext current = firingControl == null ? null : firingControl.cannonMount;
        int currentIndex = -1;
        int preferredIndex = -1;
        List<Boolean> loaded = new ArrayList<>(mounts.size());

        for (int i = 0; i < mounts.size(); i++) {
            CannonMountContext mount = mounts.get(i);
            loaded.add(WeaponFiringControl.hasResolvableShot(mount, serverLevel));

            if (current != null && mount.sameMount(current)) {
                currentIndex = i;
            }

            if (dominant != null && mount.getBlockPos().equals(dominant)) {
                preferredIndex = i;
            }
        }
        int selected = WeaponFiringControl.selectDualAimingSourceIndex(
                currentIndex, preferredIndex, loaded);
        return selected < 0 ? null : mounts.get(selected);
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
    public boolean hasAssembledControlledMount() {
        if (super.hasAssembledControlledMount()) {
            return true;
        }

        for (CannonMountContext mount : resolveControlledCbcMounts()) {
            if (mount.hasAssembledCannon()) {
                return true;
            }
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        WeaponNetworkRuntime.WeaponControlView view = WeaponNetworkRuntime.get(serverLevel).getWeaponControlViewFromPitch(worldPosition);
        if (view == null || !view.validTopology()) {
            return false;
        }

        for (WeaponNetworkRuntime.MountChannelView channel : view.channels()) {
            RadarMountAdapter mount = RadarMountRegistry.find(level, channel.mountPos());

            if (mount != null
                    && mount.isValid()
                    && mount.isAssembled()
                    && mount.supportsPitch()
                    && channel.mountPos().equals(mount.getMountPos())) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    public Boolean swivelPitchAlignmentForFiring(BlockPos mountPos, boolean lag, double minimumToleranceDegrees, double maximumToleranceDegrees) {
        if (level == null || mountPos == null || !Mods.SIMULATED.isLoaded()) {
            return null;
        }

        Direction side = SimulatedSwivelMountAdapter.findTPitchSwivelSide(this, mountPos);

        if (side == null) {
            return null;
        }

        if (!isControllerRunning() || !hasStructuralKineticSelectionForTargeting()) {
            return false;
        }

        KineticMountAdapterResolution resolution = SimulatedSwivelMountAdapter.resolveTPitch(this);
        if (!resolution.hasAdapter() || resolution.adapter() == null || resolution.adapter().relativeDirection() != side) {
            return false;
        }

        if (!isAlignedForFiring(lag, minimumToleranceDegrees, maximumToleranceDegrees)) {
            return false;
        }

        var adapter = resolution.adapter();
        var aimFrame = adapter.aimFrame();
        Vec3 actualDirection = adapter.getPhysicalWorldDirection();

        if (aimFrame == null || actualDirection == null || actualDirection.lengthSqr() < 1.0e-12) {
            return false;
        }

        double actualPitch = aimFrame.controllerTargetDegrees(CannonAxis.PITCH, actualDirection);
        double allowedError = Double.isFinite(maximumToleranceDegrees) ? Math.min(1.0, Math.max(0.0, maximumToleranceDegrees)) : 1.0;
        return Double.isFinite(actualPitch) && Math.abs(actualPitch - getTargetAngle()) <= allowedError;
    }

    /**
     * True only when this particular mount is controlled through
     * the selected structural Swivel.
     */
    public boolean isStructuralPitchMount(@Nullable BlockPos mountPos) {
        if (mountPos == null || !Mods.SIMULATED.isLoaded() || !supportsStructuralKineticMounts()) {
            return false;
        }

        KineticMountAdapterResolution resolution = SimulatedSwivelMountAdapter.resolveTPitch(this);
        if (!resolution.hasAdapter() || resolution.adapter() == null || !resolution.adapter().isValid()) {
            return false;
        }

        Direction mountSide = SimulatedSwivelMountAdapter.findTPitchSwivelSide(this, mountPos);
        return mountSide != null && resolution.adapter().relativeDirection() == mountSide;
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

        Direction.Axis axis = orientation.crossbarAxis();
        CannonMountContext selected = CannonMountContext.resolveEndpoint(level, mountPos);

        if (selected != null) {
            ensureAdjacentMountsCurrent(axis);
            Direction positive = positiveDirection(axis);

            for (Direction side : List.of(positive, positive.getOpposite())) {

                BlockPos endpointPos = worldPosition.relative(side);

                if (!level.hasChunkAt(endpointPos)) {
                    continue;
                }

                CannonMountContext direct = CannonMountContext.of(level.getBlockEntity(endpointPos));

                if (direct != null && direct.isCurrent() && direct.getBlockPos().equals(endpointPos) && direct.sameMount(selected)) {
                    return false;
                }
            }

            for (CannonMountContext mount : cachedAdjacentMounts) {
                if (mount.sameMount(selected)) {
                    return true;
                }
            }
        }

        Direction positive = positiveDirection(axis);
        for (Direction side : List.of(positive, positive.getOpposite())) {
            BlockPos endpointPos = worldPosition.relative(side);

            RadarMountAdapter adapter = RadarMountRegistry.find(level, endpointPos);
            if (adapter == null || !adapter.isValid() || !adapter.supportsPitch()) {
                continue;
            }

            if (mountPos.equals(adapter.getMountPos())) {
                return false;
            }
        }

        if (Mods.SIMULATED.isLoaded() && SimulatedSwivelMountAdapter.findTPitchLinkSide(this, mountPos) != null) {
            return true;
        }

        return false;
    }

    @Override
    public ControllerMovementLimits getSupportedMovementLimits() {
        ControllerMovementLimits defaults = ControllerMovementLimits.defaults(CannonAxis.PITCH);

        double minimum = defaults.minDegrees();
        double maximum = defaults.maxDegrees();
        boolean foundCannonLimits = false;

        for (CannonMountContext mount : resolveControlledCbcMounts()) {
            CannonMountContext.PitchLimits limits = mount.pitchLimits();

            if (limits == null) {
                continue;
            }

            foundCannonLimits = true;

            minimum = Math.max(minimum, limits.minDegrees());
            maximum = Math.min(maximum, limits.maxDegrees());
        }

        if (!foundCannonLimits) {
            return defaults;
        }

        return ControllerMovementLimits.validated(CannonAxis.PITCH, minimum, maximum).orElse(defaults);
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
    public Direction getInputShaftDirection() {
        TPitchControllerBlock.Orientation orientation = orientation();
        return orientation == null
                ? Direction.SOUTH
                : orientation.branchDirection();
    }

    @Override
    protected boolean supportsPhysBearingMounts() {
        return false;
    }

    @Override
    protected boolean supportsStructuralKineticMounts() {
        return Mods.SIMULATED.isLoaded();
    }

    @Override
    protected boolean shouldUseStructuralTargeting() {
        if (firingControl != null) {
            if (firingControl.cannonMount != null) {
                return isStructuralPitchMount(firingControl.cannonMount.getBlockPos());
            }

            if (firingControl.weapon != null) {
                return isStructuralPitchMount(firingControl.weapon.getMountPos());
            }
        }
        CannonMountContext primary = resolvePrimaryCbcMount();

        if (primary != null) {
            return isStructuralPitchMount(primary.getBlockPos());
        }

        if (level instanceof ServerLevel serverLevel) {
            WeaponNetworkRuntime.WeaponControlView view = WeaponNetworkRuntime.get(serverLevel).getWeaponControlViewFromPitch(worldPosition);
            return view != null && isStructuralPitchMount(view.preferredMountPos());
        }

        return false;
    }

    @Override
    protected boolean setSpecializedPitchTarget(Vec3 targetPos) {
        if (firingControl != null) {
            CannonMountContext cbc = firingControl.cannonMount;

            if (cbc != null) {
                if (cbc.isCurrent() && !isSwivelOwnedMount(cbc.getBlockPos())) {
                    setDirectCbcTarget(cbc, targetPos);
                }

                return true;
            }

            if (firingControl.weapon != null) {
                var weapon = firingControl.weapon;
                var mount = weapon.getMount();

                if (mount != null && mount.isValid() && mount.supportsPitch() && !isSwivelOwnedMount(weapon.getMountPos())) {
                    setDirectApiTarget(mount, targetPos);
                }

                return true;
            }
        }

        CannonMountContext primary = resolvePrimaryCbcMount();
        if (primary != null && isSwivelOwnedMount(primary.getBlockPos())) {
            return true;
        }

        if (level instanceof ServerLevel serverLevel) {
            WeaponNetworkRuntime.WeaponControlView view = WeaponNetworkRuntime.get(serverLevel).getWeaponControlViewFromPitch(worldPosition);

            if (view != null && isSwivelOwnedMount(view.preferredMountPos())) {
                return true;
            }
        }

        return false;
    }

    private boolean isSwivelOwnedMount(BlockPos mountPos) {
        return mountPos != null && Mods.SIMULATED.isLoaded() && SimulatedSwivelMountAdapter.findTPitchSwivelSide(this, mountPos) != null;
    }

    @Override
    protected Direction.Axis getStructuralKineticAxis() {
        TPitchControllerBlock.Orientation orientation = orientation();
        return orientation == null ? Direction.Axis.X : orientation.crossbarAxis();
    }

    @Override
    protected KineticMountAdapterResolution resolveStructuralKineticMount(Direction.Axis axis) {
        return SimulatedSwivelMountAdapter.resolveTPitch(this);
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
