package com.WynnRunica;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ObjectiveTranslator {

    private static final Pattern STAGE = Pattern.compile("Stage\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLOR_CODE = Pattern.compile("§[0-9a-fk-orA-FK-OR]");
    private static final Pattern BRACKETS = Pattern.compile("\\[[^\\]\\n]+\\]");
    private static final Pattern SCORED = Pattern.compile("^(?:[★⭑-] )?(.+): *\\d+/\\d+$");
    public static final String DAILY = "Daily Objectives";
    public static final String BOARD = "Hub Activity Objectives";

    private ObjectiveTranslator() {}

    public static String translateActivityType(String text) {
        if (text == null || text.isBlank()) return text;
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "quest" -> "Квест";
            case "mini-quest" -> "Мини-квест";
            case "cave" -> "Пещера";
            case "dungeon" -> "Подземелье";
            case "boss dungeon" -> "Подземелье с боссом";
            case "raid" -> "Рейд";
            case "world event" -> "Мировое событие";
            case "territory" -> "Территория";
            case "lootrun" -> "Лутран";
            case "guild war" -> "Война гильдий";
            default -> text;
        };
    }

    public static String translateWynntilsTask(String original) {
        if (original == null || original.isBlank()) return original;

        String clean = COLOR_CODE.matcher(original).replaceAll("").trim();
        String stageNumber = "";
        Matcher stage = STAGE.matcher(clean);
        if (stage.find()) stageNumber = stage.group(1);

        String objective = STAGE.matcher(clean).replaceAll("").trim();
        if (objective.startsWith(":") || objective.startsWith("-")) {
            objective = objective.substring(1).trim();
        }
        if (objective.isBlank()) return translateStage(original);

        QuestTracker.QuestInfo quest = QuestTracker.detect();
        String translated = findTranslation(objective, quest.name());
        if (translated == null) {
            String stageName = quest.stage();
            if (stageName.isEmpty() && !stageNumber.isEmpty()) stageName = "Stage " + stageNumber;
            TelemetrySender.recordObjective(objective, quest.name(), stageName, null);
            return translateStage(original);
        }

        translated = bracketColors(original, translated);
        String result = translateStage(original);
        int start = result.indexOf(objective);
        if (start != -1) {
            return result.substring(0, start) + translated + result.substring(start + objective.length());
        }
        if (!stageNumber.isEmpty()) return "§eЭтап " + stageNumber + "§7: " + translated;
        return translated;
    }

    static String scoredGoal(String clean) {
        Matcher scored = SCORED.matcher(clean);
        if (!scored.matches()) return null;
        return scored.group(1);
    }

    private static String translateGoal(String goal) {
        String translated = TranslationManager.getTranslationInContext(goal, DAILY, "objective");
        if (translated == null) TelemetrySender.recordObjective(goal, DAILY, "", null);
        return translated;
    }

    public static String translateWynntilsObjective(String original) {
        if (original == null) return null;
        String goal = scoredGoal(original);
        if (goal == null) return original;
        String translated = translateGoal(goal);
        if (translated == null) return original;
        return original.replace(goal, translated);
    }

    public static MutableText translateScoreboardLine(MutableText line) {
        if (line == null) return null;
        String raw = line.getString();
        String clean = COLOR_CODE.matcher(raw).replaceAll("").trim();
        if (clean.isEmpty()) return line;

        if (clean.startsWith("- ")) {
            String goal = scoredGoal(clean);
            if (goal != null) {
                String translated = translateGoal(goal);
                if (translated == null) return line;
                return Text.literal(coded(line).replace(goal, translated));
            }
        }

        if (!STAGE.matcher(clean).matches()) {
            QuestTracker.QuestInfo quest = QuestTracker.detect();
            String translated = findTranslation(clean, quest.name());
            if (translated != null) {
                return Text.literal(bracketColors(coded(line), translated)).setStyle(getEffectiveStyle(line));
            }

            if (clean.length() >= 5 && Character.isUpperCase(clean.charAt(0))
                    && !clean.startsWith("Quest:") && !clean.startsWith("Tracking:")
                    && !clean.endsWith(":") && !clean.startsWith("Party:")
                    && !clean.equalsIgnoreCase("Wynncraft") && !clean.contains("wynncraft.com")) {
                TelemetrySender.recordObjective(clean, quest.name(), quest.stage(), line);
            }
        }

        if (!STAGE.matcher(raw).find()) return line;
        return Text.literal(translateStage(raw)).setStyle(getEffectiveStyle(line));
    }

    private static String findTranslation(String text, String quest) {
        String translated = null;
        if (!quest.isBlank()) {
            translated = TranslationManager.getTranslationInContext(text, quest, "objective");
        }
        if (translated == null) {
            String common = TranslationManager.getTranslation(text, false);
            if (common != null && !common.equals(text)) translated = common;
        }
        if (translated == null) translated = TranslationManager.getTranslationInContext(text, BOARD, "objective");
        return translated;
    }

    static String bracketColors(String original, String translated) {
        List<String> opens = new ArrayList<>();
        List<String> closes = new ArrayList<>();
        String active = "";
        String shown = "";
        String outer = "";
        boolean inside = false;
        boolean closed = false;
        for (int i = 0; i < original.length(); i++) {
            char c = original.charAt(i);
            if (c == '§' && i + 1 < original.length()) {
                int end = i + 2;
                if (original.charAt(i + 1) == '#') {
                    while (end < original.length() && Character.digit(original.charAt(end), 16) >= 0) end++;
                }
                char code = Character.toLowerCase(original.charAt(i + 1));
                if (code == '#' || code == 'r' || Character.digit(code, 16) >= 0) active = original.substring(i, end);
                i = end - 1;
                continue;
            }
            if (closed) {
                closes.add(active);
                closed = false;
            }
            if (c == '[' && !inside) {
                opens.add(active);
                outer = shown;
                inside = true;
            }
            if (c == ']' && inside) {
                inside = false;
                closed = true;
            }
            shown = active;
        }
        if (closed) closes.add(outer);

        Matcher matcher = BRACKETS.matcher(translated);
        int count = 0;
        while (matcher.find()) count++;
        if (count != opens.size() || count != closes.size()) return translated;

        StringBuilder result = new StringBuilder();
        matcher.reset();
        int index = 0;
        int last = 0;
        while (matcher.find()) {
            String open = opens.get(index);
            String close = closes.get(index);
            index++;
            result.append(translated, last, matcher.start());
            last = matcher.end();
            boolean ownColor = COLOR_CODE.matcher(translated.substring(Math.max(0, matcher.start() - 2), matcher.start())).matches();
            if (ownColor || open.isEmpty()) {
                result.append(matcher.group());
                continue;
            }
            result.append(open).append(matcher.group()).append(close.isEmpty() ? "§r" : close);
        }
        return result.append(translated.substring(last)).toString();
    }

    private static String coded(Text line) {
        StringBuilder result = new StringBuilder();
        line.visit((style, value) -> {
            if (style.getColor() != null) {
                for (Formatting formatting : Formatting.values()) {
                    if (formatting.isColor() && formatting.getColorValue() == style.getColor().getRgb()) {
                        result.append('§').append(formatting.getCode());
                    }
                }
            }
            result.append(value);
            return Optional.empty();
        }, Style.EMPTY);
        return result.toString();
    }

    private static String translateStage(String text) {
        return STAGE.matcher(text).replaceAll("Этап $1");
    }

    private static Style getEffectiveStyle(Text text) {
        if (text.getStyle().getColor() != null) return text.getStyle();
        for (Text sibling : text.getSiblings()) {
            if (sibling.getStyle().getColor() != null) return sibling.getStyle();
        }
        return text.getStyle();
    }
}
