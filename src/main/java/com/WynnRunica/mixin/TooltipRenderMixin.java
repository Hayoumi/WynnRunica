package com.WynnRunica.mixin;

import com.WynnRunica.GuiTranslator;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(DrawContext.class)
public abstract class TooltipRenderMixin {
    @ModifyVariable(
            method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/util/Identifier;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private List<Text> wynnrunica$translateRenderedPixelTooltip(List<Text> tooltip) {
        return GuiTranslator.translatePixelTooltip(tooltip);
    }
}
