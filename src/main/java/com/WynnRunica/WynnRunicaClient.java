package com.WynnRunica;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.WynnRunica.gui.GuiScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.List;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class WynnRunicaClient implements ClientModInitializer {

    private static KeyBinding toggleKey;
    private static KeyBinding reloadKey;
    private static KeyBinding openGuiKey;
    private static boolean toggleKeyWasDown = false;
    private static boolean reloadKeyWasDown = false;
    private static boolean openGuiKeyWasDown = false;
    private static int pendingGuiRefreshTicks = 0;

    private static final KeyBinding.Category WR_CATEGORY =
            KeyBinding.Category.create(Identifier.of("wynnrunica"));

    public static List<KeyBinding> keyBindings() {
        return List.of(toggleKey, reloadKey, openGuiKey);
    }

    public static Text translationToggleKey() {
        return toggleKey == null ? Text.literal("F8") : toggleKey.getBoundKeyLocalizedText();
    }


    @Override
    public void onInitializeClient() {

        HadesRelay.removeOldFiles();
        TranslationManager.reload();
        Thread updater = new Thread(() -> {
            if (TranslationUpdater.update()) {
                MinecraftClient.getInstance().execute(() -> {
                    TranslationManager.reload();
                    GuiTranslator.refreshOpenScreen();
                });
            }
        }, "WynnRunica translations");
        updater.setDaemon(true);
        updater.start();
        Config.loadConfig();
        ClientReceiveMessageEvents.MODIFY_GAME.register(ServerNotificationTranslator::receive);

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

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Открыть меню",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                WR_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            ChoiceTicker.clientTick(client);

            if (pendingGuiRefreshTicks > 0) {
                pendingGuiRefreshTicks--;
                GuiTranslator.refreshOpenScreen();
            }

            if (client.player != null && Config.isEnabled("Отправка строк")) {
                QuestTracker.QuestInfo trackedQuest = QuestTracker.detect();
                if (!trackedQuest.objective().isBlank()) {
                    TelemetrySender.recordObjective(trackedQuest.objective(), trackedQuest.name(),
                            trackedQuest.stage(), null);
                }
            }

            long windowHandle = client.getWindow().getHandle();

            int toggle = InputUtil.fromTranslationKey(toggleKey.getBoundKeyTranslationKey()).getCode();
            boolean isDown = GLFW.glfwGetKey(windowHandle, toggle) == GLFW.GLFW_PRESS;

            if (isDown && !toggleKeyWasDown) {
                boolean enabled = !Config.isTranslationOn();

                Config.setTranslationEnabled(enabled);
                Config.saveConfig();
                TranslationManager.refreshNpcs();
                com.WynnRunica.GuiTranslator.refreshOpenScreen();

                String status = enabled ? "§aвключён" : "§cвыключен";
                if (client.player != null) {
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
                    int brokenFiles = TranslationManager.reload();
                    com.WynnRunica.GuiTranslator.refreshOpenScreen();
                    String message = "[§3Wynn§fRunica] Перевод §lобновлён";
                    if (brokenFiles > 0) message += "§r§c, файлов с ошибками: " + brokenFiles + " (подробности в логе)";
                    client.inGameHud.getChatHud().addMessage(Text.literal(message));
                }
            }
            reloadKeyWasDown = reloadDown;

            int openGui = InputUtil.fromTranslationKey(openGuiKey.getBoundKeyTranslationKey()).getCode();
            boolean openGuiDown = GLFW.glfwGetKey(windowHandle, openGui) == GLFW.GLFW_PRESS;

            if (openGuiDown && !openGuiKeyWasDown) {
                if (client.currentScreen instanceof GuiScreen) {
                    client.setScreen(null);
                } else if (client.currentScreen == null) {
                    client.setScreen(new GuiScreen(null));
                }
            }
            openGuiKeyWasDown = openGuiDown;

            DialogueInstantReveal.tick(client);
        });


        new Thread(VersionChecker::versionCheck).start();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            DialogueInstantReveal.reset();
            pendingGuiRefreshTicks = 20;
            ChatManager.join(client);
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

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            DialogueInstantReveal.reset();
            pendingGuiRefreshTicks = 0;
            TelemetrySender.flush();
            ChatManager.reset();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> TelemetrySender.shutdown());

        ChatManager.init();

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.startsWith("!") && ChatManager.available()) {
                if (Config.isEnabled("Общий чат")) {
                    String text = message.startsWith("! ") ? message.substring(2) : message.substring(1);
                    ChatManager.sendMessage(text);
                } else {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null) {
                        client.inGameHud.getChatHud().addMessage(
                                Text.literal("[§3Wynn§fRunica] §cОбщий чат отключён в настройках.")
                        );
                    }
                }
                return false;
            }
            return true;
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            Command<FabricClientCommandSource> chatCommand = ctx -> {
                String message = StringArgumentType.getString(ctx, "message");
                if (!ChatManager.available()) {
                    ctx.getSource().sendFeedback(
                            Text.literal("[§3Wynn§fRunica] §cОбщий чат работает только на Wynncraft.")
                    );
                } else if (Config.isEnabled("Общий чат")) {
                    ChatManager.sendMessage(message);
                } else {
                    ctx.getSource().sendFeedback(
                            Text.literal("[§3Wynn§fRunica] §cОбщий чат отключён в настройках.")
                    );
                }
                return 1;
            };

            dispatcher.register(literal("wr")
                    .then(argument("message", StringArgumentType.greedyString())
                            .executes(chatCommand)));

            dispatcher.register(literal("цк")
                    .then(argument("message", StringArgumentType.greedyString())
                            .executes(chatCommand)));
        });
    }
}
