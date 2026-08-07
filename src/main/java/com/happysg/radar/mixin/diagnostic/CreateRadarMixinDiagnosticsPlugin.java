package com.happysg.radar.mixin.diagnostic;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CreateRadarMixinDiagnosticsPlugin
        implements IMixinConfigPlugin {
    private static final String ERROR_HANDLER =
            "com.happysg.radar.mixin.diagnostic.CreateRadarMixinErrorHandler";

    @Override
    public void onLoad(String mixinPackage) {
        try {
            EarlyDiagnosticJournal.beginBoot("mixin_plugin");
            Mixins.registerErrorHandlerClass(ERROR_HANDLER);
            EarlyDiagnosticJournal.record("MIXIN_PLUGIN", "LOADED", Map.of(
                    "package", mixinPackage,
                    "error_handler", ERROR_HANDLER));
        } catch (Throwable ignored) {
            // Diagnostics must never affect transformation.
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName,
                                    String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets,
                              Set<String> otherTargets) {
        try {
            EarlyDiagnosticJournal.record("MIXIN_TARGETS", "ACCEPTED", Map.of(
                    "ours", Integer.toString(myTargets.size()),
                    "foreign", Integer.toString(otherTargets.size())));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
        record("PRE_APPLY", targetClassName, mixinClassName, mixinInfo);
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
        record("POST_APPLY", targetClassName, mixinClassName, mixinInfo);
    }

    private static void record(String status, String target,
                               String mixinClassName, IMixinInfo info) {
        try {
            EarlyDiagnosticJournal.record("MIXIN_APPLY", status, Map.of(
                    "target", target,
                    "mixin", mixinClassName,
                    "config", info.getConfig().getName(),
                    "priority", Integer.toString(info.getPriority()),
                    "phase", info.getPhase().toString()));
        } catch (Throwable ignored) {
        }
    }
}
