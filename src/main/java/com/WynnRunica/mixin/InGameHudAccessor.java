package com.WynnRunica.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(InGameHud.class)
public interface InGameHudAccessor {

    @Accessor("overlayMessage")
    Text getOverlayMessage();

    @Accessor("overlayMessage")
    void setOverlayMessage(Text message);

    @Accessor("overlayRemaining")
    int getOverlayRemaining();

    @Accessor("overlayRemaining")
    void setOverlayRemaining(int overlayRemaining);
}
