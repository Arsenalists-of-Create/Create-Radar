package com.happysg.radar.block.monitor;

import com.happysg.radar.block.radar.track.RadarTrack;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/** Deterministic checks for the monitor-only Ponder jamming frames. */
public final class MonitorPonderJammingSelfTest {
    private static final double EPSILON = 1.0E-9;

    private MonitorPonderJammingSelfTest() {
    }

    public static void verify() {
        verifyGuidanceTier();
        verifyDirectionalTier();
        verifySevereTier();
        verifyRepeatabilityAndReset();
    }

    private static void verifyGuidanceTier() {
        Map<String, RadarTrack> first = frame(
                PonderJammingVisual.Tier.GUIDANCE, 0L, false);
        Map<String, RadarTrack> next = frame(
                PonderJammingVisual.Tier.GUIDANCE, 20L, false);
        require(first.size() == 4 && next.size() == 4,
                "guidance tier should retain all four real contacts");
        for (Map.Entry<String, RadarTrack> entry : first.entrySet()) {
            RadarTrack later = requireTrack(next, entry.getKey());
            requireSamePosition(entry.getValue(), later,
                    "guidance contacts must remain visually steady");
            RadarTrack.JammingData data = entry.getValue().getJammingData();
            require(data != null && data.outerStrength() > 0.0F,
                    "guidance tier should carry hidden guidance corruption");
            require(data.directionalStrength() == 0.0F
                            && data.severeStrength() == 0.0F,
                    "guidance tier must not advertise visible jitter");
        }
    }

    private static void verifyDirectionalTier() {
        Map<String, RadarTrack> first = frame(
                PonderJammingVisual.Tier.DIRECTIONAL, 0L, false);
        Map<String, RadarTrack> next = frame(
                PonderJammingVisual.Tier.DIRECTIONAL, 10L, false);
        requireMoved(requireTrack(first, "ponder_sector_contact_a"),
                requireTrack(next, "ponder_sector_contact_a"),
                "first jammer-sector contact should jitter");
        requireMoved(requireTrack(first, "ponder_sector_contact_b"),
                requireTrack(next, "ponder_sector_contact_b"),
                "second jammer-sector contact should jitter");
        requireSamePosition(requireTrack(first, "ponder_outside_contact"),
                requireTrack(next, "ponder_outside_contact"),
                "contacts outside the jammer sector should remain steady");
        requireSamePosition(requireTrack(first, "ponder_friendly_ship"),
                requireTrack(next, "ponder_friendly_ship"),
                "friendly contact outside the jammer sector should remain steady");
    }

    private static void verifySevereTier() {
        Map<String, RadarTrack> first = frame(
                PonderJammingVisual.Tier.SEVERE, 0L, false);
        Map<String, RadarTrack> next = frame(
                PonderJammingVisual.Tier.SEVERE, 5L, false);
        for (String id : first.keySet()) {
            RadarTrack later = requireTrack(next, id);
            requireMoved(first.get(id), later,
                    "every surviving severe-tier contact should jitter");
            require(first.get(id).getJammingData() != null
                            && first.get(id).getJammingData().severeStrength()
                            > 0.0F,
                    "severe contacts should carry severe jamming metadata");
        }

        require(frame(PonderJammingVisual.Tier.SEVERE, 19L, false)
                        .containsKey("ponder_sector_contact_b"),
                "dropout contact should be visible before its outage");
        require(!frame(PonderJammingVisual.Tier.SEVERE, 20L, false)
                        .containsKey("ponder_sector_contact_b"),
                "dropout contact should disappear on schedule");
        require(frame(PonderJammingVisual.Tier.SEVERE, 35L, false)
                        .containsKey("ponder_sector_contact_b"),
                "dropout contact should return on schedule");

        require(requireTrack(frame(PonderJammingVisual.Tier.SEVERE,
                19L, false), "ponder_friendly_ship").isFriendly(),
                "friendly identification should initially be present");
        require(!requireTrack(frame(PonderJammingVisual.Tier.SEVERE,
                20L, false), "ponder_friendly_ship").isFriendly(),
                "friendly identification should be lost on schedule");
        require(requireTrack(frame(PonderJammingVisual.Tier.SEVERE,
                40L, false), "ponder_friendly_ship").isFriendly(),
                "friendly identification should later return");

        Map<String, RadarTrack> fakeShipFrame = frame(
                PonderJammingVisual.Tier.SEVERE, 8L, true);
        RadarTrack fakeShip = fakeShipFrame.values().stream()
                .filter(RadarTrack::isSynthetic)
                .findFirst().orElseThrow(() -> new AssertionError(
                        "severe state should create a synthetic contact"));
        require(fakeShip.getSilhouetteId() != null,
                "enabled fake hull should carry silhouette metadata");
        RadarTrack iconOnlyFakeShip = frame(
                PonderJammingVisual.Tier.SEVERE, 8L, false).values().stream()
                .filter(RadarTrack::isSynthetic)
                .findFirst().orElseThrow();
        require(iconOnlyFakeShip.getSilhouetteId() == null,
                "disabled fake hull should remain an icon-only contact");
        require(frame(PonderJammingVisual.Tier.SEVERE, 28L, true).values()
                        .stream().noneMatch(RadarTrack::isSynthetic),
                "first synthetic contact should disappear on schedule");
        require(frame(PonderJammingVisual.Tier.SEVERE, 44L, false).values()
                        .stream().anyMatch(RadarTrack::isSynthetic),
                "a later fake contact should appear on schedule");
        require(frame(PonderJammingVisual.Tier.SEVERE, 70L, false).values()
                        .stream().noneMatch(RadarTrack::isSynthetic),
                "later fake contact should disappear on schedule");
    }

    private static void verifyRepeatabilityAndReset() {
        Map<String, RadarTrack> first = frame(
                PonderJammingVisual.Tier.SEVERE, 12L, true);
        Map<String, RadarTrack> repeated = frame(
                PonderJammingVisual.Tier.SEVERE, 12L, true);
        require(first.keySet().equals(repeated.keySet()),
                "re-entering a tier should reproduce the same contacts");
        for (String id : first.keySet()) {
            requireSamePosition(first.get(id), repeated.get(id),
                    "re-entering a tier should reset its animation phase");
        }
        require(PonderJammingVisual.createFrame(null, Vec3.ZERO,
                0L, 0L, false).tracks().isEmpty(),
                "cleared Ponder state should provide no override tracks");
    }

    private static Map<String, RadarTrack> frame(
            PonderJammingVisual.Tier tier, long animationTick,
            boolean fakeHullEnabled) {
        Map<String, RadarTrack> byId = new LinkedHashMap<>();
        for (RadarTrack track : PonderJammingVisual.createFrame(
                tier, Vec3.ZERO, 1_000L + animationTick,
                animationTick, fakeHullEnabled).tracks()) {
            byId.put(track.getId(), track);
        }
        return byId;
    }

    private static RadarTrack requireTrack(Map<String, RadarTrack> tracks,
                                           String id) {
        RadarTrack track = tracks.get(id);
        if (track == null) {
            throw new AssertionError("missing Ponder track " + id);
        }
        return track;
    }

    private static void requireMoved(RadarTrack first, RadarTrack second,
                                     String message) {
        require(first.position().distanceToSqr(second.position()) > EPSILON,
                message);
    }

    private static void requireSamePosition(RadarTrack first,
                                            RadarTrack second,
                                            String message) {
        require(first.position().distanceToSqr(second.position()) <= EPSILON,
                message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
