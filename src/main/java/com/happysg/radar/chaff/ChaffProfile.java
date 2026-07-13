package com.happysg.radar.chaff;

import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

/** Pure firework-to-chaff strength calculation. */
public record ChaffProfile(int weight, double chance, int durationTicks) {
    private static final int MIN_WEIGHT = 1;
    private static final int MAX_WEIGHT = 14;

    public static ChaffProfile from(Fireworks fireworks, ChaffSettings settings) {
        if (fireworks == null || fireworks.explosions().isEmpty()) {
            return null;
        }

        int weight = 0;
        for (FireworkExplosion explosion : fireworks.explosions()) {
            weight += explosion.shape() == FireworkExplosion.Shape.LARGE_BALL ? 2 : 1;
            if (weight >= MAX_WEIGHT) {
                weight = MAX_WEIGHT;
                break;
            }
        }

        weight = Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, weight));
        double strength = (weight - MIN_WEIGHT) / (double) (MAX_WEIGHT - MIN_WEIGHT);
        double chance = lerp(settings.minChance(), settings.maxSingleChance(), strength);
        int duration = (int) Math.round(lerp(settings.minDurationTicks(), settings.maxDurationTicks(), strength));
        return new ChaffProfile(weight, chance, duration);
    }

    private static double lerp(double start, double end, double amount) {
        return start + (end - start) * amount;
    }
}
