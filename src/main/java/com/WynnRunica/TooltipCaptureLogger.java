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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

public final class TooltipCaptureLogger {
    private static final boolean DEBUG_CAPTURE_LOG = Boolean.getBoolean("wynnrunica.debug.captureLog");
    private static final Gson GSON = new Gson();
    private static final Pattern HAS_WORD = Pattern.compile("\\p{L}{2,}");
    private static final Pattern GUI_NUMBER =
            Pattern.compile("(?<!§)[+\\-]?\\d+(?:[.,/]\\d+)*");
    private static final Pattern MARKET_CLEAN_RE =
            Pattern.compile("§(?:#[0-9a-fA-F]{6}|.)|[\\uE000-\\uF8FF\\x{C0000}-\\x{10FFFF}]");
    private static final ConcurrentLinkedQueue<TooltipEntry> QUEUE = new ConcurrentLinkedQueue<>();
    private static final Path LOG_FILE = DEBUG_CAPTURE_LOG
            ? FabricLoader.getInstance().getConfigDir().resolve("WynnRunica")
                    .resolve("untranslated-tooltips-debug.jsonl")
            : null;
    private static final Set<String> seenFingerprints = ConcurrentHashMap.newKeySet();
    private static volatile boolean writerStarted;
    private static volatile String lastCapturedFingerprint = "";
    private static ItemStack lastStack;
    private static List<Text> lastTooltip;
    private static List<Text> lastDisplayed;
    private static Text lastScreen;

    static {
        if (DEBUG_CAPTURE_LOG) loadSeenFingerprints();
    }

    private static void loadSeenFingerprints() {
        if (LOG_FILE == null || !Files.exists(LOG_FILE)) return;
        try (java.io.BufferedReader reader = Files.newBufferedReader(LOG_FILE, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int idx = line.indexOf("\"fingerprint\":\"");
                if (idx >= 0) {
                    int start = idx + 15;
                    int end = line.indexOf('"', start);
                    if (end > start) {
                        seenFingerprints.add(line.substring(start, end));
                        continue;
                    }
                }
                int idIdx = line.indexOf("\"id\":\"");
                if (idIdx >= 0) {
                    int start = idIdx + 6;
                    int end = line.indexOf('"', start);
                    if (end > start) {
                        seenFingerprints.add(line.substring(start, end));
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    private record Segment(String text, String color, String font, boolean bold,
                           boolean italic, boolean underlined, boolean strikethrough,
                           boolean obfuscated, boolean icon) {}

    private record TooltipLine(String text, String key, String saveKey,
                               String translation, boolean missing,
                               List<Segment> segments, List<Segment> displaySegments) {}

    private record TooltipEntry(String id, String fingerprint, String familyId,
                                String itemId, String itemName, String screen,
                                String scopeTitle, List<String> anchors, String source,
                                String tooltipStyle, long capturedAt,
                                List<TooltipLine> lines) {}

    private TooltipCaptureLogger() {}

    public static void capture(ItemStack stack, List<Text> tooltip, Text screenTitle) {
        capture(stack, tooltip, tooltip, screenTitle);
    }

    public static void capture(ItemStack stack, List<Text> tooltip, List<Text> displayed,
                               Text screenTitle) {
        if (!TelemetrySender.allowed() || stack == null || stack.isEmpty()
                || tooltip == null || tooltip.isEmpty()) return;

        if (lastStack != null && ItemStack.areItemsAndComponentsEqual(stack, lastStack)
                && java.util.Objects.equals(screenTitle, lastScreen)
                && tooltip.equals(lastTooltip) && java.util.Objects.equals(displayed, lastDisplayed)) return;
        lastStack = stack.copy();
        lastTooltip = List.copyOf(tooltip);
        lastDisplayed = displayed == null ? null : List.copyOf(displayed);
        lastScreen = screenTitle;

        List<TooltipLine> lines = new ArrayList<>(tooltip.size());
        java.util.TreeSet<String> missingKeys = new java.util.TreeSet<>();
        boolean anyMissing = false;
        String screen = screenTitle == null ? "" : screenTitle.getString();
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        String rawTitle = TextEmojiUtils.extract(tooltip.getFirst()).key;
        String scopeTitle = normalizeNumbers(rawTitle);
        List<String> anchors = new ArrayList<>();
        List<String> loreKeys = new ArrayList<>();
        for (Text line : tooltip) loreKeys.add(TextEmojiUtils.extract(line).key);
        GuiScope scope = TranslationManager.findScopeByTitle(rawTitle, loreKeys, screen, itemId);
        for (int index = 1; index < tooltip.size() && anchors.size() < 2; index++) {
            String anchor = normalizeNumbers(TextEmojiUtils.extract(tooltip.get(index)).key);
            if (isUsefulTranslationKey(anchor) && !anchor.equals(scopeTitle)) anchors.add(anchor);
        }

        for (int index = 0; index < tooltip.size(); index++) {
            Text line = tooltip.get(index);
            TextEmojiUtils.Extracted extracted = TextEmojiUtils.extract(line);
            String text = line.getString();
            String key = extracted.key;
            String saveKey = normalizeNumbers(key);
            boolean useful = isUsefulTranslationKey(key);
            String resolved = TranslationManager.getGuiTranslation(key, scope);
            String translation = resolved.equals(key) ? null : resolved;
            boolean market = isMarketLine(text);
            // Строка стата переводится по словарю названий, а не целиком: непереведённой она не считается.
            boolean missing = useful && !market && translation == null
                    && TranslationManager.findGuiLabelTranslation(key, null) == null;
            if (missing) anyMissing = true;
            List<Segment> segments = serialize(line);
            List<Segment> displayedSegments = displayed != null && index < displayed.size()
                    ? serialize(displayed.get(index)) : List.of();
            lines.add(new TooltipLine(text, key, saveKey, translation, missing,
                    segments, displayedSegments));
        }
        // Подсказка, в которой всё переведено, никому не нужна.
        if (!anyMissing) return;

        boolean abilityTree = isAbilityTree(screen, lines);
        // Ники игроков на хаб не уходят: в названии экрана, предмета и в строках вместо ника <actor>.
        List<String> names = ServerNotificationTranslator.knownNames();
        screen = ServerNotificationTranslator.hideNames(screen, names);
        scopeTitle = ServerNotificationTranslator.hideNames(scopeTitle, names);
        anchors.replaceAll(anchor -> ServerNotificationTranslator.hideNames(anchor, names));
        for (int index = 0; index < lines.size(); index++) {
            TooltipLine line = lines.get(index);
            String saveKey = ServerNotificationTranslator.hideNames(line.saveKey(), names);
            if (line.missing()) missingKeys.add(saveKey);
            lines.set(index, new TooltipLine(ServerNotificationTranslator.hideNames(line.text(), names),
                    ServerNotificationTranslator.hideNames(line.key(), names), saveKey, line.translation(),
                    line.missing(), hideNames(line.segments(), names), hideNames(line.displaySegments(), names)));
        }

        var tooltipStyle = stack.get(DataComponentTypes.TOOLTIP_STYLE);
        String tooltipStyleId = tooltipStyle == null ? "minecraft:default" : tooltipStyle.toString();
        String familyId = sha256(itemId + "\u001e" + scopeTitle
                + "\u001e" + tooltipStyleId + "\u001e" + String.join("\u001f", anchors));
        // Один снимок на набор непереведённых строк. Тот же предмет с другими числами, цветами
        // или ценой даёт тот же снимок, иначе каждый ролл статов занимал бы на хабе место.
        String fingerprint = sha256(familyId + "\u001e" + String.join("\u001f", missingKeys));
        String itemName = ServerNotificationTranslator.hideNames(tooltip.getFirst().getString(), names);
        String source = abilityTree ? "ability_tree" : "interface";
        TooltipEntry entry = new TooltipEntry(fingerprint, fingerprint, familyId, itemId,
                itemName, screen, scopeTitle, anchors, source,
                tooltipStyleId, System.currentTimeMillis(), lines);
        if (!"ability_tree".equals(source)) {
            TelemetrySender.recordTooltipSnapshot(fingerprint, familyId, entry);
        }
        if (!DEBUG_CAPTURE_LOG || !seenFingerprints.add(fingerprint)) return;
        lastCapturedFingerprint = fingerprint;
        QUEUE.add(entry);
        ensureWriterStarted();
    }

    private static List<Segment> hideNames(List<Segment> segments, List<String> names) {
        List<Segment> result = new ArrayList<>();
        for (Segment segment : segments) {
            result.add(new Segment(ServerNotificationTranslator.hideNames(segment.text(), names), segment.color(),
                    segment.font(), segment.bold(), segment.italic(), segment.underlined(),
                    segment.strikethrough(), segment.obfuscated(), segment.icon()));
        }
        return result;
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

    private static final java.util.Set<String> dumpedFinal = new java.util.HashSet<>();

    // Отладка вёрстки (ключ запуска -Dwynnrunica.debug=true). Подсказка записывается ровно в том
    // виде, в каком приходит на отрисовку, то есть после Wynntils и других модов, и рядом то, что
    // из неё сделал наш мод, с настоящими ширинами строк. По этому файлу проверка вёрстки видит
    // то же, что игрок на экране: config/WynnRunica/tooltip-final-debug.jsonl.
    static void dumpFinal(List<Text> tooltip, List<Text> result) {
        StringBuilder id = new StringBuilder();
        for (Text line : tooltip) id.append(line.getString().replaceAll("\\d+", "0")).append('\n');
        if (!dumpedFinal.add(id.toString())) return;
        com.google.gson.JsonObject entry = new com.google.gson.JsonObject();
        entry.addProperty("itemName", tooltip.getFirst().getString());
        entry.addProperty("source", "final");
        entry.add("lines", dumpLines(tooltip));
        entry.add("result", dumpLines(result));
        try {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("WynnRunica/tooltip-final-debug.jsonl");
            Files.writeString(file, GSON.toJson(entry) + "\n", StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException error) {
            System.out.println("[WynnRunica] отладочная запись подсказки не удалась: " + error.getMessage());
        }
    }

    private static com.google.gson.JsonArray dumpLines(List<Text> lines) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (Text line : lines) {
            com.google.gson.JsonObject row = new com.google.gson.JsonObject();
            row.addProperty("text", line.getString());
            row.addProperty("width", TextEmojiUtils.width.applyAsInt(line));
            com.google.gson.JsonArray segments = new com.google.gson.JsonArray();
            for (Segment segment : serialize(line)) {
                com.google.gson.JsonObject part = GSON.toJsonTree(segment).getAsJsonObject();
                part.addProperty("width", TextEmojiUtils.width.applyAsInt(Text.literal(segment.text())
                        .setStyle(styleOf(line, segment))));
                segments.add(part);
            }
            row.add("segments", segments);
            array.add(row);
        }
        return array;
    }

    // Стиль куска с таким же текстом в исходной строке: нужен, чтобы измерить его настоящую ширину.
    private static net.minecraft.text.Style styleOf(Text line, Segment segment) {
        return line.visit((style, text) -> text.equals(segment.text())
                ? java.util.Optional.of(style) : java.util.Optional.<net.minecraft.text.Style>empty(),
                net.minecraft.text.Style.EMPTY).orElse(net.minecraft.text.Style.EMPTY);
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

    private static boolean isMarketLine(String rawText) {
        if (rawText == null || rawText.isBlank()) return false;
        String clean = MARKET_CLEAN_RE.matcher(rawText).replaceAll("").trim().toLowerCase(java.util.Locale.ROOT);
        if (clean.equals("price") || clean.equals("цена")) return true;
        if (clean.contains("left-click to buy") || clean.contains("right-click to compare") || clean.contains("cannot afford to buy")) return true;
        if (clean.contains("to sell") || clean.contains("row") || clean.contains("converts")) return false;
        return (clean.contains("stx") || clean.contains("²") || clean.contains("eb") || clean.contains("le") || clean.contains("emeralds"))
                && clean.chars().anyMatch(Character::isDigit);
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
                if (LOG_FILE == null) return;
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
