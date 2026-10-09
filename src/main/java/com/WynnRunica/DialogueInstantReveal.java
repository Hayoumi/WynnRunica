package com.WynnRunica;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.PlayerInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import static com.WynnRunica.TextUtils.extractCleanText;

public final class DialogueInstantReveal {
    private static final Logger LOGGER = LoggerFactory.getLogger("WynnRunica/Reveal");
    private static final Path LOG_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("WynnRunica/reveal_debug.log");
    private static final DialogueRevealController controller = new DialogueRevealController(
            DialogueInstantReveal::log);
    private static boolean logStarted;
    private static boolean logFailureReported;
    private static long clientTick;

    private DialogueInstantReveal() {}

    public static void observe(Text message) {
        observe(message, false, true);
    }

    public static void observe(Text message, boolean choicesPresent) {
        observe(message, choicesPresent, true);
    }

    public static boolean isComplete(Text message, boolean choicesPresent) {
        if (message == null) return false;
        Snapshot snapshot = new Snapshot();
        collect(message, Style.EMPTY, snapshot);
        return !snapshot.body.isEmpty()
                && (snapshot.ready || !snapshot.typing && (choicesPresent || snapshot.choices));
    }

    public static void observe(Text message, boolean choicesPresent, boolean dialoguePresent) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!enabled()) {
            apply(client, controller.reset("DISABLED"));
            return;
        }
        Snapshot snapshot = new Snapshot();
        collect(message, Style.EMPTY, snapshot);
        DialogueRevealController.Control control = snapshot.ready
                ? DialogueRevealController.Control.READY
                : snapshot.typing ? DialogueRevealController.Control.TYPING
                : DialogueRevealController.Control.UNKNOWN;
        apply(client, controller.observe(snapshot.body.toString().replaceAll("\\s+", ""),
                snapshot.speaker.toString().trim(), control, choicesPresent || snapshot.choices,
                dialoguePresent, clientTick, System.nanoTime()));
    }

    public static void expect(String fullLine) {
        String compact = fullLine == null ? "" : fullLine.replaceAll("\\s+", "");
        controller.expect(fullLine == null ? -1 : compact.codePointCount(0, compact.length()));
    }

    public static void expectFromCatalog(String typed) {
        controller.expect(DialogueRevealController.lineLength(typed));
    }

    public static void tick(MinecraftClient client) {
        clientTick++;
        if (client != null && client.player != null && client.getNetworkHandler() != null) {
            var entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
            if (entry != null) controller.latency((entry.getLatency() + 49) / 50);
        }
        boolean connected = client != null && client.player != null && client.getNetworkHandler() != null;
        apply(client, controller.tick(clientTick, System.nanoTime(), connected && enabled(),
                connected && client.player.input.playerInput.sneak(),
                client != null && client.currentScreen != null));
    }

    public static void reset() {
        apply(MinecraftClient.getInstance(), controller.reset("RESET"));
    }

    private static boolean enabled() {
        return Config.isTranslationEnabled() && Config.isEnabled("Быстрый диалог");
    }

    private static void apply(MinecraftClient client, DialogueRevealController.Action action) {
        if (action == DialogueRevealController.Action.NONE || client == null || client.player == null
                || client.getNetworkHandler() == null) return;
        PlayerInput input = client.player.input.playerInput;
        boolean sneak = action == DialogueRevealController.Action.PRESS || input.sneak();
        client.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(new PlayerInput(
                input.forward(), input.backward(), input.left(), input.right(),
                input.jump(), sneak, input.sprint())));
    }

    private static void collect(Text node, Style parent, Snapshot snapshot) {
        Style style = node.getStyle().withParent(parent);
        StyleSpriteSource fontSource = style.getFont();
        String font = fontSource instanceof StyleSpriteSource.Font source
                ? source.id().toString() : "";
        node.getContent().visit(value -> {
            if (font.contains("dialogue/text/wynncraft/body_")) {
                snapshot.body.append(extractCleanText(value));
            } else if (font.contains("dialogue/text/nameplate")) {
                snapshot.speaker.append(extractCleanText(value));
            } else if (font.equals("minecraft:hud/dialogue/text/control")) {
                snapshot.typing |= value.indexOf('\uE000') >= 0;
                snapshot.ready |= value.indexOf('\uE001') >= 0;
            } else if (font.contains("dialogue/text/wynncraft/choice_")) {
                snapshot.choices |= !extractCleanText(value).isBlank();
            }
            return java.util.Optional.empty();
        });
        for (Text sibling : node.getSiblings()) collect(sibling, style, snapshot);
    }

    private static void log(String message) {
        if (!Config.DEBUG) return;
        try {
            Files.createDirectories(LOG_PATH.getParent());
            if (Files.exists(LOG_PATH) && Files.size(LOG_PATH) > 8 * 1024 * 1024) {
                Files.move(LOG_PATH, LOG_PATH.resolveSibling("reveal_debug.previous.log"),
                        StandardCopyOption.REPLACE_EXISTING);
                logStarted = false;
            }
            String header = logStarted ? "" : System.currentTimeMillis()
                    + " REVEAL_LOG_VERSION=4 single_pulse=true max_hold_ms=200 max_hold_ticks=4 fallback_enabled=true\n";
            Files.writeString(LOG_PATH, header + System.currentTimeMillis() + " " + message + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            logStarted = true;
        } catch (Exception error) {
            if (!logFailureReported) {
                LOGGER.warn("Unable to write dialogue reveal diagnostics to {}", LOG_PATH, error);
                logFailureReported = true;
            }
        }
    }

    private static final class Snapshot {
        private final StringBuilder body = new StringBuilder();
        private final StringBuilder speaker = new StringBuilder();
        private boolean choices;
        private boolean typing;
        private boolean ready;
    }
}
