package com.happysg.radar.block.behavior.networks;

import com.happysg.radar.block.behavior.networks.config.TargetingConfig;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class WeaponNetworkData extends SavedData {

    /** A group is uniquely identified by its mount location (dim + pos). */
    public record MountKey(ResourceKey<Level> dim, BlockPos mountPos) {}

    public static class Group {
        public final MountKey key;

        public @Nullable BlockPos yawPos;
        public @Nullable BlockPos pitchPos;
        public @Nullable BlockPos firingPos;

        public CompoundTag targetingTag;
        public final Set<BlockPos> dataLinks = new HashSet<>();

        public Group(MountKey key) {
            this.key = key;
            this.targetingTag = defaultTargetingTag();
        }

        public int controllerCount() {
            int c = 0;
            if (yawPos != null) c++;
            if (pitchPos != null) c++;
            if (firingPos != null) c++;
            return c;
        }

        public boolean isFull() {
            return controllerCount() >= 3;
        }
    }
    public record WeaponGroupView(BlockPos mountPos,
                                  @Nullable BlockPos yawPos,
                                  @Nullable BlockPos pitchPos,
                                  @Nullable BlockPos firingPos) {

        /**
         * All endpoints that exist (yaw/pitch/firing).
         */
        public Set<BlockPos> endpoints() {
            Set<BlockPos> out = new HashSet<>();
            if (yawPos != null) out.add(yawPos);
            if (pitchPos != null) out.add(pitchPos);
            if (firingPos != null) out.add(firingPos);
            return out;
        }

        /**
         * All endpoints except the one you started from.
         */
        public Set<BlockPos> otherEndpoints(BlockPos exclude) {
            Set<BlockPos> out = endpoints();
            out.remove(exclude);
            return out;
        }
    }
    @Nullable
    private Group findGroupByEndpointSlow(ResourceKey<Level> dim, BlockPos endpointPos) {
        for (Group g : groupsByMount.values()) {
            if (!g.key.dim().equals(dim))
                continue;

            if (endpointPos.equals(g.yawPos) || endpointPos.equals(g.pitchPos) || endpointPos.equals(g.firingPos)) {
                return g;
            }
        }
        return null;
    }

    @Nullable
    public WeaponGroupView getWeaponGroupViewFromEndpoint(ResourceKey<Level> dim, BlockPos endpointPos) {
        // Fast path
        String mountKey = controllerToMount.get(key(dim, endpointPos));

        Group g = null;

        if (mountKey != null) {
            g = groupsByMount.get(mountKey);
        }

        // Self-heal path (index missing or stale)
        if (g == null) {
            g = findGroupByEndpointSlow(dim, endpointPos);
            if (g == null)
                return null;

            // rebuild the index for next time
            String mk = key(dim, g.key.mountPos());
            if (g.yawPos != null)    controllerToMount.put(key(dim, g.yawPos), mk);
            if (g.pitchPos != null)  controllerToMount.put(key(dim, g.pitchPos), mk);
            if (g.firingPos != null) controllerToMount.put(key(dim, g.firingPos), mk);

            // also ensure groupsByMount is keyed correctly (just in case)
            groupsByMount.put(mk, g);

            setDirty();
        }

        return new WeaponGroupView(g.key.mountPos(), g.yawPos, g.pitchPos, g.firingPos);
    }


    // "dim|posLong" -> Group
    private final Map<String, Group> groupsByMount = new HashMap<>();

    // index datalink position -> mount key string
    private final Map<String, String> dataLinkToMount = new HashMap<>();

    // index controller position -> mount key string (fast lookup)
    private final Map<String, String> controllerToMount = new HashMap<>();

    // -------------------------
    // SavedData plumbing
    // -------------------------

    public static WeaponNetworkData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        WeaponNetworkData::new,
                        WeaponNetworkData::load
                ),
                "radar_mount_links"
        );
    }

    public WeaponNetworkData() {}

    // -------------------------
    // Load / Save
    // -------------------------

    public static WeaponNetworkData load(CompoundTag tag, HolderLookup.Provider provider) {
        WeaponNetworkData data = new WeaponNetworkData();

        ListTag groups = tag.getList("Groups", Tag.TAG_COMPOUND);
        for (int i = 0; i < groups.size(); i++) {
            CompoundTag g = groups.getCompound(i);

            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(g.getString("Dim")));
            BlockPos mountPos = NbtUtils.readBlockPos(g, "MountPos").orElse(null);

            String mountKey = key(dim, mountPos);
            Group group = new Group(new MountKey(dim, mountPos));

            if (g.contains("YawPos", Tag.TAG_COMPOUND)) group.yawPos = NbtUtils.readBlockPos(g, "YawPos").orElse(null);
            if (g.contains("PitchPos", Tag.TAG_COMPOUND)) group.pitchPos = NbtUtils.readBlockPos(g, "PitchPos").orElse(null);
            if (g.contains("FiringPos", Tag.TAG_COMPOUND)) group.firingPos = NbtUtils.readBlockPos(g, "FiringPos").orElse(null);

            // Targeting tag (optional)
            if (g.contains("Targeting", Tag.TAG_COMPOUND)) {
                group.targetingTag = g.getCompound("Targeting");
            } else {
                group.targetingTag = defaultTargetingTag();
            }

            // Populate controller -> mount index
            if (group.yawPos != null)   data.controllerToMount.put(key(dim, group.yawPos), mountKey);
            if (group.pitchPos != null) data.controllerToMount.put(key(dim, group.pitchPos), mountKey);
            if (group.firingPos != null)data.controllerToMount.put(key(dim, group.firingPos), mountKey);

            // Datalinks
            ListTag links = g.getList("DataLinks", Tag.TAG_COMPOUND);
            for (int j = 0; j < links.size(); j++) {
                CompoundTag entry = links.getCompound(j);

                BlockPos lp = new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z"));

                group.dataLinks.add(lp);
                data.dataLinkToMount.put(key(dim, lp), mountKey);
            }

            data.groupsByMount.put(mountKey, group);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag groups = new ListTag();

        for (Group group : groupsByMount.values()) {
            CompoundTag g = new CompoundTag();
            g.putString("Dim", group.key.dim().location().toString());
            g.put("MountPos", NbtUtils.writeBlockPos(group.key.mountPos()));

            if (group.yawPos != null) g.put("YawPos", NbtUtils.writeBlockPos(group.yawPos));
            if (group.pitchPos != null) g.put("PitchPos", NbtUtils.writeBlockPos(group.pitchPos));
            if (group.firingPos != null) g.put("FiringPos", NbtUtils.writeBlockPos(group.firingPos));

            g.put("Targeting", group.targetingTag);

            ListTag links = new ListTag();
            for (BlockPos p : group.dataLinks) {
                links.add(NbtUtils.writeBlockPos(p));
            }
            g.put("DataLinks", links);

            groups.add(g);
        }

        tag.put("Groups", groups);
        return tag;
    }

    // -------------------------
    // Accessors
    // -------------------------

    public @Nullable Group getGroup(ResourceKey<Level> dim, BlockPos mountPos) {
        return groupsByMount.get(key(dim, mountPos));
    }

    public Group getOrCreateGroup(ResourceKey<Level> dim, BlockPos mountPos) {
        String k = key(dim, mountPos);
        return groupsByMount.computeIfAbsent(k, _k -> {
            setDirty();
            return new Group(new MountKey(dim, mountPos));
        });
    }

    public Map<String, Group> getGroups() {
        return Collections.unmodifiableMap(groupsByMount);
    }

    /** Fast lookup: which mount group owns this controller? */
    public @Nullable BlockPos getMountForController(ResourceKey<Level> dim, BlockPos controllerPos) {
        String mk = controllerToMount.get(key(dim, controllerPos));
        if (mk == null) return null;
        return posFromKey(mk);
    }

    /** Fast lookup: get the Group for a controller pos (or null). */
    public @Nullable Group getGroupForController(ResourceKey<Level> dim, BlockPos controllerPos) {
        BlockPos mount = getMountForController(dim, controllerPos);
        return mount == null ? null : getGroup(dim, mount);
    }

    // -------------------------
    // Mutation helpers
    // -------------------------

    /** Used at placement-time: merge new selections into the mount's group. */
    public boolean tryMergeIntoGroup(Group group,
                                     @Nullable BlockPos yaw,
                                     @Nullable BlockPos pitch,
                                     @Nullable BlockPos firing) {

        // Cannot add a different controller of an existing type
        if (yaw != null && group.yawPos != null && !group.yawPos.equals(yaw)) return false;
        if (pitch != null && group.pitchPos != null && !group.pitchPos.equals(pitch)) return false;
        if (firing != null && group.firingPos != null && !group.firingPos.equals(firing)) return false;

        ResourceKey<Level> dim = group.key.dim();
        String mountKey = key(dim, group.key.mountPos());

        // Fill empty slots + update controller index
        if (yaw != null && group.yawPos == null) {
            group.yawPos = yaw;
            controllerToMount.put(key(dim, yaw), mountKey);
        }
        if (pitch != null && group.pitchPos == null) {
            group.pitchPos = pitch;
            controllerToMount.put(key(dim, pitch), mountKey);
        }
        if (firing != null && group.firingPos == null) {
            group.firingPos = firing;
            controllerToMount.put(key(dim, firing), mountKey);
        }

        setDirty();
        return true;
    }

    public void addDataLinkToGroup(Group group, BlockPos dataLinkPos) {
        ResourceKey<Level> dim = group.key.dim();
        String mountKey = key(dim, group.key.mountPos());

        group.dataLinks.add(dataLinkPos);
        dataLinkToMount.put(key(dim, dataLinkPos), mountKey);
        setDirty();
    }

    /**
     * Optional helper: remove a specific controller and keep group if it still has links.
     */
    public void removeController(ResourceKey<Level> dim, BlockPos controllerPos) {
        String mountKey = controllerToMount.remove(key(dim, controllerPos));
        if (mountKey == null) return;

        Group group = groupsByMount.get(mountKey);
        if (group == null) return;

        if (controllerPos.equals(group.yawPos)) group.yawPos = null;
        if (controllerPos.equals(group.pitchPos)) group.pitchPos = null;
        if (controllerPos.equals(group.firingPos)) group.firingPos = null;

        // Auto-delete if empty (no links + no controllers)
        cleanupIfEmpty(dim, mountKey, group);

        setDirty();
    }
    public boolean removeDataLink(ResourceKey<Level> dim, BlockPos dataLinkPos) {
        String dlKey = key(dim, dataLinkPos);
        String mountKey = dataLinkToMount.remove(dlKey);
        if (mountKey == null) return false;

        Group group = groupsByMount.get(mountKey);
        if (group == null) {
            setDirty();
            return true;
        }

        group.dataLinks.remove(dataLinkPos);

        // If group is now empty, delete it
        cleanupIfEmpty(dim, mountKey, group);

        setDirty();
        return true;
    }

    public void removeDataLinkAndCleanup(ResourceKey<Level> dim, BlockPos dataLinkPos) {
        String dlKey = key(dim, dataLinkPos);
        String mountKey = dataLinkToMount.remove(dlKey);
        if (mountKey == null) return;

        Group group = groupsByMount.get(mountKey);
        if (group == null) {
            setDirty();
            return;
        }

        group.dataLinks.remove(dataLinkPos);

        if (group.dataLinks.isEmpty()) {
            if (group.yawPos != null) {
                controllerToMount.remove(key(dim, group.yawPos));
                group.yawPos = null;
            }
            if (group.pitchPos != null) {
                controllerToMount.remove(key(dim, group.pitchPos));
                group.pitchPos = null;
            }
            if (group.firingPos != null) {
                controllerToMount.remove(key(dim, group.firingPos));
                group.firingPos = null;
            }

            groupsByMount.remove(mountKey);
        } else {
            cleanupIfEmpty(dim, mountKey, group);
        }

        setDirty();
    }

    private void cleanupIfEmpty(ResourceKey<Level> dim, String mountKey, Group group) {
        if (!group.dataLinks.isEmpty()) return;
        if (group.controllerCount() != 0) return;

        // no links, no controllers => delete group
        groupsByMount.remove(mountKey);
    }
    public boolean updateWeaponEndpointPosition(ResourceKey<Level> dim, BlockPos oldPos, BlockPos newPos) {
        if (oldPos.equals(newPos))
            return true;

        String oldKey = key(dim, oldPos);
        String mountKey = controllerToMount.get(oldKey);

        Group g = null;

        // fast path via index
        if (mountKey != null) {
            g = groupsByMount.get(mountKey);
        }
        if (g == null) {
            g = findGroupByEndpointSlow(dim, oldPos);
            if (g == null)
                return false;

            mountKey = key(dim, g.key.mountPos());
            groupsByMount.put(mountKey, g);

            if (g.yawPos != null)    controllerToMount.put(key(dim, g.yawPos), mountKey);
            if (g.pitchPos != null)  controllerToMount.put(key(dim, g.pitchPos), mountKey);
            if (g.firingPos != null) controllerToMount.put(key(dim, g.firingPos), mountKey);
        }

        // if newPos is already indexed to a different mount, don't silently steal it
        String newKey = key(dim, newPos);
        String existingMountForNew = controllerToMount.get(newKey);
        if (existingMountForNew != null && !existingMountForNew.equals(mountKey)) {
            return false;
        }

        boolean updated = false;

        // update the actual group field
        if (oldPos.equals(g.yawPos)) {
            g.yawPos = newPos;
            updated = true;
        } else if (oldPos.equals(g.pitchPos)) {
            g.pitchPos = newPos;
            updated = true;
        } else if (oldPos.equals(g.firingPos)) {
            g.firingPos = newPos;
            updated = true;
        }

        if (!updated)
            return false;

        // fix the index
        controllerToMount.remove(oldKey);
        controllerToMount.put(newKey, mountKey);

        setDirty();
        return true;
    }
    // -------------------------
    // Validation (no mutation)
    // -------------------------

    public boolean canMergeIntoGroup(Group group,
                                     @Nullable BlockPos yaw,
                                     @Nullable BlockPos pitch,
                                     @Nullable BlockPos firing) {

        if (group.isFull()) return false;

        if (yaw != null && group.yawPos != null && !group.yawPos.equals(yaw)) return false;
        if (pitch != null && group.pitchPos != null && !group.pitchPos.equals(pitch)) return false;
        if (firing != null && group.firingPos != null && !group.firingPos.equals(firing)) return false;

        return true;
    }

    // -------------------------
    // Key helpers
    // -------------------------

    private static String key(ResourceKey<Level> dim, BlockPos pos) {
        return dim.location() + "|" + pos.asLong();
    }

    /** Extract BlockPos from "dim|posLong". Dimension is ignored here since caller already knows it. */
    private static BlockPos posFromKey(String key) {
        int idx = key.indexOf('|');
        long packed = Long.parseLong(key.substring(idx + 1));
        return BlockPos.of(packed);
    }

    private static CompoundTag defaultTargetingTag() {
        CompoundTag root = new CompoundTag();
        root.put("targeting", TargetingConfig.DEFAULT.toTag());
        return root;
    }

    public record ValidationResult(
            int groupsRemoved,
            int controllersCleared,
            int dataLinksRemoved
    ) {}

    public ValidationResult validateAllKnownPositions(ServerLevel level, boolean onlyIfChunkLoaded) {
        if (level == null) return new ValidationResult(0,0,0);

        int groupsRemoved = 0;
        int controllersCleared = 0;
        int dataLinksRemoved = 0;

        ResourceKey<Level> levelDim = level.dimension();

        // i snapshot keys so i can mutate safely
        List<String> mountKeys = new ArrayList<>(groupsByMount.keySet());

        for (String mountKey : mountKeys) {
            Group group = groupsByMount.get(mountKey);
            if (group == null) continue;

            if (!group.key.dim().equals(levelDim)) continue;

            // if the mount is definitely gone, remove the whole group + indexes
            if (isDefinitelyMissing(level, group.key.mountPos(), onlyIfChunkLoaded, true)) {
                removeGroupFully(levelDim, mountKey, group);
                groupsRemoved++;
                continue;
            }

            // yaw
            if (group.yawPos != null && isDefinitelyMissing(level, group.yawPos, onlyIfChunkLoaded, true)) {
                controllerToMount.remove(key(levelDim, group.yawPos));
                group.yawPos = null;
                controllersCleared++;
            }

            // pitch
            if (group.pitchPos != null && isDefinitelyMissing(level, group.pitchPos, onlyIfChunkLoaded, true)) {
                controllerToMount.remove(key(levelDim, group.pitchPos));
                group.pitchPos = null;
                controllersCleared++;
            }

            // firing
            if (group.firingPos != null && isDefinitelyMissing(level, group.firingPos, onlyIfChunkLoaded, true)) {
                controllerToMount.remove(key(levelDim, group.firingPos));
                group.firingPos = null;
                controllersCleared++;
            }

            // datalinks
            if (!group.dataLinks.isEmpty()) {
                Iterator<BlockPos> it = group.dataLinks.iterator();
                while (it.hasNext()) {
                    BlockPos dlPos = it.next();

                    if (!isDefinitelyMissing(level, dlPos, onlyIfChunkLoaded, true)) {
                        continue; // PRESENT or UNKNOWN => keep
                    }

                    it.remove();
                    dataLinkToMount.remove(key(levelDim, dlPos));
                    dataLinksRemoved++;
                }
            }

            // delete group if empty (no links + no controllers)
            cleanupIfEmpty(levelDim, mountKey, group);
        }

        if (groupsRemoved != 0 || controllersCleared != 0 || dataLinksRemoved != 0) {
            setDirty();
        }

        return new ValidationResult(groupsRemoved, controllersCleared, dataLinksRemoved);
    }


    private void removeGroupFully(ResourceKey<Level> dim, String mountKey, Group group) {
        // clear controller indexes
        if (group.yawPos != null) controllerToMount.remove(key(dim, group.yawPos));
        if (group.pitchPos != null) controllerToMount.remove(key(dim, group.pitchPos));
        if (group.firingPos != null) controllerToMount.remove(key(dim, group.firingPos));

        // clear datalink index
        for (BlockPos dl : group.dataLinks) {
            dataLinkToMount.remove(key(dim, dl));
        }

        groupsByMount.remove(mountKey);
    }

    private enum Presence { PRESENT, MISSING, UNKNOWN }

    private Presence checkPresence(ServerLevel level, BlockPos pos, boolean onlyIfChunkLoaded, boolean requireBlockEntity) {
        if (pos == null) return Presence.MISSING;

        // i don't want this validator chunkloading the world
        if (onlyIfChunkLoaded && !level.hasChunkAt(pos)) {
            return Presence.UNKNOWN;
        }

        // air in a loaded chunk is a guaranteed delete signal
        if (level.isEmptyBlock(pos)) {
            return Presence.MISSING;
        }

        if (!requireBlockEntity) {
            return Presence.PRESENT;
        }

        // VS-safe: if the BE isn't accessible yet, don't delete links
        return level.getBlockEntity(pos) != null ? Presence.PRESENT : Presence.UNKNOWN;
    }

    private boolean isDefinitelyMissing(ServerLevel level, BlockPos pos, boolean onlyIfChunkLoaded, boolean requireBlockEntity) {
        return checkPresence(level, pos, onlyIfChunkLoaded, requireBlockEntity) == Presence.MISSING;
    }


}
