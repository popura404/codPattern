package com.cdp.codpattern.command;

import com.cdp.codpattern.config.BackPackConfig.BackpackConfigManager;
import com.cdp.codpattern.config.WeaponFilterConfig.WeaponFilterConfig;
import com.cdp.codpattern.core.ConfigPath.ConfigPath;
import com.cdp.codpattern.network.SyncBackpackConfigPacket;
import com.cdp.codpattern.network.SyncWeaponFilterPacket;
import com.cdp.codpattern.network.handler.PacketHandler;
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
public class UpdateWeaponfilterConfigCommand {

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
        Path filterPath = ConfigPath.SERVERFLITER.getPath(server);

        // 过滤配置统一加载
        WeaponFilterConfig fliterconfig = WeaponFilterConfig.LoadorCreate(filterPath);

        int successCount = 0;

        for (ServerPlayer player : players) {
            try {
                // 加载玩家的背包配置
                var playerBackpackData = BackpackConfigManager.LoadorCreatePlayer(player.getStringUUID(), backpackPath);

                // 同步到客户端
                PacketHandler.sendToPlayer(new SyncBackpackConfigPacket(playerBackpackData), player);
                PacketHandler.sendToPlayer(new SyncWeaponFilterPacket(fliterconfig), player);

                successCount++;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 对执行者反馈
        int finalSuccessCount1 = successCount;
        source.sendSuccess(() -> Component.literal("已同步 " + finalSuccessCount1 + " 名玩家的配置"), true);

        return 1;
    }
}

