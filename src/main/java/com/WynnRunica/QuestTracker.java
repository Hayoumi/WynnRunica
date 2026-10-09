package com.WynnRunica;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuestTracker {
    public record QuestInfo(String name, String stage, String objective) {
        public static final QuestInfo UNKNOWN = new QuestInfo("", "", "");
    }

    private static final Pattern STAGE_PATTERN = Pattern.compile("Stage\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRACKING_PATTERN = Pattern.compile("Tracking:\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLEAN_DECOR = Pattern.compile("§[0-9a-fk-orA-FK-OR]");

    private static Boolean wynntilsPresent = null;
    private static Field activityField = null;
    private static Method getTrackedNameMethod = null;
    private static Method getTrackedTaskMethod = null;
    private static Method isTrackingMethod = null;

    private QuestTracker() {}

    public static QuestInfo detect() {
        QuestInfo fromWynntils = detectWynntils();
        if (fromWynntils != null && !fromWynntils.name().isBlank()) {
            return fromWynntils;
        }

        QuestInfo fromScoreboard = detectScoreboard();
        if (fromScoreboard != null && !fromScoreboard.name().isBlank()) {
            return fromScoreboard;
        }

        QuestInfo fromCompass = detectCompass();
        if (fromCompass != null && !fromCompass.name().isBlank()) {
            return fromCompass;
        }

        String fallbackQuest = TranslationManager.getCurrentQuest();
        if (fallbackQuest != null && !fallbackQuest.isBlank()) {
            return new QuestInfo(fallbackQuest, "", "");
        }

        return QuestInfo.UNKNOWN;
    }

    private static QuestInfo detectWynntils() {
        if (wynntilsPresent == null) {
            wynntilsPresent = FabricLoader.getInstance().isModLoaded("wynntils");
            if (wynntilsPresent) {
                try {
                    Class<?> modelsClass = Class.forName("com.wynntils.core.components.Models");
                    activityField = modelsClass.getField("Activity");
                    Class<?> activityClass = activityField.getType();
                    getTrackedNameMethod = activityClass.getMethod("getTrackedName");
                    getTrackedTaskMethod = activityClass.getMethod("getTrackedTask");
                    isTrackingMethod = activityClass.getMethod("isTracking");
                } catch (Throwable e) {
                    wynntilsPresent = false;
                }
            }
        }

        if (!Boolean.TRUE.equals(wynntilsPresent) || activityField == null) return null;

        try {
            Object activityInstance = activityField.get(null);
            if (activityInstance != null && isTrackingMethod != null) {
                boolean tracking = (boolean) isTrackingMethod.invoke(activityInstance);
                if (tracking) {
                    String name = (String) getTrackedNameMethod.invoke(activityInstance);
                    String stage = "";
                    String objective = "";
                    if (getTrackedTaskMethod != null) {
                        Object taskObj = getTrackedTaskMethod.invoke(activityInstance);
                        if (taskObj instanceof java.util.Optional<?> optional) {
                            taskObj = optional.orElse(null);
                        }
                        if (taskObj != null) {
                            String taskText = readableTaskText(taskObj);
                            Matcher m = STAGE_PATTERN.matcher(taskText);
                            if (m.find()) {
                                stage = "Stage " + m.group(1);
                            }
                            String withoutStage = STAGE_PATTERN.matcher(taskText).replaceAll("").trim();
                            if (!withoutStage.isBlank() && withoutStage.length() <= 300) {
                                objective = withoutStage;
                            }
                        }
                    }
                    if (name != null && !name.isBlank()) {
                        return new QuestInfo(name.trim(), stage, objective);
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static QuestInfo detectScoreboard() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return null;

        Scoreboard scoreboard = client.world.getScoreboard();
        if (scoreboard == null) return null;

        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) return null;

        Collection<ScoreboardEntry> entries = scoreboard.getScoreboardEntries(objective);
        if (entries == null || entries.isEmpty()) return null;

        String foundQuest = "";
        String foundStage = "";

        for (ScoreboardEntry entry : entries) {
            Text display = entry.display();
            String raw = display != null ? display.getString() : entry.owner();
            if (raw == null) continue;
            String clean = CLEAN_DECOR.matcher(raw).replaceAll("").trim();
            if (clean.isEmpty()) continue;

            Matcher stageMatcher = STAGE_PATTERN.matcher(clean);
            if (stageMatcher.find()) {
                foundStage = "Stage " + stageMatcher.group(1);
            }

            if (clean.startsWith("Quest:") || clean.startsWith("Tracking:")) {
                foundQuest = clean.substring(clean.indexOf(':') + 1).trim();
            } else if (clean.startsWith("[") && clean.endsWith("]")) {
                String candidate = clean.substring(1, clean.length() - 1).trim();
                if (!candidate.equalsIgnoreCase("Info") && !candidate.equalsIgnoreCase("Wynncraft")) {
                    foundQuest = candidate;
                }
            }
        }

        if (!foundQuest.isBlank()) {
            return new QuestInfo(foundQuest, foundStage, "");
        }
        return null;
    }

    private static String readableTaskText(Object value) {
        if (value instanceof Text text) return text.getString();
        try {
            Method getString = value.getClass().getMethod("getString");
            Object result = getString.invoke(value);
            if (result instanceof String text) return text;
        } catch (Throwable ignored) {}
        return String.valueOf(value);
    }

    private static QuestInfo detectCompass() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return null;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack == null || stack.isEmpty()) continue;
            String name = stack.getName().getString();
            if (name != null && name.toLowerCase().contains("compass")) {
                String fullText = stack.toString();
                Matcher m = TRACKING_PATTERN.matcher(fullText);
                if (m.find()) {
                    String quest = m.group(1).trim();
                    Matcher sm = STAGE_PATTERN.matcher(fullText);
                    String stage = sm.find() ? "Stage " + sm.group(1) : "";
                    return new QuestInfo(quest, stage, "");
                }
            }
        }
        return null;
    }
}
