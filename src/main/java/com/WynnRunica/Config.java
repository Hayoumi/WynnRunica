package com.WynnRunica;

import com.WynnRunica.gui.Feature;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Config {
    private static final Path configFile = FabricLoader.getInstance().getConfigDir()
            .resolve("WynnRunica").resolve("WynnRunica.json");
    private static final String MASTER_KEY = "Перевод";
    public static final boolean DEBUG = Boolean.getBoolean("wynnrunica.debug");
    public static final List<Feature> features = new ArrayList<>();
    private static volatile boolean translationOn = true;
    private static boolean saveFailed;

    static {
        translation("Имена NPC", "Имена NPC", "Над персонажами, мобами и в окне диалога", "npc", 0xFF7FD3C0);
        translation("Надписи в мире", "Надписи в мире", "Пещеры, сундуки и таблички в воздухе", "world", 0xFF8FC3F0);
        translation("Диалоги", "Диалоги", "Реплики квестов и варианты ответа", "dialogue", 0xFFF0C869);
        translation("Интерфейсы", "Интерфейсы", "Меню, кнопки и дерево навыков", "gui", 0xFFC4DD5F);
        translation("Предметы", "Предметы", "Названия, описания и характеристики", "items", 0xFFC3A2F2);
        translation("Уведомления чата", "Сообщения сервера", "Системные сообщения и события в чате", "server", 0xFFF2A07C);
        translation("Задачи квестов", "Задачи квестов", "Цели на экране и в трекере", "quests", 0xFFE9B0CF);

        tool("Быстрый диалог", "Пропуск печати", "Реплика появляется сразу целиком", "fast", 0xFFF0C869);
        features.getLast().setEnabled(false);
        tool("Общий чат", "Общий чат", "Переписка между игроками с модом", "chat", 0xFF8FC3F0);
        tool("Отправка строк", "Помогать переводу", "Отправлять новые тексты авторам перевода", "help", 0xFFE9B0CF);
    }

    private static void translation(String name, String title, String description, String icon, int color) {
        features.add(new Feature(name, title, description, icon, color, Feature.Category.TRANSLATION));
    }

    private static void tool(String name, String title, String description, String icon, int color) {
        features.add(new Feature(name, title, description, icon, color, Feature.Category.TOOLS));
    }

    public static boolean isTranslationOn() {
        return translationOn;
    }

    public static void setTranslationEnabled(boolean enabled) {
        translationOn = enabled;
    }

    public static boolean isTranslationEnabled() {
        if (!translationOn) return false;
        for (Feature feature : features) {
            if (feature.getCategory() == Feature.Category.TRANSLATION && feature.isEnabled()) return true;
        }
        return false;
    }

    public static boolean isEnabled(String name) {
        for (Feature feature : features) {
            if (!feature.getName().equals(name)) continue;
            if (feature.getCategory() == Feature.Category.TRANSLATION && !translationOn) return false;
            return feature.isEnabled();
        }
        return false;
    }

    public static boolean didSaveFail() {
        return saveFailed;
    }

    public static void saveConfig() {
        JsonObject json = new JsonObject();
        json.addProperty(MASTER_KEY, translationOn);
        for (Feature feature : features) {
            json.addProperty(feature.getName(), feature.isEnabled());
        }
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, new GsonBuilder().setPrettyPrinting().create().toJson(json));
            saveFailed = false;
        } catch (IOException e) {
            saveFailed = true;
            System.out.println("WynnRunica: oshibka seyva configa: " + e.getMessage());
        }
    }

    public static void loadConfig() {
        if (!Files.exists(configFile)) {
            saveConfig();
            return;
        }
        try {
            JsonObject loaded = JsonParser.parseString(Files.readString(configFile)).getAsJsonObject();
            if (isBoolean(loaded.get(MASTER_KEY))) translationOn = loaded.get(MASTER_KEY).getAsBoolean();
            for (Feature feature : features) {
                if (isBoolean(loaded.get(feature.getName()))) {
                    feature.setEnabled(loaded.get(feature.getName()).getAsBoolean());
                }
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("WynnRunica: oshibka zagruzki configa: " + e.getMessage());
        }
    }

    private static boolean isBoolean(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
    }
}
