package com.WynnRunica;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class ChatBadgeFormatterTest {
    public static void main(String[] args) {
        JsonObject badge = JsonParser.parseString("""
                {"prefix":"[BOSS]","prefix_type":"static","prefix_c1":"#A855F7",
                 "prefix_bold":1,"prefix_italic":true,"name_type":"static",
                 "name_c1":"#22C55E","name_bold":true}
                """).getAsJsonObject();
        Text sender = ChatManager.formatSender("Alex", badge);
        expect(sender.getString().equals("[BOSS] Alex"), "Prefix appears once before the sender");
        List<Style> styles = styles(sender);
        expect(styles.getFirst().getColor().getRgb() == 0xA855F7, "Prefix uses Hub color");
        expect(styles.getFirst().isBold() && styles.getFirst().isItalic(), "Prefix flags survive numeric booleans");
        expect(styles.getLast().getColor().getRgb() == 0x22C55E && styles.getLast().isBold(), "Name uses Hub color and bold");

        expect(ChatManager.formatSender("Alex", null).getString().equals("Alex"),
                "Removing the badge does not reuse an earlier prefix");
        JsonObject gradient = JsonParser.parseString("""
                {"name_type":"gradient","name_c1":"#FF0000","name_c2":"#0000FF"}
                """).getAsJsonObject();
        Text unicode = ChatManager.formatSender("A🧡Б", gradient);
        expect(unicode.getString().equals("A🧡Б"), "Do not split supplementary glyphs");
        List<Style> colors = styles(unicode);
        expect(colors.size() == 3, "One gradient step per Unicode code point");
        expect(colors.getFirst().getColor().getRgb() == 0xFF0000
                && colors.getLast().getColor().getRgb() == 0x0000FF, "Gradient endpoints follow Hub values");

        JsonObject malformed = JsonParser.parseString("""
                {"prefix":null,"name_type":"static","name_c1":"#FFFFFFFF","name_bold":{}}
                """).getAsJsonObject();
        expect(ChatManager.formatSender("Alex", malformed).getString().equals("Alex"),
                "Optional malformed fields cannot drop the sender");
        JsonObject message = new JsonObject();
        message.addProperty("sender_name", "Alex");
        message.addProperty("text", "Alex met Alexander: [BOSS] Alex");
        message.add("badge", badge);
        Text formatted = ChatManager.formatMessage(message);
        expect(formatted.getString().endsWith("§7: §fAlex met Alexander: [BOSS] Alex"),
                "Message contents and mentions remain untouched");
        message.remove("badge");
        expect(ChatManager.formatMessage(message).getString().contains("Alex§7: §f"),
                "Message without badge uses plain sender without cached styling");
        System.out.println("Common chat badge checks passed");
    }

    private static List<Style> styles(Text text) {
        List<Style> result = new ArrayList<>();
        text.visit((style, value) -> {
            if (!value.isEmpty()) result.add(style);
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private static void expect(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
}
