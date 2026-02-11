package com.cdp.codpattern.command;

import com.cdp.codpattern.network.OpenBackpackScreenPacket;
import com.cdp.codpattern.network.handler.PacketHandler;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 打开背包界面，用于debug
 */
public class MainMenuScreenCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("screen")
                .executes(context -> {
                    if (context.getSource().getEntity() instanceof ServerPlayer player) {
                        PacketHandler.sendToPlayer(new OpenBackpackScreenPacket(), player);
                        return 1;
                    }
                    context.getSource().sendFailure(Component.literal("服务端禁用"));
                    return 0;
                });
    }
}
