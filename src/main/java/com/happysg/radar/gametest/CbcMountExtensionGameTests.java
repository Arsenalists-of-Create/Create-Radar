package com.happysg.radar.gametest;

import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlock;
import com.happysg.radar.block.controller.firing.FireControllerBlockEntity;
import com.happysg.radar.block.controller.tpitch.TPitchControllerBlock;
import com.happysg.radar.block.controller.tpitch.TPitchControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlock;
import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.happysg.radar.compat.cbc.CannonMountContext;
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
    @GameTest(templateNamespace = "simulated",
            template = "extrakineticstest.swivelbearing")
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

            BlockPos tPitchPos = center.offset(7, 0, 0);
            level.setBlockAndUpdate(tPitchPos,
                    ModBlocks.T_PITCH.getDefaultState()
                            .setValue(TPitchControllerBlock.ORIENTATION,
                                    TPitchControllerBlock.Orientation.X_UP));
            BlockPos tPitchInputPos = tPitchPos.above();
            BlockState tPitchMotor = AllBlocks.CREATIVE_MOTOR.get()
                    .getStateForPlacement(placementContext(
                            level, player, tPitchInputPos,
                            AllBlocks.CREATIVE_MOTOR.asStack()));
            require(tPitchMotor != null
                            && tPitchMotor.getValue(
                            DirectionalKineticBlock.FACING) == Direction.DOWN,
                    "Creative Motor did not face into the T-Pitch branch input");
        } finally {
            site.close();
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated",
            template = "extrakineticstest.swivelbearing")
    public static void tPitchAcceptsMixedDirectAndExtendedMounts(
            GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(320, FIXTURE_Y, 8);
        TestSite site = new TestSite(helper.getLevel(), controllerPos, 4);
        try {
            site.prepare();
            ServerLevel level = helper.getLevel();
            BlockPos directMountPos = controllerPos.west();
            BlockPos extensionPos = controllerPos.east();
            BlockPos extendedMountPos = extensionPos.east();
            BlockPos unrelatedMountPos = controllerPos.north(3);

            placeMount(level, directMountPos);
            placeExtension(level, extensionPos, Direction.EAST);
            placeMount(level, extendedMountPos);
            placeMount(level, unrelatedMountPos);
            level.setBlockAndUpdate(controllerPos,
                    ModBlocks.T_PITCH.getDefaultState()
                            .setValue(TPitchControllerBlock.ORIENTATION,
                                    TPitchControllerBlock.Orientation.X_SOUTH));

            if (!(level.getBlockEntity(controllerPos)
                    instanceof TPitchControllerBlockEntity controller)) {
                throw new GameTestAssertException(
                        "T-Pitch controller block entity was not created");
            }

            require(controller.canLinkMount(directMountPos),
                    "T-Pitch rejected its direct crossbar mount");
            require(controller.canLinkMount(extensionPos),
                    "T-Pitch rejected its extension endpoint");
            require(controller.canLinkMount(extendedMountPos),
                    "T-Pitch rejected the canonical mount behind its extension");
            require(!controller.canLinkMount(unrelatedMountPos),
                    "T-Pitch accepted a mount outside its crossbar endpoints");

            level.setBlockAndUpdate(extensionPos,
                    extensionState(Direction.WEST));
            controller.markMountDirtyExternal();
            require(!controller.canLinkMount(extendedMountPos),
                    "T-Pitch retained a mount behind an invalid extension");

            placeExtension(level, extensionPos, Direction.EAST);
            controller.markMountDirtyExternal();
            require(controller.canLinkMount(extendedMountPos),
                    "T-Pitch did not recover its valid extension endpoint");
            level.setBlockAndUpdate(extendedMountPos,
                    Blocks.AIR.defaultBlockState());
            placeMount(level, extendedMountPos);
            require(controller.canLinkMount(extendedMountPos),
                    "T-Pitch retained a stale canonical mount after replacement");
        } finally {
            site.close();
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated",
            template = "extrakineticstest.swivelbearing")
    public static void tPitchBuildsDualWeaponControlView(
            GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(384, FIXTURE_Y, 8);
        TestSite site = new TestSite(helper.getLevel(), controllerPos, 7);
        try {
            site.prepare();
            ServerLevel level = helper.getLevel();
            BlockPos primaryMount = controllerPos.west();
            BlockPos secondaryMount = controllerPos.east();
            BlockPos primaryYaw = controllerPos.north(3);
            BlockPos primaryFire = controllerPos.north(5);
            BlockPos secondaryYaw = controllerPos.south(3);
            BlockPos secondaryFire = controllerPos.south(5);

            placeMount(level, primaryMount);
            placeMount(level, secondaryMount);
            level.setBlockAndUpdate(controllerPos,
                    ModBlocks.T_PITCH.getDefaultState()
                            .setValue(TPitchControllerBlock.ORIENTATION,
                                    TPitchControllerBlock.Orientation.X_SOUTH));
            level.setBlockAndUpdate(primaryYaw,
                    ModBlocks.AUTO_YAW_CONTROLLER_BLOCK
                            .getDefaultState());
            level.setBlockAndUpdate(secondaryYaw,
                    ModBlocks.AUTO_YAW_CONTROLLER_BLOCK
                            .getDefaultState());
            level.setBlockAndUpdate(primaryFire,
                    ModBlocks.FIRE_CONTROLLER_BLOCK
                            .getDefaultState());
            level.setBlockAndUpdate(secondaryFire,
                    ModBlocks.FIRE_CONTROLLER_BLOCK
                            .getDefaultState());

            // Link the per-mount endpoints first to prove that the T-Pitch
            // overlay is independent of placement order.
            linkController(level, primaryYaw, primaryMount,
                    DataLinkBlockEntity.WeaponEndpointType.YAW);
            linkController(level, primaryFire, primaryMount,
                    DataLinkBlockEntity.WeaponEndpointType.FIRING);
            linkController(level, secondaryYaw, secondaryMount,
                    DataLinkBlockEntity.WeaponEndpointType.YAW);
            linkController(level, secondaryFire, secondaryMount,
                    DataLinkBlockEntity.WeaponEndpointType.FIRING);
            linkController(level, controllerPos, primaryMount,
                    DataLinkBlockEntity.WeaponEndpointType.PITCH);

            WeaponNetworkRuntime runtime =
                    WeaponNetworkRuntime.get(level);
            WeaponNetworkRuntime.WeaponControlView view =
                    runtime.getWeaponControlViewFromPitch(controllerPos);
            require(view != null, "T-Pitch dual control view was absent");
            require(view.mode()
                            == WeaponNetworkRuntime.WeaponNetworkMode
                            .T_PITCH_DUAL,
                    "T-Pitch retained the ordinary single-mount mode");
            require(view.validTopology(),
                    "Valid two-mount T-Pitch topology was rejected");
            require(view.channels().size() == 2,
                    "Dual view did not expose exactly two mount channels");

            WeaponNetworkRuntime.MountChannelView first =
                    view.channels().get(0);
            WeaponNetworkRuntime.MountChannelView second =
                    view.channels().get(1);
            require(first.mountPos().equals(primaryMount)
                            && primaryYaw.equals(first.yawPos())
                            && primaryFire.equals(first.firingPos()),
                    "Selected primary mount did not retain its yaw/fire endpoints");
            require(second.mountPos().equals(secondaryMount)
                            && secondaryYaw.equals(second.yawPos())
                            && secondaryFire.equals(second.firingPos()),
                    "Companion mount did not expose its yaw/fire endpoints");
            require(!runtime.canAttachEndpoint(
                            DataLinkBlockEntity.WeaponEndpointType.PITCH,
                            controllerPos.above(4), secondaryMount),
                    "Companion mount accepted a competing pitch endpoint");

            level.setBlockAndUpdate(primaryMount,
                    Blocks.AIR.defaultBlockState());
            ((TPitchControllerBlockEntity)
                    level.getBlockEntity(controllerPos))
                    .markMountDirtyExternal();
            WeaponNetworkRuntime.WeaponControlView fallback =
                    runtime.getWeaponControlViewFromPitch(controllerPos);
            require(fallback != null
                            && fallback.channels().stream().anyMatch(
                            channel -> channel.mountPos()
                                    .equals(secondaryMount)),
                    "Remaining mount disappeared when the preferred mount was removed");
        } finally {
            site.close();
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated",
            template = "extrakineticstest.swivelbearing")
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
    public static void tPitchContactDominanceAndExtensionQualification(
            GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(640, FIXTURE_Y, 8);
        TestSite site = new TestSite(
                helper.getLevel(), controllerPos, 10);
        try {
            site.prepare();
            ServerLevel level = helper.getLevel();
            BlockPos westMount = controllerPos.west();
            BlockPos eastExtension = controllerPos.east();
            BlockPos eastMount = eastExtension.east();
            BlockPos sideFire = controllerPos.south(4);

            placeMount(level, westMount);
            placeExtension(level, eastExtension, Direction.EAST);
            placeMount(level, eastMount);
            level.setBlockAndUpdate(controllerPos,
                    ModBlocks.T_PITCH.getDefaultState()
                            .setValue(
                                    TPitchControllerBlock.ORIENTATION,
                                    TPitchControllerBlock.Orientation
                                            .X_SOUTH));
            level.setBlockAndUpdate(sideFire,
                    ModBlocks.FIRE_CONTROLLER_BLOCK
                            .getDefaultState());

            WeaponNetworkRuntime runtime =
                    WeaponNetworkRuntime.get(level);
            advertise(level, runtime, controllerPos, sideFire);
            runtime.reconcile();
            WeaponNetworkRuntime.WeaponControlView directOnly =
                    runtime.getWeaponControlViewFromPitch(controllerPos);
            require(directOnly != null
                            && directOnly.channels().size() == 1
                            && westMount.equals(
                            directOnly.preferredMountPos()),
                    "Unlinked extension side joined T-Pitch contact");

            linkController(level, sideFire, eastMount,
                    DataLinkBlockEntity.WeaponEndpointType.FIRING);
            runtime.reconcile();
            WeaponNetworkRuntime.WeaponControlView sideTargeted =
                    runtime.getWeaponControlViewFromPitch(controllerPos);
            require(sideTargeted != null
                            && sideTargeted.channels().size() == 2
                            && eastMount.equals(
                            sideTargeted.preferredMountPos()),
                    "Side Data Link did not qualify and dominate extension mount");

            linkController(level, controllerPos, westMount,
                    DataLinkBlockEntity.WeaponEndpointType.PITCH);
            runtime.reconcile();
            WeaponNetworkRuntime.WeaponControlView ownLinkTie =
                    runtime.getWeaponControlViewFromPitch(controllerPos);
            require(ownLinkTie != null
                            && westMount.equals(
                            ownLinkTie.preferredMountPos())
                            && westMount.equals(
                            ownLinkTie.channels().getFirst()
                                    .mountPos()),
                    "T-Pitch's own Data Link did not win a two-sided tie");

            level.destroyBlock(sideFire.below(), false);
            runtime.reconcile();
            WeaponNetworkRuntime.WeaponControlView extensionRemoved =
                    runtime.getWeaponControlViewFromPitch(controllerPos);
            require(extensionRemoved != null
                            && extensionRemoved.channels().size() == 1,
                    "Extension side survived removal of its qualifying Data Link");
        } finally {
            site.close();
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated",
            template = "extrakineticstest.swivelbearing")
    public static void contactTPitchIsStableAndMultiMountFireFailsClosed(
            GameTestHelper helper) {
        BlockPos center = new BlockPos(672, FIXTURE_Y, 8);
        TestSite site = new TestSite(helper.getLevel(), center, 10);
        try {
            site.prepare();
            ServerLevel level = helper.getLevel();
            BlockPos tPitchPos = center.west(5);
            BlockPos stableWest = tPitchPos.west();
            BlockPos stableEast = tPitchPos.east();
            placeMount(level, stableWest);
            placeMount(level, stableEast);
            level.setBlockAndUpdate(tPitchPos,
                    ModBlocks.T_PITCH.getDefaultState()
                            .setValue(
                                    TPitchControllerBlock.ORIENTATION,
                                    TPitchControllerBlock.Orientation
                                            .X_SOUTH));

            BlockPos firstMount = center.east(3);
            BlockPos ambiguousFire = firstMount.east();
            BlockPos secondMount = ambiguousFire.east();
            BlockPos pitchPos = firstMount.west();
            placeMount(level, firstMount);
            placeMount(level, secondMount);
            level.setBlockAndUpdate(pitchPos,
                    ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK
                            .getDefaultState()
                            .setValue(
                                    HorizontalKineticBlock
                                            .HORIZONTAL_FACING,
                                    Direction.EAST));
            level.setBlockAndUpdate(ambiguousFire,
                    ModBlocks.FIRE_CONTROLLER_BLOCK
                            .getDefaultState());

            WeaponNetworkRuntime runtime =
                    WeaponNetworkRuntime.get(level);
            advertise(level, runtime, tPitchPos, pitchPos,
                    ambiguousFire);
            runtime.reconcile();

            WeaponNetworkRuntime.WeaponControlView tPitchView =
                    runtime.getWeaponControlViewFromPitch(tPitchPos);
            require(tPitchView != null
                            && tPitchView.channels().size() == 2
                            && stableWest.equals(
                            tPitchView.preferredMountPos())
                            && stableWest.equals(
                            tPitchView.channels().getFirst()
                                    .mountPos()),
                    "Contact-only T-Pitch dominance was not stable (x,y,z)");

            WeaponNetworkRuntime.WeaponControlView invalid =
                    runtime.getWeaponControlViewFromPitch(pitchPos);
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

    private static void placeMount(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, CBCBlocks.CANNON_MOUNT.getDefaultState()
                // CBC exposes the horizontal extension shaft perpendicular to
                // the cannon's horizontal facing.
                .setValue(CannonMountBlock.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static void placeExtension(ServerLevel level, BlockPos pos,
                                       Direction facing) {
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
