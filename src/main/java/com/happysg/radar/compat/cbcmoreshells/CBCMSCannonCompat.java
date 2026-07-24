package com.happysg.radar.compat.cbcmoreshells;

import com.cainiao1053.cbcmoreshells.cannon_control.contraption.MountedDualCannonContraption;
import com.cainiao1053.cbcmoreshells.cannons.dual_cannon.IDualCannonBlockEntity;
import com.cainiao1053.cbcmoreshells.index.CBCMSMunitionPropertiesHandlers;
import com.cainiao1053.cbcmoreshells.munitions.big_cannon.AbstractCannonTorpedoProjectile;
import com.cainiao1053.cbcmoreshells.munitions.big_cannon.TorpedoProjectileBlock;
import com.cainiao1053.cbcmoreshells.munitions.big_cannon.config.TorpedoProperties;
import com.cainiao1053.cbcmoreshells.munitions.dual_cannon.AbstractDualCannonProjectile;
import com.cainiao1053.cbcmoreshells.munitions.dual_cannon.DualCannonProjectileBlock;
import com.cainiao1053.cbcmoreshells.munitions.dual_cannon.config.DualCannonProperties;
import com.happysg.radar.compat.cbc.CannonUtil;
import com.happysg.radar.targeting.ProjectileModel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption;
import rbasamoyai.createbigcannons.cannons.big_cannons.BigCannonBehavior;
import rbasamoyai.createbigcannons.cannons.big_cannons.IBigCannonBlockEntity;
import rbasamoyai.createbigcannons.munitions.big_cannon.propellant.BigCannonPropellantBlock;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionProperties;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionPropertiesHandler;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;


public final class CBCMSCannonCompat {
    public enum Family {
        DUAL_CANNON, BIG_CANNON_TORPEDO
    }

    public enum SolverMode { STANDARD_ASYNC, CBCMS_SERVER }
    public enum MuzzlePolicy { CBC, OUTSIDE_CENTER_BACKOFF_TWO }

    public record ShotState(
            Family family,
            ProjectileModel projectileModel,
            SolverMode solverMode,
            BallisticPropertiesComponent ballistics,
            int lifetimeCapTicks,
            MuzzlePolicy muzzlePolicy,
            String ammunitionId,
            String fingerprint,
            boolean legacyEligible,
            String diagnosticReason
    ) {}

    private CBCMSCannonCompat() {}

    public static boolean isCBCMSMount(@Nullable AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedDualCannonContraption;
    }

    public static boolean isCBCMSBarrel(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof IDualCannonBlockEntity;
    }

    @Nullable
    public static ShotState resolveShotState(AbstractMountedCannonContraption cannon, ServerLevel level) {
        if (cannon == null || level == null) return null;
        try {
            if (cannon instanceof MountedDualCannonContraption dual) return resolveDual(dual, level);
            if (cannon instanceof MountedBigCannonContraption big) return resolveBigCannonTorpedo(big, level);
        } catch (Throwable ignored) {
            // Compatibility must fail closed when an optional mod changes shape.
        }
        return null;
    }

    @Nullable
    private static ShotState resolveDual(MountedDualCannonContraption cannon, ServerLevel level) {
        List<Assembly<AbstractDualCannonProjectile>> shots =
                collectAssemblies(cannon, IDualCannonBlockEntity.class, 2, level, CBCMSCannonCompat::dualProjectile);
        if (shots.isEmpty()) return null;
        ShotState first = dualState(cannon, shots.getFirst(), level);
        if (first == null) return null;
        if (shots.size() == 2) {
            ShotState second = dualState(cannon, shots.get(1), level);
            if (second == null || !equivalentTrajectory(first, second)) return null;
            String pairFingerprint = digest(first.fingerprint() + "|" + second.fingerprint());
            return new ShotState(first.family(), first.projectileModel(), first.solverMode(), first.ballistics(),
                    first.lifetimeCapTicks(), first.muzzlePolicy(), first.ammunitionId() + "+" + second.ammunitionId(),
                    pairFingerprint, first.legacyEligible(), "dual_centerline_pair");
        }
        return first;
    }

    @Nullable
    private static ShotState dualState(MountedDualCannonContraption cannon,
                                       Assembly<AbstractDualCannonProjectile> assembly, ServerLevel level) {
        AbstractDualCannonProjectile projectile = assembly.projectile();
        BallisticPropertiesComponent ballistics = CannonUtil.getProjectileBallistics(projectile);
        if (ballistics == null) return null;
        DimensionMunitionProperties dimension = DimensionMunitionPropertiesHandler.getProperties(level);
        double gravity = ballistics.gravity() * dimension.gravityMultiplier();
        double speed = projectile.getInitVel();
        int addedLifetime = cannon.getCannonMaterial() == null ? 0
                : cannon.getCannonMaterial().properties().addedLifetime();
        double equipmentLifetime = privateFloat(cannon, "equipmentLifetimeModifier", 1.0F);
        int configuredLifetime = Math.max(0, (int) ((projectile.getLifetime() + addedLifetime)
                * cannon.commandLifetimeModifier * equipmentLifetime));
        ProjectileModel model = ProjectileModel.cbc(speed, gravity, ballistics.drag(),
                dimension.dragMultiplier(), ballistics.isQuadraticDrag());
        return state(Family.DUAL_CANNON, model, SolverMode.STANDARD_ASYNC, ballistics,
                configuredLifetime + 1, MuzzlePolicy.OUTSIDE_CENTER_BACKOFF_TWO, projectile,
                assembly, true, "dual_centerline");
    }

    @Nullable
    private static ShotState resolveBigCannonTorpedo(MountedBigCannonContraption cannon, ServerLevel level) {
        Direction direction = cannon.initialOrientation();
        BlockPos pos = cannon.getStartPos();
        if (direction == null || pos == null || cannon.presentBlockEntities == null) return null;
        float speed = 0.0F;
        List<StructureTemplate.StructureBlockInfo> pieces = new ArrayList<>();
        TorpedoProjectileBlock<?> root = null;
        while (cannon.presentBlockEntities.get(pos) instanceof IBigCannonBlockEntity be) {
            StructureTemplate.StructureBlockInfo info = be.cannonBehavior().block();
            if (info == null) return null;
            Block block = info.state().getBlock();
            if (info.state().isAir()) {
                if (root == null) speed = Math.max(0.0F, speed - 1.0F);
            } else if (root == null && block instanceof BigCannonPropellantBlock propellant) {
                speed += Math.max(0.0F, propellant.getChargePower(info));
            } else if (root == null && block instanceof TorpedoProjectileBlock<?> torpedo) {
                root = torpedo;
                pieces.add(info);
            } else if (root != null && root.isValidAddition(pieces, info, pieces.size(), direction)) {
                pieces.add(info);
            }
            if (root != null && root.isComplete(pieces, direction)) break;
            pos = pos.relative(direction);
        }
        if (root == null || !root.isComplete(pieces, direction)) return null;
        AbstractCannonTorpedoProjectile projectile = root.getProjectile(level, pieces);
        if (projectile == null) return null;
        BallisticPropertiesComponent ballistics = CannonUtil.getProjectileBallistics(projectile);
        if (ballistics == null) return null;
        int lifetime = runtimeTorpedoLifetime(projectile, fallbackTorpedoLifetime(projectile)) + 1;
        speed += projectile.addedChargePower();
        DimensionMunitionProperties dimension = DimensionMunitionPropertiesHandler.getProperties(level);
        ProjectileModel model = new CBCMSTorpedoProjectileModel(speed,
                ballistics.gravity() * dimension.gravityMultiplier(), ballistics.drag(),
                ballistics.isQuadraticDrag(), dimension.dragMultiplier(), projectile.getBuoyancyFactor(),
                projectile.getTorpedoSpeed(), lifetime);
        Assembly<AbstractCannonTorpedoProjectile> assembly = new Assembly<>(projectile, List.copyOf(pieces));
        return state(Family.BIG_CANNON_TORPEDO, model, SolverMode.CBCMS_SERVER, ballistics, lifetime,
                MuzzlePolicy.CBC, projectile, assembly, false, "big_cannon_torpedo");
    }

    private interface ProjectileFactory<T extends Entity> {
        @Nullable T create(Block block, ServerLevel level,
                           List<StructureTemplate.StructureBlockInfo> pieces, Direction direction);
    }

    private static <T extends Entity> List<Assembly<T>> collectAssemblies(
            AbstractMountedCannonContraption cannon, Class<?> barrelType, int limit,
            ServerLevel level, ProjectileFactory<T> factory
    ) {
        List<Assembly<T>> result = new ArrayList<>();
        Direction direction = cannon.initialOrientation();
        BlockPos pos = cannon.getStartPos();
        if (direction == null || pos == null || cannon.presentBlockEntities == null) return result;
        Block root = null;
        List<StructureTemplate.StructureBlockInfo> pieces = new ArrayList<>();
        while (result.size() < limit) {
            BlockEntity be = cannon.presentBlockEntities.get(pos);
            if (be == null || !barrelType.isInstance(be)) break;
            BigCannonBehavior behavior = (BigCannonBehavior)
                    ((rbasamoyai.createbigcannons.cannons.ICannonBlockEntity<?>) be).cannonBehavior();
            StructureTemplate.StructureBlockInfo info = behavior.block();
            if (info == null) return List.of();
            Block block = info.state().getBlock();
            if (root != null && (info.state().isAir() || block instanceof BigCannonPropellantBlock)) {
                return List.of();
            }
            if (!info.state().isAir() && !(block instanceof BigCannonPropellantBlock)) {
                if (root == null) {
                    root = block;
                }
                if (!validAddition(root, pieces, info, direction)) {
                    return List.of();
                }
                pieces.add(info);
                T projectile = factory.create(root, level, pieces, direction);
                if (projectile != null) {
                    result.add(new Assembly<>(projectile, List.copyOf(pieces)));
                    root = null;
                    pieces.clear();
                }
            }
            pos = pos.relative(direction);
        }
        if (root != null) return List.of();
        return result;
    }

    @Nullable
    private static AbstractDualCannonProjectile dualProjectile(
            Block block, ServerLevel level, List<StructureTemplate.StructureBlockInfo> pieces, Direction direction) {
        if (!(block instanceof DualCannonProjectileBlock<?> projectileBlock)) return null;
        if (!projectileBlock.isComplete(pieces, direction)) return null;
        return projectileBlock.getProjectile(level, pieces);
    }

    private static boolean validAddition(
            Block root, List<StructureTemplate.StructureBlockInfo> pieces,
            StructureTemplate.StructureBlockInfo addition, Direction direction
    ) {
        int index = pieces.size();
        if (root instanceof DualCannonProjectileBlock<?> block) {
            return block.isValidAddition(pieces, addition, index, direction);
        }
        return false;
    }

    private static boolean equivalentTrajectory(ShotState a, ShotState b) {
        return a.lifetimeCapTicks() == b.lifetimeCapTicks()
                && a.projectileModel().equals(b.projectileModel())
                && a.ballistics().equals(b.ballistics());
    }

    private static <T extends Entity> ShotState state(
            Family family, ProjectileModel model, SolverMode solver, BallisticPropertiesComponent ballistics,
            int lifetime, MuzzlePolicy muzzle, T projectile, Assembly<T> assembly,
            boolean legacy, String reason
    ) {
        String ammunition = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()));
        StringBuilder raw = new StringBuilder(ammunition).append('|').append(family).append('|')
                .append(model).append('|').append(ballistics).append('|').append(lifetime);
        for (StructureTemplate.StructureBlockInfo info : assembly.pieces()) {
            raw.append('|').append(info.pos()).append(':').append(info.state());
        }
        return new ShotState(family, model, solver, ballistics, Math.max(1, lifetime), muzzle,
                ammunition, digest(raw.toString()), legacy, reason);
    }

    private static String digest(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 12);
        } catch (NoSuchAlgorithmException impossible) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private static float privateFloat(Object owner, String fieldName, float fallback) {
        try {
            Field field = owner.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getFloat(owner);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return fallback;
        }
    }

    private static int fallbackTorpedoLifetime(AbstractCannonTorpedoProjectile projectile) {
        TorpedoProperties properties = CBCMSMunitionPropertiesHandlers.TORPEDO_PROJECTILE.getPropertiesOf(projectile);
        return properties == null ? 1 : Math.max(1, properties.lifetime());
    }

    private static int runtimeTorpedoLifetime(AbstractCannonTorpedoProjectile projectile, int fallback) {
        try {
            Method setChargePower = projectile.getClass().getMethod("setChargePower", float.class);
            setChargePower.invoke(projectile, 0.0F);
            Class<?> type = projectile.getClass();
            while (type != null) {
                try {
                    Field field = type.getDeclaredField("ageRemaining");
                    field.setAccessible(true);
                    return Math.max(1, field.getInt(projectile));
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return Math.max(1, fallback);
    }

    private record Assembly<T extends Entity>(
            T projectile, List<StructureTemplate.StructureBlockInfo> pieces
    ) {}
}
