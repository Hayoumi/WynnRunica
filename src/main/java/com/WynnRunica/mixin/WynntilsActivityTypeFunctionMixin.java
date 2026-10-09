package com.WynnRunica.mixin;

import com.WynnRunica.Config;
import com.WynnRunica.ObjectiveTranslator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.wynntils.functions.ActivityFunctions$ActivityTypeFunction", remap = false)
public abstract class WynntilsActivityTypeFunctionMixin {

    @Inject(
            method = "getValue(Lcom/wynntils/core/consumers/functions/arguments/FunctionArguments;)Ljava/lang/String;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void wynnrunica$translateActivityType(@Coerce Object args, CallbackInfoReturnable<String> cir) {
        if (!Config.isTranslationEnabled() || !Config.isEnabled("Задачи квестов")) return;

        String original = cir.getReturnValue();
        if (original == null || original.isEmpty()) return;

        String translated = ObjectiveTranslator.translateActivityType(original);
        if (translated != null && !translated.equals(original)) {
            cir.setReturnValue(translated);
        }
    }
}
