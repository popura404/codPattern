package com.cdp.codpattern.command;

import com.cdp.codpattern.adapter.forge.command.DistributeBackpackItemsCommand;
import com.cdp.codpattern.adapter.forge.command.UpdateWeaponFilterConfigCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class CommandRegistration {
    private CommandRegistration() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("cdp")
                .then(Commands.literal("test")
                        .executes(context -> {
                            Player player = context.getSource().getPlayer();
                            if (player != null) {
                                Component message = Component.translatable("command.codpattern.test.output");
                                player.sendSystemMessage(message);
                            }
                            return 1;
                        }))
                .then(MainMenuScreenCommand.buildCommand())
                .then(UpdateWeaponFilterConfigCommand.buildCommand())
                .then(DistributeBackpackItemsCommand.buildCommand());
        dispatcher.register(root);
    }
}
