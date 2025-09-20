package com.cdp.codpattern.command;

import com.cdp.codpattern.client.gui.screen.MainMenuScreen;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class MainMenuScreenCommand {
    @SubscribeEvent
    public void onCommandRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();


        dispatcher.register(
                Commands.literal("cdp")
                        .then(Commands.literal("screen")
                                .executes(context -> {
                                    if(context.getSource().getEntity() instanceof Player ) {



                                        //Minecraft.getInstance().setScreen(new MainMenuScreen());
                                        Minecraft.getInstance().execute(() -> {
                                            Minecraft.getInstance().player.playNotifySound(SoundEvents.COW_HURT , SoundSource.PLAYERS , 1f , 1f);
                                            // 每次打开时创建新实例
                                           Minecraft.getInstance().setScreen(new MainMenuScreen());
                                            context.getSource().sendSuccess(() -> {
                                            if (Minecraft.getInstance().screen != null) {
                                                return Component.literal("screen height:\n" + Minecraft.getInstance().screen.height);
                                            }else {
                                                return null;
                                            }
                                        }, true);
                                        });
                                        return 1;



                                    }else {
                                        context.getSource().sendFailure(Component.literal("failure"));
                                        return 0;
                                    }
                                })
                        )
        );
    }
}
