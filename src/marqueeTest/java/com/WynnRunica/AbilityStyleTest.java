package com.WynnRunica;

import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AbilityStyleTest {
    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static List<Style> styles(Text text) {
        List<Style> result = new ArrayList<>();
        text.visit((style, value) -> {
            if (!value.isBlank()) result.add(style);
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private static Text unlockTitle(String name, int color) {
        return Text.empty()
                .append(Text.literal("§aUnlock ").setStyle(Style.EMPTY.withColor(0xFFFFFF)))
                .append(Text.literal(name + "§a ability").setStyle(Style.EMPTY.withColor(color).withBold(true)));
    }

    public static void run() {
        for (int color : new int[]{0xFFE14D, 0xFFFFFF, 0xE14DFF}) {
            Text original = unlockTitle("Counter", color);
            var extracted = TextEmojiUtils.extractTooltip(original);
            String translated = ("§a" + TranslationManager.getGuiTranslation("Unlock Ability") + " §*§lКонтрудар")
                    .replace("§*", TextEmojiUtils.accentCode(original));
            List<Style> result = styles(TextEmojiUtils.rebuild(translated, extracted.icons, extracted.contentStyle, extracted.key));
            expect(result.getFirst().getColor().getRgb() == 0x55FF55,
                    "08.10: the words before the ability name stay green");
            expect(result.getLast().getColor().getRgb() == color && result.getLast().isBold(),
                    "08.10: the ability name takes the colour the server gave it: " + Integer.toHexString(color));
        }

        String label = TranslationManager.getGuiTranslation("Paladin Archetype");
        expect(TranslationLoader.hasCyrillic(label) && !label.contains("§"),
                "08.10: an archetype label is translated and sets no colour of its own: " + label);
        for (int color : new int[]{0x60C5CD, 0x555555}) {
            Text original = Text.literal("Paladin Archetype").setStyle(Style.EMPTY.withColor(color).withBold(true));
            var extracted = TextEmojiUtils.extractTooltip(original);
            Style shown = styles(TextEmojiUtils.rebuild(label, extracted.icons, extracted.contentStyle, extracted.key)).getFirst();
            expect(shown.getColor().getRgb() == color && shown.isBold(),
                    "08.10: an archetype label keeps the colour and weight of the original: " + Integer.toHexString(color));
        }
        expect(TranslationManager.getGuiTranslation("You have reached the Paladin Archetype")
                        .equals("You have reached the Paladin Archetype"),
                "08.10: a sentence that ends with an archetype name is not an archetype label");
    }
}
