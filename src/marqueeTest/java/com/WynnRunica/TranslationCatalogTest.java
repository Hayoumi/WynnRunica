package com.WynnRunica;

import java.nio.file.Path;

public final class TranslationCatalogTest {
    public static void main(String[] args) {
        int broken = TranslationLoader.loadAll(Path.of("src/main/resources"));
        check(broken == 0, "all bundled JSON files load, broken: " + broken);
        check(TranslationManager.translations.size() > 28000,
                "quest lines are loaded, got " + TranslationManager.translations.size());
        check(TranslationManager.ambiguousKeys.size() > 100 && TranslationManager.ambiguousKeys.size() < 2000,
                "lines shared by several quests, got " + TranslationManager.ambiguousKeys.size());
        check(TranslationManager.npcTranslations.size() > 1900,
                "NPC names, got " + TranslationManager.npcTranslations.size());

        String line = "<playername>, it's good to see you. Have you been well?";
        check(TranslationLoader.hasCyrillic(TranslationManager.getTranslation(line, false)), "dialogue line");
        check(TranslationManager.hasExactTranslationInContext(line, "The Cursed One", "dialogue"), "dialogue in its quest");
        check(!TranslationManager.getEntryIdInContext(line, "The Cursed One", "dialogue").isEmpty(), "dialogue entry id");

        String objective = TranslationManager.getTranslationInContext(
                "Return to the resistance camp at [-1, 2, -3].", "The Cursed One", "objective");
        check(objective != null && TranslationLoader.hasCyrillic(objective), "objective found by its number template");

        TranslationManager.reloadGuiPatterns();
        check(TranslationManager.getGuiTranslation("(5)").equals("(§f5§7)"), "GUI number template");
        check(TranslationManager.fillTemplate("<num> <pl:минута|минуты|минут>", java.util.List.of("22"))
                .equals("22 минуты"), "plural form");
        ChatMessageCatalog.loadFrom(Path.of("src/main/resources/chat/messages.json"));
        String captured = ChatMessageCatalog.translate("[NewM] has taken control of Roots of Corruption!", noColors("[NewM] has taken control of Roots of Corruption!"));
        check(captured != null && captured.contains("[NewM]") && captured.contains("Roots of Corruption")
                && TranslationLoader.hasCyrillic(captured), "bundled chat catalog loads and translates: " + captured);
        String buff = ChatMessageCatalog.translate("Hanqiru has given you 25% resistance and 8% strength.", noColors("Hanqiru has given you 25% resistance and 8% strength."));
        check(buff != null && buff.contains("Hanqiru") && buff.indexOf("25%") < buff.indexOf("8%")
                && !buff.contains("resistance"), "chat slots keep their values: " + buff);
        String title = ServerNotificationTranslator.questTitle("§e                           §lMushroom Man");
        check(title != null && title.startsWith("§e                           §l")
                && TranslationLoader.hasCyrillic(title) && !title.contains("Mushroom"),
                "quest name in the completion line comes from the quest banner: " + title);
        check(ServerNotificationTranslator.questTitle("§e§lNot A Real Quest") == null, "other bold lines are left alone");
        check(TranslationManager.statNames.size() > 200, "stat names are loaded from their own file, got " + TranslationManager.statNames.size());
        String[] fire = TranslationManager.statNames.get("Fire Defence");
        check(fire != null && fire[1] != null && !fire[0].contains("  ") && !fire[0].equals(fire[1]),
                "06.10: a stat name has its own form after a number and no stray spaces");
        var pulls = TranslationManager.findGuiLabelTranslation("- 3 Reward Pulls", null);
        var onePull = TranslationManager.findGuiLabelTranslation("- +1 Aspect Pulls", null);
        check(pulls != null && pulls.translation().equals("Пулла Награды")
                && onePull != null && onePull.translation().equals("Аспектный Пулл"),
                "07.10: a stat name after a number takes the form for that number: " + pulls + " / " + onePull);
        String npcQuest = "Random_NPC's";
        int npcLines = TranslationManager.questToKeys.get(npcQuest).size();
        String typed = "maybeyoushouldgoandseethekingbeforeheleavesforthenorthernfrontagain.";
        DialogueTypingMatcher matcher = new DialogueTypingMatcher();
        long started = System.nanoTime();
        for (int length = 1; length <= typed.length(); length++) {
            for (int frame = 0; frame < 5; frame++) {
                matcher.find(typed.substring(0, length), npcQuest, false, TranslationManager.translations,
                        TranslationManager.questTranslations, TranslationManager.questToKeys);
            }
        }
        long millis = (System.nanoTime() - started) / 1_000_000;
        System.out.println("Typing an unknown line among " + npcLines + " Random NPC lines: " + millis + " ms");
        check(millis < 3000, "06.10: typing an untranslated line must not freeze the game, took " + millis + " ms");
        System.out.println("Translation catalog checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String[] noColors(String message) {
        String[] codes = new String[message.length()];
        java.util.Arrays.fill(codes, "");
        return codes;
    }
}
