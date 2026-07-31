package com.WynnRunica.mixin;

import com.WynnRunica.GuiTranslationCache;
import com.WynnRunica.TooltipCaptureLogger;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(HandledScreen.class)
public abstract class TooltipCaptureMixin {
    private static final ThreadLocal<Boolean> wynnrunica$readingOriginal =
            ThreadLocal.withInitial(() -> false);

    @Shadow
    protected abstract List<Text> getTooltipFromItem(ItemStack stack);

    @Inject(method = "getTooltipFromItem", at = @At("RETURN"))
    private void wynnrunica$capturePixelTooltip(ItemStack stack,
                                                 CallbackInfoReturnable<List<Text>> callback) {
        if (wynnrunica$readingOriginal.get()) return;

        ItemStack original = GuiTranslationCache.originals.get(
                GuiTranslationCache.keyFor(stack));
        if (original == null) {
            TooltipCaptureLogger.capture(stack, callback.getReturnValue(),
                    ((Screen) (Object) this).getTitle());
            return;
        }

        wynnrunica$readingOriginal.set(true);
        try {
            TooltipCaptureLogger.capture(original, getTooltipFromItem(original),
                    ((Screen) (Object) this).getTitle());
        } finally {
            wynnrunica$readingOriginal.set(false);
        }
    }
}
