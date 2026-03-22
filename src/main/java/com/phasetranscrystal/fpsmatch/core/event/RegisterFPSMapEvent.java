package com.phasetranscrystal.fpsmatch.core.event;

import com.mojang.datafixers.util.Function3;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

public class RegisterFPSMapEvent extends Event {
    private final FPSMCore fpsmCore;

    public RegisterFPSMapEvent(FPSMCore fpsmCore) {
        this.fpsmCore = fpsmCore;
    }

    public void registerGameType(String typeName, Function3<ServerLevel, String, AreaData, BaseMap> mapFactory) {
        fpsmCore.registerGameType(typeName, mapFactory);
    }
}
