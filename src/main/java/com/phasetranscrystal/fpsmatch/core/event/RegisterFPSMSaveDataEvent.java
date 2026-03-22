package com.phasetranscrystal.fpsmatch.core.event;

import com.phasetranscrystal.fpsmatch.core.data.save.FPSMDataManager;
import com.phasetranscrystal.fpsmatch.core.data.save.SaveHolder;
import net.minecraftforge.eventbus.api.Event;

public class RegisterFPSMSaveDataEvent extends Event {
    private final FPSMDataManager dataManager;

    public RegisterFPSMSaveDataEvent(FPSMDataManager dataManager) {
        this.dataManager = dataManager;
    }

    public <T> void registerData(Class<T> clazz, String folderName, SaveHolder<T> saveHolder) {
        dataManager.registerData(clazz, folderName, saveHolder);
    }
}
