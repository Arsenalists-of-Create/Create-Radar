package com.happysg.radar.block.arad.rwr;

public record RwrContactEvaluation(
        boolean emitting,
        boolean detectableByReceiver,
        boolean lockCapable,
        boolean lockedOnExactTarget,
        float signalStrength
) {
    public static RwrContactEvaluation notEmitting() {
        return new RwrContactEvaluation(false, false, false, false, 0.0F);
    }
}
