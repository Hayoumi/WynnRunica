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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class TranslationLoader {

    public static HashMap<String, String> keyToQuest = new HashMap<>();
    public static HashMap<String, String> loadFromConfig() {

        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("WynnRunica");
        Path questsPath = configPath.resolve("quests");

        if (!Files.exists(questsPath)) {
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


                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }


            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        HashMap<String, String> result = new HashMap<>();

            try {
                DirectoryStream <Path> filesi = Files.newDirectoryStream(questsPath);
                for (Path file : filesi) {
                    try (BufferedReader reader = Files.newBufferedReader(file)) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isEmpty() || line.startsWith("#")) continue;
                            String[] parts = line.split("@", 2);
                            if (parts.length == 2) {
                                result.put(parts[0].trim(), parts[1].trim());
                                keyToQuest.put(parts[0].trim(), file.getFileName().toString());
                            }

                        }
                    } catch (IOException e) {
                        System.out.println("Oshibka chtenia faila: " + e.getMessage());
                    }

                }
            } catch (IOException e) {
                System.out.println("Oshibka chtenia direktorii: " + e.getMessage());
            }

        System.out.println("Zagrugheno perevodov: " + result.size());
        return result;

    }
}