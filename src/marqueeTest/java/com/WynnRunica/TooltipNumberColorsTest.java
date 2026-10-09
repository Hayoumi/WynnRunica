package com.WynnRunica;

import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TooltipNumberColorsTest {
    public static void main(String[] args) {
        Text source = part("Total Damage: ", 0xAAAAAA).append(part("-30%", 0xFF5555))
                .append(part(" (of your DPS, per bash)", 0x555555));
        Text translated = TextEmojiUtils.rebuild("§7Общий урон: §f-30% §8(от вашего DPS, за баш)", List.of(), Style.EMPTY);
        Text fixed = TooltipNumberColors.preserve(source, translated);
        expectColor(fixed, "-30%", 0xFF5555);
        expectColor(fixed, "Общий урон", 0xAAAAAA);
        expectColor(fixed, "от вашего DPS", 0x555555);
        expect(fixed.getString().equals(translated.getString()), "Content and spacing stay unchanged");
        expect(TooltipNumberColors.preserve(source, fixed) == fixed, "Already correct text needs no rewrite");
        source = part("§7Damage: §c-12% §b+4 §f0.5 §7s", 0xAA00AA);
        translated = TextEmojiUtils.rebuild("§7Урон: §f-12% +4 0.5 §7с", List.of(), Style.EMPTY);
        fixed = TooltipNumberColors.preserve(source, translated);
        expectColor(fixed, "-12%", 0xFF5555);
        expectColor(fixed, "+4", 0x55FFFF);
        expectColor(fixed, "0.5", 0xFFFFFF);
        expectColor(fixed, "с", 0xAAAAAA);
        source = part("Points: ", 0xAAAAAA).append(part("3", 0x55FF55)).append(part("/16", 0xAAAAAA));
        translated = part("Очки: 3/16", 0xFFFFFF);
        fixed = TooltipNumberColors.preserve(source, translated);
        expectColor(fixed, "3", 0x55FF55);
        expectColor(fixed, "/16", 0xAAAAAA);
        source = part("Cost: ", 0xAAAAAA).append(part("7", 0xFF5555)).append(part(" and ", 0xAAAAAA))
                .append(part("7", 0x55FF55));
        translated = part("Цена: 7 и 7", 0xFFFFFF);
        fixed = TooltipNumberColors.preserve(source, translated);
        int first = fixed.getString().indexOf('7');
        int last = fixed.getString().lastIndexOf('7');
        expect(colors(fixed).get(first) == 0xFF5555 && colors(fixed).get(last) == 0x55FF55,
                "Repeated equal values keep their individual positions");
        var font = new StyleSpriteSource.Font(Identifier.of("minecraft", "language/wynncraft"));
        source = part("Time: §#82EFF42.5s", 0xAAAAAA);
        translated = Text.literal("Время: 2.5 с").setStyle(Style.EMPTY.withColor(0xFFFFFF).withBold(true).withFont(font));
        fixed = TooltipNumberColors.preserve(source, translated);
        expectColor(fixed, "2.5", 0x82EFF4);
        expectColor(fixed, "с", 0x82EFF4);
        fixed.visit((style, value) -> {
            expect(style.isBold() && font.equals(style.getFont()), "Numeric font and bold are retained");
            return Optional.empty();
        }, Style.EMPTY);
        source = part("Mana: ", 0xAAAAAA).append(part("+23/3s", 0x55FF55));
        translated = TextEmojiUtils.rebuild("§7Мана: §f+23/3§bс", List.of(), Style.EMPTY);
        fixed = TooltipNumberColors.preserve(source, translated);
        expectColor(fixed, "+23/3с", 0x55FF55);
        source = part("Speed: ", 0xAAAAAA).append(part("-1 tier", 0xFF5555));
        translated = part("Скорость: -1 ур.", 0x55FF55);
        fixed = TooltipNumberColors.preserve(source, translated);
        expectColor(fixed, "-1", 0xFF5555);
        expectColor(fixed, "ур.", 0xFF5555);
        source = part("Time: ", 0xAAAAAA).append(part("6", 0xFFFFFF)).append(part("/5s", 0x55FFFF));
        fixed = TooltipNumberColors.preserve(source, part("Время: 6/5с", 0xFFFFFF));
        expectColor(fixed, "6", 0xFFFFFF);
        expectColor(fixed, "/5с", 0x55FFFF);
        source = part("Time: ", 0xAAAAAA).append(part("60s", 0xFFFFFF)).append(part(".", 0xAAAAAA));
        fixed = TooltipNumberColors.preserve(source, part("Время: 60с.", 0x55FFFF));
        expectColor(fixed, "60с", 0xFFFFFF);
        expectColor(fixed, ".", 0xAAAAAA);
        fixed = TooltipNumberColors.preserve(part("3 minutes", 0xFFFFFF), part("3 минуты", 0xAAAAAA));
        expectColor(fixed, "3", 0xFFFFFF);
        expectColor(fixed, "минуты", 0xFFFFFF);
        fixed = TooltipNumberColors.preserve(part("3m", 0xFFFFFF), part("3м", 0xAAAAAA));
        expectColor(fixed, "3м", 0xFFFFFF);
        source = part("Damage: ", 0xAAAAAA).append(part("5 Air", 0xFFFFFF));
        translated = part("Урон: 5 Воздухом", 0xAAAAAA);
        fixed = TooltipNumberColors.preserve(source, translated);
        expectColor(fixed, "Воздухом", 0xAAAAAA);
        source = part("Duration: ", 0xAAAAAA).append(part("3 seconds", 0xFFFFFF));
        translated = part("Длительность: 3 ур.", 0xAAAAAA);
        fixed = TooltipNumberColors.preserve(source, translated);
        expectColor(fixed, "ур.", 0xAAAAAA);
        Text mismatched = part("Цена: 8", 0xFFFFFF);
        expect(TooltipNumberColors.preserve(part("Cost: 7", 0xFF5555), mismatched) == mismatched,
                "Different values cannot inherit unrelated colors");
        mismatched = part("Цена: 7 и 8", 0xFFFFFF);
        expect(TooltipNumberColors.preserve(part("Cost: 7", 0xFF5555), mismatched) == mismatched,
                "An extra translated number cannot shift subsequent colors");
        mismatched = part("Цена: 7", 0xFFFFFF);
        expect(TooltipNumberColors.preserve(part("Cost: 7 and 8", 0xFF5555), mismatched) == mismatched,
                "Missing translated values cannot shift colors");
        Text textOnly = part("Без чисел", 0xFFFFFF);
        expect(TooltipNumberColors.preserve(source, textOnly) == textOnly, "Non-numeric lines stay untouched");
        System.out.println("Tooltip source numeric colors passed");
    }

    private static net.minecraft.text.MutableText part(String value, int color) {
        return Text.literal(value).setStyle(Style.EMPTY.withColor(color));
    }

    private static List<Integer> colors(Text text) {
        List<Integer> result = new ArrayList<>();
        text.visit((style, value) -> {
            for (int i = 0; i < value.length(); i++) result.add(style.getColor().getRgb());
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private static void expectColor(Text text, String value, int color) {
        int start = text.getString().indexOf(value);
        expect(start >= 0, "Expected text: " + value);
        List<Integer> colors = colors(text);
        for (int i = start; i < start + value.length(); i++) expect(colors.get(i) == color, "Color of " + value);
    }

    private static void expect(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
}
