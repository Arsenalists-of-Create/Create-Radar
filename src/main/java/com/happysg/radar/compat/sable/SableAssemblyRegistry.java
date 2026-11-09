package com.happysg.radar.compat.sable;

import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SableAssemblyRegistry extends SavedData {
    private static final String DATA_NAME = "create_radar_sable_assemblies";

    private final Map<UUID, UUID> ownerByMember = new HashMap<>();

    private static final Comparator<UUID> UUID_ORDER = Comparator.comparing(UUID::toString);

    private SableAssemblyRegistry() {}

    public static SableAssemblyRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(SableAssemblyRegistry::new, SableAssemblyRegistry::load, null), DATA_NAME);
    }

    public UUID groupOf(UUID memberId) {
        return ownerByMember.getOrDefault(memberId, memberId);
    }

    public UUID observe(ServerLevel level, UUID memberId) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return groupOf(memberId);
        }

        SubLevel source = container.getSubLevel(memberId);
        if (source == null || source.isRemoved()) {
            return groupOf(memberId);
        }

        Set<UUID> connected = new HashSet<>();
        connected.add(memberId);

        for (SubLevel sublevel : SubLevelHelper.getConnectedChain(source)) {
            if (sublevel != null && !sublevel.isRemoved()) {
                connected.add(sublevel.getUniqueId());
            }
        }

        UUID existingOwner = ownerByMember.get(memberId);
        UUID owner = existingOwner != null ? existingOwner : connected.stream()
                .filter(ownerByMember::containsKey)
                .map(ownerByMember::get)
                .min(UUID_ORDER)
                .orElseGet(() -> connected.stream().min(UUID_ORDER).orElse(memberId));

        Set<UUID> mergedOwners = new HashSet<>();
        mergedOwners.add(owner);

        for (UUID id : connected) {
            UUID previous = ownerByMember.get(id);
            if (previous != null) {
                mergedOwners.add(previous);
            }
        }

        boolean changed = false;

        for (Map.Entry<UUID, UUID> entry : ownerByMember.entrySet()) {
            if (mergedOwners.contains(entry.getValue()) && !owner.equals(entry.getValue())) {
                entry.setValue(owner);
                changed = true;
            }
        }

        for (UUID id : connected) {
            if (!owner.equals(ownerByMember.put(id, owner))) {
                changed = true;
            }
        }

        if (changed) {
            setDirty();
        }

        return owner;
    }

    public List<SubLevel> loadedMembers(ServerLevel level, UUID anyMemberOrOwner) {
        UUID owner = groupOf(anyMemberOrOwner);
        SubLevelContainer container = SubLevelContainer.getContainer(level);

        if (container == null) {
            return List.of();
        }

        List<SubLevel> result = new ArrayList<>();

        Set<UUID> ids = new HashSet<>();
        ids.add(owner);

        for (Map.Entry<UUID, UUID> entry : ownerByMember.entrySet()) {
            if (owner.equals(entry.getValue())) {
                ids.add(entry.getKey());
            }
        }

        for (UUID id : ids) {
            SubLevel member = container.getSubLevel(id);

            if (member != null && !member.isRemoved()) {
                result.add(member);
            }
        }

        result.sort(Comparator.comparing(member -> member.getUniqueId().toString()));

        return List.copyOf(result);
    }

    private static SableAssemblyRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        SableAssemblyRegistry data = new SableAssemblyRegistry();
        CompoundTag members = tag.getCompound("Members");

        for (String key : members.getAllKeys()) {
            try {
                data.ownerByMember.put(UUID.fromString(key), UUID.fromString(members.getString(key)));
            } catch (IllegalArgumentException ignored) {}
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag members = new CompoundTag();

        for (Map.Entry<UUID, UUID> entry : ownerByMember.entrySet()) {
            members.putString(entry.getKey().toString(), entry.getValue().toString());
        }

        tag.put("Members", members);
        return tag;
    }
}
