package com.happysg.radar.compat.cbc;

import com.happysg.radar.api.weapon.WeaponShotAdapterRegistry;
import com.happysg.radar.api.weapon.WeaponShotContext;
import com.happysg.radar.api.weapon.WeaponShotProfile;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.targeting.ObstructionChecker;
import com.happysg.radar.targeting.ObstructionResult;
import com.happysg.radar.targeting.PitchConstraint;
import com.happysg.radar.targeting.ProjectileModel;
import com.happysg.radar.targeting.ProjectileModelBridge;
import com.happysg.radar.targeting.ProjectileSimulator;
import com.happysg.radar.targeting.TargetPredictor;
import com.happysg.radar.targeting.TargetingComputer;
import com.happysg.radar.targeting.TargetingResult;
import com.happysg.radar.targeting.TargetingSnapshot;
import com.happysg.radar.targeting.Trajectory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedAutocannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionProperties;
import rbasamoyai.createbigcannons.munitions.config.DimensionMunitionPropertiesHandler;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class CbcSimulatedAim {
    private static final int ASYNC_TARGETING_FIRE_FRESH_TICKS = 4;
    private static final int ASYNC_TARGETING_TASK_TIMEOUT_TICKS = 100;
    private static final int ASYNC_TARGETING_REFRESH_TICKS = 2;
    private static final int TARGETING_EXECUTOR_THREADS = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 1));
    private static final int TARGETING_EXECUTOR_QUEUE_CAPACITY = 16;
    private static final double TARGET_MOVE_SQR = 0.09;
    private static final double VELOCITY_CHANGE_SQR = 0.0004;
    private static final double MIN_CONFIDENCE = 0.05;
    private static final ThreadPoolExecutor TARGETING_EXECUTOR = createTargetingExecutor();

    private final TargetingComputer asyncTargetingComputer = new TargetingComputer(
            null, new ProjectileSimulator(), null, ObstructionChecker.NONE);
    private final ProjectileSimulator validationProjectileSimulator = new ProjectileSimulator();
    private final ObstructionChecker mainThreadObstructionChecker = new ObstructionChecker();
    private final TargetPredictor targetPredictor = new TargetPredictor();

    @Nullable private CompletableFuture<TargetingResult> pendingFuture;
    @Nullable private SolveRequest pendingRequest;
    @Nullable private SolveRequest acceptedRequest;
    @Nullable private TargetingResult acceptedResult;
    private long lastSubmissionTick = Long.MIN_VALUE;

    public static boolean supports(AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedBigCannonContraption
                || cannon instanceof MountedAutocannonContraption
                || Mods.CBC_AT.isLoaded() && com.happysg.radar.compat.cbc_at.CBCATCannonCompat.isCBCATCannon(cannon)
                || Mods.CBCMODERNWARFARE.isLoaded() && com.happysg.radar.compat.cbcmw.CBCMWCannonCompat.isCBCMWCannon(cannon);
    }

    public void reset() {
        if (pendingFuture != null) {
            pendingFuture.cancel(true);
        }
        pendingFuture = null;
        pendingRequest = null;
        acceptedRequest = null;
        acceptedResult = null;
        lastSubmissionTick = Long.MIN_VALUE;
    }

    public TargetingResult solve(ServerLevel level, CannonMountBlockEntity mount,
                                AbstractMountedCannonContraption cannon,
                                Vec3 target, Vec3 targetVelocity, Vec3 targetAcceleration,
                                Vec3 shooterVelocity, @Nullable AABB targetBounds,
                                @Nullable AutoPitchControllerBlockEntity pitchController) {
        if (level == null || mount == null || cannon == null || !finite(target)
                || !supports(cannon) || mount.getContraption() == null
                || mount.getContraption().getContraption() != cannon
                || !level.getServer().isSameThread()) {
            reset();
            return TargetingResult.noShot("missing mounted cannon");
        }

        Vec3 velocity = finite(targetVelocity) ? targetVelocity : Vec3.ZERO;
        Vec3 acceleration = finite(targetAcceleration) ? targetAcceleration : Vec3.ZERO;
        Vec3 inherited = finite(shooterVelocity) ? shooterVelocity : Vec3.ZERO;
        long now = level.getGameTime();
        PitchOrientedContraptionEntity entity = mount.getContraption();
        boolean addon = Mods.CBC_AT.isLoaded() && com.happysg.radar.compat.cbc_at.CBCATCannonCompat.isCBCATCannon(cannon)
                || Mods.CBCMODERNWARFARE.isLoaded() && com.happysg.radar.compat.cbcmw.CBCMWCannonCompat.isCBCMWCannon(cannon);
        if (addon && CBCMuzzleUtil.getMuzzleExitLocal(cannon) == null) {
            reset();
            return TargetingResult.noShot("missing addon muzzle");
        }

        Vec3 muzzle = CBCMuzzleUtil.getCBCSpawnAnchorWorld(entity);
        Vec3 pivot = entity.toGlobalVector(Vec3.atCenterOf(BlockPos.ZERO), 0);
        Vec3 forward = CBCMuzzleUtil.getForwardWorld(entity);
        if (!finite(muzzle) || !finite(pivot) || !finite(forward) || forward.lengthSqr() < 1.0e-12) {
            reset();
            return TargetingResult.noShot("invalid muzzle geometry");
        }

        double muzzleOffset = Math.max(0.0, muzzle.subtract(pivot).dot(forward));
        ProjectileModel model;
        int flightTicks;
        String fingerprint;
        WeaponShotProfile profile;
        try {
            profile = addon || cannon instanceof MountedBigCannonContraption
                    ? WeaponShotAdapterRegistry.resolve(new WeaponShotContext(level, mount.getBlockPos(), null, null))
                    : null;
        } catch (RuntimeException | LinkageError exception) {
            reset();
            return TargetingResult.noShot("shot profile unavailable");
        }
        if (addon && profile == null) {
            reset();
            return TargetingResult.noShot("addon shot profile unavailable");
        }
        if (profile != null) {
            if (profile.aimMode() != WeaponShotProfile.AimMode.BALLISTIC
                    || profile.projectileModel() == null || profile.maxFlightTicks() <= 0) {
                reset();
                return TargetingResult.noShot("shot profile not ballistic");
            }
            model = ProjectileModelBridge.toInternal(profile.projectileModel());
            flightTicks = profile.maxFlightTicks();
            fingerprint = profile.fingerprint();
        } else {
            float speed = CannonUtil.getInitialVelocity(cannon, level);
            BallisticPropertiesComponent ballistics = CannonUtil.getBallistics(cannon, level);
            if (!Float.isFinite(speed) || speed <= 0.0f || ballistics == null) {
                reset();
                return TargetingResult.noShot("unresolved ballistics");
            }
            DimensionMunitionProperties dimension = DimensionMunitionPropertiesHandler.getProperties(level);
            double gravity = ballistics.gravity() * dimension.gravityMultiplier();
            double drag = ballistics.drag();
            double dragDensity = dimension.dragMultiplier();
            if (!Double.isFinite(gravity) || !Double.isFinite(drag) || !Double.isFinite(dragDensity)) {
                reset();
                return TargetingResult.noShot("invalid ballistics");
            }
            flightTicks = cannon instanceof MountedAutocannonContraption
                    ? Math.max(1, CannonUtil.getAutocannonLifetimeTicks(cannon))
                    : Math.max(160, Math.min(2400, (int) Math.ceil(muzzle.distanceTo(target) / speed * 3.0 + 120.0)));
            model = ProjectileModel.cbc(speed, gravity, drag, dragDensity, ballistics.isQuadraticDrag());
            fingerprint = "cbc:" + speed + ":" + ballistics + ":" + dragDensity;
        }
        if (model == null || !Double.isFinite(model.muzzleSpeed()) || model.muzzleSpeed() <= 0.0
                || !Double.isFinite(model.gravity()) || !Double.isFinite(model.drag())
                || !Double.isFinite(model.dragDensity())) {
            reset();
            return TargetingResult.noShot("invalid projectile model");
        }

        double minPitch = pitchController == null ? -89.0 : pitchController.getMinAngleDeg();
        double maxPitch = pitchController == null ? 89.0 : pitchController.getMaxAngleDeg();
        CannonMountContext mountContext = CannonMountContext.of(mount);
        CannonMountContext.PitchLimits mountLimits = mountContext == null ? null : mountContext.pitchLimits();
        if (mountLimits != null) {
            minPitch = Math.max(minPitch, mountLimits.minDegrees());
            maxPitch = Math.min(maxPitch, mountLimits.maxDegrees());
        }
        if (!Double.isFinite(minPitch) || !Double.isFinite(maxPitch) || minPitch > maxPitch) {
            reset();
            return TargetingResult.noShot("invalid pitch limits");
        }

        TargetingSnapshot.Builder builder = TargetingSnapshot.builder(level)
                .muzzlePosition(muzzle)
                .launchPivotPosition(profile == null && muzzleOffset > 0.0 ? pivot : null)
                .muzzleForwardOffset(profile == null ? muzzleOffset : 0.0)
                .inheritedVelocity(inherited)
                .targetPosition(target)
                .targetVelocity(velocity)
                .targetAcceleration(acceleration)
                .targetAabb(targetBounds)
                .projectileSpeed(model.muzzleSpeed())
                .gravity(model.gravity())
                .drag(model.drag())
                .dragDensity(model.dragDensity())
                .quadraticDrag(model.quadraticDrag())
                .cbcPhysics(model.cbcPhysics())
                .maxFlightTicks(flightTicks)
                .pitchConstraint(PitchConstraint.world(minPitch, maxPitch))
                .preferHighArc(pitchController != null && pitchController.isArtillery());
        if (acceptedResult != null && acceptedResult.hasShot() && acceptedRequest != null && acceptedRequest.cannon() == cannon && Objects.equals(acceptedRequest.fingerprint(), fingerprint)) {
            builder.preferredYawDeg(acceptedResult.desiredYawDeg());
            builder.preferredPitchDeg(acceptedResult.desiredPitchDeg());
        }
        TargetingSnapshot snapshot = builder.build();
        SolveRequest current = new SolveRequest(level, cannon, fingerprint, snapshot, model, now);

        if (pendingFuture != null && pendingFuture.isDone()) {
            CompletableFuture<TargetingResult> completed = pendingFuture;
            SolveRequest completedRequest = pendingRequest;
            pendingFuture = null;
            pendingRequest = null;
            try {
                TargetingResult result = completed.getNow(null);
                if (completedRequest != null && result != null && compatible(current, completedRequest)
                        && now - completedRequest.tick() <= ASYNC_TARGETING_FIRE_FRESH_TICKS
                        && validate(level, completedRequest, result)) {
                    acceptedRequest = completedRequest;
                    acceptedResult = result;
                }
            } catch (RuntimeException ignored) {
                acceptedRequest = null;
                acceptedResult = null;
            }
        }

        if (pendingFuture != null && pendingRequest != null && (now - pendingRequest.tick() > ASYNC_TARGETING_TASK_TIMEOUT_TICKS || !compatible(current, pendingRequest))) {
            pendingFuture.cancel(true);
            pendingFuture = null;
            pendingRequest = null;
        }

        if (pendingFuture == null && (lastSubmissionTick == Long.MIN_VALUE || now - lastSubmissionTick >= ASYNC_TARGETING_REFRESH_TICKS)) {
            try {
                TargetingSnapshot workerSnapshot = snapshot.withoutLiveLevel();
                pendingRequest = current;
                pendingFuture = submitTargetingTask(() -> asyncTargetingComputer.solve(workerSnapshot, model));
                lastSubmissionTick = now;
            } catch (RejectedExecutionException rejected) {
                pendingRequest = null;
                pendingFuture = null;
            }
        }

        if (acceptedResult != null && acceptedRequest != null && now - acceptedRequest.tick() <= ASYNC_TARGETING_FIRE_FRESH_TICKS && compatible(current, acceptedRequest)) {
            return acceptedResult;
        }
        return TargetingResult.noShot("awaiting validated targeting solution");
    }

    private boolean validate(ServerLevel level, SolveRequest request, TargetingResult result) {
        if (!result.valid() || !result.hasShot() || result.aimSolution() == null || result.confidence() < MIN_CONFIDENCE) {
            return false;
        }
        Vec3 direction = result.aimSolution().aimDirection();
        int ticks = result.predictedFlightTicks();
        TargetingSnapshot snapshot = request.snapshot();
        ProjectileModel model = request.model();
        if (!finite(direction) || direction.lengthSqr() < 1.0e-12 || ticks <= 0 || ticks > snapshot.maxFlightTicks()) {
            return false;
        }
        ProjectileSimulator.SimulationResult trajectory;
        try {
            trajectory = validationProjectileSimulator.simulate(
                    snapshot.launchPosition(direction), direction, snapshot.inheritedVelocityAt(direction),
                    model, Math.min(snapshot.maxFlightTicks(), ticks + 1), level);
        } catch (RuntimeException exception) {
            return false;
        }
        if (trajectory == null || trajectory.samples().size() < 2) {
            return false;
        }

        Vec3 segmentImpact = null;
        int segmentImpactTick = -1;
        AABB targetBox = snapshot.targetAabb();
        if (targetBox != null) {
            var samples = trajectory.samples();
            for (int i = 0; i + 1 < samples.size(); i++) {
                Trajectory.Sample from = samples.get(i);
                Trajectory.Sample to = samples.get(i + 1);
                Vec3 targetFrom = targetPredictor.predictPosition(snapshot, from.tick());
                Vec3 targetTo = targetPredictor.predictPosition(snapshot, to.tick());
                Vec3 relativeFrom = from.position().subtract(targetFrom.subtract(snapshot.targetPosition()));
                Vec3 relativeTo = to.position().subtract(targetTo.subtract(snapshot.targetPosition()));
                Vec3 intersection = targetBox.contains(relativeFrom)
                        ? relativeFrom
                        : targetBox.clip(relativeFrom, relativeTo).orElse(null);
                if (intersection == null) {
                    continue;
                }
                Vec3 travel = relativeTo.subtract(relativeFrom);
                double lengthSquared = travel.lengthSqr();
                double fraction = lengthSquared < 1.0e-12 ? 0.0
                        : Math.max(0.0, Math.min(1.0, intersection.subtract(relativeFrom).dot(travel) / lengthSquared));
                segmentImpact = from.position().lerp(to.position(), fraction);
                segmentImpactTick = to.tick();
                break;
            }
            if (segmentImpact == null) {
                return false;
            }
        } else {
            double tolerance = Math.max(0.75, Math.min(2.5,
                    0.75 + snapshot.targetVelocity().length() * 2.0));
            double bestDistanceSqr = tolerance * tolerance;
            var samples = trajectory.samples();
            boolean intersectsTarget = false;
            for (int i = 0; i + 1 < samples.size(); i++) {
                Trajectory.Sample from = samples.get(i);
                Trajectory.Sample to = samples.get(i + 1);
                if (from.tick() >= ticks) {
                    break;
                }
                Vec3 targetFrom = targetPredictor.predictPosition(snapshot, from.tick());
                Vec3 targetTo = targetPredictor.predictPosition(snapshot, to.tick());
                Vec3 relativeFrom = from.position().subtract(targetFrom.subtract(snapshot.targetPosition()));
                Vec3 relativeTo = to.position().subtract(targetTo.subtract(snapshot.targetPosition()));
                Vec3 travel = relativeTo.subtract(relativeFrom);
                double lengthSquared = travel.lengthSqr();
                double fraction = lengthSquared < 1.0e-12 ? 0.0 : Math.max(0.0,
                        Math.min(1.0, snapshot.targetPosition().subtract(relativeFrom).dot(travel) / lengthSquared));
                Vec3 closest = relativeFrom.add(travel.scale(fraction));
                double distanceSqr = closest.distanceToSqr(snapshot.targetPosition());
                if (distanceSqr <= bestDistanceSqr) {
                    bestDistanceSqr = distanceSqr;
                    segmentImpact = from.position().lerp(to.position(), fraction);
                    segmentImpactTick = to.tick();
                    intersectsTarget = true;
                }
            }
            if (!intersectsTarget) {
                return false;
            }
        }
        int pathEndTick = segmentImpact == null ? ticks : segmentImpactTick;
        try {
            ObstructionResult obstruction = mainThreadObstructionChecker.check(
                    level, trajectory, pathEndTick, segmentImpact,
                    model.cbcPhysics() && !model.usesCustomDynamics());
            return obstruction.clear();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean compatible(SolveRequest current, SolveRequest prior) {
        TargetingSnapshot a = current.snapshot();
        TargetingSnapshot b = prior.snapshot();
        return current.level() == prior.level()
                && current.cannon() == prior.cannon()
                && Objects.equals(current.fingerprint(), prior.fingerprint())
                && a.targetPosition().distanceToSqr(b.targetPosition()) <= TARGET_MOVE_SQR
                && a.targetVelocity().distanceToSqr(b.targetVelocity()) <= VELOCITY_CHANGE_SQR
                && a.targetAcceleration().distanceToSqr(b.targetAcceleration()) <= VELOCITY_CHANGE_SQR
                && a.inheritedVelocity().distanceToSqr(b.inheritedVelocity()) <= VELOCITY_CHANGE_SQR
                && a.muzzlePosition().distanceToSqr(b.muzzlePosition()) <= TARGET_MOVE_SQR
                && Objects.equals(a.pitchConstraint(), b.pitchConstraint())
                && a.preferHighArc() == b.preferHighArc();
    }

    private static <T> CompletableFuture<T> submitTargetingTask(java.util.function.Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable task = () -> {
            if (future.isDone()) {
                return;
            }
            try {
                future.complete(supplier.get());
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        };
        future.whenComplete((value, failure) -> {
            if (future.isCancelled()) {
                TARGETING_EXECUTOR.remove(task);
            }
        });
        TARGETING_EXECUTOR.execute(task);
        return future;
    }

    private static ThreadPoolExecutor createTargetingExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                TARGETING_EXECUTOR_THREADS, TARGETING_EXECUTOR_THREADS,
                10L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(TARGETING_EXECUTOR_QUEUE_CAPACITY),
                new TargetingThreadFactory());
        executor.allowCoreThreadTimeOut(false);
        return executor;
    }

    private static final class TargetingThreadFactory implements ThreadFactory {
        private final AtomicInteger threadIndex = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "CreateRadar-Targeting-" + threadIndex.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    private record SolveRequest(ServerLevel level, AbstractMountedCannonContraption cannon, String fingerprint, TargetingSnapshot snapshot, ProjectileModel model, long tick) {}

    private static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x)
                && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
