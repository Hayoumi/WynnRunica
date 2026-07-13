package com.WynnRunica;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

public final class UntranslatedLogger {

    public static volatile boolean ENABLED = false;

    private static final Pattern HAS_WORD = Pattern.compile("\\p{L}{2,}");
    private static final Pattern GUI_NUMBER = Pattern.compile("(?<!§)\\d+[.,/\\d]*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Gson GSON = new Gson();

    private static final Set<String> seenGui = ConcurrentHashMap.newKeySet();
    private static final Set<String> seenDialogues = ConcurrentHashMap.newKeySet();
    private static final ConcurrentLinkedQueue<String> guiQueue = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<DialogueEntry> dialogueQueue = new ConcurrentLinkedQueue<>();

    private static Path guiLogFile;
    private static Path dialogueLogFile;
    private static volatile boolean writerStarted = false;

    private record DialogueEntry(
            String id,
            String text,
            String speaker,
            boolean choice,
            String quest,
            long capturedAt
    ) {}

    static {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir().resolve("WynnRunica");
            Files.createDirectories(configDir);

            guiLogFile = configDir.resolve("untranslated.txt");
            dialogueLogFile = configDir.resolve("untranslated-dialogues.jsonl");

            loadGuiEntries();
            loadDialogueEntries();
        } catch (IOException e) {
            System.out.println("[WynnRunica] Failed to initialize untranslated logger: " + e.getMessage());
        }
    }

    private UntranslatedLogger() {}

    public static void log(String text) {
        if (!ENABLED || guiLogFile == null || text == null) return;

        String clean = singleLine(text);
        if (!isUsefulEnglishText(clean)) return;

        String normalized = GUI_NUMBER.matcher(clean).replaceAll("<num>");
        if (!seenGui.add(normalized)) return;

        guiQueue.add(normalized);
        ensureWriterStarted();
    }

    public static void logDialogue(String text, String speaker, boolean choice, String quest) {
        if (!ENABLED || dialogueLogFile == null || text == null) return;

        String clean = singleLine(text);
        if (!isUsefulEnglishText(clean) || clean.indexOf('@') >= 0) return;

        String lookupKey = dialogueLookupKey(clean);
        if (TranslationPrinter.translations.containsKey(lookupKey)) return;
        if (!seenDialogues.add(lookupKey)) return;

        String cleanSpeaker = singleLine(speaker == null ? "" : speaker);
        String cleanQuest = singleLine(quest == null ? "" : quest);

        dialogueQueue.add(new DialogueEntry(
                UUID.randomUUID().toString(),
                clean,
                cleanSpeaker,
                choice,
                cleanQuest,
                System.currentTimeMillis()
        ));
        ensureWriterStarted();
    }

    public static void setEnabled(boolean on) {
        ENABLED = on;
        if (on) ensureWriterStarted();
    }

    private static void loadGuiEntries() throws IOException {
        if (!Files.exists(guiLogFile)) return;
        for (String line : Files.readAllLines(guiLogFile, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) seenGui.add(line);
        }
    }

    private static void loadDialogueEntries() throws IOException {
        if (!Files.exists(dialogueLogFile)) return;

        for (String line : Files.readAllLines(dialogueLogFile, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            try {
                DialogueEntry entry = GSON.fromJson(line, DialogueEntry.class);
                if (entry != null && entry.text() != null) {
                    seenDialogues.add(dialogueLookupKey(singleLine(entry.text())));
                }
            } catch (Exception ignored) {}
        }
    }

    private static void ensureWriterStarted() {
        if (writerStarted) return;

        synchronized (UntranslatedLogger.class) {
            if (writerStarted) return;
            writerStarted = true;

            Thread writer = new Thread(UntranslatedLogger::writerLoop, "WynnRunica-UntranslatedWriter");
            writer.setDaemon(true);
            writer.start();
        }
    }

    private static void writerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                boolean wroteAnything = writeGuiBatch();
                wroteAnything |= writeDialogueBatch();
                if (!wroteAnything) Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                System.out.println("[WynnRunica] Failed to write untranslated text: " + e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static boolean writeGuiBatch() throws IOException {
        if (guiQueue.isEmpty() || guiLogFile == null) return false;

        try (BufferedWriter writer = Files.newBufferedWriter(guiLogFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            String line;
            while ((line = guiQueue.poll()) != null) {
                writer.write(line);
                writer.newLine();
            }
        }
        return true;
    }

    private static boolean writeDialogueBatch() throws IOException {
        if (dialogueQueue.isEmpty() || dialogueLogFile == null) return false;

        try (BufferedWriter writer = Files.newBufferedWriter(dialogueLogFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            DialogueEntry entry;
            while ((entry = dialogueQueue.poll()) != null) {
                writer.write(GSON.toJson(entry));
                writer.newLine();
            }
        }
        return true;
    }

    private static String dialogueLookupKey(String text) {
        return text.replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private static String singleLine(String text) {
        return WHITESPACE.matcher(text.replace('\u0000', ' ')).replaceAll(" ").trim();
    }

    private static boolean isUsefulEnglishText(String text) {
        if (text.isEmpty() || containsCyrillic(text)) return false;
        return HAS_WORD.matcher(stripDecor(text)).find();
    }

    private static String stripDecor(String text) {
        StringBuilder clean = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            if (c >= 0xE000 && c <= 0xF8FF) continue;
            if (Character.isHighSurrogate(c) && i + 1 < text.length()) {
                int codePoint = Character.toCodePoint(c, text.charAt(i + 1));
                if (codePoint >= 0xF0000 && codePoint <= 0x10FFFD) {
                    i++;
                    continue;
                }
            }
            clean.append(c);
        }
        return clean.toString().replace("<em>", "").trim();
    }

    private static boolean containsCyrillic(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0400 && c <= 0x052F) return true;
        }
        return false;
    }
}
