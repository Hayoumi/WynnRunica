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

    public static class Extracted {
        public final String key;
        public final List<Text> icons;
        public final Style contentStyle;
        Extracted(String key, List<Text> icons, Style contentStyle) {
            this.key = key;
            this.icons = icons;
            this.contentStyle = contentStyle;
        }
    }

    public static Extracted extract(Text source) {
        StringBuilder sb = new StringBuilder();
        List<Text> icons = new ArrayList<>();
        Style[] contentStyle = new Style[]{Style.EMPTY};
        walk(source, Style.EMPTY, sb, icons, contentStyle);
        return new Extracted(sb.toString(), icons, contentStyle[0]);
    }

    public static Style findWynncraftPixelStyle(Text source) {
        return findWynncraftPixelStyle(source, Style.EMPTY);
    }

    private static Style findWynncraftPixelStyle(Text node, Style parentStyle) {
        Style merged = node.getStyle().withParent(parentStyle);
        if (isWynncraftPixelFont(merged))
            return merged;

        for (Text child : node.getSiblings()) {
            Style found = findWynncraftPixelStyle(child, merged);
            if (found != null)
                return found;
        }
        return null;
    }

    private static void walk(Text node, Style parentStyle, StringBuilder out,
                              List<Text> icons, Style[] firstContentStyle) {
        Style merged = node.getStyle().withParent(parentStyle);

        StyleSpriteSource font = merged.getFont();
        String fontStr = font == null ? "" : font.toString();
        boolean isIcon = fontStr.contains("hud/dialogue/text/common/")
                || fontStr.contains("minecraft:common")
                || fontStr.contains("minecraft:keybind")
                || fontStr.contains("minecraft:interface")
                || fontStr.contains("minecraft:tooltip")
                || fontStr.contains("minecraft:space");

        if (isIcon) {
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

        for (Text child : node.getSiblings()) {
            walk(child, merged, out, icons, firstContentStyle);
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
        int start = source.getString().indexOf(original);
        if (start < 0)
            return source;

        int[] offset = {0};
        boolean[] inserted = {false};
        MutableText copy = copyReplacingLabel(
                source, Style.EMPTY, start, start + original.length(),
                original, translated, offset, inserted);
        return inserted[0] ? copy : source;
    }

    private static MutableText copyReplacingLabel(Text node, Style parent,
                                                  int start, int end,
                                                  String original, String translated,
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

                if (!inserted[0]) {
                    Style translatedStyle = translated.codePoints()
                            .anyMatch(TextEmojiUtils::isCyrillic)
                            ? effectiveStyle.withFont(WYNNCRAFT_CYRILLIC_FONT)
                            : effectiveStyle;
                    Text translatedText = Text.literal(translated).setStyle(translatedStyle);
                    result.append(translatedText);

                    int widthDelta = MinecraftClient.getInstance().textRenderer.getWidth(
                            Text.literal(original).setStyle(effectiveStyle))
                            - MinecraftClient.getInstance().textRenderer.getWidth(translatedText);
                    if (widthDelta != 0) {
                        result.append(Text.literal(new String(Character.toChars(
                                        0xD0000 + widthDelta)))
                                .setStyle(Style.EMPTY.withFont(SPACE_FONT)));
                    }
                    inserted[0] = true;
                }

                result.append(Text.literal(value.substring(localEnd))
                        .setStyle(node.getStyle()));
            }
            offset[0] = nodeEnd;
        }

        for (Text sibling : node.getSiblings()) {
            result.append(copyReplacingLabel(
                    sibling, effectiveStyle, start, end,
                    original, translated, offset, inserted));
        }
        return result;
    }

    private static Text rebuild(String translated, List<Text> icons, Style rootStyle,
                                boolean dialogue, String original) {
        MutableText result = Text.literal("");

        Style baseStyle = rootStyle != null ? rootStyle : Style.EMPTY;
        Style current = baseStyle;
        StringBuilder buf = new StringBuilder();
        int iconIdx = 0;
        boolean[] usedIcons = dialogue ? new boolean[icons.size()] : null;

        String[] originalRuns = alignmentRuns(translated, original, icons);
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
                    Style iconStyle = icon.getStyle();

                    if (iconStyle.getColor() == null) {
                        iconStyle = iconStyle.withColor(current.getColor());
                    }

                    fixedIcon.setStyle(iconStyle);
                    result.append(fixedIcon);
                }

                i += 3;
                continue;
            }

            char c = translated.charAt(i);
            if (dialogue && c == '[') {
                int end = translated.indexOf(']', i);
                if (end >= 0) {
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
                        current = baseStyle.withColor(Integer.parseInt(hex, 16));
                        i += 7;
                        continue;
                    } catch (NumberFormatException ignored) {}
                }
                net.minecraft.util.Formatting fmt = net.minecraft.util.Formatting.byCode(code);

                if (fmt != null) {
                    if (fmt == net.minecraft.util.Formatting.RESET) {
                        current = baseStyle;
                    } else if (fmt.isColor()) {
                        current = baseStyle.withColor(fmt);
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
        if (original == null || MinecraftClient.getInstance().textRenderer == null) return null;
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
        var renderer = MinecraftClient.getInstance().textRenderer;
        int delta = renderer.getWidth(Text.literal(originalRuns[index]).setStyle(baseStyle))
                - renderer.getWidth(run);
        int before = delta / 2;
        appendSpacer(result, before);
        result.append(run);
        appendSpacer(result, delta - before);
    }

    private static void appendSpacer(MutableText result, int pixels) {
        if (pixels == 0) return;
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
        Integer runType = null; // 0 = normal (style), 1 = space (SPACE_FONT), 2 = cyrillic (WYNNCRAFT_CYRILLIC_FONT)

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
