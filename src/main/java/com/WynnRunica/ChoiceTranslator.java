package com.WynnRunica;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class ChoiceTranslator {

    private static final int MIN_SUBSTRING_MATCH_LENGTH = 7;
    private static final Map<String, CachedMatch> matchCache = new HashMap<>();

    private record CachedMatch(String fullEn, String fullRu) {}

    private ChoiceTranslator() {}

    public static boolean hasTranslation(String originalKey, String quest) {
        if (originalKey == null || originalKey.isBlank() || quest == null || quest.isBlank()) return false;

        String exact = getExactTranslation(originalKey, quest);
        if (exact != null && !exact.isBlank()) return true;

        if (originalKey.length() >= MIN_SUBSTRING_MATCH_LENGTH) {
            CachedMatch match = findSubstringMatch(originalKey, quest);
            return match != null;
        }

        return false;
    }

    public static String findFullTranslation(String originalKey, String quest) {
        if (originalKey == null || originalKey.isBlank() || quest == null || quest.isBlank()) return null;

        String exact = getExactTranslation(originalKey, quest);
        if (exact != null && !exact.isBlank()) return exact;

        if (originalKey.length() >= MIN_SUBSTRING_MATCH_LENGTH) {
            CachedMatch match = findSubstringMatch(originalKey, quest);
            if (match != null) return match.fullRu();
        }

        return null;
    }

    private static String getExactTranslation(String originalKey, String quest) {
        if (quest == null || quest.isBlank()) return null;
        String translation = TranslationManager.getTranslationInContext(originalKey, quest, "choice");
        if (translation != null && !translation.isBlank()) return translation;
        return null;
    }

    private static CachedMatch findSubstringMatch(String originalKey, String quest) {
        if (quest == null || quest.isBlank()) return null;

        String cacheKey = quest + "#" + originalKey;
        CachedMatch cached = matchCache.get(cacheKey);
        if (cached != null) return cached;

        Map<String, String> choices = TranslationManager.getChoiceTranslations(quest);
        if (choices.isEmpty()) return null;

        String queryLower = originalKey.toLowerCase(Locale.ROOT);
        CachedMatch match = searchMap(choices, queryLower);
        if (match != null) {
            cacheResult(cacheKey, match);
            return match;
        }

        return null;
    }

    private static CachedMatch searchMap(Map<String, String> map, String queryLower) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String en = entry.getKey();
            if (en.length() < queryLower.length()) continue;
            String enLower = en.toLowerCase(Locale.ROOT);
            if (enLower.contains(queryLower)) {
                return new CachedMatch(en, entry.getValue());
            }
        }
        return null;
    }

    private static void cacheResult(String key, CachedMatch match) {
        if (matchCache.size() > 500) {
            matchCache.clear();
        }
        matchCache.put(key, match);
    }

    public static void clearCache() {
        matchCache.clear();
    }

}
