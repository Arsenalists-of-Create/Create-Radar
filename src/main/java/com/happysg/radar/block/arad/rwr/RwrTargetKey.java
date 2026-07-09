package com.happysg.radar.block.arad.rwr;

import java.util.UUID;

public record RwrTargetKey(Kind kind, UUID id) {
    public enum Kind {
        ENTITY,
        SABLE_SHIP
    }
}
