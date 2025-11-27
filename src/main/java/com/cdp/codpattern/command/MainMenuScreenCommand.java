package com.cdp.codpattern.command;

import com.cdp.codpattern.network.OpenBackpackScreenPacket;
import com.cdp.codpattern.network.handler.PacketHandler;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 打开背包界面，用于debug
 */
@Mod.EventBusSubscriber(modid = "codpattern", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MainMenuScreenCommand {
    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("cdp")
                        .then(Commands.literal("screen")
                                .executes(context -> {
                                    if (context.getSource().getEntity() instanceof ServerPlayer player) {
                                        // 发送网络包给C打开GUI
                                        PacketHandler.sendToPlayer(new OpenBackpackScreenPacket(), player);
                                        return 1;
                                    } else {
                                        context.getSource().sendFailure(Component.literal("服务端禁用"));
                                        return 0;
                                    }
                                })
                        )
        );
    }
}
