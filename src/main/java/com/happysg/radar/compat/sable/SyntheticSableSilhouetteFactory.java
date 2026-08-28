package com.happysg.radar.compat.sable;

import java.util.Random;
import java.util.UUID;

/** Deterministic, client-reproducible hulls for synthetic radar contacts. */
public final class SyntheticSableSilhouetteFactory {
    public static final int REVISION = 1;
    private static final int ARCHETYPE_COUNT = 3;

    private SyntheticSableSilhouetteFactory() {
    }

    public static Profile profile(UUID id) {
        long seed = seed(id);
        Random random = new Random(seed);
        int archetype = random.nextInt(ARCHETYPE_COUNT);
        double length = 12.0 + random.nextDouble() * 24.0;
        double beam = 5.0 + random.nextDouble() * 9.0;
        double height = 3.0 + random.nextDouble() * 5.0;
        float heading = (float) (unitRandom(mix64(seed
                ^ 0x6A09E667F3BCC909L)) * 360.0);
        return new Profile(archetype, length, beam, height, heading);
    }

    public static SubLevelSilhouette create(UUID id) {
        Profile profile = profile(id);
        SubLevelSilhouette.Builder builder = SubLevelSilhouette.builder();
        switch (profile.archetype()) {
            case 0 -> addPatrolHull(builder, profile);
            case 1 -> addFreighterHull(builder, profile);
            default -> addCatamaranHull(builder, profile);
        }
        return builder.build();
    }

    /** Projects the centered hull at its stable heading but without translation. */
    public static SubLevelSilhouette.ProjectedSilhouette project(
            UUID id,
            SubLevelSilhouette silhouette,
            SubLevelSilhouette.ProjectionSettings settings
    ) {
        double radians = Math.toRadians(profile(id).headingDegrees());
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return silhouette.project((localX, localY, localZ, destination) ->
                        destination.set(localX * cos + localZ * sin,
                                localY,
                                -localX * sin + localZ * cos),
                settings);
    }

    private static void addPatrolHull(SubLevelSilhouette.Builder builder,
                                      Profile profile) {
        double length = profile.length();
        double beam = profile.beam();
        double height = profile.height();
        addCentered(builder, beam * 0.72, -length * 0.50,
                -length * 0.20, height * 0.65);
        addCentered(builder, beam, -length * 0.20,
                length * 0.20, height);
        addCentered(builder, beam * 0.72, length * 0.20,
                length * 0.38, height * 0.75);
        addCentered(builder, beam * 0.36, length * 0.38,
                length * 0.50, height * 0.50);
    }

    private static void addFreighterHull(SubLevelSilhouette.Builder builder,
                                         Profile profile) {
        double length = profile.length();
        double beam = profile.beam();
        double height = profile.height();
        addCentered(builder, beam * 0.82, -length * 0.50,
                -length * 0.38, height * 0.85);
        addCentered(builder, beam, -length * 0.38,
                length * 0.30, height);
        addCentered(builder, beam * 0.74, length * 0.30,
                length * 0.43, height * 0.72);
        addCentered(builder, beam * 0.40, length * 0.43,
                length * 0.50, height * 0.48);
    }

    private static void addCatamaranHull(SubLevelSilhouette.Builder builder,
                                         Profile profile) {
        double length = profile.length();
        double beam = profile.beam();
        double height = profile.height();
        double hullWidth = beam * 0.27;
        double hullOffset = beam * 0.31;
        addOffset(builder, -hullOffset, hullWidth,
                -length * 0.48, length * 0.40, height * 0.72);
        addOffset(builder, hullOffset, hullWidth,
                -length * 0.48, length * 0.40, height * 0.72);
        addCentered(builder, beam, -length * 0.16,
                length * 0.18, height);
        addCentered(builder, beam * 0.55, length * 0.18,
                length * 0.46, height * 0.55);
    }

    private static void addCentered(SubLevelSilhouette.Builder builder,
                                    double width, double minZ, double maxZ,
                                    double height) {
        addOffset(builder, 0.0, width, minZ, maxZ, height);
    }

    private static void addOffset(SubLevelSilhouette.Builder builder,
                                  double centerX, double width,
                                  double minZ, double maxZ, double height) {
        double halfWidth = width * 0.5;
        builder.add(centerX - halfWidth, -height * 0.5, minZ,
                centerX + halfWidth, height * 0.5, maxZ);
    }

    private static long seed(UUID id) {
        if (id == null) {
            return 0L;
        }
        return mix64(id.getMostSignificantBits()
                ^ Long.rotateLeft(id.getLeastSignificantBits(), 23));
    }

    private static double unitRandom(long seed) {
        return (mix64(seed) >>> 11) * 0x1.0p-53;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public record Profile(int archetype, double length, double beam,
                          double height, float headingDegrees) {
    }
}
