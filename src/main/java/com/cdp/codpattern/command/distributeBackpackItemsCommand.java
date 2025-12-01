package com.cdp.codpattern.command;

import com.cdp.codpattern.core.handler.Weaponhandling;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mod.EventBusSubscriber(modid = "codpattern", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class distributeBackpackItemsCommand {

    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event){
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("cdp")
                        .then(Commands.literal("distribute")
                                .requires(source -> source.hasPermission(2))
                                .executes(commandcontext -> {
                                    List<ServerPlayer> list = getAllOnlinePlayers();
                                    try {
                                        for (ServerPlayer player : list){
                                            Weaponhandling.distributeBackpackItems(player);
                                        }
                                    } catch (Exception ignored) {
                                    }
                                   return 1;
                                })
                        .then(Commands.argument("target" , EntityArgument.players())
                                .requires(source -> source.hasPermission(2))
                                .executes( commandContext -> distributeGunsplayer(commandContext.getSource(), EntityArgument.getPlayers(commandContext , "target"))
                                )
                        ))
        );
    }

    private static List<ServerPlayer> getAllOnlinePlayers() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.getPlayerList().getPlayers();
        }
        return new ArrayList<>();
    }

    private static int distributeGunsplayer(CommandSourceStack pSource ,Collection<ServerPlayer> pTargets) {
        //我不知道为什么要写这个if，但是不写，他就运行不了
        if(pTargets == null) pSource.sendFailure(Component.literal("何意味"));
        for(ServerPlayer serverplayer : pTargets){
            Weaponhandling.distributeBackpackItems(serverplayer);
        }
        return 1;
    }
}
