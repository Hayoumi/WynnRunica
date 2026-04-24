package com.WynnRunica.mixin;

import com.WynnRunica.TranslationPrinter;
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
import java.util.List;

@Mixin(InGameHud.class)
public class TitleTrackerMixin {

    private static boolean isModifying = false;
    private static final int MAX_WIDTH = 234;
    private static final char SPECIAL_CHAR = '\uDAFF';
    private static final char ZERO_WIDTH_CHAR = '\uE000';
    private static final Style[] bodyStyles = new Style[5];

    static {
        for (int i = 0; i < 5; i++) {
            bodyStyles[i] = Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("minecraft", "hud/dialogue/text/wynncraft/body_" + i)));

        }
    }

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void onSetOverlay(Text message, boolean tinted, CallbackInfo ci) {
        if (isModifying) return;

        try {
            if (message == null) return;

            List<Text> siblings = ((MutableText) message).getSiblings();
            ArrayList<Integer> textIndices = new ArrayList<>();
            ArrayList<Text> textSibs = new ArrayList<>();

            for (int i = 0; i < siblings.size(); i++) {
                Text sib = siblings.get(i);
                if (sib.getStyle().getFont() != null &&
                        sib.getStyle().getFont().toString().contains("body_") &&
                        sib.getString().length() > 2) {
                    textIndices.add(i);
                    textSibs.add(sib);
                }
            }
            if (textSibs.isEmpty()) return;

            StringBuilder keyBuilder = new StringBuilder();
            int totalOriginalWidth = 0;
            for (Text sib : textSibs) {
                if (keyBuilder.length() > 0) keyBuilder.append(" ");
                keyBuilder.append(extractCleanText(sib.getString()));
                totalOriginalWidth += getRenderWidth(sib);
            }

            String key = keyBuilder.toString().trim().replaceAll(" +", " ");
            if (key.isEmpty()) return;

            String playerName = MinecraftClient.getInstance().getSession().getUsername();
            key = key.replace(playerName, "<playername>");

            String translation = TranslationPrinter.getTranslation(key);
            if (translation.equals(key)) return;

            translation = translation.replace("<playername>", playerName);

            ArrayList<String> lines = splitTextIntoLines(translation, bodyStyles[0], 0);
            if (lines.isEmpty()) return;

            MutableText copy = Text.literal(lines.get(0)).setStyle(bodyStyles[0]);
            for (int i = 1; i < lines.size() && i < 5; i++) {
                copy = manageWidth(copy);
                copy.append(Text.literal(lines.get(i)).setStyle(bodyStyles[i]));
            }

            int newWidth = getRenderWidth(copy);
            int diff = totalOriginalWidth - newWidth;
            if (diff > 0) {
                int spaces = (diff + 3) / 4;
                int modulo = diff % 4;
                StringBuilder sb = new StringBuilder(" ".repeat(spaces));
                if (modulo != 0) {
                    sb.append(SPECIAL_CHAR).append((char)(ZERO_WIDTH_CHAR - (4 - modulo)));
                }
                copy.append(Text.literal(sb.toString()).setStyle(bodyStyles[0]));
            } else if (diff < 0) {
                int backUp = -diff;
                copy.append(Text.literal("" + SPECIAL_CHAR + (char)(ZERO_WIDTH_CHAR - backUp)).setStyle(bodyStyles[0]));
            }

            siblings.set(textIndices.get(0), copy);
            for (int i = 1; i < textIndices.size(); i++) {
                String fontStr = textSibs.get(i).getStyle().getFont().toString();
                int n = 0;
                for (int j = 1; j < 5; j++) {
                    if (fontStr.contains("body_" + j)) { n = j; break; }
                }
                siblings.set(textIndices.get(i), Text.literal("").setStyle(bodyStyles[n]));
            }

            isModifying = true;
            ((InGameHud)(Object)this).setOverlayMessage(message, tinted);
            isModifying = false;
            ci.cancel();

        } catch (Exception e) {
            isModifying = false;
            System.out.println("WynnRunica oshibka: " + e.getMessage());
        }
    }

    private String extractCleanText(String text) {
        if (text.length() <= 2) return "";
        StringBuilder out = new StringBuilder();
        boolean skipNext = false;
        for (char c : text.toCharArray()) {
            if (skipNext) { skipNext = false; continue; }
            if (c == SPECIAL_CHAR) { out.append(" "); skipNext = true; continue; }
            out.append(c);
        }
        return out.toString();
    }

    private ArrayList<String> splitTextIntoLines(String text, Style style, int adjustWidth) {
        ArrayList<String> lines = new ArrayList<>();
        String remaining = text;
        while (!remaining.isEmpty()) {
            if (lines.size() == 5) {
                lines.set(4, lines.get(4) + " " + remaining);
                break;
            }
            if (getRenderWidth(Text.literal(remaining).setStyle(style)) <= MAX_WIDTH - adjustWidth) {
                lines.add(remaining);
                break;
            }
            int splitIndex = findBestSplitIndex(remaining, style, MAX_WIDTH - adjustWidth);
            if (splitIndex <= 0) splitIndex = 1;
            lines.add(remaining.substring(0, splitIndex).stripTrailing());
            remaining = remaining.substring(splitIndex).stripLeading();
        }
        return lines;
    }

    private int findBestSplitIndex(String text, Style style, int maxWidth) {
        int low = 0, high = text.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (getRenderWidth(Text.literal(text.substring(0, mid)).setStyle(style)) <= maxWidth)
                low = mid;
            else
                high = mid - 1;
        }
        int lastSpace = text.lastIndexOf(' ', low);
        return lastSpace > 0 ? lastSpace : low;
    }

    private MutableText manageWidth(MutableText component) {
        int width = getRenderWidth(component);
        if (width == 0) return component;
        String specialChars;
        if (width > 0) {
            specialChars = SPECIAL_CHAR + "" + (char)(ZERO_WIDTH_CHAR - width);
        } else {
            int needed = -width;
            int spaces = (needed + 3) / 4;
            int modulo = needed % 4;
            StringBuilder sb = new StringBuilder(" ".repeat(spaces));
            if (modulo != 0) {
                sb.append(SPECIAL_CHAR).append((char)(ZERO_WIDTH_CHAR - (4 - modulo)));
            }
            specialChars = sb.toString();
        }
        MutableText appendment = Text.literal(specialChars).setStyle(component.getStyle());
        return component.append(appendment);
    }

    private int getRenderWidth(Text component) {
        return MinecraftClient.getInstance().textRenderer.getWidth(component);
    }
}