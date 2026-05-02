package com.misp.mspellcompat.client;

import com.misp.mspellcompat.MSpellCompat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(value = MSpellCompat.MOD_ID, dist = Dist.CLIENT)
public final class MSpellCompatClient {
    public MSpellCompatClient(ModContainer modContainer) {
        ClientConfigScreenRegistration.register(modContainer);
    }
}
