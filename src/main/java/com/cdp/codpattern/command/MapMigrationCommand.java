package com.cdp.codpattern.command;

import com.cdp.codpattern.config.storage.ServerMapStorage;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class MapMigrationCommand {
    private MapMigrationCommand() {}
    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("migrate").requires(source -> source.hasPermission(4))
                .then(Commands.literal("check").executes(context -> {
                    ServerMapStorage.get(context.getSource().getServer()).command(context.getSource(), false); return 1;
                }))
                .then(Commands.literal("confirm").executes(context -> {
                    ServerMapStorage.get(context.getSource().getServer()).command(context.getSource(), true); return 1;
                }));
    }
}
