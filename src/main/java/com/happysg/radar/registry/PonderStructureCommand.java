package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

final class PonderStructureCommand {
    private static final int PERMISSION_LEVEL = 2;
    private static final int GRID_GAP = 4;
    private static final int PLAYER_CLEARANCE = 5;
    private static final int MAX_STRUCTURE_AXIS = 48;

    private PonderStructureCommand() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("radar_ponder")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .executes(context -> spawnPonderStructures(context.getSource())));
    }

    private static int spawnPonderStructures(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!player.canUseGameMasterBlocks()) {
            source.sendFailure(Component.literal(
                    "You must be in Creative mode and allowed to use structure blocks."));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        try {
            List<PonderTemplate> templates = loadTemplates(level);
            if (templates.isEmpty()) {
                source.sendFailure(Component.literal(
                        "No ponder NBTs were found under assets/create_radar/ponder."));
                return 0;
            }

            GridLayout layout = createLayout(templates, player);
            String validationFailure = validateLayout(level, layout);
            if (validationFailure != null) {
                source.sendFailure(Component.literal(validationFailure));
                return 0;
            }

            for (TemplatePlacement placement : layout.placements()) {
                placeTemplate(level, player, placement);
            }

            sendSuccess(source, layout);
            return templates.size();
        } catch (Exception exception) {
            CreateRadar.getLogger().error("Failed to spawn ponder editing structures", exception);
            String detail = exception.getMessage();
            source.sendFailure(Component.literal("Failed to spawn ponder structures"
                    + (detail == null || detail.isBlank() ? "." : ": " + detail)));
            return 0;
        }
    }

    private static List<PonderTemplate> loadTemplates(ServerLevel level) throws IOException {
        IModFileInfo modFileInfo = ModList.get().getModFileById(CreateRadar.MODID);
        if (modFileInfo == null) {
            throw new IOException("Create Radar's mod file could not be located");
        }

        Path ponderRoot = modFileInfo.getFile()
                .findResource("assets", CreateRadar.MODID, "ponder");
        if (!Files.isDirectory(ponderRoot)) {
            throw new IOException("Ponder asset directory does not exist: " + ponderRoot);
        }

        List<Path> nbtFiles;
        try (Stream<Path> files = Files.walk(ponderRoot)) {
            nbtFiles = files
                    .filter(Files::isRegularFile)
                    .filter(PonderStructureCommand::isNbtFile)
                    .sorted(Comparator.comparing(path -> resourcePath(ponderRoot, path)))
                    .toList();
        }

        List<PonderTemplate> templates = new ArrayList<>(nbtFiles.size());
        for (Path nbtFile : nbtFiles) {
            String resourcePath = resourcePath(ponderRoot, nbtFile);
            String structurePath = resourcePath.substring(0, resourcePath.length() - ".nbt".length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, structurePath);

            CompoundTag tag;
            try (InputStream input = Files.newInputStream(nbtFile)) {
                tag = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            }

            StructureTemplate template = level.getStructureManager().readStructure(tag);
            Vec3i size = template.getSize();
            validateTemplateSize(id, size);
            templates.add(new PonderTemplate(id, template, size));
        }

        templates.sort(Comparator.comparing(template -> template.id().toString()));
        return List.copyOf(templates);
    }

    private static boolean isNbtFile(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".nbt");
    }

    private static String resourcePath(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static void validateTemplateSize(ResourceLocation id, Vec3i size) throws IOException {
        if (size.getX() < 1 || size.getY() < 1 || size.getZ() < 1) {
            throw new IOException("Ponder template " + id + " has an empty size: " + formatSize(size));
        }
        if (size.getX() > MAX_STRUCTURE_AXIS
                || size.getY() > MAX_STRUCTURE_AXIS
                || size.getZ() > MAX_STRUCTURE_AXIS) {
            throw new IOException("Ponder template " + id + " is too large for a structure block: "
                    + formatSize(size));
        }
    }

    private static GridLayout createLayout(List<PonderTemplate> templates, ServerPlayer player) {
        int columnCount = (int) Math.ceil(Math.sqrt(templates.size()));
        int rowCount = (templates.size() + columnCount - 1) / columnCount;
        int[] columnWidths = new int[columnCount];
        int[] rowDepths = new int[rowCount];

        for (int index = 0; index < templates.size(); index++) {
            PonderTemplate template = templates.get(index);
            int column = index % columnCount;
            int row = index / columnCount;
            columnWidths[column] = Math.max(columnWidths[column], template.size().getX());
            rowDepths[row] = Math.max(rowDepths[row], template.size().getZ());
        }

        int totalWidth = sum(columnWidths) + GRID_GAP * (columnCount - 1);
        int totalDepth = sum(rowDepths) + GRID_GAP * (rowCount - 1);
        BlockPos gridOrigin = gridOrigin(player.blockPosition(), player.getDirection(), totalWidth, totalDepth);

        int[] columnOffsets = offsets(columnWidths);
        int[] rowOffsets = offsets(rowDepths);
        List<TemplatePlacement> placements = new ArrayList<>(templates.size());
        for (int index = 0; index < templates.size(); index++) {
            int column = index % columnCount;
            int row = index / columnCount;
            BlockPos origin = gridOrigin.offset(columnOffsets[column], 0, rowOffsets[row]);
            placements.add(new TemplatePlacement(templates.get(index), origin));
        }

        return new GridLayout(List.copyOf(placements), gridOrigin, totalWidth, totalDepth);
    }

    private static int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    private static int[] offsets(int[] sizes) {
        int[] offsets = new int[sizes.length];
        for (int index = 1; index < sizes.length; index++) {
            offsets[index] = offsets[index - 1] + sizes[index - 1] + GRID_GAP;
        }
        return offsets;
    }

    private static BlockPos gridOrigin(BlockPos playerPos, Direction facing, int width, int depth) {
        int x;
        int z;
        switch (facing) {
            case NORTH -> {
                x = playerPos.getX() - width / 2;
                z = playerPos.getZ() - PLAYER_CLEARANCE - depth + 1;
            }
            case SOUTH -> {
                x = playerPos.getX() - width / 2;
                z = playerPos.getZ() + PLAYER_CLEARANCE;
            }
            case WEST -> {
                x = playerPos.getX() - PLAYER_CLEARANCE - width + 1;
                z = playerPos.getZ() - depth / 2;
            }
            case EAST -> {
                x = playerPos.getX() + PLAYER_CLEARANCE;
                z = playerPos.getZ() - depth / 2;
            }
            default -> throw new IllegalArgumentException("Expected a horizontal player direction");
        }
        return new BlockPos(x, playerPos.getY(), z);
    }

    private static String validateLayout(ServerLevel level, GridLayout layout) {
        int structureBlockY = layout.origin().getY() - 1;
        if (structureBlockY < level.getMinBuildHeight()) {
            return "The structure blocks would be below the world's build limit.";
        }

        for (TemplatePlacement placement : layout.placements()) {
            BlockPos origin = placement.origin();
            Vec3i size = placement.template().size();
            BlockPos farCorner = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
            if (farCorner.getY() >= level.getMaxBuildHeight()) {
                return "Ponder template " + placement.template().id()
                        + " would exceed the world's build height.";
            }
            if (!level.getWorldBorder().isWithinBounds(origin)
                    || !level.getWorldBorder().isWithinBounds(farCorner)) {
                return "Ponder template " + placement.template().id()
                        + " would cross the world border.";
            }
        }
        return null;
    }

    private static void placeTemplate(ServerLevel level, ServerPlayer player,
                                      TemplatePlacement placement) throws IOException {
        PonderTemplate ponderTemplate = placement.template();
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(false);
        boolean placed = ponderTemplate.template().placeInWorld(
                level,
                placement.origin(),
                placement.origin(),
                settings,
                level.getRandom(),
                Block.UPDATE_CLIENTS);
        if (!placed) {
            throw new IOException("Ponder template " + ponderTemplate.id() + " could not be placed");
        }

        BlockPos structureBlockPos = placement.origin().below();
        BlockState structureBlockState = Blocks.STRUCTURE_BLOCK.defaultBlockState()
                .setValue(StructureBlock.MODE, StructureMode.SAVE);
        level.setBlock(structureBlockPos, structureBlockState, Block.UPDATE_ALL);

        BlockEntity blockEntity = level.getBlockEntity(structureBlockPos);
        if (!(blockEntity instanceof StructureBlockEntity structureBlock)) {
            throw new IOException("Structure block entity was not created at " + structureBlockPos.toShortString());
        }

        structureBlock.setStructureName(ponderTemplate.id());
        structureBlock.createdBy(player);
        structureBlock.setStructurePos(new BlockPos(0, 1, 0));
        structureBlock.setStructureSize(ponderTemplate.size());
        structureBlock.setMirror(Mirror.NONE);
        structureBlock.setRotation(Rotation.NONE);
        structureBlock.setMode(StructureMode.SAVE);
        structureBlock.setIgnoreEntities(false);
        structureBlock.setIntegrity(1.0F);
        structureBlock.setShowAir(false);
        structureBlock.setShowBoundingBox(true);
        structureBlock.setChanged();
        BlockState configuredState = level.getBlockState(structureBlockPos);
        level.sendBlockUpdated(structureBlockPos, configuredState, configuredState, Block.UPDATE_ALL);
    }

    private static void sendSuccess(CommandSourceStack source, GridLayout layout) {
        source.sendSuccess(() -> Component.literal("Spawned " + layout.placements().size()
                + " editable ponder structures in a " + layout.width() + "x" + layout.depth() + " grid.")
                .withStyle(ChatFormatting.GREEN), false);

        for (TemplatePlacement placement : layout.placements()) {
            source.sendSuccess(() -> Component.literal("- " + placement.template().id()
                    + " @ " + placement.origin().toShortString()), false);
        }

        Path outputDirectory = source.getServer()
                .getWorldPath(LevelResource.GENERATED_DIR)
                .resolve(CreateRadar.MODID)
                .resolve("structures")
                .toAbsolutePath()
                .normalize();
        String outputPath = outputDirectory.toString();
        Component path = Component.literal(outputPath)
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, outputPath))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to copy the generated structure folder"))));
        source.sendSuccess(() -> Component.literal("Structure Block saves: ").append(path), false);
        source.sendSuccess(() -> Component.literal(
                "Copy saved NBTs into src/main/resources/assets/create_radar/ponder."), false);
    }

    private static String formatSize(Vec3i size) {
        return size.getX() + "x" + size.getY() + "x" + size.getZ();
    }

    private record PonderTemplate(ResourceLocation id, StructureTemplate template, Vec3i size) {
    }

    private record TemplatePlacement(PonderTemplate template, BlockPos origin) {
    }

    private record GridLayout(List<TemplatePlacement> placements, BlockPos origin, int width, int depth) {
    }
}
