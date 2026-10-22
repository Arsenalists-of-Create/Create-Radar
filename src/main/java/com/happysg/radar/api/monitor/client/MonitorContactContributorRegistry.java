package com.happysg.radar.api.monitor.client;

import com.happysg.radar.api.monitor.MonitorContactContributor;
import com.happysg.radar.api.monitor.MonitorContactSnapshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;

public final class MonitorContactContributorRegistry {
    private static final List<MonitorContactContributor> CONTRIBUTORS = new ArrayList<>();
    private static final Set<Class<?>> WARNED_CONTRIBUTORS = new HashSet<>();
    private static final Logger LOGGER = LogUtils.getLogger();

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
            try {
                Collection<MonitorContactSnapshot> contributed = contributor.getContacts(level, monitorPos);
                if (contributed == null || contributed.isEmpty()) {
                    continue;
                }

                for (MonitorContactSnapshot contact : contributed) {
                    if (contact == null || !finite(contact.position())) {
                        continue;
                    }

                    contacts.add(contact);
                }

            } catch (RuntimeException exception) {
                if (WARNED_CONTRIBUTORS.add(contributor.getClass())) {
                    LOGGER.warn("Monitor contact contributor {} failed", contributor.getClass().getName(), exception);
                }
            }
        }

        return List.copyOf(contacts);
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    public static List<MonitorContactContributor> registeredContributors() {
        return Collections.unmodifiableList(CONTRIBUTORS);
    }
}