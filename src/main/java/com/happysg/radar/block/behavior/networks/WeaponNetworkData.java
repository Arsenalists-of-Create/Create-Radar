package com.happysg.radar.block.behavior.networks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

public class WeaponNetworkData extends SavedData {
    private final Map<String, Group> groups = new HashMap<>();
    private final Map<String, String> controllerToMount = new HashMap<>();

    public WeaponNetworkData() {}

    public static WeaponNetworkData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(WeaponNetworkData::new, WeaponNetworkData::load, net.minecraft.util.datafix.DataFixTypes.LEVEL), "weapon_network_data");
    }

    private static String key(ResourceKey<Level> dim, BlockPos pos) {
        return dim.location().toString() + "|" + pos.asLong();
    }

    public BlockPos getMountForController(ResourceKey<Level> dim, BlockPos pos) {
        String mountKey = controllerToMount.get(key(dim, pos));
        if (mountKey == null) return null;
        int idx = mountKey.indexOf('|');
        return BlockPos.of(Long.parseLong(mountKey.substring(idx + 1)));
    }

    public WeaponGroupView getWeaponGroupViewFromEndpoint(ResourceKey<Level> dim, BlockPos pos) {
        BlockPos mountPos = getMountForController(dim, pos);
        if (mountPos == null) return null;
        Group group = groups.get(key(dim, mountPos));
        if (group == null) return null;
        return new WeaponGroupView(group.yawPos, group.pitchPos, group.firingPos, mountPos);
    }

    public Group getOrCreateGroup(ResourceKey<Level> dim, BlockPos mountPos) {
        String k = key(dim, mountPos);
        return groups.computeIfAbsent(k, s -> {
            setDirty();
            return new Group(new MountKey(dim, mountPos));
        });
    }

    public void removeController(ResourceKey<Level> dim, BlockPos pos) {
        String ck = key(dim, pos);
        String mk = controllerToMount.remove(ck);
        if (mk != null) {
            Group g = groups.get(mk);
            if (g != null) {
                if (pos.equals(g.yawPos)) g.yawPos = null;
                if (pos.equals(g.pitchPos)) g.pitchPos = null;
                if (pos.equals(g.firingPos)) g.firingPos = null;
                g.dataLinks.remove(pos);
            }
            setDirty();
        }
    }

    public boolean tryMergeIntoGroup(Group group, BlockPos yaw, BlockPos pitch, BlockPos fire) {
        ResourceKey<Level> dim = group.key.dim();
        String mk = key(dim, group.key.mountPos());
        if (yaw != null) {
            group.yawPos = yaw;
            controllerToMount.put(key(dim, yaw), mk);
        }
        if (pitch != null) {
            group.pitchPos = pitch;
            controllerToMount.put(key(dim, pitch), mk);
        }
        if (fire != null) {
            group.firingPos = fire;
            controllerToMount.put(key(dim, fire), mk);
        }
        setDirty();
        return true;
    }

    public void addDataLinkToGroup(Group group, BlockPos pos) {
        group.dataLinks.add(pos);
        controllerToMount.put(key(group.key.dim(), pos), key(group.key.dim(), group.key.mountPos()));
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        ListTag groupsList = new ListTag();
        for (Group g : groups.values()) {
            CompoundTag gTag = new CompoundTag();
            gTag.putString("dim", g.key.dim().location().toString());
            gTag.putLong("mount_p", g.key.mountPos().asLong());
            if (g.yawPos != null) gTag.putLong("yaw_p", g.yawPos.asLong());
            if (g.pitchPos != null) gTag.putLong("pitch_p", g.pitchPos.asLong());
            if (g.firingPos != null) gTag.putLong("fire_p", g.firingPos.asLong());

            ListTag dlList = new ListTag();
            for (BlockPos p : g.dataLinks) {
                CompoundTag pTag = new CompoundTag();
                pTag.put("p", NbtUtils.writeBlockPos(p));
                dlList.add(pTag);
            }
            gTag.put("datalinks", dlList);
            groupsList.add(gTag);
        }
        tag.put("groups", groupsList);
        return tag;
    }

    public static WeaponNetworkData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        WeaponNetworkData data = new WeaponNetworkData();
        ListTag groupsList = tag.getList("groups", Tag.TAG_COMPOUND);
        for (int i = 0; i < groupsList.size(); i++) {
            CompoundTag gTag = groupsList.getCompound(i);
            String dimStr = gTag.getString("dim");
            if (dimStr.isEmpty()) continue;
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimStr));
            BlockPos mount = BlockPos.of(gTag.getLong("mount_p"));
            Group g = new Group(new MountKey(dim, mount));
            if (gTag.contains("yaw_p")) g.yawPos = BlockPos.of(gTag.getLong("yaw_p"));
            if (gTag.contains("pitch_p")) g.pitchPos = BlockPos.of(gTag.getLong("pitch_p"));
            if (gTag.contains("fire_p")) g.firingPos = BlockPos.of(gTag.getLong("fire_p"));
            
            ListTag dlList = gTag.getList("datalinks", Tag.TAG_COMPOUND);
            for (int j = 0; j < dlList.size(); j++) {
                NbtUtils.readBlockPos(dlList.getCompound(j), "p").ifPresent(g.dataLinks::add);
            }
            String mk = key(dim, mount);
            data.groups.put(mk, g);
            if (g.yawPos != null) data.controllerToMount.put(key(dim, g.yawPos), mk);
            if (g.pitchPos != null) data.controllerToMount.put(key(dim, g.pitchPos), mk);
            if (g.firingPos != null) data.controllerToMount.put(key(dim, g.firingPos), mk);
            for (BlockPos dl : g.dataLinks) data.controllerToMount.put(key(dim, dl), mk);
        }
        return data;
    }

    public boolean updateWeaponEndpointPosition(ResourceKey<Level> dim, BlockPos oldPos, BlockPos newPos) {
        String mountKey = controllerToMount.remove(key(dim, oldPos));
        if (mountKey != null) {
            controllerToMount.put(key(dim, newPos), mountKey);
            Group g = groups.get(mountKey);
            if (g != null) {
                if (oldPos.equals(g.yawPos)) g.yawPos = newPos;
                if (oldPos.equals(g.pitchPos)) g.pitchPos = newPos;
                if (oldPos.equals(g.firingPos)) g.firingPos = newPos;
                if (g.dataLinks.remove(oldPos)) g.dataLinks.add(newPos);
            }
            setDirty();
            return true;
        }
        return false;
    }

    public Group getGroupForController(ResourceKey<Level> dim, BlockPos pos) {
        String mk = controllerToMount.get(key(dim, pos));
        return mk != null ? groups.get(mk) : null;
    }

    public void tick(ServerLevel sl) {}
    public void removeDataLinkAndCleanup(ResourceKey<Level> dim, BlockPos pos) { removeController(dim, pos); }
    public boolean canMergeIntoGroup(Group group, BlockPos yaw, BlockPos pitch, BlockPos fire) { return true; }
    public Map<String, Group> getGroups() { return groups; }
    public ValidationResult validateAllKnownPositions(ServerLevel level, boolean fix) { return new ValidationResult(0,0,0); }
    public record ValidationResult(int groupsRemoved, int controllersCleared, int dataLinksRemoved) {}

    public static class Group {
        public final MountKey key;
        public BlockPos yawPos;
        public BlockPos pitchPos;
        public BlockPos firingPos;
        public final List<BlockPos> dataLinks = new ArrayList<>();

        public Group(MountKey key) {
            this.key = key;
        }
    }

    public record MountKey(ResourceKey<Level> dim, BlockPos mountPos) {}

    public record WeaponGroupView(BlockPos yawPos, BlockPos pitchPos, BlockPos firingPos, BlockPos mountPos) {}
}