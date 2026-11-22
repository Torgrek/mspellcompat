package com.misp.mspellcompat.client;

import com.misp.mspellcompat.util.MSpellCompatDebug;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public final class ClientConfigScreenRegistration {
    private ClientConfigScreenRegistration() {
    }

    public static void register(ModContainer modContainer) {
        boolean clothConfigLoaded = LoadingModList.get().getModFileById("cloth_config") != null;

        IConfigScreenFactory screenFactory;
        if (clothConfigLoaded) {
            screenFactory = (container, parent) -> MSpellCompatClothConfigScreen.create(parent);
        } else {

            screenFactory = ConfigurationScreen::new;
        }

        modContainer.registerExtensionPoint(IConfigScreenFactory.class, screenFactory);
        MSpellCompatDebug.log("client_config", "screen_registered", "clothConfigLoaded=" + clothConfigLoaded
                + ",factory=" + screenFactory.getClass().getName());
    }
}
