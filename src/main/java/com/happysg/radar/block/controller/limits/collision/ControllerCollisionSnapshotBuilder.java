package com.happysg.radar.block.controller.limits.collision;

import com.happysg.radar.block.controller.kinetic.CannonAxis;
import com.happysg.radar.block.controller.limits.ControllerLimitAccess;
import com.happysg.radar.block.controller.limits.ControllerMovementLimits;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.CBCMuzzleUtil;
import com.happysg.radar.compat.cbc.CannonMountContext;
import com.happysg.radar.compat.vs2.SableUtils;
import com.happysg.radar.config.RadarConfig;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlock;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Builds one bounded, static collision snapshot for an open controller GUI. */
public final class ControllerCollisionSnapshotBuilder {
    private static final double EPSILON = 1.0e-8;

    private ControllerCollisionSnapshotBuilder() {
    }

    public static ControllerCollisionSnapshot build(
            ServerPlayer player, BlockEntity blockEntity,
            Direction capturedPlayerDirection
    ) {
        if (!(blockEntity instanceof ControllerLimitAccess controller)) {
            return ControllerCollisionSnapshot.error(
                    ControllerCollisionSnapshot.Status.NO_CONTROLLER,
                    CannonAxis.PITCH);
        }

        CannonAxis axis = controller.getControlledAxis();
        if (!controller.hasAssembledControlledMount()) {
            return ControllerCollisionSnapshot.error(
                    ControllerCollisionSnapshot.Status.NO_MOUNT, axis);
        }
        ControllerMovementLimits supportedLimits =
                controller.getSupportedMovementLimits();
        ControllerMovementLimits movementLimits = controller
                .getMovementLimits().constrainedTo(supportedLimits);
        List<CannonMountContext> mounts = collisionMounts(blockEntity);
        List<BlockPos> mountPositions = collisionMountPositions(blockEntity);
        Set<UUID> controlledSublevels = collisionSublevels(blockEntity);
        Set<BlockEntity> controlledMounts = new HashSet<>();
        for (CannonMountContext mount : mounts) {
            controlledMounts.add(mount.blockEntity());
        }
        ServerLevel rootLevel = player.serverLevel();

        Vec3 resolvedOrigin = resolveOrigin(blockEntity, mounts,
                mountPositions);
        Vec3 requestedOrigin = blockEntity
                instanceof ControllerCollisionSource source
                ? source.resolveCollisionViewOrigin() : null;
        boolean hasRequestedOrigin = finite(requestedOrigin);
        Vec3 origin = hasRequestedOrigin ? requestedOrigin : resolvedOrigin;
        Vec3 dialCenterWorld = hasRequestedOrigin ? origin
                : resolveDialCenter(axis, blockEntity, mounts,
                mountPositions, origin);
        BlockEntity frameReference = !hasRequestedOrigin && !mounts.isEmpty()
                ? mounts.getFirst().blockEntity() : blockEntity;
        Vec3 parentUp = worldDirection(frameReference, new Vec3(0, 1, 0));
        Vec3 fallbackLook = horizontal(player.getLookAngle(), parentUp);
        Vec3 capturedLook = directionVector(capturedPlayerDirection);
        if (capturedLook.lengthSqr() < EPSILON) {
            capturedLook = fallbackLook;
        }

        List<CannonData> cannons = resolveCannons(mounts);
        Vec3 structuralForward = blockEntity
                instanceof ControllerCollisionSource source
                ? source.resolveCollisionCannonForward() : null;
        Vec3 cannonForward = resolveForward(axis, frameReference,
                cannons, structuralForward, capturedLook);
        Vec3 neutralForward = resolveNeutralForward(axis, blockEntity,
                mounts, cannons, cannonForward);
        float depth = ControllerCollisionSnapshot.DEFAULT_DEPTH;
        ControllerCollisionViewFrame frame = (axis == CannonAxis.PITCH
                ? ControllerCollisionViewFrame.pitch(origin, parentUp,
                cannonForward, capturedLook)
                : ControllerCollisionViewFrame.yaw(origin, parentUp,
                cannonForward)).withCenteredDepthRange(depth);
        Vec3 dialCenter = frame.pointToView(dialCenterWorld);
        float dialZeroDegrees = dialZeroDegrees(
                axis, frame, neutralForward);

        Limits limits = limits();
        ScanBudget budget = new ScanBudget(limits.maxScannedBlocks,
                limits.maxBoxes);
        ArrayList<ControllerCollisionSnapshot.OrientedBox> cannonBoxes =
                new ArrayList<>();
        Set<UUID> controlledContraptions = new HashSet<>();
        float desiredHalfSpan = ControllerCollisionSnapshot.DEFAULT_HALF_SPAN;

        for (CannonData cannon : cannons) {
            controlledContraptions.add(cannon.entity.getUUID());
            collectContraption(cannon.entity, cannon.parent,
                    frame, ControllerCollisionSnapshot.Category.CANNON,
                    cannonBoxes, budget, Float.POSITIVE_INFINITY,
                    Float.POSITIVE_INFINITY);
            Vec3 muzzle = resolveMuzzleWorld(cannon);
            Vec3 projectedMuzzle = frame.pointToView(muzzle);
            desiredHalfSpan = Math.max(desiredHalfSpan,
                    (float) Math.max(Math.abs(projectedMuzzle.x),
                            Math.abs(projectedMuzzle.y)) + 3.0f);
        }
        for (ControllerCollisionSnapshot.OrientedBox box : cannonBoxes) {
            desiredHalfSpan = Math.max(desiredHalfSpan,
                    Math.max(Math.abs(box.centerU()) + box.radiusU(),
                            Math.abs(box.centerV()) + box.radiusV()));
        }
        desiredHalfSpan = Math.max(desiredHalfSpan,
                controlledSublevelHalfSpan(rootLevel, controlledSublevels,
                        frame));

        boolean spanClipped = desiredHalfSpan > limits.maxHalfSpan;
        float halfSpan = Math.min(desiredHalfSpan, limits.maxHalfSpan);
        ArrayList<ControllerCollisionSnapshot.OrientedBox> result =
                new ArrayList<>();
        for (ControllerCollisionSnapshot.OrientedBox box : cannonBoxes) {
            if (box.intersects(halfSpan, depth)) {
                result.add(box);
            }
        }

        AABB worldBounds = worldBounds(frame, halfSpan, depth);
        collectSableLevels(rootLevel, frame, halfSpan, depth,
                worldBounds, controlledContraptions, controlledSublevels,
                controlledMounts, result, budget);
        collectLevel(rootLevel, Function.identity(), frame, halfSpan,
                depth, worldBounds,
                ControllerCollisionSnapshot.Category.ENVIRONMENT,
                controlledMounts, result, budget);
        collectCreateContraptions(rootLevel, worldBounds,
                controlledContraptions, frame, halfSpan, depth,
                result, budget);

        ControllerCollisionSnapshot.Status status = !hasRequestedOrigin
                && mountPositions.isEmpty() && mounts.isEmpty()
                ? ControllerCollisionSnapshot.Status.NO_MOUNT
                : ControllerCollisionSnapshot.Status.OK;
        return new ControllerCollisionSnapshot(status, axis, halfSpan,
                depth, (float) dialCenter.x, (float) dialCenter.y,
                dialZeroDegrees, supportedLimits.minDegrees(),
                supportedLimits.maxDegrees(), movementLimits.minDegrees(),
                movementLimits.maxDegrees(), spanClipped, budget.truncated,
                trimToLimit(result, limits.maxBoxes));
    }

    private static List<CannonMountContext> collisionMounts(
            BlockEntity blockEntity
    ) {
        return blockEntity instanceof ControllerCollisionSource source
                ? deduplicateMounts(source.resolveCollisionCbcMounts())
                : List.of();
    }

    private static List<BlockPos> collisionMountPositions(
            BlockEntity blockEntity
    ) {
        return blockEntity instanceof ControllerCollisionSource source
                ? source.resolveCollisionMountPositions() : List.of();
    }

    private static Set<UUID> collisionSublevels(BlockEntity blockEntity) {
        return blockEntity instanceof ControllerCollisionSource source
                ? Set.copyOf(source.resolveCollisionSublevelIds()) : Set.of();
    }

    private static List<CannonMountContext> deduplicateMounts(
            List<CannonMountContext> source
    ) {
        ArrayList<CannonMountContext> result = new ArrayList<>();
        for (CannonMountContext mount : source) {
            if (mount == null || !mount.isCurrent()) {
                continue;
            }
            if (result.stream().noneMatch(existing -> existing.sameMount(mount))) {
                result.add(mount);
            }
        }
        return List.copyOf(result);
    }

    private static Vec3 resolveOrigin(BlockEntity controller,
                                      List<CannonMountContext> mounts,
                                      List<BlockPos> positions) {
        ArrayList<Vec3> points = new ArrayList<>();
        for (CannonMountContext mount : mounts) {
            PitchOrientedContraptionEntity entity = mount.getContraption();
            Vec3 local = entity != null && entity.isAlive()
                    ? entity.position() : mount.getBlockPos().getCenter();
            points.add(toRootWorld(parentOf(mount.blockEntity()), local));
        }
        if (points.isEmpty() && controller.getLevel() != null) {
            SubLevelAccess parent = parentOf(controller);
            for (BlockPos position : positions) {
                points.add(toRootWorld(parent, position.getCenter()));
            }
        }
        if (points.isEmpty()) {
            points.add(toRootWorld(parentOf(controller),
                    controller.getBlockPos().getCenter()));
        }
        Vec3 total = Vec3.ZERO;
        for (Vec3 point : points) {
            total = total.add(point);
        }
        return total.scale(1.0 / points.size());
    }

    private static Vec3 resolveDialCenter(
            CannonAxis axis, BlockEntity controller,
            List<CannonMountContext> mounts, List<BlockPos> positions,
            Vec3 fallback
    ) {
        if (axis != CannonAxis.PITCH) {
            return fallback;
        }
        ArrayList<Vec3> centers = new ArrayList<>();
        for (CannonMountContext mount : mounts) {
            centers.add(toRootWorld(parentOf(mount.blockEntity()),
                    mount.getBlockPos().getCenter().add(0.0, 2.0, 0.0)));
        }
        if (centers.isEmpty() && controller.getLevel() != null) {
            SubLevelAccess parent = parentOf(controller);
            for (BlockPos position : positions) {
                centers.add(toRootWorld(parent,
                        position.getCenter().add(0.0, 2.0, 0.0)));
            }
        }
        if (centers.isEmpty()) {
            return fallback;
        }
        Vec3 total = Vec3.ZERO;
        for (Vec3 center : centers) {
            total = total.add(center);
        }
        return total.scale(1.0 / centers.size());
    }

    private static List<CannonData> resolveCannons(
            List<CannonMountContext> mounts
    ) {
        ArrayList<CannonData> result = new ArrayList<>();
        for (CannonMountContext mount : mounts) {
            PitchOrientedContraptionEntity entity = mount.getContraption();
            if (entity != null && entity.isAlive()
                    && entity.getContraption()
                    instanceof AbstractMountedCannonContraption) {
                result.add(new CannonData(entity, parentOf(mount.blockEntity()),
                        mount.initialOrientation()));
            }
        }
        return result;
    }

    private static Vec3 resolveForward(CannonAxis axis,
                                       BlockEntity frameReference,
                                       List<CannonData> cannons,
                                       Vec3 structuralForward,
                                       Vec3 fallback) {
        if (!cannons.isEmpty()) {
            CannonData cannon = cannons.getFirst();
            if (axis == CannonAxis.YAW) {
                Vec3 local = CBCMuzzleUtil.getForwardWorld(cannon.entity);
                Vec3 world = worldDirection(cannon.parent, local);
                if (world.lengthSqr() >= EPSILON) {
                    return world;
                }
            }
            if (cannon.initialOrientation != null) {
                return worldDirection(cannon.parent,
                        directionVector(cannon.initialOrientation));
            }
        }
        if (structuralForward != null
                && structuralForward.lengthSqr() >= EPSILON) {
            return structuralForward;
        }
        return fallback.lengthSqr() >= EPSILON ? fallback
                : worldDirection(frameReference, new Vec3(0, 0, 1));
    }

    private static Vec3 resolveNeutralForward(
            CannonAxis axis, BlockEntity controller,
            List<CannonMountContext> mounts, List<CannonData> cannons,
            Vec3 fallback
    ) {
        if (controller instanceof ControllerCollisionSource source) {
            Vec3 structural = source.resolveCollisionNeutralForward();
            if (structural != null && structural.lengthSqr() >= EPSILON) {
                return structural;
            }
        }
        if (axis != CannonAxis.YAW) {
            return fallback;
        }
        if (!cannons.isEmpty()) {
            CannonData cannon = cannons.getFirst();
            if (cannon.initialOrientation != null) {
                return worldDirection(cannon.parent,
                        directionVector(cannon.initialOrientation));
            }
        }
        for (CannonMountContext mount : mounts) {
            BlockState state = mount.blockEntity().getBlockState();
            if (state.hasProperty(CannonMountBlock.HORIZONTAL_FACING)) {
                return worldDirection(parentOf(mount.blockEntity()),
                        directionVector(state.getValue(
                                CannonMountBlock.HORIZONTAL_FACING)));
            }
        }
        return fallback;
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x)
                && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    static float dialZeroDegrees(
            CannonAxis axis, ControllerCollisionViewFrame frame,
            Vec3 neutralForward
    ) {
        if (neutralForward == null) {
            return 0.0f;
        }
        Vec3 projected = frame.vectorToView(neutralForward);
        if (projected.x * projected.x + projected.y * projected.y
                < EPSILON) {
            return 0.0f;
        }
        if (axis == CannonAxis.PITCH) {
            return projected.x < 0.0 ? 180.0f : 0.0f;
        }
        return (float) Math.toDegrees(Math.atan2(
                projected.x, projected.y));
    }

    private static Vec3 resolveMuzzleWorld(CannonData cannon) {
        if (cannon.entity.getContraption()
                instanceof AbstractMountedCannonContraption mounted) {
            BlockPos exit = CBCMuzzleUtil.getMuzzleExitLocal(mounted);
            if (exit != null) {
                Vec3 local = cannon.entity.toGlobalVector(
                        Vec3.atCenterOf(exit), 1.0f);
                return toRootWorld(cannon.parent, local);
            }
        }
        return toRootWorld(cannon.parent,
                CBCMuzzleUtil.getCBCSpawnAnchorWorld(cannon.entity));
    }

    private static void collectLevel(
            Level level, Function<Vec3, Vec3> localToWorld,
            ControllerCollisionViewFrame frame, float halfSpan,
            float depth, AABB localBounds,
            ControllerCollisionSnapshot.Category category,
            Set<BlockEntity> controlledMounts,
            List<ControllerCollisionSnapshot.OrientedBox> output,
            ScanBudget budget
    ) {
        // Renderer-only mount pieces can extend beyond their owning block.
        int minX = (int) Math.floor(localBounds.minX) - 2;
        int minY = (int) Math.floor(localBounds.minY) - 2;
        int minZ = (int) Math.floor(localBounds.minZ) - 2;
        int maxX = (int) Math.floor(localBounds.maxX) + 2;
        int maxY = (int) Math.floor(localBounds.maxY) + 2;
        int maxZ = (int) Math.floor(localBounds.maxZ) + 2;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = minY; y <= maxY && !budget.exhausted(); y++) {
            for (int z = minZ; z <= maxZ && !budget.exhausted(); z++) {
                for (int x = minX; x <= maxX; x++) {
                    cursor.set(x, y, z);
                    if (!level.hasChunkAt(cursor)) {
                        continue;
                    }
                    if (!budget.inspect()) {
                        return;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    BlockPos position = cursor.immutable();
                    BlockEntity scannedBlockEntity =
                            level.getBlockEntity(position);
                    ControllerCollisionSnapshot.Category blockCategory =
                            categoryForBlock(category,
                                    controlledMounts.contains(
                                            scannedBlockEntity));
                    for (ControllerVisualBox shapeBox
                            : ControllerDisplayShapeResolver.resolve(
                            level, position, state)) {
                        if (!appendDisplayBox(shapeBox.move(position),
                                localToWorld, frame, blockCategory, output,
                                budget, halfSpan, depth)) {
                            return;
                        }
                    }
                    if (!appendCannonMountVisual(
                            scannedBlockEntity, localToWorld,
                            frame, blockCategory, output, budget, halfSpan,
                            depth)) {
                        return;
                    }
                }
            }
        }
    }

    private static void collectSableLevels(
            ServerLevel rootLevel, ControllerCollisionViewFrame frame,
            float halfSpan, float depth, AABB worldBounds,
            Set<UUID> excludedContraptions,
            Set<UUID> controlledSublevels,
            Set<BlockEntity> controlledMounts,
            List<ControllerCollisionSnapshot.OrientedBox> output,
            ScanBudget budget
    ) {
        if (!Mods.SABLE.isLoaded() || budget.exhausted()) {
            return;
        }
        ArrayList<SubLevel> subLevels = new ArrayList<>();
        SableUtils.getLoadedShips(rootLevel, worldBounds.inflate(1.0e-4))
                .forEach(subLevels::add);
        subLevels.sort((first, second) -> Boolean.compare(
                controlledSublevels.contains(second.getUniqueId()),
                controlledSublevels.contains(first.getUniqueId())));
        for (SubLevel subLevel : subLevels) {
            if (budget.exhausted()) {
                return;
            }
            Function<Vec3, Vec3> localToWorld = point -> {
                Vector3d transformed = subLevel.logicalPose()
                        .transformPosition(new Vector3d(point.x, point.y, point.z));
                return new Vec3(transformed.x, transformed.y, transformed.z);
            };
            Function<Vec3, Vec3> worldToLocal = point -> {
                Vector3d transformed = subLevel.logicalPose()
                        .transformPositionInverse(new Vector3d(point.x, point.y, point.z));
                return new Vec3(transformed.x, transformed.y, transformed.z);
            };
            AABB localBounds = transformedBounds(frame, halfSpan, depth,
                    worldToLocal);
            ControllerCollisionSnapshot.Category category =
                    controlledSublevels.contains(subLevel.getUniqueId())
                            ? ControllerCollisionSnapshot.Category.CANNON
                            : ControllerCollisionSnapshot.Category.ENVIRONMENT;
            collectLevel(subLevel.getLevel(), localToWorld, frame,
                    halfSpan, depth, localBounds, category,
                    controlledMounts, output, budget);
            if (budget.exhausted()) {
                return;
            }
            for (AbstractContraptionEntity entity : subLevel.getLevel()
                    .getEntitiesOfClass(AbstractContraptionEntity.class,
                            localBounds)) {
                if (!excludedContraptions.contains(entity.getUUID())) {
                    collectContraption(entity, subLevel, frame,
                            category,
                            output, budget, halfSpan, depth);
                }
            }
        }
    }

    private static void collectCreateContraptions(
            ServerLevel level, AABB worldBounds, Set<UUID> excluded,
            ControllerCollisionViewFrame frame, float halfSpan,
            float depth,
            List<ControllerCollisionSnapshot.OrientedBox> output,
            ScanBudget budget
    ) {
        if (budget.exhausted()) {
            return;
        }
        for (AbstractContraptionEntity entity : level.getEntitiesOfClass(
                AbstractContraptionEntity.class, worldBounds)) {
            if (!excluded.contains(entity.getUUID())) {
                collectContraption(entity, null, frame,
                        ControllerCollisionSnapshot.Category.ENVIRONMENT,
                        output, budget, halfSpan, depth);
            }
        }
    }

    private static void collectContraption(
            AbstractContraptionEntity entity,
            SubLevelAccess parent,
            ControllerCollisionViewFrame frame,
            ControllerCollisionSnapshot.Category category,
            List<ControllerCollisionSnapshot.OrientedBox> output,
            ScanBudget budget, float halfSpan, float depth
    ) {
        if (entity == null || entity.getContraption() == null) {
            return;
        }
        Function<Vec3, Vec3> localToWorld = point -> toRootWorld(parent,
                entity.toGlobalVector(point, 1.0f));
        Level contraptionWorld = entity.getContraption()
                .getContraptionWorld();
        for (StructureBlockInfo info : entity.getContraption()
                .getBlocks().values()) {
            if (budget.exhausted()) {
                return;
            }
            BlockPos pos = info.pos();
            for (ControllerVisualBox shapeBox
                    : ControllerDisplayShapeResolver.resolve(
                    contraptionWorld, pos, info.state())) {
                if (!appendDisplayBox(shapeBox.move(pos), localToWorld,
                        frame, category, output, budget, halfSpan, depth)) {
                    return;
                }
            }
        }
    }

    private static boolean appendCannonMountVisual(
            BlockEntity blockEntity, Function<Vec3, Vec3> localToWorld,
            ControllerCollisionViewFrame frame,
            ControllerCollisionSnapshot.Category category,
            List<ControllerCollisionSnapshot.OrientedBox> output,
            ScanBudget budget, float halfSpan, float depth
    ) {
        if (!(blockEntity instanceof CannonMountBlockEntity mount)) {
            return true;
        }
        BlockState state = mount.getBlockState();
        Vec3 up = state.hasProperty(CannonMountBlock.VERTICAL_DIRECTION)
                ? directionVector(state.getValue(
                CannonMountBlock.VERTICAL_DIRECTION).getOpposite())
                : new Vec3(0, 1, 0);
        PitchOrientedContraptionEntity cannon = mount.getContraption();
        Vec3 forward = cannon != null && cannon.isAlive()
                ? CBCMuzzleUtil.getForwardWorld(cannon)
                : state.hasProperty(CannonMountBlock.HORIZONTAL_FACING)
                ? directionVector(state.getValue(
                CannonMountBlock.HORIZONTAL_FACING))
                : new Vec3(0, 0, 1);
        Vec3 pivot = mount.getBlockPos().getCenter().add(up.scale(0.5));
        for (ControllerVisualBox box : ControllerMountVisualGeometry.bracket(
                pivot, up, forward)) {
            if (!appendDisplayBox(box, localToWorld, frame, category,
                    output, budget, halfSpan, depth)) {
                return false;
            }
        }
        return true;
    }

    static ControllerCollisionSnapshot.Category categoryForBlock(
            ControllerCollisionSnapshot.Category fallback,
            boolean controlledMount
    ) {
        return controlledMount
                ? ControllerCollisionSnapshot.Category.CANNON : fallback;
    }

    private static boolean appendDisplayBox(
            ControllerVisualBox localBox,
            Function<Vec3, Vec3> localToWorld,
            ControllerCollisionViewFrame frame,
            ControllerCollisionSnapshot.Category category,
            List<ControllerCollisionSnapshot.OrientedBox> output,
            ScanBudget budget, float halfSpan, float depth
    ) {
        ControllerCollisionSnapshot.OrientedBox box = orientedBox(
                localBox, localToWorld, frame, category);
        if (box == null || !box.intersects(halfSpan, depth)) {
            return true;
        }
        if (!budget.addBox()) {
            return false;
        }
        output.add(box);
        return true;
    }

    private static ControllerCollisionSnapshot.OrientedBox orientedBox(
            ControllerVisualBox box, Function<Vec3, Vec3> localToWorld,
            ControllerCollisionViewFrame frame,
            ControllerCollisionSnapshot.Category category
    ) {
        Vec3 centerLocal = box.center();
        if (box.axisX().lengthSqr() <= EPSILON
                || box.axisY().lengthSqr() <= EPSILON
                || box.axisZ().lengthSqr() <= EPSILON) {
            return null;
        }
        Vec3 centerWorld = localToWorld.apply(centerLocal);
        Vec3 center = frame.pointToView(centerWorld);
        Vec3 x = frame.vectorToView(localToWorld.apply(
                centerLocal.add(box.axisX())).subtract(centerWorld));
        Vec3 y = frame.vectorToView(localToWorld.apply(
                centerLocal.add(box.axisY())).subtract(centerWorld));
        Vec3 z = frame.vectorToView(localToWorld.apply(
                centerLocal.add(box.axisZ())).subtract(centerWorld));
        ControllerCollisionSnapshot.OrientedBox result =
                new ControllerCollisionSnapshot.OrientedBox(
                        (float) center.x, (float) center.y, (float) center.z,
                        (float) x.x, (float) x.y, (float) x.z,
                        (float) y.x, (float) y.y, (float) y.z,
                        (float) z.x, (float) z.y, (float) z.z,
                        category);
        return result.isFinite() ? result : null;
    }

    private static AABB worldBounds(ControllerCollisionViewFrame frame,
                                    float halfSpan, float depth) {
        return transformedBounds(frame, halfSpan, depth, Function.identity());
    }

    private static AABB transformedBounds(
            ControllerCollisionViewFrame frame, float halfSpan,
            float depth, Function<Vec3, Vec3> worldTransform
    ) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int uSign : new int[]{-1, 1}) {
            for (int vSign : new int[]{-1, 1}) {
                for (int depthSide : new int[]{0, 1}) {
                    Vec3 point = frame.viewToWorld(uSign * halfSpan,
                            vSign * halfSpan, depthSide * depth);
                    point = worldTransform.apply(point);
                    minX = Math.min(minX, point.x);
                    minY = Math.min(minY, point.y);
                    minZ = Math.min(minZ, point.z);
                    maxX = Math.max(maxX, point.x);
                    maxY = Math.max(maxY, point.y);
                    maxZ = Math.max(maxZ, point.z);
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static float controlledSublevelHalfSpan(
            ServerLevel rootLevel, Set<UUID> controlledSublevels,
            ControllerCollisionViewFrame frame
    ) {
        if (!Mods.SABLE.isLoaded() || controlledSublevels.isEmpty()) {
            return ControllerCollisionSnapshot.DEFAULT_HALF_SPAN;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(rootLevel);
        if (container == null) {
            return ControllerCollisionSnapshot.DEFAULT_HALF_SPAN;
        }
        float halfSpan = ControllerCollisionSnapshot.DEFAULT_HALF_SPAN;
        for (UUID id : controlledSublevels) {
            SubLevel subLevel = container.getSubLevel(id);
            if (subLevel == null || subLevel.isRemoved()) {
                continue;
            }
            BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
            if (bounds == null || bounds.maxX() < bounds.minX()
                    || bounds.maxY() < bounds.minY()
                    || bounds.maxZ() < bounds.minZ()) {
                continue;
            }
            for (int xSide : new int[]{0, 1}) {
                double x = xSide == 0 ? bounds.minX() : bounds.maxX() + 1.0;
                for (int ySide : new int[]{0, 1}) {
                    double y = ySide == 0 ? bounds.minY() : bounds.maxY() + 1.0;
                    for (int zSide : new int[]{0, 1}) {
                        double z = zSide == 0 ? bounds.minZ() : bounds.maxZ() + 1.0;
                        Vector3d world = subLevel.logicalPose()
                                .transformPosition(new Vector3d(x, y, z));
                        Vec3 view = frame.pointToView(new Vec3(
                                world.x, world.y, world.z));
                        halfSpan = Math.max(halfSpan,
                                (float) Math.max(Math.abs(view.x),
                                        Math.abs(view.y)) + 3.0f);
                    }
                }
            }
        }
        return halfSpan;
    }

    private static SubLevelAccess parentOf(BlockEntity blockEntity) {
        return blockEntity == null || blockEntity.getLevel() == null
                || !Mods.SABLE.isLoaded() ? null
                : SableUtils.getShipManagingPos(blockEntity);
    }

    private static Vec3 toRootWorld(SubLevelAccess parent, Vec3 point) {
        return parent == null ? point : SableUtils.getWorldVec(point, parent);
    }

    private static Vec3 worldDirection(BlockEntity reference, Vec3 local) {
        return worldDirection(parentOf(reference), local);
    }

    private static Vec3 worldDirection(SubLevelAccess parent, Vec3 local) {
        return parent == null ? local
                : SableUtils.getWorldVecDirectionTransform(local, parent);
    }

    private static Vec3 directionVector(Direction direction) {
        if (direction == null) {
            return Vec3.ZERO;
        }
        return new Vec3(direction.getStepX(), direction.getStepY(),
                direction.getStepZ());
    }

    private static Vec3 horizontal(Vec3 vector, Vec3 up) {
        Vec3 result = vector.subtract(up.scale(vector.dot(up)));
        return result.lengthSqr() < EPSILON ? Vec3.ZERO : result.normalize();
    }

    private static List<ControllerCollisionSnapshot.OrientedBox> trimToLimit(
            List<ControllerCollisionSnapshot.OrientedBox> boxes, int limit
    ) {
        return boxes.size() <= limit ? List.copyOf(boxes)
                : List.copyOf(boxes.subList(0, limit));
    }

    private static Limits limits() {
        if (RadarConfig.server() == null) {
            return new Limits(64, 100_000, 16_384);
        }
        return new Limits(
                RadarConfig.server().controllerCollisionViewMaxHalfSpan.get(),
                RadarConfig.server().controllerCollisionViewMaxScannedBlocks.get(),
                RadarConfig.server().controllerCollisionViewMaxBoxes.get());
    }

    private record CannonData(PitchOrientedContraptionEntity entity,
                              SubLevelAccess parent,
                              Direction initialOrientation) {
    }

    private record Limits(int maxHalfSpan, int maxScannedBlocks,
                          int maxBoxes) {
    }

    private static final class ScanBudget {
        private final int maxScannedBlocks;
        private final int maxBoxes;
        private int scannedBlocks;
        private int boxes;
        private boolean truncated;

        private ScanBudget(int maxScannedBlocks, int maxBoxes) {
            this.maxScannedBlocks = maxScannedBlocks;
            this.maxBoxes = maxBoxes;
        }

        private boolean inspect() {
            if (scannedBlocks >= maxScannedBlocks) {
                truncated = true;
                return false;
            }
            scannedBlocks++;
            return true;
        }

        private boolean addBox() {
            if (boxes >= maxBoxes) {
                truncated = true;
                return false;
            }
            boxes++;
            return true;
        }

        private boolean exhausted() {
            return truncated && (scannedBlocks >= maxScannedBlocks
                    || boxes >= maxBoxes);
        }
    }
}
