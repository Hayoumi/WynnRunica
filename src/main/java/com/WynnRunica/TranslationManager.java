package com.WynnRunica;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TranslationManager {
    public static final HashMap<String, String> translations = new HashMap<>();
    public static final HashMap<String, String> guiTranslations = new HashMap<>();
    public static final HashMap<String, String> npcTranslations = new HashMap<>();

    static final HashMap<String, String> keyToQuest = new HashMap<>();
    static final HashSet<String> ambiguousKeys = new HashSet<>();
    static final HashMap<String, String> questIds = new HashMap<>();
    static final HashMap<String, HashSet<String>> questSpeakers = new HashMap<>();
    static final HashMap<String, List<String>> questToKeys = new HashMap<>();
    static final HashMap<String, HashMap<String, String>> questTranslations = new HashMap<>();
    static final HashMap<String, HashMap<String, String>> questChoiceTranslations = new HashMap<>();
    static final HashMap<String, HashMap<String, HashMap<String, String>>> questTranslationsByKind = new HashMap<>();
    static final HashMap<String, HashMap<String, HashMap<String, String>>> questEntryIds = new HashMap<>();

    public static final Map<String, List<GuiScope>> scopes = new HashMap<>();
    public static final Map<String, String> archetypes = new HashMap<>();
    private static final List<GuiPattern> guiPatterns = new ArrayList<>();
    static final HashMap<String, String[]> statNames = new HashMap<>();

    static void addStatName(String en, String ru) {
        String name = structuralPixelLabel(en);
        String translated = structuralPixelLabel(ru);
        if (name == null || translated == null) return;
        String[] forms = statNames.computeIfAbsent(name, key -> new String[2]);
        if (isAfterNumber(en)) forms[1] = translated;
        else forms[0] = translated;
    }

    private static final Pattern PLURAL_TOKEN = Pattern.compile("<pl:([^|<>]*)\\|([^|<>]*)\\|([^|<>]*)>");

    private static boolean isAfterNumber(String text) {
        String clean = pixelPlainText(text);
        Matcher number = NUMBER_OR_TOKEN.matcher(clean);
        return number.find() && !hasLetter(clean.substring(0, number.start()));
    }

    private static String currentQuest = null;
    private static String fuzzyText = null;
    private static String fuzzyQuest = null;
    private static String fuzzyResult = null;
    private static final DialogueTypingMatcher dialogueTypingMatcher = new DialogueTypingMatcher();

    public record GuiPattern(Pattern pattern, String translationTemplate) {}
    public record GuiLabelMatch(String source, String translation, String unitSource, String unitTranslation) {}

    public interface NpcRefreshable {
        void wr$refresh();
    }

    private static final String COLOR_CODE = "(?:§#[0-9a-fA-F]{6}|§[0-9a-fA-FklmnorKLMNOR])";
    static final String COINS_VALUE = "\\((?:\\d+(?:[.,]\\d+)*(?:stx|¼²|²½|²) ?)+\\)";
    private static final Pattern COINS_TEMPLATE = Pattern.compile(
            "\\((?:" + COLOR_CODE + "*<num>" + COLOR_CODE + "*(?:stx|¼²|²½|²)" + COLOR_CODE + "* ?)+\\)");

    static String foldCoins(String template) {
        return COINS_TEMPLATE.matcher(template).replaceAll("<num>");
    }
    private static final String ARCHETYPE_NAMES = "(?:Paladin|Battle Monk|Fallen|Trickster|Acrobat|Shadestepper|Riftwalker|Lightwielder|Light Bender|Arcanist|Sharpshooter|Boltslinger|Trapper|Acolyte|Summoner|Ritualist)";

    private static final Pattern REQUIRED_ABILITY = Pattern.compile(
            "^(.*Required Ability:\\s*" + COLOR_CODE + "?)\\s*(.+)$");
    private static final Pattern REQUIRED_BULLET = Pattern.compile(
            "^((?:§r)?§c-\\s*" + COLOR_CODE + "?)\\s*(.+)$");
    private static final Pattern ARCHETYPE_COUNT = Pattern.compile(
            "^(.*?)(?:Min\\s+)?(" + ARCHETYPE_NAMES + ")\\s+Archetype:\\s*" + COLOR_CODE + "?\\s*(\\d+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ARCHETYPE_LABEL = Pattern.compile(
            "^((?:" + COLOR_CODE + "|\\s)*)(" + ARCHETYPE_NAMES + ")\\s+Archetype$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("(?<!§)[+\\-]?\\d+(?:[.,/]\\d+)*");
    private static final Pattern NUMBER_OR_TOKEN = Pattern.compile("<num>|[+\\-]?\\d+(?:[.,/]\\d+)*");
    private static final Pattern DASH_BEFORE_NUMBER = Pattern.compile("-(?:§(?:#[0-9a-fA-F]{6}|.))*$");
    private static final Pattern TRAILING_COLORS =Pattern.compile("§(?:#[0-9a-fA-F]{6}|[0-9a-fA-FklmnorKLMNOR])+");
    private static final Pattern UNIT_WORDS = Pattern.compile("(?iU)\\b(?:to|s|tier|hits/s|hits|уд\\.?/с|уд|ур|с)\\b");

    public static int reload() {
        NpcNameResolver.clearCache();
        ChoiceTranslator.clearCache();
        ChoiceFrameStitcher.clear();
        dialogueTypingMatcher.reset();
        fuzzyText = null;
        fuzzyQuest = null;
        fuzzyResult = null;

        translations.clear();
        guiTranslations.clear();
        npcTranslations.clear();
        keyToQuest.clear();
        ambiguousKeys.clear();
        questIds.clear();
        questSpeakers.clear();
        questToKeys.clear();
        questTranslations.clear();
        questChoiceTranslations.clear();
        questTranslationsByKind.clear();
        questEntryIds.clear();
        scopes.clear();
        archetypes.clear();

        int brokenFiles = TranslationLoader.loadAll(FabricLoader.getInstance().getConfigDir().resolve("WynnRunica"));
        try {
            ChatMessageCatalog.reload();
        } catch (IllegalStateException error) {
            System.out.println("[WynnRunica] Chat catalog kept: " + error.getMessage());
        }
        reloadGuiPatterns();
        return brokenFiles;
    }

    static void reloadGuiPatterns() {
        guiPatterns.clear();
        for (Map.Entry<String, String> entry : guiTranslations.entrySet()) {
            String key = foldCoins(entry.getKey());
            if (!key.contains("<num>")) continue;

            String[] parts = key.split("<num>", -1);
            StringBuilder regex = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (i > 0) regex.append("(" + COLOR_CODE + "*(?:" + COINS_VALUE + "|[+\\-]?\\d+(?:[.,/]\\d+)*))");
                if (i < parts.length - 1) part = part.replaceAll(COLOR_CODE + "+$", "");
                regex.append(Pattern.quote(part));
            }
            guiPatterns.add(new GuiPattern(Pattern.compile(regex.toString()), foldCoins(entry.getValue())));
        }
    }

    public static void refreshNpcs() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        for (Entity entity : client.world.getEntities()) {
            if (entity instanceof NpcRefreshable refreshable) refreshable.wr$refresh();
        }
    }

    static String lookupKey(String text) {
        return text.replace(" ", "").toLowerCase(Locale.ROOT);
    }

    public static String getTranslation(String text, boolean updateQuest) {
        String key = lookupKey(text);
        if (updateQuest) detectCurrentQuest();

        Map<String, String> questLines = null;
        if (currentQuest != null) questLines = questTranslations.get(currentQuest);
        if (questLines != null && questLines.containsKey(key)) return questLines.get(key);

        String exact = translations.get(key);
        if (exact != null) {
            if (updateQuest && keyToQuest.containsKey(key) && !ambiguousKeys.contains(key)) {
                currentQuest = keyToQuest.get(key);
            }
            return exact;
        }

        List<String> numbers = new ArrayList<>();
        Matcher number = NUMBER.matcher(text);
        while (number.find()) numbers.add(number.group());
        if (!numbers.isEmpty()) {
            String template = translations.get(lookupKey(NUMBER.matcher(text).replaceAll("<num>")));
            if (template != null) return fillTemplate(template, numbers);
        }

        if (currentQuest == null) return text;
        if (key.equals(fuzzyText) && currentQuest.equals(fuzzyQuest)) return fuzzyResult;
        List<String> questKeys = questToKeys.get(currentQuest);
        if (questKeys == null || questKeys.isEmpty()) return text;

        String bestKey = null;
        double bestScore = 0.0;
        for (String candidate : questKeys) {
            int longer = Math.max(key.length(), candidate.length());
            if (Math.abs(key.length() - candidate.length()) > longer * 0.18) continue;
            double score = Epstein.similarity(key, candidate);
            if (score > bestScore) {
                bestScore = score;
                bestKey = candidate;
            }
        }

        String result = text;
        if (bestScore > 0.82) {
            if (questLines != null && questLines.containsKey(bestKey)) {
                result = questLines.get(bestKey);
            } else {
                result = translations.get(bestKey);
            }
        }
        result = fillTemplate(result, numbers);
        fuzzyText = key;
        fuzzyQuest = currentQuest;
        fuzzyResult = result;
        return result;
    }

    public static boolean hasExactTranslation(String text) {
        return text != null && translations.containsKey(lookupKey(text));
    }

    public static boolean hasExactTranslationInContext(String text, String quest, String kind) {
        return getTranslationInContext(text, quest, kind) != null;
    }

    public static String getTranslationInContext(String text, String quest, String kind) {
        if (text == null || text.isBlank() || quest == null || quest.isBlank()) return null;
        HashMap<String, HashMap<String, String>> kinds = questTranslationsByKind.get(quest.trim());
        if (kinds == null || !kinds.containsKey(kind)) return null;
        HashMap<String, String> lines = kinds.get(kind);

        String exact = lines.get(lookupKey(text));
        if (exact != null) return exact;

        List<String> numbers = new ArrayList<>();
        Matcher number = NUMBER.matcher(text);
        while (number.find()) numbers.add(number.group());
        if (numbers.isEmpty()) return null;
        String template = lines.get(lookupKey(NUMBER.matcher(text).replaceAll("<num>")));
        if (template == null) return null;
        return fillTemplate(template, numbers);
    }

    public static String getEntryIdInContext(String text, String quest, String kind) {
        if (text == null || text.isBlank() || quest == null || quest.isBlank()) return "";
        HashMap<String, HashMap<String, String>> kinds = questEntryIds.get(quest.trim());
        if (kinds == null || !kinds.containsKey(kind)) return "";
        HashMap<String, String> ids = kinds.get(kind);

        String id = ids.get(lookupKey(text));
        if (id != null) return id;
        if (!kind.equals("objective")) return "";
        return ids.getOrDefault(lookupKey(NUMBER.matcher(text).replaceAll("<num>")), "");
    }

    public static String getQuestId(String quest) {
        if (quest == null || quest.isBlank()) return "";
        return questIds.getOrDefault(quest.trim(), quest.trim());
    }

    public static boolean isSpeakerInQuest(String quest, String speaker) {
        if (quest == null || speaker == null || speaker.isBlank()) return false;
        HashSet<String> speakers = questSpeakers.get(quest.trim());
        return speakers != null && speakers.contains(speaker.trim());
    }

    public static Map<String, String> getChoiceTranslations(String quest) {
        if (quest == null || !questChoiceTranslations.containsKey(quest.trim())) return Map.of();
        return questChoiceTranslations.get(quest.trim());
    }

    public static void observeExactDialogue(String text) {
        if (text != null && !text.isEmpty()) dialogueTypingMatcher.observeExact(lookupKey(text));
    }

    public static DialogueTypingMatcher.Result getTypingTranslation(String text, boolean settled) {
        return dialogueTypingMatcher.find(lookupKey(text), detectCurrentQuest(), settled,
                translations, questTranslations, questToKeys);
    }

    public static void resetTypingTranslation() {
        dialogueTypingMatcher.reset();
    }

    public static String getCurrentQuest() {
        return currentQuest;
    }

    private static String detectCurrentQuest() {
        QuestTracker.QuestInfo info = QuestTracker.detect();
        if (!info.name().isBlank()) currentQuest = info.name();
        return currentQuest;
    }

    public static void registerScope(GuiScope scope) {
        if (scope.nameEn == null || scope.nameEn.isBlank()) return;
        String name = cleanName(scope.nameEn);
        addScope(name, scope);
        addScope("unlock " + name + " ability", scope);
    }

    private static void addScope(String name, GuiScope scope) {
        List<GuiScope> list = scopes.get(name);
        if (list == null) {
            list = new ArrayList<>();
            scopes.put(name, list);
        }
        list.add(scope);
    }

    public static void registerArchetype(String en, String ru) {
        archetypes.put(cleanName(en), ru);
    }

    public static boolean isUnlockAbilityTitle(String text) {
        if (text == null) return false;
        String name = cleanName(text);
        return name.startsWith("unlock ") && name.endsWith(" ability");
    }

    private static String translateArchetypeLabel(String text) {
        if (text == null || !text.contains("Archetype")) return null;
        Matcher label = ARCHETYPE_LABEL.matcher(text);
        if (!label.matches()) return null;
        String name = cleanName(label.group(2));
        if (!archetypes.containsKey(name)) return null;
        return "Архетип: " + archetypes.get(name);
    }

    private static List<GuiScope> scopesFor(String title) {
        String name = cleanName(title);
        List<GuiScope> candidates = scopes.get(name);
        if ((candidates == null || candidates.isEmpty()) && isUnlockAbilityTitle(name)) {
            candidates = scopes.get(name.substring("unlock ".length(), name.length() - " ability".length()).trim());
        }
        if (candidates == null) return List.of();
        return candidates;
    }

    private static GuiScope findUniqueScope(String title) {
        List<GuiScope> candidates = scopesFor(title);
        if (candidates.size() == 1) return candidates.getFirst();
        return null;
    }

    public static GuiScope findScopeByTitle(String title, Iterable<String> loreLines, String screen, String itemId) {
        if (title == null || title.isBlank()) return null;
        List<GuiScope> candidates = scopesFor(title);
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.getFirst();

        GuiScope best = candidates.getFirst();
        int bestScore = -1;
        for (GuiScope candidate : candidates) {
            if (!candidate.matchesContext(screen, itemId)) continue;
            int score = candidate.matchScore(loreLines) + candidate.contextScore(screen, itemId);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    public static String cleanName(String text) {
        return text.replaceAll(COLOR_CODE, "").trim().toLowerCase(Locale.ROOT);
    }

    public static String getGuiTranslation(String text) {
        return getGuiTranslation(text, null);
    }

    public static String getGuiTranslation(String text, GuiScope scope) {
        if (text == null || text.isEmpty()) return text;
        String archetypeLabel = translateArchetypeLabel(text);
        if (archetypeLabel != null) return archetypeLabel;
        if (scope != null) {
            String local = scope.findTranslation(text);
            if (local != null) return local;
        }
        String requirement = translateRequirement(text);
        if (requirement != null) return requirement;
        String translated = findGuiTranslation(text);
        if (translated != null) return translated;
        return text;
    }

    private static String translateRequirement(String text) {
        if (text.contains("Required Ability:")) {
            Matcher match = REQUIRED_ABILITY.matcher(text);
            GuiScope ability = match.matches() ? findUniqueScope(match.group(2).trim()) : null;
            if (ability != null) {
                return match.group(1).replace("Required Ability:", "Требуется способность:") + ability.nameRu;
            }
        }

        if (text.startsWith("§c-") || text.startsWith("§r§c-") || text.startsWith("§c -")) {
            Matcher match = REQUIRED_BULLET.matcher(text);
            GuiScope ability = match.matches() ? findUniqueScope(match.group(2).trim()) : null;
            if (ability != null) return match.group(1) + ability.nameRu;
        }

        if (!text.contains("Archetype")) return null;
        Matcher count = ARCHETYPE_COUNT.matcher(text);
        if (count.matches() && archetypes.containsKey(cleanName(count.group(2)))) {
            String archetype = archetypes.get(cleanName(count.group(2)));
            String numberColor = "§f";
            if (count.group(1).contains("§8")) numberColor = "§7";
            if (text.toLowerCase(Locale.ROOT).contains("min")) {
                return "§7Мин. архетип: " + archetype + " " + numberColor + count.group(3);
            }
            return count.group(1) + "Архетип: " + archetype + " " + numberColor + count.group(3);
        }
        return translateArchetypeLabel(text);
    }

    public static String findGuiTranslation(String text) {
        if (text == null || text.isEmpty()) return null;
        String archetypeLabel = translateArchetypeLabel(text);
        if (archetypeLabel != null) return archetypeLabel;

        List<String> variants = new ArrayList<>();
        variants.add(text);
        String collapsed = collapseFormatting(text);
        if (!collapsed.equals(text)) variants.add(collapsed);
        String iconFirst = text.replaceAll("^§[0-9a-fA-F#]{1,7}<em>", "<em>");
        if (!iconFirst.equals(text)) variants.add(iconFirst);

        for (String variant : variants) {
            String exact = guiTranslations.get(variant);
            if (exact != null) return fillTemplate(exact, List.of());
        }
        if (!iconFirst.equals(text) && guiTranslations.containsKey("§7" + iconFirst)) {
            return fillTemplate(guiTranslations.get("§7" + iconFirst), List.of());
        }
        for (String variant : variants) {
            for (GuiPattern guiPattern : guiPatterns) {
                Matcher match = guiPattern.pattern().matcher(variant);
                if (match.matches()) return fillTemplate(guiPattern.translationTemplate(), match);
            }
        }
        return null;
    }

    private static String collapseFormatting(String text) {
        StringBuilder result = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '§' && i + 3 < text.length() && text.charAt(i + 2) == '§') {
                char first = Character.toLowerCase(text.charAt(i + 1));
                char second = Character.toLowerCase(text.charAt(i + 3));
                if ((isColorOrReset(first) && isColorOrReset(second)) || first == second) {
                    i += 2;
                    continue;
                }
            }
            result.append(text.charAt(i));
            i++;
        }
        return result.toString();
    }

    private static boolean isColorOrReset(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || c == 'r';
    }

    static String fillTemplate(String template, Matcher match) {
        List<String> numbers = new ArrayList<>();
        if (match != null) {
            for (int i = 1; i <= match.groupCount(); i++) numbers.add(match.group(i));
        }
        return fillTemplate(template, numbers);
    }

    static String fillTemplate(String template, List<String> numbers) {
        if (!template.contains("<num>") && !template.contains("<pl:")) return template;
        StringBuilder out = new StringBuilder(template.length());
        String number = null;
        int next = 0;
        int i = 0;
        while (i < template.length()) {
            if (template.startsWith("<num>", i)) {
                number = null;
                if (next < numbers.size()) number = numbers.get(next++);
                if (number == null) {
                    out.append("<num>");
                } else {
                    if (number.startsWith("§")) removeTrailingColor(out);
                    if (number.startsWith("-") && DASH_BEFORE_NUMBER.matcher(out).find()) number = number.substring(1);
                    out.append(number);
                }
                i += "<num>".length();
                continue;
            }
            if (template.startsWith("<pl:", i)) {
                int end = template.indexOf('>', i);
                String[] forms = new String[0];
                if (end > 0) forms = template.substring(i + "<pl:".length(), end).split("\\|", -1);
                if (forms.length == 3) {
                    out.append(pluralForm(number, forms));
                    i = end + 1;
                    continue;
                }
            }
            out.append(template.charAt(i));
            i++;
        }
        return out.toString();
    }

    private static void removeTrailingColor(StringBuilder out) {
        int last = out.lastIndexOf("§");
        if (last < 0 || last < out.length() - 8) return;
        if (TRAILING_COLORS.matcher(out.substring(last)).matches()) out.setLength(last);
    }

    static String pluralForm(String number, String[] forms) {
        if (number == null) return forms[2];
        if (number.contains(".") || number.contains(",") || number.contains("/")) return forms[1];
        long n;
        try {
            n = Long.parseLong(number.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException error) {
            return forms[2];
        }
        long lastTwo = n % 100;
        long last = n % 10;
        if (last == 1 && lastTwo != 11) return forms[0];
        if (last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14)) return forms[1];
        return forms[2];
    }

    public static GuiLabelMatch findGuiLabelTranslation(String text, String fullTranslation) {
        String source = structuralPixelLabel(text);
        String translated = structuralPixelLabel(fullTranslation);
        if (source != null && translated != null && !source.equals(translated)) {
            String unit = structuralPixelUnit(text);
            String unitTranslation = structuralPixelUnit(fullTranslation);
            if (unit != null && unitTranslation != null && !unit.equals(unitTranslation)) {
                return new GuiLabelMatch(source, translated, unit, unitTranslation);
            }
            return new GuiLabelMatch(source, translated, null, null);
        }
        String[] known = statNames.get(source);
        if (known == null) return null;
        String name = known[0];
        if (isAfterNumber(text) && known[1] != null) name = known[1];
        if (name == null) name = known[1];

        Matcher number = NUMBER_OR_TOKEN.matcher(pixelPlainText(text));
        String value = number.find() && !number.group().equals("<num>") ? number.group() : null;
        Matcher token = PLURAL_TOKEN.matcher(name);
        StringBuilder out = new StringBuilder();
        while (token.find()) {
            String[] forms = {token.group(1), token.group(2), token.group(3)};
            token.appendReplacement(out, Matcher.quoteReplacement(pluralForm(value, forms)));
        }
        return new GuiLabelMatch(source, token.appendTail(out).toString(), null, null);
    }

    private static String pixelPlainText(String value) {
        return TextUtils.extractCleanText(value.replaceAll("§(?:#[0-9a-fA-F]{6}|.)", "").replace("<em>", " "));
    }

    private static boolean hasLetter(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) return true;
        }
        return false;
    }

    private static String structuralPixelLabel(String value) {
        if (value == null) return null;
        String clean = pixelPlainText(value);
        Matcher number = NUMBER_OR_TOKEN.matcher(clean);
        if (!number.find()) return null;
        String before = clean.substring(0, number.start()).trim();
        int end = number.end();
        while (number.find() && !hasLetter(clean.substring(end, number.start()))) end = number.end();
        String after = clean.substring(end).trim();

        if (hasLetter(before)) {
            String extraWords = UNIT_WORDS.matcher(after.replace("<num>", "")).replaceAll("");
            if (hasLetter(extraWords)) return null;
            return before.replaceAll("[^\\p{L}]+$", "").replaceFirst("^[^\\p{L}]+", "").trim();
        }
        if (after.isEmpty() || after.split("\\s+").length > 3) return null;
        if (!PLURAL_TOKEN.matcher(after).replaceAll("").replaceAll("[\\p{L}\\s'’\\-/.%]", "").isEmpty()) return null;
        return after;
    }

    private static String structuralPixelUnit(String value) {
        if (value == null) return null;
        String clean = pixelPlainText(value);
        Matcher number = NUMBER_OR_TOKEN.matcher(clean);
        if (!number.find() || !hasLetter(clean.substring(0, number.start()))) return null;
        String unit = clean.substring(number.end()).trim().replaceAll("^[^\\p{L}]+|[^\\p{L}./]+$", "");
        if (!hasLetter(unit)) return null;
        return unit;
    }
}
