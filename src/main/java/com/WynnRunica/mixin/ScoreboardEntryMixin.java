package com.WynnRunica.mixin;

import com.WynnRunica.Config;
import com.WynnRunica.ObjectiveTranslator;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScoreboardEntry.class)
public abstract class ScoreboardEntryMixin {

    @Inject(method = "name", at = @At("RETURN"), cancellable = true)
    private void wynnrunica$translateScoreboardEntryName(CallbackInfoReturnable<Text> cir) {
        if (!Config.isTranslationEnabled() || !Config.isEnabled("Задачи квестов")) return;

        Text original = cir.getReturnValue();
        if (original == null) return;

        MutableText translated = ObjectiveTranslator.translateScoreboardLine(original.copy());
        if (translated != null && !translated.getString().equals(original.getString())) {
            cir.setReturnValue(translated);
        }
    }
}
