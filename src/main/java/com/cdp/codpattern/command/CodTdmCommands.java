package com.cdp.codpattern.command;

import com.cdp.codpattern.compat.fpsmatch.FpsMatchGatewayProvider;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * @deprecated 此独立命令已弃用。
 *             请使用 {@link TdmFpsmCommandHandler} 提供的 /fpsm tdm 命令。
 *             例如: /fpsm tdm create &lt;mapName&gt;
 */
@Deprecated
public class CodTdmCommands {

    /**
     * @deprecated 使用 /fpsm tdm 命令代替
     */
    @Deprecated
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("codtdm")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("mapName", StringArgumentType.word())
                                .executes(context -> {
                                    String mapName = StringArgumentType.getString(context, "mapName");
                                    CommandSourceStack source = context.getSource();
                                    ServerLevel level = source.getLevel();

                                    net.minecraft.core.BlockPos pos = source.getPlayerOrException().blockPosition();
                                    FpsMatchGatewayProvider.gateway().createAndRegisterMap(
                                            level,
                                            mapName,
                                            pos,
                                            pos.offset(10, 10, 10));

                                    source.sendSuccess(
                                            () -> Component.translatable("command.codpattern.codtdm.deprecated_created", mapName),
                                            true);
                                    return 1;
                                }))));
    }
}
