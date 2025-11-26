package com.cdp.codpattern.command;

import com.cdp.codpattern.core.handler.Weaponhandling;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
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
                                .executes(context -> {
                                    List<ServerPlayer> list = getAllOnlinePlayers();
                                    try {
                                        for (ServerPlayer player : list){
                                            Weaponhandling.distributeBackpackItems(player);
                                        }
                                    } catch (Exception ignored) {
                                    }
                                   return 1;
                                })
                        )
        );
    }

    public static List<ServerPlayer> getAllOnlinePlayers() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.getPlayerList().getPlayers();
        }
        return new ArrayList<>();
    }
}
