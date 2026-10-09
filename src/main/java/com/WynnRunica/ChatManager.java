package com.WynnRunica;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import java.awt.Color;

public class ChatManager {

    private static final String SEND_URL = "https://transcript.shyutarque.site/hub/chat/send";
    private static final String PRESENCE_URL = "https://transcript.shyutarque.site/hub/chat/presence";
    private static final String MESSAGES_URL = "https://transcript.shyutarque.site/hub/chat/messages";
    private static final String AUTH_START_URL = "https://transcript.shyutarque.site/hub/chat/auth/start";
    private static final String AUTH_FINISH_URL = "https://transcript.shyutarque.site/hub/chat/auth/finish";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "WynnRunica-ChatWorker");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicBoolean IS_POLLING = new AtomicBoolean(false);
    private static final Set<Long> SEEN_IDS = Collections.synchronizedSet(new HashSet<>());
    private static long lastMessageId = 0;
    private static boolean initialized = false;
    private static volatile boolean joined;
    private static volatile String token;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        SCHEDULER.scheduleWithFixedDelay(ChatManager::pollMessages, 1, 1, TimeUnit.SECONDS);
        SCHEDULER.scheduleWithFixedDelay(ChatManager::sendPresence, 20, 20, TimeUnit.SECONDS);
    }

    public static void sendMessage(String text) {
        if (text == null || text.isBlank()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        JsonObject message = new JsonObject();
        message.addProperty("text", text.length() > 600 ? text.substring(0, 600) : text);

        SCHEDULER.execute(() -> {
            try {
                if (token == null && !authenticate()) {
                    notice(client, "§cНе удалось подтвердить аккаунт Minecraft для общего чата.");
                    return;
                }
                HttpResponse<String> res = post(SEND_URL, message, token);
                if (res.statusCode() == 401 && authenticate()) res = post(SEND_URL, message, token);
                int code = res.statusCode();
                if (code == 200) pollMessages();
                if (code == 403) notice(client, "§cВы заблокированы в общем чате.");
                if (code == 429) notice(client, "§cСлишком часто! Подождите немного.");
            } catch (Exception ignored) {
            }
        });
    }

    private static void notice(MinecraftClient client, String text) {
        client.execute(() -> {
            if (client.player != null) client.inGameHud.getChatHud().addMessage(Text.literal("[§3Wynn§fRunica] " + text));
        });
    }

    // Подтверждает личность так же, как при входе на сервер Minecraft: игра сообщает Mojang
    // случайный код от хаба, хаб сверяет его у Mojang и выдаёт пропуск в общий чат.
    // Токен аккаунта уходит только в Mojang, на хаб он не попадает.
    private static boolean authenticate() {
        MinecraftClient client = MinecraftClient.getInstance();
        var session = client.getSession();
        if (session.getUuidOrNull() == null) return false;
        try {
            JsonObject start = new JsonObject();
            start.addProperty("name", session.getUsername());
            HttpResponse<String> first = post(AUTH_START_URL, start, null);
            if (first.statusCode() != 200) return false;
            String serverId = JsonParser.parseString(first.body()).getAsJsonObject().get("serverId").getAsString();

            client.getApiServices().sessionService()
                    .joinServer(session.getUuidOrNull(), session.getAccessToken(), serverId);

            JsonObject finish = new JsonObject();
            finish.addProperty("serverId", serverId);
            HttpResponse<String> second = post(AUTH_FINISH_URL, finish, null);
            if (second.statusCode() != 200) return false;
            token = JsonParser.parseString(second.body()).getAsJsonObject().get("token").getAsString();
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    private static HttpResponse<String> post(String url, JsonObject body, String chatToken) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(Duration.ofSeconds(8))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        if (chatToken != null) request.header("X-Chat-Token", chatToken);
        return HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public static void pollMessages() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !Config.isEnabled("Общий чат")) return;
        if (!IS_POLLING.compareAndSet(false, true)) return;

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(MESSAGES_URL + "?after=" + lastMessageId))
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();

            HttpResponse<String> res = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200) return;

            JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
            JsonArray msgs = json.getAsJsonArray("messages");
            if (msgs == null || msgs.isEmpty()) return;

            if (lastMessageId == 0) {
                for (JsonElement el : msgs) {
                    if (!el.isJsonObject()) continue;
                    long id = el.getAsJsonObject().has("id") ? el.getAsJsonObject().get("id").getAsLong() : 0;
                    if (id > lastMessageId) lastMessageId = id;
                    SEEN_IDS.add(id);
                }
                return;
            }

            for (JsonElement el : msgs) {
                if (!el.isJsonObject()) continue;
                JsonObject msgObj = el.getAsJsonObject();
                long id = msgObj.has("id") ? msgObj.get("id").getAsLong() : 0;
                if (id <= 0 || !SEEN_IDS.add(id)) continue;
                if (id > lastMessageId) lastMessageId = id;

                Text formatted = formatMessage(msgObj);
                client.execute(() -> {
                    if (client.player != null && Config.isEnabled("Общий чат")) {
                        client.inGameHud.getChatHud().addMessage(formatted);
                    }
                });
            }
        } catch (Exception ignored) {
        } finally {
            IS_POLLING.set(false);
        }
    }

    static Text formatMessage(JsonObject obj) {
        String sender = obj.has("sender_name") ? plain(obj.get("sender_name").getAsString()) : "Unknown";
        String content = obj.has("text") ? plain(obj.get("text").getAsString()) : "";
        JsonElement badge = obj.get("badge");
        MutableText root = Text.literal("[§3Wynn§fRunica] ");
        root.append(ChatManager.formatSender(sender,
                badge != null && badge.isJsonObject() ? badge.getAsJsonObject() : null));
        return root.append(Text.literal("§7: §f" + content));
    }

    public static void join(MinecraftClient client) {
        joined = true;
        SCHEDULER.execute(ChatManager::sendPresence);
    }

    private static void sendPresence() {
        if (!joined) return;
        try {
            if (token == null && !authenticate()) return;
            if (post(PRESENCE_URL, new JsonObject(), token).statusCode() == 401) token = null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {}
    }

    public static void reset() {
        joined = false;
        lastMessageId = 0;
        SEEN_IDS.clear();
    }

    // Чужой текст показывается без цветовых кодов и переносов строк.
    private static String plain(String value) {
        return value.replaceAll("§.?", "").replaceAll("\\p{Cntrl}", " ");
    }

    public static Text formatSender(String name, JsonObject badge) {
        if (badge == null) return Text.literal(name).formatted(Formatting.GOLD);
        MutableText result = Text.empty();
        String prefix = string(badge, "prefix", "");
        if (!prefix.isBlank()) {
            result.append(styled(prefix, badge, "prefix", 0xA855F7, 0x0EB6DA));
            result.append(Text.literal(" "));
        }
        if (string(badge, "name_type", "default").equalsIgnoreCase("default")) {
            result.append(Text.literal(name).setStyle(Style.EMPTY.withFormatting(Formatting.GOLD)
                    .withBold(flag(badge, "name_bold"))));
        } else {
            result.append(styled(name, badge, "name", 0xFFFFFF, 0x4EF440));
        }
        return result;
    }

    private static Text styled(String text, JsonObject badge, String field, int first, int second) {
        String type = string(badge, field + "_type", "static");
        int c1 = color(string(badge, field + "_c1", ""), first);
        int c2 = color(string(badge, field + "_c2", ""), second);
        Style style = Style.EMPTY.withBold(flag(badge, field + "_bold"))
                .withItalic(field.equals("prefix") && flag(badge, "prefix_italic"));
        if (type.equalsIgnoreCase("static")) return Text.literal(text).setStyle(style.withColor(c1));
        int[] codePoints = text.codePoints().toArray();
        double phase = (System.currentTimeMillis() % 2500) / 2500.0;
        MutableText result = Text.empty();
        for (int i = 0; i < codePoints.length; i++) {
            double position = (double) i / Math.max(1, codePoints.length - 1);
            int rgb;
            if (type.equalsIgnoreCase("chroma")) {
                float hue = (float) ((double) i / Math.max(1, codePoints.length) - phase);
                rgb = Color.HSBtoRGB(hue - (float) Math.floor(hue), 0.85f, 1) & 0xFFFFFF;
            } else {
                double amount = type.equalsIgnoreCase("wave")
                        ? (Math.sin(2 * Math.PI * (position - phase)) + 1) / 2 : position;
                rgb = blend(c1, c2, amount);
            }
            result.append(Text.literal(new String(Character.toChars(codePoints[i]))).setStyle(style.withColor(rgb)));
        }
        return result;
    }

    private static int blend(int first, int second, double amount) {
        int rgb = 0;
        for (int shift : new int[]{16, 8, 0}) {
            int a = first >> shift & 255;
            rgb |= (int) (a + amount * ((second >> shift & 255) - a)) << shift;
        }
        return rgb;
    }

    private static int color(String hex, int fallback) {
        return hex.matches("#?[0-9a-fA-F]{6}") ? Integer.parseInt(hex.replace("#", ""), 16) : fallback;
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : fallback;
    }

    private static boolean flag(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive()
                && ("true".equalsIgnoreCase(value.getAsString()) || "1".equals(value.getAsString()));
    }
}
