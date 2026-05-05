package com.happysg.radar.block.controller.id;

public record IDRecord(long idInt, String secretID, String name) {
    public long shipId() { return idInt; }
}
