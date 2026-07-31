package com.WynnRunica;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TranslationPrinter {
    public static HashMap<String, String> translations = new HashMap<>();
    public static HashMap<String, String> guiTranslations = new HashMap<>();
    public static final HashMap<String, List<String>> questToKeys = new HashMap<>();
    private static String currentQuest = null;

    private static String fuzzyCacheText = null;
    private static String fuzzyCacheQuest = null;
    private static String fuzzyCacheResult = null;

    public static class GuiPattern {
        public final java.util.regex.Pattern pattern;
        public final String translationTemplate;

        public GuiPattern(java.util.regex.Pattern pattern, String translationTemplate) {
            this.pattern = pattern;
            this.translationTemplate = translationTemplate;
        }
    }

    public static final List<GuiPattern> guiPatterns = new ArrayList<>();
    private static final HashMap<String, String> guiLabelTranslations = new HashMap<>();
    private static final java.util.HashSet<String> ambiguousGuiLabels = new java.util.HashSet<>();

    public static void reload() {
        HashMap<String, String> rawTranslations = TranslationLoader.loadFromConfig();
        guiTranslations = TranslationLoader.loadGuiFromConfig();

        HashMap<String, String> rawKeyToQuest = new HashMap<>(TranslationLoader.keyToQuest);
        TranslationLoader.keyToQuest.clear();
        questToKeys.clear();
        fuzzyCacheText = null; fuzzyCacheQuest = null; fuzzyCacheResult = null;

        HashMap<String, String> cleanTranslations = new HashMap<>();
        for (java.util.Map.Entry<String, String> entry : rawTranslations.entrySet()) {
            String rawKey = entry.getKey();
            String cleanKey = rawKey.replace(" ", "").toLowerCase();
            String ruText = entry.getValue();
            cleanTranslations.put(cleanKey, ruText);

            String quest = rawKeyToQuest.get(rawKey);
            if (quest != null) {
                TranslationLoader.keyToQuest.put(cleanKey, quest);
                questToKeys.computeIfAbsent(quest, q -> new ArrayList<>()).add(cleanKey);
            }
        }
        translations = cleanTranslations;

        guiPatterns.clear();
        guiLabelTranslations.clear();
        ambiguousGuiLabels.clear();
        for (String key : guiTranslations.keySet()) {
            String label = structuralPixelLabel(key);
            String translatedLabel = structuralPixelLabel(guiTranslations.get(key));
            if (hasPixelAlignment(key)
                    && label != null && translatedLabel != null && !label.equals(translatedLabel)
                    && !ambiguousGuiLabels.contains(label)) {
                String previous = guiLabelTranslations.putIfAbsent(label, translatedLabel);
                if (previous != null && !previous.equals(translatedLabel)) {
                    guiLabelTranslations.remove(label);
                    ambiguousGuiLabels.add(label);
                }
            }
            if (!key.contains("<num>")) continue;

            String[] parts = key.split("<num>", -1);
            StringBuilder pb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) pb.append("([+\\-]?\\d+(?:[.,/]\\d+)*)");
                pb.append(java.util.regex.Pattern.quote(parts[i]));
            }
            try {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pb.toString());
                guiPatterns.add(new GuiPattern(p, guiTranslations.get(key)));
            } catch (Exception e) {
                System.out.println("[WynnRunica] Failed to compile pattern for: " + key);
            }
        }
    }


    public static String getTranslation(String text, boolean updateQuest) {
        String cleanText = dialogueLookupKey(text);

        String exact = translations.get(cleanText);
        if (exact != null) {
            if (updateQuest) {
                String newQuest = TranslationLoader.keyToQuest.get(cleanText);
                if (newQuest != null && !newQuest.equals(currentQuest)
                        && !TranslationLoader.ambiguousKeys.contains(cleanText)) {
                    currentQuest = newQuest;
                }
            }
            return exact;
        }

        if (currentQuest == null) return text;

        if (cleanText.equals(fuzzyCacheText) && currentQuest.equals(fuzzyCacheQuest)) {
            return fuzzyCacheResult;
        }

        List<String> keysOfQuest = questToKeys.get(currentQuest);
        if (keysOfQuest == null || keysOfQuest.isEmpty()) return text;

        String bestKey = null;
        double bestScore = 0.0;
        for (String key : keysOfQuest) {
            double score = Epstein.similarity(cleanText, key);
            if (score > bestScore) {
                bestScore = score;
                bestKey = key;
            }
        }

        String result = (bestScore > 0.82) ? translations.get(bestKey) : text;
        fuzzyCacheText = cleanText;
        fuzzyCacheQuest = currentQuest;
        fuzzyCacheResult = result;
        return result;
    }

    public static boolean hasExactTranslation(String text) {
        return text != null && translations.containsKey(dialogueLookupKey(text));
    }

    public static String getCurrentQuest() {
        return currentQuest;
    }

    private static String dialogueLookupKey(String text) {
        return text.replace(" ", "").toLowerCase(Locale.ROOT);
    }

    public static String getGuiTranslation(String text) {
        if (text == null || text.isEmpty()) return text;

        String translated = findGuiTranslation(text);
        if (translated != null) return translated;

        return text;
    }

    public static String findGuiTranslation(String text) {
        if (text == null || text.isEmpty()) return null;

        String exact = guiTranslations.get(text);
        if (exact != null) return fillTemplate(exact, null);

        for (GuiPattern gp : guiPatterns) {
            java.util.regex.Matcher m = gp.pattern.matcher(text);
            if (m.matches()) {
                return fillTemplate(gp.translationTemplate, m);
            }
        }
        return null;
    }

    private static String fillTemplate(String template, java.util.regex.Matcher m) {
        if (!template.contains("<num>") && !template.contains("<pl:")) return template;
        StringBuilder out = new StringBuilder(template.length());
        String current = null;
        int group = 1, i = 0;
        while (i < template.length()) {
            if (template.startsWith("<num>", i)) {
                current = m != null && group <= m.groupCount() ? m.group(group++) : null;
                out.append(current != null ? current : "<num>");
                i += "<num>".length();
            } else if (template.startsWith("<pl:", i)) {
                int end = template.indexOf('>', i);
                String[] forms = end < 0 ? null : template.substring(i + 4, end).split("\\|", -1);
                if (forms == null || forms.length != 3) {
                    out.append(template.charAt(i++));
                } else {
                    out.append(pluralForm(current, forms));
                    i = end + 1;
                }
            } else {
                out.append(template.charAt(i++));
            }
        }
        return out.toString();
    }

    private static String pluralForm(String number, String[] forms) {
        if (number == null) return forms[2];
        if (number.matches(".*[.,/].*")) return forms[1];
        long n;
        try {
            n = Long.parseLong(number.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return forms[2];
        }
        long hundreds = n % 100, units = n % 10;
        if (units == 1 && hundreds != 11) return forms[0];
        if (units >= 2 && units <= 4 && (hundreds < 12 || hundreds > 14)) return forms[1];
        return forms[2];
    }

    public record GuiLabelMatch(String source, String translation) {}

    public static GuiLabelMatch findGuiLabelTranslation(String text) {
        return findGuiLabelTranslation(text, findGuiTranslation(text));
    }

    public static GuiLabelMatch findGuiLabelTranslation(String text, String fullTranslation) {
        String source = structuralPixelLabel(text);
        String exact = structuralPixelLabel(fullTranslation);
        if (source != null && exact != null && !source.equals(exact))
            return new GuiLabelMatch(source, exact);
        String translation = guiLabelTranslations.get(source);
        return translation == null ? null : new GuiLabelMatch(source, translation);
    }

    private static String structuralPixelLabel(String value) {
        if (value == null) return null;
        String clean = TextUtils.extractCleanText(value
                .replaceAll("\u00A7(?:#[0-9a-fA-F]{6}|.)", "")
                .replace("<em>", " "));
        var number = java.util.regex.Pattern.compile("(?:<num>|[+\\-]?\\d+(?:[.,/]\\d+)*)")
                .matcher(clean);
        if (!number.find()) return null;
        String before = clean.substring(0, number.start()).trim();
        String after = clean.substring(number.end()).trim();
        if (before.codePoints().anyMatch(Character::isLetter)) {
            String words = after.replace("<num>", "")
                    .replaceAll("(?i)\\b(?:to|s)\\b", "")
                    .replaceAll("[^\\p{L}]", "");
            return words.isEmpty() ? before.replaceFirst("^[^\\p{L}]*", "").trim() : null;
        }
        return !after.isEmpty() && after.split("\\s+").length <= 3
                && after.replaceAll("[\\p{L}\\s'’\\-]", "").isEmpty()
                ? after : null;
    }

    private static boolean hasPixelAlignment(String value) {
        return value != null && value.codePoints()
                .anyMatch(codePoint -> codePoint >= 0xC0000 && codePoint <= 0xDFFFF);
    }

    /* public static int getTranslationsCount() {
        return translations.size();
    }   забыл зачем )))
    */

}
