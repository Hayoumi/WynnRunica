package com.WynnRunica;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class TooltipNumberColors {
    private static final Pattern NUMBER = Pattern.compile("[+\\-]?\\d+(?:[.,/]\\d+)*(?:[%°])?");
    private static final Pattern UNIT = Pattern.compile("\\s*(seconds?|secs?|minutes?|mins?|tiers?|blocks?|s|m|секунд(?:а|ы)?|сек\\.?|с|минут(?:а|ы)?|мин\\.?|м|ур\\.?|блок(?:ов|а)?)\\b\\.?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private TooltipNumberColors() {}

    public static Text preserve(Text original, Text translated) {
        return preserve(original, translated, true);
    }

    static Text preserve(Text original, Text translated, boolean symbols) {
        StyledString source = collect(original, true);
        StyledString target = collect(translated, false);
        List<Replacement> replacements = new ArrayList<>();
        if (hasNumber(target.text)) numberColors(source, target, replacements);
        barColors(source, target, replacements);
        if (symbols) symbolColors(source, target, replacements);
        if (replacements.isEmpty()) return translated;
        replacements.sort((first, second) -> Integer.compare(first.position, second.position));
        MutableText result = Text.empty();
        int[] offset = {0};
        int[] cursor = {0};
        translated.visit((style, text) -> {
            int start = 0;
            while (cursor[0] < replacements.size()
                    && replacements.get(cursor[0]).position < offset[0] + text.length()) {
                Replacement change = replacements.get(cursor[0]);
                int local = change.position - offset[0];
                if (local > start) result.append(Text.literal(text.substring(start, local)).setStyle(style));
                int end = local + 1;
                cursor[0]++;
                while (cursor[0] < replacements.size()) {
                    Replacement next = replacements.get(cursor[0]);
                    if (next.position != offset[0] + end || next.color != change.color
                            || end == text.length()) break;
                    end++;
                    cursor[0]++;
                }
                result.append(Text.literal(text.substring(local, end)).setStyle(style.withColor(change.color)));
                start = end;
            }
            if (start < text.length()) result.append(Text.literal(text.substring(start)).setStyle(style));
            offset[0] += text.length();
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private static void numberColors(StyledString source, StyledString target, List<Replacement> out) {
        var before = NUMBER.matcher(source.text);
        var after = NUMBER.matcher(target.text);
        List<Replacement> found = new ArrayList<>();
        while (before.find()) {
            if (!after.find() || !before.group().equals(after.group())) return;
            for (int i = 0; i < before.end() - before.start(); i++) {
                int expected = source.colors[before.start() + i];
                int position = after.start() + i;
                if (target.colors[position] != expected) {
                    found.add(new Replacement(position, expected));
                }
            }
            preserveUnit(source, target, before.end(), after.end(), found);
        }
        if (after.find()) return;
        out.addAll(found);
    }

    private static void barColors(StyledString source, StyledString target, List<Replacement> out) {
        int from = 0;
        int start = 0;
        while (start < target.text.length()) {
            int end = runEnd(target.text, start);
            char sign = target.text.charAt(start);
            if (end - start >= 4 && !Character.isLetterOrDigit(sign) && !Character.isWhitespace(sign)) {
                int found = findRun(source.text, sign, end - start, from);
                if (found >= 0) {
                    for (int i = 0; i < end - start; i++) {
                        int expected = source.colors[found + i];
                        if (target.colors[start + i] != expected) out.add(new Replacement(start + i, expected));
                    }
                    from = found + end - start;
                }
            }
            start = end;
        }
    }

    private static void symbolColors(StyledString source, StyledString target, List<Replacement> out) {
        List<Integer> before = symbols(source.text);
        List<Integer> after = symbols(target.text);
        if (before.size() != after.size()) return;
        for (int i = 0; i < before.size(); i++) {
            if (source.text.charAt(before.get(i)) != target.text.charAt(after.get(i))) return;
        }
        boolean[] taken = new boolean[target.text.length()];
        for (Replacement change : out) taken[change.position] = true;
        for (int i = 0; i < before.size(); i++) {
            int position = after.get(i);
            int expected = source.colors[before.get(i)];
            if (!taken[position] && target.colors[position] != expected) out.add(new Replacement(position, expected));
        }
    }

    private static List<Integer> symbols(String text) {
        List<Integer> found = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean bracket = "()[]{}*".indexOf(c) >= 0;
            boolean sign = c > 0x7F && !Character.isLetterOrDigit(c) && !Character.isWhitespace(c);
            if (bracket || sign) found.add(i);
        }
        return found;
    }

    private static int runEnd(String text, int start) {
        int end = start + 1;
        while (end < text.length() && text.charAt(end) == text.charAt(start)) end++;
        return end;
    }

    private static int findRun(String text, char sign, int length, int from) {
        int start = from;
        while (start < text.length()) {
            int end = runEnd(text, start);
            if (text.charAt(start) == sign && end - start == length) return start;
            start = end;
        }
        return -1;
    }

    private static void preserveUnit(StyledString source, StyledString target, int sourceEnd,
                                     int targetEnd, List<Replacement> replacements) {
        var before = UNIT.matcher(source.text).region(sourceEnd, source.text.length());
        var after = UNIT.matcher(target.text).region(targetEnd, target.text.length());
        if (!before.lookingAt() || !after.lookingAt()
                || !unitKind(before.group(1)).equals(unitKind(after.group(1)))) return;
        int color = source.colors[before.start(1)];
        for (int i = before.start(1); i < before.end(1); i++) {
            if (source.colors[i] != color) return;
        }
        for (int i = after.start(1); i < after.end(); i++) {
            int expected = i >= after.end(1) && before.end() > before.end(1)
                    ? source.colors[before.end(1)] : color;
            if (target.colors[i] != expected) replacements.add(new Replacement(i, expected));
        }
    }

    private static String unitKind(String unit) {
        String value = unit.toLowerCase(Locale.ROOT).replace(".", "");
        if (value.equals("s") || value.startsWith("sec") || value.equals("с") || value.startsWith("сек")) return "seconds";
        if (value.equals("m") || value.equals("м") || value.startsWith("min") || value.startsWith("мин")) return "minutes";
        if (value.startsWith("tier") || value.equals("ур")) return "tiers";
        return "blocks";
    }

    private static boolean hasNumber(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) >= '0' && text.charAt(i) <= '9') return true;
        }
        return false;
    }

    private static StyledString collect(Text value, boolean parseCodes) {
        StringBuilder text = new StringBuilder();
        int[] colors = new int[value.getString().length()];
        value.visit((style, content) -> {
            int base = style.getColor() == null ? 0xFFFFFF : style.getColor().getRgb();
            int color = base;
            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);
                if (parseCodes && c == '§' && i + 1 < content.length()) {
                    char code = content.charAt(i + 1);
                    if (code == '#' && i + 7 < content.length()) {
                        try {
                            color = Integer.parseInt(content.substring(i + 2, i + 8), 16);
                            i += 7;
                            continue;
                        } catch (NumberFormatException ignored) {}
                    }
                    Formatting format = Formatting.byCode(code);
                    if (format != null) {
                        if (format == Formatting.RESET) color = base;
                        else if (format.getColorValue() != null) color = format.getColorValue();
                        i++;
                        continue;
                    }
                }
                colors[text.length()] = color;
                text.append(c);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return new StyledString(text.toString(), colors);
    }

    private record StyledString(String text, int[] colors) {}
    private record Replacement(int position, int color) {}
}
