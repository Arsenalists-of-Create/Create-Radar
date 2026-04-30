package riftyboi.cbcmodernwarfare.munitions.munitions_contraption_launcher.guidance.infrared_homing;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import rbasamoyai.createbigcannons.munitions.config.components.EntityDamagePropertiesComponent;
public class InfraredSeekerProperties {
    public BallisticPropertiesComponent ballisticPropertiesComponent() { return null; }
    public EntityDamagePropertiesComponent entityDamagePropertiesComponent() { return null; }
    public GProperties guidanceBlockProperties() { return new GProperties(); }
    public static class GProperties { public float addedGravity() { return 0f; } public float addedSpread() { return 0f; } public float maxSpeed() { return 0f; } }
}
