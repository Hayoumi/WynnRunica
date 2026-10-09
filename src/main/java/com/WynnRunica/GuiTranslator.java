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
    private static final int SPACE_GLYPH_BASE = 0xD0000;
    private static final StyleSpriteSource.Font SPACE_FONT =
            new StyleSpriteSource.Font(Identifier.of("minecraft", "space"));

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
                    // §* = цвет названия способности в оригинале: сервер красит его по виду узла.
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

    private enum Align { NONE, COLUMN, CENTER, PARAGRAPH, RIGHT, CELLS }

    private static String widenedKey;
    private static List<Text> widenedValue;

    // Отступ в пару пикселей есть и у обычных строк подсказки. Центрированной или прижатой вправо
    // считается только строка с заметным отступом.
    private static final int MIN_ALIGN_INDENT = 8;

    // Раскладывает переведённую подсказку заново. Сначала по оригиналу определяется, как стояла
    // каждая строка: таблица «название ... значение», по центру подсказки, абзац по центру,
    // прижата вправо или обычная. Потом считается одна общая ширина новой подсказки, и каждая
    // строка ставится в ней так же, как стояла в оригинале.
    public static List<Text> widenColumns(List<Text> input, List<Text> output) {
        if (input == output) return output;
        // В ключ входит и оформление: выбранный пункт списка отличается от невыбранного только цветом.
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

        // У пиксельной подсказки предмета шапка стоит рядом с иконкой и не выравнивается: это строки
        // с отрицательным отступом (иконка, название) и следующие за ними строки с одним и тем же
        // отступом (плашки редкости и типа). Шапка кончается на первой строке с другим отступом:
        // у руны, например, сразу под плашкой идёт разделитель, и он уже обычная строка.
        int bodyStart = 0;
        // Над шапкой бывают чужие строки без отступа: Wynntils пишет над предметом из письма
        // «From <игрок>». Шапка начинается с первой строки с отступом, если он отрицательный.
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

        // Сколько строк держат ширину подсказки. Если такая строка одна, то она сама и есть ширина:
        // её конец совпадает с правым краем всегда, и выравниванием вправо это не считается.
        int widest = 0;
        for (List<Glyph> glyphs : before) {
            if (advance(glyphs, 0, glyphs.size()) == width) widest++;
        }

        // Общая ось: ряд иконок, значения под ним и разделитель стоят на одной середине, даже когда
        // отступ у них маленький. Строки с такой общей серединой считаются центрированными.
        int axis = sharedAxis(before, bodyStart, count);

        Align[] align = new Align[count];
        int[] paragraph = new int[count];
        java.util.Map<Integer, List<Glyph>> cellLines = new java.util.HashMap<>();
        java.util.HashSet<Integer> wideParagraphs = new java.util.HashSet<>();
        for (int i = 0; i < count; i++) {
            align[i] = Align.NONE;
            paragraph[i] = -1;
        }
        // Таблица статов: строки без отступа, где значение отнесено вправо, и все значения
        // кончаются на одной вертикали. Эта вертикаль не обязана совпадать с краем подсказки:
        // подсказку может делать шире другая строка.
        int columnsRight = 0;
        // Самый большой разрыв между названием и значением. Пара пикселей после иконки («- [замок]
        // Unidentified Boots») таблицей не считается: в таблице хотя бы у одной строки разрыв заметный.
        int columnsGap = 0;
        for (int i = bodyStart; i < count; i++) {
            List<Glyph> glyphs = before.get(i);
            if (hasLead(glyphs) || lastJump(glyphs) == null) continue;
            columnsRight = Math.max(columnsRight, visibleEnd(glyphs));
            columnsGap = Math.max(columnsGap, advance(glyphs, lastJump(glyphs)[0], lastJump(glyphs)[1]));
        }
        for (int i = bodyStart; i < count; ) {
            List<Glyph> glyphs = before.get(i);
            if (!hasLead(glyphs)) {
                if (lastJump(glyphs) != null && visibleEnd(glyphs) == columnsRight && columnsGap >= COLUMN_GAP) {
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
            int[] axes = columnAxes(before, output, i, last, width);
            boolean block = last > i && deepest >= MIN_ALIGN_INDENT;
            for (int k = i; k <= last && block; k++) {
                if (!isCentered(before.get(k), blockWidth)) block = false;
            }
            for (int k = i; k <= last; k++) {
                List<Glyph> line = before.get(k);
                // Строка из нескольких ячеек, стоящих по центру над ячейками соседней строки
                // («Сейчас / Станет» над числами): ячейки перевода встают на те же оси.
                if (axes != null && cells(line).size() >= 2) {
                    align[k] = Align.CELLS;
                    cellLines.put(k, placeCells(cells(line), glyphs(output.get(k)), axes));
                    continue;
                }
                // Соседняя строка с тем же отступом, но другой длины: это список с общим левым краем.
                // Одна из его строк может случайно оказаться посередине подсказки, двигать её нельзя.
                boolean listed = k > i && sharesLeftEdge(line, before.get(k - 1))
                        || k < last && sharesLeftEdge(line, before.get(k + 1));
                boolean holdsWidth = widest == 1 && advance(line, 0, line.size()) == width;
                if (block) {
                    align[k] = Align.PARAGRAPH;
                    paragraph[k] = i;
                    // Абзац во всю ширину подсказки стоял по её центру, а не по центру самого себя.
                    if (blockWidth >= width - 2) wideParagraphs.add(i);
                } else if (listed) {
                    continue;
                } else if ((indent(line) >= MIN_ALIGN_INDENT || k == 0 && indent(line) >= 3) && isCentered(line, width)
                        || isExactlyCentered(line, width)
                        || isPaddedCenter(line, width)
                        || axis >= 0 && Math.abs(indent(line) + visibleEnd(line) - axis) <= 2) {
                    // Заголовок по центру бывает почти во всю ширину, поэтому отступ у него может быть маленьким.
                    align[k] = Align.CENTER;
                } else if (indent(line) >= MIN_ALIGN_INDENT && visibleEnd(line) == width && !holdsWidth) {
                    align[k] = Align.RIGHT;
                }
            }
            i = last + 1;
        }

        // Значения под рядом иконок («0 0 0 0 125» под STR DEX INT DEF AGI) стоят под своими иконками,
        // но сами не по центру подсказки. Такой ряд сдвигается вместе с рядом над ним. Узнаётся он по
        // распорке в хвосте и по тем же краям, что у ряда выше (между ними бывает пустая строка).
        for (int k = bodyStart + 1; k < count; k++) {
            List<Glyph> line = before.get(k);
            if (align[k] != Align.NONE || !hasLead(line)) continue;
            Glyph tail = line.getLast();
            if (!isMinecraftSpaceStyle(tail.style()) && !isAlignmentCodePoint(tail.codePoint())) continue;
            int above = k - 1;
            while (above > bodyStart && advance(before.get(above), 0, before.get(above).size()) == 0) above--;
            List<Glyph> upper = before.get(above);
            if (align[above] == Align.CENTER && Math.abs(indent(line) - indent(upper)) <= 6
                    && Math.abs(visibleEnd(line) - visibleEnd(upper)) <= 3) {
                align[k] = Align.CENTER;
            }
        }

        // Строки перевода; у выравниваемых строк старый отступ снимается.
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

        // Таблице нужен зазор между названием и значением не меньше, чем был в оригинале (но хватит 8).
        // Ширина остальных строк новой подсказки.
        int newWidth = 0;
        for (int i = 0; i < after.size(); i++) {
            if (i < count && align[i] == Align.COLUMN) continue;
            if (i < count && align[i] == Align.RIGHT) newWidth = Math.max(newWidth, visibleEnd(after.get(i)));
            else if (i < count && align[i] == Align.CELLS) {
                // Ячейки сдвинутся на половину прироста ширины и обязаны поместиться с обеих сторон.
                newWidth = Math.max(newWidth, Math.max(2 * visibleEnd(after.get(i)) - width, width - 2 * indent(after.get(i))));
            } else newWidth = Math.max(newWidth, advance(after.get(i), 0, after.get(i).size()));
        }

        // Таблице нужен зазор между названием и значением не меньше, чем был в оригинале (но хватит 8).
        // Поле справа от значений остаётся таким же, как в оригинале, если подсказку держит другая строка.
        int normalGap = Integer.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            if (align[i] == Align.COLUMN) normalGap = Math.min(normalGap, columnGap(before.get(i)));
        }
        // Общая правая вертикаль значений: по строке, которой нужно больше всего места
        // (название + зазор + значение), но не левее, чем была в оригинале.
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
            // Ширина подсказки не изменилась, строку не переводили: она остаётся ровно там, где была.
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
                // Строку не переводили: она сдвигается на половину прироста ширины и остаётся относительно
                // середины там же, где её поставил сервер. Мерить её саму нельзя: у ряда иконок справа
                // невидимый хвост, а в строке со страницами значок клавиши стоит сбоку от точек.
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
                // Ячейки стояли вокруг середины подсказки: вместе с ней и сдвигаются.
                shift = indent(glyphs) + (newWidth - width) / 2;
                glyphs = new ArrayList<>(glyphs.subList(leadEnd(glyphs), glyphs.size()));
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

    // Ячейка: кусок текста между распорками. {первая буква, за последней буквой, левый край, правый край}.
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

    // Две колонки по центру: в нескольких строках подряд первые ячейки стоят на одной оси, последние
    // на другой, и оси симметричны относительно середины подсказки («Сейчас / Станет» над числами).
    // Возвращает удвоенные координаты осей или null, если блок не такой.
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
        // Общий левый край у всех строк: это колонки, выровненные влево, их двигать не нужно.
        if (left.size() < 2 || edges.size() == 1) return null;
        List<Integer> sortedLeft = new ArrayList<>(left);
        List<Integer> sortedRight = new ArrayList<>(right);
        java.util.Collections.sort(sortedLeft);
        java.util.Collections.sort(sortedRight);
        int leftAxis = sortedLeft.get(sortedLeft.size() / 2);
        int rightAxis = sortedRight.get(sortedRight.size() / 2);
        for (int i = 0; i < left.size(); i++) {
            // Сервер сам ставит ячейки с погрешностью в несколько пикселей.
            if (Math.abs(left.get(i) - leftAxis) > 10 || Math.abs(right.get(i) - rightAxis) > 10) return null;
        }
        if (Math.abs(leftAxis + rightAxis - 2 * width) > 12) return null;
        return new int[]{leftAxis, rightAxis};
    }

    // Ставит ячейки перевода на оси колонок: первую на левую, последнюю на правую.
    // Ячейки между ними (стрелки) остаются там, где была их середина.
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

    // Сумма левого и правого края, общая для двух и более строк с отступом и разной длиной.
    // Одинаковая середина у строк разной длины случайной не бывает. Возвращает -1, если такой нет.
    private static int sharedAxis(List<List<Glyph>> lines, int from, int to) {
        int best = -1;
        int bestCount = 1;
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
            if (otherLength && same > bestCount) {
                best = candidate;
                bestCount = same;
            }
        }
        return best;
    }

    private static boolean sharesLeftEdge(List<Glyph> line, List<Glyph> neighbour) {
        // У строк по центру одинаковый отступ бывает только при почти одинаковой длине.
        return hasLead(neighbour) && indent(neighbour) == indent(line)
                && Math.abs(visibleEnd(neighbour) - visibleEnd(line)) > 4;
    }

    private static int leadEnd(List<Glyph> glyphs) {
        int end = 0;
        while (end < glyphs.size() && (isMinecraftSpaceStyle(glyphs.get(end).style())
                || isAlignmentCodePoint(glyphs.get(end).codePoint()))) end++;
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

    // По центру стоит строка, у которой поля слева и справа от видимого текста равны.
    private static boolean isCentered(List<Glyph> glyphs, int width) {
        int left = indent(glyphs);
        int right = width - visibleEnd(glyphs);
        return Math.abs(left - right) <= 2;
    }

    // Ряд иконок или строка во всю ширину стоят по центру с отступом в несколько пикселей.
    // Такой строке верим, только если поля слева и справа равны с точностью до пикселя. Края считаются
    // по самым левым и самым правым видимым знакам: в строке бывают сдвиги назад.
    // Сервер ставит ряд иконок по центру распорками с двух сторон: отступ слева и такой же хвост
    // справа. Хвост в конце строки и равные поля надёжнее любого порога на размер отступа.
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

    // Левый и правый край видимых знаков строки.
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
            // Коды цвета вида §7 внутри текста места на экране не занимают.
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
        return !isMinecraftSpaceStyle(glyph.style()) && !Character.isWhitespace(codePoint)
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
                    labelled = TextEmojiUtils.replaceFirstPixelLabel(labelled, label.unitSource(), label.unitTranslation());
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
                    || preserveStatDecorations && isPixelStatLine(extracted.key)) {
                continue;
            }

            // Метка «по центру» здесь не нужна: строку держит по центру её же распорка в начале,
            // а ширину распорки под новый текст подгоняет widenColumns.
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

        // Куски без букв (одиночный код цвета перед значком) колонками не считаются.
        pieces.removeIf(text -> !text.codePoints().anyMatch(Character::isLetter));
        if (pieces.isEmpty()) return null;
        Text result = line;
        for (int i = 0; i < pieces.size(); i++) {
            String ru = TranslationManager.guiTranslations.get(pieces.get(i));
            if (ru == null) continue;
            boolean rightColumn = i > 0 && i == pieces.size() - 1;
            // Здесь меняется только слово внутри готовой строки, цвет остаётся её собственный.
            // Коды цвета из перевода сюда нельзя: они показались бы текстом («F53291Ящики»).
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

    private static boolean isPixelStatLine(String value) {
        if (value == null) return false;
        boolean aligned = value.codePoints()
                .anyMatch(codePoint -> codePoint >= 0xC0000 && codePoint <= 0xDFFFF);
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
