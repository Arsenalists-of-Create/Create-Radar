package com.happysg.radar.block.controller.tpitch;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.block.controller.kinetic.PlacementShaftTarget;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.compat.cbc.CannonMountContext;
import com.happysg.radar.registry.ModBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TPitchControllerBlock extends KineticBlock
        implements IBE<TPitchControllerBlockEntity>, PlacementShaftTarget {
    public static final EnumProperty<Orientation> ORIENTATION =
            EnumProperty.create("orientation", Orientation.class);

    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };

    public TPitchControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(ORIENTATION, Orientation.X_SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ORIENTATION);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(ORIENTATION).branchDirection().getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos,
                                   BlockState state, Direction face) {
        // Like the ordinary pitch controller, this block samples an adjacent
        // source while keeping its generated control output kinetically isolated.
        return false;
    }

    @Override
    public boolean hasPlacementShaftTowards(
            LevelReader world, BlockPos pos, BlockState state, Direction face
    ) {
        return face == state.getValue(ORIENTATION).branchDirection();
    }

    @Override
    public Class<TPitchControllerBlockEntity> getBlockEntityClass() {
        return TPitchControllerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TPitchControllerBlockEntity>
    getBlockEntityType() {
        return ModBlockEntityTypes.T_PITCH_CONTROLLER.get();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return defaultBlockState();
        }

        Direction mountDirection = findPreferredCannonMount(context, player);
        Direction.Axis crossbarAxis = mountDirection != null
                ? mountDirection.getAxis()
                : context.getHorizontalDirection().getClockWise().getAxis();

        Vec3 towardPlayer = player.getEyePosition()
                .subtract(Vec3.atCenterOf(context.getClickedPos()));
        Direction branchDirection = closestPerpendicularDirection(crossbarAxis, towardPlayer);
        if (player.isCrouching()) {
            branchDirection = branchDirection.getOpposite();
        }

        return defaultBlockState()
                .setValue(ORIENTATION, Orientation.from(crossbarAxis, branchDirection));
    }

    @Nullable
    private static Direction findPreferredCannonMount(BlockPlaceContext context, Player player) {
        BlockPos placementPos = context.getClickedPos();
        Vec3 look = player.getLookAngle();
        Direction bestDirection = null;
        double bestScore = -Double.MAX_VALUE;

        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            if (CannonMountContext.resolveEndpoint(
                    context.getLevel(), placementPos.relative(direction)) == null) {
                continue;
            }

            double score = look.x * direction.getStepX()
                    + look.z * direction.getStepZ();
            if (score > bestScore) {
                bestScore = score;
                bestDirection = direction;
            }
        }

        return bestDirection;
    }

    static Direction closestPerpendicularDirection(Direction.Axis crossbarAxis,
                                                   Vec3 towardPlayer) {
        Direction bestDirection = crossbarAxis == Direction.Axis.X
                ? Direction.SOUTH
                : Direction.EAST;
        double bestScore = directionScore(bestDirection, towardPlayer);

        for (Direction direction : Direction.values()) {
            if (direction.getAxis() == crossbarAxis) {
                continue;
            }

            double score = directionScore(direction, towardPlayer);
            if (score > bestScore) {
                bestScore = score;
                bestDirection = direction;
            }
        }

        return bestDirection;
    }

    private static double directionScore(Direction direction, Vec3 vector) {
        return vector.x * direction.getStepX()
                + vector.y * direction.getStepY()
                + vector.z * direction.getStepZ();
    }

    public static boolean isCrossbarEndpointPosition(BlockPos controllerPos,
                                                     BlockPos endpointPos,
                                                     Direction.Axis crossbarAxis) {
        if (controllerPos == null || endpointPos == null
                || crossbarAxis == null || !crossbarAxis.isHorizontal()) {
            return false;
        }
        int dx = endpointPos.getX() - controllerPos.getX();
        int dy = endpointPos.getY() - controllerPos.getY();
        int dz = endpointPos.getZ() - controllerPos.getZ();
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) {
            return false;
        }
        return crossbarAxis == Direction.Axis.X ? dx != 0 : dz != 0;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Orientation orientation = state.getValue(ORIENTATION);
        Direction rotatedCrossbar = rotation.rotate(
                orientation.crossbarAxis() == Direction.Axis.X
                        ? Direction.EAST
                        : Direction.SOUTH);
        Direction rotatedBranch = rotation.rotate(orientation.branchDirection());
        return state.setValue(ORIENTATION,
                Orientation.from(rotatedCrossbar.getAxis(), rotatedBranch));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Orientation orientation = state.getValue(ORIENTATION);
        return state.setValue(ORIENTATION, Orientation.from(
                orientation.crossbarAxis(),
                mirror.mirror(orientation.branchDirection())));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        if (level instanceof ServerLevel serverLevel
                && state.getBlock() != newState.getBlock()) {
            if (level.getBlockEntity(pos)
                    instanceof TPitchControllerBlockEntity pitch) {
                pitch.releaseKineticActuator();
            }
            breakAttachedDataLinks(level, pos);
            WeaponNetworkRuntime.get(serverLevel)
                    .unregisterContactController(pos);
            NetworkData.get(serverLevel).onEndpointRemoved(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private static void breakAttachedDataLinks(Level level,
                                               BlockPos controllerPos) {
        for (Direction direction : Direction.values()) {
            BlockPos linkPos = controllerPos.relative(direction);
            BlockState linkState = level.getBlockState(linkPos);
            if (!(linkState.getBlock() instanceof DataLinkBlock)) {
                continue;
            }
            if (linkState.hasProperty(DataLinkBlock.LINK_STYLE)
                    && linkState.getValue(DataLinkBlock.LINK_STYLE)
                    != DataLinkBlock.LinkStyle.CONTROLLER) {
                continue;
            }
            if (linkState.hasProperty(DataLinkBlock.FACING)) {
                Direction facing = linkState.getValue(DataLinkBlock.FACING);
                if (!linkPos.relative(facing.getOpposite())
                        .equals(controllerPos)) {
                    continue;
                }
            }
            level.destroyBlock(linkPos, true);
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                        BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide
                && level.getBlockEntity(pos)
                instanceof TPitchControllerBlockEntity pitch) {
            pitch.markMountDirtyExternal();
            pitch.invalidateKineticActuator();
        }
    }

    @Override
    public void neighborChanged(@NotNull BlockState state,
                                @NotNull Level level,
                                @NotNull BlockPos pos,
                                @NotNull Block block,
                                @NotNull BlockPos fromPos,
                                boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide
                && level.getBlockEntity(pos)
                instanceof TPitchControllerBlockEntity pitch) {
            pitch.onRelevantNeighborChanged(fromPos);
        }
    }

    public enum Orientation implements StringRepresentable {
        X_SOUTH("x_south", Direction.Axis.X, Direction.SOUTH, 0, 0),
        X_NORTH("x_north", Direction.Axis.X, Direction.NORTH, 0, 180),
        X_UP("x_up", Direction.Axis.X, Direction.UP, 90, 0),
        X_DOWN("x_down", Direction.Axis.X, Direction.DOWN, 270, 0),
        Z_WEST("z_west", Direction.Axis.Z, Direction.WEST, 0, 90),
        Z_EAST("z_east", Direction.Axis.Z, Direction.EAST, 0, 270),
        Z_UP("z_up", Direction.Axis.Z, Direction.UP, 90, 90),
        Z_DOWN("z_down", Direction.Axis.Z, Direction.DOWN, 270, 90);

        private final String serializedName;
        private final Direction.Axis crossbarAxis;
        private final Direction branchDirection;
        private final int modelRotationX;
        private final int modelRotationY;

        Orientation(String serializedName, Direction.Axis crossbarAxis,
                    Direction branchDirection, int modelRotationX,
                    int modelRotationY) {
            this.serializedName = serializedName;
            this.crossbarAxis = crossbarAxis;
            this.branchDirection = branchDirection;
            this.modelRotationX = modelRotationX;
            this.modelRotationY = modelRotationY;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }

        public Direction.Axis crossbarAxis() {
            return crossbarAxis;
        }

        public Direction branchDirection() {
            return branchDirection;
        }

        public int modelRotationX() {
            return modelRotationX;
        }

        public int modelRotationY() {
            return modelRotationY;
        }

        public static Orientation from(Direction.Axis crossbarAxis,
                                       Direction branchDirection) {
            if (!crossbarAxis.isHorizontal()
                    || branchDirection.getAxis() == crossbarAxis) {
                throw new IllegalArgumentException(
                        "T-Pitch orientation requires a horizontal crossbar "
                                + "and a perpendicular branch");
            }

            for (Orientation orientation : values()) {
                if (orientation.crossbarAxis == crossbarAxis
                        && orientation.branchDirection == branchDirection) {
                    return orientation;
                }
            }

            throw new IllegalArgumentException(
                    "Unsupported T-Pitch orientation: crossbar="
                            + crossbarAxis + ", branch=" + branchDirection);
        }
    }
}
