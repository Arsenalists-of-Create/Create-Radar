
package com.happysg.radar.compat.sable;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.UUID;

public final class SableWeaponFiringAccess {
    private SableWeaponFiringAccess() {}

    @Nullable
    public static SubLevelAccess findSubLevel(ServerLevel level, UUID id) {
        if (level == null || id == null) {
            return null;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container == null ? null : container.getSubLevel(id);
    }
}
