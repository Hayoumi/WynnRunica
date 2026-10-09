package com.WynnRunica;

import java.util.ArrayList;
import java.util.List;

import static com.WynnRunica.DialogueRevealController.Action.*;
import static com.WynnRunica.DialogueRevealController.Control.*;

public final class DialogueRevealControllerTest {
    public static void main(String[] args) {
        Fixture normal = typing();
        expect(normal.tick(3, 110) == PRESS, "Fresh typing gets one early pulse");
        expect(normal.tick(4, 160) == NONE, "Do not collapse the server input pulse to one tick");
        expect(normal.tick(5, 210) == NONE, "Keep a single press through the next server ticks");
        expect(normal.tick(6, 260) == NONE, "Do not send duplicate press packets while held");
        expect(normal.tick(7, 310) == RELEASE, "Restore input after at most 200 ms");
        normal.frame("Hey!F-fasterthanlasttime!Keepup!", TYPING, 8, 360);
        normal.frame("Hey!F-fasterthanlasttime!Keepup!!", TYPING, 9, 410);
        expect(normal.tick(10, 460) == NONE, "Never retry later in the same page");

        Fixture slowClient = typing();
        expect(slowClient.tick(3, 110) == PRESS, "Start pulse before slow frame");
        expect(slowClient.tick(4, 410) == RELEASE, "A slow client must not stretch the press across four slow ticks");
        Fixture catchUp = typing();
        expect(catchUp.tick(3, 110) == PRESS, "Start pulse before catch-up ticks");
        expect(catchUp.tick(7, 160) == RELEASE, "Catch-up ticks also bound the press");
        Fixture manual = typing();
        expect(manual.tick(3, 110) == PRESS, "Start pulse before manual input");
        expect(manual.controller.tick(4, ms(130), true, true, false) == RELEASE,
                "Manual Shift takes ownership immediately");

        Fixture ready = typing();
        ready.frame("Hey!F-fasterthanlasttime!Keepup!", READY, 2, 105);
        expect(ready.tick(3, 110) == NONE, "Do not press continue on a complete page");

        Fixture burst = typing();
        String full = "Hey!F-fasterthanlasttime!Keepup!";
        for (int length = 8; length <= full.length(); length++) {
            burst.frame(full.substring(0, length), TYPING, 2, 101);
        }
        expect(burst.tick(3, 110) == NONE, "Coalesced frames invalidate queued growth evidence");

        Fixture stale = typing();
        expect(stale.tick(5, 350) == NONE, "Old packet cannot authorize a late press");

        Fixture delayed = new Fixture();
        delayed.frame(".", TYPING, 0, 0);
        delayed.frame("...Th", TYPING, 18, 900);
        delayed.frame("...Tha", TYPING, 19, 950);
        delayed.frame("...Thankyouforc-c-comingalong.", TYPING, 19, 960);
        expect(delayed.tick(20, 1049) == NONE,
                "12:33:43 regression: never press after all 30 characters arrive");

        Fixture held = typing();
        expect(held.tick(3, 110) == PRESS, "Start pulse");
        expect(held.frame(full, READY, 3, 115) == RELEASE,
                "Release immediately when ready arrives, before next tick");
        expect(held.tick(4, 160) == NONE, "No second release or pulse after acknowledgement");

        Fixture jump = typing();
        expect(jump.tick(3, 110) == PRESS, "Start pulse before jump");
        expect(jump.frame(full, TYPING, 3, 115) == RELEASE,
                "A large reveal releases even if control update lags");

        Fixture choices = typing();
        expect(choices.tick(3, 110) == PRESS, "Start pulse before choices");
        expect(choices.controller.observe(full, "NPC", READY, true, true, 3, ms(120)) == RELEASE,
                "A choice page cancels the held input");
        for (int tick = 4; tick < 100; tick++) {
            choices.controller.observe(full, "NPC", READY, true, true, tick, ms(tick * 50));
            expect(choices.tick(tick, tick * 50) == NONE, "Never select an answer automatically");
        }
        expect(choices.events.stream().filter(line -> line.startsWith("CHOICES ")).count() == 1,
                "Repeated choice frames do not flood diagnostics");

        Fixture afterChoice = new Fixture();
        afterChoice.controller.observe(full, "NPC", READY, true, true, 0, 0);
        afterChoice.frame("It'sa", TYPING, 1, 50);
        afterChoice.controller.tick(2, ms(100), true, true, false);
        afterChoice.frame("It'sab", TYPING, 2, 110);
        afterChoice.frame("It'sabr", TYPING, 3, 150);
        expect(afterChoice.tick(4, 160) == NONE,
                "Shift used for answering cannot trigger another automatic press");

        Fixture unknown = new Fixture();
        unknown.frame("Hey!", UNKNOWN, 0, 0);
        unknown.frame("Hey!F", UNKNOWN, 1, 50);
        unknown.frame("Hey!Ff", UNKNOWN, 2, 100);
        expect(unknown.tick(3, 110) == NONE, "A control font alone is not evidence of typing");

        Fixture repeated = typing();
        repeated.frame("Hey!Ff", TYPING, 2, 105);
        expect(repeated.tick(3, 110) == NONE, "Latest unchanged frame cancels a pending press");

        Fixture screen = typing();
        expect(screen.controller.tick(3, ms(110), true, false, true) == NONE,
                "Do not synthesize input while another screen is open");
        Fixture openedScreen = typing();
        expect(openedScreen.tick(3, 110) == PRESS, "Start pulse before opening a screen");
        expect(openedScreen.controller.tick(4, ms(130), true, false, true) == RELEASE,
                "Opening a screen releases input immediately");

        Fixture disabled = typing();
        expect(disabled.tick(3, 110) == PRESS, "Start pulse before disabling");
        expect(disabled.controller.tick(4, ms(160), false, false, false) == RELEASE,
                "Disabling restores real input");

        Fixture regression = typing();
        expect(regression.tick(3, 110) == PRESS, "Start pulse before regressing frame");
        expect(regression.frame("Hey!", TYPING, 3, 115) == RELEASE, "Regressing frame releases input");
        regression.frame("Hey!Ff", TYPING, 4, 160);
        regression.frame("Hey!Ffa", TYPING, 5, 200);
        expect(regression.tick(6, 210) == NONE, "A stale prefix cannot rearm the same page");

        Fixture empty = typing();
        expect(empty.tick(3, 110) == PRESS, "Start pulse before empty frame");
        expect(empty.frame("", TYPING, 3, 115) == RELEASE, "Empty frame releases input");
        empty.frame("Hey!Ff", TYPING, 4, 160);
        empty.frame("Hey!Ffa", TYPING, 5, 200);
        expect(empty.tick(6, 210) == NONE, "Transient empty frame cannot rearm the same page");

        Fixture shortLine = new Fixture();
        shortLine.expected = 14;
        shortLine.frame("Ah...", TYPING, 0, 0);
        shortLine.frame("Ah...t", TYPING, 1, 50);
        shortLine.frame("Ah...to", TYPING, 2, 100);
        expect(shortLine.tick(3, 110) == NONE,
                "30.09 regression: a short line finishes before the press arrives and would be skipped");

        Fixture unrecognised = typing();
        unrecognised.controller.expect(-1);
        expect(unrecognised.tick(3, 110) == NONE, "Unknown line length never risks a skip");

        Fixture laggy = typing();
        laggy.controller.latency(20);
        expect(laggy.tick(3, 110) == NONE, "High ping needs more of the line left");

        DialogueRevealController.clearLines();
        DialogueRevealController.rememberLine("Ah... tourists.");
        DialogueRevealController.rememberLine("Ah... tourists everywhere, every single day of the week.");
        expect(DialogueRevealController.lineLength("Ah...touris") == 14,
                "Untranslated catalog lines give their length; shared beginnings keep the shortest");
        expect(DialogueRevealController.lineLength("Ah...") == -1, "Too little typed to recognise a line");
        expect(DialogueRevealController.lineLength("Somethingelseentirely") == -1, "Unknown line stays unknown");

        Fixture nextPage = typing();
        expect(nextPage.tick(3, 110) == PRESS, "Start first page pulse");
        expect(nextPage.frame("It'sa", TYPING, 3, 115) == RELEASE, "Page transition releases old pulse");
        nextPage.frame("It'sab", TYPING, 4, 160);
        nextPage.frame("It'sabr", TYPING, 5, 200);
        expect(nextPage.tick(6, 210) == PRESS, "Independent next page can accelerate safely");
        typingMatcher();
        System.out.println("Dialogue reveal regression checks passed");
    }

    private static void typingMatcher() {
        String known = "there'sabitoftroubleupahead.theysentmeoutheretointerceptnewrecruits.";
        String unknown = "there'sablacksmithheretoo,youcouldsellyouroldhelmetthereforsomeemeralds.";
        java.util.Map<String, String> translations = java.util.Map.of(known, "Впереди неспокойно.");
        java.util.Map<String, java.util.Map<String, String>> byQuest = java.util.Map.of("King's Recruit", translations);
        java.util.Map<String, List<String>> keys = java.util.Map.of("King's Recruit", List.of(known));

        DialogueTypingMatcher other = new DialogueTypingMatcher();
        DialogueTypingMatcher.Result last = null;
        for (int length = 3; length <= unknown.length(); length++) {
            last = other.find(unknown.substring(0, length), "King's Recruit", false, translations, byQuest, keys);
        }
        expect(last == null, "02.10 regression: a different line with the same beginning loses the borrowed translation");
        expect(other.find(unknown, "King's Recruit", true, translations, byQuest, keys) == null,
                "A finished untranslated line is never shown with another line's translation");

        java.util.Map<String, String> both = java.util.Map.of(known, "Впереди неспокойно.", unknown, "Здесь есть и кузнец.");
        DialogueTypingMatcher switching = new DialogueTypingMatcher();
        boolean borrowedAfterSplit = false;
        for (int length = 3; length <= unknown.length(); length++) {
            last = switching.find(unknown.substring(0, length), "King's Recruit", false, both, byQuest, keys);
            if (length > 14 && last != null && last.translation().startsWith("Впереди")) borrowedAfterSplit = true;
            if (length == 14) expect(last != null && last.translation().equals("Здесь есть и кузнец."),
                    "The right line is picked on the very letter where the wrong one stops fitting");
        }
        expect(!borrowedAfterSplit && last != null && last.visibleTranslation().equals("Здесь есть и кузнец."),
                "After the split the matcher follows the right line to the end");

        DialogueTypingMatcher same = new DialogueTypingMatcher();
        for (int length = 3; length <= known.length(); length++) {
            last = same.find(known.substring(0, length), "King's Recruit", false, translations, byQuest, keys);
        }
        expect(last != null && last.visibleTranslation().equals("Впереди неспокойно."),
                "The real line is still translated while it is being typed");
    }

    private static Fixture typing() {
        Fixture fixture = new Fixture();
        fixture.frame("Hey!", TYPING, 0, 0);
        fixture.frame("Hey!F", TYPING, 1, 50);
        fixture.frame("Hey!Ff", TYPING, 2, 100);
        return fixture;
    }

    private static final class Fixture {
        final List<String> events = new ArrayList<>();
        final DialogueRevealController controller = new DialogueRevealController(events::add);
        int expected = 40;

        DialogueRevealController.Action frame(String body, DialogueRevealController.Control control,
                                              long tick, long time) {
            var action = controller.observe(body, "NPC", control, false, true, tick, ms(time));
            controller.expect(expected);
            return action;
        }

        DialogueRevealController.Action tick(long tick, long time) {
            return controller.tick(tick, ms(time), true, false, false);
        }
    }

    private static long ms(long time) { return time * 1_000_000; }
    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
