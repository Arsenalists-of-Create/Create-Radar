package com.happysg.radar.compat.cbcmoreshells;

import com.cainiao1053.cbcmoreshells.cannon_control.contraption.MountedDualCannonContraption;
import com.cainiao1053.cbcmoreshells.cannon_control.contraption.MountedProjectileRackContraption;
import com.cainiao1053.cbcmoreshells.cannon_control.contraption.MountedTorpedoTubeContraption;
import com.cainiao1053.cbcmoreshells.cannons.dual_cannon.IDualCannonBlockEntity;
import com.cainiao1053.cbcmoreshells.cannons.projectile_rack.IProjectileRackBlockEntity;
import com.cainiao1053.cbcmoreshells.cannons.torpedo_tube.ITorpedoTubeBlockEntity;
import com.cainiao1053.cbcmoreshells.index.CBCMSMunitionPropertiesHandlers;
import com.cainiao1053.cbcmoreshells.munitions.big_cannon.AbstractCannonTorpedoProjectile;
import com.cainiao1053.cbcmoreshells.munitions.big_cannon.TorpedoProjectileBlock;
import com.cainiao1053.cbcmoreshells.munitions.big_cannon.config.TorpedoProperties;
import com.cainiao1053.cbcmoreshells.munitions.dual_cannon.AbstractDualCannonProjectile;
import com.cainiao1053.cbcmoreshells.munitions.dual_cannon.DualCannonProjectileBlock;
import com.cainiao1053.cbcmoreshells.munitions.dual_cannon.config.DualCannonProperties;
import com.cainiao1053.cbcmoreshells.munitions.racked_projectile.*;
import com.cainiao1053.cbcmoreshells.munitions.racked_projectile.he_bomb.HEBombProjectile;
import com.cainiao1053.cbcmoreshells.munitions.racked_projectile.aphe_bomb.APHEBombProjectile;
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
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption;
import rbasamoyai.createbigcannons.cannons.big_cannons.BigCannonBehavior;
import rbasamoyai.createbigcannons.cannons.big_cannons.IBigCannonBlockEntity;
import rbasamoyai.createbigcannons.munitions.big_cannon.propellant.BigCannonPropellantBlock;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionProperties;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionPropertiesHandler;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;

public final class CBCMSCannonCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    public enum Family {
        DUAL_CANNON, BIG_CANNON_TORPEDO, TORPEDO_TUBE, PROJECTILE_RACK_BOMB, PROJECTILE_RACK_ROCKET,
        PROJECTILE_RACK_DUAL_ROCKET, PROJECTILE_RACK_TORPEDO, PROJECTILE_RACK_LOITERING_ROCKET,
        PROJECTILE_RACK_BOUNCING_BOMB, PROJECTILE_RACK_DEPTH_CHARGE
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

    public static boolean isCBCMSTorpedoTube(@Nullable AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedTorpedoTubeContraption;
    }

    public static boolean isCBCMSProjectileRack(@Nullable AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedProjectileRackContraption;
    }

    public static boolean isCBCMSProjectileRackBarrel(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof IProjectileRackBlockEntity;
    }

    public static boolean isCBCMSTorpedoTubeBarrel(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof ITorpedoTubeBlockEntity;
    }

    public static boolean isCBCMSMountedWeapon(@Nullable AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedDualCannonContraption || cannon instanceof MountedProjectileRackContraption || cannon instanceof MountedTorpedoTubeContraption;
    }

    public static boolean isCBCMSBarrel(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof IDualCannonBlockEntity;
    }

    @Nullable
    public static ShotState resolveShotState(AbstractMountedCannonContraption cannon, ServerLevel level) {
        if (cannon == null || level == null || !level.getServer().isSameThread()) {
            return null;
        }
        try {
            if (cannon instanceof MountedDualCannonContraption dual) return resolveDual(dual, level);
            if (cannon instanceof MountedProjectileRackContraption rack) return resolveProjectileRack(rack, level);
            if (cannon instanceof MountedBigCannonContraption big) return resolveBigCannonTorpedo(big, level);
            if (cannon instanceof MountedTorpedoTubeContraption tube) return resolveTorpedoTube(tube, level);
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("Failed to resolve CBC:MS shot state for {}", cannon.getClass().getName(), failure);
        }
        return null;
    }

    @Nullable
    private static ShotState resolveDual(MountedDualCannonContraption cannon, ServerLevel level) {
        List<DualAssembly> shots = collectDualAssemblies(cannon);
        if (shots.isEmpty()) return null;

        ShotState first = dualState(cannon, shots.get(0), level);
        if (first == null) return null;

        if (shots.size() == 2) {
            ShotState second = dualState(cannon, shots.get(1), level);
            if (second == null || !equivalentTrajectory(first, second)) return null;
            return new ShotState(
                    first.family(), first.projectileModel(), first.solverMode(), first.ballistics(),
                    first.lifetimeCapTicks(), first.muzzlePolicy(), first.ammunitionId() + "+" + second.ammunitionId(),
                    digest(first.fingerprint() + "|" + second.fingerprint()),
                    first.legacyEligible(), "dual_centerline_pair");
        }
        return first;
    }

    @Nullable
    private static ShotState dualState(MountedDualCannonContraption cannon, DualAssembly assembly, ServerLevel level) {
        if (assembly.pieces().isEmpty()) return null;
        if (!(assembly.pieces().get(0).state().getBlock() instanceof DualCannonProjectileBlock<?> block)) return null;

        AbstractDualCannonProjectile projectile = block.getProjectile(level, assembly.pieces());
        if (projectile == null) return null;

        DualCannonProperties properties = CBCMSMunitionPropertiesHandlers.DUAL_CANNON_PROPERTIES.getPropertiesOf(projectile);
        if (properties == null || properties.ballistics() == null) return null;
        BallisticPropertiesComponent ballistics = properties.ballistics();
        double speed = projectile.getInitVel();
        double gravity = ballistics.gravity();
        double drag = ballistics.drag();
        boolean quadratic = ballistics.isQuadraticDrag();
        if (!Double.isFinite(speed) || speed <= 0.0 || !Double.isFinite(gravity) || !Double.isFinite(drag)) return null;

        int addedLifetime = cannon.getCannonMaterial() == null ? 0 : cannon.getCannonMaterial().properties().addedLifetime();
        float equipmentLifetime = privateFloat(cannon, "equipmentLifetimeModifier", 1.0F);
        int lifetime = Math.max(1, (int) ((projectile.getLifetime() + addedLifetime) * cannon.commandLifetimeModifier * equipmentLifetime));
        ProjectileModel model = new CBCMSDualCannonProjectileModel(speed, gravity, drag, quadratic, lifetime);

        return state(Family.DUAL_CANNON, model, SolverMode.STANDARD_ASYNC, ballistics, lifetime,
                MuzzlePolicy.OUTSIDE_CENTER_BACKOFF_TWO, projectile,
                new Assembly<>(projectile, assembly.pieces()), false, "dual_centerline_loaded");
    }

    @Nullable
    private static ShotState resolveBigCannonTorpedo(MountedBigCannonContraption cannon, ServerLevel level) {
        Direction direction = cannon.initialOrientation();
        BlockPos pos = cannon.getStartPos();
        if (direction == null || pos == null || cannon.presentBlockEntities == null) return null;
        float speed = 0.0F;
        List<StructureTemplate.StructureBlockInfo> pieces = new ArrayList<>();
        TorpedoProjectileBlock<?> root = null;
        int limit = Math.max(1, cannon.getBlocks().size() + 1);

        for (int i = 0; i < limit && cannon.presentBlockEntities.get(pos) instanceof IBigCannonBlockEntity be; i++) {
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
            } else {
                return null;
            }
            if (root != null && root.isComplete(pieces, direction)) break;
            pos = pos.relative(direction);
        }

        if (root == null || !root.isComplete(pieces, direction)) return null;
        AbstractCannonTorpedoProjectile projectile = root.getProjectile(level, pieces);
        if (projectile == null) return null;
        TorpedoProperties properties = CBCMSMunitionPropertiesHandlers.TORPEDO_PROJECTILE.getPropertiesOf(projectile);
        if (properties == null || properties.ballistics() == null) return null;
        BallisticPropertiesComponent ballistics = properties.ballistics();
        int lifetime = runtimeTorpedoLifetime(projectile, Math.max(1, properties.lifetime())) + 1;
        speed += projectile.addedChargePower();
        DimensionMunitionProperties dimension = DimensionMunitionPropertiesHandler.getProperties(level);
        ProjectileModel model = new CBCMSTorpedoProjectileModel(speed,
                ballistics.gravity() * dimension.gravityMultiplier(), ballistics.drag(),
                ballistics.isQuadraticDrag(), dimension.dragMultiplier(), projectile.getBuoyancyFactor(),
                projectile.getTorpedoSpeed(), lifetime);
        return state(Family.BIG_CANNON_TORPEDO, model, SolverMode.CBCMS_SERVER, ballistics, lifetime, MuzzlePolicy.CBC, projectile, new Assembly<>(projectile, List.copyOf(pieces)), false, "big_cannon_torpedo");
    }

    @Nullable
    private static ShotState resolveTorpedoTube(MountedTorpedoTubeContraption cannon, ServerLevel level) {
        Direction direction = cannon.initialOrientation();
        BlockPos pos = cannon.getStartPos();
        if (direction == null || pos == null || cannon.presentBlockEntities == null) return null;

        List<StructureTemplate.StructureBlockInfo> pieces = new ArrayList<>();
        TorpedoProjectileBlock<?> root = null;
        float chargePower = 0.0F;
        int propellantCount = 0;
        int limit = Math.max(1, cannon.getBlocks().size() + 1);

        for (int i = 0; i < limit && cannon.presentBlockEntities.get(pos) instanceof ITorpedoTubeBlockEntity tube; i++) {
            StructureTemplate.StructureBlockInfo info = tube.cannonBehavior().block();
            if (info == null) return null;
            Block block = info.state().getBlock();

            if (root == null && info.state().isAir()) {
                if (chargePower <= 0.0F) return null;
                chargePower = Math.max(0.0F, chargePower - 1.0F);
                pos = pos.relative(direction);
                continue;
            }
            if (root == null && block instanceof BigCannonPropellantBlock propellant) {
                if (propellantCount == 0 && !propellant.canBeIgnited(info, direction)) return null;
                chargePower += Math.max(0.0F, propellant.getChargePower(info));
                propellantCount++;
                pos = pos.relative(direction);
                continue;
            }
            if (root == null) {
                if (!(block instanceof TorpedoProjectileBlock<?> torpedo)) return null;
                root = torpedo;
            }
            if (!root.isValidAddition(pieces, info, pieces.size(), direction)) return null;
            pieces.add(info);
            if (root.isComplete(pieces, direction)) break;
            pos = pos.relative(direction);
        }

        if (root == null || !root.isComplete(pieces, direction)) return null;
        AbstractCannonTorpedoProjectile projectile = root.getProjectile(level, List.copyOf(pieces));
        if (projectile == null) return null;
        TorpedoProperties properties = CBCMSMunitionPropertiesHandlers.TORPEDO_PROJECTILE.getPropertiesOf(projectile);
        if (properties == null || properties.ballistics() == null) return null;
        BallisticPropertiesComponent ballistics = properties.ballistics();
        float speed = projectile.getTorpedoSpeed();
        if (!Float.isFinite(speed) || speed <= 0.0F) return null;
        chargePower += projectile.addedChargePower();
        int lifetime = runtimeTorpedoLifetime(projectile, Math.max(1, properties.lifetime()), chargePower) + 1;
        DimensionMunitionProperties dimension = DimensionMunitionPropertiesHandler.getProperties(level);
        ProjectileModel model = new CBCMSTorpedoProjectileModel(speed,
                ballistics.gravity() * dimension.gravityMultiplier(), ballistics.drag(),
                ballistics.isQuadraticDrag(), dimension.dragMultiplier(), projectile.getBuoyancyFactor(),
                projectile.getTorpedoSpeed(), lifetime);
        return state(Family.TORPEDO_TUBE, model, SolverMode.CBCMS_SERVER, ballistics, lifetime, MuzzlePolicy.CBC, projectile, new Assembly<>(projectile, List.copyOf(pieces)), false, "torpedo_tube");
    }

    @Nullable
    private static ShotState resolveProjectileRack(MountedProjectileRackContraption cannon, ServerLevel level) {
        Direction direction = cannon.initialOrientation();
        BlockPos pos = cannon.getStartPos();
        if (direction == null || pos == null || cannon.presentBlockEntities == null) return null;

        List<StructureTemplate.StructureBlockInfo> pieces = new ArrayList<>();
        RackedProjectileBlock<?> root = null;
        int propellantCount = 0;
        int limit = Math.max(1, cannon.getBlocks().size() + 1);

        for (int i = 0; i < limit && cannon.presentBlockEntities.get(pos) instanceof IProjectileRackBlockEntity rack; i++) {
            BigCannonBehavior behavior = (BigCannonBehavior) rack.cannonBehavior();
            StructureTemplate.StructureBlockInfo info = behavior.block();
            if (info == null) return null;
            Block block = info.state().getBlock();
            if (root == null && info.state().isAir()) {
                if (propellantCount == 0) return null;
                pos = pos.relative(direction);
                continue;
            }
            if (root == null && block instanceof BigCannonPropellantBlock propellant && !(block instanceof RackedProjectileBlock<?>)) {
                if (propellantCount == 0 && !propellant.canBeIgnited(info, direction)) return null;
                propellantCount++;
                pos = pos.relative(direction);
                continue;
            }
            if (!(block instanceof RackedProjectileBlock<?> part)) return null;
            if (root == null) root = part;
            pieces.add(info);
            for (int j = 0; j < pieces.size(); j++) {
                StructureTemplate.StructureBlockInfo piece = pieces.get(j);
                if (!(piece.state().getBlock() instanceof RackedProjectileBlock<?> p) || !p.isValidAddition(pieces, piece, j, direction)) return null;
            }
            if (root.isComplete(pieces, direction)) break;
            pos = pos.relative(direction);
        }
        if (root == null || !root.isComplete(pieces, direction)) return null;
        AbstractRackedProjectile projectile = root.getProjectile(level, List.copyOf(pieces));
        return resolveRackedProjectile(projectile, pieces, level);
    }

    @Nullable
    private static ShotState resolveRackedProjectile(@Nullable AbstractRackedProjectile projectile, List<StructureTemplate.StructureBlockInfo> pieces, ServerLevel level) {
        if (projectile == null) return null;
        float speed = projectile.initialVelocity();
        if (!Float.isFinite(speed) || speed <= 0.0F) return null;

        DimensionMunitionProperties dimension = DimensionMunitionPropertiesHandler.getProperties(level);
        BallisticPropertiesComponent ballistics;
        ProjectileModel model;
        Family family;
        SolverMode solver = SolverMode.CBCMS_SERVER;
        int lifetime;
        boolean legacy = false;
        String reason;

        boolean regularRocket = projectile instanceof AbstractRackedRocketProjectile || projectile instanceof AbstractDualRackedRocketProjectile;
        if (regularRocket || projectile instanceof AbstractRackedLoiteringRocketProjectile) {
            int thrustTicks;
            double steadyVelocity;
            if (regularRocket) {
                var properties = CBCMSMunitionPropertiesHandlers.RACKED_ROCKET.getPropertiesOf(projectile);
                if (properties == null || properties.ballistics() == null) return null;
                ballistics = properties.ballistics();
                lifetime = Math.max(1, properties.lifetime());
                thrustTicks = Math.max(0, properties.thrustTime());
                steadyVelocity = properties.steadyStateVel();
                boolean dual = projectile instanceof AbstractDualRackedRocketProjectile;
                family = dual ? Family.PROJECTILE_RACK_DUAL_ROCKET : Family.PROJECTILE_RACK_ROCKET;
                reason = dual ? "projectile_rack_dual_rocket" : "projectile_rack_powered_rocket";
            } else {
                var properties = CBCMSMunitionPropertiesHandlers.LOITERING_ROCKET.getPropertiesOf(projectile);
                if (properties == null || properties.ballistics() == null || properties.control() == null) return null;
                ballistics = properties.ballistics();
                lifetime = Math.max(1, properties.lifetime());
                thrustTicks = Math.max(0, properties.thrustTime());
                steadyVelocity = properties.steadyStateVel();
                family = Family.PROJECTILE_RACK_LOITERING_ROCKET;
                reason = "projectile_rack_loitering_launch_only";
            }
            model = new CBCMSRackedRocketProjectileModel(speed, ballistics.gravity() * dimension.gravityMultiplier(), ballistics.drag(), thrustTicks, steadyVelocity, lifetime);
        } else if (projectile instanceof AbstractRackedTorpedoProjectile) {
            var properties = CBCMSMunitionPropertiesHandlers.RACKED_TORPEDO.getPropertiesOf(projectile);
            if (properties == null || properties.ballistics() == null) return null;
            ballistics = properties.ballistics();
            lifetime = Math.max(1, properties.lifetime());
            model = new CBCMSRackedTorpedoProjectileModel(speed,
                    ballistics.gravity() * dimension.gravityMultiplier(), ballistics.drag(),
                    properties.buoyancyFactor(), properties.steadyStateVel(), properties.waterDamp(), lifetime);
            family = Family.PROJECTILE_RACK_TORPEDO;
            reason = "projectile_rack_torpedo";
        } else if (projectile instanceof AbstractBouncingProjectile || projectile instanceof AbstractDepthChargeProjectile) {
            boolean depthCharge = projectile instanceof AbstractDepthChargeProjectile;
            if (depthCharge) {
                var properties = CBCMSMunitionPropertiesHandlers.RACKED_SHRAPNEL_PROJECTILE.getPropertiesOf(projectile);
                if (properties == null || properties.ballistics() == null) return null;
                ballistics = properties.ballistics();
                lifetime = Math.max(1, properties.lifetime());
            } else {
                var properties = CBCMSMunitionPropertiesHandlers.RACKED_PROJECTILE.getPropertiesOf(projectile);
                if (properties == null || properties.ballistics() == null) return null;
                ballistics = properties.ballistics();
                lifetime = Math.max(1, properties.lifetime());
            }
            model = new CBCMSSpecialRackedBombProjectileModel(speed,
                    ballistics.gravity() * dimension.gravityMultiplier(), ballistics.drag(),
                    ballistics.isQuadraticDrag(), dimension.dragMultiplier(),
                    depthCharge, ballistics.deflection(), lifetime);
            family = depthCharge ? Family.PROJECTILE_RACK_DEPTH_CHARGE : Family.PROJECTILE_RACK_BOUNCING_BOMB;
            reason = depthCharge ? "projectile_rack_depth_charge" : "projectile_rack_bouncing_bomb_pre_impact";
        } else if (projectile instanceof HEBombProjectile || projectile instanceof APHEBombProjectile) {
            var properties = CBCMSMunitionPropertiesHandlers.RACKED_PROJECTILE.getPropertiesOf(projectile);
            if (properties == null || properties.ballistics() == null) return null;
            ballistics = properties.ballistics();
            lifetime = Math.max(1, properties.lifetime());
            model = ProjectileModel.cbc(speed,
                    ballistics.gravity() * dimension.gravityMultiplier(), ballistics.drag(),
                    dimension.dragMultiplier(), ballistics.isQuadraticDrag());
            family = Family.PROJECTILE_RACK_BOMB;
            solver = SolverMode.STANDARD_ASYNC;
            legacy = true;
            reason = "projectile_rack_bomb";
        } else {
            return null;
        }
        return state(family, model, solver, ballistics, lifetime, MuzzlePolicy.CBC, projectile, new Assembly<>(projectile, List.copyOf(pieces)), legacy, reason);
    }

    private static List<DualAssembly> collectDualAssemblies(MountedDualCannonContraption cannon) {
        List<DualAssembly> result = new ArrayList<>();
        Direction direction = cannon.initialOrientation();
        BlockPos position = cannon.getStartPos();
        if (direction == null || position == null || cannon.presentBlockEntities == null) return result;
        Block root = null;
        List<StructureTemplate.StructureBlockInfo> pieces = new ArrayList<>();
        int limit = Math.max(1, cannon.getBlocks().size() + 1);

        for (int i = 0; i < limit && result.size() < 2; i++) {
            BlockEntity entity = cannon.presentBlockEntities.get(position);
            if (!(entity instanceof IDualCannonBlockEntity dualBlock)) break;
            BigCannonBehavior behavior = (BigCannonBehavior) dualBlock.cannonBehavior();
            StructureTemplate.StructureBlockInfo info = behavior.block();
            if (info == null) return List.of();
            Block block = info.state().getBlock();
            if (root != null && (info.state().isAir() || block instanceof BigCannonPropellantBlock)) return List.of();

            if (!info.state().isAir() && !(block instanceof BigCannonPropellantBlock)) {
                if (root == null) root = block;
                if (!(root instanceof DualCannonProjectileBlock<?> projectile)) return List.of();
                pieces.add(info);
                for (int j = 0; j < pieces.size(); j++) {
                    StructureTemplate.StructureBlockInfo part = pieces.get(j);
                    if (!(part.state().getBlock() instanceof DualCannonProjectileBlock<?> partBlock) || !partBlock.isValidAddition(pieces, part, j, direction)) return List.of();
                }
                if (projectile.isComplete(pieces, direction)) {
                    result.add(new DualAssembly(List.copyOf(pieces)));
                    root = null;
                    pieces.clear();
                }
            }
            position = position.relative(direction);
        }
        return root == null ? result : List.of();
    }

    private static boolean equivalentTrajectory(ShotState first, ShotState second) {
        return first.lifetimeCapTicks() == second.lifetimeCapTicks() && first.projectileModel().equals(second.projectileModel()) && first.ballistics().equals(second.ballistics());
    }

    private static <T extends Entity> ShotState state(Family family, ProjectileModel model, SolverMode solver, BallisticPropertiesComponent ballistics, int lifetime, MuzzlePolicy muzzle, T projectile, Assembly<T> assembly, boolean legacy, String reason) {
        String ammunition = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()));
        StringBuilder raw = new StringBuilder(ammunition).append('|').append(family).append('|').append(model).append('|').append(ballistics).append('|').append(lifetime);
        for (StructureTemplate.StructureBlockInfo info : assembly.pieces()) {
            raw.append('|').append(info.pos()).append(':').append(info.state());
        }
        return new ShotState(family, model, solver, ballistics, Math.max(1, lifetime), muzzle, ammunition, digest(raw.toString()), legacy, reason);
    }

    private static String digest(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
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

    private static int runtimeTorpedoLifetime(AbstractCannonTorpedoProjectile projectile, int fallback) {
        return runtimeTorpedoLifetime(projectile, fallback, 0.0F);
    }

    private static int runtimeTorpedoLifetime(AbstractCannonTorpedoProjectile projectile, int fallback, float chargePower) {
        try {
            Method setChargePower = projectile.getClass().getMethod("setChargePower", float.class);
            setChargePower.invoke(projectile, chargePower);
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
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
        return Math.max(1, fallback);
    }

    private record DualAssembly(List<StructureTemplate.StructureBlockInfo> pieces) {}
    private record Assembly<T extends Entity>(T projectile, List<StructureTemplate.StructureBlockInfo> pieces) {}
}