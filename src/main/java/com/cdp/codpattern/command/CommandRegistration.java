package com.cdp.codpattern.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;



public class CommandRegistration {

    @SubscribeEvent
    public void onCommandRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("mymod")
                        .then(Commands.literal("web")
                                .executes(context -> {
                                    Player player = context.getSource().getPlayer();
                                    if (player != null) {
                                        Component message = Component.literal("访问我们的网站");

                                        player.sendSystemMessage(message);
                                    }
                                    return 1;
                                })
                        )
        );
    }
}
