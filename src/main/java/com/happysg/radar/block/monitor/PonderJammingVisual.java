package com.happysg.radar.block.monitor;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.sable.SableSilhouetteStatus;
import com.happysg.radar.compat.sable.SyntheticSableSilhouetteFactory;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Deterministic radar frames used only by Ponder's directional-jamming visual hooks. */
final class PonderJammingVisual {
    static final long LOOP_TICKS = 80L;
    private static final String SOURCE_ID = "ponder:directional_jammer";
    private static final UUID FAKE_SHIP_ID = UUID.nameUUIDFromBytes(
            "create_radar:ponder_fake_ship".getBytes(StandardCharsets.UTF_8));
    private static final UUID FAKE_CONTACT_ID = UUID.nameUUIDFromBytes(
            "create_radar:ponder_fake_contact".getBytes(StandardCharsets.UTF_8));

    enum Tier {
        GUIDANCE,
        DIRECTIONAL,
        SEVERE
    }

    record Frame(List<RadarTrack> tracks) {
        Frame {
            tracks = List.copyOf(tracks);
        }
    }

    private PonderJammingVisual() {
    }

    static Frame createFrame(Tier tier, Vec3 radarCenter, long gameTime,
                             long relativeTick, boolean fakeHullEnabled) {
        if (tier == null || radarCenter == null) {
            return new Frame(List.of());
        }
        long animationTick = Math.max(0L, relativeTick);
        long phase = Math.floorMod(animationTick, LOOP_TICKS);
        List<RadarTrack> tracks = new ArrayList<>();

        addRealTrack(tracks, tier, radarCenter, gameTime, animationTick,
                "ponder_sector_contact_a", new Vec3(30.0, 0.0, 6.0),
                TrackCategory.CONTRAPTION, false, true);
        if (tier != Tier.SEVERE || phase < 20L || phase >= 35L) {
            addRealTrack(tracks, tier, radarCenter, gameTime, animationTick,
                    "ponder_sector_contact_b", new Vec3(25.0, 0.0, -11.0),
                    TrackCategory.PLAYER, false, true);
        }
        if (tier != Tier.SEVERE || phase < 50L || phase >= 65L) {
            addRealTrack(tracks, tier, radarCenter, gameTime, animationTick,
                    "ponder_outside_contact", new Vec3(-27.0, 0.0, 18.0),
                    TrackCategory.CONTRAPTION, false, false);
        }
        boolean friendly = tier != Tier.SEVERE
                || phase < 20L || (phase >= 40L && phase < 60L);
        addRealTrack(tracks, tier, radarCenter, gameTime, animationTick,
                "ponder_friendly_ship", new Vec3(-18.0, 0.0, -27.0),
                TrackCategory.SABLE, friendly, false);

        if (tier == Tier.SEVERE && phase >= 8L && phase < 28L) {
            tracks.add(createSyntheticTrack(FAKE_SHIP_ID, radarCenter,
                    new Vec3(13.0, 0.0, 25.0), gameTime, animationTick,
                    fakeHullEnabled, true));
        }
        if (tier == Tier.SEVERE && phase >= 44L && phase < 70L) {
            tracks.add(createSyntheticTrack(FAKE_CONTACT_ID, radarCenter,
                    new Vec3(-5.0, 0.0, 31.0), gameTime, animationTick,
                    false, false));
        }
        return new Frame(tracks);
    }

    private static void addRealTrack(List<RadarTrack> tracks, Tier tier,
                                     Vec3 radarCenter, long gameTime,
                                     long animationTick, String id,
                                     Vec3 baseOffset, TrackCategory category,
                                     boolean friendly, boolean inJammerSector) {
        Vec3 visibleOffset = switch (tier) {
            case GUIDANCE -> Vec3.ZERO;
            case DIRECTIONAL -> inJammerSector
                    ? jitter(id, animationTick, 10L, 4.0) : Vec3.ZERO;
            case SEVERE -> jitter(id, animationTick, 5L, 13.0);
        };
        RadarTrack track = new RadarTrack(id,
                radarCenter.add(baseOffset).add(visibleOffset), Vec3.ZERO,
                gameTime, category,
                category == TrackCategory.SABLE ? "Sable:ship" : "ponder",
                category == TrackCategory.SABLE ? 6.0F : 1.0F);
        track.setFriendly(friendly);

        float directionalStrength = tier != Tier.GUIDANCE && inJammerSector
                ? 0.85F : 0.0F;
        float severeStrength = tier == Tier.SEVERE ? 1.0F : 0.0F;
        Vec3 hiddenGuidanceError = new Vec3(3.0, 0.0, -2.0)
                .add(visibleOffset);
        track.setJammingData(new RadarTrack.JammingData(
                SOURCE_ID, 0.85F, directionalStrength, severeStrength,
                tier == Tier.SEVERE ? 0.75F : 0.0F,
                hiddenGuidanceError, Vec3.ZERO,
                mix64(id.hashCode() ^ Math.floorDiv(animationTick,
                        tier == Tier.SEVERE ? 5L : 10L))));
        tracks.add(track);
    }

    private static RadarTrack createSyntheticTrack(UUID id, Vec3 radarCenter,
                                                   Vec3 baseOffset, long tick,
                                                   long animationTick,
                                                   boolean fakeHullEnabled,
                                                   boolean shipContact) {
        Vec3 visibleOffset = jitter(id.toString(), animationTick, 5L, 13.0);
        TrackCategory category = shipContact
                ? TrackCategory.SABLE : TrackCategory.CONTRAPTION;
        RadarTrack track = new RadarTrack(id.toString(),
                radarCenter.add(baseOffset).add(visibleOffset), Vec3.ZERO,
                tick, category,
                shipContact ? "Sable:ship" : "jamming:synthetic", 6.0F);
        track.setSynthetic(true);
        track.setJammingData(new RadarTrack.JammingData(
                SOURCE_ID, 0.85F, 0.85F, 1.0F, 0.75F,
                visibleOffset, Vec3.ZERO,
                mix64(id.getMostSignificantBits()
                        ^ Math.floorDiv(animationTick, 5L))));
        if (shipContact && fakeHullEnabled) {
            track.setSilhouette(id, SyntheticSableSilhouetteFactory.REVISION,
                    SableSilhouetteStatus.READY);
        }
        return track;
    }

    private static Vec3 jitter(String id, long tick, long period,
                               double amplitude) {
        long epoch = Math.floorDiv(tick, Math.max(1L, period));
        RandomSource random = RandomSource.create(mix64(
                ((long) id.hashCode() << 32) ^ epoch));
        return new Vec3((random.nextDouble() * 2.0 - 1.0) * amplitude,
                0.0,
                (random.nextDouble() * 2.0 - 1.0) * amplitude);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
