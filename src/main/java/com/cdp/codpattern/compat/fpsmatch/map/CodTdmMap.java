package com.cdp.codpattern.compat.fpsmatch.map;

import com.cdp.codpattern.app.tdm.model.TdmGameTypes;
import com.cdp.codpattern.app.tdm.port.CodTdmActionPort;
import com.cdp.codpattern.app.tdm.port.CodTdmReadPort;
import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import com.phasetranscrystal.fpsmatch.core.data.SpawnPointData;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.map.BaseTeam;
import com.phasetranscrystal.fpsmatch.core.map.EndTeleportMap;
import com.phasetranscrystal.fpsmatch.core.map.GiveStartKitsMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * COD Team Deathmatch 地图核心类
 * 实现完整的团队死斗游戏逻辑
 */
public class CodTdmMap extends BaseMap implements GiveStartKitsMap<CodTdmMap>, EndTeleportMap<CodTdmMap> {
    private final CodTdmMapLifecycleRuntime lifecycleRuntime;
    private final CodTdmActionPort actionPort;
    private final CodTdmReadPort readPort;

    // 地图配置态
    private final CodTdmKitsRuntime kitsRuntime;

    /**
     * 构造函数
     */
    public CodTdmMap(ServerLevel serverLevel, String mapName, AreaData areaData) {
        super(serverLevel, mapName, areaData);
        CodTdmMapRuntimeAssembly.BootstrapResult bootstrapResult = CodTdmMapRuntimeAssembly.bootstrap(
                this,
                player -> CodTdmMap.super.leave(player),
                () -> mapName,
                () -> isStart,
                () -> this.isStart = true,
                () -> this.isStart = false
        );
        this.kitsRuntime = bootstrapResult.kitsRuntime();
        this.lifecycleRuntime = bootstrapResult.lifecycleRuntime();
        this.actionPort = bootstrapResult.actionPort();
        this.readPort = bootstrapResult.readPort();
    }

    // 基础方法覆盖

    @Override
    public void tick() {
        lifecycleRuntime.tick();
    }

    @Override
    public void syncToClient() {
        lifecycleRuntime.syncToClient();
    }

    @Override
    public String getGameType() {
        return TdmGameTypes.CDP_TDM;
    }

    @Override
    public void startGame() {
        lifecycleRuntime.startGame();
    }

    @Override
    public void victory() {
        lifecycleRuntime.transitionToEnded();
    }

    @Override
    public boolean victoryGoal() {
        return lifecycleRuntime.hasReachedVictoryGoal();
    }

    @Override
    public void resetGame() {
        lifecycleRuntime.resetGame();
    }

    /**
     * 玩家离开房间（不是换队）。
     * 会尝试传送到比赛结束点，然后移除房间内状态。
     */
    @Override
    public void leave(ServerPlayer player) {
        lifecycleRuntime.leaveRoom(player);
    }

    /**
     * 发放玩家装备 (基于背包系统)
     */
    @Override
    public void givePlayerKits(ServerPlayer player) {
        kitsRuntime.givePlayerKits(player);
    }

    // GiveStartKitsMap 实现

    @Override
    public ArrayList<ItemStack> getKits(BaseTeam team) {
        return kitsRuntime.getOrCreateKits(team.name);
    }

    @Override
    public void addKits(BaseTeam team, ItemStack itemStack) {
        kitsRuntime.addKit(team.name, itemStack);
    }

    @Override
    public void setStartKits(Map<String, ArrayList<ItemStack>> kits) {
        kitsRuntime.setStartKits(kits);
    }

    @Override
    public void setAllTeamKits(ItemStack itemStack) {
        kitsRuntime.setAllTeamKits(itemStack);
    }

    @Override
    public Map<String, List<ItemStack>> getStartKits() {
        return kitsRuntime.startKitsSnapshot();
    }

    @Override
    public CodTdmMap getMap() {
        return this;
    }

    // EndTeleportMap 实现

    @Override
    public void setMatchEndTeleportPoint(SpawnPointData data) {
        lifecycleRuntime.setMatchEndTeleportPoint(data);
    }

    CodTdmActionPort actionPort() {
        return actionPort;
    }

    CodTdmReadPort readPort() {
        return readPort;
    }
}
