package com.WynnRunica;

import net.fabricmc.loader.api.FabricLoader;
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
import net.minecraft.util.Formatting;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.List;

public class GuiTranslator {

    private static final String CENTER_MARKER = "<center>";
    private static final java.util.regex.Pattern COLOR_CODES = java.util.regex.Pattern.compile("§(?:#[0-9a-fA-F]{6}|.)");
    private static final java.util.regex.Pattern PER_SECOND = java.util.regex.Pattern.compile("/\\d+s\\b");
    private static final int SPACE_GLYPH_BASE = 0xD0000;
    private static final StyleSpriteSource.Font SPACE_FONT =
            new StyleSpriteSource.Font(Identifier.of("minecraft", "space"));

    public static Text translateTitle(Text text) {
        if (text == null || !Config.isTranslationEnabled() || !Config.isEnabled("Интерфейсы")) return text;
        Text banner = NameplateStyler.translatePills(text);
        if (banner != text) return banner;
        var ex = TextEmojiUtils.extractTooltip(text);
        if (ex.key.isBlank()) return text;
        String translated = TranslationManager.getGuiTranslation(ex.key);
        String name = MinecraftClient.getInstance().getSession().getUsername();
        if (translated.equals(ex.key) && ex.key.contains(name)) {
            String masked = ex.key.replace(name, "<playername>");
            String found = TranslationManager.getGuiTranslation(masked);
            if (!found.equals(masked)) translated = found.replace("<playername>", name);
        }
        if (translated.equals(ex.key)) {
            TelemetrySender.recordTitle(ex.key, text);
            return text;
        }
        Style style = ex.contentStyle == null ? Style.EMPTY : ex.contentStyle;
        return TooltipNumberColors.preserve(text, TextEmojiUtils.rebuild(translated, ex.icons, style, ex.key));
    }

    public static void translateStack(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        List<String> loreKeys = new ArrayList<>();
        if (lore != null) {
            for (Text line : lore.lines()) {
                loreKeys.add(TextEmojiUtils.extract(line).key);
            }
        }

        Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
        boolean isCustom = name != null;
        if (name == null)
            name = stack.get(DataComponentTypes.ITEM_NAME);

        Text finalName = null;
        boolean nameChanged = false;
        boolean nameCentered = false;
        GuiScope guiScope = null;

        if (name != null) {
            var ex = TextEmojiUtils.extractTooltip(name);
            if (TextEmojiUtils.findWynncraftPixelStyle(name) == null
                    && !keepEnglishForWynntils(ex.key)) {
                guiScope = TranslationManager.findScopeByTitle(ex.key, loreKeys, currentScreenKey(),
                        Registries.ITEM.getId(stack.getItem()).toString());
                GuiScope titleScope = guiScope;
                boolean unlockAbility = TranslationManager.isUnlockAbilityTitle(ex.key) && titleScope != null
                        && titleScope.nameRu != null && !titleScope.nameRu.isBlank();
                String translated;
                if (unlockAbility) {
                    translated = "§a" + TranslationManager.getGuiTranslation("Unlock Ability") + " §*§l" + titleScope.nameRu;
                } else if (titleScope != null && titleScope.nameRu != null && !titleScope.nameRu.isBlank()) {
                    translated = titleScope.nameRu;
                } else {
                    translated = TranslationManager.getGuiTranslation(ex.key);
                }
                if (!translated.equals(ex.key)) {
                    nameCentered = translated.startsWith(CENTER_MARKER);
                    if (nameCentered) {
                        translated = translated.substring(CENTER_MARKER.length());
                        translated = stripLeadingAlignment(translated);
                    }

                    Style style = ex.contentStyle != null && ex.contentStyle != Style.EMPTY
                            ? ex.contentStyle
                            : Style.EMPTY;
                    translated = translated.replace("§*", TextEmojiUtils.accentCode(name));
                    finalName = TooltipNumberColors.preserve(name,
                            TextEmojiUtils.rebuild(translated, ex.icons, style, ex.key));
                    nameChanged = true;
                }
            }
        }

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
                var ex = TextEmojiUtils.extractTooltip(line);
                boolean profilePage = characterProfile && isProfilePageLine(ex.key);
                boolean profilePager = characterProfile && isProfilePagerLine(ex.key);
                if (TextEmojiUtils.findWynncraftPixelStyle(line) != null && !profilePage) {
                    newLines.add(line);
                    if (profilePager) {
                        centeredLines.add(newLines.size() - 1);
                    }
                    continue;
                }
                String translated = TranslationManager.getGuiTranslation(ex.key, guiScope);
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
                    translated = translated.replace("§*", TextEmojiUtils.accentCode(line));
                    if (centered)
                        centeredLines.add(newLines.size());
                    newLines.add(TooltipNumberColors.preserve(line,
                            TextEmojiUtils.rebuild(translated, ex.icons, style, ex.key)));
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

    private static final java.util.regex.Pattern WYNNTILS_CONTAINER_BUTTONS =
            java.util.regex.Pattern.compile(
                    "§7(?:Next|Previous) Page"
                            + "|§a§l(?:Next|Previous) Page"
                            + "|§f§lPage \\d+§a [<>].*"
                            + "|§7Click again to confirm"
                            + "|§c§lClose Chest");

    private static boolean keepEnglishForWynntils(String key) {
        return key != null
                && FabricLoader.getInstance().isModLoaded("wynntils")
                && WYNNTILS_CONTAINER_BUTTONS.matcher(key).matches();
    }

    public static List<Text> translatePixelTooltip(List<Text> tooltip) {
        if (!Config.isTranslationEnabled() || !Config.isEnabled("Предметы") || tooltip == null || tooltip.isEmpty())
            return tooltip;

        List<Text> result = FabricLoader.getInstance().isModLoaded("wynntils")
                ? translateWynntilsPixelTooltip(tooltip) : translateVanillaPixelTooltip(tooltip);
        if (Config.DEBUG) TooltipCaptureLogger.dumpFinal(tooltip, result);
        return result;
    }

    private static List<Text> translateWynntilsPixelTooltip(List<Text> tooltip) {
        return widenColumns(tooltip, translatePixelTooltip(tooltip, true));
    }

    private static final int COLUMN_GAP = 8;

    private record Glyph(int codePoint, Style style, int advance) {}

    private enum Align { NONE, COLUMN, CENTER, PARAGRAPH, RIGHT, CELLS, GRID }

    private static String widenedKey;
    private static List<Text> widenedValue;

    private static final int MIN_ALIGN_INDENT = 8;

    public static List<Text> widenColumns(List<Text> input, List<Text> output) {
        if (input == output) return output;
        StringBuilder key = new StringBuilder();
        for (List<Text> lines : List.of(input, output)) {
            for (Text line : lines) {
                line.visit((style, text) -> {
                    key.append(text).append('\u0000').append(style.hashCode()).append('\u0000');
                    return java.util.Optional.empty();
                }, Style.EMPTY);
                key.append('\n');
            }
        }
        if (key.toString().equals(widenedKey)) return widenedValue;

        int count = Math.min(input.size(), output.size());
        List<List<Glyph>> before = new ArrayList<>();
        int width = 0;
        for (Text line : input) {
            List<Glyph> glyphs = glyphs(line);
            before.add(glyphs);
            width = Math.max(width, advance(glyphs, 0, glyphs.size()));
        }

        int bodyStart = 0;
        int headerStart = 0;
        while (headerStart < before.size() && leadEnd(before.get(headerStart)) == 0) headerStart++;
        boolean itemHeader = headerStart < before.size() && indent(before.get(headerStart)) < 0;
        if (itemHeader) {
            bodyStart = headerStart;
            while (bodyStart < before.size() && !before.get(bodyStart).isEmpty() && indent(before.get(bodyStart)) < 0) bodyStart++;
            int beside = bodyStart < before.size() ? indent(before.get(bodyStart)) : 0;
            while (beside > 0 && bodyStart < before.size() && !before.get(bodyStart).isEmpty()
                    && indent(before.get(bodyStart)) == beside) bodyStart++;
        }

        int widest = 0;
        for (List<Glyph> glyphs : before) {
            if (advance(glyphs, 0, glyphs.size()) == width) widest++;
        }

        List<Integer> centerAxes = sharedAxes(before, bodyStart, count);

        Align[] align = new Align[count];
        int[] paragraph = new int[count];
        java.util.Map<Integer, List<Glyph>> cellLines = new java.util.HashMap<>();
        java.util.HashSet<Integer> wideParagraphs = new java.util.HashSet<>();
        for (int i = 0; i < count; i++) {
            align[i] = Align.NONE;
            paragraph[i] = -1;
        }
        int columnsRight = 0;
        int columnsGap = 0;
        for (int i = bodyStart; i < count; i++) {
            List<Glyph> glyphs = before.get(i);
            int[] jump = columnJump(glyphs);
            if (hasLead(glyphs) || jump == null) continue;
            columnsRight = Math.max(columnsRight, visibleEnd(glyphs));
            columnsGap = Math.max(columnsGap, advance(glyphs, jump[0], jump[1]));
        }
        for (int i = bodyStart; i < count; ) {
            List<Glyph> glyphs = before.get(i);
            if (!hasLead(glyphs)) {
                if (columnJump(glyphs) != null && visibleEnd(glyphs) == columnsRight && columnsGap >= COLUMN_GAP) {
                    align[i] = Align.COLUMN;
                }
                i++;
                continue;
            }
            int last = i;
            while (last + 1 < count && hasLead(before.get(last + 1))) last++;

            int blockWidth = 0;
            int deepest = 0;
            for (int k = i; k <= last; k++) {
                blockWidth = Math.max(blockWidth, visibleEnd(before.get(k)));
                deepest = Math.max(deepest, indent(before.get(k)));
            }
            boolean broken = false;
            for (int k = i; k <= last; k++) {
                List<int[]> row = cells(before.get(k));
                if (!row.isEmpty() && row.getFirst()[2] != indent(before.get(k))) broken = true;
            }
            if (broken && before.get(i).getFirst().codePoint() == '§') {
                i = last + 1;
                continue;
            }
            int[] axes = columnAxes(before, output, i, last, width);
            java.util.Map<Integer, List<Glyph>> grid = axes == null ? gridLines(before, output, i, last) : null;
            boolean block = last > i && deepest >= MIN_ALIGN_INDENT;
            for (int k = i; k <= last && block; k++) {
                if (!isCentered(before.get(k), blockWidth)) block = false;
            }
            for (int k = i; k <= last; k++) {
                List<Glyph> line = before.get(k);
                if (grid != null && grid.containsKey(k)) {
                    align[k] = Align.GRID;
                    cellLines.put(k, grid.get(k));
                    continue;
                }
                if (axes != null && cells(line).size() >= 2) {
                    align[k] = Align.CELLS;
                    cellLines.put(k, placeCells(cells(line), glyphs(output.get(k)), axes));
                    continue;
                }
                boolean listed = false;
                for (int other = i; other <= last; other++) {
                    if (other != k && sharesLeftEdge(line, before.get(other))) listed = true;
                    if (other != k && hasLead(before.get(other)) && indent(before.get(other)) == indent(line)
                            && !isCentered(line, width)) listed = true;
                }
                boolean holdsWidth = widest == 1 && advance(line, 0, line.size()) == width;
                if (block) {
                    align[k] = Align.PARAGRAPH;
                    paragraph[k] = i;
                    if (blockWidth >= width - 2) wideParagraphs.add(i);
                } else if (listed) {
                    continue;
                } else if (line.getFirst().codePoint() == '§' && cells(line).size() >= 2 && !onAxis(line, centerAxes)) {
                    continue;
                } else if ((indent(line) >= MIN_ALIGN_INDENT || k == 0 && indent(line) >= 3) && isCentered(line, width)
                        || isExactlyCentered(line, width)
                        || isPaddedCenter(line, width)
                        || onAxis(line, centerAxes)) {
                    align[k] = Align.CENTER;
                } else if (indent(line) >= MIN_ALIGN_INDENT && visibleEnd(line) == width && !holdsWidth) {
                    align[k] = Align.RIGHT;
                }
            }
            boolean table = false;
            for (int k = i; k <= last; k++) {
                if (align[k] == Align.CENTER && cells(before.get(k)).size() >= 4) table = true;
            }
            for (int k = i; k <= last && table; k++) {
                if (align[k] == Align.NONE && cells(before.get(k)).size() >= 4) align[k] = Align.CENTER;
            }
            i = last + 1;
        }

        for (int k = bodyStart + 1; k < count; k++) {
            List<Glyph> line = before.get(k);
            if (align[k] != Align.NONE || !hasLead(line)) continue;
            Glyph tail = line.getLast();
            if (!isMinecraftSpaceStyle(tail.style()) && !isAlignmentCodePoint(tail.codePoint())) continue;
            int above = k - 1;
            while (above > bodyStart && advance(before.get(above), 0, before.get(above).size()) == 0) above--;
            List<Glyph> upper = before.get(above);
            boolean sameSpan = advance(line, 0, line.size()) == advance(upper, 0, upper.size());
            if (align[above] == Align.CENTER && Math.abs(indent(line) - indent(upper)) <= 6
                    && (sameSpan || Math.abs(visibleEnd(line) - visibleEnd(upper)) <= 3)) {
                align[k] = Align.CENTER;
            }
        }

        if (new java.io.File("C:/Users/Administrator/AppData/Local/Temp/wr-debug-align").exists()) { System.err.println("ALIGN width=" + width + " body=" + bodyStart + " " + java.util.Arrays.toString(align)); for (int q = 0; q < count; q++) System.err.println("  L" + q + " lead=" + hasLead(before.get(q)) + " ind=" + indent(before.get(q)) + " end=" + visibleEnd(before.get(q)) + " cells=" + cells(before.get(q)).size() + " first=" + (before.get(q).isEmpty() ? -1 : before.get(q).getFirst().codePoint())); }
        List<List<Glyph>> after = new ArrayList<>();
        java.util.HashSet<Integer> untouched = new java.util.HashSet<>();
        for (int i = 0; i < output.size(); i++) {
            List<Glyph> glyphs = glyphs(output.get(i));
            if (cellLines.containsKey(i)) glyphs = cellLines.get(i);
            if (i < count && align[i] == Align.CENTER && before.get(i).equals(glyphs)) {
                untouched.add(i);
                after.add(glyphs);
                continue;
            }
            if (i < count && (align[i] == Align.CENTER || align[i] == Align.PARAGRAPH || align[i] == Align.RIGHT)) {
                glyphs = new ArrayList<>(glyphs.subList(leadEnd(glyphs), glyphs.size()));
            }
            after.add(glyphs);
        }

        int newWidth = 0;
        for (int i = 0; i < after.size(); i++) {
            if (i < count && align[i] == Align.COLUMN) continue;
            if (i < count && align[i] == Align.RIGHT) newWidth = Math.max(newWidth, visibleEnd(after.get(i)));
            else if (i < count && align[i] == Align.CELLS) {
                newWidth = Math.max(newWidth, Math.max(2 * visibleEnd(after.get(i)) - width, width - 2 * indent(after.get(i))));
            } else newWidth = Math.max(newWidth, advance(after.get(i), 0, after.get(i).size()));
        }

        int normalGap = Integer.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            if (align[i] == Align.COLUMN) normalGap = Math.min(normalGap, columnGap(before.get(i)));
        }
        int newColumns = columnsRight;
        if (normalGap != Integer.MAX_VALUE) {
            int targetGap = Math.min(normalGap, COLUMN_GAP);
            for (int i = 0; i < count; i++) {
                if (align[i] != Align.COLUMN) continue;
                List<Glyph> glyphs = after.get(i);
                int[] jump = lastJump(glyphs);
                if (jump == null) continue;
                int name = advance(glyphs, 0, jump[0]);
                int value = visibleEnd(glyphs) - advance(glyphs, 0, jump[1]);
                newColumns = Math.max(newColumns, name + targetGap + value);
            }
            newColumns = Math.max(newColumns, newWidth - (width - columnsRight));
            newWidth = Math.max(newWidth, newColumns);
        }

        List<Text> result = new ArrayList<>(output);
        boolean changed = false;
        for (int i = 0; i < count; i++) {
            if (newWidth == width && newColumns == columnsRight && before.get(i).equals(glyphs(output.get(i)))) continue;
            List<Glyph> glyphs = after.get(i);
            int at = 0;
            int shift = 0;
            if (align[i] == Align.COLUMN) {
                int[] jump = lastJump(glyphs);
                if (jump == null) continue;
                at = jump[1];
                shift = newColumns - visibleEnd(glyphs);
            } else if (align[i] == Align.CENTER && untouched.contains(i)) {
                shift = (newWidth - width) / 2;
            } else if (align[i] == Align.CENTER) {
                shift = (newWidth - visibleEnd(glyphs)) / 2;
            } else if (align[i] == Align.PARAGRAPH) {
                int blockWidth = 0;
                for (int k = paragraph[i]; k < count && paragraph[k] == paragraph[i]; k++) {
                    blockWidth = Math.max(blockWidth, visibleEnd(after.get(k)));
                }
                if (wideParagraphs.contains(paragraph[i])) blockWidth = newWidth;
                shift = (blockWidth - visibleEnd(glyphs)) / 2;
            } else if (align[i] == Align.RIGHT) {
                shift = newWidth - visibleEnd(glyphs);
            } else if (align[i] == Align.CELLS) {
                shift = indent(glyphs) + (newWidth - width) / 2;
                glyphs = new ArrayList<>(glyphs.subList(leadEnd(glyphs), glyphs.size()));
            } else if (align[i] == Align.GRID) {
                shift = 0;
            } else {
                continue;
            }
            if (shift != 0) {
                glyphs = new ArrayList<>(glyphs);
                glyphs.add(at, new Glyph(SPACE_GLYPH_BASE + shift, Style.EMPTY.withFont(SPACE_FONT), shift));
            }
            result.set(i, toText(glyphs));
            changed = true;
        }
        if (!changed) result = output;
        widenedKey = key.toString();
        widenedValue = result;
        return result;
    }

    private static List<int[]> cells(List<Glyph> glyphs) {
        List<int[]> cells = new ArrayList<>();
        int[] cell = null;
        int x = 0;
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph glyph = glyphs.get(i);
            if (isMinecraftSpaceStyle(glyph.style()) || isAlignmentCodePoint(glyph.codePoint())) {
                cell = null;
            } else if (isVisible(glyph)) {
                if (cell == null) {
                    cell = new int[]{i, i + 1, x, x + glyph.advance()};
                    cells.add(cell);
                }
                cell[1] = i + 1;
                cell[3] = x + glyph.advance();
            }
            x += glyph.advance();
        }
        return cells;
    }

    private static List<int[]> units(List<int[]> cells, List<int[]> shape) {
        List<int[]> units = new ArrayList<>();
        for (int c = 0; c < cells.size(); c++) {
            int[] cell = cells.get(c);
            boolean hangs = c + 1 < shape.size() && shape.get(c)[3] - shape.get(c)[2] <= 12
                    && shape.get(c + 1)[2] - shape.get(c)[3] <= 4;
            if (hangs) {
                int[] next = cells.get(c + 1);
                units.add(new int[]{cell[0], next[1], cell[2], next[3], next[2]});
                c++;
            } else {
                units.add(new int[]{cell[0], cell[1], cell[2], cell[3], cell[2]});
            }
        }
        return units;
    }

    private static java.util.Map<Integer, List<Glyph>> gridLines(List<List<Glyph>> before, List<Text> output, int first, int last) {
        java.util.Map<Integer, List<int[]>> was = new java.util.TreeMap<>();
        java.util.Map<Integer, List<int[]>> now = new java.util.HashMap<>();
        java.util.Map<Integer, List<Glyph>> translated = new java.util.HashMap<>();
        java.util.TreeMap<Integer, Integer> rowsAt = new java.util.TreeMap<>();
        for (int i = first; i <= last && i < output.size(); i++) {
            List<int[]> shape = cells(before.get(i));
            List<Glyph> glyphs = glyphs(output.get(i));
            List<int[]> cells = cells(glyphs);
            if (shape.size() < 2 || cells.size() != shape.size()) continue;
            List<int[]> old = units(shape, shape);
            if (old.size() < 2) continue;
            was.put(i, old);
            now.put(i, units(cells, shape));
            translated.put(i, glyphs);
            for (int u = 1; u < old.size(); u++) {
                Integer near = rowsAt.floorKey(old.get(u)[4] + 4);
                if (near != null && near >= old.get(u)[4] - 4) old.get(u)[4] = near;
                rowsAt.merge(old.get(u)[4], 1, Integer::sum);
            }
        }
        boolean shared = false;
        for (int rows : rowsAt.values()) {
            if (rows >= 2) shared = true;
        }
        if (!shared) return null;
        int edge = -1;
        for (List<int[]> old : was.values()) {
            if (edge < 0) edge = old.getFirst()[4];
            if (Math.abs(old.getFirst()[4] - edge) > 2) return null;
        }

        java.util.TreeMap<Integer, Integer> shifts = new java.util.TreeMap<>();
        int carried = 0;
        for (int anchor : rowsAt.keySet()) {
            int gap = Integer.MAX_VALUE;
            for (List<int[]> old : was.values()) {
                for (int u = 1; u < old.size(); u++) {
                    if (old.get(u)[4] == anchor) gap = Math.min(gap, old.get(u)[2] - old.get(u - 1)[3]);
                }
            }
            int shift = carried;
            for (int row : was.keySet()) {
                List<int[]> old = was.get(row);
                List<int[]> fresh = now.get(row);
                for (int u = 1; u < old.size(); u++) {
                    if (old.get(u)[4] != anchor) continue;
                    int moved = u == 1 ? 0 : shifts.getOrDefault(old.get(u - 1)[4], 0);
                    int end = old.get(u - 1)[2] + moved + fresh.get(u - 1)[3] - fresh.get(u - 1)[2];
                    shift = Math.max(shift, end + gap - old.get(u)[2]);
                }
            }
            shifts.put(anchor, shift);
            carried = shift;
        }

        if (carried == 0) return null;
        java.util.Map<Integer, List<Glyph>> lines = new java.util.HashMap<>();
        for (int row : was.keySet()) {
            List<int[]> old = was.get(row);
            List<int[]> fresh = now.get(row);
            List<Glyph> glyphs = translated.get(row);
            List<Glyph> line = new ArrayList<>();
            int cursor = 0;
            for (int u = 0; u < old.size(); u++) {
                int start = old.get(u)[2] + (u == 0 ? 0 : shifts.getOrDefault(old.get(u)[4], 0));
                if (start > cursor) {
                    line.add(new Glyph(SPACE_GLYPH_BASE + start - cursor, Style.EMPTY.withFont(SPACE_FONT), start - cursor));
                    cursor = start;
                }
                line.addAll(glyphs.subList(fresh.get(u)[0], fresh.get(u)[1]));
                cursor += fresh.get(u)[3] - fresh.get(u)[2];
            }
            line.addAll(glyphs.subList(fresh.getLast()[1], glyphs.size()));
            lines.put(row, line);
        }
        return lines;
    }

    private static int[] columnAxes(List<List<Glyph>> before, List<Text> output, int first, int last, int width) {
        List<Integer> left = new ArrayList<>();
        List<Integer> right = new ArrayList<>();
        java.util.HashSet<Integer> edges = new java.util.HashSet<>();
        for (int i = first; i <= last && i < output.size(); i++) {
            List<int[]> cells = cells(before.get(i));
            if (cells.size() < 2) continue;
            if (cells(glyphs(output.get(i))).size() != cells.size()) return null;
            left.add(cells.getFirst()[2] + cells.getFirst()[3]);
            right.add(cells.getLast()[2] + cells.getLast()[3]);
            edges.add(cells.getFirst()[2]);
        }
        if (left.size() < 2 || edges.size() == 1) return null;
        List<Integer> sortedLeft = new ArrayList<>(left);
        List<Integer> sortedRight = new ArrayList<>(right);
        java.util.Collections.sort(sortedLeft);
        java.util.Collections.sort(sortedRight);
        int leftAxis = sortedLeft.get(sortedLeft.size() / 2);
        int rightAxis = sortedRight.get(sortedRight.size() / 2);
        for (int i = 0; i < left.size(); i++) {
            if (Math.abs(left.get(i) - leftAxis) > 10 || Math.abs(right.get(i) - rightAxis) > 10) return null;
        }
        if (Math.abs(leftAxis + rightAxis - 2 * width) > 12) return null;
        return new int[]{leftAxis, rightAxis};
    }

    private static List<Glyph> placeCells(List<int[]> was, List<Glyph> translated, int[] axes) {
        List<int[]> now = cells(translated);
        List<Glyph> result = new ArrayList<>();
        int cursor = 0;
        for (int c = 0; c < now.size(); c++) {
            int width = now.get(c)[3] - now.get(c)[2];
            int middle = was.get(c)[2] + was.get(c)[3];
            if (c == 0) middle = axes[0];
            if (c == now.size() - 1) middle = axes[1];
            int start = Math.max((middle - width) / 2, c == 0 ? 0 : cursor + 2);
            if (start > cursor) {
                result.add(new Glyph(SPACE_GLYPH_BASE + start - cursor, Style.EMPTY.withFont(SPACE_FONT), start - cursor));
            }
            result.addAll(translated.subList(now.get(c)[0], now.get(c)[1]));
            cursor = start + width;
        }
        return result;
    }

    private static boolean onAxis(List<Glyph> line, List<Integer> axes) {
        for (int axis : axes) {
            if (Math.abs(indent(line) + visibleEnd(line) - axis) <= 2) return true;
        }
        return false;
    }

    private static List<Integer> sharedAxes(List<List<Glyph>> lines, int from, int to) {
        List<Integer> axes = new ArrayList<>();
        for (int i = from; i < to; i++) {
            if (!hasLead(lines.get(i)) || indent(lines.get(i)) < 3) continue;
            int candidate = indent(lines.get(i)) + visibleEnd(lines.get(i));
            int length = visibleEnd(lines.get(i)) - indent(lines.get(i));
            int same = 0;
            boolean otherLength = false;
            for (int k = from; k < to; k++) {
                List<Glyph> other = lines.get(k);
                if (!hasLead(other) || indent(other) < 3) continue;
                if (Math.abs(indent(other) + visibleEnd(other) - candidate) > 2) continue;
                same++;
                if (Math.abs(visibleEnd(other) - indent(other) - length) > 4) otherLength = true;
            }
            if (otherLength && same >= 2 && !axes.contains(candidate)) axes.add(candidate);
        }
        return axes;
    }

    private static boolean sharesLeftEdge(List<Glyph> line, List<Glyph> neighbour) {
        return hasLead(neighbour) && indent(neighbour) == indent(line)
                && Math.abs(visibleEnd(neighbour) - visibleEnd(line)) > 4;
    }

    private static int leadEnd(List<Glyph> glyphs) {
        int end = 0;
        int spaces = 0;
        while (end < glyphs.size()) {
            Glyph glyph = glyphs.get(end);
            boolean space = isMinecraftSpaceStyle(glyph.style()) || isAlignmentCodePoint(glyph.codePoint());
            if (!space && glyph.advance() != 0) break;
            if (space) spaces++;
            end++;
        }
        if (spaces == 0) return 0;
        return end;
    }

    private static boolean hasLead(List<Glyph> glyphs) {
        int lead = leadEnd(glyphs);
        return lead > 0 && lead < glyphs.size() && indent(glyphs) >= 0;
    }

    private static int indent(List<Glyph> glyphs) {
        return advance(glyphs, 0, leadEnd(glyphs));
    }

    private static int visibleEnd(List<Glyph> glyphs) {
        int x = 0;
        int end = 0;
        for (Glyph glyph : glyphs) {
            x += glyph.advance();
            if (isVisible(glyph)) end = x;
        }
        return end;
    }

    private static boolean isCentered(List<Glyph> glyphs, int width) {
        int left = indent(glyphs);
        int right = width - visibleEnd(glyphs);
        return Math.abs(left - right) <= 2;
    }

    private static boolean isPaddedCenter(List<Glyph> glyphs, int width) {
        if (!hasLead(glyphs)) return false;
        Glyph last = glyphs.getLast();
        boolean tail = isMinecraftSpaceStyle(last.style()) || isAlignmentCodePoint(last.codePoint());
        return tail && Math.abs(indent(glyphs) - (width - advance(glyphs, 0, glyphs.size()))) <= 1;
    }

    private static boolean isExactlyCentered(List<Glyph> glyphs, int width) {
        int[] extent = extent(glyphs);
        return extent[1] > extent[0] && extent[0] >= 3 && Math.abs(extent[0] - (width - extent[1])) <= 1;
    }

    private static int[] extent(List<Glyph> glyphs) {
        int x = 0;
        int left = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        for (Glyph glyph : glyphs) {
            if (isVisible(glyph)) {
                left = Math.min(left, x);
                right = Math.max(right, x + glyph.advance());
            }
            x += glyph.advance();
        }
        return left == Integer.MAX_VALUE ? new int[]{0, 0} : new int[]{left, right};
    }

    private static int columnGap(List<Glyph> glyphs) {
        int[] jump = lastJump(glyphs);
        return jump == null || jump[0] == 0 ? Integer.MAX_VALUE : advance(glyphs, jump[0], jump[1]);
    }

    private static int[] columnJump(List<Glyph> glyphs) {
        int[] jump = lastJump(glyphs);
        if (jump == null) return null;
        for (int i = 0; i < jump[0]; i++) {
            if (isVisible(glyphs.get(i)) && glyphs.get(i).advance() > 0) return jump;
        }
        return null;
    }

    private static int[] lastJump(List<Glyph> glyphs) {
        List<int[]> clusters = clusters(glyphs);
        for (int i = clusters.size() - 1; i >= 0; i--) {
            int[] cluster = clusters.get(i);
            if (cluster[1] < glyphs.size() && isJump(glyphs, cluster)) return cluster;
        }
        return null;
    }

    private static boolean isJump(List<Glyph> glyphs, int[] cluster) {
        boolean back = false;
        for (Glyph glyph : glyphs.subList(cluster[0], cluster[1])) {
            if (isMinecraftSpaceStyle(glyph.style()) && glyph.advance() > 0) return true;
            if (isAlignmentCodePoint(glyph.codePoint()) && isWynncraftLanguage(glyph.style())) {
                if (glyph.advance() < 0) back = true;
                else if (glyph.advance() > 0 && back) return true;
            }
        }
        return false;
    }

    private static List<int[]> clusters(List<Glyph> glyphs) {
        List<int[]> clusters = new ArrayList<>();
        for (int i = 0; i < glyphs.size(); ) {
            if (isVisible(glyphs.get(i))) {
                i++;
                continue;
            }
            int start = i;
            while (i < glyphs.size() && !isVisible(glyphs.get(i))) i++;
            clusters.add(new int[]{start, i});
        }
        return clusters;
    }

    private static List<Glyph> glyphs(Text line) {
        List<Glyph> glyphs = new ArrayList<>();
        line.visit((style, value) -> {
            boolean code = false;
            for (int codePoint : value.codePoints().toArray()) {
                if (code || codePoint == '§') {
                    glyphs.add(new Glyph(codePoint, style, 0));
                    code = !code;
                    continue;
                }
                glyphs.add(new Glyph(codePoint, style,
                        TextEmojiUtils.width.applyAsInt(Text.literal(new String(Character.toChars(codePoint))).setStyle(style))));
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return glyphs;
    }

    private static Text toText(List<Glyph> glyphs) {
        MutableText line = Text.empty();
        StringBuilder run = new StringBuilder();
        Style style = null;
        for (Glyph glyph : glyphs) {
            if (style != null && !glyph.style().equals(style)) {
                line.append(Text.literal(run.toString()).setStyle(style));
                run.setLength(0);
            }
            style = glyph.style();
            run.appendCodePoint(glyph.codePoint());
        }
        if (style != null) line.append(Text.literal(run.toString()).setStyle(style));
        return line;
    }

    private static int advance(List<Glyph> glyphs, int from, int to) {
        int sum = 0;
        for (Glyph glyph : glyphs.subList(from, to)) sum += glyph.advance();
        return sum;
    }

    private static boolean isVisible(Glyph glyph) {
        int codePoint = glyph.codePoint();
        return glyph.advance() != 0 && !isMinecraftSpaceStyle(glyph.style()) && !Character.isWhitespace(codePoint)
                && !isAlignmentCodePoint(codePoint);
    }

    private static boolean isWynncraftLanguage(Style style) {
        StyleSpriteSource font = style.getFont();
        return font != null && font.toString().contains("language/wynncraft");
    }

    private static List<Text> translateVanillaPixelTooltip(List<Text> tooltip) {
        return widenColumns(tooltip, translatePixelTooltip(tooltip, false));
    }

    static List<Text> translatePixelTooltip(List<Text> tooltip,
                                            boolean preserveStatDecorations) {
        List<String> keys = new ArrayList<>();
        for (Text line : tooltip) keys.add(TextEmojiUtils.extract(line).key);
        GuiScope scope = keys.isEmpty() ? null
                : TranslationManager.findScopeByTitle(keys.getFirst(), keys.subList(1, keys.size()), currentScreenKey(), "");
        List<Text> translatedLines = null;
        for (int i = 0; i < tooltip.size(); i++) {
            Text line = tooltip.get(i);
            var extracted = TextEmojiUtils.extractTooltip(line);
            Style pixelStyle = TextEmojiUtils.findWynncraftPixelStyle(line);
            Style style = pixelStyle;
            if (style == null) {
                style = extracted.contentStyle != null && extracted.contentStyle != Style.EMPTY
                        ? extracted.contentStyle
                        : Style.EMPTY;
            }

            String translated = scope == null
                    ? TranslationManager.findGuiTranslation(extracted.key)
                    : TranslationManager.getGuiTranslation(extracted.key, scope);
            var label = TranslationManager.findGuiLabelTranslation(extracted.key, translated);
            if (label != null && (preserveStatDecorations || pixelStyle != null)
                    && hasUniformLabelStyle(line, label.source())
                    && (label.unitSource() == null || hasUniformLabelStyle(line, label.unitSource()))) {
                if (translatedLines == null)
                    translatedLines = new ArrayList<>(tooltip);
                Text labelled = TextEmojiUtils.replaceFirstPixelLabel(line, label.source(), label.translation());
                if (label.unitSource() != null)
                    labelled = TextEmojiUtils.replaceFirstPixelLabel(labelled, label.unitSource(), label.unitTranslation(),
                            label.unitSource().equals("to") || endsAtAlignment(labelled.getString(), label.unitSource()));
                java.util.regex.Matcher perSecond = PER_SECOND.matcher(labelled.getString());
                List<String> units = new ArrayList<>();
                while (perSecond.find()) units.add(perSecond.group());
                for (String unit : units)
                    labelled = TextEmojiUtils.replaceFirstPixelLabel(labelled, unit, unit.replace('s', 'с'), true);
                if ("to".equals(label.unitSource())) {
                    while (labelled.getString().contains("tier")) {
                        Text next = TextEmojiUtils.replaceFirstPixelLabel(labelled, "tier", "ур.", false);
                        if (next == labelled) break;
                        labelled = next;
                    }
                }
                translatedLines.set(i, labelled);
                continue;
            }

            if (translated == null || translated.equals(extracted.key)) {
                Text columns = translateAlignedColumns(line, extracted.key);
                if (columns != null) {
                    if (translatedLines == null)
                        translatedLines = new ArrayList<>(tooltip);
                    translatedLines.set(i, columns);
                }
                continue;
            }

            if (translated.equals(extracted.key)
                    || TranslationLoader.hasCyrillic(extracted.key)
                    || preserveStatDecorations && isPixelStatLine(extracted.key)
                    && !alignment(translated).equals(alignment(extracted.key))) {
                continue;
            }

            if (translated.startsWith(CENTER_MARKER)) {
                translated = translated.substring(CENTER_MARKER.length());
                if (extracted.key.startsWith("<em>") && !translated.contains("<em>")) translated = "<em>" + translated;
            }
            if (translatedLines == null)
                translatedLines = new ArrayList<>(tooltip);
            translated = translated.replace("§*", TextEmojiUtils.accentCode(line));
            translatedLines.set(i, TooltipNumberColors.preserve(line,
                    TextEmojiUtils.rebuild(translated, extracted.icons, style, extracted.key)));
        }
        return translatedLines == null ? tooltip : translatedLines;
    }

    static boolean hasUniformLabelStyle(Text line, String label) {
        StringBuilder visible = new StringBuilder();
        List<Style> styles = new ArrayList<>();
        line.visit((style, value) -> {
            Style active = style;
            Style reset = style.withBold(false).withItalic(false).withUnderline(false)
                    .withStrikethrough(false).withObfuscated(false);
            for (int i = 0; i < value.length(); i++) {
                if (value.charAt(i) == '§' && i + 1 < value.length()) {
                    char code = value.charAt(i + 1);
                    if (code == '#' && i + 7 < value.length()) {
                        try {
                            active = reset.withColor(Integer.parseInt(value.substring(i + 2, i + 8), 16));
                            i += 7;
                            continue;
                        } catch (NumberFormatException ignored) {}
                    }
                    Formatting format = Formatting.byCode(code);
                    if (format != null) {
                        active = format == Formatting.RESET ? reset
                                : format.isColor() ? reset.withColor(format) : active.withFormatting(format);
                        i++;
                        continue;
                    }
                }
                visible.append(value.charAt(i));
                styles.add(active);
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        int start = visible.indexOf(label);
        if (start < 0 || label.isEmpty()) return false;
        Style first = styles.get(start);
        int color = first.getColor() == null ? 0xFFFFFF : first.getColor().getRgb();
        for (int i = start + 1; i < start + label.length(); i++) {
            Style style = styles.get(i);
            int rgb = style.getColor() == null ? 0xFFFFFF : style.getColor().getRgb();
            if (color != rgb || first.isBold() != style.isBold() || first.isItalic() != style.isItalic()
                    || first.isUnderlined() != style.isUnderlined()
                    || first.isStrikethrough() != style.isStrikethrough()
                    || first.isObfuscated() != style.isObfuscated()) return false;
        }
        return true;
    }

    private static Text translateAlignedColumns(Text line, String key) {
        if (key == null) return null;
        List<String> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        for (String part : key.split("<em>", -1)) {
            for (int offset = 0; offset < part.length(); ) {
                int codePoint = part.codePointAt(offset);
                if (isAlignmentCodePoint(codePoint)) {
                    addPiece(pieces, piece);
                } else {
                    piece.appendCodePoint(codePoint);
                }
                offset += Character.charCount(codePoint);
            }
            addPiece(pieces, piece);
        }

        pieces.removeIf(text -> !text.codePoints().anyMatch(Character::isLetter));
        if (pieces.isEmpty()) return null;
        Text result = line;
        for (int i = 0; i < pieces.size(); i++) {
            String ru = TranslationManager.guiTranslations.get(pieces.get(i));
            if (ru == null) continue;
            boolean rightColumn = i > 0 && i == pieces.size() - 1;
            String label = COLOR_CODES.matcher(pieces.get(i)).replaceAll("");
            String word = COLOR_CODES.matcher(ru).replaceAll("");
            result = TextEmojiUtils.replaceFirstPixelLabel(result, label, word, rightColumn);
        }
        return result == line ? null : result;
    }

    private static void addPiece(List<String> pieces, StringBuilder piece) {
        String value = piece.toString().trim();
        if (!value.isEmpty()) pieces.add(value);
        piece.setLength(0);
    }

    private static boolean isAlignmentCodePoint(int codePoint) {
        return codePoint >= 0xC0000 && codePoint <= 0xDFFFF;
    }

    private static boolean endsAtAlignment(String line, String unit) {
        int start = line.indexOf(unit);
        if (start < 0) return false;
        int next = start + unit.length();
        while (next < line.length() && Character.isWhitespace(line.charAt(next))) next++;
        return next < line.length() && isAlignmentCodePoint(line.codePointAt(next));
    }

    private static String alignment(String value) {
        StringBuilder marks = new StringBuilder();
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (isAlignmentCodePoint(codePoint)) marks.appendCodePoint(codePoint);
            offset += Character.charCount(codePoint);
        }
        return marks.toString();
    }

    private static boolean isPixelStatLine(String value) {
        if (value == null) return false;
        boolean aligned = false;
        boolean afterLetter = false;
        String text = value.replace("<em>", "");
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (Character.isLetter(codePoint)) afterLetter = true;
            if (afterLetter && isAlignmentCodePoint(codePoint)) aligned = true;
            offset += Character.charCount(codePoint);
        }
        return aligned && (value.contains("<num>")
                || value.codePoints().anyMatch(Character::isDigit));
    }

    private static String currentScreenKey() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.currentScreen == null || client.currentScreen.getTitle() == null) return "";
        return TextEmojiUtils.extract(client.currentScreen.getTitle()).key;
    }

    public static void refreshOpenScreen() {
        GuiTranslationCache.clear();
    }
}
