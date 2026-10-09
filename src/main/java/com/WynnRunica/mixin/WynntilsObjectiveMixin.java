package com.WynnRunica.mixin;

import com.WynnRunica.Config;
import com.WynnRunica.ObjectiveTranslator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.wynntils.models.objectives.WynnObjective", remap = false)
public abstract class WynntilsObjectiveMixin {

    @Inject(method = "asObjectiveString()Ljava/lang/String;", at = @At("RETURN"), cancellable = true, require = 0)
    private void wynnrunica$translateObjective(CallbackInfoReturnable<String> cir) {
        if (!Config.isTranslationEnabled() || !Config.isEnabled("Задачи квестов")) return;

        String original = cir.getReturnValue();
        String translated = ObjectiveTranslator.translateWynntilsObjective(original);
        if (translated != null && !translated.equals(original)) cir.setReturnValue(translated);
    }
}
