package com.WynnRunica.mixin;

import com.WynnRunica.HadesRelay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
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

    @ModifyArg(
            method = "tryCreateConnection",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/wynntils/hades/protocol/builders/HadesNetworkBuilder;"
                            + "setAddress(Ljava/net/InetAddress;I)"
                            + "Lcom/wynntils/hades/protocol/builders/HadesNetworkBuilder;"
            ),
            index = 1,
            require = 0
    )
    private int wynnrunica$relayHadesPort(int original) {
        return HadesRelay.port(original);
    }
}
