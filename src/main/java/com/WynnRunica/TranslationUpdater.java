package com.WynnRunica;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class TranslationUpdater {
    private static final String GITHUB = "https://raw.githubusercontent.com/Hayoumi/WynnRunica/main/src/main/resources/";
    private static final String MANIFEST = "translations-manifest.json";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private static boolean isUpdate = true;

    private TranslationUpdater() {}

    public static boolean update() {
        if (isUpdate == false) return false;
        return update(GITHUB, FabricLoader.getInstance().getConfigDir().resolve("WynnRunica"));
    }

    static boolean update(String source, Path root) {
        Path staging = root.resolveSibling(root.getFileName() + ".update-staging");
        try {
            byte[] manifestBytes = download(source, MANIFEST);
            JsonArray files = readVersion2(manifestBytes, MANIFEST).getAsJsonArray("files");
            if (files == null || files.isEmpty()) {
                throw new IOException("translation manifest is empty");
            }

            List<String> folders = new ArrayList<>();
            List<String> changed = new ArrayList<>();
            for (JsonElement element : files) {
                JsonObject row = element.getAsJsonObject();
                String path = row.get("path").getAsString();
                Path local = root.resolve(path).normalize();
                if (!isSafePath(path) || !local.startsWith(root)) {
                    throw new IOException("unsafe manifest path: " + path);
                }
                String folder = path.substring(0, path.indexOf('/'));
                if (!folders.contains(folder)) folders.add(folder);
                if (!Files.isRegularFile(local)
                        || !sha256(Files.readAllBytes(local)).equalsIgnoreCase(row.get("sha256").getAsString())) {
                    changed.add(path);
                }
            }
            if (!folders.contains("quests") || !folders.contains("gui") || !folders.contains("npc")) {
                throw new IOException("translation manifest must contain quests, gui and npc");
            }
            Path currentManifest = root.resolve(MANIFEST);
            boolean sameManifest = Files.isRegularFile(currentManifest)
                    && java.util.Arrays.equals(Files.readAllBytes(currentManifest), manifestBytes);
            if (changed.isEmpty() && sameManifest) return false;

            deleteFolder(staging);
            for (JsonElement element : files) {
                JsonObject row = element.getAsJsonObject();
                String path = row.get("path").getAsString();
                Path destination = staging.resolve(path).normalize();
                Files.createDirectories(destination.getParent());
                if (!changed.contains(path)) {
                    Files.copy(root.resolve(path), destination);
                    continue;
                }
                byte[] bytes = download(source, path.replace(" ", "%20"));
                if (!sha256(bytes).equalsIgnoreCase(row.get("sha256").getAsString())) {
                    throw new IOException("checksum mismatch: " + path);
                }
                readVersion2(bytes, path);
                Files.write(destination, bytes);
            }
            Files.write(staging.resolve(MANIFEST), manifestBytes);
            replaceFolders(root, staging, folders);
            System.out.println("[WynnRunica] Updated JSON v2 translations: " + changed.size() + " of " + files.size() + " files");
            return true;
        } catch (Exception error) {
            System.out.println("[WynnRunica] Translation update skipped; current catalog kept: " + error.getMessage());
            try {
                deleteFolder(staging);
            } catch (IOException ignored) {}
            return false;
        }
    }

    private static void replaceFolders(Path root, Path staging, List<String> folders) throws IOException {
        Path backup = root.resolveSibling(root.getFileName() + ".update-backup");
        deleteFolder(backup);
        Files.createDirectories(backup);
        folders.add(MANIFEST);
        try {
            Files.createDirectories(root);
            for (String name : folders) {
                if (Files.exists(root.resolve(name))) move(root.resolve(name), backup.resolve(name));
            }
            for (String name : folders) {
                move(staging.resolve(name), root.resolve(name));
            }
        } catch (IOException error) {
            for (String name : folders) {
                deleteFolder(root.resolve(name));
                if (Files.exists(backup.resolve(name))) move(backup.resolve(name), root.resolve(name));
            }
            throw error;
        } finally {
            deleteFolder(staging);
            deleteFolder(backup);
        }
    }

    private static JsonObject readVersion2(byte[] bytes, String name) throws IOException {
        JsonObject json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!json.has("schemaVersion") || json.get("schemaVersion").getAsInt() != 2) {
            throw new IOException("not JSON v2: " + name);
        }
        return json;
    }

    private static byte[] download(String source, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(source + path))
                .header("User-Agent", "WynnRunica/2").build();
        HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + path);
        }
        return response.body();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static boolean isSafePath(String path) {
        if (!path.endsWith(".json") || path.contains("..") || path.contains("\\") || path.contains(":")) {
            return false;
        }
        return path.startsWith("quests/") || path.startsWith("gui/")
                || path.startsWith("npc/") || path.startsWith("chat/");
    }

    private static void move(Path from, Path to) throws IOException {
        Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteFolder(Path path) throws IOException {
        if (!Files.exists(path)) return;
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(path)) {
                for (Path child : children) deleteFolder(child);
            }
        }
        Files.delete(path);
    }
}
