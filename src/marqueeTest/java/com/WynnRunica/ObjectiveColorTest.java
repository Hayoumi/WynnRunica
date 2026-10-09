package com.WynnRunica;

public final class ObjectiveColorTest {
    private static void expect(String actual, String expected, String message) {
        if (!actual.equals(expected)) throw new AssertionError(message + ": " + actual);
    }

    public static void run() {
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
