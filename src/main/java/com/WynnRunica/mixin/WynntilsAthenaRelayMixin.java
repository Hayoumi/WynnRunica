package com.WynnRunica.mixin;

import com.WynnRunica.AthenaRelay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.net.URI;

@Pseudo
@Mixin(targets = "com.wynntils.core.net.NetManager", remap = false)
public abstract class WynntilsAthenaRelayMixin {
    @ModifyVariable(
            method = {
                    "createGetRequest(Ljava/net/URI;Ljava/util/Map;)Ljava/net/http/HttpRequest;",
                    "createPostRequest(Ljava/net/URI;Ljava/util/Map;Lcom/google/gson/JsonObject;)Ljava/net/http/HttpRequest;"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private URI wynnrunica$relayAthena(URI uri) {
        String original = uri.toString();
        String rewritten = AthenaRelay.rewrite(original);
        return rewritten.equals(original) ? uri : URI.create(rewritten);
    }
}
