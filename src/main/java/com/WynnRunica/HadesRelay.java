package com.WynnRunica;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class HadesRelay {
    private static final String HADES_HOST = "io.wynntils.com";
    private static final int HADES_PORT = 9000;
    private static final String DEFAULT_RELAY = "shyutarque.site:9000";
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir()
            .resolve("WynnRunica").resolve("hades-relay.txt");

    private static volatile String host = "";
    private static volatile int port = HADES_PORT;
    private static volatile boolean active;

    private HadesRelay() {}

    public static void init() {
        Thread probe = new Thread(HadesRelay::detect, "WynnRunica Hades probe");
        probe.setDaemon(true);
        probe.start();
    }

    public static String host(String original) {
        return active && HADES_HOST.equals(original) ? host : original;
    }

    public static int port(int original) {
        return active && original == HADES_PORT ? port : original;
    }

    private static void detect() {
        String configured = readConfigured();
        if (configured.isEmpty()) return;

        boolean forced = configured.startsWith("!");
        if (forced) configured = configured.substring(1);

        int colon = configured.lastIndexOf(':');
        String targetHost = colon > 0 ? configured.substring(0, colon) : configured;
        int targetPort = HADES_PORT;
        if (colon > 0) {
            try {
                targetPort = Integer.parseInt(configured.substring(colon + 1).trim());
            } catch (NumberFormatException e) {
                System.out.println("[WynnRunica] hades-relay.txt: неверный порт в " + configured);
                return;
            }
        }
        if (!connectable(targetHost, targetPort)) {
            System.out.println("[WynnRunica] зеркало Hades не отвечает (" + targetHost + ":" + targetPort
                    + "), соединение идёт напрямую");
            return;
        }
        host = targetHost;
        port = targetPort;
        active = true;
        System.out.println("[WynnRunica] Hades идёт через зеркало " + targetHost + ":" + targetPort
                + (forced ? " (принудительно)" : ""));
    }

    private static boolean connectable(String targetHost, int targetPort) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetHost, targetPort), 6000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String readConfigured() {
        try {
            if (!Files.exists(CONFIG)) {
                Files.createDirectories(CONFIG.getParent());
                Files.write(CONFIG, List.of(
                        "# TCP-zerkalo dlya " + HADES_HOST + ":" + HADES_PORT + " (spisok soyuznikov, HP, mana).",
                        "# Nuzhno tam, gde etot adres blokiruetsya. Format: host:port",
                        "# Vklyucheno po umolchaniyu - zakommentiruy stroku nizhe chtoby otklyuchit.",
                        DEFAULT_RELAY));
                return DEFAULT_RELAY;
            }
            for (String line : Files.readAllLines(CONFIG)) {
                String value = line.trim();
                if (!value.isEmpty() && !value.startsWith("#")) return value;
            }
        } catch (IOException ignored) {
        }
        return "";
    }
}
