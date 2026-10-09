package com.WynnRunica;

import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class ChoiceFrameStitcher {

    private static final int MIN_OVERLAP = 5;
    private static final int SAFE_STATIC_WIDTH = 140;
    private static final long STATIC_SETTLE_MS = 4000;
    private static final long EXPIRY_MS = 5000;
    private static final Map<String, SlotState> activeSlots = new HashMap<>();
    private static String currentChoiceSetKey = "";

    private static final class SlotState {
        final String quest;
        final String speaker;
        final String stage;
        final Text originalMessage;
        final String choiceSetKey;
        final int choiceIndex;

        String lastFrame = "";
        final StringBuilder accumulated = new StringBuilder();
        int identicalTicks = 0;
        int maxOffset = 0;
        boolean reachedEnd = false;
        boolean sent = false;
        long lastUpdated;
        long lastChanged;

        SlotState(String quest, String speaker, String stage, Text originalMessage,
                  String choiceSetKey, int choiceIndex) {
            this.quest = quest;
            this.speaker = speaker;
            this.stage = stage;
            this.originalMessage = originalMessage;
            this.choiceSetKey = choiceSetKey;
            this.choiceIndex = choiceIndex;
            this.lastUpdated = System.currentTimeMillis();
            this.lastChanged = lastUpdated;
        }
    }

    private ChoiceFrameStitcher() {}

    public static void observe(String choiceSetKey, int choiceIndex, String frameText,
                               String speaker, String quest, String stage,
                               Text originalMessage, int sourceWidth) {
        if (choiceSetKey == null || frameText == null || frameText.isBlank()) return;

        cleanupIfSetChanged(choiceSetKey);

        String slotKey = choiceSetKey + "#" + choiceIndex;
        SlotState slot = activeSlots.computeIfAbsent(slotKey,
                k -> new SlotState(quest, speaker, stage, originalMessage, choiceSetKey, choiceIndex));

        slot.lastUpdated = System.currentTimeMillis();
        if (slot.sent) return;

        if (slot.accumulated.length() == 0) {
            slot.accumulated.append(frameText);
            slot.lastFrame = frameText;
            slot.identicalTicks = 1;
            slot.maxOffset = 0;
            return;
        }

        if (frameText.equals(slot.lastFrame)) {
            slot.identicalTicks++;
            if (shouldCommitStatic(slot, sourceWidth)) {
                commit(slot);
            }
            return;
        }

        slot.identicalTicks = 0;
        slot.lastChanged = slot.lastUpdated;
        String accumStr = slot.accumulated.toString();
        int subIdx = accumStr.indexOf(frameText);

        if (subIdx != -1) {
            if (subIdx < slot.maxOffset) {
                slot.reachedEnd = true;
                commit(slot);
            } else if (subIdx > slot.maxOffset) {
                slot.maxOffset = subIdx;
            }
        } else {
            int maxOverlap = Math.min(accumStr.length(), frameText.length());
            int overlap = 0;
            for (int k = maxOverlap; k >= MIN_OVERLAP; k--) {
                if (accumStr.endsWith(frameText.substring(0, k))) {
                    overlap = k;
                    break;
                }
            }

            if (overlap > 0) {
                slot.accumulated.append(frameText.substring(overlap));
                slot.maxOffset = slot.accumulated.length() - frameText.length();
            } else {
                slot.accumulated.setLength(0);
                slot.accumulated.append(frameText);
                slot.maxOffset = 0;
            }
        }

        slot.lastFrame = frameText;
    }

    private static boolean shouldCommitStatic(SlotState slot, int sourceWidth) {
        return sourceWidth < SAFE_STATIC_WIDTH
                && slot.lastUpdated - slot.lastChanged >= STATIC_SETTLE_MS;
    }

    private static void commit(SlotState slot) {
        if (slot.sent) return;
        slot.sent = true;

        String fullText = slot.accumulated.toString().trim();
        if (fullText.isEmpty()) return;

        if (ChoiceTranslator.hasTranslation(fullText, slot.quest)) return;

        UntranslatedLogger.logDialogue(fullText, slot.speaker, true,
                slot.quest, slot.originalMessage);
        TelemetrySender.recordChoice(fullText, slot.speaker, slot.quest,
                slot.stage, slot.originalMessage, slot.choiceSetKey, slot.choiceIndex);
    }

    public static void onDialogueChange(String newChoiceSetKey) {
        cleanupIfSetChanged(newChoiceSetKey != null ? newChoiceSetKey : "");
    }

    private static void cleanupIfSetChanged(String newChoiceSetKey) {
        if (!newChoiceSetKey.equals(currentChoiceSetKey)) {
            currentChoiceSetKey = newChoiceSetKey;
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<String, SlotState>> it = activeSlots.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, SlotState> entry = it.next();
                if (now - entry.getValue().lastUpdated > EXPIRY_MS
                        || !entry.getValue().choiceSetKey.equals(currentChoiceSetKey)) {
                    it.remove();
                }
            }
        }
        if (activeSlots.size() > 50) {
            activeSlots.clear();
        }
    }

    public static void clear() {
        activeSlots.clear();
        currentChoiceSetKey = "";
    }
}
