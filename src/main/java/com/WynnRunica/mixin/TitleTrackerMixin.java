package com.WynnRunica.mixin;

import com.WynnRunica.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
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

@Mixin(InGameHud.class)
public class TitleTrackerMixin {

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"))
    private void animateChoices(CallbackInfo ci) {
        ChoiceTicker.renderFrame();
    }

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
        if (!Config.isTranslationEnabled() || isModifying || message == null) return;

        try {
            MutableText copy = message.copy();
            DialogueParts parts = DialogueParts.read(copy);
            boolean dialogueOverlay = parts.speakerIndex() != -1
                    || parts.hasPortrait()
                    || !parts.choices().isEmpty();
            DialogueInstantReveal.observe(message, !parts.choices().isEmpty(), dialogueOverlay);
            String key = parts.key();
            if (key.isEmpty() && dialogueOverlay) {
                key = fallbackDialogueKey(message);
            }
            if (key.isEmpty() && parts.choices().isEmpty()) {
                TranslationManager.resetTypingTranslation();
                ChoiceFrameStitcher.onDialogueChange("");
                ChoiceTicker.reset();
                return;
            }
            if (parts.choices().isEmpty()) {
                ChoiceFrameStitcher.onDialogueChange("");
                ChoiceTicker.reset();
            }

            boolean stabilized = stabilize(key);
            String playerName = MinecraftClient.getInstance().getSession().getUsername();
            String lookupKey = key.replace(playerName, "<playername>");
            QuestTracker.QuestInfo trackedQuest = QuestTracker.detect();
            String questContext = !trackedQuest.name().isEmpty()
                    ? trackedQuest.name() : TranslationManager.getCurrentQuest();
            boolean exactTranslation = questContext != null && !questContext.isBlank()
                    ? TranslationManager.hasExactTranslationInContext(lookupKey, questContext, "dialogue")
                    : TranslationManager.hasExactTranslation(lookupKey);
            if (exactTranslation) TranslationManager.observeExactDialogue(lookupKey);
            DialogueTypingMatcher.Result typing = exactTranslation ? null
                    : TranslationManager.getTypingTranslation(lookupKey, stabilized);
            if (dialogueOverlay) {
                if (exactTranslation || typing != null)
                    DialogueInstantReveal.expect(exactTranslation ? lookupKey : typing.source());
                else DialogueInstantReveal.expectFromCatalog(lookupKey);
            }
            String translation = exactTranslation
                    ? (questContext != null && !questContext.isBlank()
                        ? TranslationManager.getTranslationInContext(lookupKey, questContext, "dialogue")
                        : TranslationManager.getTranslation(lookupKey, stabilized))
                    : typing != null ? typing.visibleTranslation() : lookupKey;

            if (Config.isEnabled("Отправка строк")) {
                TelemetrySender.observeDialogue(lookupKey, exactTranslation ? translation : "",
                        parts.speaker(), questContext, trackedQuest.stage(), message,
                        dialogueOverlay && DialogueInstantReveal.isComplete(message, !parts.choices().isEmpty()));
            }
            if (stabilized) {
                String quest = questContext;
                UntranslatedLogger.logDialogue(lookupKey, parts.speaker(), false,
                        quest, message);
            }

            boolean modified = false;
            if (Config.isEnabled("Диалоги") && !translation.equals(lookupKey)) {
                String visibleTranslation = translation.replace("<playername>", playerName);
                String layoutTranslation = (typing != null ? typing.translation() : translation)
                        .replace("<playername>", playerName);
                modified = replaceBody(message, copy, parts,
                        visibleTranslation, layoutTranslation);
            }
            if (Config.isEnabled("Диалоги")) {
                modified |= replaceChoices(message, parts, playerName, stabilized);
                modified |= replaceControls(copy);
            }
            if (Config.isEnabled("Имена NPC")) {
                modified |= replaceSpeaker(copy, parts);
            }
            if (!modified) {
                if (parts.choices().isEmpty()) {
                    ChoiceTicker.reset();
                }
                return;
            }

            isModifying = true;
            try {
                ChoiceTicker.setActiveOverlay(copy);
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

    private static boolean replaceControls(MutableText message) {
        boolean changed = false;
        for (int i = 0; i < message.getSiblings().size(); i++) {
            Text source = message.getSiblings().get(i);
            if (!fontId(source).equals("minecraft:hud/dialogue/text/control")) continue;
            MutableText replacement = Text.empty();
            boolean[] translated = {false};
            source.visit((style, value) -> {
                String result = value.replace("to continue", "продолжить")
                        .replace("to choose option", "выбрать ответ")
                        .replace("to confirm", "подтвердить");
                translated[0] |= !result.equals(value);
                replacement.append(Text.literal(result).setStyle(style));
                return java.util.Optional.empty();
            }, Style.EMPTY);
            if (!translated[0]) continue;
            int difference = width(source) - width(replacement);
            if (difference != 0) {
                replacement.append(Text.literal(new String(Character.toChars(0xD0000 + difference)))
                        .setStyle(Style.EMPTY.withFont(new StyleSpriteSource.Font(
                                Identifier.of("minecraft", "space")))));
            }
            message.getSiblings().set(i, replacement);
            changed = true;
        }
        return changed;
    }

    private static boolean replaceBody(Text original, MutableText copy, DialogueParts parts,
                                       String translation, String layoutTranslation) {
        if (parts.textIndices().isEmpty()) return false;

        boolean bold = parts.text().stream().allMatch(text -> text.getStyle().isBold());
        Style baseStyle = bold ? BODY_STYLES[0].withBold(true) : BODY_STYLES[0];
        TextColor color = parts.text().isEmpty() ? null
                : parts.text().getFirst().getStyle().getColor();
        if (color != null && parts.text().stream()
                .allMatch(text -> color.equals(text.getStyle().getColor()))) {
            baseStyle = baseStyle.withColor(color);
        }
        Text rebuilt = TextEmojiUtils.rebuildDialogue(
                translation, parts.icons(), baseStyle);
        int wrapWidth = parts.hasPortrait() ? MAX_WIDTH - PORTRAIT_OFFSET : MAX_WIDTH;
        Text layout = translation.equals(layoutTranslation) ? rebuilt
                : TextEmojiUtils.rebuildDialogue(layoutTranslation, parts.icons(), baseStyle);
        List<MutableText> lines = wrapWithStableLayout(rebuilt, layout, wrapWidth);
        if (lines.isEmpty()) return false;
        lines = mergeOverflow(lines);

        boolean singleSibling = parts.text().size() == 1;
        boolean hasInlineIcon = translation.contains("<em>");
        MutableText replacement = Text.literal("");
        int firstText = parts.textIndices().getFirst();
        int indent = 0;
        for (int index : parts.bodyIndices()) {
            if (index >= firstText) break;
            Text component = parts.siblings().get(index);
            String raw = component.getString();
            if (isBodyTextFont(fontId(component))
                    && extractCleanText(raw).trim().isEmpty()) {
                replacement.append(component.copy());
                if (!raw.isEmpty() && raw.chars().allMatch(character -> character == ' ')) {
                    indent += width(component);
                }
            }
        }

        if (isCenteredHint(parts)) {
            List<MutableText> fullLines = new ArrayList<>();
            for (MutableText line : TextEmojiUtils.wrap(layout, wrapWidth)) {
                if (!line.getString().isEmpty()) fullLines.add(line);
            }
            fullLines = mergeOverflow(fullLines);
            for (int i = 0; i < lines.size(); i++) {
                MutableText line = moveToLine(lines.get(i), i);
                int fullWidth = width(line);
                if (i < fullLines.size()) fullWidth = width(moveToLine(fullLines.get(i), i));
                int shift = (MAX_WIDTH - 1 - fullWidth) / 2;
                compensate(replacement, shift, BODY_STYLES[i]);
                if (i + 1 < lines.size()) resetWidth(line, shift);
                replacement.append(line);
            }
        } else if (singleSibling) {
            if (!hasInlineIcon && parts.icons().isEmpty()) {
                String raw = parts.text().getFirst().getString();
                if (raw.length() >= 2 && Character.isHighSurrogate(raw.charAt(0))) {
                    replacement.append(Text.literal(raw.substring(0, 2))
                            .setStyle(BODY_STYLES[0]));
                }
            }

            for (int i = 0; i < lines.size(); i++) {
                MutableText line = moveToLine(lines.get(i), i);
                resetWidth(line, i == 0 ? indent : 0);
                replacement.append(line);
            }
            compensate(replacement,
                    width(parts.text().getFirst()) - width(replacement), BODY_STYLES[0]);

        } else {
            for (int i = 0; i < lines.size(); i++) {
                MutableText line = moveToLine(lines.get(i), i);
                if (i + 1 < lines.size()) resetWidth(line, i == 0 ? indent : 0);
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

    private static boolean isCenteredHint(DialogueParts parts) {
        if (parts.speakerIndex() != -1 || parts.hasPortrait()) return false;
        String first = parts.text().getFirst().getString();
        return !first.isEmpty() && first.codePointAt(0) >= 0xC0000;
    }

    private static List<MutableText> wrapWithStableLayout(Text visible, Text layout, int width) {
        String visibleString = visible.getString();
        String layoutString = layout.getString();
        if (!layoutString.startsWith(visibleString)) {
            return TextEmojiUtils.wrap(visible, width);
        }

        List<MutableText> layoutLines = TextEmojiUtils.wrap(layout, width);
        List<MutableText> result = new ArrayList<>();
        int visibleEnd = visibleString.length();
        int searchFrom = 0;

        for (MutableText line : layoutLines) {
            String lineString = line.getString();
            if (lineString.isEmpty()) continue;
            int start = layoutString.indexOf(lineString, searchFrom);
            if (start < 0) return TextEmojiUtils.wrap(visible, width);

            int shown = Math.min(lineString.length(), Math.max(0, visibleEnd - start));
            if (shown > 0) result.add(takePrefix(line, shown));
            searchFrom = start + lineString.length();
            if (visibleEnd <= searchFrom) break;
        }

        return result.isEmpty() ? TextEmojiUtils.wrap(visible, width) : result;
    }

    private static MutableText takePrefix(Text source, int length) {
        MutableText result = Text.literal("");
        int[] remaining = {length};
        source.visit((style, value) -> {
            if (remaining[0] <= 0) return java.util.Optional.of(true);
            int end = Math.min(value.length(), remaining[0]);
            if (end > 0 && end < value.length()
                    && Character.isHighSurrogate(value.charAt(end - 1))) {
                end--;
            }
            if (end > 0) {
                result.append(Text.literal(value.substring(0, end)).setStyle(style));
                remaining[0] -= end;
            }
            return remaining[0] <= 0
                    ? java.util.Optional.of(true) : java.util.Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private static boolean replaceChoices(Text originalMessage, DialogueParts parts,
                                          String playerName, boolean stabilized) {
        boolean modified = false;
        List<Text> siblings = parts.siblings();
        int choiceIndex = 0;
        String choiceSetKey = parts.key();
        ChoiceTicker.beginChoices(choiceSetKey);
        for (List<Integer> group : parts.choices().values()) {
            choiceIndex++;
            StringBuilder source = new StringBuilder();
            int sourceWidth = 0;
            for (int index : group) {
                source.append(extractCleanText(siblings.get(index).getString()));
                sourceWidth += width(siblings.get(index));
            }

            String original = source.toString().trim();
            if (original.isEmpty()) continue;
            String originalKey = original.replace(playerName, "<playername>");
            QuestTracker.QuestInfo qInfo = QuestTracker.detect();
            String quest = !qInfo.name().isEmpty() ? qInfo.name() : TranslationManager.getCurrentQuest();
            if (stabilized) {
                ChoiceFrameStitcher.observe(choiceSetKey, choiceIndex, originalKey,
                        parts.speaker(), quest, qInfo.stage(), originalMessage, sourceWidth);
            }

            String fullRu = ChoiceTranslator.findFullTranslation(originalKey, quest);
            if (fullRu == null) continue;

            int first = group.getFirst();
            Style style = siblings.get(first).getStyle();
            List<Text> icons = new ArrayList<>();
            for (int index : group) icons.addAll(TextEmojiUtils.extract(siblings.get(index)).icons);
            MutableText replacement = ChoiceTicker.getOrUpdateSlot(
                    choiceIndex, fullRu, sourceWidth, first, style, playerName, icons);
            siblings.set(first, replacement);
            for (int i = 1; i < group.size(); i++) {
                siblings.set(group.get(i), Text.literal(""));
            }
            modified = true;
        }
        ChoiceTicker.endChoices();
        return modified;
    }

    private static boolean replaceSpeaker(MutableText copy, DialogueParts parts) {
        if (parts.speakerIndex() == -1 || parts.speaker().isEmpty()) return false;
        String translation = TranslationManager.npcTranslations.get(parts.speaker());
        if (translation == null) return false;

        Text originalSibling = copy.getSiblings().get(parts.speakerIndex());
        String speaker = parts.speaker();
        MutableText replacement = Text.literal("");
        Style spacerStyle = BODY_STYLES[0].withBold(false);
        boolean[] replaced = {false};
        originalSibling.visit((style, value) -> {
            int at = replaced[0] ? -1 : value.indexOf(speaker);
            if (at < 0) {
                replacement.append(Text.literal(value).setStyle(style));
                return java.util.Optional.empty();
            }
            replaced[0] = true;
            Text name = Text.literal(translation).setStyle(style);
            int diff = width(Text.literal(speaker).setStyle(style)) - width(name);
            replacement.append(Text.literal(value.substring(0, at)).setStyle(style));
            compensate(replacement, diff / 2, spacerStyle);
            replacement.append(name);
            compensate(replacement, diff - diff / 2, spacerStyle);
            replacement.append(Text.literal(value.substring(at + speaker.length())).setStyle(style));
            return java.util.Optional.empty();
        }, Style.EMPTY);
        if (!replaced[0]) return false;

        copy.getSiblings().set(parts.speakerIndex(), replacement);
        return true;
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

    private static void resetWidth(MutableText text, int extra) {
        compensate(text, -width(text) - extra, text.getStyle().withBold(false));
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
        return fontId(text.getStyle());
    }

    private static String fontId(Style style) {
        StyleSpriteSource source = style.getFont();
        return source instanceof StyleSpriteSource.Font font ? font.id().toString() : "";
    }

    private static boolean isBodyTextFont(String font) {
        return font.startsWith("minecraft:hud/dialogue/text/wynncraft/body_")
                && hasLineSuffix(font, 5);
    }

    private static boolean isBodyIconFont(String font) {
        return (font.startsWith("minecraft:hud/dialogue/text/common/body_")
                || font.startsWith("minecraft:hud/dialogue/text/merchant/body_")
                || font.startsWith("minecraft:hud/dialogue/text/currency/body_")
                || font.startsWith("minecraft:hud/dialogue/text/keybind/body_"))
                && hasLineSuffix(font, 5);
    }

    private static boolean isChoiceFont(String font) {
        return font.startsWith("minecraft:hud/dialogue/text/wynncraft/choice_")
                && hasLineSuffix(font, 4);
    }

    private static String fallbackDialogueKey(Text message) {
        StringBuilder result = new StringBuilder();
        message.visit((style, value) -> {
            String font = fontId(style);
            boolean dialogueBody = font.startsWith("minecraft:hud/dialogue/text/")
                    && !font.contains("nameplate")
                    && !font.contains("control")
                    && !font.contains("choice_");
            String clean = dialogueBody ? extractCleanText(value).trim() : "";
            if (!clean.isEmpty()) {
                if (!result.isEmpty()) result.append(' ');
                result.append(clean);
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return result.toString().trim().replaceAll(" +", " ");
    }

    private static boolean hasLineSuffix(String font, int count) {
        char line = font.charAt(font.length() - 1);
        return line >= '0' && line < '0' + count;
    }

    private record DialogueParts(List<Text> siblings, List<Integer> bodyIndices,
                                 List<Integer> textIndices, List<Text> text,
                                 List<Text> icons, Map<String, List<Integer>> choices,
                                 boolean hasPortrait, String speaker, int speakerIndex) {

        private static DialogueParts read(MutableText message) {
            List<Text> siblings = message.getSiblings();
            List<Integer> body = new ArrayList<>();
            List<Integer> textIndices = new ArrayList<>();
            List<Text> text = new ArrayList<>();
            List<Text> icons = new ArrayList<>();
            Map<String, List<Integer>> choices = new LinkedHashMap<>();
            boolean portrait = false;
            String speaker = "";
            int speakerIndex = -1;

            for (int i = 0; i < siblings.size(); i++) {
                Text sibling = siblings.get(i);
                String font = fontId(sibling);
                portrait |= font.contains("dialogue/portrait");
                if (font.contains("dialogue/text/nameplate")) {
                    String candidate = extractCleanText(sibling.getString()).trim();
                    if (candidate.codePoints().anyMatch(Character::isLetter)
                            && candidate.length() > speaker.length()) {
                        speaker = candidate;
                        speakerIndex = i;
                    }
                }
                if (isBodyTextFont(font)) {
                    body.add(i);
                    if (!extractCleanText(sibling.getString()).trim().isEmpty()) {
                        textIndices.add(i);
                        text.add(sibling);
                        icons.addAll(TextEmojiUtils.extract(sibling).icons);
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
                    choices, portrait, speaker, speakerIndex);
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
