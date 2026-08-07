package com.happysg.radar.debug;

public enum ConflictConfidence {
    LOW,
    MEDIUM,
    HIGH;

    public static ConflictConfidence fromScore(int score) {
        if (score >= 80) return HIGH;
        if (score >= 45) return MEDIUM;
        return LOW;
    }
}
