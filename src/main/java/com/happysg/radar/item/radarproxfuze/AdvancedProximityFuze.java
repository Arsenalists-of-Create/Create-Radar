package com.happysg.radar.item.radarproxfuze;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.SableUtils;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.cannon_control.ControlPitchContraption;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.index.CBCDataComponents;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;
import rbasamoyai.createbigcannons.munitions.fuzes.ProximityFuzeItem;


import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public class AdvancedProximityFuze extends ProximityFuzeItem {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int ARMING_DELAY_TICKS = 5;
    private static final int DEFAULT_DETONATION_DISTANCE = 5;
    private static final int MAX_VOXEL_BLOCK_CHECKS = 8192;

    private static final String TAG_SOURCE_MOUNT_POS = "sourceMountPos";
    private static final String TAG_NETWORK_FILTERER_POS = "networkFiltererPos";
    private static final String TAG_NETWORK_DIMENSION = "networkDimension";

    private static final ThreadLocal<Deque<LaunchContext>> LAUNCH_CONTEXT =
            ThreadLocal.withInitial(ArrayDeque::new);

    public AdvancedProximityFuze(Properties properties) {
        super(properties);
    }

    @Override
    public boolean onProjectileClip(ItemStack stack, AbstractCannonProjectile projectile, Vec3 start, Vec3 end, ProjectileContext ctx, boolean baseFuze) {
        return false;
    }

    @Override
    public boolean onProjectileTick(ItemStack stack, AbstractCannonProjectile projectile) {
        super.onProjectileTick(stack, projectile);

        RadarTrack target = getSelectedRadarTarget(stack, projectile);
        if (target != null) {
            ProximityTarget proximityTarget = getRadarProximityTarget(stack, projectile, target);
            if (proximityTarget != null && proximityTarget.distanceSqr() <= getDetonationDistanceSqr(stack)) {
                snapToTarget(projectile, proximityTarget.targetPos());
                return true;
            }

            return false;
        }

        if (projectile.tickCount < ARMING_DELAY_TICKS) {
            return false;
        }

        ProximityTarget proximityTarget = getNearbyProximityTarget(stack, projectile);
        if (proximityTarget != null) {
            snapToTarget(projectile, proximityTarget.targetPos());
            return true;
        }

        return false;
    }

    @Override
    public boolean onProjectileImpact(ItemStack stack, AbstractCannonProjectile projectile, HitResult hitResult, AbstractCannonProjectile.ImpactResult impactResult, boolean baseFuze) {
        return !baseFuze;
    }

    @Override
    public boolean onProjectileExpiry(ItemStack stack, AbstractCannonProjectile projectile) {
        return false;
    }

    public static void pushLaunchContext(ServerLevel level, BlockPos mountPos) {
        BlockPos filtererPos = NetworkData.get(level).getFiltererForWeaponMount(level.dimension(), mountPos);
        LAUNCH_CONTEXT.get().push(new LaunchContext(
                level.dimension().location(),
                mountPos.immutable(),
                filtererPos != null ? filtererPos.immutable() : null
        ));
    }

    public static boolean pushLaunchContext(ServerLevel level, PitchOrientedContraptionEntity contraptionEntity) {
        ControlPitchContraption controller = contraptionEntity.getController();
        if (!(controller instanceof CannonMountBlockEntity mount)) {
            return false;
        }

        pushLaunchContext(level, mount.getBlockPos());
        return true;
    }

    public static void popLaunchContext() {
        Deque<LaunchContext> contexts = LAUNCH_CONTEXT.get();
        if (!contexts.isEmpty()) {
            contexts.pop();
        }
        if (contexts.isEmpty()) {
            LAUNCH_CONTEXT.remove();
        }
    }

    public static void assignCurrentLaunchContext(ItemStack stack) {
        if (!(stack.getItem() instanceof AdvancedProximityFuze)) {
            return;
        }

        LaunchContext context = LAUNCH_CONTEXT.get().peek();
        if (context == null) {
            LOGGER.warn("failed to get context");
            return;
        }
        LOGGER.warn("Advanced proximity fuze launch context: dimension={}, mountPos={}, networkFiltererPos={}",
                context.dimension(),
                context.mountPos(),
                context.filtererPos()
        );

        CompoundTag tag = getCustomData(stack);
        tag.put(TAG_SOURCE_MOUNT_POS, NbtUtils.writeBlockPos(context.mountPos()));
        tag.putString(TAG_NETWORK_DIMENSION, context.dimension().toString());

        if (context.filtererPos() != null) {
            tag.put(TAG_NETWORK_FILTERER_POS, NbtUtils.writeBlockPos(context.filtererPos()));
        } else {
            tag.remove(TAG_NETWORK_FILTERER_POS);
        }

        setCustomData(stack, tag);
    }

    @Nullable
    public static BlockPos getSourceMountPos(ItemStack stack) {
        return NbtUtils.readBlockPos(getCustomData(stack), TAG_SOURCE_MOUNT_POS).orElse(null);
    }

    @Nullable
    public static BlockPos getNetworkFiltererPos(ItemStack stack) {
        return NbtUtils.readBlockPos(getCustomData(stack), TAG_NETWORK_FILTERER_POS).orElse(null);
    }

    @Nullable
    public static ResourceLocation getNetworkDimension(ItemStack stack) {
        CompoundTag tag = getCustomData(stack);
        return tag.contains(TAG_NETWORK_DIMENSION) ? ResourceLocation.tryParse(tag.getString(TAG_NETWORK_DIMENSION)) : null;
    }

    private static CompoundTag getCustomData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void setCustomData(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static double getDetonationDistance(ItemStack stack) {
        return Math.max(1, stack.getOrDefault(CBCDataComponents.DETONATION_DISTANCE, DEFAULT_DETONATION_DISTANCE));
    }

    private static double getDetonationDistanceSqr(ItemStack stack) {
        double distance = getDetonationDistance(stack);
        return distance * distance;
    }

    @Nullable
    private static RadarTrack getSelectedRadarTarget(ItemStack stack, AbstractCannonProjectile projectile) {
        ResourceLocation contextDimension = getNetworkDimension(stack);
        if (contextDimension != null && !contextDimension.equals(projectile.level().dimension().location())) {
            return null;
        }

        BlockPos controllerPos = getNetworkFiltererPos(stack);
        if (controllerPos == null) {
            return null;
        }

        if (!(projectile.level().getBlockEntity(controllerPos) instanceof NetworkFiltererBlockEntity controller)) {
            return null;
        }

        return controller.activeTrackCache;
    }

    @Nullable
    private static ProximityTarget getRadarProximityTarget(ItemStack stack, AbstractCannonProjectile projectile, RadarTrack target) {
        if (projectile.level() instanceof ServerLevel serverLevel) {
            ProximityTarget structureTarget = getLiveStructureTarget(serverLevel, projectile, target, getDetonationDistance(stack));
            if (structureTarget != null) {
                return structureTarget;
            }
        }

        Vec3 targetPos = target.getPosition();
        return targetPos == null ? null : new ProximityTarget(targetPos, sweptDistanceToPointSqr(projectile, targetPos));
    }

    @Nullable
    private static ProximityTarget getNearbyProximityTarget(ItemStack stack, AbstractCannonProjectile projectile) {
        double detonationDistance = getDetonationDistance(stack);
        double detonationDistanceSqr = detonationDistance * detonationDistance;

        ProximityTarget nearest = projectile.level()
                .getEntities(projectile, projectile.getBoundingBox().inflate(detonationDistance), projectile::canHitEntity)
                .stream()
                .map(entity -> entityProximityTarget(projectile, entity, detonationDistance))
                .filter(target -> target.distanceSqr() <= detonationDistanceSqr)
                .min((left, right) -> Double.compare(left.distanceSqr(), right.distanceSqr()))
                .orElse(null);

        ProximityTarget structureTarget = getNearbyStructureTarget(stack, projectile, detonationDistance, detonationDistanceSqr);
        if (nearest == null || structureTarget != null && structureTarget.distanceSqr() < nearest.distanceSqr()) {
            nearest = structureTarget;
        }

        return nearest;
    }

    @Nullable
    private static ProximityTarget getNearbyStructureTarget(ItemStack stack, AbstractCannonProjectile projectile, double detonationDistance, double detonationDistanceSqr) {
        if (!Mods.SABLE.isLoaded()) {
            return null;
        }

        UUID sourceShipId = getSourceShipId(stack, projectile);
        AABB searchBounds = projectile.getBoundingBox().minmax(new AABB(previousPosition(projectile), projectile.position())).inflate(detonationDistance);
        ProximityTarget nearest = null;
        for (SubLevel subLevel : SableUtils.getLoadedShips(projectile.level(), searchBounds)) {
            UUID shipId = subLevel.getUniqueId();
            if (shipId != null && shipId.equals(sourceShipId)) {
                continue;
            }

            ProximityTarget target = subLevelProximityTarget(projectile, subLevel, detonationDistance);
            if (target == null) {
                continue;
            }

            if (target.distanceSqr() <= detonationDistanceSqr && (nearest == null || target.distanceSqr() < nearest.distanceSqr())) {
                nearest = target;
            }
        }

        return nearest;
    }

    private static void snapToTarget(AbstractCannonProjectile projectile, Vec3 targetPos) {
        Vec3 toTarget = targetPos.subtract(projectile.position());
        if (toTarget.lengthSqr() < 1.0E-9D) {
            return;
        }

        Vec3 direction = toTarget.normalize();
        double speed = Math.max(projectile.getDeltaMovement().length(), 0.01D);
        projectile.setDeltaMovement(direction.scale(speed));
        projectile.setOrientation(direction);
    }

    private static Vec3 previousPosition(AbstractCannonProjectile projectile) {
        return new Vec3(projectile.xOld, projectile.yOld, projectile.zOld);
    }

    private static double sweptDistanceToPointSqr(AbstractCannonProjectile projectile, Vec3 point) {
        Vec3 start = previousPosition(projectile);
        Vec3 end = projectile.position();
        Vec3 travel = end.subtract(start);
        double travelSqr = travel.lengthSqr();

        if (travelSqr < 1.0E-9D) {
            return point.distanceToSqr(end);
        }

        double t = point.subtract(start).dot(travel) / travelSqr;
        t = Math.max(0.0D, Math.min(1.0D, t));
        Vec3 closest = start.add(travel.scale(t));
        return point.distanceToSqr(closest);
    }

    @Nullable
    private static ProximityTarget getLiveStructureTarget(ServerLevel level, AbstractCannonProjectile projectile, RadarTrack target, double detonationDistance) {
        if (target.trackCategory() == TrackCategory.SABLE && Mods.SABLE.isLoaded()) {
            try {
                UUID shipId = UUID.fromString(target.id());
                SubLevelContainer container = SubLevelContainer.getContainer(level);
                SubLevelAccess subLevel = container == null ? null : container.getSubLevel(shipId);
                return subLevelProximityTarget(projectile, subLevel, detonationDistance);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        if (target.trackCategory() == TrackCategory.CONTRAPTION) {
            try {
                Entity entity = level.getEntity(UUID.fromString(target.id()));
                return entity != null && entity.isAlive() ? entityProximityTarget(projectile, entity, detonationDistance) : null;
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        return null;
    }

    @Nullable
    private static ProximityTarget entityProximityTarget(AbstractCannonProjectile projectile, Entity entity, double detonationDistance) {
        if (entity instanceof AbstractContraptionEntity contraptionEntity) {
            ProximityTarget contraptionTarget = contraptionProximityTarget(projectile, contraptionEntity, detonationDistance);
            if (contraptionTarget != null) {
                return contraptionTarget;
            }
        }

        return sweptDistanceToAabb(projectile, entity.getBoundingBox().inflate(projectile.getBbWidth() * 0.5D));
    }

    @Nullable
    private static ProximityTarget subLevelProximityTarget(AbstractCannonProjectile projectile, @Nullable SubLevelAccess subLevel, double detonationDistance) {
        AABB bounds = toAabb(subLevel);
        if (bounds == null) {
            return null;
        }

        ProximityTarget broadTarget = sweptDistanceToAabb(projectile, bounds);
        double broadLimit = detonationDistance + projectile.getBbWidth();
        if (broadTarget.distanceSqr() > broadLimit * broadLimit) {
            return broadTarget;
        }

        ProximityTarget voxelTarget = subLevelVoxelProximityTarget(projectile, subLevel, detonationDistance);
        return voxelTarget != null ? voxelTarget : broadTarget;
    }

    @Nullable
    private static ProximityTarget contraptionProximityTarget(AbstractCannonProjectile projectile, AbstractContraptionEntity contraptionEntity, double detonationDistance) {
        if (contraptionEntity.getContraption() == null || contraptionEntity.getContraption().getBlocks().isEmpty()) {
            return sweptDistanceToAabb(projectile, contraptionEntity.getBoundingBox().inflate(projectile.getBbWidth() * 0.5D));
        }

        ProximityTarget broadTarget = sweptDistanceToAabb(projectile, contraptionEntity.getBoundingBox().inflate(projectile.getBbWidth() * 0.5D));
        double broadLimit = detonationDistance + projectile.getBbWidth();
        if (broadTarget.distanceSqr() > broadLimit * broadLimit) {
            return broadTarget;
        }

        Vec3 localStart = contraptionEntity.toLocalVector(previousPosition(projectile), 1.0F);
        Vec3 localEnd = contraptionEntity.toLocalVector(projectile.position(), 1.0F);
        AABB localSearch = new AABB(localStart, localEnd).inflate(detonationDistance + projectile.getBbWidth() + 1.0D);
        ProximityTarget nearest = null;
        int checked = 0;

        for (StructureBlockInfo blockInfo : contraptionEntity.getContraption().getBlocks().values()) {
            if (++checked > MAX_VOXEL_BLOCK_CHECKS) {
                return nearest != null ? nearest : broadTarget;
            }

            BlockPos pos = blockInfo.pos();
            if (!localSearch.intersects(new AABB(pos))) {
                continue;
            }

            ProximityTarget target = blockShapeProximityTarget(projectile, blockInfo.state(), pos, point -> contraptionEntity.toGlobalVector(point, 1.0F));
            if (target != null && (nearest == null || target.distanceSqr() < nearest.distanceSqr())) {
                nearest = target;
            }
        }

        return nearest != null ? nearest : broadTarget;
    }

    @Nullable
    private static UUID getSourceShipId(ItemStack stack, AbstractCannonProjectile projectile) {
        BlockPos sourceMountPos = getSourceMountPos(stack);
        if (sourceMountPos == null || !Mods.SABLE.isLoaded()) {
            return null;
        }

        SubLevelAccess sourceShip = SableCompanion.INSTANCE.getContaining(projectile.level(), sourceMountPos);
        return sourceShip == null ? null : sourceShip.getUniqueId();
    }

    @Nullable
    private static AABB toAabb(@Nullable SubLevelAccess subLevel) {
        if (subLevel == null || subLevel.boundingBox() == null) {
            return null;
        }

        BoundingBox3dc box = subLevel.boundingBox();
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    @Nullable
    private static AABB toAabb(@Nullable SubLevel subLevel) {
        if (subLevel == null || subLevel.boundingBox() == null) {
            return null;
        }

        BoundingBox3dc box = subLevel.boundingBox();
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    @Nullable
    private static ProximityTarget subLevelVoxelProximityTarget(AbstractCannonProjectile projectile, SubLevelAccess subLevel, double detonationDistance) {
        if (!(subLevel instanceof SubLevel concreteSubLevel)) {
            return null;
        }

        AABB worldSearch = projectile.getBoundingBox()
                .minmax(new AABB(previousPosition(projectile), projectile.position()))
                .inflate(detonationDistance + projectile.getBbWidth() + 1.0D);
        AABB localSearch = transformAabb(worldSearch, point -> transformSubLevelPointInverse(subLevel, point));
        dev.ryanhcode.sable.companion.math.BoundingBox3ic localBounds = concreteSubLevel.getPlot().getBoundingBox();
        int minX = Math.max(localBounds.minX(), (int)Math.floor(localSearch.minX));
        int minY = Math.max(localBounds.minY(), (int)Math.floor(localSearch.minY));
        int minZ = Math.max(localBounds.minZ(), (int)Math.floor(localSearch.minZ));
        int maxX = Math.min(localBounds.maxX(), (int)Math.floor(localSearch.maxX));
        int maxY = Math.min(localBounds.maxY(), (int)Math.floor(localSearch.maxY));
        int maxZ = Math.min(localBounds.maxZ(), (int)Math.floor(localSearch.maxZ));
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return null;
        }

        long count = (long)(maxX - minX + 1) * (long)(maxY - minY + 1) * (long)(maxZ - minZ + 1);
        if (count > MAX_VOXEL_BLOCK_CHECKS) {
            return null;
        }

        ProximityTarget nearest = null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = concreteSubLevel.getLevel().getBlockState(pos);
                    ProximityTarget target = blockShapeProximityTarget(projectile, state, pos, point -> transformSubLevelPoint(subLevel, point));
                    if (target != null && (nearest == null || target.distanceSqr() < nearest.distanceSqr())) {
                        nearest = target;
                    }
                }
            }
        }

        return nearest;
    }

    @Nullable
    private static ProximityTarget blockShapeProximityTarget(AbstractCannonProjectile projectile, BlockState state, BlockPos pos, PointTransform transform) {
        if (state.isAir()) {
            return null;
        }

        VoxelShape shape = state.getCollisionShape(projectile.level(), pos, CollisionContext.empty());
        if (shape.isEmpty()) {
            return null;
        }

        ProximityTarget nearest = null;
        for (AABB localBox : shape.toAabbs()) {
            AABB worldBox = transformAabb(localBox.move(pos), transform);
            ProximityTarget target = sweptDistanceToAabb(projectile, worldBox.inflate(projectile.getBbWidth() * 0.5D));
            if (nearest == null || target.distanceSqr() < nearest.distanceSqr()) {
                nearest = target;
            }
        }

        return nearest;
    }

    private static Vec3 transformSubLevelPoint(SubLevelAccess subLevel, Vec3 point) {
        Vector3d transformed = subLevel.logicalPose().transformPosition(new Vector3d(point.x, point.y, point.z));
        return new Vec3(transformed.x(), transformed.y(), transformed.z());
    }

    private static Vec3 transformSubLevelPointInverse(SubLevelAccess subLevel, Vec3 point) {
        Vector3d transformed = subLevel.logicalPose().transformPositionInverse(new Vector3d(point.x, point.y, point.z));
        return new Vec3(transformed.x(), transformed.y(), transformed.z());
    }

    private static AABB transformAabb(AABB box, PointTransform transform) {
        AABB result = null;
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    Vec3 transformed = transform.apply(new Vec3(
                            x == 0 ? box.minX : box.maxX,
                            y == 0 ? box.minY : box.maxY,
                            z == 0 ? box.minZ : box.maxZ
                    ));
                    AABB pointBox = new AABB(transformed, transformed);
                    result = result == null ? pointBox : result.minmax(pointBox);
                }
            }
        }

        return result == null ? box : result;
    }

    private static ProximityTarget sweptDistanceToAabb(AbstractCannonProjectile projectile, AABB bounds) {
        Vec3 start = previousPosition(projectile);
        Vec3 end = projectile.position();
        return bounds.clip(start, end)
                .map(hit -> new ProximityTarget(hit, 0.0D))
                .orElseGet(() -> closestSegmentAabbTarget(start, end, bounds));
    }

    private static ProximityTarget closestSegmentAabbTarget(Vec3 start, Vec3 end, AABB bounds) {
        Vec3 travel = end.subtract(start);
        double travelSqr = travel.lengthSqr();
        if (travelSqr < 1.0E-9D) {
            Vec3 closest = closestPointOnAabb(end, bounds);
            return new ProximityTarget(closest, closest.distanceToSqr(end));
        }

        double low = 0.0D;
        double high = 1.0D;
        for (int i = 0; i < 20; i++) {
            double third = (high - low) / 3.0D;
            double left = low + third;
            double right = high - third;
            double leftDistance = pointAabbDistanceSqr(start.add(travel.scale(left)), bounds);
            double rightDistance = pointAabbDistanceSqr(start.add(travel.scale(right)), bounds);
            if (leftDistance < rightDistance) {
                high = right;
            } else {
                low = left;
            }
        }

        Vec3 closestOnSegment = start.add(travel.scale((low + high) * 0.5D));
        Vec3 closestOnBounds = closestPointOnAabb(closestOnSegment, bounds);
        return new ProximityTarget(closestOnBounds, closestOnBounds.distanceToSqr(closestOnSegment));
    }

    private static double pointAabbDistanceSqr(Vec3 point, AABB bounds) {
        return closestPointOnAabb(point, bounds).distanceToSqr(point);
    }

    private static Vec3 closestPointOnAabb(Vec3 point, AABB bounds) {
        return new Vec3(
                Math.max(bounds.minX, Math.min(bounds.maxX, point.x)),
                Math.max(bounds.minY, Math.min(bounds.maxY, point.y)),
                Math.max(bounds.minZ, Math.min(bounds.maxZ, point.z))
        );
    }

    private record LaunchContext(ResourceLocation dimension, BlockPos mountPos, @Nullable BlockPos filtererPos) {
    }

    private record ProximityTarget(Vec3 targetPos, double distanceSqr) {
    }

    @FunctionalInterface
    private interface PointTransform {
        Vec3 apply(Vec3 point);
    }
}
