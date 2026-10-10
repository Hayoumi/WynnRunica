package com.WynnRunica;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NameplateStyler {
    private static final Pattern WORD_RUN = Pattern.compile(
            "\\p{L}[\\p{L}\\p{M}'’\\-]*(?:[ \\u00A0]+\\p{L}[\\p{L}\\p{M}'’\\-]*)*");
    private static final Pattern SPACES = Pattern.compile("[ \\u00A0]+");
    private static final Pattern WORD = Pattern.compile("[^ \\u00A0]+");
    private static final Pattern ANY_WORD = Pattern.compile("[^\\s\\u00A0]+");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");

    private NameplateStyler() {}

    public record Result(String text, int[] styles) {}

    public static Text apply(Text original, String translation) {
        if (original == null || translation == null) return null;
        List<Style> palette = new ArrayList<>();
        List<Integer> charStyles = new ArrayList<>();
        StringBuilder raw = new StringBuilder();
        original.visit((style, part) -> {
            Style active = style;
            Style reset = style.withBold(false).withItalic(false).withUnderline(false)
                    .withStrikethrough(false).withObfuscated(false);
            for (int i = 0; i < part.length(); i++) {
                if (part.charAt(i) == '§' && i + 1 < part.length()) {
                    char code = part.charAt(i + 1);
                    if (code == '#' && i + 7 < part.length()) {
                        try {
                            active = reset.withColor(Integer.parseInt(part.substring(i + 2, i + 8), 16));
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
                if (!palette.contains(active)) palette.add(active);
                charStyles.add(palette.indexOf(active));
                raw.append(part.charAt(i));
            }
            return Optional.empty();
        }, Style.EMPTY);

        Result result = restyle(raw.toString(), toArray(charStyles), translation);
        if (result == null) return null;
        Set<Integer> textColors = new HashSet<>();
        for (int i = 0; i < raw.length(); i++) {
            if (!Character.isLetter(raw.charAt(i))) continue;
            Style style = palette.get(charStyles.get(i));
            textColors.add(style.getColor() == null ? 0xFFFFFF : style.getColor().getRgb());
        }
        applyExplicitStyles(result, translation, palette, textColors);

        MutableText out = Text.empty();
        String text = result.text();
        int[] styles = result.styles();
        int start = 0;
        for (int i = 1; i <= text.length(); i++) {
            if (i < text.length() && styles[i] == styles[start]) continue;
            out.append(Text.literal(text.substring(start, i)).setStyle(palette.get(styles[start])));
            start = i;
        }
        return TooltipNumberColors.preserve(original, out);
    }

    private static final Pattern HAS_WORD = Pattern.compile("\\p{L}{2,}");

    private static final String LEVEL_BADGE = "";
    private static final String NPC_BADGE = "";

    public static boolean isMob(Text text) {
        return text.visit((style, value) -> {
            if (style.getFont() instanceof StyleSpriteSource.Font font) {
                String id = font.id().toString();
                if (id.startsWith("minecraft:nameplate/")) return Optional.of(true);
                if (id.equals("minecraft:banner/pill") && (value.startsWith(LEVEL_BADGE) || value.startsWith(NPC_BADGE)))
                    return Optional.of(true);
            }
            return Optional.empty();
        }, Style.EMPTY).isPresent();
    }

    public static Text[] splitTail(Text text) {
        String whole = text.getString();
        int cut = whole.length();
        while (cut > 0) {
            int lineStart = whole.lastIndexOf('\n', cut - 1);
            String line = whole.substring(lineStart + 1, cut).replaceAll("§.", "");
            if (HAS_WORD.matcher(line).find()) break;
            if (lineStart < 0) return null;
            cut = lineStart;
        }
        if (cut == 0 || cut == whole.length()) return null;

        int end = cut;
        MutableText name = Text.empty();
        MutableText below = Text.empty();
        int[] at = {0};
        text.visit((style, value) -> {
            int from = at[0];
            at[0] += value.length();
            int split = Math.max(0, Math.min(value.length(), end - from));
            if (split > 0) name.append(Text.literal(value.substring(0, split)).setStyle(style));
            if (split < value.length()) below.append(Text.literal(value.substring(split)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return new Text[] {name, below};
    }

    private static void applyExplicitStyles(Result result, String translation, List<Style> palette,
                                            Set<Integer> textColors) {
        StringBuilder visible = new StringBuilder();
        List<Style> explicit = new ArrayList<>();
        Style active = Style.EMPTY;
        for (int i = 0; i < translation.length(); i++) {
            if (translation.startsWith("<em>", i)) { i += 3; continue; }
            if (translation.charAt(i) == '§' && i + 1 < translation.length()) {
                char code = translation.charAt(i + 1);
                if (code == '#' && i + 7 < translation.length()) {
                    try {
                        active = Style.EMPTY.withColor(Integer.parseInt(translation.substring(i + 2, i + 8), 16));
                        i += 7;
                        continue;
                    } catch (NumberFormatException ignored) {}
                }
                Formatting format = Formatting.byCode(code);
                if (format != null) {
                    active = format == Formatting.RESET ? Style.EMPTY
                            : format.isColor() ? Style.EMPTY.withColor(format) : active.withFormatting(format);
                    i++;
                    continue;
                }
            }
            visible.append(translation.charAt(i));
            explicit.add(active);
        }
        Matcher words = ANY_WORD.matcher(visible);
        List<int[]> matches = new ArrayList<>();
        int cursor = 0;
        while (words.find()) {
            int start = result.text().indexOf(words.group(), cursor);
            if (start < 0) return;
            matches.add(new int[] {words.start(), start, words.end() - words.start()});
            cursor = start + words.end() - words.start();
        }
        for (int[] match : matches) {
            for (int i = 0; i < match[2]; i++) {
                Style override = explicit.get(match[0] + i);
                int position = match[1] + i;
                Style current = palette.get(result.styles()[position]);
                if (override.getColor() != null && textColors.contains(override.getColor().getRgb())) {
                    current = current.withColor(override.getColor());
                }
                if (override.isBold()) current = current.withBold(true);
                if (override.isItalic()) current = current.withItalic(true);
                if (override.isUnderlined()) current = current.withUnderline(true);
                if (override.isStrikethrough()) current = current.withStrikethrough(true);
                if (override.isObfuscated()) current = current.withObfuscated(true);
                if (!palette.contains(current)) palette.add(current);
                result.styles()[position] = palette.indexOf(current);
            }
        }
    }

    public static Result restyle(String raw, int[] styles, String translation) {
        StringBuilder visible = new StringBuilder();
        List<Integer> rawIndex = new ArrayList<>();
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == '§' && i + 1 < raw.length()) {
                i++;
                continue;
            }
            visible.append(raw.charAt(i));
            rawIndex.add(i);
        }
        String en = visible.toString();
        String ru = translation.replace("<em>", "").replace("<num>", "0").replaceAll("§(?:#[0-9a-fA-F]{6}|.)", "");

        List<int[]> enRuns = runs(en);
        List<int[]> ruRuns = runs(ru);
        if (enRuns.isEmpty()) return null;
        if (enRuns.size() != ruRuns.size() || !gaps(en, enRuns).equals(gaps(ru, ruRuns))) {
            return spread(raw, styles, en, rawIndex, ru);
        }

        Output out = new Output(raw, styles);
        int copied = 0;
        for (int k = 0; k < enRuns.size(); k++) {
            int[] run = enRuns.get(k);
            List<int[]> enWords = new ArrayList<>();
            Matcher word = WORD.matcher(en.substring(run[0], run[1]));
            while (word.find()) {
                int start = rawIndex.get(run[0] + word.start());
                int end = rawIndex.get(run[0] + word.end() - 1) + 1;
                enWords.add(new int[] {start, end});
            }
            String[] ruWords = SPACES.split(ru.substring(ruRuns.get(k)[0], ruRuns.get(k)[1]));

            out.copy(copied, enWords.getFirst()[0]);
            writeWords(out, enWords, ruWords);
            copied = enWords.getLast()[1];
        }
        out.copy(copied, raw.length());
        return new Result(out.text.toString(), toArray(out.textStyles));
    }

    private static void writeWords(Output out, List<int[]> enWords, String[] ruWords) {
        int reached = 0;
        for (int j = 0; j < ruWords.length; j++) {
            int k = j * enWords.size() / ruWords.length;
            int style = out.styles[enWords.get(k)[0]];
            if (j > 0) out.add(' ', style);
            while (reached < k) {
                out.copyCodes(enWords.get(reached)[1], enWords.get(reached + 1)[0]);
                reached++;
            }
            for (int c = 0; c < ruWords[j].length(); c++) out.add(ruWords[j].charAt(c), style);
        }
        while (reached < enWords.size() - 1) {
            out.copyCodes(enWords.get(reached)[1], enWords.get(reached + 1)[0]);
            reached++;
        }
    }

    private static Result spread(String raw, int[] styles, String en, List<Integer> rawIndex, String ru) {
        int headerEnd = 0;
        int lineStart = 0;
        while (lineStart < en.length()) {
            int lineEnd = en.indexOf('\n', lineStart);
            if (lineEnd < 0 || !isIconLine(en.substring(lineStart, lineEnd))) break;
            headerEnd = lineEnd + 1;
            lineStart = lineEnd + 1;
        }
        int footerStart = en.length();
        while (footerStart > headerEnd) {
            int lastLine = en.lastIndexOf('\n', footerStart - 1) + 1;
            if (!isIconLine(en.substring(lastLine, footerStart))) break;
            footerStart = Math.max(headerEnd, lastLine - 1);
        }
        String body = en.substring(headerEnd, footerStart);
        for (int i = 0; i < body.length(); i++) {
            if (isIcon(body.charAt(i))) return null;
        }

        List<int[]> enWords = new ArrayList<>();
        List<Integer> enLine = new ArrayList<>();
        List<Boolean> enNumber = new ArrayList<>();
        Matcher word = ANY_WORD.matcher(body);
        while (word.find()) {
            enWords.add(new int[] {rawIndex.get(headerEnd + word.start()), rawIndex.get(headerEnd + word.end() - 1) + 1});
            enLine.add(lineOf(body, word.start()));
            enNumber.add(isNumber(word.group()));
        }
        List<String> ruWords = new ArrayList<>();
        List<String> ruSpaces = new ArrayList<>();
        List<Integer> ruLine = new ArrayList<>();
        List<Boolean> ruNumber = new ArrayList<>();
        int end = 0;
        word = ANY_WORD.matcher(ru);
        while (word.find()) {
            ruSpaces.add(ru.substring(end, word.start()));
            ruWords.add(word.group());
            ruLine.add(lineOf(ru, word.start()));
            ruNumber.add(isNumber(word.group()));
            end = word.end();
        }
        if (enWords.isEmpty() || ruWords.isEmpty()) return null;

        int lines = lineOf(body, body.length()) + 1;
        boolean byLine = lines == lineOf(ru, ru.length()) + 1;
        for (int line = 0; line < lines && byLine; line++) {
            if (enLine.contains(line) != ruLine.contains(line)) byLine = false;
        }

        Output out = new Output(raw, styles);
        if (headerEnd > 0) {
            out.copy(0, rawIndex.get(headerEnd));
        }
        out.copyCodes(headerEnd == 0 ? 0 : rawIndex.get(headerEnd), enWords.getFirst()[0]);
        int reached = 0;
        for (int j = 0; j < ruWords.size(); j++) {
            List<Integer> enSame = new ArrayList<>();
            List<Integer> ruSame = new ArrayList<>();
            for (int i = 0; i < enWords.size(); i++) {
                if ((!byLine || enLine.get(i).equals(ruLine.get(j))) && enNumber.get(i).equals(ruNumber.get(j))) enSame.add(i);
            }
            for (int i = 0; i < ruWords.size(); i++) {
                if ((!byLine || ruLine.get(i).equals(ruLine.get(j))) && ruNumber.get(i).equals(ruNumber.get(j))) ruSame.add(i);
            }
            if (enSame.isEmpty()) {
                for (int i = 0; i < enWords.size(); i++) {
                    if (!byLine || enLine.get(i).equals(ruLine.get(j))) enSame.add(i);
                }
                ruSame.clear();
                for (int i = 0; i < ruWords.size(); i++) {
                    if (!byLine || ruLine.get(i).equals(ruLine.get(j))) ruSame.add(i);
                }
            }
            int k = enSame.get(ruSame.indexOf(j) * enSame.size() / ruSame.size());

            int style = styles[enWords.get(k)[0]];
            while (reached < k) {
                out.copyCodes(enWords.get(reached)[1], enWords.get(reached + 1)[0]);
                reached++;
            }
            String space = ruSpaces.get(j);
            for (int c = 0; c < space.length(); c++) out.add(space.charAt(c), style);
            for (int c = 0; c < ruWords.get(j).length(); c++) out.add(ruWords.get(j).charAt(c), style);
        }
        if (footerStart < en.length()) out.copy(rawIndex.get(footerStart), raw.length());
        return new Result(out.text.toString(), toArray(out.textStyles));
    }

    private static boolean isIcon(char c) {
        return (c >= 0xE000 && c <= 0xF8FF) || Character.isSurrogate(c);
    }

    private static boolean isIconLine(String line) {
        boolean icon = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (isIcon(c)) icon = true;
            else if (c != ' ') return false;
        }
        return icon;
    }

    private static boolean isNumber(String word) {
        int first = word.charAt(0) == '+' || word.charAt(0) == '-' ? 1 : 0;
        return word.length() > first && Character.isDigit(word.charAt(first));
    }

    private static int lineOf(String text, int position) {
        int line = 0;
        for (int i = 0; i < position; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    private static List<int[]> runs(String text) {
        List<int[]> runs = new ArrayList<>();
        Matcher run = WORD_RUN.matcher(text);
        while (run.find()) runs.add(new int[] {run.start(), run.end()});
        return runs;
    }

    private static List<String> gaps(String text, List<int[]> runs) {
        List<String> gaps = new ArrayList<>();
        int from = 0;
        for (int[] run : runs) {
            gaps.add(gapSymbols(text.substring(from, run[0])));
            from = run[1];
        }
        gaps.add(gapSymbols(text.substring(from)));
        return gaps;
    }

    private static String gapSymbols(String gap) {
        String withoutNumbers = NUMBER.matcher(gap).replaceAll("#");
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < withoutNumbers.length()) {
            int c = withoutNumbers.codePointAt(i);
            i += Character.charCount(c);
            if (Character.isWhitespace(c) || c == 0x00A0) continue;
            if (c >= 0xE000 && c <= 0xF8FF) continue;
            if (c >= 0xC0000) continue;
            out.appendCodePoint(c);
        }
        return out.toString();
    }

    private static int[] toArray(List<Integer> list) {
        int[] array = new int[list.size()];
        for (int i = 0; i < array.length; i++) array[i] = list.get(i);
        return array;
    }

    private static final class Output {
        final String raw;
        final int[] styles;
        final StringBuilder text = new StringBuilder();
        final List<Integer> textStyles = new ArrayList<>();

        Output(String raw, int[] styles) {
            this.raw = raw;
            this.styles = styles;
        }

        void add(char c, int style) {
            text.append(c);
            textStyles.add(style);
        }

        void copy(int from, int to) {
            for (int i = from; i < to; i++) add(raw.charAt(i), styles[i]);
        }

        void copyCodes(int from, int to) {
            for (int i = from; i + 1 < to; i++) {
                if (raw.charAt(i) != '§') continue;
                add(raw.charAt(i), styles[i]);
                add(raw.charAt(i + 1), styles[i + 1]);
                i++;
            }
        }
    }
}
