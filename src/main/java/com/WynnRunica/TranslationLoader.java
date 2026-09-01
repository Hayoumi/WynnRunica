package com.WynnRunica;

import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class TranslationLoader {

    public static HashMap<String, String> keyToQuest = new HashMap<>();
    public static java.util.HashSet<String> ambiguousKeys = new java.util.HashSet<>();

    public static HashMap<String, String> loadFromConfig() {

        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("WynnRunica");
        Path questsPath = configPath.resolve("quests");

        if (isEmptyOrMissing(questsPath)) {
            try {
                Files.createDirectories(questsPath);

                URL url = TranslationLoader.class.getProtectionDomain().getCodeSource().getLocation();
                Path urlPath = Paths.get(url.toURI());

                try (ZipFile zip = new ZipFile(urlPath.toFile())) {
                    Enumeration<? extends ZipEntry> entries = zip.entries();

                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();

                        if (entry.getName().startsWith("quests/") && !entry.isDirectory()) {
                            InputStream stream = zip.getInputStream(entry);
                            Path dest = configPath.resolve(entry.getName());
                            Files.copy(stream, dest);
                        }
                    }

                } catch (IOException e) {
                    System.out.println("Oshibka zagruzki questov iz jar: " + e.getMessage());
                }

            } catch (IOException | URISyntaxException e) {
                System.out.println("Oshibka sozdania papki questov: " + e.getMessage());

            }
        }

        HashMap<String, String> result = new HashMap<>();
        ambiguousKeys.clear();
        HashMap<String, String> firstQuestOf = new HashMap<>();

        try {
            DirectoryStream<Path> filesi = Files.newDirectoryStream(questsPath);
            for (Path file : filesi) {
                String questName = file.getFileName().toString();
                try (BufferedReader reader = Files.newBufferedReader(file)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty() || line.startsWith("#"))
                            continue;
                        String[] parts = line.split(" @ ", 2);
                        if (parts.length != 2) {
                            parts = line.split("@", 2);
                            if (parts.length == 2) {
                                parts[0] = parts[0].trim();
                                parts[1] = parts[1].trim();
                            }
                        }
                        // Незаполненная строка (ключ@ без значения) - это отсутствие
                        // перевода, а не пустой перевод. Класть её нельзя: файлы
                        // читаются подряд и последний put выигрывает, поэтому пустая
                        // строка в одном квесте гасила готовый перевод того же ключа
                        // в другом, и реплика уходила в игру пустой ячейкой.
                        if (parts.length == 2 && parts[1].isEmpty())
                            continue;
                        if (parts.length == 2) {
                            result.put(parts[0], parts[1]);
                            keyToQuest.put(parts[0], questName);

                            String norm = parts[0].replace(" ", "").toLowerCase();
                            String prev = firstQuestOf.putIfAbsent(norm, questName);
                            if (prev != null && !prev.equals(questName)) {
                                ambiguousKeys.add(norm);
                            }
                        }

                    }
                } catch (IOException e) {
                    System.out.println("Oshibka chtenia quest faila: " + e.getMessage());
                }

            }
        } catch (IOException e) {
            System.out.println("Oshibka chtenia quests direktorii: " + e.getMessage());
        }

        System.out.println("Zagrugheno perevodov: " + result.size());
        return result;

    }

    public static HashMap<String, String> loadGuiFromConfig() {

        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("WynnRunica");
        Path guiPath = configPath.resolve("gui");

        if (isEmptyOrMissing(guiPath)) {
            try {
                Files.createDirectories(guiPath);

                URL url = TranslationLoader.class.getProtectionDomain().getCodeSource().getLocation();
                Path urlPath = Paths.get(url.toURI());

                try (ZipFile zip = new ZipFile(urlPath.toFile())) {
                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (entry.getName().startsWith("gui/") && !entry.isDirectory()) {
                            InputStream stream = zip.getInputStream(entry);
                            Path dest = configPath.resolve(entry.getName());
                            Files.copy(stream, dest);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Oshibka zagruzki gui iz jar: " + e.getMessage());
                }

            } catch (IOException | URISyntaxException e) {
                System.out.println("Oshibka sozdania papki gui: " + e.getMessage());
            }
        }

        HashMap<String, String> result = new HashMap<>();
        try {
            DirectoryStream<Path> files = Files.newDirectoryStream(guiPath);
            for (Path file : files) {
                try (BufferedReader reader = Files.newBufferedReader(file)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty() || line.startsWith("#"))
                            continue;
                        String[] parts = line.split(" @ ", 2);
                        if (parts.length != 2) {
                            parts = line.split("@", 2);
                            if (parts.length == 2) {
                                parts[0] = parts[0].trim();
                                parts[1] = parts[1].trim();
                            }
                        }
                        // Незаполненная строка (ключ@ без значения) - это отсутствие
                        // перевода, а не пустой перевод. Класть её нельзя: файлы
                        // читаются подряд и последний put выигрывает, поэтому пустая
                        // строка в одном квесте гасила готовый перевод того же ключа
                        // в другом, и реплика уходила в игру пустой ячейкой.
                        if (parts.length == 2 && parts[1].isEmpty())
                            continue;
                        if (parts.length == 2) {
                            result.put(parts[0], parts[1]);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Oshibka chtenia gui faila: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Oshibka chtenia gui direktorii: " + e.getMessage());
        }

        System.out.println("Zagruzheno GUI perevodov: " + result.size());
        return result;
    }

    private static boolean isEmptyOrMissing(Path dir) {
        if (!Files.exists(dir)) return true;
        try (Stream<Path> s = Files.list(dir)) {
            return s.findAny().isEmpty();
        } catch (IOException e) {
            return true;
        }
    }


}