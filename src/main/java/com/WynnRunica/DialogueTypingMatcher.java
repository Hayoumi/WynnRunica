package com.WynnRunica;

import java.util.List;
import java.util.Map;

public final class DialogueTypingMatcher {
    private static final int QUEST_PREFIX_MINIMUM = 3;
    private static final int GLOBAL_PREFIX_MINIMUM = 9;
    private static final int FUZZY_PREFIX_MINIMUM = 8;

    private String previous = "";
    private String lockedSource;
    private int sourceProgress;
    private String lastCompletedSource;
    private String missed;
    private long lastCompletedAt;

    public Result find(String observed, String quest, boolean settled,
                       Map<String, String> translations,
                       Map<String, ? extends Map<String, String>> questTranslations,
                       Map<String, List<String>> questToKeys) {
        if (observed == null || observed.isEmpty()) {
            reset();
            return null;
        }

        if (!previous.isEmpty() && !observed.startsWith(previous)) {
            rememberLockedSource();
            clearLock();
        }
        previous = observed;

        if (lockedSource != null && isAnotherLine(observed, lockedSource, settled)) clearLock();
        // Поиск по всем репликам квеста дорогой, поэтому для одного и того же текста он идёт один раз,
        // а не на каждом кадре.
        if (lockedSource == null && !observed.equals(missed)) {
            lockedSource = findCandidate(observed, quest, translations, questTranslations, questToKeys,
                    recentCompletedSource());
            sourceProgress = 0;
            if (lockedSource != null && isAnotherLine(observed, lockedSource, settled)) clearLock();
            if (lockedSource == null) missed = observed;
        }
        if (lockedSource == null) return null;

        Alignment alignment = alignPrefix(observed, lockedSource);
        boolean diverged = !lockedSource.startsWith(observed);

        int observedProgress = diverged
                ? Math.min(lockedSource.length(), observed.length()) : 0;
        sourceProgress = Math.max(sourceProgress,
                Math.max(alignment.sourceLength(), observedProgress));
        if (settled || observed.equals(lockedSource)) sourceProgress = lockedSource.length();

        Map<String, String> scopedTranslations = quest == null ? null : questTranslations.get(quest);
        String translation = scopedTranslations == null ? null : scopedTranslations.get(lockedSource);
        if (translation == null) translation = translations.get(lockedSource);
        if (translation == null || translation.isEmpty()) return null;

        Result result = new Result(lockedSource, translation,
                proportionalPrefix(translation, sourceProgress, lockedSource.length()));
        return result;
    }

    public void reset() {
        previous = "";
        missed = null;
        clearLock();
    }

    public void observeExact(String source) {
        if (source == null || source.isEmpty()) return;
        if (lockedSource != null && source.equals(lockedSource)) {
            remember(source);
        } else if (!source.equals(lastCompletedSource)) {
            remember(source);
        }
        previous = source;
        clearLock();
    }

    private void rememberLockedSource() {
        if (lockedSource != null && sourceProgress >= Math.min(lockedSource.length(), 8)) {
            remember(lockedSource);
        }
    }

    private void remember(String source) {
        lastCompletedSource = source;
        lastCompletedAt = System.nanoTime();
    }

    private String recentCompletedSource() {
        return lastCompletedSource != null
                && System.nanoTime() - lastCompletedAt <= 15_000_000_000L
                ? lastCompletedSource : null;
    }

    private void clearLock() {
        lockedSource = null;
        sourceProgress = 0;
    }

    private static String findCandidate(String observed, String quest,
                                        Map<String, String> translations,
                                        Map<String, ? extends Map<String, String>> questTranslations,
                                        Map<String, List<String>> questToKeys,
                                        String previousSource) {
        List<String> scoped = quest == null ? null : questToKeys.get(quest);
        Map<String, String> scopedTranslations = quest == null ? null : questTranslations.get(quest);
        Map<String, String> local = scopedTranslations == null ? translations : scopedTranslations;
        String source = contextualNextPrefix(observed, scoped, local, previousSource);
        if (source != null) return source;

        source = uniquePrefix(observed, scoped, local, QUEST_PREFIX_MINIMUM);
        if (source != null) return source;

        source = uniquePrefix(observed, translations.keySet(), translations,
                GLOBAL_PREFIX_MINIMUM);
        if (source != null) return source;

        return fuzzyPrefix(observed, scoped, local);
    }

    private static String contextualNextPrefix(String observed, List<String> candidates,
                                               Map<String, String> translations,
                                               String previousSource) {
        if (previousSource == null || candidates == null
                || observed.length() < QUEST_PREFIX_MINIMUM) return null;
        for (int index = 0; index + 1 < candidates.size(); index++) {
            if (!previousSource.equals(candidates.get(index))) continue;
            String next = candidates.get(index + 1);
            if (translations.containsKey(next) && next.startsWith(observed)) return next;
        }
        return null;
    }

    private static String uniquePrefix(String observed, Iterable<String> candidates,
                                       Map<String, String> translations, int minimum) {
        if (candidates == null || observed.length() < minimum) return null;

        String match = null;
        int matches = 0;
        for (String candidate : candidates) {
            if (!translations.containsKey(candidate) || !candidate.startsWith(observed)) continue;
            match = candidate;
            matches++;
            if (matches > 1) return null;
        }
        return matches == 1 ? match : null;
    }

    private static String fuzzyPrefix(String observed, Iterable<String> candidates,
                                      Map<String, String> translations) {
        if (candidates == null || observed.length() < FUZZY_PREFIX_MINIMUM) return null;

        String best = null;
        double bestScore = 0.0;
        double secondScore = 0.0;
        for (String candidate : candidates) {
            if (!translations.containsKey(candidate)) continue;
            // Похожая реплика начинается почти так же: из первых трёх букв совпадают хотя бы две.
            // Это отсекает почти все чужие реплики до дорогого сравнения.
            int same = 0;
            for (int i = 0; i < 3 && i < candidate.length(); i++) {
                if (candidate.charAt(i) == observed.charAt(i)) same++;
            }
            if (same < 2) continue;
            double score = alignPrefix(observed, candidate).similarity();
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                best = candidate;
            } else if (score > secondScore) {
                secondScore = score;
            }
        }

        double requiredScore = requiredSimilarity(observed.length());
        double requiredMargin = observed.length() < 24 ? 0.08 : 0.04;
        return bestScore >= requiredScore && bestScore - secondScore >= requiredMargin
                ? best : null;
    }

    private static boolean isAnotherLine(String observed, String source, boolean settled) {
        if (source.startsWith(observed)) return false;
        if (settled) return Epstein.similarity(observed, source) < 0.82;
        return alignPrefix(observed, source).similarity() < requiredSimilarity(observed.length());
    }

    private static double requiredSimilarity(int length) {
        if (length < 16) return 0.82;
        if (length < 32) return 0.74;
        return 0.68;
    }

    private static Alignment alignPrefix(String observed, String source) {
        // Начало реплики длиннее набранного в полтора раза уже не может быть на него похоже
        // (сходство ниже любого порога), поэтому дальше этой длины строка не сравнивается.
        int sourceLength = Math.min(source.length(), observed.length() * 3 / 2 + 4);
        int[] previousRow = new int[sourceLength + 1];
        int[] currentRow = new int[sourceLength + 1];
        for (int j = 0; j <= sourceLength; j++) previousRow[j] = j;

        for (int i = 1; i <= observed.length(); i++) {
            currentRow[0] = i;
            char observedChar = observed.charAt(i - 1);
            for (int j = 1; j <= sourceLength; j++) {
                int replace = previousRow[j - 1]
                        + (observedChar == source.charAt(j - 1) ? 0 : 1);
                int insert = currentRow[j - 1] + 1;
                int delete = previousRow[j] + 1;
                currentRow[j] = Math.min(replace, Math.min(insert, delete));
            }
            int[] swap = previousRow;
            previousRow = currentRow;
            currentRow = swap;
        }

        int bestLength = 1;
        int bestDistance = previousRow[1];
        double bestSimilarity = similarity(observed.length(), 1, bestDistance);
        for (int length = 2; length <= sourceLength; length++) {
            int distance = previousRow[length];
            double similarity = similarity(observed.length(), length, distance);
            if (similarity > bestSimilarity
                    || similarity == bestSimilarity && length > bestLength) {
                bestLength = length;
                bestDistance = distance;
                bestSimilarity = similarity;
            }
        }
        return new Alignment(bestLength, bestDistance, bestSimilarity);
    }

    private static double similarity(int observedLength, int sourceLength, int distance) {
        return 1.0 - (double) distance / Math.max(observedLength, sourceLength);
    }

    private static String proportionalPrefix(String value, int typedLength, int sourceLength) {
        if (sourceLength <= 0 || typedLength >= sourceLength) return value;
        int visible = visibleLength(value);
        int target = Math.max(1, Math.round((float) visible * typedLength / sourceLength));
        return takeVisible(value, target);
    }

    private static int visibleLength(String value) {
        int count = 0;
        for (int index = 0; index < value.length();) {
            if (value.startsWith("<playername>", index)) {
                count++;
                index += "<playername>".length();
                continue;
            }
            int next = formattingEnd(value, index);
            if (next > index) {
                index = next;
                continue;
            }
            int codePoint = value.codePointAt(index);
            count++;
            index += Character.charCount(codePoint);
        }
        return count;
    }

    private static String takeVisible(String value, int target) {
        StringBuilder result = new StringBuilder();
        int count = 0;
        for (int index = 0; index < value.length() && count < target;) {
            if (value.startsWith("<playername>", index)) {
                result.append("<playername>");
                count++;
                index += "<playername>".length();
                continue;
            }
            int next = formattingEnd(value, index);
            if (next > index) {
                result.append(value, index, next);
                index = next;
                continue;
            }
            int codePoint = value.codePointAt(index);
            result.appendCodePoint(codePoint);
            count++;
            index += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private static int formattingEnd(String value, int index) {
        if (value.charAt(index) == '\u00A7' && index + 1 < value.length()) {
            return value.charAt(index + 1) == '#' && index + 8 <= value.length()
                    ? index + 8 : index + 2;
        }
        if (value.startsWith("<em>", index)) return index + 4;
        return index;
    }

    public record Result(String source, String translation, String visibleTranslation) {
    }

    private record Alignment(int sourceLength, int distance, double similarity) {
    }
}
