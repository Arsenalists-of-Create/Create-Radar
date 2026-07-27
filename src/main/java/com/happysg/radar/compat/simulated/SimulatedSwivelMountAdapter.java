package com.happysg.radar.compat.simulated;

import com.happysg.radar.block.controller.kinetic.CannonAxis;
import com.happysg.radar.block.controller.kinetic.KineticAimFrame;
import com.happysg.radar.block.controller.kinetic.KineticAngleMath;
import com.happysg.radar.block.controller.kinetic.KineticMountFrame;
import com.happysg.radar.block.controller.kinetic.KineticMountAdapter;
import com.happysg.radar.block.controller.kinetic.KineticMountAdapterResolution;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlock;
import dev.simulated_team.simulated.service.SimConfigService;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;

import java.util.Objects;
import java.util.UUID;


public final class SimulatedSwivelMountAdapter implements KineticMountAdapter {
    private static final double SPEED_EPSILON = 1.0e-5;
    private static final double CONTROLLER_SWIVEL_RPM_LIMIT = 32.0;

    private final KineticBlockEntity controller;
    private final Direction.Axis rotationAxis;
    private final CannonAxis cannonAxis;
    private final Direction relativeDirection;
    private final SwivelBearingBlockEntity bearing;
    private final KineticBlockEntity endpoint;
    private KineticMountFrame cachedFrame;

    private SimulatedSwivelMountAdapter(KineticBlockEntity controller,
                                        Direction.Axis rotationAxis,
                                        Direction relativeDirection,
                                        SwivelBearingBlockEntity bearing) {
        this.controller = controller;
        this.rotationAxis = rotationAxis;
        this.cannonAxis = rotationAxis == Direction.Axis.Y ? CannonAxis.YAW : CannonAxis.PITCH;
        this.relativeDirection = relativeDirection;
        this.bearing = bearing;
        this.endpoint = bearing.getExtraKinetics();
    }


    public static KineticMountAdapterResolution resolve(KineticBlockEntity controller,
                                                        Direction.Axis rotationAxis) {
        Level level = controller.getLevel();
        if (level == null) {
            return KineticMountAdapterResolution.absent("controller_level_unavailable");
        }

        Direction selectedDirection = null;
        SwivelBearingBlockEntity selectedBearing = null;
        int matches = 0;
        boolean unloadedCandidate = false;

        for (Direction direction : Direction.values()) {
            if (direction.getAxis() == rotationAxis) {
                continue;
            }

            BlockPos candidatePos = controller.getBlockPos().relative(direction);
            if (!level.hasChunkAt(candidatePos)) {
                unloadedCandidate = true;
                continue;
            }

            BlockEntity candidate = level.getBlockEntity(candidatePos);
            if (!(candidate instanceof SwivelBearingBlockEntity bearing)) {
                continue;
            }

            BlockState state = bearing.getBlockState();
            if (!(state.getBlock() instanceof SwivelBearingBlock)
                    || !state.hasProperty(SwivelBearingBlock.FACING)
                    || state.getValue(SwivelBearingBlock.FACING).getAxis() != rotationAxis) {
                continue;
            }

            matches++;
            selectedDirection = direction;
            selectedBearing = bearing;
        }

        if (unloadedCandidate) {
            return KineticMountAdapterResolution.unavailable("adjacent_controller_chunk_unloaded");
        }
        if (matches == 0) {
            return KineticMountAdapterResolution.absent("no_aligned_adjacent_swivel");
        }
        if (matches > 1) {
            return KineticMountAdapterResolution.ambiguous("multiple_aligned_adjacent_swivels");
        }


        int compatibleControllers = 0;
        boolean unloadedTopology = false;
        BlockPos bearingPos = selectedBearing.getBlockPos();
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() == rotationAxis) {
                continue;
            }
            BlockPos controllerPos = bearingPos.relative(direction);
            if (!level.hasChunkAt(controllerPos)) {
                unloadedTopology = true;
                continue;
            }
            BlockEntity candidate = level.getBlockEntity(controllerPos);
            if (rotationAxis == Direction.Axis.Y && candidate instanceof AutoYawControllerBlockEntity) {
                compatibleControllers++;
            } else if (rotationAxis != Direction.Axis.Y
                    && candidate instanceof AutoPitchControllerBlockEntity pitch
                    && pitch.getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                    && pitch.getBlockState().getValue(HorizontalDirectionalBlock.FACING).getAxis() == rotationAxis) {
                compatibleControllers++;
            }
        }
        if (unloadedTopology) {
            return KineticMountAdapterResolution.unavailable("swivel_topology_chunk_unloaded");
        }
        if (compatibleControllers != 1) {
            return KineticMountAdapterResolution.ambiguous(
                    compatibleControllers == 0 ? "swivel_has_no_compatible_controller"
                            : "multiple_controllers_share_swivel");
        }

        return KineticMountAdapterResolution.present(new SimulatedSwivelMountAdapter(
                controller, rotationAxis, selectedDirection, selectedBearing));
    }

    @Override
    public CannonAxis axis() {
        return cannonAxis;
    }

    @Override
    public Direction relativeDirection() {
        return relativeDirection;
    }

    @Override
    public boolean isValid() {
        Level level = controller.getLevel();
        if (level == null || bearing.isRemoved() || bearing.getLevel() != level) {
            return false;
        }

        BlockPos expectedPos = controller.getBlockPos().relative(relativeDirection);
        if (!bearing.getBlockPos().equals(expectedPos)
                || !level.hasChunkAt(expectedPos)
                || level.getBlockEntity(expectedPos) != bearing) {
            return false;
        }

        BlockState state = bearing.getBlockState();
        return state.getBlock() instanceof SwivelBearingBlock
                && state.hasProperty(SwivelBearingBlock.FACING)
                && state.getValue(SwivelBearingBlock.FACING).getAxis() == rotationAxis
                && bearing.getExtraKinetics() == endpoint;
    }

    @Override
    public boolean hasSameEndpoint(KineticMountAdapter other) {
        return other instanceof SimulatedSwivelMountAdapter swivel
                && swivel.endpoint == endpoint
                && swivel.rotationAxis == rotationAxis
                && swivel.relativeDirection == relativeDirection;
    }

    @Override
    public boolean isAssembled() {
        return isValid()
                && bearing.getBlockState().hasProperty(SwivelBearingBlock.ASSEMBLED)
                && bearing.getBlockState().getValue(SwivelBearingBlock.ASSEMBLED);
    }

    @Override
    public boolean isLocked() {
        return isValid()
                && bearing.getBlockState().hasProperty(SwivelBearingBlock.POWERED)
                && bearing.getBlockState().getValue(SwivelBearingBlock.POWERED);
    }

    @Override
    public double getTargetAngleDegrees() {
        return isValid() ? bearing.getTargetAngleDegrees() : Double.NaN;
    }

    @Override
    public double getPhysicalAngleDegrees() {
        if (!isValid() || !isAssembled() || bearing.getSubLevelID() == null
                || bearing.getPlatePos() == null) {
            return Double.NaN;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(controller.getLevel());
        if (container == null) {
            return Double.NaN;
        }
        SubLevel attached = container.getSubLevel(bearing.getSubLevelID());
        if (attached == null || attached.isRemoved()) {
            return Double.NaN;
        }
        BlockState bearingState = bearing.getBlockState();
        BlockState plateState = controller.getLevel().getBlockState(bearing.getPlatePos());
        if (!bearingState.hasProperty(SwivelBearingBlock.FACING)
                || !plateState.hasProperty(SwivelBearingPlateBlock.FACING)) {
            return Double.NaN;
        }

        Quaterniond containingOrientation = new Quaterniond();
        SubLevel containing = Sable.HELPER.getContaining(bearing);
        if (containing != null) {
            containingOrientation.set(containing.logicalPose().orientation());
        }
        Quaterniond bearingOrientation = new Quaterniond(
                bearingState.getValue(SwivelBearingBlock.FACING).getRotation());
        Quaterniond plateOrientation = new Quaterniond(
                plateState.getValue(SwivelBearingPlateBlock.FACING).getRotation());
        Quaterniond attachedOrientation = new Quaterniond(attached.logicalPose().orientation());
        Quaterniond relative = new Quaterniond(containingOrientation)
                .mul(bearingOrientation).conjugate()
                .mul(attachedOrientation.mul(plateOrientation));
        double angle = -2.0 * Math.toDegrees(Math.atan2(-relative.y(), relative.w()));
        return Double.isFinite(angle) ? KineticAngleMath.wrap360(angle) : Double.NaN;
    }

    @Override
    public boolean wakePhysicalAssembly() {
        if (!isValid() || !isAssembled() || bearing.getSubLevelID() == null) {
            return false;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(controller.getLevel());
        if (!(container instanceof ServerSubLevelContainer serverContainer)) {
            return false;
        }
        SubLevel attached = container.getSubLevel(bearing.getSubLevelID());
        if (!(attached instanceof ServerSubLevel serverAttached) || attached.isRemoved()) {
            return false;
        }
        PhysicsPipeline pipeline = serverContainer.physicsSystem().getPipeline();
        SubLevel containing = Sable.HELPER.getContaining(bearing);
        if (containing instanceof ServerSubLevel serverContaining) {
            pipeline.wakeUp(serverContaining);
        }
        pipeline.wakeUp(serverAttached);
        return true;
    }

    @Override
    public KineticMountFrame frameIdentity() {
        if (!isValid() || !isAssembled()) {
            return null;
        }
        UUID assemblyId = bearing.getSubLevelID();
        Direction bearingFacing = bearing.getBlockState().getValue(SwivelBearingBlock.FACING);
        Direction controllerFacing = controllerFacing();
        if (assemblyId == null || controllerFacing == null) {
            return null;
        }
        if (cachedFrame != null && cachedFrame.assemblyId().equals(assemblyId)
                && cachedFrame.bearingFacing() == bearingFacing
                && cachedFrame.controllerFacing() == controllerFacing) {
            return cachedFrame;
        }

        Direction initialOrientation = findCannonInitialOrientation(assemblyId);
        if (initialOrientation == null && cannonAxis == CannonAxis.YAW) {
            initialOrientation = Direction.SOUTH;
        }
        double neutral = cannonAxis == CannonAxis.YAW
                ? controllerYawForCardinal(initialOrientation) : 0.0;
        int sign = KineticMountFrame.conversionSignFor(cannonAxis, bearingFacing,
                controllerFacing, initialOrientation);
        cachedFrame = new KineticMountFrame(KineticMountFrame.CURRENT_VERSION, bearingFacing,
                controllerFacing, assemblyId, initialOrientation, sign, neutral);
        return cachedFrame;
    }

    @Override
    public KineticAimFrame aimFrame() {
        if (!isValid()) {
            return null;
        }
        SubLevel containing = Sable.HELPER.getContaining(bearing);
        if (containing == null) {
            return KineticAimFrame.world();
        }
        if (containing.isRemoved()) {
            return null;
        }

        Vector3d right = containing.logicalPose().transformNormal(new Vector3d(1.0, 0.0, 0.0));
        Vector3d up = containing.logicalPose().transformNormal(new Vector3d(0.0, 1.0, 0.0));
        Vector3d forward = containing.logicalPose().transformNormal(new Vector3d(0.0, 0.0, 1.0));
        if (!right.isFinite() || !up.isFinite() || !forward.isFinite()) {
            return null;
        }
        return new KineticAimFrame(
                new Vec3(right.x, right.y, right.z),
                new Vec3(up.x, up.y, up.z),
                new Vec3(forward.x, forward.y, forward.z));
    }

    @Override
    public double getEndpointTheoreticalSpeed() {
        return isValid() ? endpoint.getTheoreticalSpeed() : 0.0;
    }

    @Override
    public int getPositiveRotationSign() {
        if (!isValid()) {
            return 0;
        }
        Direction facing = bearing.getBlockState().getValue(SwivelBearingBlock.FACING);
        return facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
    }

    @Override
    public boolean isEndpointFree() {
        return isValid()
                && !endpoint.isSource()
                && !endpoint.hasSource()
                && !endpoint.hasNetwork()
                && Math.abs(endpoint.getTheoreticalSpeed()) <= SPEED_EPSILON
                && Math.abs(endpoint.getSpeed()) <= SPEED_EPSILON;
    }

    @Override
    public boolean isEndpointSafelyReleased() {
        return isEndpointFree() && endpoint.sequenceContext == null && !endpoint.isOverStressed();
    }

    @Override
    public boolean isDrivenBy(BlockPos controllerPos) {
        return controller.getBlockPos().equals(controllerPos)
                && !controller.hasSource() && controller.isSource()
                && endpoint.hasSource() && controllerPos.equals(endpoint.source)
                && controller.network != null && endpoint.network != null
                && Objects.equals(controller.network, endpoint.network)
                && Double.isFinite(endpoint.getTheoreticalSpeed())
                && Math.abs(endpoint.getTheoreticalSpeed()) > SPEED_EPSILON
                && Double.isFinite(endpoint.getSpeed())
                && Math.abs(endpoint.getSpeed()) > SPEED_EPSILON
                && !endpoint.isOverStressed();
    }

    @Override
    public boolean hasSequenceContext() {
        return endpoint.sequenceContext != null;
    }

    @Override
    public boolean discardStaleSequenceContextIfFree() {
        if (endpoint.sequenceContext == null) {
            return true;
        }
        if (!isEndpointFree()) {
            return false;
        }
        endpoint.sequenceContext = null;
        endpoint.setChanged();
        endpoint.sendData();
        return true;
    }

    @Override
    public double maximumDriveRpm(double availableInputRpm) {
        if (!Double.isFinite(availableInputRpm)) {
            return 0.0;
        }
        double maxRpm = SimConfigService.INSTANCE.server().blocks.maxSwivelBearingSpeed.getF();
        return Math.min(Math.abs(availableInputRpm),
                Math.min(CONTROLLER_SWIVEL_RPM_LIMIT, Math.max(0.0, maxRpm)));
    }

    @Override
    public double effectiveDegreesPerTick(double expectedEndpointSpeed) {
        return !Double.isFinite(expectedEndpointSpeed) ? 0.0
                : Math.abs(KineticBlockEntity.convertToAngular((float) expectedEndpointSpeed));
    }

    private Direction controllerFacing() {
        BlockState state = controller.getBlockState();
        if (state.hasProperty(DirectionalKineticBlock.FACING)) {
            return state.getValue(DirectionalKineticBlock.FACING);
        }
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return state.getValue(HorizontalDirectionalBlock.FACING);
        }
        return null;
    }

    private Direction findCannonInitialOrientation(UUID assemblyId) {
        Level level = controller.getLevel();
        SubLevelContainer container = level == null ? null : SubLevelContainer.getContainer(level);
        SubLevel attached = container == null ? null : container.getSubLevel(assemblyId);
        if (attached == null) {
            return null;
        }
        var bounds = attached.getPlot().getBoundingBox();
        if (bounds == null || bounds.volume() <= 0 || bounds.volume() > 65_536) {
            return null;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    BlockEntity candidate = level.getBlockEntity(cursor.set(x, y, z));
                    if (candidate instanceof CannonMountBlockEntity mount
                            && mount.getContraption() != null
                            && mount.getContraption().getContraption()
                            instanceof AbstractMountedCannonContraption cannon) {
                        Direction initial = cannon.initialOrientation();
                        if (initial != null && initial.getAxis().isHorizontal()) {
                            return initial;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static double controllerYawForCardinal(Direction direction) {
        if (direction == null) {
            return 0.0;
        }
        return switch (direction) {
            case SOUTH -> 0.0;
            case WEST -> 90.0;
            case NORTH -> 180.0;
            case EAST -> 270.0;
            default -> 0.0;
        };
    }
}
