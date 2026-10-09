package com.WynnRunica;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class TextEmojiUtils {

    private static final StyleSpriteSource.Font WYNNCRAFT_CYRILLIC_FONT =
            new StyleSpriteSource.Font(Identifier.of("wynnrunica", "language/wynncraft_cyrillic"));
    private static final StyleSpriteSource.Font SPACE_FONT =
            new StyleSpriteSource.Font(Identifier.of("minecraft", "space"));

    // Ширина текста в пикселях игры. В игре её считает шрифт Minecraft, а проверка вёрстки
    // без игры (TooltipLayoutCheck) подставляет сюда таблицу ширин из ресурспака.
    static java.util.function.ToIntFunction<Text> width =
            text -> MinecraftClient.getInstance().textRenderer.getWidth(text);

    public static class Extracted {
        public final String key;
        public final List<Text> icons;
        public final Style contentStyle;
        public final List<Style> styles;
        Extracted(String key, List<Text> icons, Style contentStyle, List<Style> styles) {
            this.key = key;
            this.icons = icons;
            this.contentStyle = contentStyle;
            this.styles = styles;
        }
    }

    public static Extracted extract(Text source) {
        StringBuilder sb = new StringBuilder();
        List<Text> icons = new ArrayList<>();
        Style[] contentStyle = new Style[]{Style.EMPTY};
        List<Style> styles = new ArrayList<>();
        walk(source, Style.EMPTY, sb, icons, contentStyle, styles);
        return new Extracted(sb.toString(), icons, contentStyle[0], styles);
    }

    public static Extracted extractTooltip(Text source) {
        Extracted extracted = extract(source);
        Style contentStyle = source.visit((style, value) -> isIconFont(style)
                ? java.util.Optional.<Style>empty()
                : java.util.Optional.ofNullable(firstLetterStyle(value, style)), Style.EMPTY)
                .orElse(extracted.contentStyle);
        return new Extracted(extracted.key, extracted.icons, contentStyle, extracted.styles);
    }

    public static Style findWynncraftPixelStyle(Text source) {
        Style content = findWynncraftPixelStyle(source, Style.EMPTY, true);
        return content != null ? content : findWynncraftPixelStyle(source, Style.EMPTY, false);
    }

    private static Style findWynncraftPixelStyle(Text node, Style parentStyle, boolean lettersOnly) {
        Style merged = node.getStyle().withParent(parentStyle);
        if (isWynncraftPixelFont(merged)) {
            Style content = node.getContent().visit(text ->
                    java.util.Optional.ofNullable(firstLetterStyle(text, merged))).orElse(null);
            if (content != null) return content;
            if (!lettersOnly) return merged;
        }

        for (Text child : node.getSiblings()) {
            Style found = findWynncraftPixelStyle(child, merged, lettersOnly);
            if (found != null)
                return found;
        }
        return null;
    }

    private static Style firstLetterStyle(String value, Style base) {
        Style current = base;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '§' && i + 1 < value.length()) {
                char code = value.charAt(++i);
                if (code == '#' && i + 6 < value.length()) {
                    try {
                        current = current.withColor(Integer.parseInt(value.substring(i + 1, i + 7), 16));
                        i += 6;
                    } catch (NumberFormatException ignored) {}
                } else {
                    Formatting format = Formatting.byCode(code);
                    if (format == Formatting.RESET) current = base;
                    else if (format != null && format.getColorValue() != null)
                        current = current.withColor(format.getColorValue());
                    else if (format == Formatting.BOLD) current = current.withBold(true);
                    else if (format == Formatting.ITALIC) current = current.withItalic(true);
                    else if (format == Formatting.UNDERLINE) current = current.withUnderline(true);
                    else if (format == Formatting.STRIKETHROUGH) current = current.withStrikethrough(true);
                    else if (format == Formatting.OBFUSCATED) current = current.withObfuscated(true);
                }
            } else if (Character.isLetter(value.codePointAt(i))) {
                return current;
            }
        }
        return null;
    }

    public static boolean isIconFont(Style style) {
        StyleSpriteSource font = style.getFont();
        String fontStr = font == null ? "" : font.toString();
        return fontStr.contains("hud/dialogue/text/common/")
                || fontStr.contains("minecraft:common")
                || fontStr.contains("minecraft:keybind")
                || fontStr.contains("minecraft:interface")
                || fontStr.contains("minecraft:tooltip")
                || fontStr.contains("minecraft:space");
    }

    private static void walk(Text node, Style parentStyle, StringBuilder out,
                              List<Text> icons, Style[] firstContentStyle, List<Style> styles) {
        Style merged = node.getStyle().withParent(parentStyle);
        StyleSpriteSource font = merged.getFont();
        String fontStr = font == null ? "" : font.toString();

        if (isIconFont(merged)) {
            out.append("<em>");
            MutableText iconCopy = node.copyContentOnly();
            iconCopy.setStyle(merged);
            icons.add(iconCopy);
        } else {
        final Style finalMerged = merged;
        node.getContent().visit(s -> {
            StringBuilder currentText = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);

                boolean isSurrogatePua = false;
                if (Character.isHighSurrogate(c) && i + 1 < s.length()) {
                    char low = s.charAt(i + 1);
                    int codePoint = Character.toCodePoint(c, low);
                    if (codePoint >= 0xF0000 && codePoint <= 0x10FFFD) {
                        isSurrogatePua = true;
                    }
                }

                if ((c >= '\uE000' && c <= '\uF8FF') || isSurrogatePua) {
                    if (currentText.length() > 0) {
                        out.append(currentText.toString());
                        currentText.setLength(0);
                    }
                    out.append("<em>");
                    String iconStr;
                    if (isSurrogatePua) {
                        iconStr = s.substring(i, i + 2);
                        i++;
                    } else {
                        iconStr = String.valueOf(c);
                    }
                    icons.add(Text.literal(iconStr).setStyle(finalMerged));
                } else {
                    currentText.append(c);
                }
            }
            if (currentText.length() > 0) {
                String t = currentText.toString();
                out.append(t);

                if (firstContentStyle[0] == null || firstContentStyle[0] == Style.EMPTY) {
                    String stripped = t.replaceAll("§.", "").trim();

                    if (!stripped.isEmpty()) {
                        if (stripped.codePoints().anyMatch(Character::isLetter)) {
                            firstContentStyle[0] = finalMerged;
                        } else if (!fontStr.contains("chat") && !fontStr.contains("banner")
                                && !fontStr.contains("prefix")) {
                            firstContentStyle[0] = finalMerged;
                        }
                    }
                }
            }
            return java.util.Optional.empty();
        });
        }

        while (styles.size() < out.length()) styles.add(merged);

        for (Text child : node.getSiblings()) {
            walk(child, merged, out, icons, firstContentStyle, styles);
        }
    }

    private static boolean isWynncraftPixelFont(Style style) {
        StyleSpriteSource font = style.getFont();
        String fontStr = font == null ? "" : font.toString();
        return fontStr.contains("minecraft:language/wynncraft")
                || fontStr.contains("minecraft:offset/wynncraft")
                || fontStr.contains("wynnrunica:language/wynncraft_cyrillic");
    }

    public static Text rebuild(String translated, List<Text> icons, Style rootStyle) {
        return rebuild(translated, icons, rootStyle, false, null);
    }

    public static Text rebuild(String translated, List<Text> icons, Style rootStyle,
                               String original) {
        return rebuild(translated, icons, rootStyle, false, original);
    }

    public static Text rebuildDialogue(String translated, List<Text> icons, Style rootStyle) {
        return rebuild(translated, icons, rootStyle, true, null);
    }

    public static Text replaceFirstPixelLabel(Text source, String original, String translated) {
        return replaceFirstPixelLabel(source, original, translated, false);
    }

    public static Text replaceFirstPixelLabel(Text source, String original, String translated,
                                              boolean keepRightEdge) {
        String plain = source.getString();
        int start = plain.indexOf(original);
        if (start < 0)
            return source;

        int next = start + original.length();
        while (next < plain.length() && Character.isWhitespace(plain.charAt(next))) next++;
        boolean aligned = keepRightEdge || next < plain.length()
                && plain.codePointAt(next) >= 0xC0000 && plain.codePointAt(next) <= 0xDFFFF;

        int[] offset = {0};
        boolean[] inserted = {false};
        MutableText copy = copyReplacingLabel(
                source, Style.EMPTY, start, start + original.length(),
                original, translated, keepRightEdge, aligned, offset, inserted);
        return inserted[0] ? copy : source;
    }

    private static MutableText copyReplacingLabel(Text node, Style parent,
                                                  int start, int end,
                                                  String original, String translated,
                                                  boolean keepRightEdge, boolean aligned,
                                                  int[] offset, boolean[] inserted) {
        Style effectiveStyle = node.getStyle().withParent(parent);
        String value = node.getContent() instanceof PlainTextContent plain
                ? plain.string() : null;
        MutableText result;

        if (value == null) {
            result = node.copyContentOnly().setStyle(node.getStyle());
        } else {
            int nodeStart = offset[0];
            int nodeEnd = nodeStart + value.length();
            int overlapStart = Math.max(start, nodeStart);
            int overlapEnd = Math.min(end, nodeEnd);

            if (overlapStart >= overlapEnd) {
                result = Text.literal(value).setStyle(node.getStyle());
            } else {
                int localStart = overlapStart - nodeStart;
                int localEnd = overlapEnd - nodeStart;
                result = Text.literal(value.substring(0, localStart)).setStyle(node.getStyle());
                // Код цвета вида §7 действует только внутри своего куска текста,
                // поэтому вставленному переводу и хвосту строки его надо повторить.
                String legacy = activeLegacyCodes(value.substring(0, localStart));

                if (!inserted[0]) {
                    int widthOrig = width.applyAsInt(Text.literal(original).setStyle(effectiveStyle));
                    MutableText translatedText = Text.literal("");
                    appendStyled(translatedText, legacy + translated, effectiveStyle);
                    int widthDelta = widthOrig - width.applyAsInt(translatedText);
                    Text spacer = widthDelta == 0 || !aligned ? Text.empty()
                            : Text.literal(new String(Character.toChars(0xD0000 + widthDelta)))
                                    .setStyle(Style.EMPTY.withFont(SPACE_FONT));
                    if (keepRightEdge) result.append(spacer);
                    result.append(translatedText);
                    if (!keepRightEdge) result.append(spacer);
                    inserted[0] = true;
                }

                if (localEnd < value.length()) {
                    result.append(Text.literal(legacy + value.substring(localEnd)).setStyle(node.getStyle()));
                }
            }
            offset[0] = nodeEnd;
        }

        for (Text sibling : node.getSiblings()) {
            result.append(copyReplacingLabel(
                    sibling, effectiveStyle, start, end,
                    original, translated, keepRightEdge, aligned, offset, inserted));
        }
        return result;
    }

    // Код «§*» в переводе означает «цвет выделенного слова оригинала». Нужен там, где цвет
    // зависит от предмета (например, от его редкости) и заранее в перевод не пишется.
    static String accentCode(Text original) {
        List<TextColor> colors = new ArrayList<>();
        original.visit((style, value) -> {
            if (!isIconFont(style) && style.getColor() != null && value.codePoints().anyMatch(Character::isLetter)) {
                colors.add(style.getColor());
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        if (colors.isEmpty()) return "";
        TextColor accent = colors.getFirst();
        for (TextColor color : colors) {
            if (!color.equals(colors.getFirst())) {
                accent = color;
                break;
            }
        }
        return String.format("§#%06X", accent.getRgb() & 0xFFFFFF);
    }

    static String activeLegacyCodes(String text) {
        String active = "";
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) != '§') continue;
            char code = Character.toLowerCase(text.charAt(i + 1));
            boolean color = code == 'r' || Character.digit(code, 16) >= 0;
            if (color) active = text.substring(i, i + 2);
            if (!color) active += text.substring(i, i + 2);
            i++;
        }
        return active;
    }

    private static Text rebuild(String translated, List<Text> icons, Style rootStyle,
                                boolean dialogue, String original) {
        return rebuild(translated, icons, rootStyle, dialogue, original, true);
    }

    static Text rebuildChat(String translated, List<Text> icons, Style rootStyle, String original) {
        return rebuild(translated, icons, rootStyle, false, original, false);
    }

    private static Text rebuild(String translated, List<Text> icons, Style rootStyle,
                                boolean dialogue, String original, boolean align) {
        MutableText result = Text.literal("");

        Style baseStyle = rootStyle != null ? rootStyle : Style.EMPTY;
        boolean keepBold = dialogue && baseStyle.isBold();
        Style resetStyle = baseStyle.withBold(keepBold).withUnderline(false).withStrikethrough(false);
        if (resetStyle.getColor() == null) {
            resetStyle = resetStyle.withColor(Formatting.WHITE);
        }
        if (!baseStyle.isItalic()) {
            resetStyle = resetStyle.withItalic(false);
        }
        Style current = baseStyle;
        boolean ownColor = false;
        StringBuilder buf = new StringBuilder();
        int iconIdx = 0;
        boolean[] usedIcons = dialogue ? new boolean[icons.size()] : null;

        String[] originalRuns = align ? alignmentRuns(translated, original, icons) : null;
        MutableText run = Text.literal("");
        int runIdx = 0;

        for (int i = 0; i < translated.length(); i++) {

            if (translated.startsWith("<em>", i)) {
                if (buf.length() > 0) {
                    appendStyled(run, buf.toString(), current);
                    buf.setLength(0);
                }
                flushRun(result, run, originalRuns, runIdx++, baseStyle);
                run = Text.literal("");
                int selected = dialogue ? selectIcon(icons, usedIcons, current.getColor()) : iconIdx++;
                if (selected >= 0 && selected < icons.size()) {
                    Text icon = icons.get(selected);
                    MutableText fixedIcon = icon.copy();
                    fixedIcon.setStyle(icon.getStyle());
                    result.append(fixedIcon);
                }

                i += 3;
                continue;
            }

            char c = translated.charAt(i);
            // Текст в квадратных скобках в диалоге розовый, как в игре. Если переводчик сам задал
            // цвет перед скобкой или внутри неё, остаётся его цвет.
            if (dialogue && c == '[' && !ownColor) {
                int end = translated.indexOf(']', i);
                if (end >= 0 && translated.indexOf('§', i) > end || end >= 0 && translated.indexOf('§', i) < 0) {
                    if (buf.length() > 0) {
                        appendStyled(run, buf.toString(), current);
                        buf.setLength(0);
                    }
                    appendStyled(run, translated.substring(i, end + 1),
                            baseStyle.withColor(Formatting.LIGHT_PURPLE));
                    i = end;
                    continue;
                }
            }
            if (c == '§' && i + 1 < translated.length()) {

                if (buf.length() > 0) {
                    appendStyled(run, buf.toString(), current);
                    buf.setLength(0);
                }

                char code = translated.charAt(i + 1);
                if (code == '#' && i + 7 < translated.length()) {
                    String hex = translated.substring(i + 2, i + 8);
                    try {
                        current = resetStyle.withColor(Integer.parseInt(hex, 16));
                        ownColor = true;
                        i += 7;
                        continue;
                    } catch (NumberFormatException ignored) {}
                }
                net.minecraft.util.Formatting fmt = net.minecraft.util.Formatting.byCode(code);

                if (fmt != null) {
                    if (fmt == net.minecraft.util.Formatting.RESET) {
                        current = resetStyle;
                        ownColor = false;
                    } else if (fmt.isColor()) {
                        current = resetStyle.withColor(fmt);
                        ownColor = true;
                    } else {
                        current = current.withFormatting(fmt);
                    }
                    i++;
                } else {
                    buf.append(c);
                }
            } else {
                buf.append(c);
            }
        }

        if (buf.length() > 0) {
            appendStyled(run, buf.toString(), current);
        }
        flushRun(result, run, originalRuns, runIdx, baseStyle);
        return result;
    }

    private static String[] alignmentRuns(String translated, String original, List<Text> icons) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (original == null || client != null && client.textRenderer == null) return null;
        int spacers = 0;
        for (Text icon : icons) {
            StyleSpriteSource font = icon.getStyle().getFont();
            if (font != null && font.toString().contains("minecraft:space")) spacers++;
        }
        if (spacers < 2) return null;
        String[] originalRuns = plainRuns(original);
        return originalRuns.length == plainRuns(translated).length ? originalRuns : null;
    }

    private static String[] plainRuns(String value) {
        String[] runs = value.split("<em>", -1);
        for (int i = 0; i < runs.length; i++)
            runs[i] = runs[i].replaceAll("§(?:#[0-9a-fA-F]{6}|.)", "");
        return runs;
    }

    private static void flushRun(MutableText result, MutableText run, String[] originalRuns,
                                 int index, Style baseStyle) {
        if (originalRuns == null || index >= originalRuns.length) {
            result.append(run);
            return;
        }
        int delta = width.applyAsInt(Text.literal(originalRuns[index]).setStyle(baseStyle))
                - width.applyAsInt(run);
        if (delta <= 0) {
            result.append(run);
            return;
        }
        int before = delta / 2;
        appendSpacer(result, before);
        result.append(run);
        appendSpacer(result, delta - before);
    }

    private static void appendSpacer(MutableText result, int pixels) {
        if (pixels <= 0) return;
        result.append(Text.literal(new String(Character.toChars(0xD0000 + pixels)))
                .setStyle(Style.EMPTY.withFont(SPACE_FONT)));
    }

    public static List<MutableText> wrap(Text text, int width) {
        List<MutableText> result = new ArrayList<>();
        for (StringVisitable line : MinecraftClient.getInstance().textRenderer
                .wrapLinesWithoutLanguage(text, width)) {
            MutableText copy = Text.literal("");
            line.visit((style, value) -> {
                copy.append(Text.literal(value).setStyle(style));
                return java.util.Optional.empty();
            }, Style.EMPTY);
            result.add(copy);
        }
        return result;
    }

    private static int selectIcon(List<Text> icons, boolean[] used, TextColor color) {
        if (icons.isEmpty()) return -1;
        if (color != null) {
            int rgb = color.getRgb() & 0xFFFFFF;
            for (int i = 0; i < icons.size(); i++) {
                TextColor iconColor = icons.get(i).getStyle().getColor();
                if (!used[i] && iconColor != null
                        && (iconColor.getRgb() & 0xFFFFFF) == rgb) {
                    used[i] = true;
                    return i;
                }
            }
        }
        for (int i = 0; i < icons.size(); i++) {
            if (!used[i]) {
                used[i] = true;
                return i;
            }
        }
        return -1;
    }

    private static void appendStyled(MutableText result, String value, Style style) {
        if (value == null || value.isEmpty()) return;

        StyleSpriteSource font = style.getFont();
        String fontName = font == null ? "" : font.toString();
        boolean isDialogue = fontName.contains("hud/dialogue/text/");
        boolean canUseCyrillicPixel = !isDialogue
                && (fontName.contains("wynncraft")
                || fontName.contains("banner")
                || fontName.contains("tooltip")
                || fontName.contains("offset")
                || fontName.contains("interface")
                || fontName.contains("prefix")
                || fontName.contains("chat")
                || fontName.isEmpty()
                || fontName.equals("minecraft:default"));

        StringBuilder run = new StringBuilder();
        Integer runType = null;

        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            int charCount = Character.charCount(codePoint);

            int type = 0;
            if (codePoint >= 0xC0000 && codePoint <= 0xDFFFF) {
                type = 1;
            } else if (canUseCyrillicPixel && useCyrillicFontFor(value, offset)) {
                type = 2;
            }

            if (runType != null && runType != type) {
                flushStyledRun(result, run.toString(), style, runType);
                run.setLength(0);
            }
            runType = type;
            run.appendCodePoint(codePoint);
            offset += charCount;
        }

        if (run.length() > 0 && runType != null) {
            flushStyledRun(result, run.toString(), style, runType);
        }
    }

    private static void flushStyledRun(MutableText result, String text, Style style, int runType) {
        if (runType == 1) {
            result.append(Text.literal(text).setStyle(Style.EMPTY.withFont(SPACE_FONT)));
        } else if (runType == 2) {
            result.append(Text.literal(text).setStyle(style.withFont(WYNNCRAFT_CYRILLIC_FONT)));
        } else {
            result.append(Text.literal(text).setStyle(style));
        }
    }

    private static boolean isCyrillic(int codePoint) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        return block == Character.UnicodeBlock.CYRILLIC
                || block == Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY
                || block == Character.UnicodeBlock.CYRILLIC_EXTENDED_A
                || block == Character.UnicodeBlock.CYRILLIC_EXTENDED_B;
    }

    private static boolean useCyrillicFontFor(String value, int offset) {
        int codePoint = value.codePointAt(offset);
        if (isCyrillic(codePoint)) return true;
        if (!Character.isWhitespace(codePoint)) return false;

        int before = offset;
        while (before > 0) {
            int previous = value.codePointBefore(before);
            if (!Character.isWhitespace(previous)) {
                if (isCyrillic(previous)) return true;
                break;
            }
            before -= Character.charCount(previous);
        }

        int after = offset + Character.charCount(codePoint);
        while (after < value.length()) {
            int next = value.codePointAt(after);
            if (!Character.isWhitespace(next)) return isCyrillic(next);
            after += Character.charCount(next);
        }
        return false;
    }

}
