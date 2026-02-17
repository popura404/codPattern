package com.cdp.codpattern.adapter.forge.command;

import com.cdp.codpattern.config.backpack.BackpackConfigRepository;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfig;
import com.cdp.codpattern.config.weaponfilter.WeaponFilterConfigRepository;
import com.cdp.codpattern.config.path.ConfigPath;
import com.cdp.codpattern.network.SyncBackpackConfigPacket;
import com.cdp.codpattern.network.SyncWeaponFilterPacket;
import com.cdp.codpattern.adapter.forge.network.ModNetworkChannel;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.List;

/**
 * 同步所有在线玩家config的命令，缓存到所有的客户端
 */
public class UpdateWeaponFilterConfigCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("update")
                .requires(source -> source.hasPermission(2))
                .executes(context -> executeUpdate(context.getSource()));
    }

    private static int executeUpdate(CommandSourceStack source) {
        MinecraftServer server = source.getServer();

        // 获取在线玩家
        List<ServerPlayer> players = server.getPlayerList().getPlayers();

        Path backpackPath = ConfigPath.SERVERBACKPACK.getPath(server);
        Path filterPath = ConfigPath.SERVER_FILTER.getPath(server);

        // 过滤配置统一加载
        WeaponFilterConfig filterConfig = WeaponFilterConfigRepository.loadOrCreate(filterPath);

        int successCount = 0;

        for (ServerPlayer player : players) {
            try {
                // 加载玩家的背包配置
                var playerBackpackData = BackpackConfigRepository.loadOrCreatePlayer(player.getStringUUID(),
                        backpackPath);

                // 同步到客户端
                ModNetworkChannel.sendToPlayer(new SyncWeaponFilterPacket(filterConfig), player);
                ModNetworkChannel.sendToPlayer(new SyncBackpackConfigPacket(playerBackpackData), player);

                successCount++;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 对执行者反馈
        int finalSuccessCount1 = successCount;
        source.sendSuccess(() -> Component.translatable("message.codpattern.command.sync_success", finalSuccessCount1),
                true);

        return 1;
    }
}
