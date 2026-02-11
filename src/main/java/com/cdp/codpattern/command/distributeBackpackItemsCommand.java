package com.cdp.codpattern.command;

import com.cdp.codpattern.core.handler.Weaponhandling;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class distributeBackpackItemsCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("distribute")
                .requires(source -> source.hasPermission(2))
                .executes(commandcontext -> {
                    List<ServerPlayer> list = getAllOnlinePlayers();
                    for (ServerPlayer player : list) {
                        Weaponhandling.distributeBackpackItems(player);
                    }
                    return 1;
                })
                .then(Commands.argument("target", EntityArgument.players())
                        .executes(commandContext -> distributeGunsplayer(commandContext.getSource(),
                                EntityArgument.getPlayers(commandContext, "target"))));
    }

    private static List<ServerPlayer> getAllOnlinePlayers() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.getPlayerList().getPlayers();
        }
        return new ArrayList<>();
    }

    private static int distributeGunsplayer(CommandSourceStack pSource ,Collection<ServerPlayer> pTargets) {
        if (pTargets == null) {
            pSource.sendFailure(Component.literal("目标玩家为空"));
            return 0;
        }
        for (ServerPlayer serverplayer : pTargets) {
            Weaponhandling.distributeBackpackItems(serverplayer);
        }
        return 1;
    }
}
