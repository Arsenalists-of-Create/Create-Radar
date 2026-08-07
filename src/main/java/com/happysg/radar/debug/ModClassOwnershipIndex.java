package com.happysg.radar.debug;

import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.ModFileScanData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exact class ownership derived from NeoForge's existing scan data. */
public final class ModClassOwnershipIndex {
    private final Map<String, List<ClassOwnership.Owner>> ownersByClass;
    private final Map<String, String> versionsByMod;
    private final Set<String> createRadarClasses;

    private ModClassOwnershipIndex(
            Map<String, List<ClassOwnership.Owner>> ownersByClass,
            Map<String, String> versionsByMod,
            Set<String> createRadarClasses) {
        this.ownersByClass = Map.copyOf(ownersByClass);
        this.versionsByMod = Map.copyOf(versionsByMod);
        this.createRadarClasses = Set.copyOf(createRadarClasses);
    }

    public static ModClassOwnershipIndex capture() {
        HashMap<String, ArrayList<ClassOwnership.Owner>> mutable =
                new HashMap<>();
        HashMap<String, String> versions = new HashMap<>();
        LinkedHashSet<String> ownClasses = new LinkedHashSet<>();
        for (IModFileInfo fileInfo : ModList.get().getModFiles()) {
            List<IModInfo> mods = fileInfo.getMods();
            if (mods.isEmpty()) continue;
            String fileName = fileInfo.getFile().getFileName();
            ArrayList<ClassOwnership.Owner> fileOwners = new ArrayList<>();
            for (IModInfo mod : mods) {
                String id = mod.getModId();
                String version = mod.getVersion().toString();
                versions.put(id, version);
                fileOwners.add(new ClassOwnership.Owner(id, version, fileName));
            }
            ModFileScanData scan = fileInfo.getFile().getScanResult();
            if (scan == null) continue;
            for (ModFileScanData.ClassData classData : scan.getClasses()) {
                String className = classData.clazz().getClassName();
                ArrayList<ClassOwnership.Owner> owners = mutable.computeIfAbsent(
                        className, ignored -> new ArrayList<>());
                for (ClassOwnership.Owner owner : fileOwners) {
                    if (!owners.contains(owner)) owners.add(owner);
                    if (owner.modId().equals("create_radar")) {
                        ownClasses.add(className);
                    }
                }
            }
        }
        HashMap<String, List<ClassOwnership.Owner>> frozen = new HashMap<>();
        mutable.forEach((name, owners) -> frozen.put(name,
                List.copyOf(owners)));
        return new ModClassOwnershipIndex(frozen, versions, ownClasses);
    }

    public List<ClassOwnership.Owner> owners(String className) {
        return ownersByClass.getOrDefault(normalize(className), List.of());
    }

    public List<String> modIds(String className) {
        return owners(className).stream().map(ClassOwnership.Owner::modId)
                .distinct().sorted().toList();
    }

    public String version(String modId) {
        return versionsByMod.getOrDefault(modId, "unknown");
    }

    public Set<String> createRadarClasses() {
        return createRadarClasses;
    }

    public ClassOwnership describe(String className) {
        List<ClassOwnership.Owner> owners = owners(className);
        return new ClassOwnership(normalize(className), owners,
                owners.stream().map(ClassOwnership.Owner::fileName)
                        .distinct().count() > 1);
    }

    private static String normalize(String className) {
        if (className == null) return "";
        return className.replace('/', '.');
    }
}
