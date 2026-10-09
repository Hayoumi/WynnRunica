package com.WynnRunica.mixin;

import com.WynnRunica.HadesRelay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Pseudo
@Mixin(targets = "com.wynntils.services.hades.HadesService", remap = false)
public abstract class WynntilsHadesRelayMixin {
    @Redirect(
            method = "tryCreateConnection",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/net/InetAddress;getByName(Ljava/lang/String;)Ljava/net/InetAddress;"
            ),
            require = 0
    )
    private InetAddress wynnrunica$relayHadesHost(String original) throws UnknownHostException {
        return InetAddress.getByName(HadesRelay.host(original));
    }
}
