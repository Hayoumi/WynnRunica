package com.WynnRunica;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

// Сколько снимков подсказок ушло бы на хаб по нынешним правилам мода. Берёт снимки, уже лежащие
// на хабе, и пересчитывает их так же, как TooltipCaptureLogger. Запуск: gradlew checkSnapshotVolume
public final class SnapshotVolumeCheck {
    public static void main(String[] args) throws Exception {
        TranslationLoader.loadAll(Path.of(args[1]));
        TranslationManager.reloadGuiPatterns();
        int total = 0;
        int withMissing = 0;
        Set<String> kept = new HashSet<>();
        for (String row : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
            if (row.isBlank()) continue;
            JsonObject capture = JsonParser.parseString(row).getAsJsonObject();
            if (!"interface".equals(capture.get("source").getAsString())) continue;
            total++;
            List<String> keys = new ArrayList<>();
            for (JsonElement line : capture.getAsJsonArray("lines")) keys.add(line.getAsJsonObject().get("key").getAsString());
            if (keys.isEmpty()) continue;
            String itemId = capture.get("itemId").getAsString();
            GuiScope scope = TranslationManager.findScopeByTitle(keys.getFirst(), keys, capture.get("screen").getAsString(), itemId);
            TreeSet<String> missing = new TreeSet<>();
            for (JsonElement element : capture.getAsJsonArray("lines")) {
                JsonObject line = element.getAsJsonObject();
                String key = line.get("key").getAsString();
                if (!line.get("missing").getAsBoolean() && TranslationManager.getGuiTranslation(key, scope).equals(key)) continue;
                if (!TranslationManager.getGuiTranslation(key, scope).equals(key)) continue;
                if (!line.get("missing").getAsBoolean()) continue;
                if (TranslationManager.findGuiLabelTranslation(key, null) != null) continue;
                missing.add(line.get("saveKey").getAsString());
            }
            if (missing.isEmpty()) continue;
            withMissing++;
            String title = capture.getAsJsonArray("lines").get(0).getAsJsonObject().get("saveKey").getAsString();
            kept.add(itemId + "\u001e" + title + "\u001e" + capture.get("tooltipStyle").getAsString() + "\u001e" + String.join("\u001f", missing));
        }
        System.out.println("снимков на хабе: " + total + ", с непереведёнными строками по новым правилам: " + withMissing
                + ", разных снимков по новому отпечатку: " + kept.size());
    }
}
