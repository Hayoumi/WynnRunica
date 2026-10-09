package com.WynnRunica;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ServerNotificationTranslator {
    private static final Gson GSON = new Gson();

    private static final Pattern NUMBER_OR_CODE = Pattern.compile(
            "§(?:#[0-9a-fA-F]{6}|[0-9a-fA-FklmnorKLMNOR])|(?<number>[+\\-]?\\d+(?:[.,/]\\d+)*)");
    private static final Pattern INVISIBLE = Pattern.compile(
            "§(?:#[0-9a-fA-F]{6,8}|.)|[\\uE000-\\uF8FF\\x{C0000}-\\x{10FFFF}]|\\p{Cf}");
    private static final Pattern PLAYER_COMMAND = Pattern.compile(
            "/(?:msg|tell|w|r|reply|p|party|g|guild)(?:\\s.*)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENGLISH = Pattern.compile("[A-Za-z]{2,}");
    private static final Pattern PLAYER_PREFIX = Pattern.compile("(?s)^.{1,120}?(?::|»|➜|→)\\s+.*");
    private static final Pattern SYSTEM_PREFIX = Pattern.compile("(?i)^(?:quest (?:started|completed|updated)|new quest|objective|reward|warning|error|tip|hint|event|server|information|you (?:received|gained|found|lost)|your .{1,40})\\s*:");
    private static final Pattern PLAYER_SPEECH = Pattern.compile("(?i)^[A-Za-z0-9_ ]{3,40} (?:says|shouts|whispers)(?::|\\s).*");
    private static final Pattern CONNECTION_STATUS = Pattern.compile(
            "(?i)^(?:[›»]{1,2}\\s*)?(?:trying to connect to ping server\\.{0,3}|connected to ping server!?"
            + "|successfully connected to the remote player server\\.|disconnected from the remote player server\\."
            + "|successfully co)$");

    private record Run(String text, Style style) {}
    private record Link(int start, int end, Style style) {}

    private ServerNotificationTranslator() {}

    public static Text receive(Text message, boolean overlay) {
        boolean translate = Config.isEnabled("Уведомления чата");
        boolean send = Config.isEnabled("Отправка строк");
        if (overlay || message == null || !onWynncraft() || (!translate && !send)
                || fromOtherMod(message) || fromPlayer(message)) {
            ChatCaptureContext.boundary();
            return message;
        }

        ChatReflow.Parts parts = ChatReflow.split(message);
        Text source = message;
        if (parts != null) source = parts.body();
        var extracted = TextEmojiUtils.extract(source);
        String key = extracted.key;
        if (key.isBlank() || key.length() > 2048) {
            ChatCaptureContext.boundary();
            return message;
        }

        Style base = extracted.contentStyle;
        String translation = ChatMessageCatalog.translate(key, colorCodes(extracted.styles));
        if (translation != null) {
            base = base.withColor((TextColor) null).withBold(false).withItalic(false)
                    .withUnderline(false).withStrikethrough(false);
        }
        if (translation == null) translation = questTitle(key);
        if (translation == null) {
            if (!eligible(message.getString(), false)) {
                ChatCaptureContext.boundary();
            } else if (send) {
                capture(parts, source, key);
            }
            return message;
        }
        ChatCaptureContext.boundary();
        if (!translate || translation.equals(key)) return message;

        Text rebuilt = ChatReflow.hasCardColumns(message)
                ? TextEmojiUtils.rebuildChat(translation, extracted.icons, base, extracted.key)
                : TextEmojiUtils.rebuild(translation, extracted.icons, base, extracted.key);
        Text withLinks = restoreLinks(rebuilt, source);
        if (withLinks == null) return message;
        Text result = withLinks;
        if (parts != null) result = ChatReflow.join(parts, withLinks);
        result = ChatReflow.recenter(result, message);
        logLayout(result, message);
        return result;
    }

    private static void logLayout(Text result, Text original) {
        if (!Config.DEBUG) return;
        try {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("WynnRunica/chat-layout.log");
            if (Files.exists(file) && Files.size(file) > 512 * 1024) Files.delete(file);
            Files.writeString(file, ChatReflow.describeLayout(result, original) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) {
            System.out.println("[WynnRunica] журнал вёрстки чата не записан: " + error.getMessage());
        }
    }

    static String[] colorCodes(List<Style> styles) {
        String[] codes = new String[styles.size()];
        for (int i = 0; i < codes.length; i++) {
            Style style = styles.get(i);
            if (i > 0 && style == styles.get(i - 1)) {
                codes[i] = codes[i - 1];
                continue;
            }
            String code = "";
            if (style.getColor() != null) code = String.format("§#%06X", style.getColor().getRgb() & 0xFFFFFF);
            if (style.isBold()) code += "§l";
            if (style.isItalic()) code += "§o";
            if (style.isUnderlined()) code += "§n";
            if (style.isStrikethrough()) code += "§m";
            codes[i] = code;
        }
        return codes;
    }

    static String questTitle(String key) {
        String name = INVISIBLE.matcher(key).replaceAll("").strip();
        String banner = TranslationManager.translations.get(TranslationManager.lookupKey("New Quest Started: " + name));
        if (name.isEmpty() || banner == null || !banner.contains("§#FFFF55")) return null;
        return key.replace(name, banner.substring(banner.lastIndexOf("§#FFFF55") + "§#FFFF55".length()));
    }

    static boolean onWynncraft() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return false;
        var server = client.getCurrentServerEntry();
        if (server == null) return false;
        String host = server.address.toLowerCase(Locale.ROOT).split(":", 2)[0];
        return host.equals("wynncraft.com") || host.endsWith(".wynncraft.com");
    }

    private static boolean fromPlayer(Text node) {
        Style style = node.getStyle();
        if (style.getClickEvent() instanceof ClickEvent.SuggestCommand suggest
                && PLAYER_COMMAND.matcher(suggest.command()).matches()) return true;
        if (style.getHoverEvent() instanceof HoverEvent.ShowEntity) return true;
        for (Text sibling : node.getSiblings()) {
            if (fromPlayer(sibling)) return true;
        }
        return false;
    }

    static boolean fromOtherMod(Text message) {
        StringBuilder badge = new StringBuilder();
        for (Run run : runs(message)) {
            if (!(run.style().getFont() instanceof StyleSpriteSource.Font font)) continue;
            String id = font.id().toString();
            if (!id.startsWith("minecraft:")) return true;
            if (!id.equals("minecraft:banner/pill")) continue;
            for (int i = 0; i < run.text().length(); i++) {
                char c = run.text().charAt(i);
                if (c >= 0xE000 && c <= 0xE019) badge.append((char) ('A' + c - 0xE000));
            }
        }
        if (badge.toString().startsWith("WYNN")) return true;
        String plain = INVISIBLE.matcher(message.getString()).replaceAll("").strip();
        return plain.startsWith("››") || plain.startsWith("wmd ") || plain.startsWith("♦");
    }

    static Text restoreLinks(Text rebuilt, Text source) {
        String visible = rebuilt.getString();
        List<Link> links = new ArrayList<>();
        Style wholeMessage = null;
        List<Run> linked = new ArrayList<>();
        for (Run run : runs(source)) {
            if (run.style().getClickEvent() != null && !run.text().isBlank()) linked.add(run);
        }
        linked.sort((a, b) -> b.text().length() - a.text().length());
        for (Run run : linked) {
            ClickEvent click = run.style().getClickEvent();
            int places = 0;
            int start = -1;
            for (int at = visible.indexOf(run.text()); at >= 0; at = visible.indexOf(run.text(), at + 1)) {
                boolean taken = false;
                for (Link other : links) {
                    if (at < other.end() && at + run.text().length() > other.start()) taken = true;
                }
                if (taken) continue;
                places++;
                start = at;
            }
            if (places == 1) {
                links.add(new Link(start, start + run.text().length(), run.style()));
                continue;
            }
            if (click instanceof ClickEvent.OpenUrl) return null;
            if (wholeMessage != null && !click.equals(wholeMessage.getClickEvent())) return null;
            wholeMessage = run.style();
        }
        if (wholeMessage != null) {
            Style clickable = Style.EMPTY.withClickEvent(wholeMessage.getClickEvent())
                    .withHoverEvent(wholeMessage.getHoverEvent());
            rebuilt = Text.empty().setStyle(clickable).append(rebuilt);
        }
        if (links.isEmpty()) return rebuilt;

        MutableText result = Text.literal("");
        int offset = 0;
        for (Run run : runs(rebuilt)) {
            String text = run.text();
            int from = 0;
            while (from < text.length()) {
                Link link = linkAt(links, offset + from);
                int to = from + 1;
                while (to < text.length() && linkAt(links, offset + to) == link) to++;

                Style style = run.style();
                if (link != null) {
                    style = style.withClickEvent(link.style().getClickEvent())
                            .withHoverEvent(link.style().getHoverEvent());
                    if (link.style().getColor() != null) style = style.withColor(link.style().getColor());
                }
                result.append(Text.literal(text.substring(from, to)).setStyle(style));
                from = to;
            }
            offset += text.length();
        }
        return result;
    }

    private static Link linkAt(List<Link> links, int position) {
        for (Link link : links) {
            if (position >= link.start() && position < link.end()) return link;
        }
        return null;
    }

    private static List<Run> runs(Text text) {
        List<Run> runs = new ArrayList<>();
        text.visit((style, value) -> {
            if (!value.isEmpty()) runs.add(new Run(value, style));
            return Optional.empty();
        }, Style.EMPTY);
        return runs;
    }

    private static void capture(ChatReflow.Parts parts, Text source, String key) {
        var segments = TelemetrySender.serialize(source);
        if (segments.size() > 256) return;
        List<String> names = knownNames();

        JsonArray lines = new JsonArray();
        StringBuilder family = new StringBuilder("chat");
        String[] keys = key.split("\n", -1);
        String[] texts = source.getString().split("\n", -1);
        for (int i = 0; i < keys.length; i++) {
            String saveKey = chatSaveKey(keys[i], names);
            family.append('\u001f').append(saveKey);
            JsonObject line = new JsonObject();
            line.addProperty("text", hideNames(i < texts.length ? texts[i] : keys[i], names));
            line.addProperty("key", hideNames(keys[i], names));
            line.addProperty("saveKey", saveKey);
            List<TelemetrySender.Segment> shown = new ArrayList<>();
            for (var segment : splitSegments(segments, i)) {
                shown.add(new TelemetrySender.Segment(hideNames(segment.text(), names), segment.color(), segment.font(),
                        segment.bold(), segment.italic(), segment.underlined(),
                        segment.strikethrough(), segment.obfuscated(), segment.icon()));
            }
            line.add("segments", GSON.toJsonTree(shown));
            if (i == 0 && parts != null) {
                line.add("prefixSegments", GSON.toJsonTree(TelemetrySender.serialize(parts.firstPrefix())));
            }
            lines.add(line);
        }

        StringBuilder structure = new StringBuilder(family);
        for (var segment : segments) {
            structure.append('\u001f').append(chatSaveKey(segment.text(), names))
                    .append('|').append(segment.color())
                    .append('|').append(segment.font())
                    .append('|').append(segment.bold())
                    .append('|').append(segment.italic())
                    .append('|').append(segment.underlined())
                    .append('|').append(segment.strikethrough())
                    .append('|').append(segment.obfuscated())
                    .append('|').append(segment.icon());
        }

        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("kind", "chat_message");
        snapshot.addProperty("messageId", hash(structure.toString()));
        snapshot.addProperty("familyId", hash(family.toString()));
        snapshot.addProperty("source", "server_message");
        snapshot.add("lines", lines);
        TelemetrySender.recordChatMessage(snapshot);
        ChatCaptureContext.record(snapshot);
    }

    static List<String> knownNames() {
        MinecraftClient client = MinecraftClient.getInstance();
        List<String> names = new ArrayList<>();
        names.add(client.getSession().getUsername());
        if (client.getNetworkHandler() != null) {
            for (var player : client.getNetworkHandler().getPlayerList()) {
                names.add(player.getProfile().name());
            }
        }
        return names;
    }

    static List<TelemetrySender.Segment> splitSegments(List<TelemetrySender.Segment> segments, int wanted) {
        List<TelemetrySender.Segment> result = new ArrayList<>();
        int line = 0;
        for (var segment : segments) {
            String[] parts = segment.text().split("\n", -1);
            for (int i = 0; i < parts.length; i++) {
                if (line == wanted && !parts[i].isEmpty()) {
                    result.add(new TelemetrySender.Segment(parts[i], segment.color(), segment.font(),
                            segment.bold(), segment.italic(), segment.underlined(),
                            segment.strikethrough(), segment.obfuscated(), segment.icon()));
                }
                if (i < parts.length - 1) line++;
            }
        }
        return result;
    }

    static String chatSaveKey(String text, List<String> names) {
        return normalizeNumbers(hideNames(ChatMessageCatalog.normalizeLayout(text), names));
    }

    static String hideNames(String text, List<String> names) {
        for (String name : names) {
            if (name.length() < 3 || !text.contains(name)) continue;
            text = text.replaceAll("(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])", "<actor>");
        }
        return text;
    }

    public static String normalizeNumbers(String text) {
        return NUMBER_OR_CODE.matcher(text).replaceAll(match -> {
            if (match.group("number") == null) return match.group();
            int word = match.start();
            while (word > 0 && (Character.isLetterOrDigit(text.charAt(word - 1)) || text.charAt(word - 1) == '_')) word--;
            boolean insideWord = word < match.start() && Character.isLetter(text.charAt(word))
                    && match.end() < text.length() && Character.isLetter(text.charAt(match.end()));
            if (insideWord) return match.group();
            return "<num>";
        });
    }

    public static boolean eligible(String message, boolean fromPlayer) {
        if (message == null || message.isBlank() || message.length() > 2048 || fromPlayer) return false;
        String plain = INVISIBLE.matcher(message).replaceAll("").strip();
        if (!ENGLISH.matcher(plain).find() || plain.startsWith("[WynnRunica")) return false;
        if (CONNECTION_STATUS.matcher(plain).matches()) return false;
        if (PLAYER_SPEECH.matcher(plain).matches()) return false;
        if (plain.matches("(?s)^<[^>]+>\\s+.*") || plain.startsWith("From ") || plain.startsWith("To ")) return false;
        if (plain.contains(" shouts:") || plain.contains(" whispers:") || plain.contains(" says:")) return false;
        return !PLAYER_PREFIX.matcher(plain).matches() || SYSTEM_PREFIX.matcher(plain).find();
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
