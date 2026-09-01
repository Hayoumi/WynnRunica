package com.WynnRunica;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.net.URI;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class WynnRunicaClient implements ClientModInitializer {

    public static boolean enabled = true;
    private static KeyBinding toggleKey;
    private static KeyBinding reloadKey;
    private static boolean toggleKeyWasDown = false;
    private static boolean reloadKeyWasDown = false;

    private static final KeyBinding.Category WR_CATEGORY =
            KeyBinding.Category.create(Identifier.of("wynnrunica"));


    @Override
    public void onInitializeClient() {

        TranslationUpdater.update();
        TranslationPrinter.reload();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Включить / выключить перевод",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                WR_CATEGORY
        ));

        reloadKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Обновить перевод",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F9,
                WR_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            long windowHandle = client.getWindow().getHandle();

            int toggle = InputUtil.fromTranslationKey(toggleKey.getBoundKeyTranslationKey()).getCode();
            boolean isDown = GLFW.glfwGetKey(windowHandle, toggle) == GLFW.GLFW_PRESS;

            if (isDown && !toggleKeyWasDown) {
                enabled = !enabled;
                String status = enabled ? "§aвключён" : "§cвыключен";
                if (client.player != null) {
                    com.WynnRunica.GuiTranslator.refreshOpenScreen();
                    client.inGameHud.getChatHud().addMessage(
                            Text.literal("[§3Wynn§fRunica] Перевод " + status)
                    );
                }
            }
            toggleKeyWasDown = isDown;
            int reload = InputUtil.fromTranslationKey(reloadKey.getBoundKeyTranslationKey()).getCode();
            boolean reloadDown = GLFW.glfwGetKey(windowHandle, reload) == GLFW.GLFW_PRESS;

            if (reloadDown && !reloadKeyWasDown) {
                if (client.player != null) {
                    TranslationPrinter.reload();
                    com.WynnRunica.GuiTranslator.refreshOpenScreen();
                    client.inGameHud.getChatHud().addMessage(
                            Text.literal("[§3Wynn§fRunica] Перевод §lобновлён")
                    );
                }
            }
            reloadKeyWasDown = reloadDown;

            DialogueInstantReveal.tick(client);
        });


        new Thread(VersionChecker::versionCheck).start();
        AthenaRelay.init();
        HadesRelay.init();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            DialogueInstantReveal.reset();
            if (VersionChecker.hasUpdate && client.player != null) {
                Text msg = Text.literal("[§3Wynn§fRunica] §eДоступна новая версия §a" + VersionChecker.latestVersion + "§e! §bСкачать (кликабельно): ")
                        .append(Text.literal("§8(GitHub) §e| ")
                                .styled(style -> style.withClickEvent(
                                        new ClickEvent.OpenUrl(URI.create(VersionChecker.latestGitUrl))
                                )))
                        .append(Text.literal("§a(Modrinth)")
                                .styled(style -> style.withClickEvent(
                                        new ClickEvent.OpenUrl(URI.create(VersionChecker.latestModrinthUrl))
                                )));

                client.inGameHud.getChatHud().addMessage(msg);


            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> DialogueInstantReveal.reset());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("runicauntranslated")
                        .then(literal("on").executes(ctx -> setUntranslated(ctx.getSource(), true)))
                        .then(literal("off").executes(ctx -> setUntranslated(ctx.getSource(), false)))
                        .then(literal("toggle").executes(ctx -> setUntranslated(ctx.getSource(), !UntranslatedLogger.ENABLED)))
                        .executes(ctx -> {
                            String status = UntranslatedLogger.ENABLED ? "§aвключён" : "§cвыключен";
                            ctx.getSource().sendFeedback(Text.literal(
                                    "[§3Wynn§fRunica] Лог непереведённого " + status
                                            + " §8/runicauntranslated on | off | toggle"));
                            return 1;
                        })));
    }

    private static int setUntranslated(FabricClientCommandSource source, boolean on) {
        UntranslatedLogger.setEnabled(on);
        String status = on ? "§aвключён" : "§cвыключен";
        source.sendFeedback(Text.literal(
                "[§3Wynn§fRunica] Лог непереведённого " + status));
        return 1;
    }
}
