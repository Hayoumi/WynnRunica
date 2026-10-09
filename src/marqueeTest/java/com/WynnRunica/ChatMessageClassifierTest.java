package com.WynnRunica;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;

import java.net.URI;

public final class ChatMessageClassifierTest {
    public static void main(String[] args) {
        mods();
        accepted("You gained 300 experience points.");
        accepted("Quest completed: The Cursed One");
        accepted("A cold breeze runs down your spine.\nSomething is watching you.");
        accepted("§bYour weapon has been identified.");
        rejected("PlayerName: Quest completed: a joke");
        rejected("[HERO] Custom Nickname: You gained 300 experience points.");
        rejected("From PlayerName: hello");
        rejected("To PlayerName: hello");
        rejected("<PlayerName> hello");
        rejected("Nickname shouts: hello");
        rejected("\uE040\uE041\uE060 PlayerName: hello");
        rejected("\uE005 PartyMember: hello");
        rejected("\uE006 GuildMember: hello");
        rejected("\uE007 Someone: hello");
        rejected("\uDB00\uDC06 Nickname says hello");
        rejected("[WynnRunica] connected");
        rejected("⁤ ›› Trying to connect to ping server...");
        rejected("⁤ ›› Connected to ping server!");
        rejected("⁤ ›› Successfully co");
        rejected("Successfully connected to the remote player server.");
        rejected("Disconnected from the remote player server.");
        accepted("§4[!] Congratulations to enui_ for reaching level 110 in Ⓒ Woodcutting!");
        accepted("Congratulations to care package for reaching level 100 in Mining!");
        accepted("Someone has logged into server AS12 as a Knight");
        accepted("The Necromantic Site World Event starts in 2m 59s! (399 blocks away) Click to track");
        accepted("\uDB00\uDC06 The Necromantic Site World Event starts in 2m 59s! (399 blocks away) Click to track");
        accepted("\uE060\uE046\uE048\uE043\uE042\uE062\uE016 Having issues with your content tracker not updating? Enable autotracking.");
        rejected("\uE040\uE041\uE060 Corvala: is there anyone who'd be willing to give me ability shards?");
        rejected(" ");
        rejected("A".repeat(2049));
        check(!ServerNotificationTranslator.eligible("You gained 300 experience points.",true),"player interaction");
        check(ServerNotificationTranslator.normalizeNumbers("§7You gained §#55FFFF300§r points.")
                .equals("§7You gained §#55FFFF<num>§r points."), "hex colors remain intact");
        check(ServerNotificationTranslator.normalizeNumbers("§8§l-12.5 / +300").equals("§8§l<num> / <num>"), "signed values");
        check(ServerNotificationTranslator.normalizeNumbers("Hanqiru has invited Hardt4chno to the guild")
                        .equals("Hanqiru has invited Hardt4chno to the guild")
                        && ServerNotificationTranslator.normalizeNumbers("Wait 5m30s, tier T3 x2 Lv.105").equals("Wait <num>m<num>s, tier T<num> x<num> Lv.<num>"),
                "08.10: a digit inside a name is not a number, units and prefixes still are");
        check(ServerNotificationTranslator.hideNames("RamenTozz's Pouch", java.util.List.of("RamenTozz")).equals("<actor>'s Pouch")
                        && ServerNotificationTranslator.hideNames("Eggplant", java.util.List.of("Egg")).equals("Eggplant"),
                "08.10: a nickname in a screen title is hidden, a word that only starts with it is not");
        check(ServerNotificationTranslator.normalizeNumbers("You gained 300 points.")
                .equals(ServerNotificationTranslator.normalizeNumbers("You gained 500 points.")), "one numeric family");
        Text linked = Text.literal("Read ").append(Text.literal("wynncraft.com")
                .setStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create("https://wynncraft.com")))));
        Text preserved = ServerNotificationTranslator.restoreLinks(Text.literal("Подробнее: wynncraft.com"), linked);
        check(preserved != null && preserved.getString().equals("Подробнее: wynncraft.com"), "link text survives");
        boolean[] clickable = {false};
        preserved.visit((style, value) -> {
            if (value.equals("wynncraft.com") && style.getClickEvent() instanceof ClickEvent.OpenUrl)
                clickable[0] = true;
            return java.util.Optional.empty();
        }, Style.EMPTY);
        check(clickable[0], "translated URL keeps its click action");
        Style site = Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create("https://wynncraft.com")));
        Text twoLinks = Text.literal("").append(Text.literal("play.wynncraft.com").setStyle(site))
                .append(Text.literal(" -/- ")).append(Text.literal("wynncraft.com").setStyle(site));
        check(ServerNotificationTranslator.restoreLinks(Text.literal("play.wynncraft.com -/- wynncraft.com"), twoLinks) != null,
                "02.10: one link inside the text of another does not cancel the translation");
        Text command = Text.literal("Event starts soon! ").append(Text.literal("Click to track")
                .setStyle(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/track"))));
        Text tracked = ServerNotificationTranslator.restoreLinks(Text.literal("Событие скоро начнётся! Нажми, чтобы отследить"), command);
        boolean[] everywhere = {tracked != null};
        if (tracked != null) tracked.visit((style, value) -> {
            if (!(style.getClickEvent() instanceof ClickEvent.RunCommand)) everywhere[0] = false;
            return java.util.Optional.empty();
        }, Style.EMPTY);
        check(everywhere[0], "a command click whose words were translated covers the whole message");
        check(ServerNotificationTranslator.restoreLinks(Text.literal("Подробнее"), linked) == null,
                "missing URL must not lose its click action");
        System.out.println("Chat classification and capture normalization passed");
    }
    private static void mods() {
        Style pill = Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("minecraft", "banner/pill")));
        Text wynntilsTip = Text.literal("").setStyle(pill)
                .append(Text.literal(" Press Middle Button on a player to perform quick actions."));
        check(ServerNotificationTranslator.fromOtherMod(wynntilsTip), "Wynntils badge marks a mod message");
        Text wynntilsPrefix = Text.literal(" ").setStyle(Style.EMPTY.withFont(
                new StyleSpriteSource.Font(Identifier.of("wynntils", "prefix")))).append(Text.literal("Trying to connect to Hades failed."));
        check(ServerNotificationTranslator.fromOtherMod(wynntilsPrefix), "Non-minecraft font marks a mod message");
        check(ServerNotificationTranslator.fromOtherMod(Text.literal("⁤ ›› Successfully connected to WynnAspects!")),
                "WynnAspects arrow prefix");
        check(ServerNotificationTranslator.fromOtherMod(Text.literal("wmd >> Reconnected to Wynnmod Server")), "Wynnmod prefix");
        Text rank = Text.literal("").setStyle(pill).append(Text.literal(" You gained 300 experience points."));
        check(!ServerNotificationTranslator.fromOtherMod(rank), "Wynncraft's own badges stay");
        check(!ServerNotificationTranslator.fromOtherMod(Text.literal("The war for Detlas will start in 2 minutes.")),
                "Plain server message stays");
    }

    private static void accepted(String text) { check(ServerNotificationTranslator.eligible(text,false),text); }
    private static void rejected(String text) { check(!ServerNotificationTranslator.eligible(text,false),text); }
    private static void check(boolean value,String label) { if(!value) throw new AssertionError(label); }
}
