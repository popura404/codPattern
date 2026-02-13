package com.cdp.codpattern.compat.tacz.client;

import com.cdp.codpattern.compat.tacz.TaczGatewayProvider;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.PackInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public final class TaczClientApi {
    private TaczClientApi() {
    }

    public static boolean isGun(ItemStack stack) {
        return TaczGatewayProvider.gateway().isGun(stack);
    }

    public static List<ItemStack> fillGunItemCategory(String tabName) {
        GunTabType tabType = switch (tabName) {
            case "pistol" -> GunTabType.PISTOL;
            case "rifle" -> GunTabType.RIFLE;
            case "sniper" -> GunTabType.SNIPER;
            case "shotgun" -> GunTabType.SHOTGUN;
            case "smg" -> GunTabType.SMG;
            case "mg" -> GunTabType.MG;
            case "rpg" -> GunTabType.RPG;
            default -> null;
        };
        if (tabType == null) {
            return new ArrayList<>();
        }
        return AbstractGunItem.fillItemCategory(tabType);
    }

    public static ResourceLocation getGunHudTexture(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return null;
        }
        Optional<GunDisplayInstance> display = TimelessAPI.getGunDisplay(weapon);
        return display.map(GunDisplayInstance::getHUDTexture).orElse(null);
    }

    public static Component getGunPackName(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return null;
        }
        Optional<String> gunId = TaczGatewayProvider.gateway().resolveGunId(weapon);
        if (gunId.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(gunId.get());
        if (id == null) {
            return null;
        }
        PackInfo packInfo = ClientAssetsManager.INSTANCE.getPackInfo(id);
        if (packInfo == null || packInfo.getName() == null) {
            return null;
        }
        return Component.translatable(packInfo.getName())
                .withStyle(ChatFormatting.BLUE)
                .withStyle(ChatFormatting.ITALIC);
    }
}
