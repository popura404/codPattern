package com.cdp.codpattern.compat.fpsmatch.event;

import com.cdp.codpattern.CodPattern;
import com.cdp.codpattern.app.match.model.ModeObjectInteractionContext;
import com.cdp.codpattern.common.block.ZombiesBoxInteractionBlock;
import com.cdp.codpattern.compat.fpsmatch.FpsMatchGateway;
import com.cdp.codpattern.compat.fpsmatch.FpsMatchGatewayProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CodPattern.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModeObjectInteractionEventHandler {
    private ModeObjectInteractionEventHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isBlockHandledByOwnUse(event)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        FpsMatchGateway gateway = FpsMatchGatewayProvider.gateway();
        gateway.findPlayerInteractableObjectPort(player).ifPresent(port -> applyResult(event, port.interact(
                player,
                new ModeObjectInteractionContext(
                        port.roomId(),
                        event.getHand(),
                        event.getPos(),
                        event.getFace(),
                        null,
                        heldItem(player, event)))));
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        FpsMatchGateway gateway = FpsMatchGatewayProvider.gateway();
        gateway.findPlayerInteractableObjectPort(player).ifPresent(port -> applyResult(event, port.interact(
                player,
                new ModeObjectInteractionContext(
                        port.roomId(),
                        event.getHand(),
                        null,
                        null,
                        null,
                        heldItem(player, event)))));
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        FpsMatchGateway gateway = FpsMatchGatewayProvider.gateway();
        gateway.findPlayerInteractableObjectPort(player).ifPresent(port -> applyResult(event, port.interact(
                player,
                new ModeObjectInteractionContext(
                        port.roomId(),
                        event.getHand(),
                        null,
                        null,
                        event.getTarget(),
                        heldItem(player, event)))));
    }

    private static ItemStack heldItem(ServerPlayer player, PlayerInteractEvent event) {
        return event.getHand() == null ? ItemStack.EMPTY : player.getItemInHand(event.getHand()).copy();
    }

    private static boolean isBlockHandledByOwnUse(PlayerInteractEvent.RightClickBlock event) {
        return event.getLevel().getBlockState(event.getPos()).getBlock() instanceof ZombiesBoxInteractionBlock;
    }

    private static void applyResult(PlayerInteractEvent event, InteractionResult result) {
        if (result == null || result == InteractionResult.PASS) {
            return;
        }
        event.setCancellationResult(result);
        event.setCanceled(true);
    }
}
