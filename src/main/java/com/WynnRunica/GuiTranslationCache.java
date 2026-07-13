package com.WynnRunica;

import net.minecraft.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GuiTranslationCache {

    public static final Map<Integer, ItemStack> originals = new ConcurrentHashMap<>();

    private static int lastSyncId = -1;

    public static void resetIfSyncIdChanged(int syncId) {
        if (syncId != lastSyncId) {
            originals.clear();
            lastSyncId = syncId;
        }
    }

    public static int keyFor(ItemStack stack) {
        return System.identityHashCode(stack);
    }

    public static void rememberOriginal(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        originals.putIfAbsent(keyFor(stack), stack.copy());
    }
}
