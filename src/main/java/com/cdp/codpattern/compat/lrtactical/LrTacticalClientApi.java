package com.cdp.codpattern.compat.lrtactical;

import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public final class LrTacticalClientApi {
    private LrTacticalClientApi() {
    }

    @OnlyIn(Dist.CLIENT)
    public static Component getLrItemPackName(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !LrTacticalGatewayProvider.gateway().isLoaded()) {
            return null;
        }
        return LrTacticalLoadedClientApi.getLrItemPackName(stack);
    }

    public static NonNullList<ItemStack> fillLrItemCategory(boolean isMelee) {
        if (!LrTacticalGatewayProvider.gateway().isLoaded()) {
            return NonNullList.create();
        }
        return isMelee
                ? LrTacticalGatewayProvider.gateway().fillMeleeCategory()
                : LrTacticalGatewayProvider.gateway().fillThrowableCategory();
    }
}
