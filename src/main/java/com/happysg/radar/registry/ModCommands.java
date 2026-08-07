package com.happysg.radar.registry;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.WeaponFiringControl;
import com.happysg.radar.block.behavior.networks.WeaponNetworkRuntime;
import com.happysg.radar.block.controller.firing.FireControllerBlockEntity;
import com.happysg.radar.block.controller.id.IDManager;
import com.happysg.radar.block.controller.kinetic.DebugSwivelFollow;
import com.happysg.radar.block.controller.kinetic.DebugSwivelSweep;
import com.happysg.radar.block.controller.limits.ControllerMovementLimits;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.compat.cbc.CannonMountContext;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.debug.DiagnosticReportCoordinator;
import com.happysg.radar.debug.InspectorSessionManager;
import com.happysg.radar.debug.ConflictTraceRecorder;
import com.happysg.radar.debug.ConflictTraceSessionManager;
import com.happysg.radar.targeting.Trajectory;
import com.happysg.radar.targeting.TargetingSolverSelfTest;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;

public class  ModCommands {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        PonderStructureCommand.register(dispatcher);
        dispatcher.register(
                Commands.literal("radar")
                        .then(Commands.literal("debug")
                            .requires(src -> src.hasPermission(2))
                                .then(Commands.literal("toggle_beam")
                                        .executes(ctx -> toggleDebugBeams(ctx.getSource()))
                                )
                        )
        );
        dispatcher.register(
                Commands.literal("radar")
                        .then(Commands.literal("debug")
                        .requires(src -> src.hasPermission(2)) // OP-only; change if desired
                        .then(Commands.literal("dump_links")
                                .executes(ctx -> dumpLinks(ctx.getSource()))
                        ))
        );
        dispatcher.register(
                Commands.literal("radar")
                        .then(Commands.literal("debug")
                        .requires(cs -> cs.hasPermission(2))
                                .then(Commands.literal("list_active_filters")
                                        .executes(ctx -> dumpNetworkFilters(ctx.getSource()))
                                )
                        )
        );
        dispatcher.register(
                Commands.literal("radar")
                        .then(Commands.literal("debug")
                            .requires(s -> s.hasPermission(2))
                            .then(Commands.literal("validate_networks")
                                    .executes(ctx -> validateNetworks(ctx.getSource()
                                            )
                                    )
                            )
                        )
        );
        dispatcher.register(
                Commands.literal("radar")
                        .then(Commands.literal("debug")
                                .then(Commands.literal("weapon_endpoints")
                                        .requires(cs -> cs.hasPermission(2))
                                        .executes(ctx -> dumpWeaponEndpoints(ctx.getSource()))
                                )
                        )
        );
        dispatcher.register(
                Commands.literal("radar")
                        .then(Commands.literal("debug")
                        .then(Commands.literal("list_ship_ids")
                                .requires(src -> src.hasPermission(2)) // OP only
                                .executes(ctx -> listShipIds(ctx.getSource()))
                        ))
        );
        dispatcher.register(
                Commands.literal("radar")
                        .then(Commands.literal("debug")
                            .requires(src -> src.hasPermission(2))
                                .then(Commands.literal("cannon_solver")
                                        .executes(ctx -> cannonSolverDebug(ctx.getSource(), false, 160))
                                        .then(Commands.literal("arc")
                                                .executes(ctx -> cannonSolverDebug(ctx.getSource(), true, 160))
                                                .then(Commands.argument("ticks", IntegerArgumentType.integer(20, 400))
                                                        .executes(ctx -> cannonSolverDebug(ctx.getSource(), true, IntegerArgumentType.getInteger(ctx, "ticks")))
                                                )
                                        )
                                )
                        )
        );
        dispatcher.register(
                Commands.literal("radar")
                        .then(Commands.literal("debug")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.literal("targeting_selftest")
                                        .executes(ctx -> targetingSelfTest(ctx.getSource()))
                                )
                        )
        );
        dispatcher.register(
                Commands.literal("radar")
                        .then(Commands.literal("debug")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.literal("swivel_sweep")
                                        .then(Commands.argument("degrees", FloatArgumentType.floatArg(-180.0F, 180.0F))
                                                .executes(ctx -> debugSwivelSweep(
                                                        ctx.getSource(),
                                                        FloatArgumentType.getFloat(ctx, "degrees")))
                                        )
                                )
                        )
        );
        dispatcher.register(
                Commands.literal("radar")
                        .then(Commands.literal("debug")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.literal("swivel_follow")
                                        .executes(ctx -> debugSwivelFollow(ctx.getSource()))
                                )
                        )
        );
        dispatcher.register(
                Commands.literal("radar")
                        .then(Commands.literal("debug")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.literal("gen_debug_file")
                                        .executes(ctx ->
                                                DiagnosticReportCoordinator
                                                        .startOverall(
                                                                ctx.getSource()))
                                )
                                .then(Commands.literal("report")
                                        .executes(ctx ->
                                                DiagnosticReportCoordinator
                                                        .startOverall(
                                                                ctx.getSource()))
                                )
                                .then(Commands.literal("inspect")
                                        .executes(ctx -> toggleInspector(
                                                ctx.getSource(), null))
                                        .then(Commands.literal("on")
                                                .executes(ctx ->
                                                        toggleInspector(
                                                                ctx.getSource(),
                                                                true)))
                                        .then(Commands.literal("off")
                                                .executes(ctx ->
                                                        toggleInspector(
                                                                ctx.getSource(),
                                                                false)))
                                        .then(Commands.literal("dump")
                                                .executes(ctx ->
                                                        DiagnosticReportCoordinator
                                                                .dumpInspectedBlock(
                                                                        ctx.getSource())))
                                )
                                .then(Commands.literal("conflicts")
                                        .executes(ctx -> conflictTraceStatus(
                                                ctx.getSource()))
                                        .then(Commands.literal("on")
                                                .executes(ctx ->
                                                        setConflictTrace(
                                                                ctx.getSource(),
                                                                true)))
                                        .then(Commands.literal("off")
                                                .executes(ctx ->
                                                        setConflictTrace(
                                                                ctx.getSource(),
                                                                false)))
                                )
                        )
        );
        dispatcher.register(
                Commands.literal("radar")
                        .then(Commands.literal("controller_angle")
                                .then(Commands.argument("min", FloatArgumentType.floatArg(-180,360))
                                        .then(Commands.argument("max", FloatArgumentType.floatArg(-180,360))
                                                .executes(ctx -> {

                                                    float min = FloatArgumentType.getFloat(ctx, "min");
                                                    float max = FloatArgumentType.getFloat(ctx, "max");

                                                    setControllerAngle(ctx.getSource(), min, max);
                                                    return 1;
                                                })
                                        )
                                )
                        )
        );


    }

    private static int toggleInspector(CommandSourceStack source,
                                       @Nullable Boolean requestedState) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal(
                    "A player is required to use inspection mode."));
            return 0;
        }
        boolean enabled = requestedState == null
                ? InspectorSessionManager.toggle(player)
                : InspectorSessionManager.setEnabled(player, requestedState);
        source.sendSuccess(() -> Component.literal(
                "Create Radar inspection mode: "
                        + (enabled ? "ON" : "OFF"))
                .withStyle(enabled ? ChatFormatting.GREEN
                        : ChatFormatting.GRAY), false);
        return 1;
    }

    private static int setConflictTrace(CommandSourceStack source,
                                        boolean enabled) {
        ConflictTraceRecorder.State state = enabled
                ? ConflictTraceSessionManager.enable(source.getServer(),
                source.getPlayer())
                : ConflictTraceSessionManager.disable(source.getServer());
        source.sendSuccess(() -> Component.literal(
                "Create Radar conflict trace: "
                        + (state.enabled() ? "ON" : "OFF")
                        + " | session=" + state.sessionId()
                        + " | retained=" + state.retainedEvents()
                        + " | dropped=" + state.droppedEvents())
                .withStyle(state.enabled() ? ChatFormatting.GREEN
                        : ChatFormatting.GRAY), false);
        return 1;
    }

    private static int conflictTraceStatus(CommandSourceStack source) {
        ConflictTraceRecorder.State state = ConflictTraceRecorder.state();
        source.sendSuccess(() -> Component.literal(
                "Create Radar conflict trace: "
                        + (state.enabled() ? "ON" : "OFF")
                        + " | session=" + state.sessionId()
                        + " | retained=" + state.retainedEvents()
                        + " | queued=" + state.queuedWrites()
                        + " | dropped=" + state.droppedEvents())
                .withStyle(state.enabled() ? ChatFormatting.GREEN
                        : ChatFormatting.GRAY), false);
        return 1;
    }

    private static int targetingSelfTest(CommandSourceStack source) {
        List<TargetingSolverSelfTest.Result> results = TargetingSolverSelfTest.runBasicInterceptChecks();
        int failed = 0;
        for (TargetingSolverSelfTest.Result result : results) {
            if (!result.passed()) {
                failed++;
            }

            ChatFormatting color = result.passed() ? ChatFormatting.GREEN : ChatFormatting.RED;
            source.sendSuccess(
                    () -> Component.literal((result.passed() ? "PASS " : "FAIL ") + result.name() + ": " + result.detail())
                            .withStyle(color),
                    false
            );
        }

        int total = results.size();
        int passed = total - failed;
        ChatFormatting summaryColor = failed == 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
        source.sendSuccess(
                () -> Component.literal("Targeting self-test: " + passed + "/" + total + " passed")
                        .withStyle(summaryColor),
                true
        );
        return failed == 0 ? 1 : 0;
    }

    private static int debugSwivelSweep(CommandSourceStack source, float degrees) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockHitResult hit = raycastBlock(player, 12.0);
        if (hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Look at a pitch or yaw controller."));
            return 0;
        }

        BlockPos controllerPos = hit.getBlockPos();
        BlockEntity blockEntity = level.getBlockEntity(controllerPos);
        DebugSwivelSweep.StartResult result;
        String controllerType;
        if (blockEntity instanceof AutoPitchControllerBlockEntity pitch) {
            result = pitch.startDebugSwivelSweep(degrees);
            controllerType = "pitch";
        } else if (blockEntity instanceof AutoYawControllerBlockEntity yaw) {
            result = yaw.startDebugSwivelSweep(degrees);
            controllerType = "yaw";
        } else {
            source.sendFailure(Component.literal("That block is not a pitch or yaw controller."));
            return 0;
        }

        if (!result.started() || result.bearingDirection() == null) {
            source.sendFailure(Component.literal("Could not start Swivel sweep: " + result.reason()));
            return 0;
        }

        BlockPos bearingPos = controllerPos.relative(result.bearingDirection());
        source.sendSuccess(
                () -> Component.literal("Started " + controllerType + " Swivel sweep by " + degrees
                        + " degrees at " + bearingPos + "; it will return to 0 after settling."),
                false
        );
        LOGGER.warn("Started debug Swivel sweep controller={} type={} bearing={} degrees={}",
                controllerPos, controllerType, bearingPos, degrees);
        return 1;
    }

    private static int debugSwivelFollow(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockHitResult hit = raycastBlock(player, 12.0);
        if (hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Look at a pitch or yaw controller."));
            return 0;
        }

        BlockPos controllerPos = hit.getBlockPos();
        BlockEntity blockEntity = level.getBlockEntity(controllerPos);
        DebugSwivelFollow.ToggleResult result;
        String controllerType;
        if (blockEntity instanceof AutoPitchControllerBlockEntity pitch) {
            result = pitch.toggleDebugSwivelFollow(player);
            controllerType = "pitch";
        } else if (blockEntity instanceof AutoYawControllerBlockEntity yaw) {
            result = yaw.toggleDebugSwivelFollow(player);
            controllerType = "yaw";
        } else {
            source.sendFailure(Component.literal("That block is not a pitch or yaw controller."));
            return 0;
        }

        if (result.state() == DebugSwivelFollow.ToggleState.FAILED) {
            source.sendFailure(Component.literal("Could not toggle Swivel player follow: "
                    + result.reason()));
            return 0;
        }
        if (result.state() == DebugSwivelFollow.ToggleState.STOPPED) {
            source.sendSuccess(
                    () -> Component.literal("Stopped " + controllerType
                            + " Swivel player follow and restored its previous target."),
                    false
            );
            LOGGER.warn("Stopped debug Swivel player follow controller={} type={} player={}",
                    controllerPos, controllerType, player.getGameProfile().getName());
            return 1;
        }

        BlockPos bearingPos = controllerPos.relative(result.bearingDirection());
        source.sendSuccess(
                () -> Component.literal("Started " + controllerType
                        + " Swivel player follow at " + bearingPos
                        + ". Run the command again while looking at this controller to stop."),
                false
        );
        LOGGER.warn("Started debug Swivel player follow controller={} type={} bearing={} player={}",
                controllerPos, controllerType, bearingPos, player.getGameProfile().getName());
        return 1;
    }

    private static int cannonSolverDebug(CommandSourceStack source, boolean drawArc, int arcTicks) throws CommandSyntaxException {
        SolverDebugContext context = resolveSolverDebugContext(source);
        if (context == null || context.pitch == null) {
            source.sendFailure(Component.literal("Look at a linked cannon mount, pitch controller, yaw controller, or firing controller."));
            return 0;
        }

        context.pitch.getFiringControl();
        WeaponFiringControl control = context.pitch.firingControl;
        if (control == null) {
            source.sendFailure(Component.literal("No WeaponFiringControl is available for that cannon group."));
            return 0;
        }

        WeaponFiringControl.SolverDebugReport report = control.buildSolverDebugReport(context.level, arcTicks);
        for (String line : report.lines()) {
            source.sendSuccess(() -> Component.literal(line), false);
        }

        if (drawArc) {
            if (report.trajectory() == null || report.trajectory().samples().isEmpty()) {
                source.sendFailure(Component.literal("No trajectory arc could be generated."));
            } else {
                drawTrajectoryArc(context.level, report.trajectory().samples());
                source.sendSuccess(() -> Component.literal("Drew cannon solver arc with " + report.trajectory().samples().size() + " samples."), false);
            }
        }

        LOGGER.warn("Ran cannon solver debug command for mount={} pitch={} arc={} ticks={}",
                context.mount == null ? null : context.mount.getBlockPos(),
                context.pitch.getBlockPos(),
                drawArc,
                arcTicks);
        return 1;
    }

    @Nullable
    private static SolverDebugContext resolveSolverDebugContext(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockHitResult hit = raycastBlock(player, 12.0);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos pos = hit.getBlockPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return null;
        }

        CannonMountContext mount = CannonMountContext.of(be);
        AutoPitchControllerBlockEntity pitch = be instanceof AutoPitchControllerBlockEntity pitchController ? pitchController : null;
        AutoYawControllerBlockEntity yaw = be instanceof AutoYawControllerBlockEntity yawController ? yawController : null;

        WeaponNetworkRuntime runtime = WeaponNetworkRuntime.get(level);
        WeaponNetworkRuntime.WeaponGroupView group = null;
        if (mount != null) {
            group = runtime.getWeaponGroupView(mount.getBlockPos());
        } else if (pitch != null || yaw != null || be instanceof FireControllerBlockEntity) {
            group = runtime.getWeaponGroupViewFromEndpoint(pos);
        }

        if (group != null) {
            if (mount == null) {
                mount = CannonMountContext.of(level.getBlockEntity(group.mountPos()));
            }
            if (pitch == null && group.pitchPos() != null && level.getBlockEntity(group.pitchPos()) instanceof AutoPitchControllerBlockEntity linkedPitch) {
                pitch = linkedPitch;
            }
            if (yaw == null && group.yawPos() != null && level.getBlockEntity(group.yawPos()) instanceof AutoYawControllerBlockEntity linkedYaw) {
                yaw = linkedYaw;
            }
        }

        return new SolverDebugContext(level, mount, pitch, yaw);
    }

    private static void drawTrajectoryArc(ServerLevel level, List<Trajectory.Sample> samples) {
        int step = Math.max(1, samples.size() / 120);
        int last = Math.max(1, samples.size() - 1);
        for (int i = 0; i < samples.size(); i += step) {
            Trajectory.Sample sample = samples.get(i);
            float t = (float) i / (float) last;
            float r = 0.15F + 0.85F * t;
            float g = 1.0F - 0.75F * t;
            float b = 0.10F;
            Vec3 pos = sample.position();
            level.sendParticles(new DustParticleOptions(new Vector3f(r, g, b), 1.0F), pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }
    }

    private record SolverDebugContext(ServerLevel level,
                                      @Nullable CannonMountContext mount,
                                      @Nullable AutoPitchControllerBlockEntity pitch,
                                      @Nullable AutoYawControllerBlockEntity yaw) {
    }

    private static void setControllerAngle(CommandSourceStack source, float minIn, float maxIn) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();

        BlockHitResult hit = raycastBlock(player, 6.0);
        if (hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Not looking at a block."));
            return;
        }

        BlockPos pos = hit.getBlockPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            source.sendFailure(Component.literal("That block has no block entity."));
            return;
        }

        double min = Math.min(minIn, maxIn);
        double max = Math.max(minIn, maxIn);

        // pitch controller -> clamp to [-180, 180]
        if (be instanceof AutoPitchControllerBlockEntity pitch) {

            min = Mth.clamp(min, -90, 90);
            max = Mth.clamp(max, -90, 90);

            pitch.setMovementLimits(min, max);
            ControllerMovementLimits applied = pitch.getMovementLimits();
            min = applied.minDegrees();
            max = applied.maxDegrees();

            be.setChanged();
            level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
            boolean clamped = (min != minIn || max != maxIn);
            if (clamped) {
                double finalMax = max;
                double finalMin1 = min;
                source.sendSuccess(
                        () -> Component.literal("Values clamped to the mounted cannon range; now [" + finalMin1 + ", " + finalMax + "]"),
                        false
                );
            }else {

                double finalMax1 = max;
                double finalMin = min;
                source.sendSuccess(
                        () -> Component.literal("Set PITCH limits to [" + finalMin + ", " + finalMax1 + "]"),
                        false
                );
                return;
            }
        } else if (be instanceof AutoYawControllerBlockEntity yaw) {

            min = Mth.clamp(min, -180, 180);
            max = Mth.clamp(max, -180, 180);

            yaw.setMovementLimits(min, max);

            be.setChanged();
            level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);

            double finalMin2 = min;
            double finalMax2 = max;
            boolean clamped = (min != minIn || max != maxIn);
            if (clamped) {
                source.sendSuccess(
                        () -> Component.literal("Values clamped to yaw range [-180, 180] now [" + finalMin2 + ", " + finalMax2 + "]"),
                        false
                );
            }else source.sendSuccess(

                    () -> Component.literal("Set YAW limits to [" + finalMin2 + ", " + finalMax2 + "]"),
                    false
            );

        }else{
            source.sendFailure(Component.literal("That isn't a pitch or yaw controller."));
        }
    }

    private static BlockHitResult raycastBlock(ServerPlayer player, double distance) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(distance));

        return player.level().clip(new ClipContext(
                eye,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));
    }

    private static int toggleDebugBeams(CommandSourceStack source) {
        RadarConfig.DEBUG_BEAMS = !RadarConfig.DEBUG_BEAMS;

        source.sendSuccess(
                () -> Component.literal(
                        "Radar debug beams: " + (RadarConfig.DEBUG_BEAMS ? "ON" : "OFF")
                ),
                true
        );

        return 1;
    }

    private static int listShipIds(CommandSourceStack source) {
        if (IDManager.ID_RECORDS.isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("No VS2 ship ID records found.")
                            .withStyle(ChatFormatting.GRAY),
                    false
            );
            return 1;
        }

        source.sendSuccess(
                () -> Component.literal("VS2 Ship IFF Records:")
                        .withStyle(ChatFormatting.GOLD),
                false
        );

        IDManager.ID_RECORDS.forEach((slug, record) -> {
            Component line = Component.literal("- ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(String.valueOf(slug)).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" | name="))
                    .append(Component.literal(record.name()).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" | secret="))
                    .append(Component.literal(record.secretID()).withStyle(ChatFormatting.RED));
            source.sendSuccess(() -> line, false);
        });
        return IDManager.ID_RECORDS.size();
    }


    private static int dumpLinks(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        var groups = WeaponNetworkRuntime.get(level).getGroups();

        if (groups.isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("No mount link groups found."),
                    false
            );
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("--- Radar Mount Links ---"),
                false
        );

        for (WeaponNetworkRuntime.WeaponGroupView group : groups) {
            source.sendSuccess(
                    () -> Component.literal(String.format(
                            "Mount: %s @ %s",
                            level.dimension().location(),
                            posStr(group.mountPos())
                    )),
                    false
            );

            source.sendSuccess(() ->
                    Component.literal("  Yaw:    " + optPos(group.yawPos())), false);
            source.sendSuccess(() ->
                    Component.literal("  Pitch:  " + optPos(group.pitchPos())), false);
            source.sendSuccess(() ->
                    Component.literal("  Firing: " + optPos(group.firingPos())), false);

            if (group.dataLinks().isEmpty()) {
                source.sendSuccess(
                        () -> Component.literal("  DataLinks: <none>"),
                        false
                );
            } else {
                source.sendSuccess(
                        () -> Component.literal("  DataLinks:"),
                        false
                );
                for (BlockPos p : group.dataLinks()) {
                    source.sendSuccess(
                            () -> Component.literal("    - " + posStr(p)),
                            false
                    );
                }
            }
        }

        return 1;
    }
    private static int dumpNetworkFilters(CommandSourceStack source) {
        ServerLevel level = source.getLevel();

        NetworkData data = NetworkData.get(level);

        if (data.getGroups().isEmpty()) {
            source.sendFailure(Component.literal(
                    "No network groups found."
            ));
            return 0;
        }

        source.sendSystemMessage(Component.literal(
                "=== Radar Network Filters ==="
        ).withStyle(ChatFormatting.GOLD));

        data.getGroups().forEach((key, group) -> {



            source.sendSystemMessage(Component.literal(
                    " Monitors: " + group.monitorEndpoints
            ));

            source.sendSystemMessage(Component.literal(
                    " Radar: " + group.radarPos + " (" + group.radarKind + ")"
            ));

            source.sendSystemMessage(Component.literal(
                    " Weapon Endpoints: " + group.weaponEndpoints.size()
            ));

            source.sendSystemMessage(Component.literal(
                    " Targeting Filter:"
            ).withStyle(ChatFormatting.AQUA));

            source.sendSystemMessage(Component.literal(
                    group.targetingTag.toString()
            ).withStyle(ChatFormatting.GRAY));

            source.sendSystemMessage(Component.literal(
                    " Identification Filter:"
            ).withStyle(ChatFormatting.AQUA));

            source.sendSystemMessage(Component.literal(
                    group.identificationTag.toString()
            ).withStyle(ChatFormatting.GRAY));

            source.sendSystemMessage(Component.literal(
                    " Detection Filter:"
            ).withStyle(ChatFormatting.AQUA));

            source.sendSystemMessage(Component.literal(
                    group.detectionTag.toString()
            ).withStyle(ChatFormatting.GRAY));
        });

        return 1;
    }


    private static String posStr(BlockPos pos) {
        return "[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
    }

    private static String optPos(@Nullable BlockPos pos) {
        return pos == null ? "<none>" : posStr(pos);
    }

    private static int validateNetworks(CommandSourceStack source){

        ServerLevel level = source.getLevel();

        var n = NetworkData.get(level).validateAllKnownPositions(level, true);
        var weaponGroups = WeaponNetworkRuntime.get(level).getGroups().size();

        source.sendSuccess(() -> Component.literal(
                "Network scrub complete. " +
                        "NetworkData: groupsRemoved=" + n.groupsRemoved() +
                        ", endpointsRemoved=" + n.endpointsRemoved() +
                        ", mountsRemoved=" + n.mountsRemoved() +
                        ", dataLinksRemoved=" + n.dataLinksRemoved() +
                        " | WeaponNetworkRuntime: loadedGroups=" + weaponGroups
        ), true);

        return 1;
    }
    private static int dumpWeaponEndpoints(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        NetworkData data = NetworkData.get(level);

        source.sendSuccess(() ->
                        Component.literal("=== Radar Weapon Endpoints Dump ===")
                                .withStyle(ChatFormatting.GOLD),
                false
        );

        if (data.getGroupsByFiltererView().isEmpty()) {
            source.sendSuccess(() ->
                            Component.literal("No filter groups found.")
                                    .withStyle(ChatFormatting.GRAY),
                    false
            );
            return 1;
        }

        for (NetworkData.Group group : data.getGroupsByFiltererView().values()) {
            BlockPos filtererPos = group.key.filtererPos();
            ResourceKey<Level> dim = group.key.dim();

            source.sendSuccess(() ->
                            Component.literal("")
                                    .append(Component.literal("[FilterGroup] ").withStyle(ChatFormatting.YELLOW))
                                    .append(Component.literal("Filterer @ "))
                                    .append(Component.literal(dim.location().toString())
                                            .withStyle(ChatFormatting.AQUA))
                                    .append(Component.literal(" "))
                                    .append(Component.literal(filtererPos.toShortString())
                                            .withStyle(ChatFormatting.GREEN)),
                    false
            );
            if (!group.monitorEndpoints.isEmpty()) {
                source.sendSuccess(() ->
                                Component.literal("- Monitors (" + group.monitorEndpoints.size() + "): " + group.monitorEndpoints)
                                        .withStyle(ChatFormatting.GRAY),
                        false
                );
            }

            if (group.weaponEndpoints.isEmpty()) {
                source.sendSuccess(() ->
                                Component.literal(" - Weapon endpoints: <none>")
                                        .withStyle(ChatFormatting.DARK_GRAY),
                        false
                );
                continue;
            }

            source.sendSuccess(() ->
                            Component.literal(" - Weapon endpoints (" + group.weaponEndpoints.size() + "):")
                                    .withStyle(ChatFormatting.GRAY),
                    false
            );

            for (BlockPos ep : group.weaponEndpoints) {
                source.sendSuccess(() ->
                                Component.literal("   - ")
                                        .append(Component.literal(ep.toShortString())
                                                .withStyle(ChatFormatting.WHITE)),
                        false
                );
            }
        }

        source.sendSuccess(() ->
                        Component.literal("=== End Dump ===")
                                .withStyle(ChatFormatting.GOLD),
                false
        );

        return 1;
    }

}
