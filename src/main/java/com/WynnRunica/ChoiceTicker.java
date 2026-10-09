package com.WynnRunica;

import com.WynnRunica.mixin.InGameHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChoiceTicker {
    private static final int CHOICE_TEXT_WIDTH = 134;
    private static final Style SPACE_STYLE = Style.EMPTY.withFont(
            new StyleSpriteSource.Font(Identifier.of("minecraft", "space")));
    private static final Map<Integer, SlotState> activeSlots = new HashMap<>();
    private static final Set<Integer> frameSlots = new HashSet<>();
    private static String choiceSet = "";
    private static MutableText activeOverlay;

    private static final class SlotState {
        final String fullRu;
        final String playerName;
        final Style style;
        final List<Text> glyphs = new ArrayList<>();
        final int[] widths;
        final Marquee animation = new Marquee(System.nanoTime());
        final int viewportWidth;
        int sourceWidth;
        int siblingIndex;
        int lastStart = -1;

        SlotState(String fullRu, int sourceWidth, int siblingIndex, Style style,
                  String playerName, List<Text> icons) {
            this.fullRu = fullRu;
            this.playerName = playerName;
            this.style = style;
            this.sourceWidth = sourceWidth;
            this.siblingIndex = siblingIndex;
            Text text = TextEmojiUtils.rebuildDialogue(
                    fullRu.replace("<playername>", playerName), icons, style);
            text.visit((glyphStyle, value) -> {
                value.codePoints().forEach(codePoint -> glyphs.add(
                        Text.literal(new String(Character.toChars(codePoint))).setStyle(glyphStyle)));
                return java.util.Optional.empty();
            }, Style.EMPTY);
            widths = glyphs.stream().mapToInt(ChoiceTicker::width).toArray();
            this.viewportWidth = Math.max(sourceWidth, CHOICE_TEXT_WIDTH);
        }

        int start(long now) {
            return animation.startAt(now, Marquee.maxStart(widths, viewportWidth));
        }

        MutableText window(int start) {
            MutableText result = Text.empty();
            int end = Marquee.endAt(widths, start, viewportWidth);
            for (int i = start; i < end; i++) result.append(glyphs.get(i));
            compensate(result, sourceWidth - width(result));
            return result;
        }
    }

    private ChoiceTicker() {}

    public static void beginChoices(String key) {
        if (!choiceSet.equals(key)) {
            reset();
            choiceSet = key;
        }
        frameSlots.clear();
    }

    public static void endChoices() {
        activeSlots.keySet().retainAll(frameSlots);
    }

    public static void setActiveOverlay(MutableText overlay) {
        activeOverlay = overlay;
    }

    public static MutableText getOrUpdateSlot(int slotIndex, String fullRu, int sourceWidth,
                                             int siblingIndex, Style style, String playerName,
                                             List<Text> icons) {
        frameSlots.add(slotIndex);
        SlotState slot = activeSlots.get(slotIndex);
        if (slot == null || !slot.fullRu.equals(fullRu) || !slot.style.equals(style)
                || !slot.playerName.equals(playerName)) {
            slot = new SlotState(fullRu, sourceWidth, siblingIndex, style, playerName, icons);
            activeSlots.put(slotIndex, slot);
        } else {
            slot.sourceWidth = sourceWidth;
            slot.siblingIndex = siblingIndex;
        }
        slot.lastStart = slot.start(System.nanoTime());
        return slot.window(slot.lastStart);
    }

    private static boolean isOverlayActive(MinecraftClient client) {
        return activeOverlay != null && client != null && client.inGameHud != null
                && ((InGameHudAccessor) client.inGameHud).getOverlayRemaining() > 0
                && ((InGameHudAccessor) client.inGameHud).getOverlayMessage() == activeOverlay;
    }

    public static void clientTick(MinecraftClient client) {
        if (!isOverlayActive(client) || !Config.isTranslationEnabled()
                || !Config.isEnabled("Диалоги")) reset();
    }

    public static void renderFrame() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (activeSlots.isEmpty() || !isOverlayActive(client)) return;
        long now = System.nanoTime();
        MutableText next = null;
        for (SlotState slot : activeSlots.values()) {
            int start = slot.start(now);
            if (start == slot.lastStart) continue;
            slot.lastStart = start;
            if (next == null) next = activeOverlay.copy();
            if (slot.siblingIndex >= 0 && slot.siblingIndex < next.getSiblings().size()) {
                next.getSiblings().set(slot.siblingIndex, slot.window(start));
            }
        }
        if (next != null) {
            activeOverlay = next;
            ((InGameHudAccessor) client.inGameHud).setOverlayMessage(next);
        }
    }

    public static void reset() {
        activeSlots.clear();
        frameSlots.clear();
        choiceSet = "";
        activeOverlay = null;
    }

    private static void compensate(MutableText text, int pixels) {
        if (pixels != 0) {
            text.append(Text.literal(new String(Character.toChars(0xD0000 + pixels)))
                    .setStyle(SPACE_STYLE));
        }
    }

    private static int width(Text text) {
        return MinecraftClient.getInstance().textRenderer.getWidth(text);
    }

    static final class Marquee {
        private static final double START_HOLD_MS = 1000;
        private static final double STEP_MS = 110;
        private static final double END_HOLD_MS = 1500;
        private static final double RETURN_MS = 350;
        private final long startedAt;

        Marquee(long now) {
            startedAt = now;
        }

        public int startAt(long now, int maxStart) {
            if (maxStart <= 0) return 0;
            double forwardMs = (maxStart - 1) * STEP_MS;
            double cycleMs = START_HOLD_MS + forwardMs + END_HOLD_MS + RETURN_MS;
            double elapsed = Math.max(0, (now - startedAt) / 1_000_000.0) % cycleMs;
            if (elapsed < START_HOLD_MS) return 0;
            elapsed -= START_HOLD_MS;
            if (elapsed < forwardMs) return Math.min(maxStart, 1 + (int) (elapsed / STEP_MS));
            elapsed -= forwardMs;
            if (elapsed < END_HOLD_MS) return maxStart;
            double progress = (elapsed - END_HOLD_MS) / RETURN_MS;
            double eased = 1 - Math.pow(1 - progress, 3);
            return Math.max(0, maxStart - (int) Math.round(maxStart * eased));
        }

        public static int maxStart(int[] widths, int available) {
            if (available <= 0) return 0;
            int start = widths.length;
            int suffixWidth = 0;
            while (start > 0 && suffixWidth + widths[start - 1] <= available) {
                suffixWidth += widths[--start];
            }
            return Math.min(start, Math.max(0, widths.length - 1));
        }

        public static int endAt(int[] widths, int start, int available) {
            int end = start;
            int used = 0;
            while (end < widths.length && used + widths[end] <= available) {
                used += widths[end++];
            }
            return end;
        }
    }
}
