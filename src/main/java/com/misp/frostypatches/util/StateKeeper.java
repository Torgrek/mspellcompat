package com.misp.frostypatches.util;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

public final class StateKeeper {

    public static final Logger LOGGER = LogUtils.getLogger();

    public static boolean isItNotHardcore(Minecraft minecraft){
        return minecraft.level != null && !minecraft.level.getLevelData().isHardcore();
    }

}
