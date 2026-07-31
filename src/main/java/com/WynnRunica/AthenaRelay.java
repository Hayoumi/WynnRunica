package com.WynnRunica;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public final class AthenaRelay {
    private static final String ATHENA = "https://athena.wynntils.com/";
    private static final String DEFAULT_RELAY = "https://shyutarque.site/wynnrunica/athena/";
    private static final String PROBE_PATH = "cache/get/serverList";
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir()
            .resolve("WynnRunica").resolve("athena-relay.txt");

    private static volatile String relay = "";
    private static volatile boolean active;

    private AthenaRelay() {}

    public static void init() {
        Thread probe = new Thread(AthenaRelay::detect, "WynnRunica Athena probe");
        probe.setDaemon(true);
        probe.start();
    }

    public static String rewrite(String uri) {
        return active && uri.startsWith(ATHENA) ? relay + uri.substring(ATHENA.length()) : uri;
    }

    private static void detect() {
        String configured = readConfiguredRelay();
        if (configured.isEmpty()) return;

        boolean forced = configured.startsWith("!");
        if (forced) configured = configured.substring(1);

        if (!forced && reachable(ATHENA + PROBE_PATH)) {
            System.out.println("[WynnRunica] Athena доступна напрямую, зеркало не используется");
            return;
        }
        if (!reachable(configured + PROBE_PATH)) {
            System.out.println("[WynnRunica] зеркало Athena не отвечает (" + configured
                    + "), запросы идут напрямую");
            return;
        }
        relay = configured;
        active = true;
        System.out.println("[WynnRunica] запросы к Athena идут через зеркало " + configured
                + (forced ? " (принудительно)" : ""));
    }

    private static boolean reachable(String url) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Wynntils")
                    .GET()
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static String readConfiguredRelay() {
        try {
            if (!Files.exists(CONFIG)) {
                Files.createDirectories(CONFIG.getParent());
                Files.write(CONFIG, List.of(
                        "# Zerkalo Athena dlya regionov, gde athena.wynntils.com nedostupna.",
                        "# Ispolzuetsya tolko esli Athena ne otvechaet napryamuyu.",
                        "# Pustaya stroka ili udalyonnyy adres otklyuchayut perenapravlenie.",
                        "# Prefiks ! zastavlyaet vsegda idti cherez zerkalo (dlya proverki).",
                        DEFAULT_RELAY));
                return DEFAULT_RELAY;
            }
            for (String line : Files.readAllLines(CONFIG)) {
                String value = line.trim();
                if (!value.isEmpty() && !value.startsWith("#")) {
                    return value.endsWith("/") ? value : value + "/";
                }
            }
        } catch (IOException ignored) {
        }
        return "";
    }
}
