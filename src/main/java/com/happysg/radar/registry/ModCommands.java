package com.happysg.radar.registry;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.behavior.networks.WeaponNetworkData;
import com.happysg.radar.block.controller.id.IDManager;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.controller.yaw.AutoYawControllerBlockEntity;
import com.happysg.radar.config.RadarConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.internal.versions.neoforge.NeoForgeVersion;

import javax.annotation.Nullable;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class  ModCommands {
    static String DIR_NAME = "create_radar_debug";
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
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
                                .then(Commands.literal("gen_debug_file")
                                        .executes(ctx -> {
                                            genDebugFile(ctx.getSource());
                                            return 1;
                                        })
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

        // ─────────────────────────────────────────────
        // pitch controller → clamp to [-180, 180]
        // ─────────────────────────────────────────────
        if (be instanceof AutoPitchControllerBlockEntity pitch) {

            min = Mth.clamp(min, -90, 90);
            max = Mth.clamp(max, -90, 90);

            pitch.setMinAngleDeg(min);
            pitch.setMaxAngleDeg(max);

            be.setChanged();
            level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
            boolean clamped = (min != minIn || max != maxIn);
            if (clamped) {
                double finalMax = max;
                double finalMin1 = min;
                source.sendSuccess(
                        () -> Component.literal("Values clamped to pitch range [-90, 90] now [" + finalMin1 + ", " + finalMax + "]"),
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

            yaw.setMinAngleDeg(min);
            yaw.setMaxAngleDeg(max);

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
            Component line = Component.literal("• ")
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
        WeaponNetworkData data = WeaponNetworkData.get(level);

        if (data.getGroups().isEmpty()) {
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

        for (WeaponNetworkData.Group group : data.getGroups().values()) {
            WeaponNetworkData.MountKey key = group.key;

            source.sendSuccess(
                    () -> Component.literal(String.format(
                            "Mount: %s @ %s",
                            key.dim().location(),
                            posStr(key.mountPos())
                    )),
                    false
            );

            source.sendSuccess(() ->
                    Component.literal("  Yaw:    " + optPos(group.yawPos)), false);
            source.sendSuccess(() ->
                    Component.literal("  Pitch:  " + optPos(group.pitchPos)), false);
            source.sendSuccess(() ->
                    Component.literal("  Firing: " + optPos(group.firingPos)), false);

            if (group.dataLinks.isEmpty()) {
                source.sendSuccess(
                        () -> Component.literal("  DataLinks: <none>"),
                        false
                );
            } else {
                source.sendSuccess(
                        () -> Component.literal("  DataLinks:"),
                        false
                );
                for (BlockPos p : group.dataLinks) {
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
        var w = WeaponNetworkData.get(level).validateAllKnownPositions(level, true);

        source.sendSuccess(() -> Component.literal(
                "Network scrub complete. " +
                        "NetworkData: groupsRemoved=" + n.groupsRemoved() +
                        ", endpointsRemoved=" + n.endpointsRemoved() +
                        ", mountsRemoved=" + n.mountsRemoved() +
                        ", dataLinksRemoved=" + n.dataLinksRemoved() +
                        " | WeaponNetworkData: groupsRemoved=" + w.groupsRemoved() +
                        ", controllersCleared=" + w.controllersCleared() +
                        ", dataLinksRemoved=" + w.dataLinksRemoved()
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
                                Component.literal("   • ")
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

    private static void genDebugFile(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ServerLevel level = source.getLevel();
            Path dir = FMLPaths.GAMEDIR.get().resolve(DIR_NAME);
            String fileName = "debug_" + System.currentTimeMillis() + ".txt";
            Path file = dir.resolve(fileName);

            List<String> out = new ArrayList<>();

            // i wrote this so dumps are easy to spot and compare
            out.add("=== Create Radar Debug Dump ===");
            out.add("Generated: " + Instant.now());
            if(source.getPlayer()!= null){
                String sender =  source.getPlayer().toString();
                out.add("Generated by:" + sender);
            }else{
                out.add("Generated by: Sender unknown, likely server");
            }
            out.add("");

            addServerType(server, out);
            addEnvironment(out);
            addVersions(server, out);
            addPackHeuristics(out);
            addWorldInfo(level, out);
            addModList(out);

            out.add("");
            out.add("=== Radar Output ===");
            out.add("");
            out.add("=== radar list_ship_ids ===");
            out.addAll(runAndCapture(source, "radar debug list_ship_ids"));
            out.add("");

            out.add("=== radar dump_links ===");
            out.addAll(runAndCapture(source, "radar debug dump_links"));
            out.add("");

            out.add("=== radar list_active_filters ===");
            out.addAll(runAndCapture(source, "radar debug list_active_filters"));
            out.add("");



            out.add("=== radar debug weapon_endpoints ===");
            out.addAll(runAndCapture(source, "radar debug weapon_endpoints"));
            out.add("");



        try {
                Files.createDirectories(dir);
                Files.write(file, out, StandardCharsets.UTF_8);

                sendDumpSuccess(source, file, dir);
            } catch (Exception e) {
                source.sendFailure(Component.literal("Failed to write debug dump: " + e.getMessage()));
            }
        }

        private static void addServerType(MinecraftServer server, List<String> out) {
            // i wrote this to confirm if they're on a dedicated server jar or integrated singleplayer
            out.add("Dedicated server: " + server.isDedicatedServer());
            out.add("Online mode: " + server.usesAuthentication());
            out.add("Server port: " + server.getPort());
            out.add("");
        }

        private static void addEnvironment(List<String> out) {
            // i wrote this to capture the system runtime since java/os differences can cause one-user bugs
            out.add("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")");
            out.add("Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
            out.add("");
        }

        private static void addVersions(MinecraftServer server, List<String> out) {
            // i wrote this so i can verify minecraft/forge are exactly what i expect
            out.add("Minecraft: " + server.getServerVersion());
            out.add("Forge: " + NeoForgeVersion.getVersion());
            out.add("");
        }

        private static void addPackHeuristics(List<String> out) {
            try {
                Path gameDir = FMLPaths.GAMEDIR.get();

                // i wrote this to guess whether this instance came from a curseforge/modrinth pack export
                boolean hasCurseManifest = Files.exists(gameDir.resolve("manifest.json"));
                boolean hasModrinthIndex = Files.exists(gameDir.resolve("modrinth.index.json"));

                out.add("GameDir: " + gameDir.toAbsolutePath());
                out.add("manifest.json present: " + hasCurseManifest);
                out.add("modrinth.index.json present: " + hasModrinthIndex);

                String guessed = hasModrinthIndex ? "modrinth-pack-likely"
                        : hasCurseManifest ? "curseforge-pack-likely"
                        : "unknown";
                out.add("Pack source guess: " + guessed);
            } catch (Exception e) {
                out.add("Pack heuristics: ERROR " + e.getMessage());
            }
            out.add("");
        }

        private static void addWorldInfo(ServerLevel level, List<String> out) {
            try {
                Path root = level.getServer().getWorldPath(LevelResource.ROOT);
                Path levelDat = root.resolve("level.dat");

                // i wrote this so i can approximate world creation time (best effort)
                if (Files.exists(levelDat)) {
                    BasicFileAttributes attrs = Files.readAttributes(levelDat, BasicFileAttributes.class);
                    out.add("World folder: " + root.toAbsolutePath());
                    out.add("level.dat created: " + attrs.creationTime());
                    out.add("level.dat modified: " + attrs.lastModifiedTime());
                } else {
                    out.add("World folder: " + root.toAbsolutePath());
                    out.add("level.dat: missing");
                }

                out.add("Dimension: " + level.dimension().location());
                out.add("GameTime: " + level.getGameTime());
            } catch (Exception e) {
                out.add("World info: ERROR " + e.getMessage());
            }
            out.add("");
        }

        private static void addModList(List<String> out) {
            // i wrote this so i can see exactly what mod versions they actually have loaded
            out.add("=== Loaded Mods ===");
            ModList.get().getMods().forEach(mod ->
                    out.add(mod.getModId() + " " + mod.getVersion())
            );
            out.add("");
        }

        private static void sendDumpSuccess(CommandSourceStack source, Path file, Path dir) {
            Component fileLine = Component.literal("Create Radar debug generated: ")
                    .append(Component.literal(file.getFileName().toString())
                            .withStyle(style -> style
                                    .withUnderlined(true)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, file.toAbsolutePath().toString()))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy file path")))
                            ));


            Component copyFolder = Component.literal("[Copy folder path]")
                    .withStyle(style -> style
                            .withUnderlined(true)
                            .withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, dir.toAbsolutePath().toString()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy the folder path")))
                    );

            source.sendSuccess(() -> fileLine, false);
            source.sendSuccess(() -> Component.literal("").append(copyFolder), false);
        }

        // --- OPTIONAL: execute other commands and capture their chat output into the file ---

        private static List<String> runAndCapture(CommandSourceStack original, String command) {
            List<String> captured = new ArrayList<>();

            // i wrote this to capture anything the command tries to print to chat
            CommandSource capturingSource = new CommandSource() {
                @Override
                public void sendSystemMessage(Component component) {
                    captured.add(component.getString());
                }

                @Override
                public boolean acceptsSuccess() {
                    return true;
                }

                @Override
                public boolean acceptsFailure() {
                    return true;
                }

                @Override
                public boolean shouldInformAdmins() {
                    return false;
                }
            };

            // i wrote this so the executed command still has the same context (level, pos, entity, permissions)
            CommandSourceStack stack = new CommandSourceStack(
                    capturingSource,
                    original.getPosition(),
                    original.getRotation(),
                    original.getLevel(),
                    2,
                    original.getTextName(),
                    original.getDisplayName(),
                    original.getServer(),
                    original.getEntity()
            );

            try {
                original.getServer().getCommands().getDispatcher().execute(command, stack);
            } catch (Exception e) {
                captured.add("ERROR executing '" + command + "': " + e.getMessage());
            }

            return captured;

    }

}