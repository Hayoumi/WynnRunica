package com.WynnRunica;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.util.regex.Pattern;

public final class NpcNameplateCapture {
    private NpcNameplateCapture() {}

    public static void record(Text text) {
        if (!ServerNotificationTranslator.onWynncraft()) return;
        var segments = TelemetrySender.serialize(text);
        String key = captureKey(segments);
        if (key == null) return;
        if (mentionsOnlinePlayer(key)) return;
        String full = NpcNameResolver.resolveNameplate(key);
        if (full != null && !full.isBlank()) return;
        String decorated = NpcNameResolver.resolve(key);
        if (decorated != null && !decorated.isBlank()) return;
        TelemetrySender.recordNpcNameplate(key, segments);
    }

    static String captureKey(java.util.List<TelemetrySender.Segment> segments) {
        if (segments.isEmpty() || segments.size() > 256) return null;
        StringBuilder body = new StringBuilder();
        StringBuilder badges = new StringBuilder();
        boolean italicName = false;
        boolean healthBar = false;
        for (var segment : segments) {
            if (segment.font().contains("nameplate/")) healthBar = true;
            if (segment.font().contains("banner")) {
                segment.text().codePoints().forEach(point -> {
                    if (point >= 0xE030 && point <= 0xE049) badges.append((char) ('A' + point - 0xE030));
                });
            }
            if (!segment.icon()) {
                body.append(segment.text());
                if (!segment.text().isBlank() && segment.italic()) italicName = true;
            }
        }
        if (healthBar && body.indexOf("\n") >= 0) body.setLength(body.indexOf("\n"));
        String key = NpcNameResolver.normalizeKey(body.toString());
        return key.length() > 2000 || NpcNameplateCapture.reject(key, italicName, badges.toString()) ? null : key;
    }

    private static boolean mentionsOnlinePlayer(String key) {
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler == null) return false;
        for (var player : handler.getPlayerList()) {
            String name = player.getProfile().name();
            if (name == null || name.length() < 4) continue;
            int at = key.indexOf(name);
            while (at >= 0) {
                int end = at + name.length();
                if ((at == 0 || !Character.isLetterOrDigit(key.charAt(at - 1)))
                        && (end == key.length() || !Character.isLetterOrDigit(key.charAt(end)))) return true;
                at = key.indexOf(name, at + 1);
            }
        }
        return false;
    }

    private static final Pattern OWNED_ENTITY = Pattern.compile(
            "(?i)(?:^|\\s)[^\\s]{2,}['’]s?\\s+(?:Shop|Totem|Bird|Rubber Duck|Puppet|Effigy|Mob Totem|Gathering Totem)(?:\\s|$)");
    private static final Pattern OWNER_FRAGMENT = Pattern.compile("(?i)^[A-Za-z0-9_]{3,16}['’]s?$");
    private static final Pattern EXPERIENCE_SHARE = Pattern.compile("(?i)^\\[[^]]*Combat XP]\\s*\\[[^]]+]$");


    public static boolean reject(String key, boolean italicName, String badges) {
        if (key == null || key.isBlank()) return true;
        if (italicName || badges.contains("DISGUISED")) return true;
        if (OWNED_ENTITY.matcher(key).find() || OWNER_FRAGMENT.matcher(key).matches()) return true;
        if (key.startsWith("[|||||") || EXPERIENCE_SHARE.matcher(key).matches()) return true;
        return key.contains("Controlled by ") && key.contains("Click for Options");
    }
}
