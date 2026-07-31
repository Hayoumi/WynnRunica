package com.WynnRunica;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.PlayerInput;

import static com.WynnRunica.TextUtils.extractCleanText;

public final class DialogueInstantReveal {
    private static final int RELEASE_DELAY_TICKS = 6;

    private static String previousBody = "";
    private static boolean typingObserved;
    private static boolean pulsed;
    private static boolean pulseActive;
    private static int releaseTicks;

    private DialogueInstantReveal() {}

    public static void observe(Text message) {
        Snapshot snapshot = inspect(message);
        if (!snapshot.hasBody || snapshot.body.isEmpty()) {
            resetPage();
            return;
        }

        String body = snapshot.body;
        if (previousBody.isEmpty()) {
            typingObserved = false;
            pulsed = false;
        } else if (!body.equals(previousBody)) {
            if (body.startsWith(previousBody)) {
                typingObserved = true;
            } else {
                typingObserved = false;
                pulsed = false;
            }
        }
        previousBody = body;

        if (typingObserved && !pulsed && snapshot.requiresShift && !snapshot.hasChoices) {
            pulsed = startPulse(MinecraftClient.getInstance());
        }
    }

    public static void tick(MinecraftClient client) {
        if (!WynnRunicaClient.enabled) {
            if (pulseActive && client.player != null && client.getNetworkHandler() != null) {
                sendInput(client, client.player.input.playerInput.sneak());
            }
            pulseActive = false;
            releaseTicks = 0;
            resetPage();
            return;
        }
        if (client.player == null || client.getNetworkHandler() == null) {
            pulseActive = false;
            releaseTicks = 0;
            resetPage();
            return;
        }
        if (pulseActive) {
            if (--releaseTicks <= 0) {
                sendInput(client, client.player.input.playerInput.sneak());
                pulseActive = false;
            } else {
                sendInput(client, true);
            }
        }
    }

    public static void reset() {
        pulseActive = false;
        releaseTicks = 0;
        resetPage();
    }

    private static boolean startPulse(MinecraftClient client) {
        if (pulseActive || client.player == null || client.getNetworkHandler() == null) return false;
        PlayerInput input = client.player.input.playerInput;
        if (input.sneak() || client.options.sneakKey.isPressed()) return false;
        sendInput(client, true);
        pulseActive = true;
        releaseTicks = RELEASE_DELAY_TICKS;
        return true;
    }

    private static void sendInput(MinecraftClient client, boolean sneak) {
        PlayerInput input = client.player.input.playerInput;
        PlayerInput overridden = new PlayerInput(
                input.forward(), input.backward(), input.left(), input.right(),
                input.jump(), sneak, input.sprint());
        client.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(overridden));
    }

    private static void resetPage() {
        previousBody = "";
        typingObserved = false;
        pulsed = false;
    }

    private static Snapshot inspect(Text message) {
        SnapshotBuilder builder = new SnapshotBuilder();
        collect(message, Style.EMPTY, builder);
        String body = builder.body.toString().replaceAll("\\s+", "");
        return new Snapshot(body, builder.hasBody, builder.hasChoices, builder.requiresShift);
    }

    private static void collect(Text node, Style parent, SnapshotBuilder builder) {
        Style style = node.getStyle().withParent(parent);
        StyleSpriteSource source = style.getFont();
        String font = source == null ? "" : source.toString();
        boolean bodyFont = font.contains("dialogue/text/wynncraft/body_");
        boolean choiceFont = font.contains("dialogue/text/wynncraft/choice_");
        boolean controlFont = font.contains("dialogue/text/control");

        node.getContent().visit(value -> {
            if (!value.isEmpty()) {
                if (bodyFont) {
                    builder.hasBody = true;
                    builder.body.append(extractCleanText(value));
                }
                if (choiceFont && !extractCleanText(value).trim().isEmpty()) {
                    builder.hasChoices = true;
                }
                if (controlFont) {
                    builder.requiresShift = true;
                }
            }
            return java.util.Optional.empty();
        });

        for (Text sibling : node.getSiblings()) {
            collect(sibling, style, builder);
        }
    }

    private record Snapshot(String body, boolean hasBody, boolean hasChoices, boolean requiresShift) {}

    private static final class SnapshotBuilder {
        private final StringBuilder body = new StringBuilder();
        private boolean hasBody;
        private boolean hasChoices;
        private boolean requiresShift;
    }
}
