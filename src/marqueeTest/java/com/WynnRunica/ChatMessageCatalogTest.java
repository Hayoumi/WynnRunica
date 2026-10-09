package com.WynnRunica;

import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToIntBiFunction;

public final class ChatMessageCatalogTest {
    public static void main(String[] args) throws Exception {
        var file = Files.createTempFile("wynnrunica-chat-", ".json");
        try {
            Files.writeString(file, """
                    {"schemaVersion":2,"domain":"chat-messages","entries":[
                      {"id":"territory","lines":[
                        {"id":"line-001","en":"§b§lTerritory Captured","ru":"§b§lТерритория захвачена"},
                        {"id":"line-002","en":"","ru":""},
                        {"id":"line-003","en":"§7- Captured <territory>","ru":"§7- Захвачено <territory>"},
                        {"id":"line-004","en":"§a<num> Emeralds","ru":"§a<num> изумрудов"}]},
                      {"id":"person","lines":[
                        {"id":"line-001","en":"Hankiro captured a territory","ru":"Hankiro захватил территорию"}]},
                      {"id":"hidden","skip":true,"lines":[
                        {"id":"line-001","en":"<actor> has left the party!","ru":""}]},
                      {"id":"resistance","lines":[
                        {"id":"line-001","en":"<actor> has given you <num>% resistance.","ru":"Вы получили <num>% сопротивления от <actor>."}]},
                      {"id":"crate","lines":[
                        {"id":"line-001","en":"<actor> has gotten a <actor> from their crate! Type /store","ru":"<actor> достаёт из ящика: <actor>! Команда §#FFD750/store"}]},
                      {"id":"bomb","lines":[
                        {"id":"line-001","en":"§7<em><actor> has thrown an","ru":"§7<em><actor> бросает"}]},
                      {"id":"pulls","lines":[
                        {"id":"line-001","en":"[<num> Reward Pulls]","ru":"[<num> попыт<pl:ка|ки|ок> на награду]"}]},
                      {"id":"person-template","lines":[
                        {"id":"line-001","en":"<actor> captured a territory","ru":"<actor> захватил территорию"}]}
                    ]}
                    """, StandardCharsets.UTF_8);
            ChatMessageCatalog.loadFrom(file);
            String source = "§b                   §lTerritory Captured\n \n§7- Captured Abandoned Pass\n§a300 Emeralds";
            String output = translate(source);
            check(output.contains("Территория захвачена"), "styled heading");
            check(output.contains("Abandoned Pass"), "territory variable");
            check(output.contains("300 изумрудов"), "number variable");
            check(output.split("\\n", -1).length == 4, "message layout");
            check(translate("OtherPlayer captured a territory")
                    .equals("OtherPlayer захватил территорию"), "explicit actor slot keeps other player");
            check(translate("Hankiro captured a territory")
                    .equals("Hankiro захватил территорию"), "exact rule wins over actor template");
            check(translate("Nobody has seen this line") == null,
                    "unknown message is reported as unknown");
            check(translate("X has left the party!").equals("X has left the party!"),
                    "a hidden rule keeps the original and still counts as known");
            check(translate("Hanqiru has given you 20% resistance.")
                    .equals("Вы получили 20% сопротивления от Hanqiru."), "slots may change order in the translation");

            String crate = "Hanqiru has gotten a Fire Aura from their crate! Type /store";
            String[] crateCodes = codes(crate, "");
            paint(crateCodes, crate, "Hanqiru", "§#FFFF55");
            paint(crateCodes, crate, " has gotten a ", "§#A0C84B");
            paint(crateCodes, crate, "Fire Aura", "§#DD55FF");
            paint(crateCodes, crate, " from their crate! Type ", "§#A0C84B");
            paint(crateCodes, crate, "/store", "§#FFD750");
            check(ChatMessageCatalog.translate(crate, crateCodes).equals(
                    "§#A0C84B§#FFFF55Hanqiru§#A0C84B достаёт из ящика: §#DD55FFFire Aura§#A0C84B! Команда §#FFD750/store"),
                    "02.10: names and items keep the colours of the original, plain words take the colour of plain words");

            String bomb = "§7<em>Marzzuca_ has thrown an";
            String[] bombCodes = codes(bomb, "§#AAAAAA");
            paint(bombCodes, bomb, "Marzzuca_", "§#FFD750");
            check(ChatMessageCatalog.translate(bomb, bombCodes).equals("§7<em>§#AAAAAA§#FFD750Marzzuca_§#AAAAAA бросает"),
                    "the colour goes after the icon the line starts with");

            check(translate("[+3 Reward Pulls]").equals("[+3 попытки на награду]")
                    && translate("[+21 Reward Pulls]").equals("[+21 попытка на награду]")
                    && translate("[+5 Reward Pulls]").equals("[+5 попыток на награду]"),
                    "02.10: the word after a number takes the Russian form for that number");

            List<String> names = List.of("Hanqiru", "99os", "Al");
            check(ServerNotificationTranslator.chatSaveKey("Hanqiru set XP Seeking bonus to level 9 on Detlas", names)
                    .equals("<actor> set XP Seeking bonus to level <num> on Detlas"), "own name and numbers become slots");
            check(ServerNotificationTranslator.chatSaveKey("99os and Hanqiruu met Al", names)
                    .equals("<actor> and Hanqiruu met Al"), "only whole names of three letters or more");

            var segments = List.of(new TelemetrySender.Segment("Header\nBody", "#55ffff", "minecraft:default",
                    true, false, false, false, false, false));
            var body = ServerNotificationTranslator.splitSegments(segments, 1);
            check(body.size() == 1 && body.getFirst().text().equals("Body")
                    && body.getFirst().bold() && body.getFirst().color().equals("#55ffff"),
                    "multiline capture retains style and order");

            Style red = Style.EMPTY.withColor(0xFF5555);
            Text wrapped = Text.empty()
                    .append(Text.literal(" ").setStyle(red))
                    .append(Text.literal("Hanqiru set XP Seeking bonus to level 9 on Roots\n").setStyle(red))
                    .append(Text.literal(" ").setStyle(red))
                    .append(Text.literal("of Corruption").setStyle(red));
            ChatReflow.Parts parts = ChatReflow.split(wrapped);
            check(parts.body().getString().equals("Hanqiru set XP Seeking bonus to level 9 on Roots of Corruption"),
                    "server line wrap is glued back into one line");
            check(parts.firstPrefix().getString().equals(" ") && parts.nextPrefix().getString().equals(" "),
                    "both markers are kept apart from the text");

            Text spaced = Text.empty()
                    .append(Text.literal(" ").setStyle(red))
                    .append(Text.literal("Watch the trailers at \n").setStyle(red))
                    .append(Text.literal(" ").setStyle(red))
                    .append(Text.literal("wynn.gg/youtube").setStyle(red));
            check(ChatReflow.split(spaced).body().getString().equals("Watch the trailers at wynn.gg/youtube"),
                    "02.10: a line that ends with a space is glued with one space, not two");

            ChatReflow.Parts single =ChatReflow.split(Text.literal("You are already in this area"));
            check(single.nextPrefix() == null && single.body().getString().equals("You are already in this area"),
                    "a plain line is one paragraph without markers");
            check(ChatReflow.split(Text.literal("Territory Captured\n\n- Captured Detlas")) == null,
                    "a block without markers stays line by line");

            Text ru = Text.literal("Эта территория на перезарядке! Пожалуйста, подождите 49 секунд прежде чем атаковать.")
                    .setStyle(red);
            ToIntBiFunction<String, Style> six = (text, style) -> text.codePointCount(0, text.length()) * 6;
            String[] flowed = ChatReflow.join(parts, ru, 240, six).getString().split("\n");
            check(flowed.length >= 3 && flowed[0].startsWith(" "), "first line keeps its icon");
            check(Arrays.stream(flowed).skip(1).allMatch(line -> line.startsWith(" ")),
                    "every continuation line gets the marker");
            check(Arrays.stream(flowed).allMatch(line -> line.length() * 6 <= 240), "every line fits");
            check(String.join(" ", Arrays.stream(flowed).map(line -> line.substring(2)).toList()).equals(ru.getString()),
                    "no word is lost or split");
            check(ChatReflow.join(parts, ru, 1000, six).getString().equals(" " + ru.getString()),
                    "a translation that fits stays on one line");
            Text title = Text.literal(" ".repeat(34) + "Level Up!\n" + " ".repeat(12) + "- Reward");
            Text titleRu = Text.literal(" ".repeat(34) + "Новый уровень!\n" + " ".repeat(12) + "- Награда");
            ToIntBiFunction<String, Style> cell = (text, style) -> {
                int total = 0;
                for (int i = 0; i < text.length(); ) {
                    int codePoint = text.codePointAt(i);
                    i += Character.charCount(codePoint);
                    if (codePoint >= 0xC0000 && codePoint <= 0xDFFFF) total += codePoint - 0xD0000;
                    else total += codePoint == ' ' ? 4 : 6;
                }
                return total;
            };
            String[] centered = ChatReflow.recenter(titleRu, title, cell).getString().split("\n");
            check(cell.applyAsInt(centered[0], Style.EMPTY) - cell.applyAsInt("Новый уровень!", Style.EMPTY) / 2
                    == 136 + cell.applyAsInt("Level Up!", Style.EMPTY) / 2,
                    "02.10: a centred line keeps its middle when the translation is longer");
            check(centered[1].equals(" ".repeat(12) + "- Награда"), "an indented list line is not moved");
            ChatCardLayoutTest.run();
            System.out.println("Independent chat catalog checks passed");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static String translate(String message) {
        return ChatMessageCatalog.translate(message, codes(message, ""));
    }

    private static String[] codes(String message, String code) {
        String[] codes = new String[message.length()];
        Arrays.fill(codes, code);
        return codes;
    }

    private static void paint(String[] codes, String message, String part, String code) {
        int start = message.indexOf(part);
        Arrays.fill(codes, start, start + part.length(), code);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
