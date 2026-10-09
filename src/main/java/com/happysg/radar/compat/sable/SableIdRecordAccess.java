
package com.happysg.radar.compat.sable;

import com.happysg.radar.block.controller.id.IDManager;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class SableIdRecordAccess {
    private SableIdRecordAccess() {}

    public static int applySecretIdToAttachedSublevels(ServerPlayer sender, UUID sourceId, String secretID) {
        SubLevelContainer container = SubLevelContainer.getContainer(sender.serverLevel());

        if (container == null) {
            return 0;
        }

        SubLevel source = container.getSubLevel(sourceId);

        if (source == null || source.boundingBox() == null) {
            return 0;
        }

        int applied = 0;

        for (SubLevel attached : SubLevelHelper.getConnectedChain(source)) {
            UUID attachedId = attached.getUniqueId();

            if (sourceId.equals(attachedId)) {
                continue;
            }

            IDManager.IDRecord existing = IDManager.getIDRecordByShipId(attachedId);
            String name = existing != null ? existing.name() : attached.getName();

            if (name == null || name.isBlank()) {
                name = attachedId.toString();
            }

            IDManager.addIDRecord(attachedId, secretID, name);
            applied++;
        }

        return applied;
    }
}
