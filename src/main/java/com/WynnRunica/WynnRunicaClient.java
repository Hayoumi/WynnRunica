package com.WynnRunica;

import net.fabricmc.api.ClientModInitializer;

public class WynnRunicaClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        TranslationPrinter.loadTraslations();
    }
}
