package com.cdp.codpattern.common.block;

import com.cdp.codpattern.app.match.model.ModeObjectInteractionContext;
import com.cdp.codpattern.compat.fpsmatch.FpsMatchGatewayProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class ZombiesBoxInteractionBlock extends Block {
    public ZombiesBoxInteractionBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (level.isClientSide) {
            return hand == InteractionHand.MAIN_HAND ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        return FpsMatchGatewayProvider.gateway()
                .findPlayerInteractableObjectPort(serverPlayer)
                .map(port -> port.interact(
                        serverPlayer,
                        new ModeObjectInteractionContext(
                                port.roomId(),
                                hand,
                                pos,
                                face(hit),
                                null,
                                heldItem(player, hand))))
                .map(result -> result == null ? InteractionResult.PASS : result)
                .orElse(InteractionResult.PASS);
    }

    private static Direction face(BlockHitResult hit) {
        return hit == null ? null : hit.getDirection();
    }

    private static ItemStack heldItem(Player player, InteractionHand hand) {
        return hand == null ? ItemStack.EMPTY : player.getItemInHand(hand).copy();
    }
}
