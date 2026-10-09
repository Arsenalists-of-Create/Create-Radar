package com.happysg.radar.api.datalink;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for addon-defined Data Link interaction behavior.
 */
public final class RadarDataLinkInteractionRegistry {
    private static final List<RadarDataLinkInteraction> INTERACTIONS = new CopyOnWriteArrayList<>();
    private RadarDataLinkInteractionRegistry() {}

    public static void register(RadarDataLinkInteraction interaction) {
        INTERACTIONS.add(Objects.requireNonNull(interaction, "interaction"));
    }

    public static RadarDataLinkInteraction.Result interact(RadarDataLinkContext context) {
        Objects.requireNonNull(context, "context");

        for (RadarDataLinkInteraction interaction : INTERACTIONS) {
            RadarDataLinkInteraction.Result result = interaction.interact(context);

            if (result == null) {
                continue;
            }

            if (result != RadarDataLinkInteraction.Result.PASS) {
                return result;
            }
        }

        return RadarDataLinkInteraction.Result.PASS;
    }
}