package com.WynnRunica;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

public class VersionChecker {

    public static String latestVersion;
    public static String latestGitUrl;
    public static String latestModrinthUrl = "https://modrinth.com/mod/wynnrunica/versions";
    public static boolean hasUpdate = false;
    public static String сurrentVer = FabricLoader.getInstance().getModContainer("wynn_runica").get()
            .getMetadata().getVersion().getFriendlyString();


    public static void versionCheck() {
        try {
            URL adress = new URL ("https://api.github.com/repos/Hayoumi/WynnRunica/releases/latest");

            BufferedReader reader = new BufferedReader(new InputStreamReader(adress.openStream()));

            StringBuilder sb = new StringBuilder();
            String files;
            while ((files = reader.readLine()) != null) {
                sb.append(files);
            }

            JsonObject fullPage = JsonParser.parseString(sb.toString()).getAsJsonObject();
            String ver = fullPage.get("tag_name").getAsString();
            latestGitUrl = fullPage.get("html_url").getAsString();
            latestVersion = ver;

            if (!ver.equals(сurrentVer)) {
                hasUpdate = true;
            }

        } catch (IOException e) {
            System.out.println("Oshibka proverki versii: " + e.getMessage());
        }

    }
}
