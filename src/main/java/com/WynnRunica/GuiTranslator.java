package com.WynnRunica;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class GuiTranslator {

    private static final String CENTER_MARKER = "<center>";
    private static final int SPACE_GLYPH_BASE = 0xD0000;
    private static final StyleSpriteSource.Font SPACE_FONT =
            new StyleSpriteSource.Font(Identifier.of("minecraft", "space"));

    public static void translateStack(ItemStack stack) {
        GuiTranslationCache.rememberOriginal(stack);

        Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
        boolean isCustom = name != null;
        if (name == null)
            name = stack.get(DataComponentTypes.ITEM_NAME);

        Text finalName = null;
        boolean nameChanged = false;
        boolean nameCentered = false;

        if (name != null) {
            var ex = TextEmojiUtils.extract(name);
            if (TextEmojiUtils.findWynncraftPixelStyle(name) == null) {
                String translated = TranslationPrinter.getGuiTranslation(ex.key);
                if (!translated.equals(ex.key)) {
                    nameCentered = translated.startsWith(CENTER_MARKER);
                    if (nameCentered) {
                        translated = translated.substring(CENTER_MARKER.length());
                        translated = stripLeadingAlignment(translated);
                    }

                    Style style = ex.contentStyle != null && ex.contentStyle != Style.EMPTY
                            ? ex.contentStyle
                            : Style.EMPTY;
                    finalName = TextEmojiUtils.rebuild(translated, ex.icons, style);
                    nameChanged = true;
                }
            }
        }

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        List<Text> newLines = null;
        List<Integer> centeredLines = new ArrayList<>();
        boolean loreChanged = false;
        boolean characterProfile = isCharacterProfile(lore);

        if (characterProfile && name != null) {
            nameCentered = true;
        }

        if (lore != null) {
            newLines = new ArrayList<>();

            for (Text line : lore.lines()) {
                var ex = TextEmojiUtils.extract(line);
                boolean profilePage = characterProfile && isProfilePageLine(ex.key);
                boolean profilePager = characterProfile && isProfilePagerLine(ex.key);
                if (TextEmojiUtils.findWynncraftPixelStyle(line) != null && !profilePage) {
                    newLines.add(line);
                    if (profilePager) {
                        centeredLines.add(newLines.size() - 1);
                    }
                    continue;
                }
                String translated = TranslationPrinter.getGuiTranslation(ex.key);
                if (!translated.equals(ex.key)) {
                    boolean centered = translated.startsWith(CENTER_MARKER) || profilePage || profilePager;
                    if (centered) {
                        if (translated.startsWith(CENTER_MARKER)) {
                            translated = translated.substring(CENTER_MARKER.length());
                            translated = stripLeadingAlignment(translated);
                        }
                    }

                    Style style = ex.contentStyle != null && ex.contentStyle != Style.EMPTY
                            ? ex.contentStyle
                            : Style.EMPTY;
                    if (centered)
                        centeredLines.add(newLines.size());
                    newLines.add(TextEmojiUtils.rebuild(translated, ex.icons, style));
                    loreChanged = true;

                } else {
                    newLines.add(line);
                    if (profilePage || profilePager) {
                        centeredLines.add(newLines.size() - 1);
                    }
                }
            }
        }

        if (nameChanged) {
            if (isCustom)
                stack.set(DataComponentTypes.CUSTOM_NAME, finalName);
            else
                stack.set(DataComponentTypes.ITEM_NAME, finalName);
        }

        if (loreChanged) {
            stack.set(DataComponentTypes.LORE, new LoreComponent(newLines));
        }

        if (nameCentered || !centeredLines.isEmpty()) {
            boolean centerName = nameCentered;
            Text nameForCentering = finalName != null ? finalName : name;
            List<Text> linesForCentering = newLines;
            MinecraftClient.getInstance().execute(() -> {
                var textRenderer = MinecraftClient.getInstance().textRenderer;
                int tooltipWidth = 0;
                if (nameForCentering != null) {
                    Text measuredName = centerName
                            ? removeLeadingSpaceGlyph(nameForCentering, characterProfile)
                            : nameForCentering;
                    tooltipWidth = Math.max(tooltipWidth, visibleTextWidth(measuredName, textRenderer));
                }
                if (linesForCentering != null) {
                    for (int i = 0; i < linesForCentering.size(); i++) {
                        Text line = linesForCentering.get(i);
                        Text measuredLine = centeredLines.contains(i) ? removeLeadingSpaceGlyph(line) : line;
                        tooltipWidth = Math.max(tooltipWidth, visibleTextWidth(measuredLine, textRenderer));
                    }
                }

                if (centerName) {
                    Text centered = centerLine(nameForCentering, tooltipWidth, textRenderer, characterProfile);
                    if (isCustom)
                        stack.set(DataComponentTypes.CUSTOM_NAME, centered);
                    else
                        stack.set(DataComponentTypes.ITEM_NAME, centered);
                }
                if (linesForCentering != null && !centeredLines.isEmpty()) {
                    List<Text> centeredList = new ArrayList<>(linesForCentering);
                    for (int idx : centeredLines) {
                        centeredList.set(idx, centerLine(centeredList.get(idx), tooltipWidth, textRenderer, false));
                    }
                    stack.set(DataComponentTypes.LORE, new LoreComponent(centeredList));
                }
            });
        }
    }

    private static Text centerLine(Text line, int tooltipWidth, net.minecraft.client.font.TextRenderer textRenderer,
                                   boolean stripPlainHeaderSpace) {
        Text content = removeLeadingSpaceGlyph(line, stripPlainHeaderSpace);
        int pad = Math.max(0, (tooltipWidth - visibleTextWidth(content, textRenderer)) / 2);
        if (pad == 0)
            return content;

        String spacer = new String(Character.toChars(SPACE_GLYPH_BASE + pad));
        return Text.empty()
                .append(Text.literal(spacer).setStyle(Style.EMPTY.withFont(SPACE_FONT)))
                .append(content);
    }

    private static int visibleTextWidth(Text line, net.minecraft.client.font.TextRenderer textRenderer) {
        return textRenderer.getWidth(withoutMinecraftSpaceGlyphs(line));
    }

    private static Text withoutMinecraftSpaceGlyphs(Text line) {
        MutableText copy = Text.literal("");
        copyWithoutMinecraftSpaceGlyphs(line, Style.EMPTY, copy);
        return copy;
    }

    private static void copyWithoutMinecraftSpaceGlyphs(Text node, Style parentStyle, MutableText result) {
        Style style = node.getStyle().withParent(parentStyle);
        if (!isMinecraftSpaceStyle(style)) {
            node.getContent().visit(text -> {
                result.append(Text.literal(text).setStyle(style));
                return java.util.Optional.empty();
            });
        }
        for (Text sibling : node.getSiblings()) {
            copyWithoutMinecraftSpaceGlyphs(sibling, style, result);
        }
    }

    private static boolean isCharacterProfile(LoreComponent lore) {
        if (lore == null)
            return false;

        int markers = 0;
        for (Text line : lore.lines()) {
            String key = TextEmojiUtils.extract(line).key.replace("<em>", "");
            if (key.contains("Total Lv:") || key.contains("Combat Lv:") || key.contains("Identifications:")
                    || key.contains("Общий ур.") || key.contains("Боевой ур.") || key.contains("Характеристики:")) {
                markers++;
            }
        }
        return markers >= 2;
    }

    private static boolean isProfilePageLine(String key) {
        String visible = key.replace("<em>", "").trim();
        return visible.matches("(?:Page|Страница)\\s+\\d+");
    }

    private static boolean isProfilePagerLine(String key) {
        String visible = key.replace("<em>", "").trim();
        if (visible.isEmpty())
            return false;

        boolean left = false;
        boolean right = false;
        boolean square = false;
        for (int offset = 0; offset < visible.length();) {
            int codePoint = visible.codePointAt(offset);
            if (Character.isWhitespace(codePoint)) {
                offset += Character.charCount(codePoint);
                continue;
            }
            if (codePoint == '«' || codePoint == '‹') {
                left = true;
            } else if (codePoint == '»' || codePoint == '›') {
                right = true;
            } else if (codePoint == '■' || codePoint == '□') {
                square = true;
            } else {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return left && right && square;
    }

    private static Text removeLeadingSpaceGlyph(Text line) {
        return removeLeadingSpaceGlyph(line, false);
    }

    private static Text removeLeadingSpaceGlyph(Text line, boolean stripPlainHeaderSpace) {
        var extracted = TextEmojiUtils.extract(line);
        if (!extracted.key.startsWith("<em>") || extracted.icons.isEmpty()
                || !isMinecraftSpaceGlyph(extracted.icons.getFirst())) {
            return line;
        }

        boolean stripLeadingPlainSpace = stripPlainHeaderSpace && extracted.key.startsWith("<em> ")
                && !extracted.key.startsWith("<em><em>");
        MutableText copy = Text.literal("");
        copyWithoutLeadingSpaceGlyph(line, Style.EMPTY, copy,
                new LeadingSpaceGlyphState(stripLeadingPlainSpace));
        return copy;
    }

    private static void copyWithoutLeadingSpaceGlyph(Text node, Style parentStyle,
                                                      MutableText result,
                                                      LeadingSpaceGlyphState state) {
        Style style = node.getStyle().withParent(parentStyle);
        node.getContent().visit(text -> {
            appendPreservedRun(result, text, style, state);
            return java.util.Optional.empty();
        });
        for (Text sibling : node.getSiblings()) {
            copyWithoutLeadingSpaceGlyph(sibling, style, result, state);
        }
    }

    private static void appendPreservedRun(MutableText result, String text, Style style,
                                           LeadingSpaceGlyphState state) {
        int offset = 0;
        if (!state.alignmentRemoved && isMinecraftSpaceStyle(style) && !text.isEmpty()) {
            offset = Character.charCount(text.codePointAt(0));
            state.alignmentRemoved = true;
        }

        if (state.alignmentRemoved && state.stripPlainHeaderSpace && offset < text.length()
                && !isMinecraftSpaceStyle(style)) {
            int codePoint = text.codePointAt(offset);
            if (Character.isWhitespace(codePoint)) {
                offset += Character.charCount(codePoint);
            }
            state.stripPlainHeaderSpace = false;
        }

        if (offset < text.length()) {
            result.append(Text.literal(text.substring(offset)).setStyle(style));
        }
    }

    private static final class LeadingSpaceGlyphState {
        private boolean alignmentRemoved;
        private boolean stripPlainHeaderSpace;

        private LeadingSpaceGlyphState(boolean stripPlainHeaderSpace) {
            this.stripPlainHeaderSpace = stripPlainHeaderSpace;
        }
    }

    private static boolean isMinecraftSpaceGlyph(Text text) {
        return isMinecraftSpaceStyle(text.getStyle());
    }

    private static boolean isMinecraftSpaceStyle(Style style) {
        StyleSpriteSource font = style.getFont();
        return font != null && font.toString().contains("minecraft:space");
    }

    private static String stripLeadingAlignment(String value) {
        StringBuilder formatting = new StringBuilder();
        int offset = 0;
        while (offset < value.length()) {
            if (value.charAt(offset) == '§' && offset + 1 < value.length()) {
                int length = value.charAt(offset + 1) == '#' && offset + 7 < value.length() ? 8 : 2;
                formatting.append(value, offset, offset + length);
                offset += length;
                continue;
            }
            int codePoint = value.codePointAt(offset);
            boolean alignment = Character.isWhitespace(codePoint)
                    || (codePoint >= 0xC0000 && codePoint <= 0xDFFFF);
            if (!alignment)
                break;
            offset += Character.charCount(codePoint);
        }
        return formatting.append(value.substring(offset)).toString();
    }

    public static List<Text> translatePixelTooltip(List<Text> tooltip) {
        if (!WynnRunicaClient.enabled || tooltip == null || tooltip.isEmpty())
            return tooltip;

        List<Text> translatedLines = null;
        for (int i = 0; i < tooltip.size(); i++) {
            Text line = tooltip.get(i);
            var extracted = TextEmojiUtils.extract(line);
            Style style = TextEmojiUtils.findWynncraftPixelStyle(line);
            if (style == null)
                continue;

            String translated = TranslationPrinter.findGuiTranslation(extracted.key);
            if (translated == null)
                continue;

            if (translatedLines == null)
                translatedLines = new ArrayList<>(tooltip);
            translatedLines.set(i, TextEmojiUtils.rebuild(translated, extracted.icons, style));
        }
        return translatedLines == null ? tooltip : translatedLines;
    }

    public static void refreshOpenScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null)
            return;
        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null)
            return;

        boolean enabled = WynnRunicaClient.enabled;

        for (Slot slot : handler.slots) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty())
                continue;
            ItemStack original = GuiTranslationCache.originals.get(GuiTranslationCache.keyFor(stack));
            if (original != null) {
                restoreComponent(stack, original, DataComponentTypes.CUSTOM_NAME);
                restoreComponent(stack, original, DataComponentTypes.ITEM_NAME);
                restoreComponent(stack, original, DataComponentTypes.LORE);
            }

            if (enabled) {
                translateStack(stack);
            }
        }
    }

    private static <T> void restoreComponent(ItemStack stack, ItemStack original,
            ComponentType<T> type) {
        T value = original.get(type);
        if (value != null) {
            stack.set(type, value);
        } else {
            stack.remove(type);
        }
    }
}
