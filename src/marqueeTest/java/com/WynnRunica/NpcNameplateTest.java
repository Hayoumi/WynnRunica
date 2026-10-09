package com.WynnRunica;

import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.util.Identifier;

public final class NpcNameplateTest {
    public static void main(String[] args) {
        var source = Text.literal("Party Finder\n").styled(s -> s.withColor(0xFF55FF))
                .append(Text.literal("Find a party or queue for a raid").styled(s -> s.withColor(0xAAAAAA)));
        var segments = TelemetrySender.serialize(source);
        expect(segments.size() == 2, "Whole capture retains ordered segments");
        expect(segments.get(0).color().equals("#FF55FF"), "Source color survives capture");
        expect(TelemetrySender.questName("Prelude to Annihilation (8m 40s left)").equals("Prelude to Annihilation")
                        && TelemetrySender.questName("Prelude to Annihilation (32m left)").equals("Prelude to Annihilation")
                        && TelemetrySender.questName("The Olmic Rune (Part 2)").equals("The Olmic Rune (Part 2)"),
                "08.10: an event countdown is not part of the quest name");
        var mob = java.util.List.of(
                new TelemetrySender.Segment("Aqua Surveyor", "#FF5555", "minecraft:default", false, false, false, false, false, false),
                new TelemetrySender.Segment(" ", null, "minecraft:default", false, false, false, false, false, false),
                new TelemetrySender.Segment("\uE00B\uE015", "#FFFFFF", "minecraft:banner/pill", false, false, false, false, false, true),
                new TelemetrySender.Segment("\n", null, "minecraft:default", false, false, false, false, false, false),
                new TelemetrySender.Segment("\uE015\uE016", null, "minecraft:nameplate/default", false, false, false, false, false, true),
                new TelemetrySender.Segment("\n\u00a75\u2620 \u00a77662 \u2739 12s", null, "minecraft:default", false, false, false, false, false, false));
        expect("Aqua Surveyor".equals(NpcNameplateCapture.captureKey(mob)),
                "08.10: a mob is captured by its name, without the effect line: " + NpcNameplateCapture.captureKey(mob));
        var bar = Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("minecraft", "nameplate/default")));
        var poisoned = Text.empty().append(Text.literal("Combat Dummy").styled(s -> s.withColor(0x55FF55)))
                .append(Text.literal("\n")).append(Text.literal("\uE015\uE016").setStyle(bar))
                .append(Text.literal("\n\u00a75\u2620 \u00a7710"));
        var parts = NameplateStyler.splitTail(poisoned);
        expect(parts != null && parts[0].getString().equals("Combat Dummy")
                        && parts[1].getString().equals("\n\uE015\uE016\n\u00a75\u2620 \u00a7710")
                        && TelemetrySender.serialize(parts[0]).getFirst().color().equals("#55FF55")
                        && TelemetrySender.serialize(parts[1]).get(1).font().equals("minecraft:nameplate/default"),
                "08.10: a mob name is separated from the health bar and the effect line, styles kept");
        expect(NameplateStyler.splitTail(source) == null,
                "08.10: a nameplate whose last line has words is not split");
        var dummy = Text.empty().append(Text.literal("Combat Dummy").styled(s -> s.withColor(0xFFFF55)))
                .append(Text.literal("\nNPC")).append(Text.literal("\nAverage DPS: 23493 / 24929"))
                .append(Text.literal("\n§5☠ §78.4k"));
        var dummyParts = NameplateStyler.splitTail(dummy);
        expect(dummyParts != null && dummyParts[0].getString().equals("Combat Dummy\nNPC\nAverage DPS: 23493 / 24929")
                        && dummyParts[1].getString().equals("\n§5☠ §78.4k"),
                "08.10: a poison line under the dummy is separated from its three lines");
        TranslationLoader.loadAll(java.nio.file.Path.of("src/main/resources"));
        String dummyRu = NpcNameResolver.resolveNameplate(dummyParts[0].getString());
        expect(dummyRu != null && dummyRu.contains("Манекен") && dummyRu.contains("23493"),
                "08.10: the poisoned dummy finds its translation without the poison line: " + dummyRu);
        for (String offer : new String[]{"Crate Offer\n2d 5h 30m\n\n20% OFF Crates", "Crate Offer\n59s\n\n10% OFF Crates",
                "Token Offer\n2d 59m\n\n30% OFF Tokens", "Crate Offer\n\n\n10% OFF Crates",
                "Rank Offer\n3d 1h 54m\n\n30% OFF Ranks\n\nExtra Bonus:\nGet 5 Tradable Shares FREE"}) {
            String offerRu = NpcNameResolver.resolveNameplate(offer);
            expect(offerRu != null && offerRu.contains("СКИДКА"), "08.10: a shop offer is translated for any time format: " + offer);
        }
        expect(NpcNameResolver.resolveNameplate("Rank Offer\n3d\n\n30% OFF Ranks\n\nExtra Bonus:\nGet 5 Tradable Shares FREE").contains("5 Акций"),
                "08.10: the bonus line declines the word by its number");
        expect(NpcNameResolver.resolveNameplate(dummy.getString()) == null,
                "08.10: with the poison line the whole dummy plate has no translation, this was the bug");
        expect(NpcNameplateCapture.captureKey(segments).equals("Party Finder Find a party or queue for a raid"),
                "One nameplate produces one key including its description");
        var icon = Text.literal("\uF001").setStyle(Style.EMPTY.withFont(
                new StyleSpriteSource.Font(Identifier.of("minecraft", "keybind"))));
        var withIcon = TelemetrySender.serialize(Text.empty().append(icon).append("Right-Click"));
        expect(withIcon.getFirst().icon(), "Icon is retained in source metadata");
        expect(NpcNameplateCapture.captureKey(withIcon).equals("Right-Click"), "Icon is excluded from lookup text");
        var extractedIcon = TextEmojiUtils.extract(Text.empty().append(icon).append("Right-Click"));
        expect(TextEmojiUtils.rebuild("§7Нажми <em> ПКМ", extractedIcon.icons, Style.EMPTY)
                .getString().contains("\uF001"), "Translated nameplate keeps original key icon");
        var merchantIcon = Text.literal("\uE005").setStyle(Style.EMPTY.withFont(
                new StyleSpriteSource.Font(Identifier.of("minecraft", "merchant"))));
        var merchantSource = Text.empty().append(merchantIcon).append("\n")
                .append(Text.literal("Party Finder\n").styled(s -> s.withColor(0xFF55FF)))
                .append(Text.literal("Find a party or queue for a raid").styled(s -> s.withColor(0xAAAAAA)));
        var extractedMerchant = TextEmojiUtils.extract(merchantSource);
        var merchantTranslation = TextEmojiUtils.rebuild("<em>\n§dПоиск группы\n§7Группы и очередь на рейд",
                extractedMerchant.icons, extractedMerchant.contentStyle);
        var translatedSegments = TelemetrySender.serialize(merchantTranslation);
        expect(merchantTranslation.getString().startsWith("\uE005\nПоиск группы\n"),
                "Merchant icon stays on its own line above the translated title");
        expect(translatedSegments.getFirst().color() == null,
                "Uncolored source icon is not tinted by the translated title");
        var badgeStyle = Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("minecraft", "banner")));
        String badge = "DISGUISED".chars().collect(StringBuilder::new,
                (out, letter) -> out.append((char) (0xE030 + letter - 'A')), StringBuilder::append).toString();
        expect(NpcNameplateCapture.captureKey(TelemetrySender.serialize(Text.literal(badge).setStyle(badgeStyle)
                .append(Text.literal("PlayerNickname").setStyle(Style.EMPTY)))) == null,
                "Disguised player label is not an NPC capture");
        expect(NpcNameplateCapture.captureKey(TelemetrySender.serialize(Text.literal("Alex's Shop Buy my wares"))) == null,
                "Player-authored shop text is not an NPC capture");
        expect(NpcNameplateCapture.captureKey(TelemetrySender.serialize(Text.literal(
                "Detlas Controlled by a guild [Lv. 100] Click for Options"))) == null,
                "Guild territory label is not an NPC capture");
        expect(NpcNameplateCapture.captureKey(TelemetrySender.serialize(Text.literal("Wooly Wybel")))
                .equals("Wooly Wybel"), "Official pet variants remain eligible");
        TranslationManager.npcTranslations.clear();
        TranslationManager.npcTranslations.put("Trade Market Buy & sell items on the market",
                "Торговая площадка\nПокупай и продавай предметы");
        TranslationManager.npcTranslations.put("Recommended Level: <num>", "Рекомендуемый уровень: <num>");
        TranslationManager.npcTranslations.put("Guard", "Стражник");
        NpcNameResolver.clearCache();
        expect(NpcNameResolver.resolveNameplate("Trade Market\nBuy & sell items on the market")
                .contains("\nПокупай"), "Whole translation retains authored line breaks");
        var recolored = TextEmojiUtils.rebuild("§dПоиск группы\n§7Найди группу",
                java.util.List.of(), Style.EMPTY.withColor(0xFF55FF));
        var recoloredSegments = TelemetrySender.serialize(recolored);
        expect(recoloredSegments.getFirst().color().equals("#FF55FF")
                        && recoloredSegments.getLast().color().equals("#AAAAAA"),
                "Translated description keeps its distinct game color");
        expect(NpcNameResolver.resolveNameplate("Recommended Level: 87").equals("Рекомендуемый уровень: 87"),
                "Numeric template uses the observed value");
        expect(NpcNameResolver.resolveNameplate("\uE005" + new String(Character.toChars(0xCFFFF))
                        + "\nRecommended Level: 87").equals("Рекомендуемый уровень: 87"),
                "Supplementary layout glyphs cannot hide a known multi-line label");
        expect(NpcNameResolver.resolve("[Lv. 10] Guard [Quest]").equals("[Lv. 10] Стражник [Квест]"),
                "Existing level and quest labels survive");
        TranslationManager.npcTranslations.put("Guard", "Охранник");
        NpcNameResolver.clearCache();
        expect(NpcNameResolver.resolve("Guard").equals("Охранник"), "Reload invalidates cached translation");

        expect(styled("§7Loot Chest §7[§f✫§8✫✫✫§7]", "Сундук с добычей [✫✫✫✫]")
                        .equals("§7Сундук с добычей §7[§f✫§8✫✫✫§7]"),
                "Chest stars keep their per-star colours");
        expect(styled("§7Loot Quality\n§2[§a|||||||100%|||||||§2]", "Качество добычи [|||||||<num>%|||||||]")
                        .equals("§7Качество добычи\n§2[§a|||||||100%|||||||§2]"),
                "Quality bar keeps its line break, colours and live value");
        expect(styled("§dLocked §5Loot Chest §5[§d✫✫✫§8✫§5]\n§c§lSLAY!§r§7 Defeat a§f Spine Shaker",
                        "Запертый сундук [✫✫✫✫]\nБОЙ! Победи костетряса")
                        .equals("§dЗапертый §5сундук §5[§d✫✫✫§8✫§5]\n§c§lБОЙ!§r§7 Победи §fкостетряса"),
                "Colour changes inside a phrase land on the matching Russian words");
        expect(styled("§7Progress\n§aCompleted", "Завершено").equals("§7Завершено"),
                "Different word structure still takes colours only from the original");
        expect(styled("§a§lClick to browse Store\n§eRanks, Crates,\nPets & more!",
                        "Открой §bмагазин§7\nПривилегии, сундуки,\nпитомцы и многое другое!")
                        .equals("§a§lОткрой магазин§e\nПривилегии, сундуки,\nпитомцы и многое другое!"),
                "01.10 store sign: hand-written colours in the translation are ignored, lines keep their own colour");
        expect(styled("§dGet §5Crates §dfor hats,\npets, weapon skins\n& more cosmetics!",
                        "Открой §bсундуки§7:\nшляпы, питомцы,\nоблики оружия\nи другие украшения!")
                        .startsWith("§dОткрой§5 сундуки:§d"),
                "Different line count spreads words over the original colours");
        var finder = Text.empty().append(merchantIcon).append("\n")
                .append(Text.literal("Party Finder\n").styled(s -> s.withColor(0xFF55FF)))
                .append(Text.literal("Find a party or queue for a raid").styled(s -> s.withColor(0xAAAAAA)));
        var finderRu = NameplateStyler.apply(finder, "<em>\n§dПоиск группы\n§7Группы и очередь на рейд");
        var finderSegments = TelemetrySender.serialize(finderRu);
        expect(finderRu.getString().equals("\nПоиск группы\nГруппы и очередь на рейд"),
                "Styled nameplate keeps icon and line breaks");
        expect(finderSegments.stream().anyMatch(s -> s.text().startsWith("Поиск") && "#FF55FF".equals(s.color()))
                        && finderSegments.stream().anyMatch(s -> s.text().startsWith("Группы") && "#AAAAAA".equals(s.color())),
                "Styled nameplate keeps each line's colour");
        String chest = "\uE060\uE041 \n§fNew rewards in §66 days§f\n\nYou have §6243§f rewards to pull\nCLICK TO PULL";
        int[] chestStyles = new int[chest.length()];
        var chestResult = NameplateStyler.restyle(chest, chestStyles,
                "Новые награды через 6 д.\n\nДоступно наград: 243\nНАЖМИ, ЧТОБЫ ПОЛУЧИТЬ");
        expect(chestResult != null && chestResult.text().startsWith("\uE060\uE041 \n"),
                "05.10: an icon header above the text does not cancel the nameplate translation");
        expect(TextEmojiUtils.activeLegacyCodes("§7").equals("§7") && TextEmojiUtils.activeLegacyCodes("§c§lA §7").equals("§7"),
                "05.10: a replaced tooltip label keeps the colour code that stood before it");
        Text autoPink = TextEmojiUtils.rebuildDialogue("Нажми [ПКМ] тут", java.util.List.of(), Style.EMPTY);
        Text ownGreen = TextEmojiUtils.rebuildDialogue("Нажми §a[ПКМ]§r тут", java.util.List.of(), Style.EMPTY);
        expect(colorOf(autoPink, "[ПКМ]") == 0xFF55FF, "Brackets in a dialogue are pink by default");
        expect(colorOf(ownGreen, "[ПКМ]") == 0x55FF55, "06.10: a colour set by the translator for brackets is kept");
        Text legacy = Text.literal("§6§lFast Travel\n§fWagon Driver\n").append(merchantIcon);
        Text styledLegacy = NameplateStyler.apply(legacy, "§6§lБыстрое Перемещение\n§fВозница");
        expect(styledLegacy != null && styledLegacy.getString().endsWith(merchantIcon.getString()),
                "An icon footer survives a translated multi-line nameplate");
        expect(colorOf(styledLegacy, "Быстрое Перемещение") == 0xFFAA00
                        && colorOf(styledLegacy, "Возница") == 0xFFFFFF,
                "Embedded source codes and authored labels retain distinct colors");
        Text gather = Text.literal("§6Oak\n§7Equipped Tool: §fAxe");
        Text gatherRu = NameplateStyler.apply(gather, "§6Дуб\n§7Инструмент: §fТопор");
        expect(colorOf(gatherRu, "Инструмент:") == 0xAAAAAA && colorOf(gatherRu, "Топор") == 0xFFFFFF,
                "An explicit tool color survives a different word count");
        Text unchangedPalette = NameplateStyler.apply(Text.literal("Inactive option").styled(s -> s.withColor(0x555555)),
                "§bНедоступный вариант");
        expect(colorOf(unchangedPalette, "Недоступный") == 0x555555,
                "A translation cannot introduce a color absent from the source palette");
        Text dimmedWithIcon = Text.empty().append(Text.literal("Inactive option\n").styled(s -> s.withColor(0x555555)))
                .append(merchantIcon.copy().styled(s -> s.withColor(0xFFFFFF)));
        Text dimmedRu = NameplateStyler.apply(dimmedWithIcon, "§fНедоступный вариант");
        expect(colorOf(dimmedRu, "Недоступный") == 0x555555,
                "A white icon cannot activate the color of an inactive caption");
        System.out.println("NPC nameplate checks passed");
    }

    private static String styled(String raw, String translation) {
        var result = NameplateStyler.restyle(raw, new int[raw.length()], translation);
        return result == null ? null : result.text();
    }

    private static int colorOf(Text text, String part) {
        int[] found = {-1};
        text.visit((style, value) -> {
            if (value.contains(part) && style.getColor() != null) found[0] = style.getColor().getRgb() & 0xFFFFFF;
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return found[0];
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
