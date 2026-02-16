package com.happysg.radar.block.behavior.networks.config;

import net.minecraft.nbt.*;

import java.util.ArrayList;
import java.util.List;

public record IdentificationConfig(List<String> entries, String label) {

    public static final IdentificationConfig DEFAULT = new IdentificationConfig(List.of(), "");

    public IdentificationConfig {
        if (entries == null) entries = List.of();
        if (label == null) label = "";
    }

    public CompoundTag toTag() {
        CompoundTag t = new CompoundTag();

        ListTag names = new ListTag();
        for (String s : entries) {
            names.add(StringTag.valueOf(s == null ? "" : s));
        }

        // i keep writing a flags list (all zeros) so older readers dont choke,
        // but gameplay should ignore it completely
        ListTag flags = new ListTag();
        for (int i = 0; i < entries.size(); i++) {
            flags.add(ByteTag.valueOf((byte) 0));
        }

        t.put("names", names);
        t.put("flags", flags);
        t.putString("label", label);
        return t;
    }

    public static IdentificationConfig fromTag(CompoundTag t) {
        if (t == null || t.isEmpty()) return DEFAULT;

        ListTag names = t.getList("names", Tag.TAG_STRING);
        int n = names.size();

        List<String> outNames = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            outNames.add(names.getString(i));
        }

        return new IdentificationConfig(outNames, t.getString("label"));
    }

    // i keep these so old call sites compile without you refactoring everything at once
    public List<String> usernames() {
        return entries();
    }
}
