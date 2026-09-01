package com.WynnRunica;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.PlayerInput;

import static com.WynnRunica.TextUtils.extractCleanText;

public final class DialogueInstantReveal {
    private static String previousBody = "";
    private static boolean grewSinceTick;
    private static boolean pulseActive;

    private DialogueInstantReveal() {}

    public static void observe(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        Snapshot snapshot = inspect(message);
        if (!snapshot.hasBody || snapshot.body.isEmpty()) {
            release(client);
            resetPage();
            return;
        }

        String body = snapshot.body;
        String previous = previousBody;
        previousBody = body;

        // Импульс шифта посылается ТОЛЬКО в момент, когда текст реально
        // дорисовался на этом пакете. Пока строка печатается, шифт её
        // раскрывает; как только печать кончилась, роста нет, импульса нет,
        // и пролистнуть реплику мы уже не можем.
        boolean growing = !previous.isEmpty()
                && !body.equals(previous)
                && body.startsWith(previous);
        if (!growing || snapshot.hasChoices) {
            release(client);
            return;
        }

        if (pulseActive) {
            grewSinceTick = true;
        } else {
            startPulse(client);
        }
    }

    public static void tick(MinecraftClient client) {
        if (!WynnRunicaClient.enabled
                || client.player == null || client.getNetworkHandler() == null) {
            if (pulseActive && client.player != null && client.getNetworkHandler() != null) {
                sendInput(client, client.player.input.playerInput.sneak());
            }
            pulseActive = false;
            grewSinceTick = false;
            resetPage();
            return;
        }
        if (!pulseActive) return;

        // Держим шифт ровно столько, сколько текст продолжает расти. Первый же
        // тик без роста отпускает его: раньше он висел шесть тиков вслепую и на
        // короткой реплике успевал сработать как «дальше».
        if (grewSinceTick) {
            grewSinceTick = false;
            sendInput(client, true);
        } else {
            release(client);
        }
    }

    // Отпустить шифт немедленно, не дожидаясь следующего тика. Пока печать шла,
    // нажатие раскрывало строку; на допечатанной строке то же нажатие для
    // сервера уже «дальше», поэтому каждый лишний тик удержания - это шанс
    // проскочить короткую реплику.
    private static void release(MinecraftClient client) {
        if (!pulseActive) return;
        pulseActive = false;
        grewSinceTick = false;
        if (client != null && client.player != null && client.getNetworkHandler() != null) {
            sendInput(client, client.player.input.playerInput.sneak());
        }
    }

    public static void reset() {
        pulseActive = false;
        grewSinceTick = false;
        resetPage();
    }

    private static boolean startPulse(MinecraftClient client) {
        if (pulseActive || client.player == null || client.getNetworkHandler() == null) return false;
        PlayerInput input = client.player.input.playerInput;
        if (input.sneak() || client.options.sneakKey.isPressed()) return false;
        sendInput(client, true);
        pulseActive = true;
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
