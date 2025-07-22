package com.happysg.radar.networking.networkhandlers;

import net.minecraft.nbt.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ListNBTHandler {

    private static final String ENTRIES_KEY = "EntriesList";
    private static final String FRIEND_OR_FOE_KEY = "FriendOrFoeList";
    private static final String SINGLE_KEY = "IDSTRING";

    public static void saveToHeldItem(Player player, List<String> entries, List<Boolean> friendOrFoe) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;

        CompoundTag tag = stack.getOrCreateTag();


        ListTag entriesTag = new ListTag();
        for (String s : entries) {
            entriesTag.add(StringTag.valueOf(s));
        }
        tag.put(ENTRIES_KEY, entriesTag);


        ListTag foeTag = new ListTag();
        for (Boolean b : friendOrFoe) {
            foeTag.add(ByteTag.valueOf(b ? (byte) 1 : (byte) 0));
        }
        tag.put(FRIEND_OR_FOE_KEY, foeTag);

        stack.setTag(tag);
    }


    public static LoadedLists loadFromHeldItem(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty() || !stack.hasTag()) return new LoadedLists();

        CompoundTag tag = stack.getTag();
        LoadedLists loaded = new LoadedLists();


        ListTag entriesTag = tag.getList(ENTRIES_KEY, Tag.TAG_STRING);
        for (int i = 0; i < entriesTag.size(); i++) {
            loaded.entries.add(entriesTag.getString(i));
        }


        ListTag foeTag = tag.getList(FRIEND_OR_FOE_KEY, Tag.TAG_BYTE);
        for (int i = 0; i < foeTag.size(); i++) {
            byte b = foeTag.get(i).getId();
            loaded.friendOrFoe.add(b != 0);
        }

        return loaded;
    }

    public static void saveStringToHeldItem(Player player, String value) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(SINGLE_KEY, value);

    }

    public static String loadStringFromHeldItem(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return "";
        CompoundTag tag = stack.getTag();
        if (tag == null) return "";
        return tag.getString(SINGLE_KEY);
    }
    public static class LoadedId {
        public static String storedID = new String();
    }
    public static class LoadedLists {
        public final List<String> entries = new ArrayList<>();
        public final List<Boolean> friendOrFoe = new ArrayList<>();
    }
}
