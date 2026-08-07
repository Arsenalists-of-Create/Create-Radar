package com.happysg.radar.mixin.diagnostic;

import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class CreateRadarMixinErrorHandler
        implements IMixinErrorHandler {
    @Override
    public ErrorAction onPrepareError(IMixinConfig config, Throwable throwable,
                                      IMixinInfo mixin, ErrorAction action) {
        record("MIXIN_PREPARE", config, throwable, mixin, "");
        return action;
    }

    @Override
    public ErrorAction onApplyError(String targetClassName,
                                    Throwable throwable, IMixinInfo mixin,
                                    ErrorAction action) {
        record("MIXIN_APPLY", mixin == null ? null : mixin.getConfig(),
                throwable, mixin, targetClassName);
        return action;
    }

    private static void record(String stage, IMixinConfig config,
                               Throwable throwable, IMixinInfo mixin,
                               String target) {
        try {
            EarlyDiagnosticJournal.recordFailure(stage,
                    target == null ? "" : target,
                    mixin == null ? "" : mixin.getClassName(),
                    config == null ? "" : config.getName(), throwable);
        } catch (Throwable ignored) {
            // Never replace the original transformation failure.
        }
    }
}
