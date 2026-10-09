package com.WynnRunica;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NpcNameResolver {
    private static final String FORMAT = "(?:§#[0-9a-fA-F]{6}|§[0-9a-fk-orA-FK-OR]|\\s)*";
    private static final String PREFIX_TAG = "(?:\\[!\\]|\\[Lv\\.[^\\]]+\\]|\\*)";
    private static final String SUFFIX_TAG = "(?:\\[[^\\]]+\\]|\\*)";

    private static final Pattern LEADING = Pattern.compile("^" + FORMAT);
    private static final Pattern TRAILING = Pattern.compile(FORMAT + "$");
    private static final Pattern PREFIX = Pattern.compile("^(?:" + FORMAT + PREFIX_TAG + ")+" + FORMAT);
    private static final Pattern SUFFIX = Pattern.compile("(?:" + FORMAT + SUFFIX_TAG + ")+" + FORMAT + "$");
    private static final Pattern JUNK = Pattern.compile("§#[0-9a-fA-F]{6}|§[0-9a-fk-orA-FK-OR]|[\\uE000-\\uF8FF\\uD800-\\uDFFF\\x{C0000}-\\x{DFFFF}]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> LOWERCASE = new ConcurrentHashMap<>();
    private static GuiScope templates;

    private NpcNameResolver() {}

    public static void clearCache() {
        CACHE.clear();
        LOWERCASE.clear();
        templates = null;
    }

    public static String normalizeKey(String text) {
        return cleanText(text);
    }

    public static String resolveNameplate(String text) {
        return findTranslation(cleanText(text));
    }

    public static String resolve(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cached = CACHE.get(raw);
        if (cached != null) {
            if (cached.isEmpty()) return null;
            return cached;
        }
        if (CACHE.size() > 5000) CACHE.clear();

        String translated = translate(raw);
        if (translated == null) {
            CACHE.put(raw, "");
        } else {
            CACHE.put(raw, translated);
        }
        return translated;
    }

    private static String translate(String raw) {
        String name = cleanText(raw);
        if (name.isEmpty()) return null;
        String ru = findTranslation(name);
        if (ru != null) return keepFormatting(raw, ru);

        String prefix = find(PREFIX, raw);
        String rest = raw.substring(prefix.length());
        String suffix = find(SUFFIX, rest);
        if (prefix.isEmpty() && suffix.isEmpty()) return null;

        String core = rest.substring(0, rest.length() - suffix.length());
        String coreName = cleanText(core);
        if (coreName.isEmpty()) return null;
        ru = findTranslation(coreName);
        if (ru == null) return null;
        return prefix + keepFormatting(core, ru) + translateSuffix(suffix);
    }

    private static String findTranslation(String name) {
        String direct = TranslationManager.npcTranslations.get(name);
        if (direct != null) return direct;

        if (templates == null) {
            GuiScope withNumbers = new GuiScope("npc-nameplates", "", "", List.of());
            for (Map.Entry<String, String> entry : TranslationManager.npcTranslations.entrySet()) {
                LOWERCASE.putIfAbsent(cleanText(entry.getKey()).toLowerCase(Locale.ROOT), entry.getValue());
                if (entry.getKey().contains("<num>")) withNumbers.putLine(entry.getKey(), entry.getValue(), false);
            }
            templates = withNumbers;
        }

        String ignoringCase = LOWERCASE.get(name.toLowerCase(Locale.ROOT));
        if (ignoringCase != null) return ignoringCase;
        return templates.findTranslation(name);
    }

    private static String keepFormatting(String original, String translation) {
        String lead = find(LEADING, original);
        String trail = find(TRAILING, original.substring(lead.length()));
        return lead + translation + trail;
    }

    private static String translateSuffix(String suffix) {
        return suffix
                .replace("[Quest]", "[Квест]")
                .replace("[Mini-Quest]", "[Мини-квест]")
                .replace("[Daily Quest]", "[Ежедневный квест]")
                .replace("[Merchant]", "[Торговец]")
                .replace("[Trade]", "[Торговля]")
                .replace("[Bank]", "[Банк]")
                .replace("[Blacksmith]", "[Кузнец]")
                .replace("[Identifier]", "[Опознаватель]");
    }

    private static String cleanText(String text) {
        if (text == null) return "";
        String withoutCodes = JUNK.matcher(text).replaceAll("").replace(' ', ' ');
        return SPACES.matcher(withoutCodes).replaceAll(" ").trim();
    }

    private static String find(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) return matcher.group();
        return "";
    }
}
