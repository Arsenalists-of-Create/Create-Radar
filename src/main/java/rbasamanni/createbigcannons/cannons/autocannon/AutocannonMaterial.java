package rbasamanni.createbigcannons.cannons.autocannon;

public interface AutocannonMaterial {
    Properties properties();
    interface Properties {
        float baseSpeed();
        int maxSpeedIncreases();
        float speedIncreasePerBarrel();
    }
}
