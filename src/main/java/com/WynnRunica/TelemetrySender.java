package com.WynnRunica;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

public final class TelemetrySender {
    public record Segment(String text, String color, String font, boolean bold,
                          boolean italic, boolean underlined, boolean strikethrough,
                          boolean obfuscated, boolean icon) {}

    private record TelemetryItem(
            String cat,
            String en,
            String speaker,
            String quest,
            String questId,
            String stage,
            String kind,
            String choiceSetId,
            int choiceIndex,
            String parentOriginal,
            String screen,
            String sessionId,
            String playerUuid,
            List<Segment> segments,
            String entryId,
            String ru,
            int sourceColorRuns,
            int sourceIcons,
            String captureId
    ) {}

    private static final Gson GSON = new Gson();
    private static final Pattern HAS_WORD = Pattern.compile("\\p{L}{2,}");
    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("(?<!§)[+\\-]?\\d+(?:[.,/]\\d+)*");
    private static final Pattern COUNTER_PATTERN =
            Pattern.compile("\\[\\d+/\\d+\\]");
    private static final Pattern COORD_PATTERN =
            Pattern.compile("\\(-?\\d+,\\s*-?\\d+\\)");
    private static final Pattern CLEAN_DECOR =
            Pattern.compile("§(?:#[0-9a-fA-F]{6}|.)|[\\uE000-\\uF8FF\\x{C0000}-\\x{10FFFF}]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final ConcurrentLinkedQueue<TelemetryItem> QUEUE = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<JsonObject> SNAPSHOT_QUEUE = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<JsonObject> CHAT_QUEUE = new ConcurrentLinkedQueue<>();
    private static final Set<String> SESSION_CAPTURE_IDS = ConcurrentHashMap.newKeySet();
    private static final String SESSION_ID = UUID.randomUUID().toString();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();
    private static final String HUB = "https://transcript.shyutarque.site/hub";
    private static final String CLIENT_VERSION = "WynnRunica/" + FabricLoader.getInstance()
            .getModContainer("wynn_runica")
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("2.0.0");

    private static final Object FLUSH_LOCK = new Object();
    private static volatile PendingDialogue pendingDialogue;
    private static volatile boolean workerStarted;
    private static volatile long retryAfterNanos;
    private static long nextRequestAtNanos;

    private record PendingDialogue(String text, String translation, String speaker,
                                   String quest, String stage, Text visual) {
        boolean isSame(String otherText, String otherTranslation, String otherSpeaker,
                       String otherQuest, String otherStage) {
            return text.equals(otherText) && speaker.equals(otherSpeaker)
                    && quest.equals(otherQuest) && stage.equals(otherStage)
                    && translation.equals(otherTranslation);
        }
    }

    private TelemetrySender() {}

    // Строки собираются только на Wynncraft. На другом сервере и в одиночной игре мод
    // ничего не отправляет: там подсказки и чат к переводу не относятся и могут быть личными.
    static boolean allowed() {
        return Config.isEnabled("Отправка строк") && ServerNotificationTranslator.onWynncraft();
    }

    private static void recordDialogue(String rawText, String rawSpeaker, String rawQuest,
                                       String rawStage, Text visual) {
        if (!allowed() || rawText == null || rawText.isBlank()) return;

        String norm = normalize(rawText, true);
        if (!isUsefulText(norm)) return;

        String speaker = rawSpeaker == null ? "" : singleLine(rawSpeaker);
        String quest = rawQuest == null ? "" : questName(rawQuest);
        String stage = rawStage == null ? "" : singleLine(rawStage);
        String possibleQuest = "";
        QuestTracker.QuestInfo qInfo = QuestTracker.detect();
        String tracked = qInfo.name();
        if (quest.isEmpty()) {
            if (!speaker.isEmpty() && !tracked.isEmpty() && TranslationManager.isSpeakerInQuest(tracked, speaker)) {
                quest = tracked;
                if (stage.isEmpty()) stage = qInfo.stage();
            } else if (!tracked.isEmpty()) {
                possibleQuest = tracked;
            }
        } else if (!speaker.isEmpty() && !TranslationManager.isSpeakerInQuest(quest, speaker)) {
            possibleQuest = quest;
            quest = "";
            stage = "";
        }
        if (!quest.isEmpty() && TranslationManager.hasExactTranslationInContext(norm, quest, "dialogue")) return;
        if (quest.isEmpty() && TranslationManager.hasExactTranslation(norm)) return;
        String questId = TranslationManager.getQuestId(quest);
        String captureId = captureHash("quests", "dialogue", questId, stage, speaker, "", "", possibleQuest, List.of(), norm);
        if (!SESSION_CAPTURE_IDS.add(captureId)) return;

        List<Segment> segments = dialogueSegments(visual);
        QUEUE.add(new TelemetryItem("quests", norm, speaker, quest, questId, stage, "dialogue", "", 0, "", "",
                SESSION_ID, getPlayerUuid(), segments, "", "", 0, 0, captureId));

        ensureWorkerStarted();
        triggerFlush();
    }

    public static void recordChoice(String rawText, String rawSpeaker, String rawQuest,
                                     String rawStage, Text visual, String rawChoiceSet, int choiceIndex) {
        if (!allowed() || rawText == null || rawText.isBlank()) return;

        String norm = normalize(rawText, true);
        if (!isUsefulText(norm)) return;

        String speaker = rawSpeaker == null ? "" : singleLine(rawSpeaker);
        String quest = rawQuest == null ? "" : questName(rawQuest);
        String stage = rawStage == null ? "" : singleLine(rawStage);
        if (quest.isEmpty()) {
            QuestTracker.QuestInfo qInfo = QuestTracker.detect();
            quest = questName(qInfo.name());
            if (stage.isEmpty()) stage = qInfo.stage();
        }
        if (TranslationManager.hasExactTranslationInContext(norm, quest, "choice")) return;
        String questId = TranslationManager.getQuestId(quest);
        String parentOriginal = rawChoiceSet == null ? "" : normalize(rawChoiceSet, true);
        String choiceSetId = parentOriginal.isBlank()
                ? captureHash("quests", "choice-set", questId, stage, speaker, "", "", "", List.of(), norm)
                : khash("choice-set\u001e" + questId + "\u001e" + stage + "\u001e" + parentOriginal);
        String captureId = khash("choice\u001e" + questId + "\u001e" + stage
                + "\u001e" + choiceSetId + "\u001e" + choiceIndex + "\u001e" + norm);
        if (!SESSION_CAPTURE_IDS.add(captureId)) return;

        QUEUE.add(new TelemetryItem("quests", norm, "", quest, questId, stage, "choice", choiceSetId, Math.max(0, choiceIndex), parentOriginal, "",
                SESSION_ID, getPlayerUuid(), dialogueSegments(visual), "", "", 0, 0, captureId));

        ensureWorkerStarted();
        triggerFlush();
    }

    public static void recordObjective(String rawText, String rawQuest, String rawStage, Text visual) {
        if (!allowed() || rawText == null || rawText.isBlank()) return;

        String norm = normalize(rawText, true);
        if (!isUsefulText(norm)) return;
        String quest = rawQuest == null ? "" : questName(rawQuest);
        String stage = rawStage == null ? "" : singleLine(rawStage);
        if (quest.isEmpty()) {
            QuestTracker.QuestInfo qInfo = QuestTracker.detect();
            quest = questName(qInfo.name());
            if (stage.isEmpty()) stage = qInfo.stage();
        }
        if (TranslationManager.hasExactTranslationInContext(norm, quest, "objective")) return;

        String questId = TranslationManager.getQuestId(quest);
        String captureId = captureHash("quests", "objective", questId, stage, "", "", "", "", List.of(), norm);
        if (!SESSION_CAPTURE_IDS.add(captureId)) return;
        QUEUE.add(new TelemetryItem("quests", norm, "", quest, questId, stage, "objective", "", 0, "", "",
                SESSION_ID, getPlayerUuid(), dialogueSegments(visual), "", "", 0, 0, captureId));
        ensureWorkerStarted();
        triggerFlush();
    }

    public static void observeDialogue(String rawText, String rawTranslation,
                                       String rawSpeaker, String rawQuest,
                                       String rawStage, Text visual, boolean complete) {
        if (!allowed() || !complete || rawText == null || rawText.isBlank()) return;
        String text = rawText.trim();
        String translation = rawTranslation == null ? "" : rawTranslation;
        String speaker = rawSpeaker == null ? "" : singleLine(rawSpeaker);
        String quest = rawQuest == null ? "" : questName(rawQuest);
        String stage = rawStage == null ? "" : singleLine(rawStage);
        PendingDialogue previous = pendingDialogue;
        if (previous != null && previous.isSame(text, translation, speaker, quest, stage)
                && java.util.Objects.equals(visual, previous.visual())) return;
        pendingDialogue = new PendingDialogue(text, translation, speaker, quest, stage,
                visual == null ? null : visual.copy());
        dispatchDialogue(pendingDialogue);
    }

    private static void dispatchDialogue(PendingDialogue pending) {
        if (pending.translation().isEmpty()) {
            recordDialogue(pending.text(), pending.speaker(), pending.quest(), pending.stage(),
                    pending.visual());
        } else {
            recordDialogueFormatting(pending.text(), pending.translation(), pending.speaker(),
                    pending.quest(), pending.stage(), pending.visual());
        }
    }

    private static void recordDialogueFormatting(String rawText, String rawTranslation,
                                                String rawSpeaker, String rawQuest,
                                                String rawStage, Text visual) {
        if (!allowed() || rawText == null || rawText.isBlank()
                || rawTranslation == null || rawTranslation.isBlank()) return;
        String norm = normalize(rawText, true);
        String quest = rawQuest == null ? "" : questName(rawQuest);
        if (quest.isEmpty()) return;
        List<Segment> segments = dialogueSegments(visual);
        int colorRuns = countDialogueColorRuns(segments);
        int icons = countDialogueIcons(segments);
        String entryId = TranslationManager.getEntryIdInContext(rawText, quest, "dialogue");
        if (entryId.isEmpty()) return;
        String speaker = rawSpeaker == null ? "" : singleLine(rawSpeaker);
        String stage = rawStage == null ? "" : singleLine(rawStage);
        String questId = TranslationManager.getQuestId(quest);
        String captureId = khash("format\u001e" + entryId + "\u001e" + formattingSignature(segments));
        if (!SESSION_CAPTURE_IDS.add(captureId)) return;
        QUEUE.add(new TelemetryItem("quests", norm, speaker, quest, questId, stage,
                "dialogue_format", "", 0, "", "", SESSION_ID,
                getPlayerUuid(), segments, entryId, rawTranslation,
                colorRuns, icons, captureId));
        ensureWorkerStarted();
        triggerFlush();
    }

    public static void recordTooltipSnapshot(String snapshotId, String familyId, Object snapshot) {
        if (!allowed() || !SESSION_CAPTURE_IDS.add(snapshotId)) return;
        JsonObject item = GSON.toJsonTree(snapshot).getAsJsonObject();
        item.addProperty("kind", "tooltip_snapshot");
        item.addProperty("snapshotId", snapshotId);
        item.addProperty("familyId", familyId);
        item.addProperty("sessionId", SESSION_ID);
        String uuid = getPlayerUuid();
        if (!uuid.isEmpty()) item.addProperty("player_uuid", uuid);
        SNAPSHOT_QUEUE.add(item);
        ensureWorkerStarted();
        triggerFlush();
    }

    public static void recordChatMessage(JsonObject message) {
        if (!allowed()) return;
        String id = message.get("messageId").getAsString();
        if (!SESSION_CAPTURE_IDS.add("chat:" + id)) return;
        message.addProperty("sessionId", SESSION_ID);
        String uuid = getPlayerUuid();
        if (!uuid.isEmpty()) message.addProperty("player_uuid", uuid);
        CHAT_QUEUE.add(message);
        ensureWorkerStarted();
        triggerFlush();
    }

    public static void recordNpcNameplate(String key, List<Segment> segments) {
        if (!allowed() || !isUsefulText(key)) return;
        String norm = normalize(key);
        String captureId = captureHash("npc", "npc_nameplate", "", "", "", "", "nameplate", "", List.of(), norm);
        if (!SESSION_CAPTURE_IDS.add(captureId)) return;
        QUEUE.add(new TelemetryItem("npc", norm, "", "", "", "", "npc_nameplate", "", 0,
                "", "nameplate", SESSION_ID, getPlayerUuid(), segments,
                "", "", 0, 0, captureId));
        ensureWorkerStarted();
        triggerFlush();
    }

    public static void flush() {
        ensureWorkerStarted();
        triggerFlush();
    }

    public static void shutdown() {
        ChatCaptureContext.boundary();
        Thread drain = new Thread(() -> {
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            flushBatch();
            while ((!QUEUE.isEmpty() || !SNAPSHOT_QUEUE.isEmpty() || !CHAT_QUEUE.isEmpty()) && System.nanoTime() < deadline) {
                flushBatch();
                if (retryAfterNanos > System.nanoTime()) break;
            }
        }, "WynnRunica-TelemetryShutdown");
        drain.setDaemon(false);
        drain.start();
    }

    private static void triggerFlush() {
        synchronized (FLUSH_LOCK) {
            FLUSH_LOCK.notifyAll();
        }
    }

    private static void ensureWorkerStarted() {
        if (workerStarted) return;
        synchronized (TelemetrySender.class) {
            if (workerStarted) return;
            workerStarted = true;
            Thread worker = new Thread(TelemetrySender::workerLoop, "WynnRunica-TelemetrySender");
            worker.setDaemon(true);
            worker.start();
        }
    }

    private static void workerLoop() {
        long nextSendAt = 0;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                synchronized (FLUSH_LOCK) {
                    while (QUEUE.isEmpty() && SNAPSHOT_QUEUE.isEmpty() && CHAT_QUEUE.isEmpty()
                            || System.nanoTime() < Math.max(nextSendAt, retryAfterNanos)) {
                        long delay = Math.max(1, (Math.max(nextSendAt, retryAfterNanos) - System.nanoTime()) / 1_000_000);
                        FLUSH_LOCK.wait(QUEUE.isEmpty() && SNAPSHOT_QUEUE.isEmpty() && CHAT_QUEUE.isEmpty() ? 60000 : delay);
                    }
                }
                flushBatch();
                nextSendAt = System.nanoTime() + Duration.ofSeconds(1).toNanos();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception error) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private static synchronized void flushBatch() {
        if (QUEUE.isEmpty() && SNAPSHOT_QUEUE.isEmpty() && CHAT_QUEUE.isEmpty()) return;

        List<JsonObject> chat = new ArrayList<>();
        JsonObject chatItem;
        while (chat.size() < 100 && (chatItem = CHAT_QUEUE.poll()) != null) chat.add(chatItem);

        List<JsonObject> snapshots = new ArrayList<>();
        JsonObject snapshot;
        while (snapshots.size() < 100 && (snapshot = SNAPSHOT_QUEUE.poll()) != null) {
            snapshots.add(snapshot);
        }

        List<TelemetryItem> batch = new ArrayList<>();
        TelemetryItem item;
        while (batch.size() < 500 && (item = QUEUE.poll()) != null) {
            batch.add(item);
        }
        if (!chat.isEmpty() && !sendSnapshots(chat, HUB + "/chat-captures/submit")) {
            int queued = CHAT_QUEUE.size();
            for (JsonObject failed : chat) {
                if (queued++ < 2000) CHAT_QUEUE.add(failed);
                else SESSION_CAPTURE_IDS.remove("chat:" + failed.get("messageId").getAsString());
            }
        }
        if (!snapshots.isEmpty() && !sendSnapshots(snapshots, HUB + "/tooltip-snapshots/submit")) {
            int queuedSnapshots = SNAPSHOT_QUEUE.size();
            for (JsonObject failed : snapshots) {
                if (queuedSnapshots++ < 2000) {
                    SNAPSHOT_QUEUE.add(failed);
                } else {
                    SESSION_CAPTURE_IDS.remove(failed.get("snapshotId").getAsString());
                }
            }
        }
        if (batch.isEmpty()) return;

        List<TelemetryItem> formatting = batch.stream()
                .filter(value -> "dialogue_format".equals(value.kind())).toList();
        List<TelemetryItem> untranslated = batch.stream()
                .filter(value -> !"dialogue_format".equals(value.kind())).toList();
        if (!untranslated.isEmpty() && !sendBatch(untranslated, HUB + "/untranslated/submit")) {
            requeue(untranslated);
        }
        if (!formatting.isEmpty() && !sendBatch(formatting, HUB + "/formatting/submit")) {
            requeue(formatting);
        }
    }

    private static void requeue(List<TelemetryItem> failed) {
        int queued = QUEUE.size();
        for (TelemetryItem item : failed) {
            if (queued++ < 2000) {
                QUEUE.add(item);
            } else {
                SESSION_CAPTURE_IDS.remove(item.captureId());
            }
        }
    }

    private static boolean sendSnapshots(List<JsonObject> snapshots, String endpoint) {
        JsonObject root = new JsonObject();
        root.addProperty("apiVersion", 3);
        root.addProperty("client", CLIENT_VERSION);
        JsonArray items = new JsonArray();
        snapshots.forEach(items::add);
        root.add("items", items);
        return sendJson(root, endpoint);
    }

    private static boolean sendBatch(List<TelemetryItem> batch, String endpoint) {
        JsonObject root = new JsonObject();
        root.addProperty("apiVersion", 3);
        root.addProperty("client", CLIENT_VERSION);
        JsonArray items = new JsonArray();
        for (TelemetryItem it : batch) {
            JsonObject obj = new JsonObject();
            obj.addProperty("cat", it.cat());
            obj.addProperty("en", it.en());
            obj.addProperty("captureId", it.captureId());
            obj.addProperty("sessionId", it.sessionId());
            if (!it.speaker().isEmpty()) obj.addProperty("speaker", it.speaker());
            if (!it.quest().isEmpty()) obj.addProperty("quest", it.quest());
            if (!it.questId().isEmpty()) obj.addProperty("questId", it.questId());
            if (!it.stage().isEmpty()) obj.addProperty("stage", it.stage());
            obj.addProperty("kind", it.kind());
            if ("dialogue".equals(it.kind()) || "dialogue_format".equals(it.kind())) obj.addProperty("complete", true);
            if (!it.choiceSetId().isEmpty()) obj.addProperty("choiceSetId", it.choiceSetId());
            if (it.choiceIndex() > 0) obj.addProperty("choiceIndex", it.choiceIndex());
            if (!it.parentOriginal().isEmpty()) obj.addProperty("parentOriginal", it.parentOriginal());
            if (!it.screen().isEmpty()) obj.addProperty("screen", it.screen());
            if (!it.playerUuid().isEmpty()) obj.addProperty("player_uuid", it.playerUuid());
            if (it.segments() != null && !it.segments().isEmpty()) {
                obj.add("segments", GSON.toJsonTree(it.segments()));
            }
            if (!it.entryId().isEmpty()) obj.addProperty("entryId", it.entryId());
            if (!it.ru().isEmpty()) obj.addProperty("ru", it.ru());
            if (it.sourceColorRuns() > 0) obj.addProperty("sourceColorRuns", it.sourceColorRuns());
            if (it.sourceIcons() > 0) obj.addProperty("sourceIcons", it.sourceIcons());
            items.add(obj);
        }
        root.add("items", items);
        return sendJson(root, endpoint);
    }

    private static boolean sendJson(JsonObject root, String endpoint) {
        byte[] payload = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
        byte[] compressed;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                gzip.write(payload);
            }
            compressed = baos.toByteArray();
        } catch (IOException e) {
            return false;
        }

        try {
            long waitNanos = nextRequestAtNanos - System.nanoTime();
            if (waitNanos > 0) {
                java.util.concurrent.TimeUnit.NANOSECONDS.sleep(waitNanos);
            }
            nextRequestAtNanos = System.nanoTime() + Duration.ofMillis(350).toNanos();
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Content-Encoding", "gzip")
                    .header("User-Agent", CLIENT_VERSION)
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(compressed))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                long delay = response.statusCode() == 404 || response.statusCode() == 426 ? 60 : 5;
                try {
                    delay = Math.max(delay, Math.min(300, Long.parseLong(
                            response.headers().firstValue("Retry-After").orElse("0"))));
                } catch (NumberFormatException ignored) {}
                retryAfterNanos = System.nanoTime() + Duration.ofSeconds(delay).toNanos();
            }
            return response.statusCode() == 200;
        } catch (Exception ignored) {
            retryAfterNanos = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            return false;
        }
    }

    static List<Segment> serialize(Text text) {
        if (text == null) return List.of();
        List<Segment> segments = new ArrayList<>();
        walk(text, Style.EMPTY, segments);
        return segments;
    }

    // Ник игрока на сайт не уходит: в кусках реплики он заменяется тем же <playername>, что и в ключе.
    private static List<Segment> dialogueSegments(Text text) {
        String name = MinecraftClient.getInstance().getSession().getUsername();
        List<Segment> result = new ArrayList<>();
        for (Segment segment : serialize(text)) {
            result.add(new Segment(segment.text().replace(name, "<playername>"), segment.color(), segment.font(),
                    segment.bold(), segment.italic(), segment.underlined(), segment.strikethrough(),
                    segment.obfuscated(), segment.icon()));
        }
        return result;
    }

    private static void walk(Text node, Style parentStyle, List<Segment> out) {
        Style style = node.getStyle().withParent(parentStyle);
        node.getContent().visit(value -> {
            if (!value.isEmpty()) out.add(segment(value, style));
            return java.util.Optional.empty();
        });
        for (Text sibling : node.getSiblings()) {
            walk(sibling, style, out);
        }
    }

    private static Segment segment(String value, Style style) {
        String color = style.getColor() == null
                ? null : String.format("#%06X", style.getColor().getRgb() & 0xFFFFFF);
        String font = "minecraft:default";
        boolean icon = false;
        StyleSpriteSource source = style.getFont();
        if (source instanceof StyleSpriteSource.Font fontSource) {
            font = fontSource.id().toString();
            icon = !font.equals("minecraft:default") && !font.equals("minecraft:uniform");
        } else if (source instanceof StyleSpriteSource.Sprite spriteSource) {
            font = "sprite:" + spriteSource.atlasId() + "/" + spriteSource.spriteId();
            icon = true;
        }
        return new Segment(value, color, font, style.isBold(), style.isItalic(),
                style.isUnderlined(), style.isStrikethrough(), style.isObfuscated(), icon);
    }

    private static boolean isDialogueBody(Segment segment) {
        return segment.font() != null && segment.font().contains("hud/dialogue/text/")
                && segment.font().contains("/body_");
    }

    private static int countDialogueIcons(List<Segment> segments) {
        int total = 0;
        for (Segment segment : segments) {
            if (isDialogueIcon(segment)) total++;
        }
        return total;
    }

    private static int countDialogueColorRuns(List<Segment> segments) {
        int total = 0;
        String previous = "";
        for (Segment segment : segments) {
            if (segment.font() == null
                    || !segment.font().contains("hud/dialogue/text/wynncraft/body_")) continue;
            String color = segment.color() == null ? "" : segment.color();
            boolean colored = !color.isEmpty() && !"#FFFFFF".equalsIgnoreCase(color)
                    && !"#FCFCFC".equalsIgnoreCase(color);
            if (colored && !color.equalsIgnoreCase(previous)) total++;
            previous = colored ? color : "";
        }
        return total;
    }

    private static boolean isDialogueIcon(Segment segment) {
        if (segment.font() == null) return false;
        return segment.font().contains("hud/dialogue/text/common/body_")
                || segment.font().contains("hud/dialogue/text/merchant/body_")
                || segment.font().contains("hud/dialogue/text/currency/body_")
                || segment.font().contains("hud/dialogue/text/keybind/body_")
                || segment.font().startsWith("sprite:");
    }

    private static String formattingSignature(List<Segment> segments) {
        StringBuilder value = new StringBuilder();
        for (Segment segment : segments) {
            if (!isDialogueBody(segment)) continue;
            value.append(segment.color()).append('|').append(segment.font()).append('|')
                    .append(segment.icon()).append('|').append(segment.text()).append('\u001f');
        }
        return khash(value.toString());
    }

    private static String getPlayerUuid() {
        var uuid = MinecraftClient.getInstance().getSession().getUuidOrNull();
        return uuid == null ? "" : uuid.toString();
    }

    private static String normalize(String text) {
        return normalize(text, false);
    }

    private static String normalize(String text, boolean playerText) {
        String clean = CLEAN_DECOR.matcher(text).replaceAll("").replace("<em>", "");
        clean = WHITESPACE.matcher(clean.replace('\u0000', ' ')).replaceAll(" ").trim();
        if (playerText) clean = clean.replace(MinecraftClient.getInstance().getSession().getUsername(), "<playername>");
        clean = COUNTER_PATTERN.matcher(clean).replaceAll("[<num>/<num>]");
        clean = COORD_PATTERN.matcher(clean).replaceAll("(<num>, <num>)");
        clean = NUMBER_PATTERN.matcher(clean).replaceAll("<num>");
        return singleLine(clean);
    }

    private static boolean isUsefulText(String text) {
        if (text.length() < 3 || text.length() > 2000 || containsCyrillic(text)) return false;
        return HAS_WORD.matcher(text).find();
    }

    private static final Pattern QUEST_TIMER = Pattern.compile("\\s*\\((?:\\d+\\s*[dhms]\\s*)+left\\)\\s*$");

    // У мировых событий в названии идёт обратный отсчёт: «Prelude to Annihilation (8m 40s left)».
    // Без него событие остаётся одним квестом, а не новым каждую секунду.
    static String questName(String raw) {
        return QUEST_TIMER.matcher(singleLine(raw)).replaceFirst("");
    }

    private static String singleLine(String text) {
        return WHITESPACE.matcher(text.replace('\u0000', ' ')).replaceAll(" ").trim();
    }

    private static boolean containsCyrillic(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x0400 && c <= 0x052F) return true;
        }
        return false;
    }

    private static String khash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 12);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String captureHash(String cat, String kind, String quest, String stage,
                                      String speaker, String screen, String itemId, String scopeTitle,
                                      List<String> anchors, String en) {
        return khash(String.join("\u001e", cat, kind, quest, stage, speaker, screen, itemId,
                scopeTitle, String.join("\u001f", anchors), en));
    }
}
