package com.WynnRunica;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class TextEmojiUtils {

    private static final StyleSpriteSource.Font WYNNCRAFT_CYRILLIC_FONT =
            new StyleSpriteSource.Font(Identifier.of("wynnrunica", "language/wynncraft_cyrillic"));

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
        boolean isWynncraftText = isWynncraftPixelFont(merged);

        boolean isIcon = fontStr.contains("minecraft:common")
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

                if (!isWynncraftText && ((c >= '\uE000' && c <= '\uF8FF') || isSurrogatePua)) {
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
                        firstContentStyle[0] = finalMerged;
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
        MutableText result = Text.literal("");
        
        Style baseStyle = rootStyle != null ? rootStyle : Style.EMPTY;
        Style current = baseStyle;
        StringBuilder buf = new StringBuilder();
        int iconIdx = 0;

        for (int i = 0; i < translated.length(); i++) {

            if (translated.startsWith("<em>", i)) {
                if (buf.length() > 0) {
                    appendStyled(result, buf.toString(), current);
                    buf.setLength(0);
                }
                if (iconIdx < icons.size()) {
                    Text icon = icons.get(iconIdx++);
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
            if (c == '§' && i + 1 < translated.length()) {

                if (buf.length() > 0) {
                    appendStyled(result, buf.toString(), current);
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
            appendStyled(result, buf.toString(), current);
        }
        return result;
    }

    private static void appendStyled(MutableText result, String value, Style style) {
        StyleSpriteSource font = style.getFont();
        String fontName = font == null ? "" : font.toString();
        if (!fontName.contains("wynncraft") || value.codePoints().noneMatch(TextEmojiUtils::isCyrillic)) {
            result.append(Text.literal(value).setStyle(style));
            return;
        }

        StringBuilder run = new StringBuilder();
        Boolean cyrillicRun = null;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            boolean cyrillic = useCyrillicFontFor(value, offset);
            if (cyrillicRun != null && cyrillicRun != cyrillic) {
                Style runStyle = cyrillicRun ? style.withFont(WYNNCRAFT_CYRILLIC_FONT) : style;
                result.append(Text.literal(run.toString()).setStyle(runStyle));
                run.setLength(0);
            }
            cyrillicRun = cyrillic;
            run.appendCodePoint(codePoint);
            offset += Character.charCount(codePoint);
        }
        if (run.length() > 0) {
            Style runStyle = Boolean.TRUE.equals(cyrillicRun) ? style.withFont(WYNNCRAFT_CYRILLIC_FONT) : style;
            result.append(Text.literal(run.toString()).setStyle(runStyle));
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
