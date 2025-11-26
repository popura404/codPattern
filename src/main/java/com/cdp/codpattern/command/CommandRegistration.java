package com.cdp.codpattern.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "codpattern", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommandRegistration {

    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("cdp")
                        .then(Commands.literal("test")
                                .executes(context -> {
                                    Player player = context.getSource().getPlayer();
                                    if (player != null) {
                                        Component message = Component.literal(">>\n");
                                        player.sendSystemMessage(message);
                                    }
                                    return 1;
                                })
                        )
        );
    }
}
