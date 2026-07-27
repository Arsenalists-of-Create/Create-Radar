package com.happysg.radar.gametest;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.kinetic.KineticAngleMath;
import com.happysg.radar.block.controller.kinetic.KineticMountAdapter;
import com.happysg.radar.block.controller.kinetic.KineticMountAdapterResolution;
import com.happysg.radar.block.controller.kinetic.KineticMountFrame;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.compat.simulated.SimulatedSwivelMountAdapter;
import com.happysg.radar.registry.ModBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

@GameTestHolder(CreateRadar.MODID)
public final class SimulatedSwivelControllerGameTests {
    private static final BlockPos BEARING_POS = new BlockPos(2, 3, 2);
    private static final double COMMAND_DEGREES = 5.0;
    private static final double MAX_CONTROLLER_SWIVEL_RPM = 32.0;
    private static final int SAMPLE_TICKS = 700;
    private static final long PHYSICS_TICK_NANOS = 45_000_000L;
    private static long lastPacedGameTick = Long.MIN_VALUE;

    private SimulatedSwivelControllerGameTests() {
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated", template = "extrakineticstest.swivelbearing", timeoutTicks = 900)
    public static void pitchEast(GameTestHelper helper) {
        runControllerTest(helper, 0, Direction.EAST, Direction.EAST);
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated", template = "extrakineticstest.swivelbearing", timeoutTicks = 900)
    public static void pitchWestMirrored(GameTestHelper helper) {
        runControllerTest(helper, 1, Direction.WEST, Direction.EAST);
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated", template = "extrakineticstest.swivelbearing", timeoutTicks = 900)
    public static void pitchNorth(GameTestHelper helper) {
        runControllerTest(helper, 2, Direction.NORTH, Direction.NORTH);
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated", template = "extrakineticstest.swivelbearing", timeoutTicks = 900)
    public static void pitchSouthMirrored(GameTestHelper helper) {
        runControllerTest(helper, 3, Direction.SOUTH, Direction.NORTH);
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated", template = "extrakineticstest.swivelbearing", timeoutTicks = 900)
    public static void yawDown(GameTestHelper helper) {
        runControllerTest(helper, 4, Direction.DOWN, Direction.DOWN);
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = "simulated", template = "extrakineticstest.swivelbearing", timeoutTicks = 900)
    public static void yawUp(GameTestHelper helper) {
        runControllerTest(helper, 5, Direction.UP, Direction.UP);
    }

    private static void runControllerTest(GameTestHelper helper, int slot, Direction bearingFacing,
                                          Direction controllerFacing) {
        AtomicReference<Fixture> fixtureRef = new AtomicReference<>();
        SampleState samples = new SampleState();
        BlockPos bearingPos = new BlockPos(8 + slot * 32, 96, 8);
        ServerLevel level = helper.getLevel();
        ChunkPos fixtureChunk = new ChunkPos(bearingPos);
        TestSite site = new TestSite(bearingPos,
                level.getForcedChunks().contains(fixtureChunk.toLong()));
        var sequence = helper.startSequence();
        sequence.thenExecute(() -> runWithCleanup(helper, site, () -> {
            rebuildBearingFixture(helper, site, bearingFacing);
            fixtureRef.set(installController(helper, site.bearingPos(),
                    bearingFacing, controllerFacing));
        }));
        sequence.thenIdle(1).thenWaitUntil(() -> armController(fixtureRef.get(), samples));
        for (int i = 0; i < SAMPLE_TICKS; i++) {
            sequence.thenIdle(1).thenExecute(() -> runWithCleanup(helper, site,
                    () -> sample(fixtureRef.get(), samples)));
        }
        sequence.thenExecute(() -> runWithCleanup(helper, site,
                () -> assertFinalPose(fixtureRef.get(), samples)));
        sequence.thenExecute(() -> cleanupFixture(helper, site));
        sequence.thenSucceed();
    }

    private static void sample(Fixture fixture, SampleState samples) {
        // Production radar targeting refreshes the same world-space launch
        // direction as solver results arrive. Reassert it every sample so this
        // test cannot accidentally exercise only the finite setAngle path.
        fixture.command().run();
        paceAsynchronousPhysics(fixture);
        KineticMountAdapter adapter = resolveAdapter(fixture.controller());
        KineticBlockEntity endpoint = fixture.bearing().getExtraKinetics();
        if (fixture.controller().sequenceContext != null || endpoint.sequenceContext != null
                || adapter.hasSequenceContext()) {
            throw new GameTestAssertException(
                    "Reference-style Swivel drive unexpectedly acquired sequence context");
        }
        if (fixture.controller().isCustomConnection(endpoint, fixture.controller().getBlockState(),
                endpoint.getBlockState())) {
            throw new GameTestAssertException("Swivel drive unexpectedly exposed a custom kinetic edge");
        }

        double generatedRpm = fixture.controller().getGeneratedSpeed();
        double endpointRpm = endpoint.getTheoreticalSpeed();
        if (Math.abs(generatedRpm) > MAX_CONTROLLER_SWIVEL_RPM + 1.0e-4
                || Math.abs(endpointRpm) > MAX_CONTROLLER_SWIVEL_RPM + 1.0e-4) {
            throw new GameTestAssertException("Swivel controller exceeded 32 RPM: generated="
                    + generatedRpm + " endpoint=" + endpointRpm);
        }
        if (fixture.controller().hasSource()) {
            throw new GameTestAssertException("Isolated controller generator acquired an input source");
        }
        if (fixture.motor().network != null && fixture.controller().network != null
                && fixture.motor().network.equals(fixture.controller().network)) {
            throw new GameTestAssertException("Input shaft and Swivel output joined one kinetic network");
        }
        if (Math.abs(generatedRpm) > 1.0e-5) {
            samples.sawGeneratedDrive = true;
        }
        if (Math.abs(endpointRpm) > 1.0e-5) {
            samples.sawPoweredEndpoint = true;
            samples.activeEndpointSamples++;
            samples.maxActiveStep = Math.max(samples.maxActiveStep,
                    adapter.effectiveDegreesPerTick(endpointRpm));
            if (!endpoint.hasSource() || !fixture.controller().getBlockPos().equals(endpoint.source)) {
                throw new GameTestAssertException("Powered Swivel endpoint lacked ordinary controller source");
            }
            if (endpoint.network == null || fixture.controller().network == null
                    || !endpoint.network.equals(fixture.controller().network)) {
                throw new GameTestAssertException("Powered Swivel endpoint had a mismatched output network");
            }
            if (Math.abs(generatedRpm) > 0.1
                    && Math.abs(endpointRpm + generatedRpm)
                    <= Math.max(0.01, Math.abs(generatedRpm) * 0.01)) {
                samples.sawOrdinaryCogRatio = true;
            }
            if (samples.forcedNetworkRebuild) {
                samples.sawPostRebuildDrive = true;
            }
        }
        if (Math.abs(endpointRpm) > 1.0e-5 && samples.activeEndpointSamples >= 2
                && !samples.forcedNetworkRebuild) {
            if (fixture.controller().sequenceContext != null || endpoint.sequenceContext != null) {
                throw new GameTestAssertException("Sequence context existed before forced rebuild");
            }
            samples.rebuildSetpoint = adapter.getTargetAngleDegrees();
            endpoint.onSpeedChanged(endpoint.getSpeed()); // unchanged-RPM regression path
            fixture.controller().detachKinetics();
            endpoint.detachKinetics();
            if (endpoint.hasSource() || endpoint.hasNetwork() || endpoint.isSource()
                    || Math.abs(endpoint.getSpeed()) > 1.0e-5
                    || Math.abs(endpoint.getTheoreticalSpeed()) > 1.0e-5) {
                throw new GameTestAssertException(
                        "Forced detach did not produce an observed disconnected topology: controllerNetwork="
                                + fixture.controller().hasNetwork() + " endpointSource="
                                + endpoint.hasSource() + " endpointNetwork=" + endpoint.hasNetwork()
                                + " endpointIsSource=" + endpoint.isSource() + " endpointSpeed="
                                + endpoint.getSpeed() + " endpointTheoretical="
                                + endpoint.getTheoreticalSpeed());
            }
            samples.sawObservedDisconnect = true;
            if (fixture.controller().sequenceContext != null || endpoint.sequenceContext != null) {
                throw new GameTestAssertException("Sequence context appeared during forced detach");
            }
            fixture.controller().attachKinetics();
            endpoint.attachKinetics();
            if (fixture.controller().sequenceContext != null || endpoint.sequenceContext != null) {
                throw new GameTestAssertException("Sequence context appeared during forced reattach");
            }
            samples.forcedNetworkRebuild = true;
        }

        double setpoint = adapter.getTargetAngleDegrees();
        if (!Double.isFinite(samples.initialSetpoint)) {
            samples.initialSetpoint = fixture.startingSetpoint();
            samples.lastSetpoint = fixture.startingSetpoint();
        }
        if (Double.isFinite(samples.lastSetpoint) && Double.isFinite(setpoint)) {
            samples.unwrappedSetpointTravel += Math.abs(KineticAngleMath.shortestDelta(
                    samples.lastSetpoint, setpoint));
        }
        samples.lastSetpoint = setpoint;
        if (samples.forcedNetworkRebuild && Double.isFinite(samples.rebuildSetpoint)
                && Math.abs(KineticAngleMath.shortestDelta(samples.rebuildSetpoint, setpoint)) > 1.0e-4) {
            samples.sawPostRebuildMotion = true;
        }

        double physical = adapter.getPhysicalAngleDegrees();
        if (Double.isFinite(physical)) {
            if (!Double.isFinite(samples.initialPhysical)) {
                samples.initialPhysical = physical;
            }
            double physicalMotion = Double.isFinite(samples.lastPhysical)
                    ? Math.abs(KineticAngleMath.shortestDelta(samples.lastPhysical, physical)) : 0.0;
            if (Double.isFinite(samples.lastPhysical)) {
                samples.unwrappedPhysicalTravel += physicalMotion;
                samples.sawPhysicalMovement |= physicalMotion > 1.0e-4;
            }
            KineticMountFrame frame = adapter.frameIdentity();
            double expected = frame == null ? Double.NaN : frame.bearingTargetFor(COMMAND_DEGREES);
            double error = Double.isFinite(expected)
                    ? Math.abs(KineticAngleMath.shortestDelta(physical, expected))
                    : Double.POSITIVE_INFINITY;
            if (error <= 0.75 && physicalMotion <= 0.1
                    && Math.abs(generatedRpm) <= 1.0e-5
                    && Math.abs(endpointRpm) <= 1.0e-5) {
                samples.stablePhysicalTicks++;
            } else {
                samples.stablePhysicalTicks = 0;
            }
            samples.lastPhysical = physical;
            samples.physicalSamples++;
        }
        if (samples.unwrappedSetpointTravel >= 180.0 || samples.unwrappedPhysicalTravel >= 180.0) {
            throw new GameTestAssertException(
                    "Swivel accumulated unsafe travel: setpoint="
                            + samples.unwrappedSetpointTravel + " physical="
                            + samples.unwrappedPhysicalTravel);
        }
    }

    private static void paceAsynchronousPhysics(Fixture fixture) {
        long gameTick = fixture.controller().getLevel().getGameTime();
        synchronized (SimulatedSwivelControllerGameTests.class) {
            if (lastPacedGameTick == gameTick) {
                return;
            }
            lastPacedGameTick = gameTick;
        }
        // GameTest otherwise sprints logical ticks much faster than Sable's
        // asynchronous physics pipeline. Pace once globally, not once per test.
        LockSupport.parkNanos(PHYSICS_TICK_NANOS);
    }

    private static void assertFinalPose(Fixture fixture, SampleState samples) {
        KineticMountAdapter adapter = resolveAdapter(fixture.controller());
        KineticMountFrame frame = adapter.frameIdentity();
        if (frame == null || samples.physicalSamples == 0) {
            throw new GameTestAssertException("Swivel physical feedback never became available");
        }
        double expected = frame.bearingTargetFor(COMMAND_DEGREES);
        double physical = adapter.getPhysicalAngleDegrees();
        double error = Math.abs(KineticAngleMath.shortestDelta(physical, expected));
        if (!Double.isFinite(error)) {
            throw new GameTestAssertException("Physical Swivel pose became unavailable");
        }

        if (!samples.sawGeneratedDrive || !samples.sawPoweredEndpoint
                || !samples.sawOrdinaryCogRatio || !samples.sawPhysicalMovement) {
            throw new GameTestAssertException("Swivel never demonstrated reference drive: generator="
                    + samples.sawGeneratedDrive + " endpoint=" + samples.sawPoweredEndpoint
                    + " cogRatio=" + samples.sawOrdinaryCogRatio
                    + " physicalMovement=" + samples.sawPhysicalMovement);
        }
        if (!samples.forcedNetworkRebuild) {
            throw new GameTestAssertException("Swivel network rebuild regression was not exercised");
        }
        if (!samples.sawObservedDisconnect) {
            throw new GameTestAssertException("Swivel rebuild never observed a disconnected topology");
        }
        if (!samples.sawPostRebuildDrive || !samples.sawPostRebuildMotion) {
            throw new GameTestAssertException("Swivel did not resume ordinary closed-loop motion after rebuild");
        }

        double setpointTravel = Math.abs(KineticAngleMath.shortestDelta(
                samples.initialSetpoint, expected));
        double physicalTravel = Math.abs(KineticAngleMath.shortestDelta(
                samples.initialPhysical, expected));
        double allowedSetpointTravel = setpointTravel
                + 2.0 * Math.max(samples.maxActiveStep, 0.5) + 0.5;
        double allowedPhysicalTravel = physicalTravel
                + 2.0 * Math.max(samples.maxActiveStep, 0.5) + 0.5;
        if (samples.unwrappedSetpointTravel > allowedSetpointTravel) {
            throw new GameTestAssertException("Swivel setpoint overshot: travel="
                    + samples.unwrappedSetpointTravel + "/" + allowedSetpointTravel);
        }
        int fullRevolutionTicks = samples.maxActiveStep <= 1.0e-6 ? SAMPLE_TICKS
                : (int) Math.ceil(360.0 / samples.maxActiveStep);
        if (error > 0.75) {
            throw new GameTestAssertException("Physical Swivel failed to converge: error=" + error
                    + " physical=" + physical + " setpoint=" + adapter.getTargetAngleDegrees()
                    + " expected=" + expected);
        }
        if (samples.unwrappedPhysicalTravel > allowedPhysicalTravel) {
            throw new GameTestAssertException("Physical Swivel overshot: travel="
                    + samples.unwrappedPhysicalTravel + "/" + allowedPhysicalTravel);
        }
        int requiredStableTicks = fullRevolutionTicks + 20;
        if (requiredStableTicks >= SAMPLE_TICKS) {
            throw new GameTestAssertException("GameTest observation window is too short: required="
                    + requiredStableTicks + " available=" + SAMPLE_TICKS);
        }
        if (samples.stablePhysicalTicks < requiredStableTicks) {
            throw new GameTestAssertException(
                    "Physical Swivel did not remain stable long enough: "
                            + samples.stablePhysicalTicks + "/" + requiredStableTicks);
        }
        if (!fixture.atTarget().getAsBoolean()) {
            throw new GameTestAssertException(
                    "Controller did not report the current physical pose ready");
        }

        KineticBlockEntity endpoint = fixture.bearing().getExtraKinetics();
        if (Math.abs(fixture.controller().getGeneratedSpeed()) > 1.0e-5
                || fixture.controller().hasSource() || fixture.controller().hasNetwork()
                || fixture.controller().isSource()
                || Math.abs(endpoint.getSpeed()) > 1.0e-5
                || Math.abs(endpoint.getTheoreticalSpeed()) > 1.0e-5
                || endpoint.hasSource() || endpoint.hasNetwork() || endpoint.isSource()
                || fixture.controller().sequenceContext != null
                || endpoint.sequenceContext != null || endpoint.isOverStressed()) {
            throw new GameTestAssertException(
                    "Generator or endpoint retained kinetic state after settling");
        }
        if (!adapter.isEndpointSafelyReleased()) {
            throw new GameTestAssertException(
                    "Endpoint retained source/network/speed/context/stress state after settling");
        }
    }

    private static void rebuildBearingFixture(GameTestHelper helper, TestSite site,
                                              Direction facing) {
        BlockState original = helper.getBlockState(BEARING_POS);
        if (!original.hasProperty(BlockStateProperties.FACING)) {
            throw new GameTestAssertException("Simulated Swivel fixture has no facing property");
        }
        BlockState replacement = original.getBlock().defaultBlockState()
                .setValue(BlockStateProperties.FACING, facing);

        ServerLevel level = helper.getLevel();
        if (!(level.getServer() instanceof GameTestServer)) {
            throw new GameTestAssertException(
                    "Absolute Sable fixture slots are restricted to the disposable GameTest server");
        }
        BlockPos bearingPos = site.bearingPos();
        ChunkPos chunk = new ChunkPos(bearingPos);
        level.setChunkForced(chunk.x, chunk.z, true);
        level.getChunk(chunk.x, chunk.z);
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    level.setBlockAndUpdate(bearingPos.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        level.setBlockAndUpdate(bearingPos.relative(facing), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(bearingPos, replacement);
        SwivelBearingBlockEntity bearing = (SwivelBearingBlockEntity) level.getBlockEntity(bearingPos);
        if (bearing == null) {
            throw new GameTestAssertException("Swivel bearing block entity was not created");
        }
        bearing.assembleNextTick = true; // Explicit test stand-in for the player's assembly action.
        bearing.setChanged();
    }

    private static Fixture installController(GameTestHelper helper, BlockPos bearingPos,
                                             Direction bearingFacing,
                                             Direction controllerFacing) {
        Direction.Axis axis = bearingFacing.getAxis();
        Direction controllerSide = axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        BlockPos controllerPos = bearingPos.relative(controllerSide);
        BlockState controllerState;
        if (axis == Direction.Axis.Y) {
            controllerState = ModBlocks.AUTO_YAW_CONTROLLER_BLOCK.getDefaultState()
                    .setValue(DirectionalKineticBlock.FACING, controllerFacing);
        } else {
            controllerState = ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.getDefaultState()
                    .setValue(HorizontalDirectionalBlock.FACING, controllerFacing);
        }

        Direction motorSide = bearingFacing.getOpposite();
        BlockPos motorPos = controllerPos.relative(motorSide);
        BlockState motorState = AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(DirectionalKineticBlock.FACING, motorSide.getOpposite());
        ServerLevel level = helper.getLevel();
        level.setBlockAndUpdate(controllerPos, controllerState);
        level.setBlockAndUpdate(motorPos, motorState);

        KineticBlockEntity controller = (KineticBlockEntity) level.getBlockEntity(controllerPos);
        KineticBlockEntity motor = (KineticBlockEntity) level.getBlockEntity(motorPos);
        SwivelBearingBlockEntity bearing = (SwivelBearingBlockEntity) level.getBlockEntity(bearingPos);
        if (controller == null || motor == null || bearing == null) {
            throw new GameTestAssertException("Kinetic fixture block entities were not created");
        }
        double startingSetpoint = bearing.getTargetAngleDegrees();
        if (controller instanceof AutoYawControllerBlockEntity yaw) {
            Vec3 worldAim = new Vec3(
                    -Math.sin(Math.toRadians(COMMAND_DEGREES)),
                    0.0,
                    Math.cos(Math.toRadians(COMMAND_DEGREES)));
            return new Fixture(controller, motor, bearing, startingSetpoint,
                    () -> {
                        if (!yaw.setRadarAimDirection(worldAim)) {
                            throw new GameTestAssertException(
                                    "Yaw controller rejected a valid world-space radar aim");
                        }
                    },
                    () -> yaw.atTargetYaw(true));
        }
        if (controller instanceof AutoPitchControllerBlockEntity pitch) {
            Vec3 worldAim = new Vec3(
                    Math.cos(Math.toRadians(COMMAND_DEGREES)),
                    Math.sin(Math.toRadians(COMMAND_DEGREES)),
                    0.0);
            return new Fixture(controller, motor, bearing, startingSetpoint,
                    () -> {
                        if (!pitch.setRadarAimDirection(worldAim)) {
                            throw new GameTestAssertException(
                                    "Pitch controller rejected a valid world-space radar aim");
                        }
                    },
                    () -> pitch.atTargetPitch(true));
        }
        throw new GameTestAssertException("Controller block entity was not created");
    }

    private static void cleanupFixture(GameTestHelper helper, TestSite site) {
        BlockPos bearingPos = site.bearingPos();
        ServerLevel level = helper.getLevel();
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    level.setBlockAndUpdate(bearingPos.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        ChunkPos chunk = new ChunkPos(bearingPos);
        if (!site.previouslyForced()) {
            level.setChunkForced(chunk.x, chunk.z, false);
        }
    }

    private static void runWithCleanup(GameTestHelper helper, TestSite site, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | Error failure) {
            cleanupFixture(helper, site);
            throw failure;
        }
    }

    private static void armController(Fixture fixture, SampleState samples) {
        KineticMountAdapter adapter = resolveAdapter(fixture.controller());
        double physical = adapter.getPhysicalAngleDegrees();
        if (!adapter.isAssembled() || !adapter.isLocked() || !Double.isFinite(physical)) {
            throw new GameTestAssertException("Swivel was not physically ready before commanding motion");
        }
        samples.initialSetpoint = adapter.getTargetAngleDegrees();
        samples.lastSetpoint = samples.initialSetpoint;
        samples.initialPhysical = physical;
        samples.lastPhysical = physical;
        fixture.command().run();
    }

    private static KineticMountAdapter resolveAdapter(KineticBlockEntity controller) {
        Direction.Axis axis = controller instanceof AutoYawControllerBlockEntity
                ? Direction.Axis.Y
                : controller.getBlockState().getValue(HorizontalDirectionalBlock.FACING).getAxis();
        KineticMountAdapterResolution resolution = SimulatedSwivelMountAdapter.resolve(controller, axis);
        if (!resolution.hasAdapter() || resolution.adapter() == null) {
            throw new GameTestAssertException("Adjacent Swivel adapter unavailable: " + resolution.reason());
        }
        return resolution.adapter();
    }

    private record Fixture(KineticBlockEntity controller, KineticBlockEntity motor,
                           SwivelBearingBlockEntity bearing, double startingSetpoint,
                           Runnable command, BooleanSupplier atTarget) {
    }

    private record TestSite(BlockPos bearingPos, boolean previouslyForced) {
    }

    private static final class SampleState {
        private double lastSetpoint = Double.NaN;
        private double initialSetpoint = Double.NaN;
        private double lastPhysical = Double.NaN;
        private double initialPhysical = Double.NaN;
        private double unwrappedSetpointTravel;
        private double unwrappedPhysicalTravel;
        private double maxActiveStep;
        private int physicalSamples;
        private int stablePhysicalTicks;
        private boolean sawGeneratedDrive;
        private boolean sawPoweredEndpoint;
        private int activeEndpointSamples;
        private boolean sawOrdinaryCogRatio;
        private boolean sawPhysicalMovement;
        private boolean forcedNetworkRebuild;
        private boolean sawObservedDisconnect;
        private boolean sawPostRebuildDrive;
        private boolean sawPostRebuildMotion;
        private double rebuildSetpoint = Double.NaN;
    }
}
