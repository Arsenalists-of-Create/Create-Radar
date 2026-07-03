package com.happysg.radar.compat.sable;

public final class SableSilhouetteStatus {
    public static final byte NONE = 0;
    public static final byte READY = 1;
    public static final byte FALLBACK = 2;
    public static final byte FAILED = 3;

    private SableSilhouetteStatus() {
    }

    public static boolean drawable(byte status) {
        return status == READY || status == FALLBACK;
    }
}
