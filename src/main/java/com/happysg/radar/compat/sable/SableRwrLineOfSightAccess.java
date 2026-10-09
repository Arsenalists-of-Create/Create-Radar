
package com.happysg.radar.compat.sable;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;

import java.util.UUID;

public final class SableRwrLineOfSightAccess {
    private SableRwrLineOfSightAccess() {}

    public static void ignoreTargetSublevel(ServerLevel level, ClipContext context, UUID targetShipId) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);

        if (container == null) {
            return;
        }

        SubLevel targetSubLevel = container.getSubLevel(targetShipId);

        if (targetSubLevel == null) {
            return;
        }

        if (context instanceof ClipContextExtension extension) {
            extension.sable$setIgnoredSubLevel(targetSubLevel);
        }
    }
}
