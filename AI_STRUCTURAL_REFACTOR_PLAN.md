# Planning v1（AI可执行重构文档）

以下计划基于当前仓库真实状态制定，作为“结构性整治路线图”。

## 0. 当前诊断（作为重构输入）
1. 超大类/超大屏幕类明显：`src/main/java/com/cdp/codpattern/fpsmatch/map/CodTdmMap.java`（1284 行）、`src/main/java/com/cdp/codpattern/client/gui/screen/TdmRoomScreen.java`（1004 行）、`src/main/java/com/cdp/codpattern/client/gui/screen/BackpackMenuScreen.java`（625 行）。
2. 网络层集中且耦合重：`src/main/java/com/cdp/codpattern/network/handler/PacketHandler.java` 一次性注册全部包，且很多 `Packet` 直接写配置/业务。
3. 命名与包结构不统一：`BackPackConfig` / `Weaponhandling` / `UpdateWeaponfilterConfigCommand` / `distributeBackpackItemsCommand`。
4. 配置与客户端缓存使用静态全局状态：`BackpackConfigManager`、`WeaponFilterConfig`，业务层直接依赖静态对象。
5. 兼容逻辑散落在业务/网络/UI：TaCZ、FPSMatch、LR Tactical、PhysicsMod 缺少统一兼容层。
6. 仓库处于大面积脏状态，且 CRLF/LF 混用，后续容易出现误伤式大 diff。

## 1. 目标结构（重构后）
```text
src/main/java/com/cdp/codpattern/
  bootstrap/                # Mod 入口与装配
  app/                      # 应用层（纯业务流程）
    backpack/
    tdm/
    refit/
  compat/                   # 兼容层（第三方模组桥接）
    tacz/
    fpsmatch/
    lrtactical/
    physicsmod/
  adapter/forge/            # 框架层（Forge 命令/事件/网络）
    command/
    event/
    network/
  client/                   # 客户端展示层（Screen/HUD/State）
    gui/
    state/
  config/                   # 配置模型与仓储
    backpack/
    weaponfilter/
    tdm/
    path/
  shared/                   # 公共 DTO/常量/工具
```

## 2. 分阶段执行（建议按顺序）

### P0 基线冻结（必须先做）
目标：避免当前脏工作区被重构误伤。  
操作：记录当前 `git status` 快照；统一换行策略（`.gitattributes` 增补 `*.java text eol=lf` 或 `crlf`）；先做“仅换行”独立提交。  
涉及文件：`.gitattributes`、全 `*.java`。  
验收：后续功能性重构 diff 不再被换行噪音淹没。

### P1 命名与包规范化（不改行为）
目标：先消除最影响维护者认知的命名噪音。  
操作：  
`Weaponhandling` -> `WeaponHandlingService`；  
`UpdateWeaponfilterConfigCommand` -> `UpdateWeaponFilterConfigCommand`；  
`distributeBackpackItemsCommand` -> `DistributeBackpackItemsCommand`；  
`ConfigPath.SERVERFLITER` -> `SERVER_FILTER`。  
涉及文件：`src/main/java/com/cdp/codpattern/core/handler/Weaponhandling.java`、`src/main/java/com/cdp/codpattern/command/*.java`、`src/main/java/com/cdp/codpattern/core/ConfigPath/ConfigPath.java`。  
验收：类名均 PascalCase，枚举常量拼写无误，编译通过。

### P2 分层骨架搭建（先建壳再迁移）
目标：建立“兼容层 vs 应用层 vs Forge 适配层”边界。  
操作：新增 `app/*`、`compat/*`、`adapter/forge/*` 包；先放接口和空实现。  
涉及文件：新增目录与 `package-info.java`（每层写依赖规则）。  
验收：目录结构清晰，文档明确“谁可依赖谁”。

### P3 网络层瘦身
目标：`Packet` 只做编解码+转发，不做业务。  
操作：把 `Add/Clone/Delete/Rename/Select/UpdateWeapon/...` 的业务搬到 `app.backpack`；`PacketHandler` 拆成 `BackpackPacketRegistrar`、`TdmPacketRegistrar`、`RefitPacketRegistrar`。  
涉及文件：`src/main/java/com/cdp/codpattern/network/*.java`、`src/main/java/com/cdp/codpattern/network/tdm/*.java`、`src/main/java/com/cdp/codpattern/network/handler/PacketHandler.java`。  
验收：`Packet#handle` 仅调用 service；`PacketHandler` < 120 行。

### P4 配置层重构（去静态全局污染）
目标：配置读写统一入口，客户端缓存与服务端存储隔离。  
操作：将 `BackpackConfigManager`、`WeaponFilterConfig` 重构为 Repository + Cache；移除 `LoadorCreate/getCLIENT/setCLIENT` 这种混合职责。  
涉及文件：`src/main/java/com/cdp/codpattern/config/BackPackConfig/*`、`src/main/java/com/cdp/codpattern/config/WeaponFilterConfig/*`、相关调用方（network/event/client）。  
验收：配置访问走统一接口；客户端不直接碰服务端存储逻辑。

### P5 TDM 核心拆分（重点屎山）
目标：把 `CodTdmMap` 从“上帝类”拆成协作组件。  
操作：抽出 `PhaseStateMachine`、`RespawnService`、`VoteService`、`ScoreService`、`KitDistributionService`；`CodTdmMap` 仅编排。  
涉及文件：`src/main/java/com/cdp/codpattern/fpsmatch/map/CodTdmMap.java`、`src/main/java/com/cdp/codpattern/fpsmatch/room/CodTdmRoomManager.java`、`src/main/java/com/cdp/codpattern/fpsmatch/event/CodTdmEventHandler.java`。  
验收：`CodTdmMap` 降到约 400-500 行；每个子服务单一职责。

### P6 客户端大屏幕拆分
目标：UI 结构可维护，新增功能时不改千行类。  
操作：`TdmRoomScreen` 拆为 `state + controller + renderer`；`BackpackMenuScreen` 拆出 `ContextMenuCoordinator`、`WeaponPreviewPanel`；`ClientTdmState` 改为实例化 `ClientMatchStateStore`。  
涉及文件：`src/main/java/com/cdp/codpattern/client/gui/screen/*.java`、`src/main/java/com/cdp/codpattern/client/ClientTdmState.java`。  
验收：单屏幕主类 < 400 行，渲染/输入/网络回调分离。

### P7 兼容层收口
目标：外部模组 API 调用全部集中在 `compat`。  
操作：建立 `TaczGateway`、`FpsMatchGateway`、`LrTacticalGateway`、`PhysicsModBridge`；业务层不再直接 `import com.tacz.../com.phasetranscrystal...`。  
涉及文件：`src/main/java/com/cdp/codpattern/compatibility/lrtactical/api/APIextension.java`、`src/main/java/com/cdp/codpattern/network/tdm/PhysicsMobRetainPacket.java`、`src/main/java/com/cdp/codpattern/fpsmatch/*`、`src/main/java/com/cdp/codpattern/core/*`。  
验收：第三方 API import 只出现在 `compat/*`。

### P8 清理与文档化
目标：维护者一眼看懂结构和扩展点。  
操作：删除冗余与历史包；补 `docs/architecture.md`、`docs/module-boundary.md`、`docs/feature-add-guide.md`；清理 `.DS_Store`。  
涉及文件：根目录文档 + 被替代旧类。  
验收：新功能接入说明完整（命令/事件/网络/兼容/配置新增路径都有模板）。

## 3. 建议的首批移动清单（第一轮）
1. `src/main/java/com/cdp/codpattern/core/handler/Weaponhandling.java` -> `src/main/java/com/cdp/codpattern/app/backpack/service/BackpackDistributor.java`
2. `src/main/java/com/cdp/codpattern/command/distributeBackpackItemsCommand.java` -> `src/main/java/com/cdp/codpattern/adapter/forge/command/DistributeBackpackItemsCommand.java`
3. `src/main/java/com/cdp/codpattern/command/UpdateWeaponfilterConfigCommand.java` -> `src/main/java/com/cdp/codpattern/adapter/forge/command/UpdateWeaponFilterConfigCommand.java`
4. `src/main/java/com/cdp/codpattern/network/handler/PacketHandler.java` -> `src/main/java/com/cdp/codpattern/adapter/forge/network/ModNetworkChannel.java`
5. `src/main/java/com/cdp/codpattern/config/BackPackConfig/*` -> `src/main/java/com/cdp/codpattern/config/backpack/*`
6. `src/main/java/com/cdp/codpattern/config/WeaponFilterConfig/*` -> `src/main/java/com/cdp/codpattern/config/weaponfilter/*`
7. `src/main/java/com/cdp/codpattern/config/TdmConfig/*` -> `src/main/java/com/cdp/codpattern/config/tdm/*`
8. `src/main/java/com/cdp/codpattern/core/ConfigPath/ConfigPath.java` -> `src/main/java/com/cdp/codpattern/config/path/ConfigPath.java`

## 4. 执行时硬性验收标准
1. `./gradlew compileJava` 必须通过。
2. 每阶段结束后 `git diff` 不应被纯换行噪音主导。
3. `network` 包中 `handle()` 不直接落盘、不直接改核心状态。
4. 兼容 API import（TaCZ/FPSMatch/LR Tactical/PhysicsMod）仅在 `compat/*`。
5. 新增功能应只需改 1 个 app service + 1 个 adapter（而不是跨多个包）。
