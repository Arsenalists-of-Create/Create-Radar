package com.happysg.radar.block.controller.kinetic;

import javax.annotation.Nullable;

/** Structural selection is intentionally independent from endpoint availability. */
public record KineticMountAdapterResolution(Selection selection,
                                            @Nullable KineticMountAdapter adapter,
                                            String reason) {
    public enum Selection {
        ABSENT,
        UNAVAILABLE,
        PRESENT,
        AMBIGUOUS
    }

    public static KineticMountAdapterResolution absent(String reason) {
        return new KineticMountAdapterResolution(Selection.ABSENT, null, reason);
    }

    public static KineticMountAdapterResolution present(KineticMountAdapter adapter) {
        return new KineticMountAdapterResolution(Selection.PRESENT, adapter, "adapter_present");
    }

    public static KineticMountAdapterResolution unavailable(String reason) {
        return new KineticMountAdapterResolution(Selection.UNAVAILABLE, null, reason);
    }

    public static KineticMountAdapterResolution ambiguous(String reason) {
        return new KineticMountAdapterResolution(Selection.AMBIGUOUS, null, reason);
    }

    public boolean isStructuralSelection() {
        return selection != Selection.ABSENT;
    }

    public boolean hasAdapter() {
        return selection == Selection.PRESENT && adapter != null;
    }
}
