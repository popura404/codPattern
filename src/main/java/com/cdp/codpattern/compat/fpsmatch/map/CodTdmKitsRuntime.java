package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.service.KitDistributionService;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final record CodTdmKitsRuntime(
        CodTdmMapSetupState mapSetupState,
        Supplier<Iterable<BaseTeam>> teamsSupplier
) {

    ArrayList<ItemStack> getOrCreateKits(String teamName) {
        return mapSetupState.getOrCreateKits(teamName);
    }

    void addKit(String teamName, ItemStack itemStack) {
        mapSetupState.addKit(teamName, itemStack);
    }

    void setStartKits(Map<String, ArrayList<ItemStack>> kits) {
        mapSetupState.setStartKits(kits);
    }

    void setAllTeamKits(ItemStack itemStack) {
        for (BaseTeam team : teamsSupplier.get()) {
            addKit(team.name, itemStack);
        }
    }

    void givePlayerKits(ServerPlayer player) {
        KitDistributionService.distributePlayerKits(player);
    }

    Map<String, List<ItemStack>> startKitsSnapshot() {
        return mapSetupState.startKitsSnapshot();
    }
}
