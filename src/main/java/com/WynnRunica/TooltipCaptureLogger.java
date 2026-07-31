package com.WynnRunica;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.registry.Registries;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;

import java.io.BufferedWriter;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

public final class TooltipCaptureLogger {
    private static final Gson GSON = new Gson();
    private static final Pattern HAS_WORD = Pattern.compile("\\p{L}{2,}");
    private static final Pattern GUI_NUMBER =
            Pattern.compile("(?<!§)[+\\-]?\\d+(?:[.,/]\\d+)*");
    private static final ConcurrentLinkedQueue<TooltipEntry> QUEUE = new ConcurrentLinkedQueue<>();
    private static final Path LOG_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("WynnRunica").resolve("untranslated-tooltips.jsonl");
    private static volatile boolean writerStarted;
    private static volatile String lastCapturedFingerprint = "";

    private record Segment(String text, String color, String font, boolean bold,
                           boolean italic, boolean underlined, boolean strikethrough,
                           boolean obfuscated, boolean icon) {}

    private record TooltipLine(String text, String key, String saveKey,
                               String translation, boolean missing,
                               List<Segment> segments) {}

    private record TooltipEntry(String id, String fingerprint, String itemId,
                                String itemName, String screen, String source, String tooltipStyle,
                                long capturedAt,
                                List<TooltipLine> lines) {}

    private TooltipCaptureLogger() {}

    public static void capture(ItemStack stack, List<Text> tooltip, Text screenTitle) {
        if (!UntranslatedLogger.ENABLED || stack == null || stack.isEmpty()
                || tooltip == null || tooltip.isEmpty()) return;

        List<TooltipLine> lines = new ArrayList<>(tooltip.size());
        StringBuilder fingerprintSource = new StringBuilder();

        for (Text line : tooltip) {
            TextEmojiUtils.Extracted extracted = TextEmojiUtils.extract(line);
            String text = line.getString();
            String key = extracted.key;
            String saveKey = normalizeNumbers(key);
            boolean useful = isUsefulTranslationKey(key);
            String translation = TranslationPrinter.findGuiTranslation(key);
            boolean missing = useful && translation == null;
            List<Segment> segments = serialize(line);
            lines.add(new TooltipLine(text, key, saveKey, translation, missing, segments));
            fingerprintSource.append(saveKey).append('\u001d')
                    .append(GSON.toJson(segments)).append('\u001f');
        }

        String screen = screenTitle == null ? "" : screenTitle.getString();
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        var tooltipStyle = stack.get(DataComponentTypes.TOOLTIP_STYLE);
        String tooltipStyleId = tooltipStyle == null ? "minecraft:default" : tooltipStyle.toString();
        String fingerprint = sha256(itemId + "\u001e" + screen + "\u001e"
                + tooltipStyleId + "\u001e" + fingerprintSource);
        if (fingerprint.equals(lastCapturedFingerprint)) return;
        lastCapturedFingerprint = fingerprint;

        String itemName = tooltip.getFirst().getString();
        String source = isAbilityTree(screen, lines) ? "ability_tree" : "interface";
        QUEUE.add(new TooltipEntry(fingerprint, fingerprint, itemId,
                itemName, screen, source, tooltipStyleId, System.currentTimeMillis(), lines));
        ensureWriterStarted();
    }

    private static boolean isAbilityTree(String screen, List<TooltipLine> lines) {
        if (!screen.startsWith("\uDAFF\uDFEA\uE000")) return false;
        for (TooltipLine line : lines) {
            String key = line.saveKey().toLowerCase(java.util.Locale.ROOT);
            if ((key.contains("ability points:")
                    && !key.contains("unused ability points:")
                    && !key.contains("next ability points:"))
                    || key.contains("очки способностей:")
                    || key.contains("archetype:") || key.contains("blocked by:")
                    || key.contains("unlocking will block:")
                    || key.contains("upgrade your <skill> skill")
                    || (key.contains("upgrade your <em>") && key.contains(" skill"))
                    || key.contains("архетип:") || key.contains("заблокировано:")
                    || key.contains("откроет, но заблокирует:")) return true;
        }
        return false;
    }

    private static List<Segment> serialize(Text source) {
        List<Segment> result = new ArrayList<>();
        walk(source, Style.EMPTY, result);
        return result;
    }

    private static void walk(Text node, Style parent, List<Segment> out) {
        Style style = node.getStyle().withParent(parent);
        node.getContent().visit(value -> {
            if (!value.isEmpty()) out.add(segment(value, style));
            return java.util.Optional.empty();
        });
        for (Text sibling : node.getSiblings()) walk(sibling, style, out);
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

    private static boolean isUsefulTranslationKey(String key) {
        if (key == null || key.isBlank() || containsCyrillic(key)) return false;
        String bare = key.replaceAll("§.", "").replace("<em>", "").trim();
        return HAS_WORD.matcher(bare).find();
    }

    private static String normalizeNumbers(String key) {
        return GUI_NUMBER.matcher(key).replaceAll("<num>");
    }

    private static boolean containsCyrillic(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x0400 && c <= 0x052F) return true;
        }
        return false;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void ensureWriterStarted() {
        if (writerStarted) return;
        synchronized (TooltipCaptureLogger.class) {
            if (writerStarted) return;
            writerStarted = true;
            Thread writer = new Thread(TooltipCaptureLogger::writerLoop,
                    "WynnRunica-TooltipWriter");
            writer.setDaemon(true);
            writer.start();
        }
    }

    private static void writerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                TooltipEntry first = QUEUE.poll();
                if (first == null) {
                    Thread.sleep(150);
                    continue;
                }
                Files.createDirectories(LOG_FILE.getParent());
                try (BufferedWriter writer = Files.newBufferedWriter(LOG_FILE,
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND)) {
                    writer.write(GSON.toJson(first));
                    writer.newLine();
                    TooltipEntry entry;
                    while ((entry = QUEUE.poll()) != null) {
                        writer.write(GSON.toJson(entry));
                        writer.newLine();
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException error) {
                System.out.println("[WynnRunica] Failed to write tooltip inbox: "
                        + error.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
