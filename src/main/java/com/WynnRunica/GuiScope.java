package com.WynnRunica;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GuiScope {
    public final String id;
    public final String nameEn;
    public final String nameRu;
    public final String screen;
    public final String itemId;
    public final int priority;
    private final List<String> anchors;
    private final Map<String, String> lines = new HashMap<>();
    private final List<TranslationManager.GuiPattern> patterns = new ArrayList<>();
    // Оригинал каждой строки с кодами цвета: по нему видно, для какого состояния записан перевод.
    private final Map<String, String> sources = new HashMap<>();
    private static final Pattern COLOR = Pattern.compile("§#[0-9a-fA-F]{6}|§[0-9a-fA-F]");

    public GuiScope(String id, String nameEn, String nameRu, List<String> anchors) {
        this(id, nameEn, nameRu, anchors, "", "");
    }

    public GuiScope(String id, String nameEn, String nameRu, List<String> anchors,
                    String screen, String itemId) {
        this(id, nameEn, nameRu, anchors, screen, itemId, 0);
    }

    public GuiScope(String id, String nameEn, String nameRu, List<String> anchors,
                    String screen, String itemId, int priority) {
        this.id = id == null ? "" : id;
        this.nameEn = nameEn;
        this.nameRu = nameRu;
        this.anchors = anchors == null ? List.of() : List.copyOf(anchors);
        this.screen = screen == null ? "" : screen;
        this.itemId = itemId == null ? "" : itemId;
        this.priority = priority;
    }

    public void putLine(String orig, String trans, boolean hasBlockedVariant) {
        if (orig == null || trans == null || orig.isEmpty() || trans.isEmpty()) return;
        if (orig.equalsIgnoreCase(trans)) return;
        if (!TranslationLoader.hasCyrillic(trans)) return;

        // Разбивка суммы в скобках сворачивается в один <num> и в ключе, и в переводе.
        orig = TranslationManager.foldCoins(orig);
        trans = TranslationManager.foldCoins(trans);
        String sk = toSkeleton(orig);
        if (sk.isEmpty()) return;

        if (sk.contains("<num>")) {
            String[] parts = sk.split("<num>", -1);
            StringBuilder pb = new StringBuilder("^");
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (i < parts.length - 1 && (part.endsWith("+") || part.endsWith("-"))) {
                    part = part.substring(0, part.length() - 1).trim();
                    if (!part.isEmpty()) part += " ";
                }
                if (i > 0) {
                    pb.append("(" + TranslationManager.COINS_VALUE + "|[+\\-]?\\d+(?:[.,/]\\d+)*)");
                }
                pb.append(Pattern.quote(part));
            }
            pb.append("$");
            try {
                patterns.add(new TranslationManager.GuiPattern(Pattern.compile(pb.toString()), trans));
                sources.put(trans, orig);
            } catch (Exception ignored) {}
        } else {
            lines.put(sk, trans);
            sources.put(trans, orig);
        }
    }

    public String findTranslation(String text) {
        if (text == null || text.isEmpty()) return null;
        String sk = toSkeleton(text);
        if (sk.isEmpty()) return null;

        String template = lines.get(sk);
        Matcher m = null;
        if (template == null) {
            for (TranslationManager.GuiPattern gp : patterns) {
                Matcher matcher = gp.pattern().matcher(sk);
                if (matcher.matches()) {
                    template = gp.translationTemplate();
                    m = matcher;
                    break;
                }
            }
        }
        if (template == null) return null;

        String translated = recolor(sources.get(template), text, TranslationManager.fillTemplate(template, m));
        if (isDimmed(text, translated)) {
            return dimColor(translated);
        }
        return translated;
    }

    // Строка ищется без учёта цвета, а цветом игра показывает состояние: выбранный пункт белый,
    // остальные серые. Перевод записан для одного состояния, поэтому его цвета меняются так же,
    // как поменялись цвета оригинала: «§8- Classic» -> «§e- Classic» даёт «§e- Классический».
    static String recolor(String stored, String actual, String translated) {
        if (stored == null) return translated;
        List<String> was = colors(stored);
        List<String> now = colors(actual);
        if (was.equals(now) || was.size() != now.size()) return translated;

        List<String> own = colors(translated);
        Map<String, String> swap = new HashMap<>();
        java.util.HashSet<String> unclear = new java.util.HashSet<>();
        for (int i = 0; i < was.size(); i++) {
            String before = swap.put(was.get(i), now.get(i));
            if (before != null && !before.equals(now.get(i))) unclear.add(was.get(i));
        }

        Matcher code = COLOR.matcher(translated);
        StringBuilder out = new StringBuilder();
        int index = 0;
        while (code.find()) {
            if (code.end() < translated.length() && COLOR.matcher(translated).region(code.end(), translated.length()).lookingAt()) {
                continue;
            }
            String old = code.group().toLowerCase(java.util.Locale.ROOT);
            String replacement = null;
            // Перевод повторяет цвета оригинала по порядку: меняем по порядку.
            if (own.equals(was)) replacement = now.get(index);
            else if (!unclear.contains(old)) replacement = swap.get(old);
            index++;
            if (replacement != null) code.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        return code.appendTail(out).toString();
    }

    // Коды цвета по порядку. Из нескольких кодов подряд действует только последний.
    private static List<String> colors(String text) {
        List<String> result = new ArrayList<>();
        Matcher code = COLOR.matcher(text);
        int end = -1;
        while (code.find()) {
            if (code.start() == end) result.removeLast();
            result.add(code.group().toLowerCase(java.util.Locale.ROOT));
            end = code.end();
        }
        return result;
    }

    public int matchScore(Iterable<String> texts) {
        if (texts == null) return 0;
        int score = 0;
        java.util.HashSet<String> found = new java.util.HashSet<>();
        for (String text : texts) {
            if (findTranslation(text) != null) score++;
            String skeleton = toSkeleton(text);
            for (String anchor : anchors) {
                if (skeleton.equals(toSkeleton(anchor))) found.add(anchor);
            }
        }
        return score + found.size() * 10;
    }

    public boolean matchesContext(String actualScreen, String actualItemId) {
        if (!screen.isBlank()) {
            if (actualScreen == null || actualScreen.isBlank() || !toSkeleton(screen).equals(toSkeleton(actualScreen))) {
                return false;
            }
        }
        return actualItemId == null || actualItemId.isBlank() || itemId.isBlank()
                || itemId.equalsIgnoreCase(actualItemId);
    }

    public int contextScore(String actualScreen, String actualItemId) {
        int score = priority;
        if (actualScreen != null && !actualScreen.isBlank() && !screen.isBlank()
                && toSkeleton(screen).equals(toSkeleton(actualScreen))) score += 100;
        if (actualItemId != null && !actualItemId.isBlank() && !itemId.isBlank()
                && itemId.equalsIgnoreCase(actualItemId)) score += 50;
        return score;
    }

    public static String toSkeleton(String s) {
        if (s == null || s.isEmpty()) return "";
        String clean = s.replaceAll("§#[0-9a-fA-F]{6}", "")
                        .replaceAll("§[0-9a-fA-FklmnorKLMNOR]", "")
                        .replaceAll("[\\uD800-\\uDBFF][\\uDC00-\\uDFFF]", "<em>")
                        .replaceAll("[\\u2600-\\u27BF\\uE000-\\uF8FF]", "<em>");
        clean = clean.replaceAll("\\s+", " ").trim();
        clean = clean.replaceAll("\\s+([,.:;!?])", "$1");
        return clean.toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isDimmed(String text, String trans) {
        if (text == null || trans == null) return false;
        String t = text.startsWith("§r") ? text.substring(2) : text;
        String r = trans.startsWith("§r") ? trans.substring(2) : trans;
        return t.startsWith("§8") && !r.startsWith("§8");
    }

    private static String dimColor(String s) {
        if (s == null || !s.contains("§")) return s;
        return s.replace("§7", "\u0000")
                .replaceAll("§[0-69a-fA-F]", "§7")
                .replaceAll("§#[0-9a-fA-F]{6}", "§7")
                .replace("\u0000", "§8");
    }
}
