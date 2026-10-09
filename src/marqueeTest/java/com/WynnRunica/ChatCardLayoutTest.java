package com.WynnRunica;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ChatCardLayoutTest {
    private static final Style SPACE = Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("minecraft:space")));
    private record Column(String text, int left, int width) {
        int center() { return left + width / 2; }
    }

    public static void run() {
        Style link = Style.EMPTY.withColor(0x55FFFF).withUnderline(true)
                .withClickEvent(new ClickEvent.RunCommand("/lootrun"))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Beacon")));
        Style title = Style.EMPTY.withColor(0xFFFF55).withBold(true);
        Text original = Text.empty().append(shift(46)).append(Text.literal("Left Beacon").setStyle(title))
                .append(shift(83)).append(Text.literal("Right Beacon").setStyle(link));
        Text ru = Text.empty().append(shift(46))
                .append(Text.literal("Длинное описание левой карточки с переносом").setStyle(title))
                .append(shift(83)).append(Text.literal("Правый Маяк").setStyle(link));
        List<Column> anchors = columns(original);
        Text fixed = ChatReflow.recenter(ru, original, ChatCardLayoutTest::width);
        check(ChatReflow.hasCardColumns(original, ChatCardLayoutTest::width), "recognises server columns");
        check(fixed.getString().contains("\n"), "long text wraps inside a card");
        List<StringBuilder> words = List.of(new StringBuilder(), new StringBuilder());
        for (Column col : columns(fixed)) {
            int index = col.center() < 160 ? 0 : 1;
            check(Math.abs(col.center() - anchors.get(index).center()) <= 1, "each card keeps its centre");
            check(col.left() >= 0 && col.left() + col.width() <= 320, "cards fit inside chat");
            words.get(index).append(col.text().replace(" ", ""));
        }
        check(words.getFirst().toString().equals("Длинноеописаниелевойкарточкиспереносом"), "left text keeps its order");
        check(words.getLast().toString().equals("ПравыйМаяк"), "right text keeps its order");
        Text shortOriginal = Text.empty().append(shift(65)).append(Text.literal("Left"))
                .append(shift(128)).append(Text.literal("Right"));
        Text wideTitle = Text.empty().append(shift(65)).append(Text.literal("Ш".repeat(25)))
                .append(shift(128)).append(Text.literal("Право"));
        Text wideFixed = ChatReflow.recenter(wideTitle, shortOriginal, ChatCardLayoutTest::width);
        check(!wideFixed.getString().contains("\n"), "a wide title uses free space next to a short title without adding a row");
        List<Column> wideColumns = columns(wideFixed);
        check(wideColumns.getFirst().center() == 77 && wideColumns.getLast().center() == 232, "unequal card widths keep both centres");
        check(wideColumns.getFirst().left() + wideColumns.getFirst().width() < wideColumns.getLast().left(), "unequal card widths keep a gap");
        fixed.visit((style, value) -> {
            if (value.contains("Правый") || value.contains("Маяк")) {
                check(style.getColor().getRgb() == 0x55FFFF && style.isUnderlined(), "link colour and underline survive");
                check(link.getClickEvent().equals(style.getClickEvent()) && link.getHoverEvent().equals(style.getHoverEvent()), "link events survive");
            }
            if (value.contains("описание")) check(style.isBold() && style.getColor().getRgb() == 0xFFFF55, "title style survives wrapping");
            return Optional.empty();
        }, Style.EMPTY);
        Text side = Text.empty().append(shift(210)).append(Text.literal("Right"));
        Text sideRu = Text.empty().append(shift(210)).append(Text.literal("Справа длиннее"));
        check(columns(ChatReflow.recenter(sideRu, side, ChatCardLayoutTest::width)).getFirst().center()
                == columns(side).getFirst().center(), "a line with only the right card keeps its centre");
        Style icon = Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("minecraft:common")));
        Text withIcon = Text.empty().append(shift(46)).append(Text.literal(new String(Character.toChars(0xC0001))).setStyle(icon))
                .append(Text.literal("Left Beacon"));
        Text iconRu = Text.empty().append(shift(46)).append(Text.literal(new String(Character.toChars(0xC0001))).setStyle(icon))
                .append(Text.literal("Описание"));
        check(ChatReflow.recenter(iconRu, withIcon, ChatCardLayoutTest::width).getString().contains(new String(Character.toChars(0xC0001))), "an icon is not mistaken for indentation");
        Text rewards = Text.empty().append(shift(50)).append(Text.literal("Rewards:"));
        Text points = Text.empty().append(shift(50)).append(Text.literal("- +3300 Experience Points"));
        Text pointsRu = Text.empty().append(shift(50)).append(Text.literal("- +3300 Очков Опыта"));
        Text emeralds = Text.empty().append(shift(50)).append(Text.literal("- +512 Emeralds"));
        Text emeraldsRu = Text.empty().append(shift(50)).append(Text.literal("- +512 Изумрудов"));
        ChatReflow.recenter(rewards, rewards, ChatCardLayoutTest::width);
        check(columns(ChatReflow.recenter(pointsRu, points, ChatCardLayoutTest::width)).getFirst().left() == 50
                        && columns(ChatReflow.recenter(emeraldsRu, emeralds, ChatCardLayoutTest::width)).getFirst().left() == 50,
                "08.10: reward lines that share an indent keep their left edge after translation");
        String[][] cardRows = {{"Purple Beacon", "Blue Beacon", "Фиолетовый Маяк", "Синий Маяк"},
                {"+4 Curses,End", "Choose a Boon", "+4 Проклятия, +4 Пулла", "Выберите Дар с"}};
        for (String[] row : cardRows) {
            int leftWidth = width(row[0], Style.EMPTY);
            int lead = 77 - leftWidth / 2;
            int gap = 232 - width(row[1], Style.EMPTY) / 2 - lead - leftWidth;
            Text rowEn = Text.empty().append(shift(lead)).append(Text.literal(row[0])).append(shift(gap)).append(Text.literal(row[1]));
            Text rowRu = Text.empty().append(shift(lead)).append(Text.literal(row[2])).append(shift(gap)).append(Text.literal(row[3]));
            List<Column> placed = columns(ChatReflow.recenter(rowRu, rowEn, ChatCardLayoutTest::width));
            check(placed.size() == 2 && Math.abs(placed.getLast().center() - columns(rowEn).getLast().center()) <= 1,
                    "09.10: card rows that share an indent are not a list, the right card keeps its centre: " + row[3]);
        }
        Text list = Text.literal("    - Reward");
        check(!ChatReflow.hasCardColumns(list, ChatCardLayoutTest::width), "ordinary indented lists stay outside card processing");
        check(ChatReflow.recenter(list, list, ChatCardLayoutTest::width).getString().equals(list.getString()), "ordinary list layout stays unchanged");
    }

    private static boolean spacer(int cp, Style style) {
        return cp >= 0xC0000 && cp <= 0xDFFFF && style.getFont().equals(SPACE.getFont());
    }

    private static int width(String value, Style style) {
        return value.codePoints().map(cp -> spacer(cp, style) ? cp - 0xD0000 : cp == ' ' ? 4 : 6).sum();
    }

    private static Text shift(int pixels) {
        return Text.literal(new String(Character.toChars(0xD0000 + pixels))).setStyle(SPACE);
    }

    private static List<Column> columns(Text text) {
        List<Column> out = new ArrayList<>();
        int[] x = {0}, left = {0}, size = {0};
        StringBuilder body = new StringBuilder();
        Runnable flush = () -> {
            if (!body.isEmpty()) { out.add(new Column(body.toString(), left[0], size[0])); body.setLength(0); size[0] = 0; }
        };
        text.visit((style, value) -> {
            value.codePoints().forEach(cp -> {
                if (cp == '\n' || spacer(cp, style)) {
                    flush.run();
                    x[0] = cp == '\n' ? 0 : x[0] + cp - 0xD0000;
                } else {
                    if (body.isEmpty()) left[0] = x[0];
                    body.appendCodePoint(cp);
                    int advance = width(new String(Character.toChars(cp)), style);
                    x[0] += advance; size[0] += advance;
                }
            });
            return Optional.empty();
        }, Style.EMPTY);
        flush.run(); return out;
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
