package com.WynnRunica;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class TranslationUpdaterTest {
    private static final List<String> requested = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Path work = Files.createTempDirectory("wynnrunica-updater");
        Path remote = work.resolve("remote");
        Path root = work.resolve("WynnRunica");
        String[] names = {"quests/A.json", "quests/B.json", "gui/shared.json", "npc/names.json"};
        for (String name : names) write(remote.resolve(name), "{\"schemaVersion\": 2, \"name\": \"" + name + "\"}\r\n");
        writeManifest(remote, names);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath().substring(1);
            requested.add(path);
            Path file = remote.resolve(path);
            if (!Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                byte[] body = Files.readAllBytes(file);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        String source = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        try {
            check(TranslationUpdater.update(source, root) && requested.size() == 5,
                    "08.10: an empty folder gets the whole catalog: " + requested);
            check(Files.readString(root.resolve("quests/A.json")).endsWith("\r\n"),
                    "08.10: files are stored byte for byte, line endings included");

            requested.clear();
            check(!TranslationUpdater.update(source, root) && requested.equals(List.of("translations-manifest.json")),
                    "08.10: nothing changed means one request and no reload: " + requested);

            write(remote.resolve("quests/B.json"), "{\"schemaVersion\": 2, \"name\": \"new\"}\n");
            writeManifest(remote, names);
            requested.clear();
            check(TranslationUpdater.update(source, root)
                            && requested.equals(List.of("translations-manifest.json", "quests/B.json")),
                    "08.10: one changed file means one download: " + requested);
            check(Files.readString(root.resolve("quests/B.json")).contains("new")
                            && Files.readString(root.resolve("quests/A.json")).contains("quests/A.json"),
                    "08.10: the changed file is replaced, the others stay");

            writeManifest(remote, names);
            write(remote.resolve("gui/shared.json"), "{\"schemaVersion\": 2, \"name\": \"broken\"}\n");
            Files.delete(root.resolve("gui/shared.json"));
            check(!TranslationUpdater.update(source, root) && !Files.exists(root.resolve("gui/shared.json"))
                            && Files.readString(root.resolve("quests/B.json")).contains("new"),
                    "08.10: a checksum mismatch leaves the catalog on disk untouched");
            check(!Files.exists(work.resolve("WynnRunica.update-backup")),
                    "08.10: no leftover folders after a failed update");
        } finally {
            server.stop(0);
        }
        System.out.println("Translation updater checks passed");
    }

    private static void writeManifest(Path remote, String[] names) throws Exception {
        StringBuilder rows = new StringBuilder();
        for (String name : names) {
            if (rows.length() > 0) rows.append(',');
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(remote.resolve(name))));
            rows.append("{\"path\": \"").append(name).append("\", \"sha256\": \"").append(hash).append("\"}");
        }
        write(remote.resolve("translations-manifest.json"), "{\"schemaVersion\": 2, \"files\": [" + rows + "]}\n");
    }

    private static void write(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
}
