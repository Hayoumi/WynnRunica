package com.WynnRunica;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntBiFunction;

public final class ChatReflow {
    private record Cell(int codePoint, Style style) {}
    private record Column(List<Cell> body, int center) {}

    private static final int CHAT_CENTER = 160;
    private static final int CENTER_TOLERANCE = 30;
    private static final int CARD_CENTER_TOLERANCE = 40;
    private static final int MIN_CENTER_LEAD = 16;
    private static final Style SPACE = Style.EMPTY.withFont(
            new StyleSpriteSource.Font(Identifier.of("minecraft", "space")));

    public record Parts(Text firstPrefix, Text nextPrefix, Text body) {}

    private ChatReflow() {}

    public static Parts split(Text message) {
        List<List<Cell>> lines = new ArrayList<>();
        lines.add(new ArrayList<>());
        message.visit((style, value) -> {
            for (int i = 0; i < value.length(); ) {
                int codePoint = value.codePointAt(i);
                i += Character.charCount(codePoint);
                if (codePoint == '\n') {
                    lines.add(new ArrayList<>());
                } else {
                    lines.getLast().add(new Cell(codePoint, style));
                }
            }
            return Optional.empty();
        }, Style.EMPTY);

        List<List<Cell>> prefixes = new ArrayList<>();
        for (List<Cell> line : lines) {
            prefixes.add(line.subList(0, prefixEnd(line)));
        }
        if (lines.size() > 1) {
            String marker = string(prefixes.get(1));
            for (List<Cell> prefix : prefixes) {
                if (prefix.isEmpty()) return null;
            }
            for (int i = 2; i < prefixes.size(); i++) {
                if (!string(prefixes.get(i)).equals(marker)) return null;
            }
        }

        List<Cell> body = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            List<Cell> part = lines.get(i).subList(prefixes.get(i).size(), lines.get(i).size());
            int end = part.size();
            while (end > 0 && part.get(end - 1).codePoint() == ' ') end--;
            part = part.subList(0, end);
            if (part.isEmpty()) continue;
            if (!body.isEmpty()) body.add(new Cell(' ', part.getFirst().style()));
            body.addAll(part);
        }
        Text nextPrefix = null;
        if (lines.size() > 1) nextPrefix = text(prefixes.get(1));
        return new Parts(text(prefixes.getFirst()), nextPrefix, text(body));
    }

    public static Text join(Parts parts, Text body) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = (int) Math.floor(ChatHud.getWidth(client.options.getChatWidth().getValue())
                / client.options.getChatScale().getValue());
        return join(parts, body, width,
                (text, style) -> client.textRenderer.getWidth(Text.literal(text).setStyle(style)));
    }

    public static Text join(Parts parts, Text body, int maxWidth, ToIntBiFunction<String, Style> width) {
        MutableText result = Text.empty().append(parts.firstPrefix());
        if (parts.nextPrefix() == null) return result.append(body);

        List<Cell> cells = cells(body);
        int room = maxWidth - measure(cells(parts.firstPrefix()), width);
        int start = 0;
        while (start < cells.size()) {
            int end = start;
            int lastSpace = -1;
            while (end < cells.size() && measure(cells.subList(start, end + 1), width) <= room) {
                if (cells.get(end).codePoint() == ' ') lastSpace = end;
                end++;
            }
            if (end < cells.size() && lastSpace > start) end = lastSpace;
            if (end == start) end = start + 1;
            result.append(text(cells.subList(start, end)));

            start = end;
            while (start < cells.size() && cells.get(start).codePoint() == ' ') start++;
            if (start < cells.size()) {
                result.append(Text.literal("\n")).append(parts.nextPrefix());
                room = maxWidth - measure(cells(parts.nextPrefix()), width);
            }
        }
        return result;
    }

    public static Text recenter(Text translated, Text original) {
        MinecraftClient client = MinecraftClient.getInstance();
        return recenter(translated, original,
                (text, style) -> client.textRenderer.getWidth(Text.literal(text).setStyle(style)));
    }

    static boolean hasCardColumns(Text original) {
        MinecraftClient client = MinecraftClient.getInstance();
        return hasCardColumns(original,
                (value, style) -> client.textRenderer.getWidth(Text.literal(value).setStyle(style)));
    }

    static boolean hasCardColumns(Text original, ToIntBiFunction<String, Style> width) {
        for (List<Cell> line : lines(original)) {
            if (cardColumns(line, columns(line, width))) return true;
        }
        return false;
    }

    private static boolean cardColumns(List<Cell> line, List<Column> columns) {
        return cardColumns(line, columns, CARD_CENTER_TOLERANCE);
    }

    private static boolean cardColumns(List<Cell> line, List<Column> columns, int tolerance) {
        if (columns.size() == 2) {
            return Math.abs(columns.getFirst().center() - CHAT_CENTER / 2) <= tolerance
                    && Math.abs(columns.getLast().center() - CHAT_CENTER * 3 / 2) <= tolerance;
        }
        if (columns.size() != 1 || line.stream().noneMatch(ChatReflow::layoutCell)) return false;
        int center = columns.getFirst().center();
        return Math.abs(center - CHAT_CENTER / 2) <= tolerance
                || Math.abs(center - CHAT_CENTER * 3 / 2) <= tolerance;
    }

    public static Text recenter(Text translated, Text original, ToIntBiFunction<String, Style> width) {
        List<List<Cell>> ru = lines(translated);
        List<List<Cell>> en = lines(original);
        if (ru.size() != en.size()) return translated;

        int[] leads = new int[en.size()];
        int[] bodies = new int[en.size()];
        boolean[] single = new boolean[en.size()];
        for (int i = 0; i < en.size(); i++) {
            List<Cell> line = en.get(i);
            int lead = leadEnd(line);
            leads[i] = measure(line.subList(0, lead), width);
            bodies[i] = measure(line.subList(lead, line.size()), width);
            single[i] = columns(line, width).size() == 1;
        }
        long now = System.nanoTime();
        boolean afterRecent = now - lastLineAt < 1_500_000_000L;

        MutableText result = Text.empty();
        for (int i = 0; i < ru.size(); i++) {
            if (i > 0) result.append(Text.literal("\n"));
            List<Cell> ruLine = ru.get(i);
            List<Cell> enLine = en.get(i);
            boolean listed = leads[i] > 0 && single[i]
                    && (i > 0 && single[i - 1] && leads[i - 1] == leads[i] && bodies[i - 1] != bodies[i]
                    || i + 1 < leads.length && single[i + 1] && leads[i + 1] == leads[i] && bodies[i + 1] != bodies[i]
                    || i == 0 && afterRecent && lastLineSingle && lastLineLead == leads[i] && lastLineBody != bodies[i]);
            if (listed) {
                result.append(text(ruLine));
                continue;
            }
            List<Column> enColumns = columns(enLine, width);
            List<Column> ruColumns = columns(ruLine, width);
            if (cardColumns(enLine, enColumns) && enColumns.size() == 2 && ruColumns.size() == 2) {
                int boundary = (enColumns.getFirst().center() + enColumns.getLast().center()) / 2;
                if (appendColumns(result, enColumns, ruColumns, boundary, width,
                        cardColumns(enLine, enColumns, CENTER_TOLERANCE))) continue;
            }
            if (cardColumns(enLine, enColumns) && enColumns.size() == 1 && ruColumns.size() == 1) {
                if (appendColumns(result, enColumns, ruColumns, CHAT_CENTER, width,
                        cardColumns(enLine, enColumns, CENTER_TOLERANCE))) continue;
            }
            int ruLead = leadEnd(ruLine);
            int enLead = leadEnd(enLine);
            int leadWidth = measure(enLine.subList(0, enLead), width);
            int enWidth = measure(enLine.subList(enLead, enLine.size()), width);
            int ruWidth = measure(ruLine.subList(ruLead, ruLine.size()), width);
            int center = leadWidth + enWidth / 2;
            boolean centered = leadWidth >= MIN_CENTER_LEAD && Math.abs(center - CHAT_CENTER) <= CENTER_TOLERANCE;
            if (!centered || ruWidth == 0) {
                result.append(text(ruLine));
                continue;
            }
            result.append(text(enLine.subList(0, enLead)));
            int shift = (enWidth - ruWidth) / 2;
            if (shift != 0) {
                result.append(Text.literal(new String(Character.toChars(0xD0000 + shift))).setStyle(SPACE));
            }
            result.append(text(ruLine.subList(ruLead, ruLine.size())));
        }
        if (leads.length > 0) {
            lastLineLead = leads[leads.length - 1];
            lastLineBody = bodies[leads.length - 1];
            lastLineSingle = single[leads.length - 1];
            lastLineAt = now;
        }
        return result;
    }

    private static int lastLineLead = -1;
    private static int lastLineBody;
    private static boolean lastLineSingle;
    private static long lastLineAt;

    private static boolean layoutCell(Cell cell) {
        return cell.codePoint() >= 0xC0000 && cell.codePoint() <= 0xDFFFF
                && cell.style().getFont().toString().contains("minecraft:space");
    }

    private static List<Column> columns(List<Cell> line, ToIntBiFunction<String, Style> width) {
        List<Column> result = new ArrayList<>();
        int start = 0;
        while (start < line.size()) {
            while (start < line.size() && layoutCell(line.get(start))) start++;
            int end = start;
            while (end < line.size() && !layoutCell(line.get(end))) end++;
            List<Cell> body = line.subList(start, end);
            if (!string(body).replaceAll("§(?:#[0-9a-fA-F]{6}|.)", "").isBlank()) {
                int lead = 0;
                while (lead < body.size()) {
                    if (body.get(lead).codePoint() == ' ') lead++;
                    else break;
                }
                int tail = body.size();
                while (tail > lead && body.get(tail - 1).codePoint() == ' ') tail--;
                body = body.subList(lead, tail);
                int left = measure(line.subList(0, start + lead), width);
                result.add(new Column(body, left + measure(body, width) / 2));
            }
            start = end;
        }
        return result;
    }

    private static boolean appendColumns(MutableText result, List<Column> original, List<Column> translated,
                                      int boundary, ToIntBiFunction<String, Style> width, boolean allowWrap) {
        List<List<List<Cell>>> wrapped = new ArrayList<>();
        int height = 1;
        int rightEdge = CHAT_CENTER * 2;
        if (original.size() == 2) {
            int firstCenter = original.getFirst().center();
            int lastCenter = original.getLast().center();
            int firstWidth = Math.min(measure(translated.getFirst().body(), width), firstCenter * 2);
            int lastWidth = Math.min(measure(translated.getLast().body(), width), (rightEdge - lastCenter) * 2);
            int low = firstCenter + (firstWidth + 1) / 2 + 2;
            int high = lastCenter - (lastWidth + 1) / 2 - 2;
            if (low <= high) boundary = Math.max(low, Math.min(high, boundary));
        }
        for (int i = 0; i < original.size(); i++) {
            int center = original.get(i).center();
            int left = original.size() == 1 || center < boundary ? 0 : boundary + 2;
            int right = original.size() == 1 || center >= boundary ? rightEdge : boundary - 2;
            int room = Math.max(1, 2 * Math.min(center - left, right - center));
            List<List<Cell>> rows = wrapColumn(translated.get(i).body(), room, width);
            wrapped.add(rows);
            height = Math.max(height, rows.size());
        }
        if (height > 1 && !allowWrap) return false;
        for (int row = 0; row < height; row++) {
            if (row > 0) result.append(Text.literal("\n"));
            int cursor = 0;
            for (int i = 0; i < original.size(); i++) {
                if (row >= wrapped.get(i).size()) continue;
                List<Cell> body = wrapped.get(i).get(row);
                int size = measure(body, width);
                int left = original.get(i).center() - size / 2;
                int shift = left - cursor;
                if (shift != 0) result.append(Text.literal(new String(Character.toChars(0xD0000 + shift))).setStyle(SPACE));
                result.append(text(body));
                cursor = left + size;
            }
        }
        return true;
    }

    private static List<List<Cell>> wrapColumn(List<Cell> body, int room, ToIntBiFunction<String, Style> width) {
        List<List<Cell>> rows = new ArrayList<>();
        int start = 0;
        while (start < body.size()) {
            int end = start;
            int lastSpace = -1;
            while (end < body.size() && measure(body.subList(start, end + 1), width) <= room) {
                if (body.get(end).codePoint() == ' ') lastSpace = end;
                end++;
            }
            if (end < body.size() && lastSpace > start) end = lastSpace;
            if (end == start) end++;
            rows.add(body.subList(start, end));
            start = end;
            while (start < body.size() && body.get(start).codePoint() == ' ') start++;
        }
        return rows;
    }

    public static String describeLayout(Text translated, Text original) {
        MinecraftClient client = MinecraftClient.getInstance();
        ToIntBiFunction<String, Style> width =
                (text, style) -> client.textRenderer.getWidth(Text.literal(text).setStyle(style));
        List<List<Cell>> ru = lines(translated);
        List<List<Cell>> en = lines(original);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.max(ru.size(), en.size()); i++) {
            out.append("  ");
            if (i < en.size()) out.append(describeLine("было ", en.get(i), width));
            if (i < ru.size()) out.append(describeLine("  стало ", ru.get(i), width));
            out.append('\n');
        }
        return out.toString();
    }

    private static String describeLine(String label, List<Cell> line, ToIntBiFunction<String, Style> width) {
        int lead = leadEnd(line);
        int left = measure(line.subList(0, lead), width);
        int size = measure(line.subList(lead, line.size()), width);
        String text = string(line.subList(lead, line.size()));
        return label + "край " + left + " середина " + (left + size / 2) + " «" + text + "»";
    }

    private static List<List<Cell>> lines(Text text) {
        List<List<Cell>> lines = new ArrayList<>();
        lines.add(new ArrayList<>());
        for (Cell cell : cells(text)) {
            if (cell.codePoint() == '\n') {
                lines.add(new ArrayList<>());
            } else {
                lines.getLast().add(cell);
            }
        }
        return lines;
    }

    private static int leadEnd(List<Cell> line) {
        int end = 0;
        while (end < line.size()) {
            int codePoint = line.get(end).codePoint();
            if (codePoint == '§' && end + 1 < line.size()) {
                end += 2;
            } else if (codePoint == ' ' || (codePoint >= 0xC0000 && codePoint <= 0xDFFFF)) {
                end++;
            } else {
                break;
            }
        }
        return end;
    }

    private static int prefixEnd(List<Cell> line) {
        int end = 0;
        boolean icon = false;
        while (end < line.size()) {
            Cell cell = line.get(end);
            int codePoint = cell.codePoint();
            if ((codePoint >= 0xE000 && codePoint <= 0xF8FF) || codePoint >= 0xC0000
                    || TextEmojiUtils.isIconFont(cell.style())) {
                icon = true;
            } else if (codePoint != ' ') {
                break;
            }
            end++;
        }
        if (!icon) return 0;
        return end;
    }

    private static List<Cell> cells(Text text) {
        List<Cell> cells = new ArrayList<>();
        text.visit((style, value) -> {
            for (int i = 0; i < value.length(); ) {
                int codePoint = value.codePointAt(i);
                i += Character.charCount(codePoint);
                cells.add(new Cell(codePoint, style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return cells;
    }

    private static String string(List<Cell> cells) {
        StringBuilder text = new StringBuilder();
        for (Cell cell : cells) text.appendCodePoint(cell.codePoint());
        return text.toString();
    }

    private static Text text(List<Cell> cells) {
        MutableText out = Text.empty();
        int start = 0;
        for (int i = 1; i <= cells.size(); i++) {
            if (i < cells.size() && cells.get(i).style().equals(cells.get(start).style())) continue;
            out.append(Text.literal(string(cells.subList(start, i))).setStyle(cells.get(start).style()));
            start = i;
        }
        return out;
    }

    private static int measure(List<Cell> cells, ToIntBiFunction<String, Style> width) {
        int total = 0;
        int start = 0;
        for (int i = 1; i <= cells.size(); i++) {
            if (i < cells.size() && cells.get(i).style().equals(cells.get(start).style())) continue;
            total += width.applyAsInt(string(cells.subList(start, i)), cells.get(start).style());
            start = i;
        }
        return total;
    }
}
