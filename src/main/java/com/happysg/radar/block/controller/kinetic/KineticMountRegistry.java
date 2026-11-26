package com.happysg.radar.block.controller.kinetic;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.Direction;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class KineticMountRegistry {
    private static final CopyOnWriteArrayList<KineticMountProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private KineticMountRegistry() {
    }

    public static void register(KineticMountProvider provider) {
        PROVIDERS.addIfAbsent(Objects.requireNonNull(provider));
    }

    public static KineticMountAdapterResolution resolve(KineticBlockEntity controller, Direction.Axis axis) {
        if (controller == null || controller.getLevel() == null || axis == null) {
            return KineticMountAdapterResolution.absent("invalid_controller");
        }

        KineticMountAdapterResolution selected = null;
        for (KineticMountProvider provider : PROVIDERS) {
            KineticMountAdapterResolution result;
            try {
                result = provider.resolve(controller, axis);
            } catch (RuntimeException exception) {
                return KineticMountAdapterResolution.unavailable("provider_failure");
            }
            if (result == null || result.selection() == KineticMountAdapterResolution.Selection.ABSENT) {
                continue;
            }
            if (selected != null) {
                return KineticMountAdapterResolution.ambiguous("multiple_structural_mounts");
            }
            selected = result;
        }
        return selected == null ? KineticMountAdapterResolution.absent("no_structural_mount") : selected;
    }
}
