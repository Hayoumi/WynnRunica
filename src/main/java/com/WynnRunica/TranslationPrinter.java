package com.WynnRunica;

import java.util.HashMap;

public class TranslationPrinter {
    public static HashMap<String, String> translations = new HashMap<>();
    private static String currentQuest = null;

    public static void loadTraslations() {
        HashMap<String, String> rawTranslations = TranslationLoader.loadFromConfig();

        HashMap<String, String> cleanTranslations = new HashMap<>();
        for (String key : rawTranslations.keySet()) {
            String cleanKey = key.replace(" ", "").toLowerCase();
            String ruText = rawTranslations.get(key);

            cleanTranslations.put(cleanKey, ruText);
            TranslationLoader.keyToQuest.put(cleanKey, TranslationLoader.keyToQuest.get(key));
        }
        translations = cleanTranslations;

    }


    public static String getTranslation(String text) {
        String cleanText = text.replace(" ", "").toLowerCase();

        String exact = translations.get(cleanText);
        if (exact != null) {
            currentQuest = TranslationLoader.keyToQuest.get(cleanText);
            return exact;
        }


        String bestKey = null;
        double bestScore = 0.0;

        if (currentQuest != null) {

            for (String key : translations.keySet()) {
                if (TranslationLoader.keyToQuest.get(key).equals(currentQuest)) {
                    double score = Epstein.similarity(cleanText, key);
                    if (score > bestScore) {
                        bestScore = score;
                        bestKey = key;
                    }
                }
            }

            if (bestScore > 0.82) {
                return translations.get(bestKey);
            } else {
                return text;
            }
        }
        return text;
    }


    /* public static int getTranslationsCount() {
        return translations.size();
    }   забыл зачем )))
    */

}
