
package com.happysg.radar.compat.sable;

import com.happysg.radar.block.controller.id.IDManager;
import com.happysg.radar.block.controller.id.IdentificationTransponder;
import com.happysg.radar.compat.vs2.SableUtils;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

import java.util.UUID;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public static IDManager.IDRecord resolveEffectiveIdentification(ServerLevel level, UUID shipId) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }

        SubLevel source = container.getSubLevel(shipId);
        if (source == null || source.isRemoved()) {
            List<SubLevel> candidates = SableAssemblyRegistry.get(level).loadedMembers(level, shipId);

            if (candidates.isEmpty()) {
                return null;
            }

            Set<UUID> component = null;
            for (SubLevel candidate : candidates) {
                Set<UUID> candidateComponent = new HashSet<>();
                candidateComponent.add(candidate.getUniqueId());

                for (SubLevel connected : SubLevelHelper.getConnectedChain(candidate)) {
                    if (connected != null && !connected.isRemoved()) {
                        candidateComponent.add(connected.getUniqueId());
                    }
                }

                if (component == null) {
                    component = candidateComponent;
                    source = candidate;
                } else if (!component.equals(candidateComponent)) {
                    return null;
                }
            }

            if (source == null) {
                return null;
            }
        }

        Set<UUID> members = new HashSet<>();
        members.add(source.getUniqueId());

        for (SubLevel connected : SubLevelHelper.getConnectedChain(source)) {
            if (connected != null && !connected.isRemoved()) {
                members.add(connected.getUniqueId());
            }
        }

        String identity = null;
        String name = null;

        for (UUID memberId : members) {
            for (var entry : IDManager.getTransponderSources(memberId).entrySet()) {
                BlockPos pos = entry.getKey();
                IDManager.IDRecord record = entry.getValue();

                if (!level.hasChunkAt(pos)) {
                    continue;
                }

                if (!(level.getBlockState(pos).getBlock() instanceof IdentificationTransponder)) {
                    continue;
                }

                var containing = SableUtils.getShipManagingPos(level, pos);
                if (containing == null || !memberId.equals(containing.getUniqueId())) {
                    continue;
                }

                String secret = record.secretID() == null ? "" : record.secretID().trim();
                String configuredName = record.name() == null ? "" : record.name().trim();
                String candidate = secret.isBlank() ? configuredName : secret;

                if (candidate.isBlank()) {
                    continue;
                }

                if (identity != null && !identity.equalsIgnoreCase(candidate)) {
                    return null;
                }

                identity = candidate;

                if (name == null) {
                    name = configuredName;
                }
            }
        }

        if (identity == null) {
            return null;
        }

        IDManager.IDRecord ownRecord = IDManager.getIDRecordByShipId(source.getUniqueId());
        if (ownRecord != null && ownRecord.name() != null && !ownRecord.name().isBlank()) {
            name = ownRecord.name();
        }

        return new IDManager.IDRecord(name == null ? "" : name, identity);
    }
}
