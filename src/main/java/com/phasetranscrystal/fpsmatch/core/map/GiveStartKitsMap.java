package com.phasetranscrystal.fpsmatch.core.map;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface GiveStartKitsMap<T> {
    ArrayList<ItemStack> getKits(BaseTeam team);

    void addKits(BaseTeam team, ItemStack itemStack);

    void setStartKits(Map<String, ArrayList<ItemStack>> kits);

    void setAllTeamKits(ItemStack itemStack);

    Map<String, List<ItemStack>> getStartKits();

    T getMap();
}
