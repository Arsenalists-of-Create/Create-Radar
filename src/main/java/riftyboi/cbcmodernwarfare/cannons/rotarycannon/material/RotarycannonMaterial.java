package riftyboi.cbcmodernwarfare.cannons.rotarycannon.material;

public class RotarycannonMaterial {
    public PropertiesRecord properties() { return new PropertiesRecord(); }
    public static class PropertiesRecord { public float baseSpeed() { return 1.0f; } public int maxSpeedIncreases() { return 1; } public float speedIncreasePerBarrel() { return 1.0f; } }
}
