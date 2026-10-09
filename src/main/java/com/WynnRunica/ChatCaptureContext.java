package com.WynnRunica;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class ChatCaptureContext {
    private static final long GAP_NANOS = Duration.ofMillis(120).toNanos();
    private static final int MAX_MESSAGES = 24;
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "WynnRunica-ChatCaptureContext");
        thread.setDaemon(true);
        return thread;
    });
    private static final List<String> messageIds = new ArrayList<>();
    private static final List<String> familyIds = new ArrayList<>();
    private static long lastArrival;
    private static long generation;

    private ChatCaptureContext() {}

    static synchronized void record(JsonObject message) {
        long now = System.nanoTime();
        if (!messageIds.isEmpty() && (now - lastArrival > GAP_NANOS || messageIds.size() >= MAX_MESSAGES)) {
            flush();
        }
        messageIds.add(message.get("messageId").getAsString());
        familyIds.add(message.get("familyId").getAsString());
        lastArrival = now;
        long ticket = ++generation;
        TIMER.schedule(() -> flushIfIdle(ticket), 120, TimeUnit.MILLISECONDS);
    }

    static synchronized void boundary() {
        flush();
    }

    private static synchronized void flushIfIdle(long ticket) {
        if (ticket == generation && !messageIds.isEmpty()
                && System.nanoTime() - lastArrival >= GAP_NANOS) {
            flush();
        }
    }

    private static void flush() {
        if (messageIds.size() >= 2) {
            JsonObject context = new JsonObject();
            context.addProperty("kind", "chat_context");
            context.addProperty("messageId", hash("context\u001f" + String.join("\u001f", messageIds)));
            context.addProperty("familyId", hash("context\u001f" + String.join("\u001f", familyIds)));
            JsonArray ids = new JsonArray();
            messageIds.forEach(ids::add);
            context.add("messageIds", ids);
            context.addProperty("source", "temporal_adjacent");
            TelemetrySender.recordChatMessage(context);
        }
        messageIds.clear();
        familyIds.clear();
        generation++;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
