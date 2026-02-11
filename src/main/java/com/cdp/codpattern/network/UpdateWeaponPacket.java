package com.cdp.codpattern.network;

import com.cdp.codpattern.config.BackPackConfig.BackpackConfig;
import com.cdp.codpattern.config.BackPackConfig.BackpackConfigManager;
import com.cdp.codpattern.config.WeaponFilterConfig.WeaponFilterConfig;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class UpdateWeaponPacket {
    private static final Set<String> ALLOWED_SLOTS = Set.of("primary", "secondary", "tactical", "lethal");
    private static final String THROWABLE_ITEM_ID = "lrtactical:throwable";

    private final int backpackId;
    private final String weaponSlot;
    private final String itemId;
    private final String nbt;

    private record ValidationResult(boolean success, String code, String message) {
        static ValidationResult ok() {
            return new ValidationResult(true, "OK", "");
        }

        static ValidationResult fail(String code, String message) {
            return new ValidationResult(false, code, message);
        }
    }

    public UpdateWeaponPacket(int backpackId, String weaponSlot, String itemId, String nbt) {
        this.backpackId = backpackId;
        this.weaponSlot = weaponSlot;
        this.itemId = itemId;
        this.nbt = nbt;
    }

    public static void encode(UpdateWeaponPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.backpackId);
        buffer.writeUtf(packet.weaponSlot);
        buffer.writeUtf(packet.itemId);
        buffer.writeUtf(packet.nbt);
    }

    public static UpdateWeaponPacket decode(FriendlyByteBuf buffer) {
        return new UpdateWeaponPacket(
                buffer.readInt(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf());
    }

    public static void handle(UpdateWeaponPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            BackpackConfig.PlayerBackpackData playerData = BackpackConfigManager.getConfig()
                    .getOrCreatePlayerData(player.getUUID().toString());
            BackpackConfig.Backpack backpack = playerData.getBackpacks_MAP().get(packet.backpackId);
            if (backpack == null) {
                sendFailureAndSync(player, playerData, packet.weaponSlot, "BAG_NOT_FOUND", "目标背包不存在");
                return;
            }

            ValidationResult slotResult = validateSlot(packet.weaponSlot);
            if (!slotResult.success()) {
                sendFailureAndSync(player, playerData, packet.weaponSlot, slotResult.code(), slotResult.message());
                return;
            }

            ValidationResult itemResult = validateItem(packet.itemId);
            if (!itemResult.success()) {
                sendFailureAndSync(player, playerData, packet.weaponSlot, itemResult.code(), itemResult.message());
                return;
            }

            CompoundTag parsedNbt;
            try {
                parsedNbt = parseNbt(packet.nbt);
            } catch (Exception e) {
                sendFailureAndSync(player, playerData, packet.weaponSlot, "NBT_INVALID", "NBT 解析失败");
                return;
            }

            ValidationResult categoryResult = validateCategory(packet.weaponSlot, packet.itemId, parsedNbt);
            if (!categoryResult.success()) {
                sendFailureAndSync(player, playerData, packet.weaponSlot, categoryResult.code(), categoryResult.message());
                return;
            }

            backpack.getItem_MAP().put(packet.weaponSlot,
                    new BackpackConfig.Backpack.ItemData(packet.itemId, 1, packet.nbt));
            BackpackConfigManager.save();
            com.cdp.codpattern.network.handler.PacketHandler.sendToPlayer(new SyncBackpackConfigPacket(playerData), player);
            com.cdp.codpattern.network.handler.PacketHandler.sendToPlayer(
                    new UpdateWeaponResultPacket(true, packet.weaponSlot, "OK", ""), player);
        });
        ctx.get().setPacketHandled(true);
    }

    private static ValidationResult validateSlot(String slot) {
        if (slot == null || !ALLOWED_SLOTS.contains(slot)) {
            return ValidationResult.fail("SLOT_INVALID", "非法槽位");
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validateItem(String itemId) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(itemId);
        if (resourceLocation == null) {
            return ValidationResult.fail("ITEM_ID_INVALID", "物品 ID 非法");
        }
        Item item = ForgeRegistries.ITEMS.getValue(resourceLocation);
        if (item == null) {
            return ValidationResult.fail("ITEM_NOT_REGISTERED", "物品未注册");
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validateCategory(String slot, String itemId, CompoundTag nbtTag) {
        WeaponFilterConfig filterConfig = WeaponFilterConfig.getWeaponFilterConfig();
        if (filterConfig == null) {
            filterConfig = new WeaponFilterConfig();
        }

        ResourceLocation itemRl = ResourceLocation.tryParse(itemId);
        Item item = itemRl == null ? null : ForgeRegistries.ITEMS.getValue(itemRl);
        if (item == null) {
            return ValidationResult.fail("ITEM_NOT_REGISTERED", "物品未注册");
        }

        if ("primary".equals(slot) || "secondary".equals(slot)) {
            ItemStack gunStack = new ItemStack(item, 1);
            if (nbtTag != null && !nbtTag.isEmpty()) {
                gunStack.setTag(nbtTag.copy());
            }
            IGun iGun = IGun.getIGunOrNull(gunStack);
            if (iGun == null) {
                return ValidationResult.fail("ITEM_CATEGORY_INVALID", "该槽位仅允许枪械");
            }
            ResourceLocation gunId = iGun.getGunId(gunStack);
            if (gunId == null || TimelessAPI.getCommonGunIndex(gunId).isEmpty()) {
                return ValidationResult.fail("ITEM_CATEGORY_INVALID", "枪械数据无效");
            }
            String gunType = TimelessAPI.getCommonGunIndex(gunId).get().getType();
            List<String> allowedTypes = "primary".equals(slot)
                    ? filterConfig.getPrimaryWeaponTabs()
                    : filterConfig.getSecondaryWeaponTabs();
            if (allowedTypes != null && !allowedTypes.isEmpty() && !allowedTypes.contains(gunType)) {
                return ValidationResult.fail("ITEM_CATEGORY_INVALID", "该武器分类不允许写入此槽位");
            }
            return ValidationResult.ok();
        }

        if (!filterConfig.isThrowablesEnabled()) {
            return ValidationResult.fail("THROWABLES_DISABLED", "当前配置禁用投掷物槽位");
        }
        if (!THROWABLE_ITEM_ID.equals(itemId)) {
            return ValidationResult.fail("ITEM_CATEGORY_INVALID", "投掷物槽位物品非法");
        }
        if (nbtTag == null || !nbtTag.contains("ThrowableId", Tag.TAG_STRING)) {
            return ValidationResult.fail("NBT_INVALID", "投掷物 NBT 缺少 ThrowableId");
        }
        return ValidationResult.ok();
    }

    private static CompoundTag parseNbt(String rawNbt) throws Exception {
        if (rawNbt == null || rawNbt.isBlank()) {
            return new CompoundTag();
        }
        return TagParser.parseTag(rawNbt);
    }

    private static void sendFailureAndSync(ServerPlayer player, BackpackConfig.PlayerBackpackData playerData, String slot,
            String code, String message) {
        com.cdp.codpattern.network.handler.PacketHandler.sendToPlayer(new SyncBackpackConfigPacket(playerData), player);
        com.cdp.codpattern.network.handler.PacketHandler.sendToPlayer(
                new UpdateWeaponResultPacket(false, slot, code, message), player);
    }
}
