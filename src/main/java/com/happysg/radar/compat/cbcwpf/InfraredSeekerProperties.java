package com.happysg.radar.compat.cbcwpf;

public class InfraredSeekerProperties {
    public Object ballisticPropertiesComponent() { return null; }
    public Object entityDamagePropertiesComponent() { return null; }
    public GuidanceBlock guidanceBlockProperties() { return null; }
    
    public interface GuidanceBlock {
        double addedGravity();
        double addedSpread();
        double maxSpeed();
    }
}
