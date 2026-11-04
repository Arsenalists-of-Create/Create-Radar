package com.happysg.radar.gametest;

import com.happysg.radar.block.controller.firing.FireControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.happysg.radar.compat.cbc.CannonMountContext;
import com.happysg.radar.compat.sable.SableDataLinkRelocation;
import com.happysg.radar.registry.ModBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlock;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountExtensionBlock;
import rbasamoyai.createbigcannons.index.CBCBlocks;

import java.util.HashSet;
import java.util.Set;

/**
 * Integration coverage for the CBC endpoint contract used by pitch, yaw,
 * T-Pitch, and Data Links. The Simulated template is only a registered empty
 * test host here; every CBC fixture is installed in a disposable absolute slot.
 */
@GameTestHolder(CreateRadar.MODID)
public final class CbcMountExtensionGameTests {
    private static final int FIXTURE_Y = 96;

    private CbcMountExtensionGameTests() {
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated", template = "extrakineticstest.swivelbearing")
    public static void resolvesOneExtensionToCanonicalMount(GameTestHelper helper) {
        BlockPos mountPos = new BlockPos(256, FIXTURE_Y, 8);
        TestSite site = new TestSite(helper.getLevel(), mountPos, 3);
        try {
            site.prepare();
            ServerLevel level = helper.getLevel();
            BlockPos extensionPos = mountPos.west();

            placeMount(level, mountPos);
            placeExtension(level, extensionPos, Direction.EAST);

            CannonMountContext direct =
                    CannonMountContext.resolveEndpoint(level, mountPos);
            CannonMountContext extended =
                    CannonMountContext.resolveEndpoint(level, extensionPos);
            require(direct != null, "Direct CBC mount did not resolve");
            require(extended != null, "Valid CBC mount extension did not resolve");
            require(direct.sameMount(extended),
                    "Extension did not canonicalize to the actual mount block entity");
            require(extended.getBlockPos().equals(mountPos),
                    "Extension exposed its own position instead of the mount position");

            level.setBlockAndUpdate(extensionPos,
                    extensionState(Direction.WEST));
            require(CannonMountContext.resolveEndpoint(level, extensionPos) == null,
                    "Extension pointing away from the mount was accepted");

            placeExtension(level, extensionPos, Direction.EAST);
            placeExtension(level, extensionPos.west(), Direction.EAST);
            require(CannonMountContext.resolveEndpoint(
                            level, extensionPos.west()) == null,
                    "A chain of mount extensions was accepted");

            CannonMountContext oldMount =
                    CannonMountContext.resolveEndpoint(level, extensionPos);
            require(oldMount != null, "Fixture mount disappeared before replacement");
            level.setBlockAndUpdate(mountPos, Blocks.AIR.defaultBlockState());
            placeMount(level, mountPos);
            CannonMountContext replacement =
                    CannonMountContext.resolveEndpoint(level, extensionPos);
            require(!oldMount.isCurrent(),
                    "Replaced mount block entity remained marked current");
            require(replacement != null && !oldMount.sameMount(replacement),
                    "Extension resolver retained a stale mount block entity");
        } finally {
            site.close();
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated",
            template = "extrakineticstest.swivelbearing")
    public static void controllersFaceDirectAndExtendedMountsOnPlacement(
            GameTestHelper helper) {
        BlockPos center = new BlockPos(448, FIXTURE_Y, 8);
        TestSite site = new TestSite(helper.getLevel(), center, 12);
        try {
            site.prepare();
            ServerLevel level = helper.getLevel();
            Player player = helper.makeMockPlayer(GameType.CREATIVE);
            player.setYRot(0);
            player.setXRot(-30);

            BlockPos directPitchPos = center.offset(-9, 0, -4);
            placeMount(level, directPitchPos.east());
            BlockState directPitch = ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.get()
                    .getStateForPlacement(placementContext(
                            level, player, directPitchPos,
                            ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.asStack()));
            require(directPitch != null
                            && directPitch.getValue(
                            HorizontalKineticBlock.HORIZONTAL_FACING)
                            == Direction.EAST,
                    "Pitch controller did not face its adjacent CBC mount");

            BlockPos extendedPitchPos = center.offset(-3, 0, -4);
            placeExtension(level, extendedPitchPos.east(), Direction.EAST);
            placeMount(level, extendedPitchPos.east(2));
            BlockState extendedPitch =
                    ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.get()
                            .getStateForPlacement(placementContext(
                                    level, player, extendedPitchPos,
                                    ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK
                                            .asStack()));
            require(extendedPitch != null
                            && extendedPitch.getValue(
                            HorizontalKineticBlock.HORIZONTAL_FACING)
                            == Direction.EAST,
                    "Pitch controller did not face a valid mount extension");

            BlockPos directYawPos = center.offset(3, 0, -4);
            placeMount(level, directYawPos.above());
            BlockState directYaw = ModBlocks.AUTO_YAW_CONTROLLER_BLOCK.get()
                    .getStateForPlacement(placementContext(
                            level, player, directYawPos,
                            ModBlocks.AUTO_YAW_CONTROLLER_BLOCK.asStack()));
            require(directYaw != null
                            && directYaw.getValue(
                            DirectionalKineticBlock.FACING)
                            == Direction.DOWN,
                    "Yaw controller did not face away from its mount above");

            BlockPos extendedYawPos = center.offset(9, 0, -4);
            placeExtension(level, extendedYawPos.below(), Direction.DOWN);
            placeMount(level, extendedYawPos.below(2));
            BlockState extendedYaw =
                    ModBlocks.AUTO_YAW_CONTROLLER_BLOCK.get()
                            .getStateForPlacement(placementContext(
                                    level, player, extendedYawPos,
                                    ModBlocks.AUTO_YAW_CONTROLLER_BLOCK
                                            .asStack()));
            require(extendedYaw != null
                            && extendedYaw.getValue(
                            DirectionalKineticBlock.FACING)
                            == Direction.UP,
                    "Yaw controller did not face away from a mount extension below");

            player.setShiftKeyDown(true);
            player.setPose(Pose.CROUCHING);
            BlockPos manualPitchPos = center.offset(-6, 0, 4);
            placeMount(level, manualPitchPos.east());
            BlockState manualPitch = ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.get()
                    .getStateForPlacement(placementContext(
                            level, player, manualPitchPos,
                            ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.asStack()));
            require(manualPitch != null
                            && manualPitch.getValue(
                            HorizontalKineticBlock.HORIZONTAL_FACING)
                            == player.getDirection().getOpposite(),
                    "Crouching no longer preserves pitch manual placement");

            player.setXRot(30);
            BlockPos manualYawPos = center.offset(0, 0, 4);
            placeMount(level, manualYawPos.above());
            BlockState manualYaw = ModBlocks.AUTO_YAW_CONTROLLER_BLOCK.get()
                    .getStateForPlacement(placementContext(
                            level, player, manualYawPos,
                            ModBlocks.AUTO_YAW_CONTROLLER_BLOCK.asStack()));
            require(manualYaw != null
                            && manualYaw.getValue(
                            DirectionalKineticBlock.FACING) == Direction.UP,
                    "Crouching no longer preserves yaw manual placement");

            player.setShiftKeyDown(false);
            player.setPose(Pose.STANDING);
            BlockPos reversedPos = center.offset(6, 0, 4);
            placeExtension(level, reversedPos.east(), Direction.WEST);
            placeMount(level, reversedPos.east(2));
            BlockState reversed = ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.get()
                    .getStateForPlacement(placementContext(
                            level, player, reversedPos,
                            ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.asStack()));
            require(reversed != null
                            && reversed.getValue(
                            HorizontalKineticBlock.HORIZONTAL_FACING)
                            == player.getDirection(),
                    "A reversed mount extension influenced pitch placement");

            BlockPos chainedPos = center.offset(9, 0, 9);
            placeExtension(level, chainedPos.east(), Direction.EAST);
            placeExtension(level, chainedPos.east(2), Direction.EAST);
            placeMount(level, chainedPos.east(3));
            BlockState chained = ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.get()
                    .getStateForPlacement(placementContext(
                            level, player, chainedPos,
                            ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.asStack()));
            require(chained != null
                            && chained.getValue(
                            HorizontalKineticBlock.HORIZONTAL_FACING)
                            == player.getDirection(),
                    "A chain of mount extensions influenced pitch placement");
        } finally {
            site.close();
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated",
            template = "extrakineticstest.swivelbearing")
    public static void createPlacementUsesVirtualControllerInputsOnly(
            GameTestHelper helper) {
        BlockPos center = new BlockPos(512, FIXTURE_Y, 8);
        TestSite site = new TestSite(helper.getLevel(), center, 12);
        try {
            site.prepare();
            ServerLevel level = helper.getLevel();
            Player player = helper.makeMockPlayer(GameType.CREATIVE);
            player.setYRot(180);
            player.setXRot(-30);

            BlockPos pitchPos = center.offset(-7, 0, 0);
            BlockState pitchState =
                    ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.getDefaultState()
                            .setValue(HorizontalKineticBlock.HORIZONTAL_FACING,
                                    Direction.EAST);
            level.setBlockAndUpdate(pitchPos, pitchState);
            BlockPos pitchInputPos = pitchPos.west();
            BlockPlaceContext pitchInputContext = placementContext(
                    level, player, pitchInputPos,
                    AllBlocks.CREATIVE_MOTOR.asStack());

            BlockState pitchMotor = AllBlocks.CREATIVE_MOTOR.get()
                    .getStateForPlacement(pitchInputContext);
            require(pitchMotor != null
                            && pitchMotor.getValue(
                            DirectionalKineticBlock.FACING) == Direction.EAST,
                    "Creative Motor did not face into the pitch input");
            Direction horizontalPreferred =
                    ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.get()
                            .getPreferredHorizontalFacing(pitchInputContext);
            require(horizontalPreferred == Direction.EAST,
                    "Horizontal Create placement helper ignored pitch input");
            require(HorizontalAxisKineticBlock.getPreferredHorizontalAxis(
                            pitchInputContext) == Direction.Axis.X,
                    "Horizontal-axis Create placement helper ignored pitch input");

            BlockState pitchShaft = AllBlocks.SHAFT.get()
                    .getStateForPlacement(placementContext(
                            level, player, pitchInputPos,
                            AllBlocks.SHAFT.asStack()));
            require(pitchShaft != null
                            && pitchShaft.getValue(
                            RotatedPillarKineticBlock.AXIS)
                            == Direction.Axis.X,
                    "Shaft did not align with the pitch input");

            level.setBlockAndUpdate(pitchInputPos, pitchMotor);
            require(level.getBlockEntity(pitchPos)
                            instanceof KineticBlockEntity,
                    "Pitch kinetic block entity was not created");
            require(level.getBlockEntity(pitchInputPos)
                            instanceof KineticBlockEntity,
                    "Creative Motor kinetic block entity was not created");
            KineticBlockEntity pitchController =
                    (KineticBlockEntity) level.getBlockEntity(pitchPos);
            KineticBlockEntity motor =
                    (KineticBlockEntity) level.getBlockEntity(pitchInputPos);
            require(!ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.get()
                            .hasShaftTowards(level, pitchPos, pitchState,
                                    Direction.WEST),
                    "Pitch virtual input leaked into runtime hasShaftTowards");
            require(!RotationPropagator.isConnected(motor, pitchController)
                            && !RotationPropagator.isConnected(
                            pitchController, motor),
                    "Pitch controller joined the Creative Motor kinetic network");

            BlockPos yawPos = center;
            level.setBlockAndUpdate(yawPos,
                    ModBlocks.AUTO_YAW_CONTROLLER_BLOCK.getDefaultState()
                            .setValue(DirectionalKineticBlock.FACING,
                                    Direction.UP));
            BlockPos yawInputPos = yawPos.above();
            BlockState yawMotor = AllBlocks.CREATIVE_MOTOR.get()
                    .getStateForPlacement(placementContext(
                            level, player, yawInputPos,
                            AllBlocks.CREATIVE_MOTOR.asStack()));
            require(yawMotor != null
                            && yawMotor.getValue(
                            DirectionalKineticBlock.FACING) == Direction.DOWN,
                    "Creative Motor did not face into the yaw input");
        } finally {
            site.close();
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated", template = "extrakineticstest.swivelbearing")
    public static void controllerLimitGuiUsesExactInputFaces(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(736, FIXTURE_Y, 8);
        TestSite site = new TestSite(helper.getLevel(), controllerPos, 3);
        try {
            site.prepare();
            ServerLevel level = helper.getLevel();
            Player player = helper.makeMockPlayer(GameType.CREATIVE);
            BlockState state = ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.getDefaultState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.EAST);
            level.setBlockAndUpdate(controllerPos, state);
            BlockHitResult bodyHit = new BlockHitResult(Vec3.atCenterOf(controllerPos), Direction.UP, controllerPos, false);

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.STONE));
            require(state.useWithoutItem(level, player, bodyHit) == InteractionResult.PASS, "Auto Pitch consumed a main-hand block placement interaction");

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Blocks.STONE));
            require(state.useWithoutItem(level, player, bodyHit) == InteractionResult.PASS, "Auto Pitch consumed an off-hand block placement interaction");

            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

            for (Direction facing : Direction.Plane.HORIZONTAL) {
                requireExactControllerInputFace(level, player, controllerPos,
                        ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK
                                .getDefaultState()
                                .setValue(HorizontalKineticBlock
                                                .HORIZONTAL_FACING,
                                        facing),
                        facing.getOpposite(),
                        "Pitch " + facing.getSerializedName());
            }

            for (Direction facing : Direction.values()) {
                requireExactControllerInputFace(level, player, controllerPos,
                        ModBlocks.AUTO_YAW_CONTROLLER_BLOCK
                                .getDefaultState()
                                .setValue(DirectionalKineticBlock.FACING, facing),
                        facing, "Yaw " + facing.getSerializedName());
            }
        } finally {
            site.close();
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated", template = "extrakineticstest.swivelbearing")
    public static void directContactBuildsOrdinaryWeaponGroup(
            GameTestHelper helper) {
        BlockPos mountPos = new BlockPos(512, FIXTURE_Y, 8);
        TestSite site = new TestSite(helper.getLevel(), mountPos, 5);
        try {
            site.prepare();
            ServerLevel level = helper.getLevel();
            BlockPos pitchPos = mountPos.west();
            BlockPos yawPos = mountPos.below();
            BlockPos firePos = mountPos.east();

            placeMount(level, mountPos);
            level.setBlockAndUpdate(pitchPos,
                    ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK
                            .getDefaultState()
                            .setValue(
                                    HorizontalKineticBlock
                                            .HORIZONTAL_FACING,
                                    Direction.EAST));
            level.setBlockAndUpdate(yawPos,
                    ModBlocks.AUTO_YAW_CONTROLLER_BLOCK
                            .getDefaultState()
                            .setValue(
                                    DirectionalKineticBlock.FACING,
                                    Direction.DOWN));
            level.setBlockAndUpdate(firePos,
                    ModBlocks.FIRE_CONTROLLER_BLOCK
                            .getDefaultState());

            WeaponNetworkRuntime runtime =
                    WeaponNetworkRuntime.get(level);
            advertise(level, runtime, pitchPos, yawPos, firePos);
            runtime.reconcile();

            WeaponNetworkRuntime.WeaponGroupView group =
                    runtime.getWeaponGroupView(mountPos);
            require(group != null,
                    "Direct controller contact did not create a group");
            require(pitchPos.equals(group.pitchPos())
                            && yawPos.equals(group.yawPos())
                            && firePos.equals(group.firingPos()),
                    "Direct contact group did not retain all three slots");
            WeaponNetworkRuntime.WeaponControlView view =
                    runtime.getWeaponControlViewFromPitch(pitchPos);
            require(view != null && view.validTopology()
                            && view.mode()
                            == WeaponNetworkRuntime.WeaponNetworkMode.SINGLE,
                    "Ordinary direct-contact control view was invalid");
            require(runtime.getEndpointOrigin(pitchPos)
                            == WeaponNetworkRuntime.EndpointOrigin.CONTACT,
                    "Direct pitch assignment did not report CONTACT origin");

            level.setBlockAndUpdate(pitchPos,
                    level.getBlockState(pitchPos)
                            .setValue(
                                    HorizontalKineticBlock
                                            .HORIZONTAL_FACING,
                                    Direction.WEST));
            runtime.markContactTopologyDirty();
            runtime.reconcile();
            require(runtime.getMountForController(pitchPos) == null,
                    "Pitch contact ignored its configured facing");
        } finally {
            site.close();
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated",
            template = "extrakineticstest.swivelbearing")
    public static void ordinaryContactRejectsMountExtensionsAndControllers(
            GameTestHelper helper) {
        BlockPos center = new BlockPos(544, FIXTURE_Y, 8);
        TestSite site = new TestSite(helper.getLevel(), center, 7);
        try {
            site.prepare();
            ServerLevel level = helper.getLevel();
            BlockPos extensionPitch = center.west(3);
            BlockPos extension = extensionPitch.east();
            BlockPos mount = extension.east();
            placeExtension(level, extension, Direction.EAST);
            placeMount(level, mount);
            level.setBlockAndUpdate(extensionPitch,
                    ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK
                            .getDefaultState()
                            .setValue(
                                    HorizontalKineticBlock
                                            .HORIZONTAL_FACING,
                                    Direction.EAST));

            BlockPos chainedPitch = center.east(2);
            BlockPos chainedYaw = chainedPitch.east();
            placeMount(level, chainedYaw.east());
            level.setBlockAndUpdate(chainedPitch,
                    ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK
                            .getDefaultState()
                            .setValue(
                                    HorizontalKineticBlock
                                            .HORIZONTAL_FACING,
                                    Direction.EAST));
            level.setBlockAndUpdate(chainedYaw,
                    ModBlocks.AUTO_YAW_CONTROLLER_BLOCK
                            .getDefaultState()
                            .setValue(
                                    DirectionalKineticBlock.FACING,
                                    Direction.DOWN));

            WeaponNetworkRuntime runtime =
                    WeaponNetworkRuntime.get(level);
            advertise(level, runtime, extensionPitch, chainedPitch,
                    chainedYaw);
            runtime.reconcile();
            require(runtime.getMountForController(extensionPitch) == null,
                    "Ordinary pitch contact linked through a mount extension");
            require(runtime.getMountForController(chainedPitch) == null,
                    "Pitch contact conducted through another controller");
            require(runtime.getMountForController(chainedYaw) == null,
                    "Incorrectly mounted yaw controller joined a weapon");
        } finally {
            site.close();
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated",
            template = "extrakineticstest.swivelbearing")
    public static void dataLinkOverridesAndRestoresContact(
            GameTestHelper helper) {
        BlockPos contactMount = new BlockPos(576, FIXTURE_Y, 8);
        TestSite site = new TestSite(
                helper.getLevel(), contactMount, 7);
        try {
            site.prepare();
            ServerLevel level = helper.getLevel();
            BlockPos pitchPos = contactMount.west();
            BlockPos explicitMount = contactMount.south(4);
            placeMount(level, contactMount);
            placeMount(level, explicitMount);
            level.setBlockAndUpdate(pitchPos,
                    ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK
                            .getDefaultState()
                            .setValue(
                                    HorizontalKineticBlock
                                            .HORIZONTAL_FACING,
                                    Direction.EAST));

            WeaponNetworkRuntime runtime =
                    WeaponNetworkRuntime.get(level);
            advertise(level, runtime, pitchPos);
            runtime.reconcile();
            require(contactMount.equals(
                            runtime.getMountForController(pitchPos)),
                    "Initial contact assignment was absent");

            linkController(level, pitchPos, explicitMount,
                    DataLinkBlockEntity.WeaponEndpointType.PITCH);
            runtime.reconcile();
            require(explicitMount.equals(
                            runtime.getMountForController(pitchPos)),
                    "Data Link did not replace contact assignment");
            require(explicitMount.equals(
                            runtime.getExplicitMountForController(
                                    pitchPos))
                            && runtime.getEndpointOrigin(pitchPos)
                            == WeaponNetworkRuntime.EndpointOrigin.DATALINK,
                    "Explicit assignment origin/lookup was incorrect");

            level.destroyBlock(pitchPos.below(), false);
            runtime.reconcile();
            require(contactMount.equals(
                            runtime.getMountForController(pitchPos))
                            && runtime.getExplicitMountForController(
                            pitchPos) == null
                            && runtime.getEndpointOrigin(pitchPos)
                            == WeaponNetworkRuntime.EndpointOrigin.CONTACT,
                    "Removing Data Link did not restore live contact");
        } finally {
            site.close();
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated",
            template = "extrakineticstest.swivelbearing")
    public static void contactConflictsFailClosedAndExplicitSlotResolves(
            GameTestHelper helper) {
        BlockPos mountPos = new BlockPos(608, FIXTURE_Y, 8);
        TestSite site = new TestSite(helper.getLevel(), mountPos, 7);
        try {
            site.prepare();
            ServerLevel level = helper.getLevel();
            BlockPos firstPitch = mountPos.west();
            BlockPos secondPitch = mountPos.east();
            BlockPos firePos = mountPos.north();
            placeMount(level, mountPos);
            level.setBlockAndUpdate(firstPitch,
                    ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK
                            .getDefaultState()
                            .setValue(
                                    HorizontalKineticBlock
                                            .HORIZONTAL_FACING,
                                    Direction.EAST));
            level.setBlockAndUpdate(secondPitch,
                    ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK
                            .getDefaultState()
                            .setValue(
                                    HorizontalKineticBlock
                                            .HORIZONTAL_FACING,
                                    Direction.WEST));
            level.setBlockAndUpdate(firePos,
                    ModBlocks.FIRE_CONTROLLER_BLOCK
                            .getDefaultState());

            WeaponNetworkRuntime runtime =
                    WeaponNetworkRuntime.get(level);
            advertise(level, runtime, firstPitch, secondPitch, firePos);
            runtime.reconcile();
            WeaponNetworkRuntime.WeaponControlView invalid =
                    runtime.getWeaponControlViewFromPitch(firstPitch);
            require(invalid != null && !invalid.validTopology(),
                    "Duplicate contact pitch controllers did not fail closed");

            FireControllerBlockEntity fire =
                    (FireControllerBlockEntity)
                            level.getBlockEntity(firePos);
            fire.setPowered(true);
            runtime.reconcile();
            require(!fire.isPowered(),
                    "Invalid topology left its fire controller powered");

            linkController(level, firstPitch, mountPos,
                    DataLinkBlockEntity.WeaponEndpointType.PITCH);
            runtime.reconcile();
            WeaponNetworkRuntime.WeaponControlView resolved =
                    runtime.getWeaponControlViewFromPitch(firstPitch);
            require(resolved != null && resolved.validTopology(),
                    "Explicit pitch slot did not resolve contact ambiguity");
            require(runtime.getWeaponControlViewFromPitch(secondPitch)
                            == null,
                    "Displaced contact pitch retained effective ownership");
        } finally {
            site.close();
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated",
            template = "extrakineticstest.swivelbearing")
    public static void multiMountFireContactFailsClosed(
            GameTestHelper helper) {
        BlockPos center = new BlockPos(672, FIXTURE_Y, 8);
        TestSite site = new TestSite(helper.getLevel(), center, 10);
        try {
            site.prepare();
            ServerLevel level = helper.getLevel();
            BlockPos firstMount = center.east(3);
            BlockPos ambiguousFire = firstMount.east();
            BlockPos secondMount = ambiguousFire.east();
            BlockPos pitchPos = firstMount.west();
            placeMount(level, firstMount);
            placeMount(level, secondMount);
            level.setBlockAndUpdate(pitchPos,
                    ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK
                            .getDefaultState()
                            .setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.EAST));
            level.setBlockAndUpdate(ambiguousFire, ModBlocks.FIRE_CONTROLLER_BLOCK.getDefaultState());

            WeaponNetworkRuntime runtime = WeaponNetworkRuntime.get(level);
            advertise(level, runtime, pitchPos, ambiguousFire);
            runtime.reconcile();

            WeaponNetworkRuntime.WeaponControlView invalid = runtime.getWeaponControlViewFromPitch(pitchPos);
            require(invalid != null && !invalid.validTopology()
                            && runtime.getMountForController(
                            ambiguousFire) == null,
                    "Fire controller touching two mounts did not fail closed");
            FireControllerBlockEntity fire =
                    (FireControllerBlockEntity)
                            level.getBlockEntity(ambiguousFire);
            fire.setPowered(true);
            runtime.reconcile();
            require(!fire.isPowered(),
                    "Multi-mount fire ambiguity remained powered");
        } finally {
            site.close();
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated",
            template = "extrakineticstest.swivelbearing")
    public static void sableAssemblyKeepsWorldYawWithMovedPitch(
            GameTestHelper helper) {
        BlockPos center = new BlockPos(832, FIXTURE_Y, 8);
        TestSite site = new TestSite(helper.getLevel(), center, 12);
        try {
            site.prepare();
            ServerLevel level = helper.getLevel();
            BlockPos worldYaw = center.west(4);
            BlockPos yawLinkPos = worldYaw.below();
            BlockPos oldPitch = center.west();
            BlockPos oldPitchLink = oldPitch.below();
            BlockPos oldMount = center.east(2);
            BlockPos assemblyDelta = new BlockPos(0, 0, 8);
            BlockPos movedPitch = oldPitch.offset(assemblyDelta);
            BlockPos movedPitchLink = oldPitchLink.offset(assemblyDelta);
            BlockPos movedMount = oldMount.offset(assemblyDelta);
            BlockPos filterer = center.west(6);

            level.setBlockAndUpdate(worldYaw,
                    ModBlocks.AUTO_YAW_CONTROLLER_BLOCK.getDefaultState());
            level.setBlockAndUpdate(oldPitch,
                    ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.getDefaultState());
            placeMount(level, oldMount);
            linkController(level, worldYaw, oldMount,
                    DataLinkBlockEntity.WeaponEndpointType.YAW);
            linkController(level, oldPitch, oldMount,
                    DataLinkBlockEntity.WeaponEndpointType.PITCH);

            NetworkData data = NetworkData.get(level);
            NetworkData.Group group = data.getOrCreateGroup(
                    level.dimension(), filterer);
            data.attachWeaponEndpoint(group, worldYaw, oldMount);
            data.addDataLinkToGroup(group, yawLinkPos, worldYaw);
            data.attachWeaponEndpoint(group, oldPitch, oldMount);
            data.addDataLinkToGroup(group, oldPitchLink, oldPitch);

            level.setBlockAndUpdate(movedPitch,
                    ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.getDefaultState());
            placeMount(level, movedMount);
            level.setBlockAndUpdate(movedPitchLink,
                    ModBlocks.RADAR_LINK.getDefaultState()
                            .setValue(DataLinkBlock.FACING, Direction.DOWN)
                            .setValue(DataLinkBlock.LINK_STYLE,
                                    DataLinkBlock.LinkStyle.CONTROLLER));
            require(level.getBlockEntity(oldPitchLink)
                            instanceof DataLinkBlockEntity,
                    "Pre-assembly pitch Data Link disappeared");
            if (!(level.getBlockEntity(movedPitchLink)
                    instanceof DataLinkBlockEntity movedLink)) {
                throw new GameTestAssertException(
                        "Assembled pitch Data Link was not created");
            }

            SableDataLinkRelocation.capture(level, level,
                    oldPitchLink, movedPitchLink);
            level.setBlockAndUpdate(oldPitchLink,
                    Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(oldPitch,
                    Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(oldMount,
                    Blocks.AIR.defaultBlockState());
            movedLink.target(movedMount);
            movedLink.setWeaponEndpointType(
                    DataLinkBlockEntity.WeaponEndpointType.PITCH);

            SableDataLinkRelocation.process(level);
            WeaponNetworkRuntime runtime = WeaponNetworkRuntime.get(level);
            runtime.reconcile();

            require(level.getBlockEntity(worldYaw)
                            instanceof AutoYawControllerBlockEntity,
                    "World-space yaw controller moved with the assembly");
            require(movedMount.equals(data.getWeaponMountForController(
                            level.dimension(), worldYaw)),
                    "Saved world-space yaw mapping retained the old mount");
            require(movedMount.equals(data.getWeaponMountForController(
                            level.dimension(), movedPitch)),
                    "Saved pitch mapping did not move into the plot");
            require(data.getFiltererForWeaponMount(
                            level.dimension(), oldMount) == null
                            && filterer.equals(data.getFiltererForWeaponMount(
                            level.dimension(), movedMount)),
                    "Saved pitch/yaw mount ownership remained split");
            require(movedMount.equals(runtime.getMountForController(worldYaw)),
                    "Runtime world-space yaw mapping retained the old mount");
            require(movedMount.equals(runtime.getMountForController(movedPitch)),
                    "Runtime pitch mapping did not move into the plot");
            require(level.getBlockEntity(yawLinkPos)
                            instanceof DataLinkBlockEntity yawLink
                            && movedMount.equals(yawLink.getTargetPosition()),
                    "Stationary yaw Data Link was not rebased to the moved mount");
            require(movedMount.equals(movedLink.getTargetPosition()),
                    "Moved pitch Data Link did not target the moved mount");

            level.setBlockAndUpdate(movedPitchLink,
                    Blocks.AIR.defaultBlockState());
            require(movedMount.equals(data.getWeaponMountForController(
                            level.dimension(), worldYaw))
                            && filterer.equals(data.getFiltererForWeaponMount(
                            level.dimension(), movedMount)),
                    "Removing one shared link released the yaw controller's mount");
        } finally {
            site.close();
        }
        helper.succeed();
    }

    private static void placeMount(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, CBCBlocks.CANNON_MOUNT.getDefaultState()
                // CBC exposes the horizontal extension shaft perpendicular to
                // the cannon's horizontal facing.
                .setValue(CannonMountBlock.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static void requireExactControllerInputFace(
            ServerLevel level, Player player, BlockPos controllerPos,
            BlockState state, Direction inputFace, String description) {
        level.setBlockAndUpdate(controllerPos, state);
        for (Direction clickedFace : Direction.values()) {
            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(controllerPos), clickedFace,
                    controllerPos, false);
            InteractionResult result = state.useWithoutItem(
                    level, player, hit);
            require((result == InteractionResult.PASS)
                            == (clickedFace == inputFace),
                    description + " treated "
                            + clickedFace.getSerializedName()
                            + (clickedFace == inputFace
                            ? " as a limits-GUI face"
                            : " as an input face"));
        }
    }

    private static void placeExtension(ServerLevel level, BlockPos pos, Direction facing) {
        level.setBlockAndUpdate(pos, extensionState(facing));
    }

    private static BlockState extensionState(Direction facing) {
        return CBCBlocks.CANNON_MOUNT_EXTENSION.getDefaultState()
                .setValue(CannonMountExtensionBlock.FACING, facing);
    }

    private static BlockPlaceContext placementContext(
            ServerLevel level, Player player, BlockPos placementPos,
            ItemStack stack
    ) {
        return new BlockPlaceContext(
                level,
                player,
                InteractionHand.MAIN_HAND,
                stack,
                new BlockHitResult(
                        Vec3.atCenterOf(placementPos),
                        Direction.UP,
                        placementPos,
                        false));
    }

    private static void linkController(
            ServerLevel level,
            BlockPos controllerPos,
            BlockPos mountPos,
            DataLinkBlockEntity.WeaponEndpointType type
    ) {
        BlockPos linkPos = controllerPos.below();
        level.setBlockAndUpdate(linkPos,
                ModBlocks.RADAR_LINK.getDefaultState()
                        .setValue(DataLinkBlock.FACING, Direction.DOWN)
                        .setValue(DataLinkBlock.LINK_STYLE,
                                DataLinkBlock.LinkStyle.CONTROLLER));
        if (!(level.getBlockEntity(linkPos)
                instanceof DataLinkBlockEntity link)) {
            throw new GameTestAssertException(
                    "Data Link block entity was not created");
        }
        link.target(mountPos);
        link.setWeaponEndpointType(type);
        require(WeaponNetworkRuntime.get(level).register(link),
                "Weapon Data Link registration failed for " + type);
    }

    private static void advertise(
            ServerLevel level,
            WeaponNetworkRuntime runtime,
            BlockPos... controllerPositions
    ) {
        for (BlockPos position : controllerPositions) {
            net.minecraft.world.level.block.entity.BlockEntity controller =
                    level.getBlockEntity(position);
            if (controller != null) {
                runtime.advertiseContactController(controller);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static final class TestSite implements AutoCloseable {
        private final ServerLevel level;
        private final BlockPos center;
        private final int radius;
        private final Set<Long> forcedByTest = new HashSet<>();

        private TestSite(ServerLevel level, BlockPos center, int radius) {
            this.level = level;
            this.center = center;
            this.radius = radius;
        }

        private void prepare() {
            if (!(level.getServer() instanceof GameTestServer)) {
                throw new GameTestAssertException(
                        "Absolute CBC fixtures require the disposable GameTest server");
            }
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    ChunkPos chunk = new ChunkPos(center.offset(x, 0, z));
                    if (!level.getForcedChunks().contains(chunk.toLong())) {
                        level.setChunkForced(chunk.x, chunk.z, true);
                        forcedByTest.add(chunk.toLong());
                    }
                    level.getChunk(chunk.x, chunk.z);
                }
            }
            clearBlocks();
        }

        private void clearBlocks() {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        level.setBlockAndUpdate(center.offset(x, y, z),
                                Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }

        @Override
        public void close() {
            clearBlocks();
            for (long packedChunk : forcedByTest) {
                ChunkPos chunk = new ChunkPos(packedChunk);
                level.setChunkForced(chunk.x, chunk.z, false);
            }
        }
    }
}
