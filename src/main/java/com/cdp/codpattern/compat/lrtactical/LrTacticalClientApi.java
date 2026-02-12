package com.cdp.codpattern.compat.lrtactical;

import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import me.xjqsh.lrtactical.api.LrTacticalAPI;
import me.xjqsh.lrtactical.api.item.IMeleeWeapon;
import me.xjqsh.lrtactical.api.item.IThrowable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public final class LrTacticalClientApi {
    private LrTacticalClientApi() {
    }

    @OnlyIn(Dist.CLIENT)
    public static Component getLrItemPackName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        ResourceLocation id = null;
        if (stack.getItem() instanceof IMeleeWeapon melee) {
            id = melee.getId(stack);
        } else if (stack.getItem() instanceof IThrowable throwable) {
            id = throwable.getId(stack);
        }
        if (id == null || IMeleeWeapon.EMPTY.equals(id) || IThrowable.EMPTY.equals(id)) {
            return null;
        }

        PackInfo packInfo = ClientAssetsManager.INSTANCE.getPackInfo(id);
        if (packInfo != null && packInfo.getName() != null) {
            return Component.translatable(packInfo.getName())
                    .withStyle(ChatFormatting.BLUE)
                    .withStyle(ChatFormatting.ITALIC);
        }

        if ("lrtactical".equals(id.getNamespace())) {
            return Component.translatable("pack.lrtactical.default_pack.name")
                    .withStyle(ChatFormatting.BLUE)
                    .withStyle(ChatFormatting.ITALIC);
        }
        return null;
    }

    public static NonNullList<ItemStack> fillLrItemCategory(boolean isMelee) {
        NonNullList<ItemStack> stacks = NonNullList.create();
        if (isMelee) {
            LrTacticalAPI.getMeleeIndexes().forEach(entry -> stacks.add(entry.createItemStack()));
        } else {
            LrTacticalAPI.getThrowableIndexes().forEach(entry -> stacks.add(entry.createItemStack()));
        }
        return stacks;
    }
}
