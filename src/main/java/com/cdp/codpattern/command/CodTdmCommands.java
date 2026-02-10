package com.cdp.codpattern.command;

import com.cdp.codpattern.fpsmatch.map.CodTdmMap;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.phasetranscrystal.fpsmatch.core.FPSMCore;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
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

                                    // 创建一个空的区域数据（以玩家当前位置为中心）
                                    net.minecraft.core.BlockPos pos = source.getPlayerOrException().blockPosition();
                                    AreaData areaData = new AreaData(pos, pos.offset(10, 10, 10));

                                    // 注册地图
                                    FPSMCore.getInstance().registerMap(mapName,
                                            new CodTdmMap(level, mapName, areaData));

                                    source.sendSuccess(() -> Component.literal(
                                            "§e[已弃用] §a已创建 TDM 地图: " + mapName + "\n§7请使用 §b/fpsm tdm §7命令管理 TDM 地图"),
                                            true);
                                    return 1;
                                }))));
    }
}
