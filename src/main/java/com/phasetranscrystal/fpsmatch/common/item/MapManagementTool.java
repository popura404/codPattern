package com.phasetranscrystal.fpsmatch.common.item;

import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.cdp.codpattern.network.map.OpenMapManagementScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.item.tool.ToolAccessHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/** Common administration entry point; deliberately not a region-selection WorldToolItem. */
public final class MapManagementTool extends Item {
    public MapManagementTool(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        open(player);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        // Consume before a chest/door can handle the click, including permission denial.
        open(context.getPlayer());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    private void open(Player player) {
        if (player instanceof ServerPlayer serverPlayer && ToolAccessHelper.ensureAdminAccess(serverPlayer)) {
            ModNetworkChannel.sendToPlayer(new OpenMapManagementScreenS2CPacket(), serverPlayer);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.codpattern.map_management.scope"));
        tooltip.add(Component.translatable("tooltip.codpattern.map_management.use"));
    }
}
