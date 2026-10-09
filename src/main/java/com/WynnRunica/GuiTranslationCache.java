package com.WynnRunica;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.WeakHashMap;

public class GuiTranslationCache {

    private record Shown(ItemStack copy, Text name, LoreComponent lore) {}

    private static final Map<ItemStack, Shown> shown = new WeakHashMap<>();
    private static final Map<ItemStack, ItemStack> originals = new WeakHashMap<>();

    public static void clear() {
        shown.clear();
        originals.clear();
    }

    public static ItemStack shownFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return stack;
        if (!Config.isTranslationEnabled() || !Config.isEnabled("Интерфейсы")) return stack;

        Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        Shown known = shown.get(stack);
        if (known != null && known.name() == name && known.lore() == lore) return known.copy();

        ItemStack copy = stack.copy();
        GuiTranslator.translateStack(copy);
        shown.put(stack, new Shown(copy, name, lore));
        originals.put(copy, stack);
        return copy;
    }

    public static ItemStack originalOf(ItemStack copy) {
        return originals.get(copy);
    }
}
