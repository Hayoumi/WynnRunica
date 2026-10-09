package com.WynnRunica.mixin;

import com.WynnRunica.GuiTranslationCache;
import com.WynnRunica.GuiTranslator;
import com.WynnRunica.Config;
import com.WynnRunica.TooltipCaptureLogger;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(HandledScreen.class)
public abstract class TooltipCaptureMixin {
    private static final ThreadLocal<Boolean> wynnrunica$readingOriginal =
            ThreadLocal.withInitial(() -> false);

    @Shadow
    protected abstract List<Text> getTooltipFromItem(ItemStack stack);

    @ModifyVariable(method = "getTooltipFromItem", at = @At("HEAD"), argsOnly = true)
    private ItemStack wynnrunica$showTranslatedCopy(ItemStack stack) {
        if (wynnrunica$readingOriginal.get()) return stack;
        return GuiTranslationCache.shownFor(stack);
    }

    @Inject(method = "getTooltipFromItem", at = @At("RETURN"), cancellable = true)
    private void wynnrunica$layoutAndCapture(ItemStack stack, CallbackInfoReturnable<List<Text>> callback) {
        if (wynnrunica$readingOriginal.get()) return;

        ItemStack original = GuiTranslationCache.originalOf(stack);
        if (original == null) {
            if (Config.isEnabled("Отправка строк")) {
                TooltipCaptureLogger.capture(stack, callback.getReturnValue(), ((Screen) (Object) this).getTitle());
            }
            return;
        }

        List<Text> english;
        wynnrunica$readingOriginal.set(true);
        try {
            english = getTooltipFromItem(original);
        } finally {
            wynnrunica$readingOriginal.set(false);
        }
        if (Config.isEnabled("Отправка строк")) {
            TooltipCaptureLogger.capture(original, english, callback.getReturnValue(), ((Screen) (Object) this).getTitle());
        }
        if (english.size() == callback.getReturnValue().size()) {
            callback.setReturnValue(GuiTranslator.widenColumns(english, callback.getReturnValue()));
        }
    }
}
