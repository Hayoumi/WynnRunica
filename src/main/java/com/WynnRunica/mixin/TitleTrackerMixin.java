package com.WynnRunica.mixin;

import com.WynnRunica.DialogueInstantReveal;
import com.WynnRunica.TextEmojiUtils;
import com.WynnRunica.TranslationPrinter;
import com.WynnRunica.UntranslatedLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.WynnRunica.TextUtils.extractCleanText;
import static com.WynnRunica.WynnRunicaClient.enabled;

@Mixin(InGameHud.class)
public class TitleTrackerMixin {

    private static final int MAX_WIDTH = 234;
    private static final int PORTRAIT_OFFSET = 24;
    private static final char SPECIAL_CHAR = '\uDAFF';
    private static final char ZERO_WIDTH_CHAR = '\uE000';
    private static final Style[] BODY_STYLES = new Style[5];
    private static boolean isModifying;
    private static String lastCleanKey = "";
    private static int consecutiveCount;

    static {
        for (int i = 0; i < BODY_STYLES.length; i++) {
            BODY_STYLES[i] = Style.EMPTY.withFont(new StyleSpriteSource.Font(
                    Identifier.of("minecraft", "hud/dialogue/text/wynncraft/body_" + i)));
        }
    }

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void onSetOverlay(Text message, boolean tinted, CallbackInfo ci) {
        if (!enabled || isModifying || message == null) return;

        try {
            DialogueInstantReveal.observe(message);
            MutableText copy = message.copy();
            DialogueParts parts = DialogueParts.read(copy);
            String key = parts.key();
            if (key.isEmpty()) return;

            boolean stabilized = stabilize(key);
            String playerName = MinecraftClient.getInstance().getSession().getUsername();
            String lookupKey = key.replace(playerName, "<playername>");
            String translation = TranslationPrinter.getTranslation(lookupKey, stabilized);

            if (stabilized) {
                UntranslatedLogger.logDialogue(lookupKey, parts.speaker(), false,
                        TranslationPrinter.getCurrentQuest(), message);
            }

            boolean modified = false;
            if (!translation.equals(lookupKey)) {
                modified = replaceBody(message, copy, parts,
                        translation.replace("<playername>", playerName));
            }
            modified |= replaceChoices(message, parts, playerName, stabilized);
            if (!modified) return;

            isModifying = true;
            try {
                ((InGameHud) (Object) this).setOverlayMessage(copy, tinted);
            } finally {
                isModifying = false;
            }
            ci.cancel();
        } catch (Exception error) {
            isModifying = false;
            error.printStackTrace();
        }
    }

    private static boolean replaceBody(Text original, MutableText copy, DialogueParts parts,
                                       String translation) {
        if (parts.textIndices().isEmpty()) return false;

        boolean bold = parts.text().stream().allMatch(text -> text.getStyle().isBold());
        Style baseStyle = bold ? BODY_STYLES[0].withBold(true) : BODY_STYLES[0];
        Text rebuilt = TextEmojiUtils.rebuildDialogue(
                translation, parts.icons(), baseStyle);
        List<MutableText> lines = TextEmojiUtils.wrap(rebuilt,
                parts.hasPortrait() ? MAX_WIDTH - PORTRAIT_OFFSET : MAX_WIDTH);
        if (lines.isEmpty()) return false;
        lines = mergeOverflow(lines);

        boolean singleSibling = parts.text().size() == 1;
        boolean hasInlineIcon = translation.contains("<em>");
        MutableText replacement = Text.literal("");
        int firstText = parts.textIndices().getFirst();
        for (int index : parts.bodyIndices()) {
            if (index >= firstText) break;
            Text component = parts.siblings().get(index);
            if (isBodyTextFont(fontId(component))
                    && extractCleanText(component.getString()).trim().isEmpty()) {
                replacement.append(component.copy());
            }
        }

        // single-sibling mode (без Wynntils)
        if (singleSibling) {
            if (!hasInlineIcon) {
                String raw = parts.text().getFirst().getString();
                if (raw.length() >= 2 && Character.isHighSurrogate(raw.charAt(0))) {
                    replacement.append(Text.literal(raw.substring(0, 2))
                            .setStyle(BODY_STYLES[0]));
                }
            }

            for (int i = 0; i < lines.size(); i++) {
                MutableText line = moveToLine(lines.get(i), i);
                resetWidth(line);
                replacement.append(line);
            }
            compensate(replacement,
                    width(parts.text().getFirst()) - width(replacement), BODY_STYLES[0]);

        // multi-sibling mode (с Wynntils)
        } else {
            for (int i = 0; i < lines.size(); i++) {
                MutableText line = moveToLine(lines.get(i), i);
                if (i + 1 < lines.size()) resetWidth(line);
                replacement.append(line);
            }
        }

        int first = parts.bodyIndices().getFirst();
        int originalWidth = width(original);
        copy.getSiblings().set(first, replacement);
        for (int index : parts.bodyIndices()) {
            if (index != first) copy.getSiblings().set(index, Text.literal(""));
        }
        compensate(replacement, originalWidth - width(copy), BODY_STYLES[0]);
        copy.getSiblings().set(first, replacement);
        return true;
    }

    private static boolean replaceChoices(Text originalMessage, DialogueParts parts,
                                          String playerName, boolean stabilized) {
        boolean modified = false;
        List<Text> siblings = parts.siblings();
        for (List<Integer> group : parts.choices().values()) {
            StringBuilder source = new StringBuilder();
            int sourceWidth = 0;
            for (int index : group) {
                source.append(extractCleanText(siblings.get(index).getString()));
                sourceWidth += width(siblings.get(index));
            }

            String original = source.toString().trim();
            if (original.isEmpty()) continue;
            String originalKey = original.replace(playerName, "<playername>");
            if (stabilized && !TranslationPrinter.hasExactTranslation(originalKey)) {
                UntranslatedLogger.logDialogue(originalKey, parts.speaker(), true,
                        TranslationPrinter.getCurrentQuest(), originalMessage);
            }

            String translation = TranslationPrinter.translations.get(
                    originalKey.replace(" ", "").toLowerCase());
            if (translation == null) continue;

            int first = group.getFirst();
            Style style = siblings.get(first).getStyle();
            MutableText replacement = TextEmojiUtils.rebuildDialogue(
                    translation.replace("<playername>", playerName), List.of(), style).copy();
            compensate(replacement, sourceWidth - width(replacement), style.withBold(false));
            siblings.set(first, replacement);
            for (int i = 1; i < group.size(); i++) {
                siblings.set(group.get(i), Text.literal(""));
            }
            modified = true;
        }
        return modified;
    }

    private static List<MutableText> mergeOverflow(List<MutableText> source) {
        if (source.size() <= BODY_STYLES.length) return source;
        ArrayList<MutableText> result = new ArrayList<>(source.subList(0, BODY_STYLES.length));
        MutableText tail = result.getLast();
        for (int i = BODY_STYLES.length; i < source.size(); i++) {
            tail.append(Text.literal(" ").setStyle(BODY_STYLES[0])).append(source.get(i));
        }
        return result;
    }

    private static MutableText moveToLine(Text source, int line) {
        MutableText result = Text.literal("").setStyle(BODY_STYLES[line]);
        source.visit((style, value) -> {
            result.append(Text.literal(value).setStyle(moveBodyFont(style, line)));
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private static Style moveBodyFont(Style style, int line) {
        StyleSpriteSource source = style.getFont();
        if (!(source instanceof StyleSpriteSource.Font font)) return style;
        String id = font.id().toString();
        if (!isBodyTextFont(id) && !isBodyIconFont(id)) return style;
        String path = font.id().getPath();
        return style.withFont(new StyleSpriteSource.Font(Identifier.of(
                font.id().getNamespace(), path.substring(0, path.length() - 1) + line)));
    }

    private static void resetWidth(MutableText text) {
        compensate(text, -width(text), text.getStyle().withBold(false));
    }

    private static void compensate(MutableText text, int pixels, Style style) {
        if (pixels == 0) return;
        if (pixels < 0) {
            text.append(Text.literal("" + SPECIAL_CHAR
                    + (char) (ZERO_WIDTH_CHAR + pixels)).setStyle(style));
            return;
        }
        int spaces = (pixels + 3) / 4;
        int modulo = pixels % 4;
        StringBuilder value = new StringBuilder(" ".repeat(spaces));
        if (modulo != 0) {
            value.append(SPECIAL_CHAR).append((char) (ZERO_WIDTH_CHAR - (4 - modulo)));
        }
        text.append(Text.literal(value.toString()).setStyle(style));
    }

    private static int width(Text text) {
        return MinecraftClient.getInstance().textRenderer.getWidth(text);
    }

    private static boolean stabilize(String key) {
        String clean = key.replace(" ", "").toLowerCase();
        if (clean.equals(lastCleanKey)) {
            consecutiveCount++;
        } else {
            lastCleanKey = clean;
            consecutiveCount = 1;
        }
        return consecutiveCount >= 5;
    }

    private static String fontId(Text text) {
        StyleSpriteSource source = text.getStyle().getFont();
        return source instanceof StyleSpriteSource.Font font ? font.id().toString() : "";
    }

    private static boolean isBodyTextFont(String font) {
        return font.startsWith("minecraft:hud/dialogue/text/wynncraft/body_")
                && hasLineSuffix(font, 5);
    }

    private static boolean isBodyIconFont(String font) {
        return font.startsWith("minecraft:hud/dialogue/text/common/body_")
                && hasLineSuffix(font, 5);
    }

    private static boolean isChoiceFont(String font) {
        return font.startsWith("minecraft:hud/dialogue/text/wynncraft/choice_")
                && hasLineSuffix(font, 4);
    }

    private static boolean hasLineSuffix(String font, int count) {
        char line = font.charAt(font.length() - 1);
        return line >= '0' && line < '0' + count;
    }

    private record DialogueParts(List<Text> siblings, List<Integer> bodyIndices,
                                 List<Integer> textIndices, List<Text> text,
                                 List<Text> icons, Map<String, List<Integer>> choices,
                                 boolean hasPortrait, String speaker) {

        private static DialogueParts read(MutableText message) {
            List<Text> siblings = message.getSiblings();
            List<Integer> body = new ArrayList<>();
            List<Integer> textIndices = new ArrayList<>();
            List<Text> text = new ArrayList<>();
            List<Text> icons = new ArrayList<>();
            Map<String, List<Integer>> choices = new LinkedHashMap<>();
            boolean portrait = false;
            String speaker = "";

            for (int i = 0; i < siblings.size(); i++) {
                Text sibling = siblings.get(i);
                String font = fontId(sibling);
                portrait |= font.contains("dialogue/portrait");
                if (font.contains("dialogue/text/nameplate")) {
                    String candidate = extractCleanText(sibling.getString()).trim();
                    if (candidate.codePoints().anyMatch(Character::isLetter)
                            && candidate.length() > speaker.length()) {
                        speaker = candidate;
                    }
                }
                if (isBodyTextFont(font)) {
                    body.add(i);
                    if (!extractCleanText(sibling.getString()).trim().isEmpty()) {
                        textIndices.add(i);
                        text.add(sibling);
                    }
                } else if (isBodyIconFont(font)) {
                    body.add(i);
                    icons.add(sibling);
                } else if (isChoiceFont(font)
                        && !extractCleanText(sibling.getString()).trim().isEmpty()) {
                    choices.computeIfAbsent(font, ignored -> new ArrayList<>()).add(i);
                }
            }
            return new DialogueParts(siblings, body, textIndices, text, icons,
                    choices, portrait, speaker);
        }

        private String key() {
            StringBuilder key = new StringBuilder();
            for (Text component : text) {
                if (!key.isEmpty()) key.append(' ');
                key.append(extractCleanText(component.getString()));
            }
            return key.toString().trim().replaceAll(" +", " ");
        }
    }
}
