package com.WynnRunica;

public final class ObjectiveColorTest {
    private static void expect(String actual, String expected, String message) {
        if (!actual.equals(expected)) throw new AssertionError(message + ": " + actual);
    }

    public static void run() {
        String edge = new String(Character.toChars(0xCFF8C));
        String portraitEdge = new String(Character.toChars(0xCFFA4));
        String toCentre = new String(Character.toChars(0xD0028));
        expect(String.valueOf(TextEmojiUtils.dialogueCentered("When death spread across the North...")), "false", "11.10: plain line, edge indent is a separate part");
        expect(String.valueOf(TextEmojiUtils.dialogueCentered(edge + "When death spread across the North...")), "false", "11.10: Summoner's Fate, edge indent glued to grey text");
        expect(String.valueOf(TextEmojiUtils.dialogueCentered(portraitEdge + "......assistant!")), "false", "11.10: the smaller indent of a portrait window is an edge indent too");
        expect(String.valueOf(TextEmojiUtils.dialogueCentered(toCentre + "Cast your ")), "true", "11.10: tutorial hint, shift to the centre");
        expect(String.valueOf(TextEmojiUtils.dialogueCentered(edge + toCentre + "Cast your ")), "true", "11.10: tutorial hint with the edge indent glued in front");
        expect(String.valueOf(TextEmojiUtils.dialogueLeadEnd(edge + toCentre + "Cast your ")), "2", "11.10: only the edge indent is taken as the lead");
        expect(String.valueOf(TextEmojiUtils.dialogueLeadEnd(toCentre + "Cast your ")), "0", "11.10: a shift to the centre is not a lead");
        expect(String.valueOf(ServerNotificationTranslator.isWynncraftHost("play.wynncraft.com")), "true", "10.10: Wynncraft address play.wynncraft.com");
        expect(String.valueOf(ServerNotificationTranslator.isWynncraftHost("wynncraft.com:25565")), "true", "10.10: Wynncraft address wynncraft.com:25565");
        expect(String.valueOf(ServerNotificationTranslator.isWynncraftHost("play.wynncraft.net")), "true", "10.10: Wynncraft address play.wynncraft.net");
        expect(String.valueOf(ServerNotificationTranslator.isWynncraftHost("lobby.wynncraft.org")), "true", "10.10: Wynncraft address lobby.wynncraft.org");
        expect(String.valueOf(ServerNotificationTranslator.isWynncraftHost("Play.Wynncraft.com.")), "true", "10.10: Wynncraft address Play.Wynncraft.com.");
        expect(String.valueOf(ServerNotificationTranslator.isWynncraftHost("notwynncraft.com")), "false", "10.10: Wynncraft address notwynncraft.com");
        expect(String.valueOf(ServerNotificationTranslator.isWynncraftHost("wynncraft.com.evil.ru")), "false", "10.10: Wynncraft address wynncraft.com.evil.ru");
        expect(String.valueOf(ServerNotificationTranslator.isWynncraftHost("localhost")), "false", "10.10: Wynncraft address localhost");
        TranslationLoader.loadAll(java.nio.file.Path.of("src/main/resources"));
        expect(TranslationManager.getTranslation("That's 3 new tickets, for a total of 12. When you'd like to draw your rewards, speak to me again!", false),
                "Новых билетов: 3, всего: 12. Когда захочешь разыграть награды, снова поговори со мной!",
                "10.10: a dialogue line with numbers gets the numbers, not the <num> mark");
        expect(TranslationManager.getTranslationInContext("You have a total of 7 draws ready. Would you like to spend them all?", "Random_NPC's", "dialogue"),
                "У тебя готово попыток: 7. Хочешь потратить их все?",
                "10.10: the same inside a quest context");
        expect(ObjectiveTranslator.scoredGoal("- Slay Lv. 20+ Mobs: 8/140"), "Slay Lv. 20+ Mobs", "09.10: цель на табло");
        expect(ObjectiveTranslator.scoredGoal("★ Craft Items: 0/6"), "Craft Items", "09.10: цель в трекере со звездой");
        expect(ObjectiveTranslator.scoredGoal("Trade 24² with Lv. 1-10 Newbie: 0/1"), "Trade 24² with Lv. 1-10 Newbie", "09.10: цель из двух строк");
        expect(String.valueOf(ObjectiveTranslator.scoredGoal("- Time Estimate: Very Fast")), "null", "09.10: не задание");
        expect(ObjectiveTranslator.bracketColors("§rTalk to Zeph at §3[10, 20, 30]§r.", "Поговорите с Зефом в [10, 20, 30]."),
                "Поговорите с Зефом в §3[10, 20, 30]§r.",
                "08.10: coordinates take the colour the server gave them");
        expect(ObjectiveTranslator.bracketColors("§rBring §b[2 Rotten Flesh]§r to §3[1, 2, 3]", "Принесите [2 Гнилой плоти] в [1, 2, 3]"),
                "Принесите §b[2 Гнилой плоти]§r в §3[1, 2, 3]§r",
                "08.10: two brackets keep two different colours, the last one returns to the text colour");
        expect(ObjectiveTranslator.bracketColors("Go to [1, 2, 3]", "Идите в [1, 2, 3]"), "Идите в [1, 2, 3]",
                "08.10: a bracket the server did not colour stays uncoloured");
        expect(ObjectiveTranslator.bracketColors("§rGo to §3[1, 2, 3]", "Идите в §e[1, 2, 3]"), "Идите в §e[1, 2, 3]",
                "08.10: a colour set by the translator wins");
        expect(ObjectiveTranslator.bracketColors("§rGo to §3[1, 2, 3]", "Идите [туда] в [1, 2, 3]"), "Идите [туда] в [1, 2, 3]",
                "08.10: a different number of brackets is not matched by guess");
    }
}
