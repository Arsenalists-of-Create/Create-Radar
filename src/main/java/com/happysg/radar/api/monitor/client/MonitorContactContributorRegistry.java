package com.happysg.radar.api.monitor.client;

import com.happysg.radar.api.monitor.MonitorContactContributor;
import com.happysg.radar.api.monitor.MonitorContactSnapshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

import java.util.*;

public final class MonitorContactContributorRegistry {

    private static final List<MonitorContactContributor> CONTRIBUTORS =
            new ArrayList<>();

    private MonitorContactContributorRegistry() {}

    public static void register(MonitorContactContributor contributor) {
        Objects.requireNonNull(contributor, "contributor");
        CONTRIBUTORS.add(contributor);
    }

    public static List<MonitorContactSnapshot> collect(ClientLevel level, BlockPos monitorPos) {
        if (level == null || monitorPos == null) {
            return List.of();
        }

        List<MonitorContactSnapshot> contacts = new ArrayList<>();

        for (MonitorContactContributor contributor : CONTRIBUTORS) {
            Collection<MonitorContactSnapshot> contributed = contributor.getContacts(level, monitorPos);

            if (contributed == null || contributed.isEmpty()) {
                continue;
            }

            for (MonitorContactSnapshot contact : contributed) {
                if (contact != null) {
                    contacts.add(contact);
                }
            }
        }

        return List.copyOf(contacts);
    }

    public static List<MonitorContactContributor> registeredContributors() {
        return Collections.unmodifiableList(CONTRIBUTORS);
    }
}