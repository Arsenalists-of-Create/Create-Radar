package com.happysg.radar.block.behavior.networks;

import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import com.happysg.radar.block.behavior.networks.config.IdentificationConfig;
import com.happysg.radar.block.behavior.networks.config.TargetingConfig;
import com.happysg.radar.block.arad.aradnetworks.ARADData;
import com.happysg.radar.block.controller.pitch.AutoPitchControllerBlockEntity;
import com.happysg.radar.block.datalink.DataLinkBlock;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.block.radar.bearing.RadarBearingBlock;
import com.happysg.radar.block.radar.plane.StationaryRadarBlock;
import com.happysg.radar.block.radar.skyradar.SkyRadarBlock;
import com.happysg.radar.block.radar.sonar.bearing.SonarBearingBlock;
import com.happysg.radar.registry.ModBlocks;
import com.mojang.logging.LogUtils;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Function;

public class NetworkData extends SavedData {

    public static final int SCHEMATIC_SNAPSHOT_VERSION = 1;
    public enum RestorationMode { SCHEMATIC_COPY, WORLD_RECOVERY }
    public record RestorationReport(int restored, int skipped) {
        public boolean hasWarnings() { return skipped > 0; }
    }

    public enum RadarKind { BEARING, STATIONARY, SKY, SONAR }
    public enum Mountkind { NORMAL, FIXED, COMPACT}
    public enum LinkOrigin { DATALINK, CONTACT }
    public enum WeaponRelocationResult { UPDATED, NOT_FOUND, CONFLICT }
    private static final String DATA_NAME = "network_data";

    // radarPos -> filtererKey
    private final Map<String, String> radarToFilterer = new HashMap<>();


    public record FilterKey(ResourceKey<Level> dim, BlockPos filtererPos) {}
    public record RadarEndpoint(BlockPos pos, RadarKind kind) {}
    public Set<BlockPos> getWeaponEndpoints(Group group) {
        return Collections.unmodifiableSet(group.weaponEndpoints);
    }
    public Collection<RadarEndpoint> getRadarEndpoints(Group group) {
        return group.getRadarEndpoints();
    }
    public static class Group {
        public final FilterKey key;
        public @Nullable String selectedTargetId;
        public final Set<BlockPos> monitorEndpoints = new HashSet<>();

        public final Map<BlockPos, RadarKind> radarEndpoints = new LinkedHashMap<>();

        public @Nullable BlockPos radarPos;
        public @Nullable RadarKind radarKind;

        /** Controllers linked into this filter group. */
        /** Controllers linked into this filter group. */
        public final Set<BlockPos> weaponEndpoints = new HashSet<>();

        /** Used weapon mounts (constraint set). */
        public final Set<BlockPos> usedWeaponMounts = new HashSet<>();

        /** DataLink blocks belonging to this group. */
        public final Set<BlockPos> dataLinks = new HashSet<>();

        /** Filter tags */
        public CompoundTag targetingTag = defaultTargetingTag();
        public CompoundTag identificationTag = defaultIdentificationTag();
        public CompoundTag detectionTag = defaultDetectionTag();

        public Group(FilterKey key) {
            this.key = key;
        }

        public Collection<RadarEndpoint> getRadarEndpoints() {
            List<RadarEndpoint> endpoints = new ArrayList<>(radarEndpoints.size());
            for (Map.Entry<BlockPos, RadarKind> entry : radarEndpoints.entrySet()) {
                endpoints.add(new RadarEndpoint(entry.getKey(), entry.getValue()));
            }
            return Collections.unmodifiableList(endpoints);
        }

        private void syncPrimaryRadar() {
            if (radarEndpoints.isEmpty()) {
                radarPos = null;
                radarKind = null;
                return;
            }
            Map.Entry<BlockPos, RadarKind> first = radarEndpoints.entrySet().iterator().next();
            radarPos = first.getKey();
            radarKind = first.getValue();
        }
    }



    // filtererKey -> group
    private final Map<String, Group> groupsByFilterer = new HashMap<>();

    // endpointPos -> filtererKey
    private final Map<String, String> endpointToFilterer = new HashMap<>();

    // endpointPos -> origin, so contact links can be pruned and datalinks can override them
    private final Map<String, LinkOrigin> endpointOrigins = new HashMap<>();

    // weaponMountPos -> filtererKey (enforces uniqueness)
    private final Map<String, String> weaponMountToFilterer = new HashMap<>();

    // datalinkPos -> filtererKey
    private final Map<String, String> dataLinkToFilterer = new HashMap<>();

    // datalinkPos -> endpointPos
    private final Map<String, String> dataLinkToEndpoint = new HashMap<>();

    // controllerPos -> weaponMountPos  (PERSISTED so cleanup works)
    private final Map<String, String> controllerToWeaponMount = new HashMap<>();


    public static NetworkData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(
                        NetworkData::new,
                        NetworkData::load,
                        null
                ),
                DATA_NAME
        );
    }
    public void setSelectedTargetId(Group g, @Nullable String id) {
        g.selectedTargetId = id;
        setDirty();
    }

    @Nullable
    public String getSelectedTargetId(Group g) {
        return g.selectedTargetId;
    }

    public NetworkData() {}
    public void dissolveNetworkForBrokenController(ServerLevel level, BlockPos brokenPos) {
        ResourceKey<Level> dim = level.dimension();

        // 1) If the broken block is the filterer/controller itself, dissolve that group
        String filtererKey = filtererKey(new FilterKey(dim, brokenPos));
        if (groupsByFilterer.containsKey(filtererKey)) {
            clearSafeZonesForBrokenController(level, groupsByFilterer.get(filtererKey));
            dissolveGroup(level, filtererKey);
            setDirty();
            return;
        }


        String controllerKey = posKey(dim, brokenPos);
        String mountKey = controllerToWeaponMount.remove(controllerKey);
        if (mountKey != null) {
            String owningFiltererKey = weaponMountToFilterer.get(mountKey);
            if (owningFiltererKey != null && groupsByFilterer.containsKey(owningFiltererKey)) {
                dissolveGroup(level, owningFiltererKey);
                setDirty();
            }
        }
    }

    private void clearSafeZonesForBrokenController(ServerLevel level, Group group) {
        if (group == null) {
            return;
        }
        Set<BlockPos> clearedControllers = new HashSet<>();
        for (BlockPos monitorPos : group.monitorEndpoints) {
            if (!(level.getBlockEntity(monitorPos) instanceof MonitorBlockEntity monitor)) {
                continue;
            }
            MonitorBlockEntity controller = monitor.getController();
            if (clearedControllers.add(controller.getBlockPos())) {
                controller.clearSafeZones();
            }
        }
    }

    private void dissolveGroup(ServerLevel level, String filtererKey) {
        Group group = groupsByFilterer.remove(filtererKey);
        if (group == null) return;

        // tell loaded nodes they are no longer linked (optional but nice)
        for (BlockPos p : group.monitorEndpoints) {
            notifyNodeDisconnected(level, p);
            endpointOrigins.remove(posKey(level.dimension(), p));
        }
        for (BlockPos p : group.radarEndpoints.keySet()) {
            notifyNodeDisconnected(level, p);
            endpointOrigins.remove(posKey(level.dimension(), p));
        }
        //notifyNodeDisconnected(level, group.);

        for (BlockPos endpointPos : group.weaponEndpoints) {
            notifyNodeDisconnected(level, endpointPos);
            endpointToFilterer.remove(posKey(level.dimension(), endpointPos));
            endpointOrigins.remove(posKey(level.dimension(), endpointPos));
        }


        for (BlockPos mountPos : group.usedWeaponMounts) {
            String mountKey = posKey(level.dimension(), mountPos);
            weaponMountToFilterer.remove(mountKey);

            // remove any controller->mount mappings that point at this mount
            controllerToWeaponMount.entrySet().removeIf(e -> mountKey.equals(e.getValue()));
        }

        for (BlockPos dlPos : group.dataLinks) {
            String dlKey = posKey(level.dimension(), dlPos);
            dataLinkToFilterer.remove(dlKey);
            dataLinkToEndpoint.remove(dlKey);
            notifyNodeDisconnected(level, dlPos);
        }

        // remove the group key itself from any other indexes if needed
        // (most of your indexes are removed above; this is just a safety sweep)
        endpointToFilterer.values().removeIf(filtererKey::equals);
        weaponMountToFilterer.values().removeIf(filtererKey::equals);
        dataLinkToFilterer.values().removeIf(filtererKey::equals);
    }

    /**
     * Soft callback for anything that cares about network disconnect.
     * Safe even if the BE doesn't implement anything.
     */
    private void notifyNodeDisconnected(ServerLevel level, @Nullable BlockPos pos) {
        if (pos == null) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof INetworkNode node) {
            node.onNetworkDisconnected();
        }
    }

// ------------------------------------------------------------
// Key helpers (string keys used by your maps)
// ------------------------------------------------------------
@Nullable
public static BlockPos getFiltererPosFromGroupKey(@Nullable String filtererKey) {
    if (filtererKey == null || filtererKey.isEmpty()) return null;
    return posFromKey(filtererKey);
}
    public Map<String, Group> getGroups() {
        return Collections.unmodifiableMap(groupsByFilterer);
    }

    private static String filtererKey(FilterKey key) {
        return key.dim().location() + "|" + key.filtererPos().asLong();
    }

    private static String posKey(ResourceKey<Level> dim, BlockPos pos) {
        return dim.location() + "|" + pos.asLong();
    }
    // ------------------------------------------------------------
    // Save / Load
    // ------------------------------------------------------------

    public static NetworkData load(CompoundTag root, HolderLookup.Provider registries) {
        NetworkData data = new NetworkData();

        // Groups
        ListTag groupsTag = root.getList("Groups", Tag.TAG_COMPOUND);
        for (int i = 0; i < groupsTag.size(); i++) {
            CompoundTag g = groupsTag.getCompound(i);

            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(g.getString("Dim")));
            BlockPos filtererPos = readPos(g.getCompound("FiltererPos"));
            String groupKey = key(dim, filtererPos);

            Group group = new Group(new FilterKey(dim, filtererPos));

            group.targetingTag = g.contains("Targeting", Tag.TAG_COMPOUND) ? g.getCompound("Targeting") : defaultTargetingTag();
            group.identificationTag = g.contains("Identification", Tag.TAG_COMPOUND) ? g.getCompound("Identification") : defaultIdentificationTag();
            group.detectionTag = g.contains("Detection", Tag.TAG_COMPOUND) ? g.getCompound("Detection") : defaultDetectionTag();
            group.selectedTargetId = g.contains("SelectedTargetId", Tag.TAG_STRING) ? g.getString("SelectedTargetId") : null;


            if (g.contains("MonitorEndpoints", Tag.TAG_LIST)) {
                ListTag list = g.getList("MonitorEndpoints", Tag.TAG_COMPOUND);
                for (int mi = 0; mi < list.size(); mi++) {
                    BlockPos p = readPos(list.getCompound(mi));
                    group.monitorEndpoints.add(p);
                    data.endpointToFilterer.put(key(dim, p), groupKey);
                    data.endpointOrigins.put(key(dim, p), LinkOrigin.DATALINK);
                }
            }
// LEGACY single-monitor worlds
            else if (g.contains("MonitorPos", Tag.TAG_COMPOUND)) {
                BlockPos p = readPos(g.getCompound("MonitorPos"));
                group.monitorEndpoints.add(p);
                data.endpointToFilterer.put(key(dim, p), groupKey);
                data.endpointOrigins.put(key(dim, p), LinkOrigin.DATALINK);
            }

            if (g.contains("Radars", Tag.TAG_LIST)) {
                ListTag radars = g.getList("Radars", Tag.TAG_COMPOUND);
                for (int ri = 0; ri < radars.size(); ri++) {
                    CompoundTag radarTag = radars.getCompound(ri);
                    BlockPos radarPos = readPos(radarTag.getCompound("Pos"));
                    RadarKind radarKind = RadarKind.valueOf(radarTag.getString("Kind"));
                    group.radarEndpoints.put(radarPos, radarKind);
                    data.endpointToFilterer.put(key(dim, radarPos), groupKey);
                    data.endpointOrigins.put(key(dim, radarPos), LinkOrigin.DATALINK);
                }
                group.syncPrimaryRadar();
            } else if (g.contains("RadarPos", Tag.TAG_COMPOUND)) {
                BlockPos radarPos = readPos(g.getCompound("RadarPos"));
                RadarKind radarKind = RadarKind.valueOf(g.getString("RadarKind"));
                group.radarEndpoints.put(radarPos, radarKind);
                group.syncPrimaryRadar();
                data.endpointToFilterer.put(key(dim, radarPos), groupKey);
                data.endpointOrigins.put(key(dim, radarPos), LinkOrigin.DATALINK);
            }

            // weapon endpoints
            ListTag weapons = g.getList("WeaponEndpoints", Tag.TAG_COMPOUND);
            for (int w = 0; w < weapons.size(); w++) {
                BlockPos ep = readPos(weapons.getCompound(w));
                group.weaponEndpoints.add(ep);
                data.endpointToFilterer.put(key(dim, ep), groupKey);
                data.endpointOrigins.put(key(dim, ep), LinkOrigin.DATALINK);
            }

            // used mounts
            ListTag usedMounts = g.getList("UsedWeaponMounts", Tag.TAG_COMPOUND);
            for (int m = 0; m < usedMounts.size(); m++) {
                BlockPos mp = readPos(usedMounts.getCompound(m));
                group.usedWeaponMounts.add(mp);
                data.weaponMountToFilterer.put(key(dim, mp), groupKey);
            }

            // datalinks
            ListTag links = g.getList("DataLinks", Tag.TAG_COMPOUND);
            for (int d = 0; d < links.size(); d++) {
                BlockPos lp = readPos(links.getCompound(d));
                group.dataLinks.add(lp);
                data.dataLinkToFilterer.put(key(dim, lp), groupKey);
            }

            data.groupsByFilterer.put(groupKey, group);
        }

        if (root.contains("EndpointOrigins", Tag.TAG_LIST)) {
            ListTag list = root.getList("EndpointOrigins", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                String endpoint = e.getString("Endpoint");
                String originName = e.getString("Origin");
                if (!endpoint.isEmpty() && !originName.isEmpty()) {
                    data.endpointOrigins.put(endpoint, LinkOrigin.valueOf(originName));
                }
            }
        }

        // dataLinkToEndpoint
        if (root.contains("DataLinkToEndpoint", Tag.TAG_LIST)) {
            ListTag list = root.getList("DataLinkToEndpoint", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                String dl = e.getString("DL");
                String ep = e.getString("EP");
                if (!dl.isEmpty() && !ep.isEmpty()) data.dataLinkToEndpoint.put(dl, ep);
            }
        }

        // controllerToWeaponMount
        if (root.contains("ControllerToWeaponMount", Tag.TAG_LIST)) {
            ListTag list = root.getList("ControllerToWeaponMount", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                String c = e.getString("C");
                String m = e.getString("M");
                if (!c.isEmpty() && !m.isEmpty()) data.controllerToWeaponMount.put(c, m);
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        ListTag groupsTag = new ListTag();

        for (Group group : groupsByFilterer.values()) {

            CompoundTag g = new CompoundTag();
            g.putString("Dim", group.key.dim().location().toString());
            g.put("FiltererPos", writePos(group.key.filtererPos()));

            g.put("Targeting", group.targetingTag);
            g.put("Identification", group.identificationTag);
            g.put("Detection", group.detectionTag);
            if (group.selectedTargetId != null)
                g.putString("SelectedTargetId", group.selectedTargetId);

            if (!group.monitorEndpoints.isEmpty()) {
                ListTag list = new ListTag();
                for (BlockPos p : group.monitorEndpoints) {
                    list.add(writePos(p));
                }
                g.put("MonitorEndpoints", list);
            }

            if (!group.radarEndpoints.isEmpty()) {
                ListTag radars = new ListTag();
                for (Map.Entry<BlockPos, RadarKind> entry : group.radarEndpoints.entrySet()) {
                    CompoundTag radarTag = new CompoundTag();
                    radarTag.put("Pos", writePos(entry.getKey()));
                    radarTag.putString("Kind", entry.getValue().name());
                    radars.add(radarTag);
                }
                g.put("Radars", radars);

                if (group.radarPos != null && group.radarKind != null) {
                    g.put("RadarPos", writePos(group.radarPos));
                    g.putString("RadarKind", group.radarKind.name());
                }
            }

            ListTag weapons = new ListTag();
            for (BlockPos ep : group.weaponEndpoints) weapons.add(writePos(ep));
            g.put("WeaponEndpoints", weapons);

            ListTag usedMounts = new ListTag();
            for (BlockPos mp : group.usedWeaponMounts) usedMounts.add(writePos(mp));
            g.put("UsedWeaponMounts", usedMounts);

            ListTag links = new ListTag();
            for (BlockPos lp : group.dataLinks) links.add(writePos(lp));
            g.put("DataLinks", links);

            groupsTag.add(g);
        }

        root.put("Groups", groupsTag);

        ListTag origins = new ListTag();
        for (var e : endpointOrigins.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("Endpoint", e.getKey());
            t.putString("Origin", e.getValue().name());
            origins.add(t);
        }
        root.put("EndpointOrigins", origins);

        // Persist dataLinkToEndpoint
        ListTag dl2ep = new ListTag();
        for (var e : dataLinkToEndpoint.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("DL", e.getKey());
            t.putString("EP", e.getValue());
            dl2ep.add(t);
        }
        root.put("DataLinkToEndpoint", dl2ep);

        // Persist controllerToWeaponMount
        ListTag c2m = new ListTag();
        for (var e : controllerToWeaponMount.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("C", e.getKey());
            t.putString("M", e.getValue());
            c2m.add(t);
        }
        root.put("ControllerToWeaponMount", c2m);

        return root;
    }

    // ------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------

    public @Nullable Group getGroup(ResourceKey<Level> dim, BlockPos filtererPos) {
        return groupsByFilterer.get(key(dim, filtererPos));
    }

    public Group getOrCreateGroup(ResourceKey<Level> dim, BlockPos filtererPos) {
        String k = key(dim, filtererPos);
        return groupsByFilterer.computeIfAbsent(k, ignored -> {
            setDirty();
            return new Group(new FilterKey(dim, filtererPos));
        });
    }

    public @Nullable BlockPos getFiltererForEndpoint(ResourceKey<Level> dim, BlockPos endpointPos) {
        String fk = endpointToFilterer.get(key(dim, endpointPos));
        return fk == null ? null : posFromKey(fk);
    }

    public @Nullable BlockPos getFiltererForWeaponMount(ResourceKey<Level> dim, BlockPos weaponMountPos) {
        String fk = weaponMountToFilterer.get(key(dim, weaponMountPos));
        return fk == null ? null : posFromKey(fk);
    }

    public @Nullable BlockPos getFiltererForDataLink(ResourceKey<Level> dim, BlockPos dataLinkPos) {
        String fk = dataLinkToFilterer.get(key(dim, dataLinkPos));
        return fk == null ? null : posFromKey(fk);
    }

    public @Nullable BlockPos getWeaponMountForController(ResourceKey<Level> dim, BlockPos controllerPos) {
        String mountKey = controllerToWeaponMount.get(key(dim, controllerPos));
        return mountKey == null ? null : posFromKey(mountKey);
    }

    public @Nullable LinkOrigin getEndpointOrigin(ResourceKey<Level> dim, BlockPos endpointPos) {
        return endpointOrigins.get(key(dim, endpointPos));
    }

    public boolean isEndpointLinked(ResourceKey<Level> dim, BlockPos endpointPos) {
        return endpointToFilterer.containsKey(key(dim, endpointPos));
    }

    // ------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------

    public boolean canAttachMonitor(Group group, BlockPos monitorPos) {
        return canClaimEndpoint(group, monitorPos, LinkOrigin.DATALINK);
    }

    public boolean canAttachRadar(Group group, BlockPos radarPos, RadarKind kind) {
        RadarKind existingKind = group.radarEndpoints.get(radarPos);
        if (existingKind != null && existingKind != kind) return false;
        return canClaimEndpoint(group, radarPos, LinkOrigin.DATALINK);
    }

    public boolean canAttachWeaponEndpoint(Group group, BlockPos controllerPos, BlockPos weaponMountPos) {
        // controller already owned by other group?
        if (!canClaimEndpoint(group, controllerPos, LinkOrigin.DATALINK)) return false;

        // mount already owned by other group?
        String mountK = key(group.key.dim(), weaponMountPos);
        String existingMountOwner = weaponMountToFilterer.get(mountK);
        String myKey = key(group.key.dim(), group.key.filtererPos());
        if (existingMountOwner != null && !existingMountOwner.equals(myKey)) {
            String endpointK = key(group.key.dim(), controllerPos);
            if (endpointOrigins.get(endpointK) != LinkOrigin.CONTACT) return false;
        }

        // in-group uniqueness
        return !group.usedWeaponMounts.contains(weaponMountPos) || group.weaponEndpoints.contains(controllerPos);
    }

    private boolean canClaimEndpoint(Group group, BlockPos endpointPos, LinkOrigin newOrigin) {
        String endpointKey = key(group.key.dim(), endpointPos);
        String existing = endpointToFilterer.get(endpointKey);
        String myKey = key(group.key.dim(), group.key.filtererPos());
        if (existing == null || existing.equals(myKey)) return true;
        return newOrigin == LinkOrigin.DATALINK && endpointOrigins.get(endpointKey) == LinkOrigin.CONTACT;
    }

    // ------------------------------------------------------------
    // Mutations (commit)
    // ------------------------------------------------------------

    public void attachMonitor(ServerLevel level, Group group, BlockPos clickedPos) {
        ResourceKey<Level> dim = group.key.dim();

        BlockPos controllerPos = clickedPos;
        if (level != null) {
            BlockEntity be = level.getBlockEntity(clickedPos);
            if (be instanceof MonitorBlockEntity m) {
                BlockPos c = m.getControllerPos();
                if (c != null) controllerPos = c;
            }
        }

        if (!claimEndpointForGroup(level, group, controllerPos, LinkOrigin.DATALINK)) return;
        group.monitorEndpoints.add(controllerPos);

        setDirty();
    }


    public void attachRadar(Group group, BlockPos radarPos, RadarKind kind) {
        if (!claimEndpointForGroup(null, group, radarPos, LinkOrigin.DATALINK)) return;
        group.radarEndpoints.put(radarPos, kind);
        group.syncPrimaryRadar();

        setDirty();
    }


    public void attachWeaponEndpoint(Group group, BlockPos controllerPos, BlockPos weaponMountPos) {
        if (!claimEndpointForGroup(null, group, controllerPos, LinkOrigin.DATALINK)) return;
        group.weaponEndpoints.add(controllerPos);
        group.usedWeaponMounts.add(weaponMountPos);

        endpointToFilterer.put(key(group.key.dim(), controllerPos), key(group.key.dim(), group.key.filtererPos()));
        endpointOrigins.put(key(group.key.dim(), controllerPos), LinkOrigin.DATALINK);
        weaponMountToFilterer.put(key(group.key.dim(), weaponMountPos), key(group.key.dim(), group.key.filtererPos()));

        // controller -> mount mapping
        controllerToWeaponMount.put(key(group.key.dim(), controllerPos), key(group.key.dim(), weaponMountPos));

        setDirty();
    }

    public void addDataLinkToGroup(Group group, BlockPos dataLinkPos, BlockPos endpointPos) {
        String gk = key(group.key.dim(), group.key.filtererPos());
        group.dataLinks.add(dataLinkPos);
        dataLinkToFilterer.put(key(group.key.dim(), dataLinkPos), gk);
        dataLinkToEndpoint.put(key(group.key.dim(), dataLinkPos), key(group.key.dim(), endpointPos));
        setDirty();
    }

    public boolean reconcileContactLinks(ServerLevel level, Group group) {
        if (level == null || group == null || !group.key.dim().equals(level.dimension())) return false;

        Set<BlockPos> reachable = findReachableContactEndpoints(level, group);
        boolean changed = false;

        for (BlockPos endpoint : snapshotContactEndpoints(group)) {
            if (!reachable.contains(endpoint)) {
                removeEndpointFromGroup(level, group, endpoint, true);
                changed = true;
            }
        }

        for (BlockPos endpoint : reachable) {
            ContactEndpoint contact = classifyContactEndpoint(level, endpoint);
            if (contact == null) continue;

            String endpointKey = key(group.key.dim(), contact.endpointPos());
            String owner = endpointToFilterer.get(endpointKey);
            String myKey = key(group.key.dim(), group.key.filtererPos());
            if (owner != null && !owner.equals(myKey)) continue;

            if (owner == null) {
                if (attachContactEndpoint(level, group, contact)) changed = true;
            }
        }

        if (changed) setDirty();
        return changed;
    }

    private Set<BlockPos> findReachableContactEndpoints(ServerLevel level, Group group) {
        Set<BlockPos> reachable = new HashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        queue.add(group.key.filtererPos());
        visited.add(group.key.filtererPos());

        for (BlockPos endpoint : allEndpoints(group)) {
            if (endpointOrigins.get(key(group.key.dim(), endpoint)) == LinkOrigin.DATALINK) {
                queue.add(endpoint);
                visited.add(endpoint);
            }
        }

        String myKey = key(group.key.dim(), group.key.filtererPos());
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();

            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos next = current.relative(dir);
                if (!visited.add(next)) continue;

                ContactEndpoint contact = classifyContactEndpoint(level, next);
                if (contact == null) continue;
                if (contact.kind() == ContactKind.MONITOR
                        && ARADData.get(level).isMonitorDatalinked(group.key.dim(), contact.endpointPos())) {
                    continue;
                }

                String endpointKey = key(group.key.dim(), contact.endpointPos());
                String owner = endpointToFilterer.get(endpointKey);
                if (owner != null && !owner.equals(myKey)) continue;

                if (reachable.add(contact.endpointPos())) {
                    queue.add(contact.endpointPos());
                }
            }
        }

        return reachable;
    }

    private Collection<BlockPos> allEndpoints(Group group) {
        Set<BlockPos> endpoints = new HashSet<>();
        endpoints.addAll(group.monitorEndpoints);
        endpoints.addAll(group.radarEndpoints.keySet());
        endpoints.addAll(group.weaponEndpoints);
        return endpoints;
    }

    private Collection<BlockPos> snapshotContactEndpoints(Group group) {
        List<BlockPos> endpoints = new ArrayList<>();
        for (BlockPos endpoint : allEndpoints(group)) {
            if (endpointOrigins.get(key(group.key.dim(), endpoint)) == LinkOrigin.CONTACT) {
                endpoints.add(endpoint);
            }
        }
        return endpoints;
    }

    private boolean attachContactEndpoint(ServerLevel level, Group group, ContactEndpoint endpoint) {
        if (!claimEndpointForGroup(level, group, endpoint.endpointPos(), LinkOrigin.CONTACT)) return false;

        switch (endpoint.kind()) {
            case MONITOR -> group.monitorEndpoints.add(endpoint.endpointPos());
            case RADAR -> {
                group.radarEndpoints.put(endpoint.endpointPos(), endpoint.radarKind());
                group.syncPrimaryRadar();
            }
            case WEAPON -> {
                group.weaponEndpoints.add(endpoint.endpointPos());
                if (endpoint.weaponMountPos() != null) {
                    group.usedWeaponMounts.add(endpoint.weaponMountPos());
                    weaponMountToFilterer.put(key(group.key.dim(), endpoint.weaponMountPos()), key(group.key.dim(), group.key.filtererPos()));
                    controllerToWeaponMount.put(key(group.key.dim(), endpoint.endpointPos()), key(group.key.dim(), endpoint.weaponMountPos()));
                }
            }
        }
        return true;
    }

    @Nullable
    private ContactEndpoint classifyContactEndpoint(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof MonitorBlockEntity monitor) {
            BlockPos controllerPos = monitor.getControllerPos();
            return new ContactEndpoint(ContactKind.MONITOR, controllerPos == null ? pos : controllerPos, null, null);
        }

        if (state.getBlock() instanceof RadarBearingBlock) {
            return new ContactEndpoint(ContactKind.RADAR, pos, RadarKind.BEARING, null);
        }
        if (state.getBlock() instanceof StationaryRadarBlock) {
            return new ContactEndpoint(ContactKind.RADAR, pos, RadarKind.STATIONARY, null);
        }
        if (state.getBlock() instanceof SkyRadarBlock) {
            return new ContactEndpoint(ContactKind.RADAR, pos, RadarKind.SKY, null);
        }
        if (state.getBlock() instanceof SonarBearingBlock) {
            return new ContactEndpoint(ContactKind.RADAR, pos, RadarKind.SONAR, null);
        }

        if (be instanceof AutoPitchControllerBlockEntity) {
            BlockPos mountPos = WeaponNetworkRuntime.get(level).getMountForController(pos);
            if (mountPos != null) {
                return new ContactEndpoint(ContactKind.WEAPON, pos, null, mountPos);
            }
        }

        return null;
    }

    private static boolean isSonarBlock(BlockState state) {
        Class<?> blockClass = state.getBlock().getClass();
        Package blockPackage = blockClass.getPackage();
        return blockPackage != null
                && blockPackage.getName().contains(".radar.sonar")
                && blockClass.getSimpleName().contains("Sonar");
    }

    private boolean claimEndpointForGroup(@Nullable ServerLevel level, Group group, BlockPos endpointPos, LinkOrigin origin) {
        String endpointKey = key(group.key.dim(), endpointPos);
        String myKey = key(group.key.dim(), group.key.filtererPos());
        String existing = endpointToFilterer.get(endpointKey);

        if (existing != null && !existing.equals(myKey) && endpointOrigins.get(endpointKey) != LinkOrigin.CONTACT) {
            return false;
        }

        if (existing != null && !existing.equals(myKey)) {
            Group oldGroup = groupsByFilterer.get(existing);
            if (oldGroup != null) {
                removeEndpointFromGroup(level, oldGroup, endpointPos, false);
            }
        }

        endpointToFilterer.put(endpointKey, myKey);
        endpointOrigins.put(endpointKey, origin);
        return true;
    }

    public void removeContactEndpoint(ServerLevel level, BlockPos endpointPos) {
        if (level == null || endpointPos == null) return;
        ResourceKey<Level> dim = level.dimension();
        BlockEntity be = level.getBlockEntity(endpointPos);
        if (be instanceof MonitorBlockEntity monitor) {
            BlockPos controllerPos = monitor.getControllerPos();
            if (controllerPos != null) endpointPos = controllerPos;
        }

        String endpointKey = key(dim, endpointPos);
        if (endpointOrigins.get(endpointKey) != LinkOrigin.CONTACT) return;

        String filtererKey = endpointToFilterer.get(endpointKey);
        Group group = groupsByFilterer.get(filtererKey);
        if (group == null) return;

        removeEndpointFromGroup(level, group, endpointPos, true);
        setDirty();
    }

    private void removeEndpointFromGroup(@Nullable ServerLevel level, Group group, BlockPos endpointPos, boolean notify) {
        ResourceKey<Level> dim = group.key.dim();
        String endpointKey = key(dim, endpointPos);

        BlockPos normalized = endpointPos;
        if (level != null && level.getBlockEntity(endpointPos) instanceof MonitorBlockEntity monitor) {
            BlockPos controllerPos = monitor.getControllerPos();
            if (controllerPos != null) normalized = controllerPos;
        }

        boolean removed = group.monitorEndpoints.remove(normalized) || group.monitorEndpoints.remove(endpointPos);
        if (removed && notify && level != null && level.getBlockEntity(normalized) instanceof MonitorBlockEntity monitor) {
            monitor.onNetworkDisconnected();
        }

        if (group.radarEndpoints.remove(endpointPos) != null || group.radarEndpoints.remove(normalized) != null) {
            group.syncPrimaryRadar();
            removed = true;
        }

        if (group.weaponEndpoints.remove(endpointPos) || group.weaponEndpoints.remove(normalized)) {
            String mountKey = controllerToWeaponMount.remove(endpointKey);
            if (mountKey != null) {
                group.usedWeaponMounts.remove(posFromKey(mountKey));
                weaponMountToFilterer.remove(mountKey);
            }
            removed = true;
        }

        if (removed) {
            endpointToFilterer.remove(endpointKey);
            endpointOrigins.remove(endpointKey);
            endpointOrigins.remove(key(dim, normalized));
            cleanupIfEmpty(key(dim, group.key.filtererPos()));
        }
    }

    private enum ContactKind { MONITOR, RADAR, WEAPON }

    private record ContactEndpoint(
            ContactKind kind,
            BlockPos endpointPos,
            @Nullable RadarKind radarKind,
            @Nullable BlockPos weaponMountPos
    ) {}

    public void retargetEndpoint(ResourceKey<Level> dim, BlockPos oldEndpoint, BlockPos newEndpoint) {
        if (oldEndpoint == null || newEndpoint == null || oldEndpoint.equals(newEndpoint))
            return;

        String oldK = key(dim, oldEndpoint);
        String newK = key(dim, newEndpoint);


        String filtererKey = endpointToFilterer.remove(oldK);
        if (filtererKey == null) return;


        endpointToFilterer.put(newK, filtererKey);
        LinkOrigin origin = endpointOrigins.remove(oldK);
        if (origin != null) endpointOrigins.put(newK, origin);

        Group group = groupsByFilterer.get(filtererKey);
        if (group != null) {
            if (group.monitorEndpoints.remove(oldEndpoint)) {
                group.monitorEndpoints.add(newEndpoint);
            }
            RadarKind movedRadarKind = group.radarEndpoints.remove(oldEndpoint);
            if (movedRadarKind != null) {
                group.radarEndpoints.put(newEndpoint, movedRadarKind);
                group.syncPrimaryRadar();
            }

            if (group.weaponEndpoints.remove(oldEndpoint)) {
                group.weaponEndpoints.add(newEndpoint);
            }
        }

        for (Map.Entry<String, String> e : dataLinkToEndpoint.entrySet()) {
            if (oldK.equals(e.getValue())) {
                e.setValue(newK);
            }
        }

        setDirty();
    }

    private static final Logger LOGGER = LogUtils.getLogger();

// In NetworkData
    @Nullable
    public BlockPos peekEndpointForDataLink(ResourceKey<Level> dim, BlockPos dataLinkPos) {
        String endpointKey = dataLinkToEndpoint.get(key(dim, dataLinkPos));
        return endpointKey == null ? null : posFromKey(endpointKey);
    }

    public boolean updateDataLinkPosition(ResourceKey<Level> dim, BlockPos oldPos, BlockPos newPos) {
        if (oldPos.equals(newPos)) return true;

        String oldKey = key(dim, oldPos);
        String newKey = key(dim, newPos);
        String alreadyMovedFilterer = dataLinkToFilterer.get(newKey);
        if (!dataLinkToFilterer.containsKey(oldKey)) {
            return alreadyMovedFilterer != null;
        }
        String oldFilterer = dataLinkToFilterer.get(oldKey);
        if (alreadyMovedFilterer != null && !alreadyMovedFilterer.equals(oldFilterer)) {
            return false;
        }
        String oldEndpoint = dataLinkToEndpoint.get(oldKey);
        String newEndpoint = dataLinkToEndpoint.get(newKey);
        if (newEndpoint != null && !Objects.equals(newEndpoint, oldEndpoint)) {
            return false;
        }

        String filtererKey = dataLinkToFilterer.remove(oldKey);
        String endpointKey = dataLinkToEndpoint.remove(oldKey);
        if (filtererKey == null) return false;

        Group group = groupsByFilterer.get(filtererKey);
        if (group == null) {
            dataLinkToFilterer.put(oldKey, filtererKey);
            if (endpointKey != null) dataLinkToEndpoint.put(oldKey, endpointKey);
            return false;
        }

        if (!group.dataLinks.remove(oldPos)) {
            dataLinkToFilterer.put(oldKey, filtererKey);
            if (endpointKey != null) dataLinkToEndpoint.put(oldKey, endpointKey);
            return false;
        }

        group.dataLinks.add(newPos);
        dataLinkToFilterer.put(newKey, filtererKey);
        if (endpointKey != null) dataLinkToEndpoint.put(newKey, endpointKey);
        setDirty();
        return true;
    }

    /**
     * Atomically relocates a radar-owned weapon controller and its mount. This
     * is intentionally separate from the generic endpoint relocation because a
     * weapon move must keep every ownership index in lock-step.
     */
    public WeaponRelocationResult relocateWeaponEndpoint(
            ResourceKey<Level> dim,
            BlockPos oldController,
            BlockPos newController,
            BlockPos oldMount,
            BlockPos newMount
    ) {
        if (oldController == null || newController == null || oldMount == null || newMount == null) {
            return WeaponRelocationResult.NOT_FOUND;
        }

        String oldEndpointKey = key(dim, oldController);
        String newEndpointKey = key(dim, newController);
        String oldMountKey = key(dim, oldMount);
        String newMountKey = key(dim, newMount);

        String filtererKey = endpointToFilterer.get(oldEndpointKey);
        if (filtererKey == null) {
            filtererKey = endpointToFilterer.get(newEndpointKey);
        }
        if (filtererKey == null) {
            return WeaponRelocationResult.NOT_FOUND;
        }

        Group group = groupsByFilterer.get(filtererKey);
        if (group == null) {
            return WeaponRelocationResult.NOT_FOUND;
        }

        String newEndpointOwner = endpointToFilterer.get(newEndpointKey);
        if (newEndpointOwner != null && !newEndpointOwner.equals(filtererKey)) {
            return WeaponRelocationResult.CONFLICT;
        }
        String newMountOwner = weaponMountToFilterer.get(newMountKey);
        if (newMountOwner != null && !newMountOwner.equals(filtererKey)) {
            return WeaponRelocationResult.CONFLICT;
        }
        String oldMountOwner = weaponMountToFilterer.get(oldMountKey);
        if (oldMountOwner != null && !oldMountOwner.equals(filtererKey)) {
            return WeaponRelocationResult.CONFLICT;
        }

        boolean hasOldEndpoint = group.weaponEndpoints.contains(oldController);
        boolean hasNewEndpoint = group.weaponEndpoints.contains(newController);
        if (!hasOldEndpoint && !hasNewEndpoint) {
            return WeaponRelocationResult.NOT_FOUND;
        }

        String storedMount = controllerToWeaponMount.get(oldEndpointKey);
        if (storedMount == null) {
            storedMount = controllerToWeaponMount.get(newEndpointKey);
        }
        if (storedMount != null && !storedMount.equals(oldMountKey) && !storedMount.equals(newMountKey)) {
            return WeaponRelocationResult.CONFLICT;
        }

        if (!oldController.equals(newController)) {
            group.weaponEndpoints.remove(oldController);
            group.weaponEndpoints.add(newController);
            endpointToFilterer.remove(oldEndpointKey);
            endpointToFilterer.put(newEndpointKey, filtererKey);
            LinkOrigin origin = endpointOrigins.remove(oldEndpointKey);
            if (origin != null) {
                endpointOrigins.put(newEndpointKey, origin);
            }
        } else {
            endpointToFilterer.put(newEndpointKey, filtererKey);
        }

        controllerToWeaponMount.remove(oldEndpointKey);
        controllerToWeaponMount.put(newEndpointKey, newMountKey);
        group.usedWeaponMounts.add(newMount);
        weaponMountToFilterer.put(newMountKey, filtererKey);

        boolean oldMountStillUsed = false;
        for (BlockPos endpoint : group.weaponEndpoints) {
            if (oldMountKey.equals(controllerToWeaponMount.get(key(dim, endpoint)))) {
                oldMountStillUsed = true;
                break;
            }
        }
        if (!oldMountStillUsed && !oldMount.equals(newMount)) {
            group.usedWeaponMounts.remove(oldMount);
            if (filtererKey.equals(weaponMountToFilterer.get(oldMountKey))) {
                weaponMountToFilterer.remove(oldMountKey);
            }
        }

        for (Map.Entry<String, String> entry : dataLinkToEndpoint.entrySet()) {
            if (oldEndpointKey.equals(entry.getValue())) {
                entry.setValue(newEndpointKey);
            }
        }

        setDirty();
        return WeaponRelocationResult.UPDATED;
    }

    public CompoundTag writeSchematicSnapshot(Group group, Function<BlockPos, CompoundTag> encoder) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SchemaVersion", SCHEMATIC_SNAPSHOT_VERSION);
        tag.put("FiltererPos", encoder.apply(group.key.filtererPos()));
        tag.putString("SelectedTargetId", group.selectedTargetId == null ? "" : group.selectedTargetId);
        tag.put("TargetingTag", group.targetingTag.copy());
        tag.put("IdentificationTag", group.identificationTag.copy());
        tag.put("DetectionTag", group.detectionTag.copy());

        ListTag monitors = new ListTag();
        for (BlockPos pos : group.monitorEndpoints) {
            monitors.add(writeEndpointSnapshot(group, pos, encoder));
        }
        tag.put("MonitorEndpoints", monitors);

        ListTag radars = new ListTag();
        for (Map.Entry<BlockPos, RadarKind> entry : group.radarEndpoints.entrySet()) {
            CompoundTag radar = writeEndpointSnapshot(group, entry.getKey(), encoder);
            radar.putString("Kind", entry.getValue().name());
            radars.add(radar);
        }
        tag.put("RadarEndpoints", radars);

        ListTag weapons = new ListTag();
        for (BlockPos pos : group.weaponEndpoints) {
            CompoundTag weapon = writeEndpointSnapshot(group, pos, encoder);
            BlockPos mount = posFromKey(controllerToWeaponMount.get(key(group.key.dim(), pos)));
            if (mount != null) {
                weapon.put("MountPos", encoder.apply(mount));
            }
            weapons.add(weapon);
        }
        tag.put("WeaponEndpoints", weapons);

        ListTag dataLinks = new ListTag();
        for (BlockPos pos : group.dataLinks) {
            CompoundTag dataLink = new CompoundTag();
            dataLink.put("DataLinkPos", encoder.apply(pos));
            BlockPos endpoint = peekEndpointForDataLink(group.key.dim(), pos);
            if (endpoint != null) {
                dataLink.put("EndpointPos", encoder.apply(endpoint));
            }
            dataLinks.add(dataLink);
        }
        tag.put("DataLinks", dataLinks);

        return tag;
    }

    private CompoundTag writeEndpointSnapshot(Group group, BlockPos pos, Function<BlockPos, CompoundTag> encoder) {
        CompoundTag tag = new CompoundTag();
        tag.put("Pos", encoder.apply(pos));
        LinkOrigin origin = endpointOrigins.get(key(group.key.dim(), pos));
        tag.putString("Origin", origin == null ? LinkOrigin.DATALINK.name() : origin.name());
        return tag;
    }

    public RestorationReport restoreSchematicSnapshot(ServerLevel level, BlockPos controllerPos, CompoundTag tag,
                                                       Function<CompoundTag, BlockPos> decoder,
                                                       RestorationMode mode) {
        ResourceKey<Level> dim = level.dimension();
        int skipped = 0;
        if (tag.getAllKeys().contains("SchemaVersion")
                && (!tag.contains("SchemaVersion", Tag.TAG_INT)
                || tag.getInt("SchemaVersion") != SCHEMATIC_SNAPSHOT_VERSION)) {
            return new RestorationReport(0, 1);
        }
        BlockPos filtererPos = decodeSnapshotPos(tag, "FiltererPos", decoder);
        if (filtererPos == null || !filtererPos.equals(controllerPos)) {
            return new RestorationReport(0, 1);
        }

        Group group = getOrCreateGroup(dim, controllerPos);
        group.selectedTargetId = mode == RestorationMode.SCHEMATIC_COPY
                ? null
                : (tag.getString("SelectedTargetId").isEmpty() ? null : tag.getString("SelectedTargetId"));
        if (tag.contains("TargetingTag", Tag.TAG_COMPOUND)) group.targetingTag = tag.getCompound("TargetingTag").copy();
        if (tag.contains("IdentificationTag", Tag.TAG_COMPOUND)) group.identificationTag = tag.getCompound("IdentificationTag").copy();
        if (tag.contains("DetectionTag", Tag.TAG_COMPOUND)) group.detectionTag = tag.getCompound("DetectionTag").copy();

        int restored = 0;
        String groupKey = key(dim, controllerPos);

        ListTag monitors = tag.getList("MonitorEndpoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < monitors.size(); i++) {
            CompoundTag entry = monitors.getCompound(i);
            BlockPos pos = decodeSnapshotPos(entry, "Pos", decoder);
            LinkOrigin origin = readSnapshotOrigin(entry);
            if (pos == null || origin == null || ownedByOther(endpointToFilterer, key(dim, pos), groupKey)) {
                skipped++;
            } else if (claimEndpointForGroup(level, group, pos, origin)) {
                group.monitorEndpoints.add(pos);
                restored++;
            } else {
                skipped++;
            }
        }

        ListTag radars = tag.getList("RadarEndpoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < radars.size(); i++) {
            CompoundTag entry = radars.getCompound(i);
            BlockPos pos = decodeSnapshotPos(entry, "Pos", decoder);
            LinkOrigin origin = readSnapshotOrigin(entry);
            RadarKind kind = readSnapshotRadarKind(entry);
            if (pos == null || origin == null || kind == null
                    || ownedByOther(endpointToFilterer, key(dim, pos), groupKey)) {
                skipped++;
            } else if (claimEndpointForGroup(level, group, pos, origin)) {
                group.radarEndpoints.put(pos, kind);
                restored++;
            } else {
                skipped++;
            }
        }
        group.syncPrimaryRadar();

        ListTag weapons = tag.getList("WeaponEndpoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < weapons.size(); i++) {
            CompoundTag entry = weapons.getCompound(i);
            BlockPos pos = decodeSnapshotPos(entry, "Pos", decoder);
            BlockPos mount = decodeSnapshotPos(entry, "MountPos", decoder);
            LinkOrigin origin = readSnapshotOrigin(entry);
            String existingMount = pos == null ? null : controllerToWeaponMount.get(key(dim, pos));
            if (pos == null || mount == null || origin == null
                    || ownedByOther(endpointToFilterer, key(dim, pos), groupKey)
                    || ownedByOther(weaponMountToFilterer, key(dim, mount), groupKey)
                    || (existingMount != null && !existingMount.equals(key(dim, mount)))
                    || !claimEndpointForGroup(level, group, pos, origin)) {
                skipped++;
                continue;
            }
            group.weaponEndpoints.add(pos);
            group.usedWeaponMounts.add(mount);
            weaponMountToFilterer.put(key(dim, mount), groupKey);
            controllerToWeaponMount.put(key(dim, pos), key(dim, mount));
            restored++;
        }

        ListTag links = tag.getList("DataLinks", Tag.TAG_COMPOUND);
        for (int i = 0; i < links.size(); i++) {
            CompoundTag entry = links.getCompound(i);
            BlockPos dataLinkPos = decodeSnapshotPos(entry, "DataLinkPos", decoder);
            BlockPos endpointPos = decodeSnapshotPos(entry, "EndpointPos", decoder);
            String existingEndpoint = dataLinkPos == null ? null : dataLinkToEndpoint.get(key(dim, dataLinkPos));
            if (dataLinkPos == null || endpointPos == null
                    || ownedByOther(dataLinkToFilterer, key(dim, dataLinkPos), groupKey)
                    || ownedByOther(endpointToFilterer, key(dim, endpointPos), groupKey)) {
                skipped++;
                continue;
            }
            if (existingEndpoint != null && !existingEndpoint.equals(key(dim, endpointPos))) {
                skipped++;
                continue;
            }
            addDataLinkToGroup(group, dataLinkPos, endpointPos);
            restored++;
        }

        setDirty();
        return new RestorationReport(restored, skipped);
    }

    @Nullable
    private static BlockPos decodeSnapshotPos(CompoundTag parent, String key,
                                              Function<CompoundTag, BlockPos> decoder) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) return null;
        CompoundTag encoded = parent.getCompound(key);
        if (!encoded.contains("X", Tag.TAG_INT) || !encoded.contains("Y", Tag.TAG_INT)
                || !encoded.contains("Z", Tag.TAG_INT)) return null;
        try {
            return decoder.apply(encoded);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean ownedByOther(Map<String, String> ownership, String objectKey, String groupKey) {
        String owner = ownership.get(objectKey);
        return owner != null && !owner.equals(groupKey);
    }

    @Nullable
    private static LinkOrigin readSnapshotOrigin(CompoundTag entry) {
        if (!entry.contains("Origin", Tag.TAG_STRING)) return LinkOrigin.DATALINK;
        try {
            return LinkOrigin.valueOf(entry.getString("Origin"));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    private static RadarKind readSnapshotRadarKind(CompoundTag entry) {
        if (!entry.contains("Kind", Tag.TAG_STRING)) return null;
        try {
            return RadarKind.valueOf(entry.getString("Kind"));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }


    public void removeDataLinkAndCleanup(ResourceKey<Level> dim, BlockPos dataLinkPos, @Nullable ServerLevel level) {
        String dlKey = key(dim, dataLinkPos);

        String filtererKey = dataLinkToFilterer.remove(dlKey);
        String endpointKey = dataLinkToEndpoint.remove(dlKey);

        if (filtererKey == null) {
            setDirty();
            return;
        }

        Group group = groupsByFilterer.get(filtererKey);
        if (group != null) {
            group.dataLinks.remove(dataLinkPos);
        }

        if (endpointKey != null && group != null && level != null) {
            BlockPos endpointPos = posFromKey(endpointKey);

            BlockPos normalizedEndpoint = endpointPos;
            BlockEntity endpointBe = level.getBlockEntity(endpointPos);
            if (endpointBe instanceof MonitorBlockEntity endpointMonitor) {
                normalizedEndpoint = endpointMonitor.getControllerPos();
            }

            boolean isMonitorEndpoint = group.monitorEndpoints.contains(normalizedEndpoint) || group.monitorEndpoints.contains(endpointPos);

            if (isMonitorEndpoint) {
                BlockPos controllerPos = group.monitorEndpoints.contains(normalizedEndpoint) ? normalizedEndpoint : endpointPos;

                BlockEntity controllerBe = level.getBlockEntity(controllerPos);
                if (!(controllerBe instanceof MonitorBlockEntity) && endpointBe instanceof MonitorBlockEntity) {
                    controllerBe = endpointBe;
                }

                if (controllerBe instanceof MonitorBlockEntity monitor) {
                    monitor.onNetworkDisconnected();
                }

                group.monitorEndpoints.remove(controllerPos);

                // remove indices for both clicked endpoint and controller
                endpointToFilterer.remove(endpointKey);
                endpointToFilterer.remove(key(level.dimension(), controllerPos));
                endpointOrigins.remove(endpointKey);
                endpointOrigins.remove(key(level.dimension(), controllerPos));
            } else if (group.radarEndpoints.remove(endpointPos) != null) {
                group.syncPrimaryRadar();
                endpointToFilterer.remove(endpointKey);
                endpointOrigins.remove(endpointKey);

            } else if (group.weaponEndpoints.remove(endpointPos)) {
                endpointToFilterer.remove(endpointKey);
                endpointOrigins.remove(endpointKey);

                // If you use controllerToWeaponMount, free it deterministically
                String mountKey = controllerToWeaponMount.remove(endpointKey);
                if (mountKey != null) {
                    BlockPos mp = posFromKey(mountKey);
                    group.usedWeaponMounts.remove(mp);
                    weaponMountToFilterer.remove(mountKey);
                }
            }
        }

        // autodelete empty group
        cleanupIfEmpty(filtererKey);

        setDirty();
    }


    private void cleanupIfEmpty(String filtererKey) {
        Group group = groupsByFilterer.get(filtererKey);
        if (group == null) return;

        boolean empty = group.monitorEndpoints.isEmpty() && group.radarEndpoints.isEmpty() && group.weaponEndpoints.isEmpty() && group.dataLinks.isEmpty();

        if (!empty) return;

        ResourceKey<Level> dim = group.key.dim();


        for (BlockPos mp : group.monitorEndpoints) {
            endpointToFilterer.remove(key(dim, mp));
            endpointOrigins.remove(key(dim, mp));
        }

        for (BlockPos radarPos : group.radarEndpoints.keySet()) {
            endpointToFilterer.remove(key(dim, radarPos));
            endpointOrigins.remove(key(dim, radarPos));
        }

        for (BlockPos ep : group.weaponEndpoints) {
            endpointToFilterer.remove(key(dim, ep));
            endpointOrigins.remove(key(dim, ep));
        }
        for (BlockPos mp : group.usedWeaponMounts) weaponMountToFilterer.remove(key(dim, mp));
        for (BlockPos dl : group.dataLinks) {
            dataLinkToFilterer.remove(key(dim, dl));
            dataLinkToEndpoint.remove(key(dim, dl));
        }

        groupsByFilterer.remove(filtererKey);
    }


    public void onEndpointRemoved(ServerLevel level, BlockPos endpointPos) {
        if (endpointPos == null || level == null) return;

        ResourceKey<Level> dim = level.dimension();

        BlockEntity be = level.getBlockEntity(endpointPos);
        if (be instanceof MonitorBlockEntity m) {
            BlockPos controllerPos = m.getControllerPos();
            if (controllerPos != null) {
                endpointPos = controllerPos;
            }
        }

        String endpointKey = key(dim, endpointPos);
        String filtererKey = endpointToFilterer.get(endpointKey);
        if (filtererKey == null) return;

        Group group = groupsByFilterer.get(filtererKey);
        if (group == null) {
            endpointToFilterer.remove(endpointKey);
            return;
        }

        if (group.monitorEndpoints.remove(endpointPos)) {
            endpointToFilterer.remove(endpointKey);
            endpointOrigins.remove(endpointKey);

        } else if (group.radarEndpoints.remove(endpointPos) != null) {
            group.syncPrimaryRadar();
            endpointToFilterer.remove(endpointKey);
            endpointOrigins.remove(endpointKey);

        } else if (group.weaponEndpoints.remove(endpointPos)) {
            endpointToFilterer.remove(endpointKey);
            endpointOrigins.remove(endpointKey);

            String mountKey = controllerToWeaponMount.remove(endpointKey);
            if (mountKey != null) {
                BlockPos mp = posFromKey(mountKey);
                group.usedWeaponMounts.remove(mp);
                weaponMountToFilterer.remove(mountKey);
            }

        } else {
            endpointToFilterer.remove(endpointKey);
            endpointOrigins.remove(endpointKey);
        }

        // Remove any datalinks targeting this endpoint
        List<String> dlKeysToRemove = new ArrayList<>();
        for (Map.Entry<String, String> e : dataLinkToEndpoint.entrySet()) {
            if (endpointKey.equals(e.getValue())) {
                dlKeysToRemove.add(e.getKey());
            }
        }

        for (String dlKey : dlKeysToRemove) {
            dataLinkToEndpoint.remove(dlKey);

            String owner = dataLinkToFilterer.get(dlKey);
            if (filtererKey.equals(owner)) {
                dataLinkToFilterer.remove(dlKey);

                BlockPos dlPos = posFromKey(dlKey);
                group.dataLinks.remove(dlPos);
            }
        }

        cleanupIfEmpty(filtererKey);
        setDirty();
    }


    public void setTargetingConfig(Group group, TargetingConfig cfg) {
        CompoundTag root = new CompoundTag();
        root.put("targeting", cfg.toTag());
        group.targetingTag = root;
        setDirty();
    }

    public void setAllFilters(Group group, TargetingConfig t, IdentificationConfig i, DetectionConfig d) {
        CompoundTag root = new CompoundTag();
        root.put("targeting", t.toTag());
        group.targetingTag = root;

        group.identificationTag = i.toTag();
        group.detectionTag = d.toTag();

        setDirty();
    }


    private static CompoundTag defaultTargetingTag() {
        CompoundTag root = new CompoundTag();
        root.put("targeting", TargetingConfig.DEFAULT.toTag());
        return root;
    }

    private static CompoundTag defaultIdentificationTag() {
        return IdentificationConfig.DEFAULT.toTag();
    }

    private static CompoundTag defaultDetectionTag() {
        return DetectionConfig.DEFAULT.toTag();
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

    public Map<String, Group> getGroupsByFiltererView() {
        return Collections.unmodifiableMap(groupsByFilterer);
    }
    @Nullable
    public Group getGroupForEndpoint(ResourceKey<Level> dim, BlockPos endpointPos) {
        BlockPos filterer = getFiltererForEndpoint(dim, endpointPos);
        if (filterer == null) return null;
        return getGroup(dim, filterer);
    }


    public record ValidationResult(
            int groupsRemoved,
            int endpointsRemoved,
            int mountsRemoved,
            int dataLinksRemoved
    ) {}

    public ValidationResult validateAllKnownPositions(ServerLevel level, boolean onlyIfChunkLoaded) {
        if (level == null) return new ValidationResult(0,0,0,0);

        int groupsRemoved = 0;
        int endpointsRemoved = 0;
        int mountsRemoved = 0;
        int dataLinksRemoved = 0;

        // i snapshot keys so i can mutate maps safely
        List<String> groupKeys = new ArrayList<>(groupsByFilterer.keySet());
        ResourceKey<Level> levelDim = level.dimension();

        for (String filtererKeyStr : groupKeys) {
            Group group = groupsByFilterer.get(filtererKeyStr);
            if (group == null) continue;

            // i only validate groups in this dimension
            if (!group.key.dim().equals(levelDim)) continue;

            // if the filterer is truly gone, dissolve the whole group
            if (isDefinitelyMissing(level, group.key.filtererPos(), onlyIfChunkLoaded, true)) {
                dissolveGroup(level, filtererKeyStr);
                groupsRemoved++;
                continue;
            }

            // monitor
            if (!group.monitorEndpoints.isEmpty()) {
                Iterator<BlockPos> it = group.monitorEndpoints.iterator();
                while (it.hasNext()) {
                    BlockPos mp = it.next();
                    if (!isDefinitelyMissing(level, mp, onlyIfChunkLoaded, true))
                        continue;

                    endpointToFilterer.remove(posKey(levelDim, mp));
                    endpointOrigins.remove(posKey(levelDim, mp));
                    it.remove();
                    endpointsRemoved++;
                }
            }

            // radar
            if (!group.radarEndpoints.isEmpty()) {
                Iterator<BlockPos> it = group.radarEndpoints.keySet().iterator();
                while (it.hasNext()) {
                    BlockPos radarPos = it.next();
                    if (!isDefinitelyMissing(level, radarPos, onlyIfChunkLoaded, true))
                        continue;

                    endpointToFilterer.remove(posKey(levelDim, radarPos));
                    endpointOrigins.remove(posKey(levelDim, radarPos));
                    it.remove();
                    endpointsRemoved++;
                }
                group.syncPrimaryRadar();
            }

            // weapon endpoints
            if (!group.weaponEndpoints.isEmpty()) {
                Iterator<BlockPos> it = group.weaponEndpoints.iterator();
                while (it.hasNext()) {
                    BlockPos controllerPos = it.next();

                    if (!isDefinitelyMissing(level, controllerPos, onlyIfChunkLoaded, true)) {
                        continue; // PRESENT or UNKNOWN => keep
                    }

                    boolean hasSpecialDL = hasMatchingDataLinkTargeting(
                            level,
                            group,
                            controllerPos,
                            onlyIfChunkLoaded,
                            st -> {
                                return st.is(ModBlocks.RADAR_LINK.get()) && st.getValue(DataLinkBlock.LINK_STYLE) == DataLinkBlock.LinkStyle.RADAR;
                            }
                    );

                    if (hasSpecialDL) {
                        continue; // don't remove this endpoint entry
                    }

                    it.remove();
                    endpointToFilterer.remove(posKey(levelDim, controllerPos));
                    endpointOrigins.remove(posKey(levelDim, controllerPos));
                    endpointsRemoved++;

                    // free its mount if we have a mapping
                    String mountKey = controllerToWeaponMount.remove(posKey(levelDim, controllerPos));
                    if (mountKey != null) {
                        weaponMountToFilterer.remove(mountKey);
                        BlockPos mountPos = posFromKey(mountKey);
                        group.usedWeaponMounts.remove(mountPos);
                        mountsRemoved++;
                    }
                }
            }

            // mounts safety sweep (only remove when definitely missing)
            if (!group.usedWeaponMounts.isEmpty()) {
                Iterator<BlockPos> it = group.usedWeaponMounts.iterator();
                while (it.hasNext()) {
                    BlockPos mountPos = it.next();

                    if (!isDefinitelyMissing(level, mountPos, onlyIfChunkLoaded, true)) {
                        continue; // PRESENT or UNKNOWN => keep
                    }

                    it.remove();
                    weaponMountToFilterer.remove(posKey(levelDim, mountPos));
                    mountsRemoved++;

                    // remove any controller->mount entries pointing at this mount
                    String mk = posKey(levelDim, mountPos);
                    controllerToWeaponMount.entrySet().removeIf(e -> mk.equals(e.getValue()));
                }
            }

            // datalinks
            if (!group.dataLinks.isEmpty()) {
                Iterator<BlockPos> it = group.dataLinks.iterator();
                while (it.hasNext()) {
                    BlockPos dlPos = it.next();
                    String dlKey = posKey(levelDim, dlPos);

                    // if datalink block is definitely gone, drop it
                    if (isDefinitelyMissing(level, dlPos, onlyIfChunkLoaded, true)) {
                        it.remove();
                        dataLinkToFilterer.remove(dlKey);
                        dataLinkToEndpoint.remove(dlKey);
                        dataLinksRemoved++;
                        continue;
                    }

                    // datalink still exists (or unknown), but its target endpoint might be gone
                    String epKey = dataLinkToEndpoint.get(dlKey);
                    if (epKey != null) {
                        BlockPos epPos = posFromKey(epKey);

                        if (isDefinitelyMissing(level, epPos, onlyIfChunkLoaded, true)) {
                            dataLinkToEndpoint.remove(dlKey);
                        }
                    }
                }
            }

            // delete group if it becomes empty after scrub
            cleanupIfEmpty(filtererKeyStr);
        }

        if (groupsRemoved != 0 || endpointsRemoved != 0 || mountsRemoved != 0 || dataLinksRemoved != 0) {
            setDirty();
        }

        return new ValidationResult(groupsRemoved, endpointsRemoved, mountsRemoved, dataLinksRemoved);
    }
    @Nullable
    private Group findGroupByFiltererPosSlow(ResourceKey<Level> dim, BlockPos filtererPos) {
        for (Group g : groupsByFilterer.values()) {
            if (!g.key.dim().equals(dim))
                continue;

            if (filtererPos.equals(g.key.filtererPos()))
                return g;
        }
        return null;
    }

    /**
     * Moves a filter controller (the filterer's position) oldPos -> newPos.
     * This re-keys the entire Group and rewrites all indexes that point at the filtererKey.
     *
     * @return true if updated, false if not found or conflict
     */
    public boolean updateFiltererPosition(ResourceKey<Level> dim, BlockPos oldPos, BlockPos newPos) {
        if (oldPos.equals(newPos))
            return true;

        String oldFiltererKey = key(dim, oldPos);
        String newFiltererKey = key(dim, newPos);

        // dont overwrite another group
        if (groupsByFilterer.containsKey(newFiltererKey) && !oldFiltererKey.equals(newFiltererKey)) {
            return false;
        }

        Group oldGroup = groupsByFilterer.get(oldFiltererKey);

        // fallback in case the map key got out of sync somehow
        if (oldGroup == null) {
            oldGroup = findGroupByFiltererPosSlow(dim, oldPos);
            if (oldGroup == null)
                return false;

            // self heal: ensure it is actually keyed correctly
            groupsByFilterer.put(oldFiltererKey, oldGroup);
        }

        // build a replacement group with the new key
        Group newGroup = new Group(new FilterKey(dim, newPos));

        // copy simple fields
        newGroup.selectedTargetId = oldGroup.selectedTargetId;
        newGroup.monitorEndpoints.addAll(oldGroup.monitorEndpoints);

        newGroup.radarEndpoints.putAll(oldGroup.radarEndpoints);
        newGroup.syncPrimaryRadar();

        // copy sets
        newGroup.weaponEndpoints.addAll(oldGroup.weaponEndpoints);
        newGroup.usedWeaponMounts.addAll(oldGroup.usedWeaponMounts);
        newGroup.dataLinks.addAll(oldGroup.dataLinks);

        // copy tags
        newGroup.targetingTag = oldGroup.targetingTag;
        newGroup.identificationTag = oldGroup.identificationTag;
        newGroup.detectionTag = oldGroup.detectionTag;

        // replace group map entry
        groupsByFilterer.remove(oldFiltererKey);
        groupsByFilterer.put(newFiltererKey, newGroup);

        for (BlockPos mp : newGroup.monitorEndpoints) {
            endpointToFilterer.put(key(dim, mp), newFiltererKey);
        }
        for (BlockPos radarPos : newGroup.radarEndpoints.keySet()) endpointToFilterer.put(key(dim, radarPos), newFiltererKey);
        for (BlockPos ep : newGroup.weaponEndpoints) endpointToFilterer.put(key(dim, ep), newFiltererKey);
        endpointToFilterer.values().removeIf(v -> v.equals(oldFiltererKey));

        // rewrite mount index (enforces uniqueness)
        for (BlockPos mp : newGroup.usedWeaponMounts) {
            weaponMountToFilterer.put(key(dim, mp), newFiltererKey);
        }
        weaponMountToFilterer.values().removeIf(v -> v.equals(oldFiltererKey));

        // rewrite datalink -> filterer mapping
        for (BlockPos lp : newGroup.dataLinks) {
            dataLinkToFilterer.put(key(dim, lp), newFiltererKey);
        }
        dataLinkToFilterer.values().removeIf(v -> v.equals(oldFiltererKey));

        setDirty();
        return true;
    }

    @Nullable
    private Group findGroupByEndpointSlow(ResourceKey<Level> dim, BlockPos endpointPos) {
        for (Group g : groupsByFilterer.values()) {
            if (!g.key.dim().equals(dim))
                continue;

            if (g.weaponEndpoints.contains(endpointPos))
                return g;
        }
        return null;
    }
    public boolean updateWeaponEndpointPosition(ResourceKey<Level> dim, BlockPos oldPos, BlockPos newPos) {
        if (oldPos.equals(newPos))
            return true;

        String oldEndpointKey = key(dim, oldPos);
        String filtererKey = endpointToFilterer.get(oldEndpointKey);

        Group g = null;

        // fast path
        if (filtererKey != null) {
            g = groupsByFilterer.get(filtererKey);
        }

        // fallback: slow scan + self heal
        if (g == null) {
            g = findGroupByEndpointSlow(dim, oldPos);
            if (g == null)
                return false;

            filtererKey = key(dim, g.key.filtererPos());
            groupsByFilterer.put(filtererKey, g);

            // rebuild endpointToFilterer index for this group
            for (BlockPos ep : g.weaponEndpoints) {
                endpointToFilterer.put(key(dim, ep), filtererKey);
            }
        }

        // refuse if newPos is already claimed by a different group
        String newEndpointKey = key(dim, newPos);
        String existingFiltererForNew = endpointToFilterer.get(newEndpointKey);
        if (existingFiltererForNew != null && !existingFiltererForNew.equals(filtererKey)) {
            return false;
        }

        // make sure the endpoint is actually in this group
        if (!g.weaponEndpoints.remove(oldPos))
            return false;

        g.weaponEndpoints.add(newPos);

        // update endpoint index
        endpointToFilterer.remove(oldEndpointKey);
        endpointToFilterer.put(newEndpointKey, filtererKey);
        LinkOrigin movedOrigin = endpointOrigins.remove(oldEndpointKey);
        if (movedOrigin != null) endpointOrigins.put(newEndpointKey, movedOrigin);

        // move controller->weaponMount mapping (if present)
        String mountKey = controllerToWeaponMount.remove(oldEndpointKey);
        if (mountKey != null) {
            controllerToWeaponMount.put(newEndpointKey, mountKey);
        }

        // if any datalink was mapped to the old endpoint, repoint it
        // (there can be multiple datalinks targeting the same endpoint, so scan)
        if (!dataLinkToEndpoint.isEmpty()) {
            for (Map.Entry<String, String> e : dataLinkToEndpoint.entrySet()) {
                if (oldEndpointKey.equals(e.getValue())) {
                    e.setValue(newEndpointKey);
                }
            }
        }

        setDirty();
        return true;
    }
    @Nullable
    private Group findGroupByMonitorSlow(ResourceKey<Level> dim, BlockPos monitorPos) {
        for (Group g : groupsByFilterer.values()) {
            if (!g.key.dim().equals(dim))
                continue;
            if (g.monitorEndpoints.contains(monitorPos))
                return g;
        }
        return null;
    }

    @Nullable
    private Group findGroupByRadarSlow(ResourceKey<Level> dim, BlockPos radarPos) {
        for (Group g : groupsByFilterer.values()) {
            if (!g.key.dim().equals(dim))
                continue;
            if (g.radarEndpoints.containsKey(radarPos))
                return g;
        }
        return null;
    }


    /**
     * Moves a monitor inside a filter group from oldPos -> newPos.
     *
     * @return true if we found + updated it, false otherwise
     */
    /**
     * Moves the group's monitor position oldPos -> newPos and keeps indexes consistent.
     *
     * @return true if updated, false if not found or conflict
     */
    public boolean updateMonitorPosition(ResourceKey<Level> dim, BlockPos oldPos, BlockPos newPos) {
        if (oldPos.equals(newPos))
            return true;

        String oldKey = key(dim, oldPos);
        String filtererKey = endpointToFilterer.get(oldKey);

        Group g = null;

        if (filtererKey != null) {
            g = groupsByFilterer.get(filtererKey);
        }

        if (g == null) {
            g = findGroupByMonitorSlow(dim, oldPos);
            if (g == null)
                return false;

            filtererKey = key(dim, g.key.filtererPos());
            groupsByFilterer.put(filtererKey, g);

            // rebuild index entries for this group
            for (BlockPos mp : g.monitorEndpoints) endpointToFilterer.put(key(dim, mp), filtererKey);
            for (BlockPos radarPos : g.radarEndpoints.keySet()) endpointToFilterer.put(key(dim, radarPos), filtererKey);
            for (BlockPos ep : g.weaponEndpoints) endpointToFilterer.put(key(dim, ep), filtererKey);
        }

        // refuse if newPos is already claimed by a different group
        String newKey = key(dim, newPos);
        String existing = endpointToFilterer.get(newKey);
        if (existing != null && !existing.equals(filtererKey)) {
            return false;
        }

        // verify we're actually moving a monitor endpoint
        if (!g.monitorEndpoints.remove(oldPos))
            return false;

        g.monitorEndpoints.add(newPos);

        // update endpoint index
        endpointToFilterer.remove(oldKey);
        endpointToFilterer.put(newKey, filtererKey);
        LinkOrigin movedOrigin = endpointOrigins.remove(oldKey);
        if (movedOrigin != null) endpointOrigins.put(newKey, movedOrigin);

        // repoint any datalink->endpoint mapping that referenced the old monitor pos
        if (!dataLinkToEndpoint.isEmpty()) {
            for (Map.Entry<String, String> e : dataLinkToEndpoint.entrySet()) {
                if (oldKey.equals(e.getValue())) {
                    e.setValue(newKey);
                }
            }
        }

        setDirty();
        return true;
    }

    /**
     * Moves the group's radar position oldPos -> newPos and keeps indexes consistent.
     *
     * @return true if updated, false if not found or conflict
     */
    public boolean updateRadarPosition(ResourceKey<Level> dim, BlockPos oldPos, BlockPos newPos) {
        if (oldPos.equals(newPos))
            return true;

        String oldKey = key(dim, oldPos);
        String filtererKey = endpointToFilterer.get(oldKey);

        Group g = null;

        // fast path
        if (filtererKey != null) {
            g = groupsByFilterer.get(filtererKey);
        }

        // slow fallback + self heal
        if (g == null) {
            g = findGroupByRadarSlow(dim, oldPos);
            if (g == null)
                return false;

            filtererKey = key(dim, g.key.filtererPos());
            groupsByFilterer.put(filtererKey, g);

            // rebuild index entries for this group
            for (BlockPos mp : g.monitorEndpoints) endpointToFilterer.put(key(dim, mp), filtererKey);
            for (BlockPos radarPos : g.radarEndpoints.keySet()) endpointToFilterer.put(key(dim, radarPos), filtererKey);
            for (BlockPos ep : g.weaponEndpoints) endpointToFilterer.put(key(dim, ep), filtererKey);
        }

        // refuse if newPos is already claimed by a different group
        String newKey = key(dim, newPos);
        String existing = endpointToFilterer.get(newKey);
        if (existing != null && !existing.equals(filtererKey)) {
            return false;
        }

        // verify we're actually moving the radar
        RadarKind movedKind = g.radarEndpoints.remove(oldPos);
        if (movedKind == null)
            return false;

        g.radarEndpoints.put(newPos, movedKind);
        g.syncPrimaryRadar();

        // update index
        endpointToFilterer.remove(oldKey);
        endpointToFilterer.put(newKey, filtererKey);
        LinkOrigin movedOrigin = endpointOrigins.remove(oldKey);
        if (movedOrigin != null) endpointOrigins.put(newKey, movedOrigin);

        // only do this if radars can be a datalink endpoint in your system (harmless if not)
        if (!dataLinkToEndpoint.isEmpty()) {
            for (Map.Entry<String, String> e : dataLinkToEndpoint.entrySet()) {
                if (oldKey.equals(e.getValue())) {
                    e.setValue(newKey);
                }
            }
        }

        setDirty();
        return true;
    }



    private boolean hasMatchingDataLinkTargeting(ServerLevel level,
                                                 Group group,
                                                 BlockPos endpointPos,
                                                 boolean onlyIfChunkLoaded,
                                                 Predicate<BlockState> stateTest) {
        if (group == null || endpointPos == null) return false;
        if (group.dataLinks.isEmpty()) return false;

        ResourceKey<Level> dim = group.key.dim();
        String endpointKey = posKey(dim, endpointPos);

        for (BlockPos dlPos : group.dataLinks) {
            // don't chunkload for validation
            if (onlyIfChunkLoaded && !level.hasChunkAt(dlPos)) {
                continue;
            }

            // datalink must exist in the world to count
            if (level.isEmptyBlock(dlPos)) {
                continue;
            }

            // does THIS datalink point at this endpoint (per saved mapping)?
            String mappedEndpointKey = dataLinkToEndpoint.get(posKey(dim, dlPos));
            if (mappedEndpointKey == null || !mappedEndpointKey.equals(endpointKey)) {
                continue;
            }

            // does the datalink have the blockstate we're looking for?
            BlockState st = level.getBlockState(dlPos);
            if (stateTest.test(st)) {
                return true;
            }
        }

        return false;
    }

    private enum Presence { PRESENT, MISSING, UNKNOWN }

    private Presence checkPresence(ServerLevel level, BlockPos pos, boolean onlyIfChunkLoaded, boolean requireBlockEntity) {
        if (pos == null) return Presence.MISSING;

        if (onlyIfChunkLoaded && !level.hasChunkAt(pos)) {
            return Presence.UNKNOWN;
        }
        if (level.isEmptyBlock(pos)) {
            return Presence.MISSING;
        }
        if (!requireBlockEntity) {
            return Presence.PRESENT;
        }
        return level.getBlockEntity(pos) != null ? Presence.PRESENT : Presence.UNKNOWN;
    }

    private boolean isDefinitelyMissing(ServerLevel level, BlockPos pos, boolean onlyIfChunkLoaded, boolean requireBlockEntity) {
        return checkPresence(level, pos, onlyIfChunkLoaded, requireBlockEntity) == Presence.MISSING;
    }




}
