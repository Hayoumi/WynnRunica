package com.WynnRunica;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HadesRelay {
    private static final String HADES_HOST = "io.wynntils.com";
    private static final String MIRROR_HOST = "hades.shyutarque.site";
    private static final int PORT = 9000;
    private static Boolean lastUsedMirror;

    private HadesRelay() {}

    public static void removeOldFiles() {
        Path folder = FabricLoader.getInstance().getConfigDir().resolve("WynnRunica");
        try {
            Files.deleteIfExists(folder.resolve("athena-relay.txt"));
            Files.deleteIfExists(folder.resolve("hades-relay.txt"));
        } catch (IOException e) {
            System.out.println("[WynnRunica] не удалось удалить старые файлы зеркал: " + e.getMessage());
        }
    }

    public static String host(String original) {
        if (!HADES_HOST.equals(original)) return original;
        boolean mirror = mirrorAnswers();
        if (lastUsedMirror == null || mirror != lastUsedMirror) {
            lastUsedMirror = mirror;
            if (mirror) {
                System.out.println("[WynnRunica] Hades идёт через зеркало " + MIRROR_HOST);
            } else {
                System.out.println("[WynnRunica] зеркало Hades не отвечает, соединение идёт напрямую");
            }
        }
        if (mirror) return MIRROR_HOST;
        return original;
    }

    private static boolean mirrorAnswers() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(MIRROR_HOST, PORT), 3000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
