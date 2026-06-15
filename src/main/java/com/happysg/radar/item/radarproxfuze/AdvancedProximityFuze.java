package com.happysg.radar.item.radarproxfuze;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
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

public class AdvancedProximityFuze extends ProximityFuzeItem {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int ARMING_DELAY_TICKS = 5;
    private static final int DEFAULT_DETONATION_DISTANCE = 5;

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
            Vec3 targetPos = target.getPosition();
            if (targetPos != null && sweptDistanceToPointSqr(projectile, targetPos) <= getDetonationDistanceSqr(stack)) {
                snapToTarget(projectile, targetPos);
                return true;
            }

            return false;
        }

        if (projectile.tickCount < ARMING_DELAY_TICKS) {
            return false;
        }

        Entity proximityTarget = getNearbyEntity(stack, projectile);
        if (proximityTarget != null) {
            snapToTarget(projectile, proximityTarget.getBoundingBox().getCenter());
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
    private static Entity getNearbyEntity(ItemStack stack, AbstractCannonProjectile projectile) {
        double detonationDistance = getDetonationDistance(stack);
        double detonationDistanceSqr = detonationDistance * detonationDistance;

        return projectile.level()
                .getEntities(projectile, projectile.getBoundingBox().inflate(detonationDistance), projectile::canHitEntity)
                .stream()
                .filter(entity -> sweptDistanceToEntitySqr(projectile, entity) <= detonationDistanceSqr)
                .findFirst()
                .orElse(null);
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

    private static double sweptDistanceToEntitySqr(AbstractCannonProjectile projectile, Entity entity) {
        Vec3 start = previousPosition(projectile);
        Vec3 end = projectile.position();
        return entity.getBoundingBox()
                .inflate(projectile.getBbWidth() * 0.5D)
                .clip(start, end)
                .map(hit -> 0.0D)
                .orElseGet(() -> sweptDistanceToPointSqr(projectile, entity.getBoundingBox().getCenter()));
    }

    private record LaunchContext(ResourceLocation dimension, BlockPos mountPos, @Nullable BlockPos filtererPos) {
    }
}
