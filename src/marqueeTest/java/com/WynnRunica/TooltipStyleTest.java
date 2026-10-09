package com.WynnRunica;

import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TooltipStyleTest {
    public static void main(String[] args) {
        for (int selected : new int[]{0xFFFFFF, 0xAAAAAA}) {
            Text source = Text.literal("§6- ").setStyle(Style.EMPTY.withColor(0xAA00AA))
                    .append(Text.literal("Most Recent").setStyle(Style.EMPTY.withColor(selected)));
            var extracted = TextEmojiUtils.extractTooltip(source);
            expect(TextEmojiUtils.extract(source).contentStyle.getColor().getRgb() == 0xAA00AA,
                    "Legacy extraction used by dialogues and chat is unchanged");
            expect(extracted.contentStyle.getColor().getRgb() == selected, "Caption inherits its own state");
            Text result = TextEmojiUtils.rebuild("§6- §rСначала новые", extracted.icons,
                    extracted.contentStyle);
            List<Style> styles = styles(result);
            expect(styles.getFirst().getColor().getRgb() == 0xFFAA00, "Only the bullet is gold");
            expect(styles.getLast().getColor().getRgb() == selected, "Selection remains dynamic");
        }
        var pixel = new StyleSpriteSource.Font(Identifier.of("minecraft", "language/wynncraft"));
        for (int selected : new int[]{0xFFFFFF, 0xAAAAAA}) {
            Text currency = Text.literal("- ").setStyle(Style.EMPTY.withColor(0x55FFFF))
                    .append(Text.literal("Cash").setStyle(Style.EMPTY.withColor(selected)));
            checkSelector(currency, "§b- §rДеньги", 0x55FFFF, selected);
            for (Style base : new Style[]{Style.EMPTY, Style.EMPTY.withFont(pixel)}) {
                String code = selected == 0xFFFFFF ? "§f" : "§7";
                Text camp = Text.literal("§7").setStyle(base.withColor(0xAA00AA))
                        .append(Text.literal("- " + code + "The Fruma Foray (East)")
                                .setStyle(base.withColor(0xFFAA00)));
                checkSelector(camp, "§6- §rВылазка во Фруму (Восток)", 0xFFAA00, selected);
                if (base.getFont().equals(pixel)) {
                    expect(TextEmojiUtils.findWynncraftPixelStyle(camp).getColor().getRgb() == selected,
                            "Pixel caption reads embedded selection color");
                }
            }
        }
        for (int selected : new int[]{0x55FFFF, 0x00AAAA}) {
            String code = selected == 0x55FFFF ? "§b" : "§3";
            Text party = Text.literal("§7" + code + "- Social")
                    .setStyle(Style.EMPTY.withColor(0xAA00AA));
            checkSelector(party, "- Общение", selected, selected);
        }
        Text hex = Text.literal("§#55FFFF- §#FFFFFFCash")
                .setStyle(Style.EMPTY.withColor(0xAA00AA).withBold(true));
        expect(TextEmojiUtils.extractTooltip(hex).contentStyle.getColor().getRgb() == 0xFFFFFF,
                "Hex caption color is read without losing bold");
        expect(TextEmojiUtils.extractTooltip(hex).contentStyle.isBold(), "Source bold is retained");
        Text reset = Text.literal("§c- §rCash").setStyle(Style.EMPTY.withColor(0xAAAAAA));
        expect(TextEmojiUtils.extractTooltip(reset).contentStyle.getColor().getRgb() == 0xAAAAAA,
                "Source reset returns to component style");
        var keybind = new StyleSpriteSource.Font(Identifier.of("minecraft", "keybind"));
        Text source = Text.literal("\uDB80\uDC05").setStyle(Style.EMPTY.withFont(pixel).withColor(0xFFFFFF))
                .append(Text.literal("\uF000").setStyle(Style.EMPTY.withFont(keybind).withColor(0xFFFFFF)))
                .append(Text.literal("Left-Click to Buy").setStyle(Style.EMPTY.withFont(pixel).withColor(0x55FF55)));
        Style content = TextEmojiUtils.findWynncraftPixelStyle(source);
        expect(content.getColor().getRgb() == 0x55FF55, "Padding and mouse icons cannot choose caption color");
        var extracted = TextEmojiUtils.extractTooltip(source);
        expect(extracted.icons.size() == 2, "Both source icons survive extraction");
        Text result = TextEmojiUtils.rebuild("<em><em> ЛКМ - купить", extracted.icons, content);
        List<Style> styles = styles(result);
        expect(styles.getFirst().getColor().getRgb() == 0xFFFFFF, "Source icon stays white");
        expect(styles.getLast().getColor().getRgb() == 0x55FF55, "Purchase caption stays green");
        Text header = Text.literal("§7").setStyle(Style.EMPTY.withColor(0xAA00AA))
                .append(Text.literal("Static Boon").setStyle(Style.EMPTY.withColor(0xFFFF55).withBold(true)));
        var title = TextEmojiUtils.extractTooltip(header);
        Text translated = TextEmojiUtils.rebuild("Статичный дар", title.icons, title.contentStyle);
        expect(styles(translated).getLast().isBold(), "Header bold survives an empty formatting prefix");
        expect(styles(translated).getLast().getColor().getRgb() == 0xFFFF55, "Header keeps source yellow");
        expect(TranslationLoader.loadAll(java.nio.file.Path.of("src/main/resources")) == 0,
                "Updated translation catalogs load");
        TranslationManager.reloadGuiPatterns();
        // 08.10: диапазон «21-25» это одно значение, подпись после него меняется отдельно,
        // и цифры остаются в своём крупном шрифте.
        var range = TranslationManager.findGuiLabelTranslation("21-25 Level Range", "<num><num> Диапазон Уровней");
        if (range == null || !range.source().equals("Level Range") || !range.translation().equals("Диапазон Уровней")) {
            throw new AssertionError("08.10: a label after a number range is replaced on its own: " + range);
        }
        // 08.10: в диапазоне второе число приходит со своим минусом. Перевод с дефисом и без
        // дефиса дают одно и то же, двойного дефиса не бывает.
        var range2 = java.util.List.of("61", "-70");
        if (!TranslationManager.fillTemplate("Только Уровни <num>-<num>", range2).equals("Только Уровни 61-70")
                || !TranslationManager.fillTemplate("Только Уровни <num><num>", range2).equals("Только Уровни 61-70")
                || !TranslationManager.fillTemplate("§f<num>§7-§f<num>§7с", range2).equals("§f61§7-§f70§7с")
                || !TranslationManager.fillTemplate("Урон: <num>", java.util.List.of("-5")).equals("Урон: -5")) {
            throw new AssertionError("08.10: a range never gets a doubled dash, a real minus stays");
        }
        // 08.10: строка цены на рынке переводится при любом составе разбивки суммы, и вне своей
        // подсказки тоже: эти шаблоны лежат в общем каталоге.
        String[][] prices = {
                {"§a- 98,500² (24¼² 3²½ 4²) each", "§a- §f98,500§7² §8(24¼² 3²½ 4²) §fза штуку"},
                {"§a- 103,425² (25¼² 16²½ 1²) total", "§a- §f103,425§7² §8(25¼² 16²½ 1²) §fвсего"},
                {"§a- 262,144² (1stx) each", "§a- §f262,144§7² §8(1stx) §fза штуку"},
                {"§a- 275,251² (1stx 3.20¼²) total", "§a- §f275,251§7² §8(1stx 3.20¼²) §fвсего"},
                {"§a- 896² (14²½) each", "§a- §f896§7² §8(14²½) §fза штуку"},
                {"§a- 940² (14²½ 44²) total", "§a- §f940§7² §8(14²½ 44²) §fвсего"},
                {"§a- 13,252² ✮ 12,999² (3¼² 11²½ 7²) each", "§a- §f13,252§7² §b✮ 12,999§3² §8(3¼² 11²½ 7²) §7за штуку"},
                // 09.10: разбивка суммы любого состава подходит под одну запись своего вида строки
                {"§a- 542² ✮ 532² (8²½ 20²) each (taxed)", "§a- §f542§7² §b✮ 532§3² §8(8²½ 20²) §fза штуку (с налогом)"},
                {"§a- 56,368² ✮ 55,328² (13¼² 32²½ 32²) total", "§a- §f56,368§7² §b✮ 55,328§3² §8(13¼² 32²½ 32²) §fвсего"},
                {"§a- 7² (7²) total (taxed)", "§a- §f7§7² §8(7²) §fвсего (с налогом)"},
                {"󏿼<em>󐀆 367² ✮ 360² (5²½ 40²) each",
                        "󏿼<em>󐀆 §#FFFFFF367§#AAAAAA² §#55FFFF✮ 360§#00AAAA² §#555555(5²½ 40²)§#FFAA00 за штуку"},
                {"󏿼<em>󐀆 945,000² ✮ 927,000² (3stx 34.32¼²) each",
                        "󏿼<em>󐀆 §#FFFFFF945,000§#AAAAAA² §#55FFFF✮ 927,000§#00AAAA² §#555555(3stx 34.32¼²)§#FFAA00 за штуку"},
        };
        // 09.10: строка класса в выборе персонажа переводится в любой подсказке (её заголовок это
        // имя персонажа, у каждого игрока своё), со значком режима игры и без него.
        String[][] classLines = {
                {"§6- Class: <em> Ninja", "§6- §7Класс: <em> §fНиндзя"},
                {"§6- Class: Ninja", "§6- §7Класс: §fНиндзя"},
                {"§7- Class: <em> Dark Wizard", "§6- §7Класс: <em> §fТёмный Маг"},
                // 09.10: у персонажа с именем заголовок не название класса, остальные строки
                // карточки тоже должны находиться без привязки к заголовку.
                {"§7- Time Played: 28.5 hours", "§6- §7Время игры: §f28.5 ч."},
                {"§7- Level: 120 (1.62%)", "§6- §7Уровень: §f120 §8(1.62%)"},
                {"§7<em> Right-Click to Edit", "<em> §aПКМ - Изменить"},
                {"<em>Content Progress", "<center>§7Прогресс"},
                {"<em>373 of 1290", "<center>§7373 из 1290"},
                {"§7- Location: Ahmsord", "§6- §7Место: §fАмсорд"},
        };
        for (String[] line : classLines) {
            String shown = TranslationManager.getGuiTranslation(line[0], null);
            if (!shown.equals(line[1])) throw new AssertionError("09.10: class line: " + line[0] + " -> " + shown);
        }
        // 09.10: ежедневное задание находится в «квесте» Daily Objectives.
        String daily = TranslationManager.getTranslationInContext("Gather Crops", ObjectiveTranslator.DAILY, "objective");
        if (!"Соберите Урожай".equals(daily)) throw new AssertionError("09.10: daily objective -> " + daily);
        daily = TranslationManager.getTranslationInContext("Slay Lv. 80+ Mobs", ObjectiveTranslator.DAILY, "objective");
        if (!"Уничтожьте Монстров Ур. 80+".equals(daily)) throw new AssertionError("09.10: daily objective -> " + daily);
        daily = TranslationManager.getTranslationInContext("Open T3+ Chests", ObjectiveTranslator.DAILY, "objective");
        if (!"Откройте Сундуки T3+".equals(daily)) throw new AssertionError("09.10: daily objective -> " + daily);
        // 09.10: строки табло лутрана с числами.
        String[][] board = {
                {"Slay! Wave 1 - 3 Mobs Left!", "§6Волна 1 - осталось мобов: §f3§6!"},
                {"Defend for 35s!", "§6Обороняйтесь: §f35 с§6!"},
                {"Loot 0/1 chests!", "§6Откройте сундуки: 0/1!"},
        };
        for (String[] row : board) {
            String shown = TranslationManager.getTranslationInContext(row[0], ObjectiveTranslator.BOARD, "objective");
            if (!row[1].equals(shown)) throw new AssertionError("09.10: board line: " + row[0] + " -> " + shown);
        }
        for (String[] price : prices) {
            String shown = TranslationManager.getGuiTranslation(price[0], null);
            if (!shown.equals(price[1])) throw new AssertionError("08.10: market price line: " + price[0] + " -> " + shown);
        }
        AbilityStyleTest.run();
        ObjectiveColorTest.run();
        checkCatalogSelector("Preferred Currency", "Cash", 0x55FFFF, 0xAAAAAA, false, false);
        checkCatalogSelector("Preferred Currency", "Silverbull Shares", 0x55FFFF, 0xAAAAAA, false, false);
        checkCatalogSelector("Change Camp", "The Fruma Foray (East)", 0xFFAA00, 0xAAAAAA, true, false);
        checkCatalogSelector("Change Raid", "The Nameless Anomaly", 0xFFAA00, 0xAAAAAA, true, false);
        checkCatalogSelector("Party Type", "Social", 0x55FFFF, 0x00AAAA, true, true);
        checkCatalogSelector("Queue Type", "Raids", 0x55FFFF, 0x00AAAA, true, true);
        checkCatalogSelector("Server Region", "North America (NA)", 0xFFAA00, 0x555555, false, false);
        checkCatalogSelector("Sort Cosmetics", "Copies", 0xFFAA00, 0xAAAAAA, false, false);
        checkCatalogSelector("Sort Results", "Most Recent", 0xFFAA00, 0xAAAAAA, false, false);
        checkCatalogSelector("Level Filter", "Lv. 100+", 0xFFAA00, 0xAAAAAA, false, false);
        // 10.10: полоса прогресса аспекта («Tier I >>>>>>>>>> Tier II [7/14]») в переводе
        // сохраняет цвета оригинала знак в знак: пройденные стрелки зелёные, остальные тёмные.
        Text barSource = Text.literal("Tier I ").setStyle(Style.EMPTY.withColor(0xAAAAAA))
                .append(Text.literal(">>>>>").setStyle(Style.EMPTY.withColor(0x55FF55)))
                .append(Text.literal(">>>>>").setStyle(Style.EMPTY.withColor(0x555555)))
                .append(Text.literal(" Tier II").setStyle(Style.EMPTY.withColor(0xFF5555)))
                .append(Text.literal(" [7/14]").setStyle(Style.EMPTY.withColor(0xAAAAAA)));
        Text barTranslated = Text.literal("Уровень I >>>>>>>>>> ").setStyle(Style.EMPTY.withColor(0xAAAAAA))
                .append(Text.literal("Уровень II").setStyle(Style.EMPTY.withColor(0xFF5555)))
                .append(Text.literal(" [7/14]").setStyle(Style.EMPTY.withColor(0xAAAAAA)));
        Text barResult = TooltipNumberColors.preserve(barSource, barTranslated);
        expect(barResult.getString().equals("Уровень I >>>>>>>>>> Уровень II [7/14]"), "10.10: bar text is kept");
        expect(colorAt(barResult, 10) == 0x55FF55 && colorAt(barResult, 14) == 0x55FF55, "10.10: passed arrows are green");
        expect(colorAt(barResult, 15) == 0x555555 && colorAt(barResult, 19) == 0x555555, "10.10: remaining arrows are dark");
        expect(colorAt(barResult, 0) == 0xAAAAAA && colorAt(barResult, 21) == 0xFF5555, "10.10: words keep their colours");
        // Полоса другой длины не трогается.
        Text shortBar = Text.literal("Уровень I >>>>> Уровень II").setStyle(Style.EMPTY.withColor(0xAAAAAA));
        expect(TooltipNumberColors.preserve(barSource, shortBar) == shortBar, "10.10: different bar is left alone");

        // 10.10: ответы игрока из квеста The Cursed One записаны как варианты выбора.
        String answer = ChoiceTranslator.findFullTranslation("I have!", "The Cursed One");
        expect("Я уже!".equals(answer), "10.10: choice stored as dialogue -> " + answer);
        answer = ChoiceTranslator.findFullTranslation("What's the main event?", "The Cursed One");
        expect("Что за главное событие?".equals(answer), "10.10: choice stored as dialogue -> " + answer);
        // Реплика персонажа за ответ игрока не выдаётся, даже если текст совпал слово в слово.
        answer = ChoiceTranslator.findFullTranslation("Thank you, Rex.", "The Cursed One");
        expect(answer == null, "10.10: NPC line must not be used as a choice -> " + answer);

        // 09.10: описание рун одинаковое у всех пяти, «входа в Рейды» стоит во второй строке.
        String[][] runeLines = {
                {"Use this item to enter Raids", "§7Используйте этот предмет для"},
                {"or craft a Corrupted Dungeon", "§7входа в §fРейды §7или создания"},
                {"Key", "§fКлюча Заражённого Подземелья"},
        };
        for (String rune : new String[]{"Uth", "Nii", "Az", "Tol", "Ek"}) {
            var runeScope = TranslationManager.findScopeByTitle("<em>" + rune + " Rune<em>",
                    List.of(runeLines[0][0], runeLines[1][0], runeLines[2][0]), "", "");
            for (String[] line : runeLines) {
                String shown = TranslationManager.getGuiTranslation(line[0], runeScope);
                expect(line[1].equals(shown), "09.10: " + rune + " Rune: " + line[0] + " -> " + shown);
            }
        }
        var currencyScope = TranslationManager.findScopeByTitle("Preferred Currency", List.of(), "", "");
        var defaultFont = new StyleSpriteSource.Font(Identifier.of("minecraft", "default"));
        for (String action : new String[]{"Left-Click to go forward", "Right-Click to go backward"}) {
            Text actionSource = Text.literal("\uF000").setStyle(Style.EMPTY.withFont(keybind).withColor(0xFFFFFF))
                    .append(Text.literal(" " + action).setStyle(Style.EMPTY.withFont(defaultFont).withColor(0x55FF55)));
            var actionExtracted = TextEmojiUtils.extractTooltip(actionSource);
            String actionTranslation = TranslationManager.getGuiTranslation(actionExtracted.key, currencyScope);
            expect(TranslationLoader.hasCyrillic(actionTranslation), "Currency action is translated");
            Text actionResult = TextEmojiUtils.rebuild(actionTranslation, actionExtracted.icons,
                    actionExtracted.contentStyle);
            expect(styles(actionResult).getFirst().getColor().getRgb() == 0xFFFFFF, "Mouse remains white");
            expect(styles(actionResult).getLast().getColor().getRgb() == 0x55FF55, "Action remains green");
        }
        expect(GuiScope.recolor("§8- Classic", "§e- Classic", "§8- Классический").equals("§e- Классический"),
                "06.10: a selector row follows the state of the source");
        expect(GuiScope.recolor("§e- Hunter§3 [VIP+]", "§8- Hunter§3 [VIP+]", "§e- Охотник§3 [VIP+]")
                .equals("§8- Охотник§3 [VIP+]"), "06.10: only the changed colour moves");
        expect(GuiScope.recolor("§7- §7Camp", "§7- §fCamp", "§7- §7Лагерь").equals("§7- §fЛагерь"),
                "06.10: same colour twice is told apart by position");
        expect(GuiScope.recolor("§7- §fCamp", "§7- §7Camp", "§6- §rЛагерь").equals("§6- §rЛагерь"),
                "06.10: colours the translator set on purpose stay");
        expect(GuiScope.recolor("§7§7- Row", "§7- Row", "§7- Ряд").equals("§7- Ряд"),
                "06.10: a doubled code is not a state change");
        GuiScope reskin = new GuiScope("test", "Class Reskin", "Облик класса", List.of());
        reskin.putLine("§8- Classic", "§8- Классический", false);
        reskin.putLine("§e- Hunter§3 [VIP+]", "§e- Охотник§3 [VIP+]", false);
        expect(reskin.findTranslation("§e- Classic").equals("§e- Классический")
                && reskin.findTranslation("§8- Classic").equals("§8- Классический")
                && reskin.findTranslation("§8- Hunter§3 [VIP+]").equals("§8- Охотник§3 [VIP+]"),
                "06.10: both states of a selector resolve from one stored line");
        TextEmojiUtils.width = text -> text.getString().length() * 6;
        int[] seen = new int[2];
        int[] states = {0xFFFFFF, 0xAAAAAA};
        for (int i = 0; i < 2; i++) {
            List<Text> english = List.of(Text.literal("Most Recent").setStyle(Style.EMPTY.withColor(states[i])));
            List<Text> russian = List.of(Text.literal("Сначала Новые").setStyle(Style.EMPTY.withColor(states[i])));
            seen[i] = styles(GuiTranslator.widenColumns(english, russian).getFirst()).getLast().getColor().getRgb();
        }
        expect(seen[0] == 0xFFFFFF && seen[1] == 0xAAAAAA,
                "06.10: a tooltip that changed only its colours is laid out again, not taken from the last one");
        Text duration = Text.literal("3s ").setStyle(Style.EMPTY.withColor(0xFFFFFF))
                .append(Text.literal("Duration").setStyle(Style.EMPTY.withColor(0xAAAAAA)));
        expect(!GuiTranslator.hasUniformLabelStyle(duration, "s Duration"), "Mixed unit and caption cannot share one style");
        expect(GuiTranslator.hasUniformLabelStyle(duration, "Duration"), "Uniform labels retain the aligned replacement");
        expect(!GuiTranslator.hasUniformLabelStyle(Text.literal("3§fs §7Duration"), "s Duration"),
                "Embedded colors also prevent flattening the label");
        Text emphasis = Text.empty().append(Text.literal("Main ").setStyle(Style.EMPTY.withUnderline(true)))
                .append(Text.literal("Attack").setStyle(Style.EMPTY));
        expect(!GuiTranslator.hasUniformLabelStyle(emphasis, "Main Attack"), "Formatting differences also prevent flattening");
        // Ряд иконок требований: сервер ставит его по центру распорками с двух сторон, отступ бывает
        // всего 2 px. После перевода подсказка шире, и ряд обязан переехать на половину прироста.
        TextEmojiUtils.width = text -> {
            int total = 0;
            for (int point : text.getString().codePoints().toArray()) total += point >= 0xD0000 ? point - 0xD0000 : 6;
            return total;
        };
        var space = new StyleSpriteSource.Font(Identifier.of("minecraft", "space"));
        Text icons = Text.empty()
                .append(Text.literal(new String(Character.toChars(0xD0000 + 2))).setStyle(Style.EMPTY.withFont(space)))
                .append(Text.literal("####################"))
                .append(Text.literal(new String(Character.toChars(0xD0000 + 2))).setStyle(Style.EMPTY.withFont(space)));
        List<Text> narrow = List.of(Text.literal("12345678901234567890 "), icons);
        List<Text> wide = List.of(Text.literal("Очень длинная переведённая строка стата"), icons);
        List<Text> placed = GuiTranslator.widenColumns(narrow, wide);
        int grown = TextEmojiUtils.width.applyAsInt(placed.get(0)) - TextEmojiUtils.width.applyAsInt(narrow.get(0));
        int moved = TextEmojiUtils.width.applyAsInt(placed.get(1)) - TextEmojiUtils.width.applyAsInt(icons);
        expect(grown > 0 && moved == grown / 2,
                "08.10: a row padded on both sides moves with the middle of the tooltip: " + grown + " " + moved);
        System.out.println("Tooltip caption style checks passed");
    }

    private static List<Style> styles(Text text) {
        List<Style> result = new ArrayList<>();
        text.visit((style, value) -> {
            if (!value.isEmpty()) result.add(style);
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private static void checkSelector(Text source, String translation, int marker, int caption) {
        var extracted = TextEmojiUtils.extractTooltip(source);
        expect(extracted.contentStyle.getColor().getRgb() == caption,
                "Caption inherits effective source color: " + source.getString());
        Text result = TextEmojiUtils.rebuild(translation, extracted.icons, extracted.contentStyle);
        List<Style> values = styles(result);
        expect(values.getFirst().getColor().getRgb() == marker, "Marker keeps source color");
        expect(values.getLast().getColor().getRgb() == caption, "Caption follows selection state");
    }

    private static void checkCatalogSelector(String title, String caption, int marker, int inactive,
                                             boolean embedded, boolean wholeRow) {
        for (int selected : new int[]{inactive, wholeRow ? 0x55FFFF : 0xFFFFFF, inactive}) {
            String code = selected == 0xFFFFFF ? "§f" : selected == 0x55FFFF ? "§b"
                    : selected == 0x00AAAA ? "§3" : selected == 0x555555 ? "§8" : "§7";
            int bullet = wholeRow ? selected : marker;
            Text row = embedded
                    ? Text.literal("§7").setStyle(Style.EMPTY.withColor(0xAA00AA))
                            .append(Text.literal((wholeRow ? code + "- " : "- " + code) + caption)
                                    .setStyle(Style.EMPTY.withColor(marker)))
                    : Text.literal(marker == 0xFFAA00 ? "§6- " : "- ")
                            .setStyle(Style.EMPTY.withColor(marker))
                            .append(Text.literal(caption).setStyle(Style.EMPTY.withColor(selected)));
            List<Text> translated = GuiTranslator.translatePixelTooltip(List.of(Text.literal(title), row), false);
            expect(TranslationLoader.hasCyrillic(translated.get(1).getString()), "Catalog resolves " + title);
            List<Style> actual = styles(translated.get(1));
            expect(actual.getFirst().getColor().getRgb() == bullet, "Catalog marker: " + title);
            expect(actual.getLast().getColor().getRgb() == selected, "Catalog selection: " + title);
        }
    }

    // Цвет знака с номером index в готовом тексте.
    private static int colorAt(Text text, int index) {
        int[] position = {0};
        int[] found = {-1};
        text.visit((style, part) -> {
            if (index >= position[0] && index < position[0] + part.length() && style.getColor() != null) {
                found[0] = style.getColor().getRgb();
            }
            position[0] += part.length();
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return found[0];
    }

    private static void expect(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
}
