package riftyboi.cbcmodernwarfare.cannons.medium_cannon.material;

public class MediumcannonMaterial {
    public PropertiesRecord properties() { return new PropertiesRecord(); }
    public static class PropertiesRecord { public float baseSpeed() { return 1.0f; } public int maxSpeedIncreases() { return 1; } public float speedIncreasePerBarrel() { return 1.0f; } }
}
