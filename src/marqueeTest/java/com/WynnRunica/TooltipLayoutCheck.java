package com.WynnRunica;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Проверка вёрстки подсказок без игры. Берёт захваченные из игры подсказки, переводит их тем же
// кодом, что и мод, и смотрит, не уехали ли строки. Ширины букв берутся из шрифтов ресурспака
// (fignya/WynnRunica-tools/font-widths.json, делает export_font_widths.py).
// Запуск: gradlew checkTooltipLayout
public final class TooltipLayoutCheck {
    private static final Map<String, Map<Integer, Integer>> WIDTHS = new HashMap<>();
    private static final Set<String> UNKNOWN = new HashSet<>();

    private record Glyph(int codePoint, int x, int advance, boolean visible) {}

    private record Line(List<Glyph> glyphs, int width, int indent, int firstVisible, int lastVisibleEnd, String text) {}

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream("build/tooltip-layout.txt"), true, StandardCharsets.UTF_8));
        Path captures = Path.of(args[0]);
        Path widths = Path.of(args[1]);
        String only = args.length > 2 ? args[2] : "";
        loadWidths(widths);
        TextEmojiUtils.width = TooltipLayoutCheck::measure;
        int broken = TranslationLoader.loadAll(Path.of(args.length > 3 ? args[3] : "src/main/resources"));
        TranslationManager.reloadGuiPatterns();
        if (broken > 0) System.out.println("не загрузилось файлов перевода: " + broken);

        Map<String, List<String>> problems = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        int total = 0;
        int translated = 0;
        for (String row : Files.readAllLines(captures, StandardCharsets.UTF_8)) {
            // Проверяются пиксельные подсказки предметов. Обычные подсказки кнопок мод раскладывает
            // другим кодом (GuiTranslator.translateStack), сюда они не относятся.
            if (row.isBlank() || !row.contains(only)) continue;
            JsonObject capture = JsonParser.parseString(row).getAsJsonObject();
            List<Text> tooltip = new ArrayList<>();
            StringBuilder shape = new StringBuilder();
            for (JsonElement element : capture.getAsJsonArray("lines")) {
                Text line = line(element.getAsJsonObject().getAsJsonArray("segments"));
                tooltip.add(line);
                shape.append(line.getString().replaceAll("\\d+", "0")).append('\n');
            }
            if (tooltip.isEmpty() || !seen.add(shape.toString())) continue;
            total++;

            List<Text> result = GuiTranslator.widenColumns(tooltip, GuiTranslator.translatePixelTooltip(tooltip, true));
            if (result == tooltip) continue;
            translated++;
            String title = capture.has("itemName") ? capture.get("itemName").getAsString() : "?";
            if (!only.isEmpty() && translated <= 2) dump(title, tooltip, result);
            check(title, tooltip, result, problems);
        }

        int count = 0;
        for (Map.Entry<String, List<String>> kind : problems.entrySet()) {
            System.out.println();
            System.out.println("== " + kind.getKey() + ": " + kind.getValue().size());
            for (String problem : kind.getValue().subList(0, Math.min(12, kind.getValue().size()))) {
                System.out.println("   " + problem);
            }
            count += kind.getValue().size();
        }
        System.out.println();
        System.out.println("подсказок разных: " + total + ", с переводом: " + translated + ", замечаний: " + count);
        if (!UNKNOWN.isEmpty()) {
            System.out.println("букв без ширины в таблице: " + UNKNOWN.size() + ", например " + UNKNOWN.stream().limit(8).toList());
        }
    }

    private static void dump(String title, List<Text> before, List<Text> after) {
        System.out.println("---- " + title);
        for (int i = 0; i < before.size(); i++) {
            Line o = layout(before.get(i));
            Line n = layout(after.get(i));
            System.out.println(String.format("%2d было: отступ %4d, с %4d по %4d, вся %4d | стало: отступ %4d, с %4d по %4d, вся %4d | %s -> %s",
                    i, o.indent(), o.firstVisible(), o.lastVisibleEnd(), o.width(),
                    n.indent(), n.firstVisible(), n.lastVisibleEnd(), n.width(), o.text(), n.text()));
        }
    }

    private static void check(String title, List<Text> before, List<Text> after, Map<String, List<String>> problems) {
        List<Line> was = new ArrayList<>();
        List<Line> now = new ArrayList<>();
        int oldWidth = 0;
        int newWidth = 0;
        for (Text text : before) {
            was.add(layout(text));
            oldWidth = Math.max(oldWidth, was.getLast().width());
        }
        for (Text text : after) {
            now.add(layout(text));
            newWidth = Math.max(newWidth, now.getLast().width());
        }
        // Код цвета не должен попадать на экран текстом («F53291Ящики»).
        for (Text text : after) {
            if (text.getString().matches(".*(?<![0-9A-Za-z#])[0-9A-Fa-f]{6}[А-Яа-яЁё].*")) {
                add(problems, "код цвета показан текстом", title + " | «" + text.getString() + "»");
            }
        }
        // Служебная метка не должна попадать на экран.
        for (Text text : after) {
            if (text.getString().contains("<center>") || text.getString().contains("<em>")) {
                add(problems, "в переводе осталась служебная метка", title + " | «" + text.getString() + "»");
            }
        }
        // Полоса прогресса («>>>>>>>>>>») в переводе должна остаться покрашенной по знакам, как в оригинале.
        for (int i = 0; i < before.size() && before.size() == after.size(); i++) {
            List<String> fresh = bars(after.get(i));
            for (String bar : bars(before.get(i))) {
                String shape = bar.substring(0, bar.indexOf('['));
                boolean sameShape = false;
                for (String other : fresh) {
                    if (other.startsWith(shape)) sameShape = true;
                }
                if (sameShape && !fresh.contains(bar)) {
                    add(problems, "полоса прогресса потеряла цвета", title + " | «" + before.get(i).getString() + "»");
                }
            }
        }
        // Ряд значений под рядом иконок («0 0 0 0 125» под STR DEX INT DEF AGI): обе строки не
        // переводятся и должны сдвинуться одинаково, иначе значения уезжают из-под иконок.
        for (int i = 1; i < was.size() && was.size() == now.size(); i++) {
            Line row = was.get(i);
            if (row.indent() < 3 || !row.text().equals(now.get(i).text())) continue;
            int above = i - 1;
            while (above > 0 && was.get(above).width() == 0) above--;
            Line upper = was.get(above);
            if (upper.indent() < 1 || !upper.text().equals(now.get(above).text())) continue;
            if (Math.abs(row.indent() - upper.indent()) > 6 || Math.abs(row.lastVisibleEnd() - upper.lastVisibleEnd()) > 3) continue;
            int moved = now.get(i).indent() - row.indent();
            int movedAbove = now.get(above).indent() - upper.indent();
            if (Math.abs(moved - movedAbove) > 1) {
                add(problems, "ряд значений уехал из-под ряда иконок", title + " | сдвиг " + moved + ", у ряда выше " + movedAbove);
            }
        }
        // Шапка: иконка и название с отрицательным отступом и плашки под названием с общим отступом.
        int bodyStart = 0;
        // Над шапкой бывают чужие строки без отступа («From <игрок>» от Wynntils).
        int headerStart = 0;
        while (headerStart < was.size() && was.get(headerStart).indent() == 0) headerStart++;
        boolean itemHeader = headerStart < was.size() && was.get(headerStart).indent() < 0;
        if (itemHeader) {
            bodyStart = headerStart;
            while (bodyStart < was.size() && !was.get(bodyStart).glyphs().isEmpty() && was.get(bodyStart).indent() < 0) bodyStart++;
            int beside = bodyStart < was.size() ? was.get(bodyStart).indent() : 0;
            while (beside > 0 && bodyStart < was.size() && !was.get(bodyStart).glyphs().isEmpty()
                    && was.get(bodyStart).indent() == beside) bodyStart++;
        }

        // Правая вертикаль таблицы статов в оригинале и после перевода.
        int oldColumns = 0;
        for (int i = bodyStart; i < was.size(); i++) {
            if (was.get(i).indent() == 0 && biggestGap(was.get(i)) >= 8) oldColumns = Math.max(oldColumns, was.get(i).lastVisibleEnd());
        }
        int newColumns = 0;
        for (int i = bodyStart; i < Math.min(was.size(), now.size()); i++) {
            Line o = was.get(i);
            if (o.indent() == 0 && biggestGap(o) >= 8 && o.lastVisibleEnd() == oldColumns) newColumns = Math.max(newColumns, now.get(i).lastVisibleEnd());
        }

        for (int i = 0; i < Math.min(was.size(), now.size()); i++) {
            Line o = was.get(i);
            Line n = now.get(i);
            if (o.firstVisible() < 0 || n.firstVisible() < 0) continue;
            String where = title + " | «" + o.text() + "» -> «" + n.text() + "»";
            boolean sameText = o.text().equals(n.text());
            if (overlap(n) > 1 && overlap(o) <= 1) {
                add(problems, "буквы наезжают друг на друга", where + " | наезд " + overlap(n) + " px");
            }

            // Строка без перевода и без табличного разрыва обязана остаться той же длины.
            if (sameText && biggestGap(o) < 8 && n.lastVisibleEnd() - n.firstVisible() != o.lastVisibleEnd() - o.firstVisible()) {
                add(problems, "непереведённая строка растянулась", where + " | было " + (o.lastVisibleEnd() - o.firstVisible())
                        + ", стало " + (n.lastVisibleEnd() - n.firstVisible()));
            }

            if (i < bodyStart) {
                if (sameText && !positions(o).equals(positions(n))) {
                    add(problems, "шапка сдвинулась, хотя текст не менялся", where + " | было с " + o.firstVisible() + ", стало с " + n.firstVisible());
                }
                continue;
            }

            // Строка из ячеек на общих осях с соседней («Сейчас / Станет» над числами):
            // после перевода середины ячеек обязаны совпадать так же, как совпадали.
            int axes = 0;
            for (int other : new int[]{i - 1, i + 1}) {
                if (other < 0 || other >= Math.min(was.size(), now.size())) continue;
                List<int[]> mine = cells(o);
                List<int[]> theirs = cells(was.get(other));
                List<int[]> mineNow = cells(n);
                List<int[]> theirsNow = cells(now.get(other));
                if (mine.size() < 2 || theirs.size() < 2 || mine.size() != mineNow.size() || theirs.size() != theirsNow.size()) continue;
                for (int a = 0; a < mine.size(); a++) {
                    for (int b = 0; b < theirs.size(); b++) {
                        if (mine.get(a)[0] == theirs.get(b)[0]) continue;
                        if (Math.abs(mine.get(a)[0] + mine.get(a)[1] - theirs.get(b)[0] - theirs.get(b)[1]) > 10) continue;
                        axes++;
                        int drift = Math.abs(mineNow.get(a)[0] + mineNow.get(a)[1] - theirsNow.get(b)[0] - theirsNow.get(b)[1]);
                        if (drift > 12 && o.indent() > 0 && Math.abs(o.indent() - (oldWidth - o.lastVisibleEnd())) <= 8) {
                            add(problems, "колонки по центру: ячейка ушла со своей оси", where + " | расхождение " + drift / 2 + " px");
                        }
                    }
                }
            }
            if (axes >= 2 && o.indent() > 0 && Math.abs(o.indent() - (oldWidth - o.lastVisibleEnd())) <= 8) continue;

            // Строка, которую сервер поставил по центру распорками с двух сторон (ряд иконок требований),
            // обязана остаться по центру. Правило от геометрии оригинала, от логики мода не зависит.
            boolean tail = !o.glyphs().isEmpty() && !o.glyphs().getLast().visible() && o.width() > o.lastVisibleEnd();
            if (i >= bodyStart && o.indent() > 0 && tail && Math.abs(o.indent() - (oldWidth - o.width())) <= 1) {
                int left = n.firstVisible();
                int right = newWidth - n.lastVisibleEnd();
                // Сравнивается с перекосом самого оригинала: у ряда значений хвост справа длиннее.
                int was0 = o.firstVisible() - (oldWidth - o.lastVisibleEnd());
                if (Math.abs(left - right - was0) > 2) {
                    add(problems, "ряд с распорками по краям ушёл с середины", where + " | слева " + left + ", справа " + right);
                }
                continue;
            }

            // Строки разной длины с общей серединой (ряд иконок, значения под ним, разделитель)
            // после перевода обязаны остаться на общей оси, куда бы она ни переехала.
            boolean sharedAxis = false;
            for (int other = bodyStart; other < Math.min(was.size(), now.size()) && o.indent() >= 3; other++) {
                Line old = was.get(other);
                if (other == i || old.indent() < 3 || old.firstVisible() < 0) continue;
                if (Math.abs(old.indent() + old.lastVisibleEnd() - o.indent() - o.lastVisibleEnd()) > 2) continue;
                if (Math.abs((old.lastVisibleEnd() - old.indent()) - (o.lastVisibleEnd() - o.indent())) <= 4) continue;
                sharedAxis = true;
                Line moved = now.get(other);
                int drift = Math.abs(moved.indent() + moved.lastVisibleEnd() - n.indent() - n.lastVisibleEnd());
                if (drift > 4 && sameText && was.get(other).text().equals(moved.text())) {
                    add(problems, "строки с общей осью разошлись", where + " | на " + drift / 2 + " px");
                }
            }
            if (sharedAxis) continue;

            // Два правила ниже не зависят от того, как мод понял выравнивание строки.
            if (sameText && newWidth == oldWidth && n.firstVisible() != o.firstVisible()) {
                add(problems, "строку не переводили и ширина та же, а она сдвинулась",
                        where + " | " + o.firstVisible() + " -> " + n.firstVisible());
            }
            if (i + 1 < Math.min(was.size(), now.size())) {
                Line nextOld = was.get(i + 1);
                Line nextNew = now.get(i + 1);
                if (o.indent() > 0 && nextOld.firstVisible() == o.firstVisible()
                        && nextOld.lastVisibleEnd() != o.lastVisibleEnd()
                        && nextNew.firstVisible() >= 0 && nextNew.firstVisible() != n.firstVisible()) {
                    add(problems, "список с общим левым краем развалился",
                            where + " | края " + n.firstVisible() + " и " + nextNew.firstVisible());
                }
            }

            boolean column = o.indent() == 0 && o.lastVisibleEnd() == oldColumns && biggestGap(o) >= 8;
            boolean centered = (o.indent() >= 8 || i == 0 && o.indent() >= 3) && Math.abs(o.indent() - (oldWidth - o.lastVisibleEnd())) <= 2
                    || o.indent() >= 3 && Math.abs(o.indent() - (oldWidth - o.lastVisibleEnd())) <= 1;
            boolean right = o.indent() >= 8 && o.lastVisibleEnd() == oldWidth && !centered;
            int paragraph = paragraphWidth(was, i, bodyStart);
            boolean inParagraph = paragraph > 0;

            if (column) {
                if (n.firstVisible() != o.firstVisible()) {
                    add(problems, "стат: левый край сдвинулся", where + " | " + o.firstVisible() + " -> " + n.firstVisible());
                }
                if (n.lastVisibleEnd() != newColumns) {
                    add(problems, "стат: значения кончаются не на одной вертикали", where + " | конец " + n.lastVisibleEnd() + ", у остальных " + newColumns);
                }
                if (newWidth - newColumns > oldWidth - oldColumns) {
                    add(problems, "стат: поле справа от значений выросло", where + " | было " + (oldWidth - oldColumns) + ", стало " + (newWidth - newColumns));
                }
                if (biggestGap(n) < 4) {
                    add(problems, "стат: название наезжает на значение", where + " | зазор " + biggestGap(n));
                }
            } else if (inParagraph) {
                int target = paragraphWidth(now, i, bodyStart);
                int content = n.lastVisibleEnd() - n.firstVisible();
                if (target > 0 && Math.abs(2 * (n.firstVisible()) + content - target) > 2) {
                    add(problems, "абзац по центру: строка не по центру абзаца", where + " | отступ " + n.firstVisible() + ", текст " + content + ", абзац " + target);
                }
            } else if (centered) {
                int content = n.lastVisibleEnd() - n.firstVisible();
                if (Math.abs(n.firstVisible() - (newWidth - n.lastVisibleEnd())) > 3 && Math.abs(n.indent() - (newWidth - n.lastVisibleEnd())) > 3) {
                    add(problems, "строка по центру: уехала от середины", where + " | отступ " + n.firstVisible() + ", текст " + content + ", ширина " + newWidth);
                }
            } else if (right) {
                if (n.lastVisibleEnd() != newWidth) {
                    add(problems, "строка справа: не прижата к правому краю", where + " | конец " + n.lastVisibleEnd() + ", ширина " + newWidth);
                }
            } else if (sameText && Math.abs(n.firstVisible() - o.firstVisible() - (newWidth - oldWidth) / 2) <= 1) {
                // Строка переехала вместе с серединой подсказки (точки страниц внизу): так и задумано.
            } else if (n.firstVisible() != o.firstVisible()) {
                add(problems, "обычная строка: левый край сдвинулся", where + " | " + o.firstVisible() + " -> " + n.firstVisible());
            }
        }
    }

    // Ширина абзаца из нескольких центрированных строк подряд, в который входит строка i. 0, если абзаца нет.
    private static int paragraphWidth(List<Line> lines, int i, int bodyStart) {
        int first = i;
        while (first > bodyStart && lines.get(first - 1).firstVisible() >= 0 && lines.get(first - 1).glyphs().getFirst().advance() >= 0
                && isSpacer(lines.get(first - 1).glyphs().getFirst())) first--;
        int last = i;
        while (last + 1 < lines.size() && !lines.get(last + 1).glyphs().isEmpty()
                && isSpacer(lines.get(last + 1).glyphs().getFirst())) last++;
        if (lines.get(i).glyphs().isEmpty() || !isSpacer(lines.get(i).glyphs().getFirst()) || last == first) return 0;

        int width = 0;
        int deepest = 0;
        for (int k = first; k <= last; k++) {
            width = Math.max(width, lines.get(k).width());
            deepest = Math.max(deepest, lines.get(k).indent());
        }
        if (deepest < 8) return 0;
        for (int k = first; k <= last; k++) {
            Line line = lines.get(k);
            if (Math.abs(2 * line.indent() + (line.lastVisibleEnd() - line.firstVisible()) - width) > 2) return 0;
        }
        return width;
    }

    // Ячейки строки: {левый край, правый край} каждого куска текста между распорками.
    private static List<int[]> cells(Line line) {
        List<int[]> cells = new ArrayList<>();
        int[] cell = null;
        for (Glyph glyph : line.glyphs()) {
            if (isSpacer(glyph) || !glyph.visible() && !Character.isWhitespace(glyph.codePoint())) {
                cell = null;
            } else if (glyph.visible()) {
                if (cell == null) {
                    cell = new int[]{glyph.x(), 0};
                    cells.add(cell);
                }
                cell[1] = glyph.x() + glyph.advance();
            }
        }
        return cells;
    }

    private static boolean isSpacer(Glyph glyph) {
        return !glyph.visible() && glyph.codePoint() >= 0xC0000 && glyph.codePoint() <= 0xDFFFF;
    }

    private static int biggestGap(Line line) {
        int gap = 0;
        int end = -1;
        for (Glyph glyph : line.glyphs()) {
            if (!glyph.visible()) continue;
            if (end >= 0) gap = Math.max(gap, glyph.x() - end);
            end = glyph.x() + glyph.advance();
        }
        return gap;
    }

    // На сколько пикселей видимая буква заходит на предыдущую (в оригинале такого нет).
    private static int overlap(Line line) {
        int worst = 0;
        int end = Integer.MIN_VALUE;
        for (Glyph glyph : line.glyphs()) {
            if (!glyph.visible()) continue;
            if (end != Integer.MIN_VALUE) worst = Math.max(worst, end - glyph.x());
            end = glyph.x() + glyph.advance();
        }
        return worst;
    }

    private static List<Integer> positions(Line line) {
        List<Integer> positions = new ArrayList<>();
        for (Glyph glyph : line.glyphs()) {
            if (glyph.visible()) positions.add(glyph.x());
        }
        return positions;
    }

    // Полосы прогресса в строке: отрезки из четырёх и более одинаковых знаков с цветом каждого знака.
    private static List<String> bars(Text text) {
        StringBuilder chars = new StringBuilder();
        List<Integer> colors = new ArrayList<>();
        text.visit((style, part) -> {
            for (int i = 0; i < part.length(); i++) {
                chars.append(part.charAt(i));
                colors.add(style.getColor() == null ? -1 : style.getColor().getRgb());
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < chars.length()) {
            int end = start + 1;
            while (end < chars.length() && chars.charAt(end) == chars.charAt(start)) end++;
            char sign = chars.charAt(start);
            if (end - start >= 4 && !Character.isLetterOrDigit(sign) && !Character.isWhitespace(sign)) {
                result.add(sign + " x" + (end - start) + " " + colors.subList(start, end));
            }
            start = end;
        }
        return result;
    }

    private static void add(Map<String, List<String>> problems, String kind, String detail) {
        problems.computeIfAbsent(kind, key -> new ArrayList<>()).add(detail);
    }

    private static Line layout(Text text) {
        List<Glyph> glyphs = new ArrayList<>();
        int[] x = {0};
        StringBuilder plain = new StringBuilder();
        text.visit((style, value) -> {
            String font = fontOf(style);
            for (int i = 0; i < value.length(); ) {
                int codePoint = value.codePointAt(i);
                i += Character.charCount(codePoint);
                if (codePoint == '§' && i < value.length()) {
                    i += Character.charCount(value.codePointAt(i));
                    continue;
                }
                int advance = advance(font, codePoint, style.isBold());
                boolean spacer = codePoint >= 0xC0000 && codePoint <= 0xDFFFF;
                boolean visible = !spacer && !Character.isWhitespace(codePoint) && !font.equals("minecraft:space");
                glyphs.add(new Glyph(codePoint, x[0], advance, visible));
                if (!spacer && (codePoint < 0xE000 || codePoint > 0xF8FF)) plain.appendCodePoint(codePoint);
                x[0] += advance;
            }
            return Optional.empty();
        }, Style.EMPTY);

        int indent = 0;
        int firstVisible = -1;
        int lastVisibleEnd = 0;
        boolean leading = true;
        for (Glyph glyph : glyphs) {
            if (glyph.visible()) {
                if (firstVisible < 0) firstVisible = glyph.x();
                lastVisibleEnd = glyph.x() + glyph.advance();
                leading = false;
            } else if (leading && isSpacer(glyph)) {
                indent += glyph.advance();
            } else {
                leading = false;
            }
        }
        return new Line(glyphs, x[0], indent, firstVisible, lastVisibleEnd, plain.toString().strip());
    }

    private static int measure(Text text) {
        return layout(text).width();
    }

    private static int advance(String font, int codePoint, boolean bold) {
        Integer known = WIDTHS.getOrDefault(font, Map.of()).get(codePoint);
        if (known == null && codePoint >= 0xC0000 && codePoint <= 0xDFFFF) return codePoint - 0xD0000;
        if (known == null) known = WIDTHS.getOrDefault("minecraft:default", Map.of()).get(codePoint);
        if (known == null) {
            UNKNOWN.add(font + " U+" + Integer.toHexString(codePoint));
            return 6;
        }
        boolean spacer = codePoint >= 0xC0000 && codePoint <= 0xDFFFF;
        return bold && !spacer && known > 0 ? known + 1 : known;
    }

    private static String fontOf(Style style) {
        if (style.getFont() instanceof StyleSpriteSource.Font font) return font.id().toString();
        return "minecraft:default";
    }

    private static Text line(JsonArray segments) {
        MutableText line = Text.empty();
        for (JsonElement element : segments) {
            JsonObject segment = element.getAsJsonObject();
            Style style = Style.EMPTY;
            if (segment.has("color") && !segment.get("color").isJsonNull()) {
                style = style.withColor(Integer.parseInt(segment.get("color").getAsString().substring(1), 16));
            }
            String font = segment.has("font") ? segment.get("font").getAsString() : "minecraft:default";
            if (!font.equals("minecraft:default") && !font.startsWith("sprite:")) {
                style = style.withFont(new StyleSpriteSource.Font(Identifier.of(font)));
            }
            if (segment.get("bold").getAsBoolean()) style = style.withBold(true);
            if (segment.get("italic").getAsBoolean()) style = style.withItalic(true);
            line.append(Text.literal(segment.get("text").getAsString()).setStyle(style));
        }
        return line;
    }

    private static void loadWidths(Path file) throws Exception {
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        for (String font : root.keySet()) {
            Map<Integer, Integer> table = new HashMap<>();
            JsonObject glyphs = root.getAsJsonObject(font);
            for (String key : glyphs.keySet()) table.put(Integer.parseInt(key, 16), glyphs.get(key).getAsInt());
            WIDTHS.put(font, table);
        }
    }
}
