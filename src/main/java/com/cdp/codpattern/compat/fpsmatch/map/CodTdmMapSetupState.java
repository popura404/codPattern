package com.cdp.codpattern.compat.fpsmatch.map;

import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CodTdmMapSetupState {
    private Map<String, ArrayList<ItemStack>> startKits = new HashMap<>();
    private SpawnPointData matchEndTeleportPoint;

    ArrayList<ItemStack> getOrCreateKits(String teamName) {
        return startKits.computeIfAbsent(teamName, ignored -> new ArrayList<>());
    }

    void addKit(String teamName, ItemStack itemStack) {
        getOrCreateKits(teamName).add(itemStack);
    }

    void setStartKits(Map<String, ArrayList<ItemStack>> kits) {
        this.startKits = new HashMap<>(kits);
    }

    Map<String, List<ItemStack>> startKitsSnapshot() {
        return new HashMap<>(startKits);
    }

    boolean hasMatchEndTeleportPoint() {
        return matchEndTeleportPoint != null;
    }

    SpawnPointData getMatchEndTeleportPoint() {
        return matchEndTeleportPoint;
    }

    void setMatchEndTeleportPoint(SpawnPointData matchEndTeleportPoint) {
        this.matchEndTeleportPoint = matchEndTeleportPoint;
    }
}
