package com.happysg.radar.block.controller.tpitch;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class TPitchPlacementSelfTest {
    private TPitchPlacementSelfTest() {
    }

    public static void main(String[] args) {
        verifyOrientationTable();
        verifyBranchSelection();
        verifyCrossbarMountPositions();
        System.out.println("PASS T-Pitch placement orientation checks");
    }

    private static void verifyOrientationTable() {
        TPitchControllerBlock.Orientation[] orientations =
                TPitchControllerBlock.Orientation.values();
        require(orientations.length == 8, "expected exactly eight orientations");

        for (TPitchControllerBlock.Orientation orientation : orientations) {
            require(orientation.crossbarAxis().isHorizontal(),
                    orientation + " has a vertical crossbar");
            require(orientation.branchDirection().getAxis()
                            != orientation.crossbarAxis(),
                    orientation + " has parallel crossbar and branch axes");
            require(TPitchControllerBlock.Orientation.from(
                            orientation.crossbarAxis(),
                            orientation.branchDirection()) == orientation,
                    orientation + " does not round-trip through from()");

            Direction transformedCrossbar = transformModelDirection(
                    Direction.EAST,
                    orientation.modelRotationX(),
                    orientation.modelRotationY());
            Direction transformedBranch = transformModelDirection(
                    Direction.SOUTH,
                    orientation.modelRotationX(),
                    orientation.modelRotationY());
            require(transformedCrossbar.getAxis() == orientation.crossbarAxis(),
                    orientation + " model crossbar rotation is incorrect");
            require(transformedBranch == orientation.branchDirection(),
                    orientation + " model branch rotation is incorrect");
        }
    }

    private static void verifyBranchSelection() {
        requireBranch(Direction.Axis.X, new Vec3(100, 2, -3), Direction.NORTH);
        requireBranch(Direction.Axis.X, new Vec3(-100, -4, 3), Direction.DOWN);
        requireBranch(Direction.Axis.X, new Vec3(0, 8, 1), Direction.UP);
        requireBranch(Direction.Axis.X, new Vec3(0, 1, 8), Direction.SOUTH);

        requireBranch(Direction.Axis.Z, new Vec3(8, 1, 100), Direction.EAST);
        requireBranch(Direction.Axis.Z, new Vec3(-8, 1, -100), Direction.WEST);
        requireBranch(Direction.Axis.Z, new Vec3(1, 8, 0), Direction.UP);
        requireBranch(Direction.Axis.Z, new Vec3(1, -8, 0), Direction.DOWN);
    }

    private static void requireBranch(Direction.Axis crossbarAxis, Vec3 vector,
                                      Direction expected) {
        Direction actual = TPitchControllerBlock.closestPerpendicularDirection(
                crossbarAxis, vector);
        require(actual == expected,
                "expected " + expected + " for " + crossbarAxis
                        + " crossbar and " + vector + ", got " + actual);
    }

    private static void verifyCrossbarMountPositions() {
        BlockPos controller = new BlockPos(10, 20, 30);
        for (TPitchControllerBlock.Orientation orientation
                : TPitchControllerBlock.Orientation.values()) {
            Direction positive = orientation.crossbarAxis() == Direction.Axis.X
                    ? Direction.EAST : Direction.SOUTH;
            require(TPitchControllerBlock.isCrossbarEndpointPosition(
                            controller, controller.relative(positive),
                            orientation.crossbarAxis()),
                    orientation + " rejected its positive crossbar side");
            require(TPitchControllerBlock.isCrossbarEndpointPosition(
                            controller, controller.relative(positive.getOpposite()),
                            orientation.crossbarAxis()),
                    orientation + " rejected its negative crossbar side");
            require(!TPitchControllerBlock.isCrossbarEndpointPosition(
                            controller,
                            controller.relative(orientation.branchDirection()),
                            orientation.crossbarAxis()),
                    orientation + " accepted its branch as a mount side");
            require(!TPitchControllerBlock.isCrossbarEndpointPosition(
                            controller,
                            controller.relative(
                                    orientation.branchDirection().getOpposite()),
                            orientation.crossbarAxis()),
                    orientation + " accepted its unbranched side as a mount side");
            require(!TPitchControllerBlock.isCrossbarEndpointPosition(
                            controller, controller.relative(positive, 2),
                            orientation.crossbarAxis()),
                    orientation + " accepted a non-adjacent endpoint");
        }
    }

    private static Direction transformModelDirection(Direction direction,
                                                     int rotationX,
                                                     int rotationY) {
        Direction transformed = direction;
        for (int degrees = 0; degrees < rotationX; degrees += 90) {
            transformed = rotateNegativeX(transformed);
        }
        for (int degrees = 0; degrees < rotationY; degrees += 90) {
            transformed = rotateNegativeY(transformed);
        }
        return transformed;
    }

    private static Direction rotateNegativeX(Direction direction) {
        return switch (direction) {
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            case NORTH -> Direction.DOWN;
            case SOUTH -> Direction.UP;
            case EAST, WEST -> direction;
        };
    }

    private static Direction rotateNegativeY(Direction direction) {
        return switch (direction) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            case UP, DOWN -> direction;
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
