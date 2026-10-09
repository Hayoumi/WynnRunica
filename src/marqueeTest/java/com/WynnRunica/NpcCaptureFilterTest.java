package com.WynnRunica;

public final class NpcCaptureFilterTest {
    public static void main(String[] args) {
        reject("KeytarAshes' Totem 27s", false);
        reject("BarryBurns' Bird", false);
        reject("CosmicThunder's Shop JOIN HSP", false);
        reject("Hanafupookie Enjoyer", true);
        reject("BarryBurns'", false);
        reject("[|||||480000|||||] Corrupted Warfront Tower", false);
        reject("[+120 Combat XP] [SomePlayer]", false);
        accept("Trade Market Buy and sell", false);
        accept("Party Finder Queue for a raid", false);
        accept("Guard", false);
        if (!NpcNameplateCapture.reject("Player", false, "DISGUISED")) throw new AssertionError("badge");
        System.out.println("NPC capture filtering passed");
    }

    private static void reject(String key, boolean italic) {
        if (!NpcNameplateCapture.reject(key, italic, "")) throw new AssertionError(key);
    }

    private static void accept(String key, boolean italic) {
        if (NpcNameplateCapture.reject(key, italic, "")) throw new AssertionError(key);
    }
}
