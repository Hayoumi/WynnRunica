package com.WynnRunica;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class TranslationLoader {
    private static final Pattern MAP_LINK = Pattern.compile(
            "\\{\\{MapLink\\|x=([^|]+)\\|y=([^|]+)\\|z=([^}]+)\\}\\}", Pattern.CASE_INSENSITIVE);
    private static final Pattern SIC = Pattern.compile("\\{\\{sic\\}\\}", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("(?<!§)[+\\-]?\\d+(?:[.,/]\\d+)*");
    private static int brokenFiles;

    private TranslationLoader() {}

    public static int loadAll(Path root) {
        brokenFiles = 0;
        loadQuests(root.resolve("quests"));
        loadGui(root.resolve("gui"));
        loadNpc(root.resolve("npc"));
        return brokenFiles;
    }

    private static void loadQuests(Path folder) {
        copyBundledJsonIfMissing("quests", folder);
        DialogueRevealController.clearLines();
        for (Path file : jsonFiles(folder)) {
            try {
                loadQuestFile(file);
            } catch (Exception error) {
                skip(file, error);
            }
        }

        for (Map.Entry<String, String> entry : TranslationManager.keyToQuest.entrySet()) {
            List<String> keys = listFor(TranslationManager.questToKeys, entry.getValue());
            if (!keys.contains(entry.getKey())) keys.add(entry.getKey());
        }
        System.out.println("[WynnRunica] Loaded JSON v2 quests: " + TranslationManager.translations.size());
    }

    private static void loadQuestFile(Path file) throws IOException {
        JsonObject root = read(file, "quest");
        String fileName = file.getFileName().toString();
        fileName = fileName.substring(0, fileName.length() - ".json".length());
        String quest = string(root, "quest", fileName);
        TranslationManager.questIds.put(quest, string(root, "id", fileName));

        for (JsonObject dialogue : objects(root, "dialogues")) {
            String speaker = string(dialogue, "speaker", "").trim();
            boolean choice = isChoiceSpeaker(speaker) || string(dialogue, "kind", "").equalsIgnoreCase("choice");
            if (!speaker.isEmpty() && !isChoiceSpeaker(speaker)) {
                HashSet<String> speakers = TranslationManager.questSpeakers.get(quest);
                if (speakers == null) {
                    speakers = new HashSet<>();
                    TranslationManager.questSpeakers.put(quest, speakers);
                }
                speakers.add(speaker);
            }
            if (choice) {
                addEntry(quest, dialogue, "choice");
            } else {
                addEntry(quest, dialogue, "dialogue");
            }
            for (JsonObject answer : objects(dialogue, "choices")) addEntry(quest, answer, "choice");
        }
        for (JsonObject answer : objects(root, "choices")) addEntry(quest, answer, "choice");
        for (JsonObject objective : objects(root, "objectives")) addEntry(quest, objective, "objective");
    }

    private static void addEntry(String quest, JsonObject entry, String kind) {
        String en = sanitizeWiki(string(entry, "en", "").trim());
        String ru = sanitizeWiki(string(entry, "ru", "").trim());
        if (en.isEmpty()) return;
        String key = TranslationManager.lookupKey(en);
        String withNumbers = TranslationManager.lookupKey(NUMBER.matcher(en).replaceAll("<num>"));
        boolean objective = kind.equals("objective");

        String id = string(entry, "id", "").trim();
        if (!id.isEmpty()) {
            String fullId = "quest/" + TranslationManager.getQuestId(quest) + "/" + id;
            HashMap<String, String> ids = mapFor(TranslationManager.questEntryIds, quest, kind);
            ids.put(key, fullId);
            if (objective) ids.putIfAbsent(withNumbers, fullId);
        }
        if (kind.equals("dialogue")) DialogueRevealController.rememberLine(en);
        if (ru.isEmpty()) return;

        HashMap<String, String> sameKind = mapFor(TranslationManager.questTranslationsByKind, quest, kind);
        sameKind.put(key, ru);
        if (objective) sameKind.putIfAbsent(withNumbers, ru);

        if (kind.equals("dialogue")) {
            mapFor(TranslationManager.questTranslations, quest).put(key, ru);
            listFor(TranslationManager.questToKeys, quest).add(key);
        }
        if (kind.equals("choice")) {
            mapFor(TranslationManager.questChoiceTranslations, quest).put(en, ru);
        }

        TranslationManager.translations.putIfAbsent(key, ru);
        String firstQuest = TranslationManager.keyToQuest.putIfAbsent(key, quest);
        if (firstQuest != null && !firstQuest.equals(quest)) TranslationManager.ambiguousKeys.add(key);
    }

    private static boolean isChoiceSpeaker(String speaker) {
        String name = speaker.toLowerCase(Locale.ROOT);
        return name.equals("choice") || name.equals("выбор");
    }

    private static String sanitizeWiki(String text) {
        text = MAP_LINK.matcher(text).replaceAll("[$1, $2, $3]");
        return SIC.matcher(text).replaceAll("");
    }

    private static void loadGui(Path folder) {
        copyBundledJsonIfMissing("gui", folder);
        HashMap<String, String> gui = TranslationManager.guiTranslations;

        Path shared = folder.resolve("shared.json");
        try {
            addPairs(gui, objects(read(shared, "gui-shared"), "entries"), false);
        } catch (Exception error) {
            skip(shared, error);
        }
        for (Path file : jsonFiles(folder.resolve("abilities"))) {
            try {
                loadAbilityFile(read(file, "ability"));
            } catch (Exception error) {
                skip(file, error);
            }
        }
        for (Path file : jsonFiles(folder.resolve("scopes"))) {
            try {
                loadScopeFile(read(file, "gui-scope"));
            } catch (Exception error) {
                skip(file, error);
            }
        }
        Path rules = folder.resolve("items").resolve("shared.json");
        try {
            addPairs(gui, objects(read(rules, "pixel-item-rules"), "rules"), true);
        } catch (Exception error) {
            skip(rules, error);
        }
        Path names = folder.resolve("items").resolve("stat-names.json");
        try {
            TranslationManager.statNames.clear();
            for (JsonObject name : objects(read(names, "pixel-item-names"), "names")) {
                TranslationManager.addStatName(string(name, "en", ""), string(name, "ru", ""));
            }
        } catch (Exception error) {
            skip(names, error);
        }
        Path overrides = folder.resolve("items").resolve("overrides.json");
        try {
            loadOverrides(read(overrides, "pixel-item-overrides"));
        } catch (Exception error) {
            skip(overrides, error);
        }
        System.out.println("[WynnRunica] Loaded JSON v2 GUI: " + gui.size());
    }

    private static void loadAbilityFile(JsonObject root) {
        JsonObject archetypes = object(root, "archetypes");
        for (Map.Entry<String, JsonElement> named : archetypes.entrySet()) {
            if (!named.getValue().isJsonObject()) continue;
            JsonObject archetype = named.getValue().getAsJsonObject();
            String en = string(archetype, "en", "").trim();
            String ru = string(archetype, "ru", "").trim();
            if (!en.isEmpty() && !ru.isEmpty()) {
                TranslationManager.registerArchetype(en, ru);
                addPair(TranslationManager.guiTranslations, en, ru, false);
            }
            GuiScope scope = new GuiScope("archetype:" + string(archetype, "id", named.getKey()),
                    en + " Archetype", "Архетип: " + ru, List.of());
            addScopeLines(scope, objects(archetype, "description"), false);
            TranslationManager.registerScope(scope);
        }

        for (JsonObject ability : objects(root, "abilities")) {
            JsonObject name = object(ability, "name");
            GuiScope scope = new GuiScope(string(ability, "id", ""), string(name, "en", ""),
                    string(name, "ru", ""), List.of());
            boolean blocked = string(ability, "has_blocked_variant", "false").equals("true");
            addScopeLines(scope, objects(ability, "description"), blocked);
            addScopeLines(scope, objects(ability, "properties"), blocked);
            addScopeLines(scope, objects(ability, "lines"), blocked);
            if (!scope.nameEn.isBlank()) {
                TranslationManager.registerScope(scope);
                addPair(TranslationManager.guiTranslations, scope.nameEn, scope.nameRu, false);
            }
        }
    }

    private static void loadScopeFile(JsonObject root) {
        for (JsonObject source : objects(root, "scopes")) {
            JsonObject title = object(source, "title");
            JsonObject match = object(source, "match");
            String screen = string(match, "screen", "");
            if (screen.equals("chat")) continue;

            List<String> anchors = new ArrayList<>();
            for (JsonElement anchor : array(match, "anchors")) {
                if (anchor.isJsonPrimitive()) anchors.add(anchor.getAsString());
            }
            int priority = Integer.parseInt(string(match, "priority", "0"));
            GuiScope scope = new GuiScope(string(source, "id", ""), string(title, "en", string(match, "title", "")),
                    string(title, "ru", ""), anchors, screen, string(match, "itemId", ""), priority);
            addScopeLines(scope, objects(source, "lines"), false);
            if (!scope.nameEn.isBlank()) TranslationManager.registerScope(scope);
        }
    }

    private static void loadOverrides(JsonObject root) {
        for (JsonObject override : objects(root, "overrides")) {
            JsonObject title = object(override, "title");
            if (title.size() == 0 && !override.has("lines")) {
                addPair(TranslationManager.guiTranslations, string(override, "en", ""), string(override, "ru", ""), true);
                continue;
            }
            GuiScope scope = new GuiScope(string(override, "id", ""), string(title, "en", ""),
                    string(title, "ru", ""), List.of(), string(override, "screen", ""), string(override, "itemId", ""));
            addScopeLines(scope, objects(override, "lines"), false);
            if (!scope.nameEn.isBlank()) TranslationManager.registerScope(scope);
        }
    }

    private static void loadNpc(Path folder) {
        copyBundledJsonIfMissing("npc", folder);
        Path names = folder.resolve("names.json");
        try {
            addPairs(TranslationManager.npcTranslations, objects(read(names, "npc"), "entries"), false);
        } catch (Exception error) {
            skip(names, error);
        }
    }

    private static void addScopeLines(GuiScope scope, List<JsonObject> rows, boolean blocked) {
        for (JsonObject row : rows) {
            String en = string(row, "en", string(row, "original", ""));
            String ru = string(row, "ru", string(row, "translation", ""));
            scope.putLine(en, ru, blocked);
        }
    }

    private static void addPairs(Map<String, String> map, List<JsonObject> rows, boolean replace) {
        for (JsonObject row : rows) {
            addPair(map, string(row, "en", ""), string(row, "ru", ""), replace);
        }
    }

    private static void addPair(Map<String, String> map, String en, String ru, boolean replace) {
        en = en.trim();
        ru = ru.trim();
        if (en.isEmpty() || ru.isEmpty() || en.equalsIgnoreCase(ru)) return;
        if (replace) {
            map.put(en, ru);
        } else {
            map.putIfAbsent(en, ru);
        }
    }

    private static JsonObject read(Path file, String domain) throws IOException {
        JsonObject root;
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        if (!string(root, "schemaVersion", "").equals("2")) throw new IOException("expected schemaVersion 2");
        if (!string(root, "domain", "").equals(domain)) throw new IOException("expected domain " + domain);
        return root;
    }

    private static void skip(Path file, Exception error) {
        brokenFiles++;
        System.out.println("[WynnRunica] JSON v2 skipped " + file.getFileName() + ": " + error.getMessage());
    }

    private static List<JsonObject> objects(JsonObject object, String key) {
        List<JsonObject> result = new ArrayList<>();
        for (JsonElement element : array(object, key)) {
            if (element.isJsonObject()) result.add(element.getAsJsonObject());
        }
        return result;
    }

    private static List<JsonElement> array(JsonObject object, String key) {
        List<JsonElement> result = new ArrayList<>();
        if (object.has(key) && object.get(key).isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray(key)) result.add(element);
        }
        return result;
    }

    private static JsonObject object(JsonObject object, String key) {
        if (object.has(key) && object.get(key).isJsonObject()) return object.getAsJsonObject(key);
        return new JsonObject();
    }

    private static String string(JsonObject object, String key, String fallback) {
        if (object.has(key) && object.get(key).isJsonPrimitive()) return object.get(key).getAsString();
        return fallback;
    }

    private static HashMap<String, String> mapFor(HashMap<String, HashMap<String, String>> byQuest, String quest) {
        HashMap<String, String> map = byQuest.get(quest);
        if (map == null) {
            map = new HashMap<>();
            byQuest.put(quest, map);
        }
        return map;
    }

    private static HashMap<String, String> mapFor(HashMap<String, HashMap<String, HashMap<String, String>>> byQuest,
                                                  String quest, String kind) {
        HashMap<String, HashMap<String, String>> kinds = byQuest.get(quest);
        if (kinds == null) {
            kinds = new HashMap<>();
            byQuest.put(quest, kinds);
        }
        return mapFor(kinds, kind);
    }

    private static List<String> listFor(HashMap<String, List<String>> byQuest, String quest) {
        List<String> list = byQuest.get(quest);
        if (list == null) {
            list = new ArrayList<>();
            byQuest.put(quest, list);
        }
        return list;
    }

    private static List<Path> jsonFiles(Path folder) {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(folder)) return files;
        try (var paths = Files.walk(folder)) {
            for (Path path : paths.toList()) {
                if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json")) files.add(path);
            }
        } catch (IOException error) {
            System.out.println("[WynnRunica] Cannot list " + folder + ": " + error.getMessage());
        }
        Collections.sort(files);
        return files;
    }

    static void copyBundledJsonIfMissing(String resourceFolder, Path destination) {
        try {
            Files.createDirectories(destination);
            Path jar = Paths.get(TranslationLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!Files.isRegularFile(jar)) return;
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || !name.startsWith(resourceFolder + "/") || !name.endsWith(".json")) continue;
                    Path target = destination.resolve(name.substring(resourceFolder.length() + 1));
                    if (Files.exists(target)) continue;
                    Files.createDirectories(target.getParent());
                    try (InputStream input = zip.getInputStream(entry)) {
                        Files.copy(input, target);
                    }
                }
            }
        } catch (IOException | URISyntaxException error) {
            System.out.println("[WynnRunica] Cannot install bundled JSON " + resourceFolder + ": " + error.getMessage());
        }
    }

    public static boolean hasCyrillic(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 'а' && c <= 'я') || (c >= 'А' && c <= 'Я') || c == 'ё' || c == 'Ё') return true;
        }
        return false;
    }
}
