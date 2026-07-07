package com.happysg.radar.block.arad.aradnetworks;

import com.happysg.radar.block.behavior.networks.NetworkData;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ARADData extends SavedData {
    public enum LinkOrigin { DATALINK, CONTACT }

    private static final String DATA_NAME = "arad_network_data";

    public record RwrKey(ResourceKey<Level> dim, BlockPos rwrPos) {}

    public static class Group {
        public final RwrKey key;
        public final Set<BlockPos> monitorEndpoints = new HashSet<>();
        public final Set<BlockPos> dataLinks = new HashSet<>();

        public Group(RwrKey key) {
            this.key = key;
        }
    }

    private final Map<String, Group> groupsByRwr = new HashMap<>();
    private final Map<String, String> monitorToRwr = new HashMap<>();
    private final Map<String, LinkOrigin> endpointOrigins = new HashMap<>();
    private final Map<String, String> dataLinkToRwr = new HashMap<>();
    private final Map<String, String> dataLinkToEndpoint = new HashMap<>();

    public static ARADData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(ARADData::new, ARADData::load, null),
                DATA_NAME
        );
    }

    public Group getOrCreateGroup(ResourceKey<Level> dim, BlockPos rwrPos) {
        String groupKey = key(dim, rwrPos);
        return groupsByRwr.computeIfAbsent(groupKey, ignored -> {
            setDirty();
            return new Group(new RwrKey(dim, rwrPos));
        });
    }

    @Nullable
    public Group getGroup(ResourceKey<Level> dim, BlockPos rwrPos) {
        return groupsByRwr.get(key(dim, rwrPos));
    }

    public boolean isMonitorLinked(ResourceKey<Level> dim, BlockPos monitorPos) {
        return monitorToRwr.containsKey(key(dim, monitorPos));
    }

    public boolean isMonitorDatalinked(ResourceKey<Level> dim, BlockPos monitorPos) {
        return endpointOrigins.get(key(dim, monitorPos)) == LinkOrigin.DATALINK;
    }

    @Nullable
    public LinkOrigin getEndpointOrigin(ResourceKey<Level> dim, BlockPos monitorPos) {
        return endpointOrigins.get(key(dim, monitorPos));
    }

    @Nullable
    public BlockPos getRwrForMonitor(ResourceKey<Level> dim, BlockPos monitorPos) {
        String rwrKey = monitorToRwr.get(key(dim, monitorPos));
        return rwrKey == null ? null : posFromKey(rwrKey);
    }

    @Nullable
    public BlockPos getRwrForDataLink(ResourceKey<Level> dim, BlockPos dataLinkPos) {
        String rwrKey = dataLinkToRwr.get(key(dim, dataLinkPos));
        return rwrKey == null ? null : posFromKey(rwrKey);
    }

    public boolean canAttachMonitor(ServerLevel level, Group group, BlockPos monitorPos) {
        String endpointKey = key(group.key.dim(), monitorPos);
        String existing = monitorToRwr.get(endpointKey);
        String myKey = key(group.key.dim(), group.key.rwrPos());
        if (existing != null && !existing.equals(myKey) && endpointOrigins.get(endpointKey) != LinkOrigin.CONTACT) {
            return false;
        }

        NetworkData radarData = NetworkData.get(level);
        BlockPos radarFilterer = radarData.getFiltererForEndpoint(group.key.dim(), monitorPos);
        if (radarFilterer == null) {
            return true;
        }
        return radarData.getEndpointOrigin(group.key.dim(), monitorPos) != NetworkData.LinkOrigin.DATALINK;
    }

    public void attachMonitor(ServerLevel level, Group group, BlockPos monitorPos, LinkOrigin origin) {
        if (!claimMonitor(level, group, monitorPos, origin)) {
            return;
        }
        group.monitorEndpoints.add(monitorPos);
        notifyMonitor(level, monitorPos);
        setDirty();
    }

    public void addDataLinkToGroup(Group group, BlockPos dataLinkPos, BlockPos monitorPos) {
        String groupKey = key(group.key.dim(), group.key.rwrPos());
        group.dataLinks.add(dataLinkPos);
        dataLinkToRwr.put(key(group.key.dim(), dataLinkPos), groupKey);
        dataLinkToEndpoint.put(key(group.key.dim(), dataLinkPos), key(group.key.dim(), monitorPos));
        setDirty();
    }

    public boolean reconcileContactLinks(ServerLevel level, Group group) {
        if (level == null || group == null || !group.key.dim().equals(level.dimension())) return false;

        Set<BlockPos> reachable = findReachableContactMonitors(level, group);
        boolean changed = false;

        for (BlockPos endpoint : snapshotContactEndpoints(group)) {
            if (!reachable.contains(endpoint)) {
                removeMonitorFromGroup(level, group, endpoint, true);
                changed = true;
            }
        }

        for (BlockPos endpoint : reachable) {
            String endpointKey = key(group.key.dim(), endpoint);
            String owner = monitorToRwr.get(endpointKey);
            String myKey = key(group.key.dim(), group.key.rwrPos());
            if (owner != null && !owner.equals(myKey)) continue;
            if (owner == null) {
                attachMonitor(level, group, endpoint, LinkOrigin.CONTACT);
                changed = true;
            }
        }

        if (changed) setDirty();
        return changed;
    }

    private Set<BlockPos> findReachableContactMonitors(ServerLevel level, Group group) {
        Set<BlockPos> reachable = new HashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        queue.add(group.key.rwrPos());
        visited.add(group.key.rwrPos());

        for (BlockPos endpoint : group.monitorEndpoints) {
            if (endpointOrigins.get(key(group.key.dim(), endpoint)) == LinkOrigin.DATALINK) {
                queue.add(endpoint);
                visited.add(endpoint);
            }
        }

        NetworkData radarData = NetworkData.get(level);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos next = current.relative(dir);
                if (!visited.add(next)) continue;

                BlockPos monitorPos = normalizeMonitor(level, next);
                if (monitorPos == null) continue;
                if (radarData.getEndpointOrigin(group.key.dim(), monitorPos) == NetworkData.LinkOrigin.DATALINK) continue;

                if (reachable.add(monitorPos)) {
                    queue.add(monitorPos);
                }
            }
        }
        return reachable;
    }

    private boolean claimMonitor(ServerLevel level, Group group, BlockPos monitorPos, LinkOrigin origin) {
        String endpointKey = key(group.key.dim(), monitorPos);
        String myKey = key(group.key.dim(), group.key.rwrPos());
        String existing = monitorToRwr.get(endpointKey);

        if (existing != null && !existing.equals(myKey) && endpointOrigins.get(endpointKey) != LinkOrigin.CONTACT) {
            return false;
        }

        if (origin == LinkOrigin.DATALINK) {
            NetworkData radarData = NetworkData.get(level);
            if (radarData.getEndpointOrigin(group.key.dim(), monitorPos) == NetworkData.LinkOrigin.DATALINK) {
                return false;
            }
            if (radarData.getEndpointOrigin(group.key.dim(), monitorPos) == NetworkData.LinkOrigin.CONTACT) {
                radarData.removeContactEndpoint(level, monitorPos);
            }
        }

        if (existing != null && !existing.equals(myKey)) {
            Group oldGroup = groupsByRwr.get(existing);
            if (oldGroup != null) {
                removeMonitorFromGroup(level, oldGroup, monitorPos, false);
            }
        }

        monitorToRwr.put(endpointKey, myKey);
        endpointOrigins.put(endpointKey, origin);
        return true;
    }

    public void removeContactEndpoint(ServerLevel level, BlockPos monitorPos) {
        ResourceKey<Level> dim = level.dimension();
        monitorPos = normalizeMonitor(level, monitorPos);
        if (monitorPos == null) return;
        if (endpointOrigins.get(key(dim, monitorPos)) != LinkOrigin.CONTACT) return;
        String groupKey = monitorToRwr.get(key(dim, monitorPos));
        Group group = groupsByRwr.get(groupKey);
        if (group == null) return;
        removeMonitorFromGroup(level, group, monitorPos, true);
        setDirty();
    }

    public void removeDataLinkAndCleanup(ResourceKey<Level> dim, BlockPos dataLinkPos, @Nullable ServerLevel level) {
        String dlKey = key(dim, dataLinkPos);
        String groupKey = dataLinkToRwr.remove(dlKey);
        String endpointKey = dataLinkToEndpoint.remove(dlKey);
        if (groupKey == null) return;

        Group group = groupsByRwr.get(groupKey);
        if (group != null) {
            group.dataLinks.remove(dataLinkPos);
        }

        if (group != null && endpointKey != null) {
            BlockPos endpoint = posFromKey(endpointKey);
            removeMonitorFromGroup(level, group, endpoint, true);
        }

        cleanupIfEmpty(groupKey);
        setDirty();
    }

    public void onEndpointRemoved(ServerLevel level, BlockPos monitorPos) {
        BlockPos normalized = normalizeMonitor(level, monitorPos);
        if (normalized == null) normalized = monitorPos;
        String endpointKey = key(level.dimension(), normalized);
        String groupKey = monitorToRwr.get(endpointKey);
        if (groupKey == null) return;

        Group group = groupsByRwr.get(groupKey);
        if (group != null) {
            removeMonitorFromGroup(level, group, normalized, false);
        } else {
            monitorToRwr.remove(endpointKey);
            endpointOrigins.remove(endpointKey);
        }
        cleanupIfEmpty(groupKey);
        setDirty();
    }

    public void dissolveRwr(ServerLevel level, BlockPos rwrPos) {
        String groupKey = key(level.dimension(), rwrPos);
        Group group = groupsByRwr.remove(groupKey);
        if (group == null) return;

        for (BlockPos monitor : group.monitorEndpoints) {
            monitorToRwr.remove(key(level.dimension(), monitor));
            endpointOrigins.remove(key(level.dimension(), monitor));
            notifyMonitor(level, monitor);
        }
        for (BlockPos dataLink : group.dataLinks) {
            dataLinkToRwr.remove(key(level.dimension(), dataLink));
            dataLinkToEndpoint.remove(key(level.dimension(), dataLink));
        }
        setDirty();
    }

    public boolean updateMonitorPosition(ResourceKey<Level> dim, BlockPos oldPos, BlockPos newPos) {
        if (oldPos.equals(newPos)) return true;
        String oldKey = key(dim, oldPos);
        String groupKey = monitorToRwr.remove(oldKey);
        LinkOrigin origin = endpointOrigins.remove(oldKey);
        if (groupKey == null) return false;

        Group group = groupsByRwr.get(groupKey);
        if (group == null || !group.monitorEndpoints.remove(oldPos)) {
            monitorToRwr.put(oldKey, groupKey);
            if (origin != null) endpointOrigins.put(oldKey, origin);
            return false;
        }

        group.monitorEndpoints.add(newPos);
        String newKey = key(dim, newPos);
        monitorToRwr.put(newKey, groupKey);
        if (origin != null) endpointOrigins.put(newKey, origin);
        for (Map.Entry<String, String> entry : dataLinkToEndpoint.entrySet()) {
            if (oldKey.equals(entry.getValue())) {
                entry.setValue(newKey);
            }
        }
        setDirty();
        return true;
    }

    public boolean updateDataLinkPosition(ResourceKey<Level> dim, BlockPos oldPos, BlockPos newPos) {
        if (oldPos.equals(newPos)) return true;
        String oldKey = key(dim, oldPos);
        String newKey = key(dim, newPos);
        String groupKey = dataLinkToRwr.remove(oldKey);
        String endpointKey = dataLinkToEndpoint.remove(oldKey);
        if (groupKey == null) return false;

        Group group = groupsByRwr.get(groupKey);
        if (group == null || !group.dataLinks.remove(oldPos)) {
            dataLinkToRwr.put(oldKey, groupKey);
            if (endpointKey != null) dataLinkToEndpoint.put(oldKey, endpointKey);
            return false;
        }

        group.dataLinks.add(newPos);
        dataLinkToRwr.put(newKey, groupKey);
        if (endpointKey != null) dataLinkToEndpoint.put(newKey, endpointKey);
        setDirty();
        return true;
    }

    private List<BlockPos> snapshotContactEndpoints(Group group) {
        List<BlockPos> endpoints = new ArrayList<>();
        for (BlockPos endpoint : group.monitorEndpoints) {
            if (endpointOrigins.get(key(group.key.dim(), endpoint)) == LinkOrigin.CONTACT) {
                endpoints.add(endpoint);
            }
        }
        return endpoints;
    }

    private void removeMonitorFromGroup(@Nullable ServerLevel level, Group group, BlockPos monitorPos, boolean notify) {
        if (!group.monitorEndpoints.remove(monitorPos)) return;
        monitorToRwr.remove(key(group.key.dim(), monitorPos));
        endpointOrigins.remove(key(group.key.dim(), monitorPos));
        if (notify && level != null) {
            notifyMonitor(level, monitorPos);
        }
        cleanupIfEmpty(key(group.key.dim(), group.key.rwrPos()));
    }

    private void cleanupIfEmpty(String groupKey) {
        Group group = groupsByRwr.get(groupKey);
        if (group == null) return;
        if (!group.monitorEndpoints.isEmpty() || !group.dataLinks.isEmpty()) return;
        groupsByRwr.remove(groupKey);
    }

    private static void notifyMonitor(ServerLevel level, BlockPos monitorPos) {
        BlockEntity be = level.getBlockEntity(monitorPos);
        if (be instanceof MonitorBlockEntity monitor) {
            monitor.refreshAradLinkState();
        }
    }

    @Nullable
    private static BlockPos normalizeMonitor(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MonitorBlockEntity monitor)) return null;
        BlockPos controllerPos = monitor.getControllerPos();
        return controllerPos == null ? pos : controllerPos;
    }

    public static ARADData load(CompoundTag root, HolderLookup.Provider registries) {
        ARADData data = new ARADData();

        ListTag groups = root.getList("Groups", Tag.TAG_COMPOUND);
        for (int i = 0; i < groups.size(); i++) {
            CompoundTag groupTag = groups.getCompound(i);
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(groupTag.getString("Dim")));
            BlockPos rwrPos = readPos(groupTag.getCompound("RwrPos"));
            Group group = new Group(new RwrKey(dim, rwrPos));
            String groupKey = key(dim, rwrPos);

            ListTag monitors = groupTag.getList("MonitorEndpoints", Tag.TAG_COMPOUND);
            for (int m = 0; m < monitors.size(); m++) {
                CompoundTag endpoint = monitors.getCompound(m);
                BlockPos monitorPos = readPos(endpoint.getCompound("Pos"));
                LinkOrigin origin = readOrigin(endpoint);
                group.monitorEndpoints.add(monitorPos);
                data.monitorToRwr.put(key(dim, monitorPos), groupKey);
                data.endpointOrigins.put(key(dim, monitorPos), origin);
            }

            ListTag dataLinks = groupTag.getList("DataLinks", Tag.TAG_COMPOUND);
            for (int d = 0; d < dataLinks.size(); d++) {
                CompoundTag link = dataLinks.getCompound(d);
                BlockPos dataLinkPos = readPos(link.getCompound("DataLinkPos"));
                group.dataLinks.add(dataLinkPos);
                data.dataLinkToRwr.put(key(dim, dataLinkPos), groupKey);
                if (link.contains("EndpointPos", Tag.TAG_COMPOUND)) {
                    BlockPos endpoint = readPos(link.getCompound("EndpointPos"));
                    data.dataLinkToEndpoint.put(key(dim, dataLinkPos), key(dim, endpoint));
                }
            }

            data.groupsByRwr.put(groupKey, group);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        ListTag groups = new ListTag();
        for (Group group : groupsByRwr.values()) {
            CompoundTag groupTag = new CompoundTag();
            groupTag.putString("Dim", group.key.dim().location().toString());
            groupTag.put("RwrPos", writePos(group.key.rwrPos()));

            ListTag monitors = new ListTag();
            for (BlockPos monitor : group.monitorEndpoints) {
                CompoundTag endpoint = new CompoundTag();
                endpoint.put("Pos", writePos(monitor));
                LinkOrigin origin = endpointOrigins.get(key(group.key.dim(), monitor));
                endpoint.putString("Origin", origin == null ? LinkOrigin.DATALINK.name() : origin.name());
                monitors.add(endpoint);
            }
            groupTag.put("MonitorEndpoints", monitors);

            ListTag links = new ListTag();
            for (BlockPos dataLink : group.dataLinks) {
                CompoundTag link = new CompoundTag();
                link.put("DataLinkPos", writePos(dataLink));
                String endpointKey = dataLinkToEndpoint.get(key(group.key.dim(), dataLink));
                if (endpointKey != null) {
                    link.put("EndpointPos", writePos(posFromKey(endpointKey)));
                }
                links.add(link);
            }
            groupTag.put("DataLinks", links);
            groups.add(groupTag);
        }
        root.put("Groups", groups);
        return root;
    }

    private static LinkOrigin readOrigin(CompoundTag tag) {
        if (!tag.contains("Origin", Tag.TAG_STRING)) return LinkOrigin.DATALINK;
        try {
            return LinkOrigin.valueOf(tag.getString("Origin"));
        } catch (IllegalArgumentException ignored) {
            return LinkOrigin.DATALINK;
        }
    }

    private static String key(ResourceKey<Level> dim, BlockPos pos) {
        return dim.location() + "|" + pos.asLong();
    }

    private static CompoundTag writePos(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        return tag;
    }

    private static BlockPos readPos(CompoundTag tag) {
        return new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
    }

    private static BlockPos posFromKey(String key) {
        int idx = key.indexOf('|');
        long packed = Long.parseLong(key.substring(idx + 1));
        return BlockPos.of(packed);
    }
}
