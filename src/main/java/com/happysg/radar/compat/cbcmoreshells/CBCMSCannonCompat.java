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
import com.cainiao1053.cbcmoreshells.munitions.dual_cannon.DualCannonProjectileBlock;
import com.cainiao1053.cbcmoreshells.munitions.dual_cannon.shaolib.CBCMSDualCannonMunitionRegistry;
import com.cainiao1053.cbcmoreshells.munitions.dual_cannon.shaolib.DualCannonMunitionProperties;
import com.cainiao1053.cbcmoreshells.munitions.dual_cannon.shaolib.DualCannonLaunchProperties;
import com.cainiao1053.cbcmoreshells.munitions.racked_projectile.*;
import com.cainiao1053.cbcmoreshells.munitions.racked_projectile.he_bomb.HEBombProjectile;
import com.cainiao1053.cbcmoreshells.munitions.racked_projectile.aphe_bomb.APHEBombProjectile;
import com.happysg.radar.compat.cbc.CannonUtil;
import com.happysg.radar.debug.DiagnosticRecorder;
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
        if (cannon == null || level == null) return null;
        try {
            if (cannon instanceof MountedDualCannonContraption dual) return resolveDual(dual, level);
            if (cannon instanceof MountedProjectileRackContraption rack) return resolveProjectileRack(rack, level);
            if (cannon instanceof MountedBigCannonContraption big) return resolveBigCannonTorpedo(big, level);
            if (cannon instanceof MountedTorpedoTubeContraption tube) return resolveTorpedoTube(tube, level);
        } catch (Throwable failure) {
            // Compatibility must fail closed when an optional mod changes shape.
            DiagnosticRecorder.warn("cbc_more_shells",
                    "resolve_shot_state", "compatibility_resolution_failed",
                    failure, level, null, "cbcmoreshells",
                    "createbigcannons");
        }
        return null;
    }

    @Nullable
    private static ShotState resolveDual(MountedDualCannonContraption cannon, ServerLevel level) {
        List<DualAssembly> shots = collectDualAssemblies(cannon);

        if (shots.isEmpty()) {
            return null;
        }

        ShotState first = dualState(cannon, shots.getFirst(), level);
        if (first == null) {
            return null;
        }

        if (shots.size() == 2) {
            ShotState second = dualState(cannon, shots.get(1), level);
            if (second == null || !equivalentTrajectory(first, second)) {
                return null;
            }

            String fingerprint = digest(first.fingerprint() + "|" + second.fingerprint());
            return new ShotState(
                    first.family(),
                    first.projectileModel(),
                    first.solverMode(),
                    first.ballistics(),
                    first.lifetimeCapTicks(),
                    first.muzzlePolicy(),
                    first.ammunitionId() + "+" + second.ammunitionId(),
                    fingerprint,
                    first.legacyEligible(),
                    "dual_centerline_pair"
            );
        }

        return first;
    }

    @Nullable
    private static ShotState dualState(MountedDualCannonContraption cannon, DualAssembly assembly, ServerLevel level) {
        var entry = assembly.entry();
        DualCannonMunitionProperties properties = CBCMSDualCannonMunitionRegistry.properties(entry);

        if (properties == null || properties.ballistics() == null || properties.dualCannon() == null) {
            return null;
        }

        var physics = properties.ballistics();
        DualCannonLaunchProperties launch = properties.dualCannon();

        double speed = launch.initialVelocity();
        double gravity = physics.gravity();
        double drag = physics.drag();
        boolean quadraticDrag = physics.quadraticDrag();

        if (!Double.isFinite(speed) || speed <= 0.0 || !Double.isFinite(gravity) || !Double.isFinite(drag)) {
            return null;
        }

        int addedLifetime = cannon.getCannonMaterial() == null ? 0 : cannon.getCannonMaterial().properties().addedLifetime();
        float equipmentLifetime = privateFloat(cannon, "equipmentLifetimeModifier", 1.0F);

        int lifetime = entry.launchProfile().resolveLifetimeTicks(launch.baseLifetime(), addedLifetime, cannon.commandLifetimeModifier, equipmentLifetime);

        BallisticPropertiesComponent ballistics = new BallisticPropertiesComponent(gravity, drag, quadraticDrag, 0, 0, 0, 0);
        DimensionMunitionProperties dimension = DimensionMunitionPropertiesHandler.getProperties(level);

        ProjectileModel model = ProjectileModel.cbc(speed, gravity * dimension.gravityMultiplier(), drag, dimension.dragMultiplier(), quadraticDrag);

        String ammunitionId = String.valueOf(BuiltInRegistries.BLOCK.getKey(assembly.pieces().getFirst().state().getBlock()));
        StringBuilder fingerprintData = new StringBuilder().append(ammunitionId).append('|').append(model).append('|').append(ballistics).append('|').append(lifetime);

        for (StructureTemplate.StructureBlockInfo info : assembly.pieces()) {
            fingerprintData.append('|').append(info.pos()).append(':').append(info.state());
        }

        return new ShotState(
                Family.DUAL_CANNON,
                model,
                SolverMode.STANDARD_ASYNC,
                ballistics,
                lifetime,
                MuzzlePolicy.OUTSIDE_CENTER_BACKOFF_TWO,
                ammunitionId,
                digest(fingerprintData.toString()),
                true,
                "dual_centerline_shaolib"
        );
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

    @Nullable
    private static ShotState resolveTorpedoTube(MountedTorpedoTubeContraption cannon, ServerLevel level) {
        Direction direction = cannon.initialOrientation();
        BlockPos pos = cannon.getStartPos();

        if (direction == null || pos == null || cannon.presentBlockEntities == null) {
            return null;
        }

        List<StructureTemplate.StructureBlockInfo> pieces = new ArrayList<>();

        TorpedoProjectileBlock<?> root = null;
        float chargePower = 0.0F;
        int propellantCount = 0;

        while (cannon.presentBlockEntities.get(pos) instanceof ITorpedoTubeBlockEntity tubeBlock) {
            StructureTemplate.StructureBlockInfo info = tubeBlock.cannonBehavior().block();

            if (info == null) {
                return null;
            }

            Block block = info.state().getBlock();

            if (root == null && info.state().isAir()) {
                if (chargePower <= 0.0F) {
                    return null;
                }

                chargePower = Math.max(0.0F, chargePower - 1.0F);
                pos = pos.relative(direction);
                continue;
            }

            if (root == null && block instanceof BigCannonPropellantBlock propellant) {

                if (propellantCount == 0 && !propellant.canBeIgnited(info, direction)) {
                    return null;
                }

                chargePower += Math.max(0.0F, propellant.getChargePower(info));
                propellantCount++;
                pos = pos.relative(direction);
                continue;
            }

            if (root == null) {
                if (!(block instanceof TorpedoProjectileBlock<?> torpedo)) {
                    return null;
                }

                root = torpedo;
            }

            if (!root.isValidAddition(pieces, info, pieces.size(), direction)) {
                return null;
            }

            pieces.add(info);

            if (root.isComplete(pieces, direction)) {
                break;
            }

            pos = pos.relative(direction);
        }

        if (root == null || !root.isComplete(pieces, direction)) {
            return null;
        }

        AbstractCannonTorpedoProjectile projectile = root.getProjectile(level, List.copyOf(pieces));
        if (projectile == null) {
            return null;
        }

        BallisticPropertiesComponent ballistics = CannonUtil.getProjectileBallistics(projectile);
        float speed = projectile.getTorpedoSpeed();

        if (ballistics == null || !Float.isFinite(speed) || speed <= 0.0F) {
            return null;
        }

        chargePower += projectile.addedChargePower();

        int lifetime = runtimeTorpedoLifetime(projectile, fallbackTorpedoLifetime(projectile), chargePower) + 1;
        DimensionMunitionProperties dimension = DimensionMunitionPropertiesHandler.getProperties(level);

        ProjectileModel model = new CBCMSTorpedoProjectileModel(
                speed,
                ballistics.gravity() * dimension.gravityMultiplier(),
                ballistics.drag(),
                ballistics.isQuadraticDrag(),
                dimension.dragMultiplier(),
                projectile.getBuoyancyFactor(),
                projectile.getTorpedoSpeed(),
                lifetime
        );

        Assembly<AbstractCannonTorpedoProjectile> assembly = new Assembly<>(projectile, List.copyOf(pieces));

        return state(
                Family.TORPEDO_TUBE,
                model,
                SolverMode.CBCMS_SERVER,
                ballistics,
                lifetime,
                MuzzlePolicy.CBC,
                projectile,
                assembly,
                false,
                "torpedo_tube"
        );
    }

    @Nullable
    private static ShotState resolveProjectileRack(MountedProjectileRackContraption cannon, ServerLevel level) {
        Direction direction = cannon.initialOrientation();
        BlockPos pos = cannon.getStartPos();

        if (direction == null || pos == null || cannon.presentBlockEntities == null) {
            return null;
        }

        List<StructureTemplate.StructureBlockInfo> pieces = new ArrayList<>();

        RackedProjectileBlock<?> root = null;
        int propellantCount = 0;

        while (cannon.presentBlockEntities.get(pos) instanceof IProjectileRackBlockEntity rackBlock) {
            BigCannonBehavior behavior = (BigCannonBehavior) rackBlock.cannonBehavior();
            StructureTemplate.StructureBlockInfo info = behavior.block();

            if (info == null) {
                return null;
            }

            Block block = info.state().getBlock();

            if (root == null && info.state().isAir()) {
                if (propellantCount == 0) {
                    return null;
                }

                pos = pos.relative(direction);
                continue;
            }

            if (root == null && block instanceof BigCannonPropellantBlock propellant && !(block instanceof RackedProjectileBlock<?>)) {
                if (propellantCount == 0
                        && !propellant.canBeIgnited(info, direction)) {
                    return null;
                }

                propellantCount++;
                pos = pos.relative(direction);
                continue;
            }

            if (!(block instanceof RackedProjectileBlock<?> projectileBlock)) {
                return null;
            }

            if (root == null) {
                root = projectileBlock;
            }

            pieces.add(info);

            for (int i = 0; i < pieces.size(); i++) {
                StructureTemplate.StructureBlockInfo piece = pieces.get(i);

                if (!(piece.state().getBlock() instanceof RackedProjectileBlock<?> part) || !part.isValidAddition(pieces, piece, i, direction)) {
                    return null;
                }
            }

            if (root.isComplete(pieces, direction)) {
                break;
            }

            pos = pos.relative(direction);
        }

        if (root == null || !root.isComplete(pieces, direction)) {
            return null;
        }

        AbstractRackedProjectile projectile = root.getProjectile(level, List.copyOf(pieces));

        return resolveRackedProjectile(projectile, pieces, level);
    }

    @Nullable
    private static ShotState resolveRackedProjectile(@Nullable AbstractRackedProjectile projectile, List<StructureTemplate.StructureBlockInfo> pieces, ServerLevel level) {
        if (projectile == null) {
            return null;
        }

        float speed = projectile.initialVelocity();
        if (!Float.isFinite(speed) || speed <= 0.0F) {
            return null;
        }

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

                if (properties == null || properties.ballistics() == null) {
                    return null;
                }

                ballistics = properties.ballistics();
                lifetime = Math.max(1, properties.lifetime());
                thrustTicks = Math.max(0, properties.thrustTime());
                steadyVelocity = properties.steadyStateVel();

                boolean dual = projectile instanceof AbstractDualRackedRocketProjectile;
                family = dual ? Family.PROJECTILE_RACK_DUAL_ROCKET : Family.PROJECTILE_RACK_ROCKET;
                reason = dual ? "projectile_rack_dual_rocket" : "projectile_rack_powered_rocket";
            } else {
                var properties = CBCMSMunitionPropertiesHandlers.LOITERING_ROCKET.getPropertiesOf(projectile);

                if (properties == null || properties.ballistics() == null || properties.control() == null) {
                    return null;
                }

                ballistics = properties.ballistics();
                lifetime = Math.max(1, properties.lifetime());
                thrustTicks = Math.max(0, properties.thrustTime());
                steadyVelocity = properties.steadyStateVel();

                family = Family.PROJECTILE_RACK_LOITERING_ROCKET;
                reason = "projectile_rack_loitering_launch_only";
            }

            model = new CBCMSRackedRocketProjectileModel(
                    speed,
                    ballistics.gravity() * dimension.gravityMultiplier(),
                    ballistics.drag(),
                    thrustTicks,
                    steadyVelocity,
                    lifetime
            );
        } else if (projectile instanceof AbstractRackedTorpedoProjectile) {
            var properties = CBCMSMunitionPropertiesHandlers.RACKED_TORPEDO.getPropertiesOf(projectile);

            if (properties == null || properties.ballistics() == null) {
                return null;
            }

            ballistics = properties.ballistics();
            lifetime = Math.max(1, properties.lifetime());

            model = new CBCMSRackedTorpedoProjectileModel(
                    speed,
                    ballistics.gravity() * dimension.gravityMultiplier(),
                    ballistics.drag(),
                    properties.buoyancyFactor(),
                    properties.steadyStateVel(),
                    properties.waterDamp(),
                    lifetime
            );

            family = Family.PROJECTILE_RACK_TORPEDO;
            reason = "projectile_rack_torpedo";

        } else if (projectile instanceof AbstractBouncingProjectile || projectile instanceof AbstractDepthChargeProjectile) {

            boolean depthCharge = projectile instanceof AbstractDepthChargeProjectile;
            if (depthCharge) {
                var properties = CBCMSMunitionPropertiesHandlers.RACKED_SHRAPNEL_PROJECTILE.getPropertiesOf(projectile);

                if (properties == null || properties.ballistics() == null) {
                    return null;
                }

                ballistics = properties.ballistics();
                lifetime = Math.max(1, properties.lifetime());
            } else {
                var properties = CBCMSMunitionPropertiesHandlers.RACKED_PROJECTILE.getPropertiesOf(projectile);

                if (properties == null || properties.ballistics() == null) {
                    return null;
                }

                ballistics = properties.ballistics();
                lifetime = Math.max(1, properties.lifetime());
            }

            model = new CBCMSSpecialRackedBombProjectileModel(
                    speed,
                    ballistics.gravity() * dimension.gravityMultiplier(),
                    ballistics.drag(),
                    ballistics.isQuadraticDrag(),
                    dimension.dragMultiplier(),
                    depthCharge,
                    ballistics.deflection(),
                    lifetime
            );

            family = depthCharge ? Family.PROJECTILE_RACK_DEPTH_CHARGE : Family.PROJECTILE_RACK_BOUNCING_BOMB;
            reason = depthCharge ? "projectile_rack_depth_charge" : "projectile_rack_bouncing_bomb_pre_impact";
        } else if (projectile instanceof HEBombProjectile || projectile instanceof APHEBombProjectile) {
            var properties = CBCMSMunitionPropertiesHandlers.RACKED_PROJECTILE.getPropertiesOf(projectile);
            ballistics = CannonUtil.getProjectileBallistics(projectile);

            if (properties == null || ballistics == null) {
                return null;
            }

            lifetime = Math.max(1, properties.lifetime());

            model = ProjectileModel.cbc(
                    speed,
                    ballistics.gravity() * dimension.gravityMultiplier(),
                    ballistics.drag(),
                    dimension.dragMultiplier(),
                    ballistics.isQuadraticDrag()
            );

            family = Family.PROJECTILE_RACK_BOMB;
            solver = SolverMode.STANDARD_ASYNC;
            legacy = true;
            reason = "projectile_rack_bomb";

        } else {
            // Unsupported ammunition
            return null;
        }

        return state(
                family,
                model,
                solver,
                ballistics,
                lifetime,
                MuzzlePolicy.CBC,
                projectile,
                new Assembly<>(projectile, List.copyOf(pieces)),
                legacy,
                reason
        );
    }

    private static List<DualAssembly> collectDualAssemblies(MountedDualCannonContraption cannon) {
        List<DualAssembly> result = new ArrayList<>();

        Direction direction = cannon.initialOrientation();
        BlockPos position = cannon.getStartPos();

        if (direction == null || position == null || cannon.presentBlockEntities == null) {
            return result;
        }

        Block root = null;
        List<StructureTemplate.StructureBlockInfo> pieces = new ArrayList<>();

        while (result.size() < 2) {
            BlockEntity entity = cannon.presentBlockEntities.get(position);

            if (!(entity instanceof IDualCannonBlockEntity dualBlock)) {
                break;
            }

            BigCannonBehavior behavior = (BigCannonBehavior) dualBlock.cannonBehavior();
            StructureTemplate.StructureBlockInfo info = behavior.block();

            if (info == null) {
                return List.of();
            }

            Block block = info.state().getBlock();

            if (root != null && (info.state().isAir() || block instanceof BigCannonPropellantBlock)) {
                return List.of();
            }

            if (!info.state().isAir()
                    && !(block instanceof BigCannonPropellantBlock)) {

                if (root == null) {
                    root = block;
                }

                if (!(root instanceof DualCannonProjectileBlock projectile)) {
                    return List.of();
                }

                pieces.add(info);

                for (int i = 0; i < pieces.size(); i++) {
                    StructureTemplate.StructureBlockInfo part = pieces.get(i);

                    if (!(part.state().getBlock() instanceof DualCannonProjectileBlock partBlock) || !partBlock.isValidAddition(pieces, part, i, direction)) {
                        return List.of();
                    }
                }

                if (projectile.isComplete(pieces, direction)) {
                    var entry = CBCMSDualCannonMunitionRegistry.of(root);

                    if (entry == null) {
                        return List.of();
                    }

                    result.add(new DualAssembly(entry, List.copyOf(pieces)));

                    root = null;
                    pieces.clear();
                }
            }

            position = position.relative(direction);
        }

        return root == null ? result : List.of();
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

    private record DualAssembly(CBCMSDualCannonMunitionRegistry.Entry entry, List<StructureTemplate.StructureBlockInfo> pieces) {}
    private record Assembly<T extends Entity>(T projectile, List<StructureTemplate.StructureBlockInfo> pieces) {}
}
