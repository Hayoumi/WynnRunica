package com.WynnRunica.mixin;

import com.WynnRunica.GuiTranslationCache;
import com.WynnRunica.GuiTranslator;
import com.WynnRunica.WynnRunicaClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class GuiTranslationMixin {

    @Inject(method = "onInventory", at = @At("HEAD"))
    private void onInventory(InventoryS2CPacket packet, CallbackInfo ci) {

        GuiTranslationCache.resetIfSyncIdChanged(packet.syncId());

        for (ItemStack stack : packet.contents()) {

            if (stack.isEmpty()) continue;
            GuiTranslationCache.rememberOriginal(stack);
            if (WynnRunicaClient.enabled) GuiTranslator.translateStack(stack);
        }
    }

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("HEAD"))
    private void onScreenHandlerSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {

        GuiTranslationCache.resetIfSyncIdChanged(packet.getSyncId());
        ItemStack stack = packet.getStack();

        if (stack.isEmpty()) return;
        GuiTranslationCache.rememberOriginal(stack);
        if (WynnRunicaClient.enabled) GuiTranslator.translateStack(stack);
    }
}
