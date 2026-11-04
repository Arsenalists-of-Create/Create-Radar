package com.happysg.radar.mixin;

import com.happysg.radar.block.controller.kinetic.TPitchKineticRecursionState;
import com.happysg.radar.block.controller.tpitch.TPitchControllerBlockEntity;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RotationPropagator.class, remap = false)
public abstract class TPitchKineticRecursionGuardMixin {

    @Unique
    private static final Logger radar$LOGGER = LogUtils.getLogger();

    @Inject(method = "getRotationSpeedModifier", at = @At("HEAD"), remap = false)

    private static void radar$detectTPitchConnection(KineticBlockEntity from, KineticBlockEntity to, CallbackInfoReturnable<Float> cir) {
        TPitchKineticRecursionState.State guard = TPitchKineticRecursionState.get();

        if (guard.depth > 0 && (from instanceof TPitchControllerBlockEntity || to instanceof TPitchControllerBlockEntity)) {
            guard.touchedTPitch = true;
        }
    }

    @Inject(method = "propagateNewSource", at = @At("HEAD"), cancellable = true, remap = false)

    private static void radar$preventKineticCycle(KineticBlockEntity current, CallbackInfo ci) {
        TPitchKineticRecursionState.State guard = TPitchKineticRecursionState.get();

        if (guard.depth == 0) {
            guard.reset();
        }

        if (current instanceof TPitchControllerBlockEntity) {
            guard.touchedTPitch = true;
        }

        Integer previousCount = guard.active.get(current);

        if (guard.touchedTPitch && previousCount != null) {
            if (!guard.warned) {
                KineticBlockEntity previous = guard.path.peekLast();
                radar$LOGGER.warn("Create: Radars prevented T-Pitch kinetic " + "propagation recursion: from={} to={} depth={}", previous == null ? "unknown" : previous.getBlockPos(), current.getBlockPos(), guard.depth);
                guard.warned = true;
            }

            ci.cancel();
            return;
        }

        guard.active.put(current, previousCount == null ? 1 : previousCount + 1);
        guard.path.addLast(current);
        guard.depth++;
    }

    @Inject(method = "propagateNewSource", at = @At("RETURN"), remap = false)
    private static void radar$finishPropagation(KineticBlockEntity current, CallbackInfo ci) {
        TPitchKineticRecursionState.State guard = TPitchKineticRecursionState.get();

        if (guard.depth <= 0) {
            return;
        }

        guard.path.removeLast();
        Integer count = guard.active.get(current);

        if (count != null) {
            if (count <= 1) {
                guard.active.remove(current);
            } else {
                guard.active.put(current, count - 1);
            }
        }

        guard.depth--;

        if (guard.depth == 0) {
            guard.reset();
        }
    }
}