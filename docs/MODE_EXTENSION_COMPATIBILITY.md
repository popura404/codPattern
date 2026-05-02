# 模式扩展兼容入口清单

本文档记录模式扩展框架化后仍保留的旧 TDM 命名入口。它们不是新增模式应该依赖的公共 API，而是为了旧调用点、旧集成和旧源码引用继续可编译而保留的 legacy facade。

## 使用原则

- 新代码优先使用 `app.match`、`network.match`、`client.gui.screen.match` 和 `ModeRoomScreen`。
- 旧 `Tdm*` 类只用于兼容旧调用，不承载新的真实实现。
- 删除 legacy facade 前，必须确认外部集成、旧命令、旧地图读取、packet 注册顺序和 UI 打开路径没有依赖旧类名。
- 不要为了适配 legacy facade 给新模式补假队伍、假 ready、假 vote 或假比分。

## 保留入口

| 旧入口 | 新入口 | 保留原因 |
|---|---|---|
| `com.cdp.codpattern.network.tdm.*` | `com.cdp.codpattern.network.match.*` | 保留旧源码引用；真实 packet 实现和 Forge 注册已在 `network.match`。 |
| `client.gui.screen.TdmRoomScreen` | `client.gui.screen.ModeRoomScreen` | 保留旧 UI 打开类名；实际房间界面实现已在公共 screen。 |
| `client.gui.screen.TdmModeSelectScreen` | `client.gui.screen.ModeSelectScreen` | 保留旧模式选择类名；实际实现已在公共 screen。 |
| `client.gui.screen.tdm.*` | `client.gui.screen.match.*` | 保留旧 DTO、renderer、state、helper 名称；真实逻辑已在公共 match 包。 |
| `client.ClientTdmState` | `client.ClientMatchState` | 保留旧客户端状态 facade；状态持有者已切到 `ClientMatchStateStore`。 |
| `client.gui.refit.TdmRoomButton` | `client.gui.refit.ModeRoomButton` | 保留旧暂停菜单按钮入口；当前入口已改用公共按钮。 |
| `client.gui.refit.TdmRoomActionButton` | `client.gui.refit.ModeRoomActionButton` | 保留旧 room action 按钮类名；当前按钮实现已公共化。 |
| `app.tdm.model.TdmGameTypes` | `app.match.BuiltInGameModes` / `GameModeRegistry` | 保留旧模式 ID 常量和别名 canonicalize 入口。 |
| `app.tdm.model.TdmMapEditorSchemas` | `app.match.editor.ModeMapEditorSchemas` | 保留旧地图编辑 schema API；公共 schema 已支持 string layer。 |
| `app.tdm.service.TdmRoomInteractionService` | `app.match.service.ModeRoomInteractionService` | 保留旧房间交互 service 名称；加入、离开、选队、ready、vote 已走公共 service。 |
| `app.tdm.service.DynamicSpawnMergeService` | `app.match.service.DynamicSpawnMergeService` | 保留旧动态出生点合并 service 名称。 |
| `compat.fpsmatch.map.CodTdmMapAccess` | `compat.fpsmatch.map.FpsMatchMapRegistry` + mode runtime ports | 保留旧 frontline map access 集成 API。 |
| `compat.fpsmatch.map.CodTacticalTdmMapAccess` | `compat.fpsmatch.map.FpsMatchMapRegistry` + mode runtime ports | 保留旧 teamdeathmatch map access 集成 API。 |

## 删除前置条件

这些入口可以继续薄化，但不应在没有完整验证时删除。删除前至少满足：

- `./gradlew compileJava` 通过。
- `rg "import com\\.cdp\\.codpattern\\.network\\.tdm\\." src/main/java -n` 无命中。
- `rg "client\\.gui\\.screen\\.tdm|ClientTdmState|TdmRoom" src/main/java/com/cdp/codpattern/client/gui/screen/match src/main/java/com/cdp/codpattern/client/gui/screen/ModeRoomScreen.java src/main/java/com/cdp/codpattern/network/handler src/main/java/com/cdp/codpattern/client/network -n` 无命中。
- `rg "TdmGameTypes|CodTdmMapAccess|CodTacticalTdmMapAccess" src/main/java -n` 只命中 legacy facade 自身或明确的旧兼容代码。
- `frontline`、`teamdeathmatch`、`cdptdm`、`cdptacticaltdm` 的注册、地图读取、房间列表、加入、离开、选队、ready、投票和结算传送行为已手动或自动验证。

## 新模式接入建议

新增模式时从这些公共入口开始：

- 模式定义：`GameModeDefinition` + `GameModeBootstrap` 注册。
- runtime：实现 `ModeRoomSummaryPort` 和 `ModeRoomLifecyclePort`，按需提供 optional ports。
- 房间交互：复用 `ModeRoomInteractionService`。
- 客户端展示：注册 `ClientModePresentation`，房间 UI 使用 capability 和 metrics。
- 地图编辑：注册 `ModeMapEditorSchema`，点位 layer 使用 string key。
- 网络：新房间 packet 构造和真实实现放在 `network.match`。
