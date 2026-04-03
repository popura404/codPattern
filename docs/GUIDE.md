# COD Pattern Guide

[项目概览](README.md) | [Q&A](QANDA.md) | [更新日志](CHANGES.md)

> 本文只描述当前仓库代码已经实现的行为。命令、目录、限制、校验与数据流均以当前版本源码为准。本文基于ai撰写，内容仅供参考

## 1. 当前版本负责什么

COD Pattern 当前把以下功能收在同一套服务端逻辑里：

1. 玩家背包预设的创建、选择、修改、复制、重命名、删除与持久化。
2. TaCZ 枪械与 LR Tactical 投掷物的背包配置、筛选与发放。
3. 通过 COD Pattern 入口执行的 TaCZ 改枪流程与配件预设保存。
4. `frontline` / `teamdeathmatch` 的地图、房间、准备、投票、阶段流转、复活与结算。
5. 对局 HUD、击杀播报、敌我高光、敌方血条和战绩导出。

对应实现入口：

- 模组初始化：`com.cdp.codpattern.CodPattern`
- 命令注册：`com.cdp.codpattern.command.CommandRegistration`
- 房间列表推送：`com.cdp.codpattern.fpsmatch.room.CodTdmRoomManager`

## 2. 运行环境与数据目录

### 2.1 运行环境

- Minecraft: `1.20.1`
- Forge: `47.4.0+`
- Java: `17`
- 必需依赖: `TaCZ 1.1.6+`
- 可选联动: `LR Tactical`、`Physics Mod`、`tacz-addon 1.1.6`

### 2.2 主要数据目录

| 路径 | 用途 |
|---|---|
| `<世界>/serverconfig/codpattern/backpack_rules/backpack_config.json` | 玩家背包、槽位物品、配件预设 |
| `<世界>/serverconfig/codpattern/backpack_rules/weapon_filter.json` | 武器分类、黑名单、投掷物开关、弹药倍率 |
| `<世界>/serverconfig/codpattern/tdm_rules/config.json` | 房间与 TDM 节奏配置 |
| `<世界>/serverconfig/codpattern/tdm_match_records/` | `frontline` 战绩导出 |
| `<世界>/serverconfig/codpattern/tactical_tdm_match_records/` | `teamdeathmatch` 战绩导出 |
| `<游戏目录>/fpsmatch/<世界名>/frontline/` | `frontline` 地图数据 |
| `<游戏目录>/fpsmatch/<世界名>/teamdeathmatch/` | `teamdeathmatch` 地图数据 |

要点：

- 背包和 TDM 配置在世界存档的 `serverconfig` 下面。
- 地图不在世界存档里，而在游戏根目录的 `fpsmatch/<世界名>/...` 下面。
- 迁移服务器时，世界存档和 `fpsmatch/` 目录要一起复制。

## 3. 服务端启动和玩家进服时会发生什么

### 3.1 服务端启动

服务端启动后会执行两件事：

1. 注册网络包。
2. 加载 `tdm_rules/config.json`。

对应实现：

- 网络注册：`CodPattern.setup` -> `ModNetworkChannel.register()`
- TDM 配置加载：`CodPattern.onServerStarting` -> `CodTdmConfig.load(server)`

### 3.2 玩家登录

玩家登录时服务端会：

1. 加载或创建 `weapon_filter.json`。
2. 加载或创建该玩家的背包数据。
3. 将筛选配置和该玩家的背包数据同步到客户端。
4. 继续交给 FPSM 房间系统处理登录后的房间状态恢复。

对应实现：

- `PlayerLoggedInEventHandler.onPlayerJoin`
- `SyncWeaponFilterPacket`
- `SyncBackpackConfigPacket`

这意味着：

- 新玩家第一次进服时会立即生成默认背包。
- 客户端武器选择界面看到的内容，以登录时或 `/cdp update` 后同步到本地的配置为准。

## 4. 背包系统

### 4.1 玩家背包数据如何创建

背包数据模型在 `BackpackConfig`，仓库在 `BackpackConfigRepository`。

当前实现固定规则：

- 每名玩家首次创建时自动生成 `3` 套默认背包。
- 默认选中背包 `1`。
- 每套背包固定 `4` 个槽位：
  - `primary`
  - `secondary`
  - `tactical`
  - `lethal`
- 每个槽位保存三类信息：
  - `item`
  - `nbt`
  - `attachmentPreset`

默认背包内容写死在 `BackpackConfig` 的静态字段里：

- 背包 1: `hk_g3` + `glock_17`
- 背包 2: `ak47` + `deagle`
- 背包 3: `m4a1` + `p320`
- 新增背包默认主副武器: `m4a1` + `p320`
- 默认投掷物: `lrtactical:m67` 与 `lrtactical:smoke_grenade`

### 4.2 背包增删改选的实际规则

对应实现：

- 新增：`AddBackpackPacket` + `BackpackConfigRepository.addCustomBackpack`
- 复制：`CloneBackpackPacket`
- 重命名：`RenameBackpackPacket`
- 删除：`DeleteBackpackPacket`
- 选择：`SelectBackpackPacket`

当前规则不是 UI 约定，而是服务端强制执行：

- 背包 ID 只允许占用 `1` 到 `10`。
- `getNextAvailableId()` 会找第一个空缺 ID。
- 当 `1` 到 `10` 全部占满时，新增和复制都会失败。
- 删除时至少要保留 `1` 套背包。
- 删除当前选中的背包后，系统会自动切到剩余背包里编号最小的一套。
- 重命名会先 `trim()`，空名直接拒绝，超过 `32` 个字符会被截断。
- 复制会把源背包 4 个槽位的数据完整复制，包括 `attachmentPreset`。
- 新增背包不会自动切换当前选中项，原选中背包会保留。

### 4.3 背包装备是怎么发放的

对应实现：

- 玩家重生事件：`PlayerRespawnHandler`
- 发放服务：`BackpackDistributor`
- 管理员命令：`/cdp distribute [target]`

正常发放链路：

1. 玩家重生触发 `PlayerRespawnEvent`。
2. 服务端读取 `weapon_filter.json`。
3. 非强制发放时，先检查玩家是否已经在房间或对局里。
4. 读取玩家当前选中的背包。
5. 先清空整个物品栏。
6. 按槽位把配置物品写入固定栏位：
   - `primary` -> 物品栏 `0`
   - `secondary` -> 物品栏 `1`
   - `tactical` -> 物品栏 `2`
   - `lethal` -> 物品栏 `3`
7. 如果物品是 TaCZ 枪械，按 `ammunitionPerMagazineMultiple` 重设备弹。
8. 广播背包已装备提示。

服务端限制：

- 玩家是旁观者时，不发放。
- 非强制发放时，玩家不在房间内，不发放。
- 枪械如果命中 `blockedItemNamespaces` 或 `blockedWeaponIds`，该槽位直接跳过。
- `tactical` / `lethal` 只有在 `throwablesEnabled` 为 `true` 时才发放。

`/cdp distribute` 使用的是强制发放分支：

- 会跳过“必须在房间内”这条限制。
- 不会跳过“玩家当前是旁观者”这条限制。

### 4.4 武器选择时服务端会校验什么

对应实现：`UpdateWeaponService`

玩家在背包界面改槽位时，服务端按下面的顺序校验：

1. 背包是否存在。
2. 槽位名是否是 `primary / secondary / tactical / lethal`。
3. `itemId` 是否是合法注册物品。
4. `nbt` 是否能被 `TagParser` 正常解析。
5. 物品是否命中枪械黑名单。
6. 槽位和物品分类是否匹配。

分类匹配规则：

- `primary` / `secondary`
  - 先尝试通过 TaCZ 能力解析枪械分类。
  - 如果不是枪，但符合 LR Tactical 近战判定，则归到 `melee`。
  - 最终分类必须包含在对应的 `primaryWeaponTabs` 或 `secondaryWeaponTabs` 里。
- `tactical` / `lethal`
  - 必须启用 `throwablesEnabled`
  - 物品 ID 必须是 `lrtactical:throwable`
  - `nbt` 里必须包含 `ThrowableId`

写入方式：

- 服务端通过校验后，直接把 `itemId` 和 `nbt` 写回 `backpack_config.json`
- 配件预设不在这一步处理

### 4.5 `/cdp update` 实际会做什么

对应实现：`UpdateWeaponFilterConfigCommand`

`/cdp update` 只做两件事：

1. 重新读取服务端的 `weapon_filter.json`
2. 逐个玩家读取各自背包数据并同步到客户端

它不会做的事：

- 不会重载 `tdm_rules/config.json`
- 不会重建地图
- 不会替代重启服务器

因此：

- 手改 `weapon_filter.json` 后，可以用 `/cdp update`
- 手改 `backpack_config.json` 后，最稳妥的方式仍然是停服修改
- 手改 `tdm_rules/config.json` 后，需要重启服务器

## 5. TaCZ 改枪与配件预设

### 5.1 为什么 TaCZ 原生改枪界面打不开

当前版本全局拦截 TaCZ 原生 `GunRefitScreen`。

对应实现：

- 客户端拦截：`TaczRefitScreenBlocker`
- Mixin 过滤附件候选：`GunRefitScreenMixin`

行为是固定的：

- 如果打开的是 TaCZ 原生改枪界面，客户端会把它替换成 `BackpackMenuScreen`
- 玩家会收到“原生改枪界面已禁用”的系统消息

### 5.2 背包改枪会怎样准备数据

对应实现：`AttachmentPresetRequestService`

玩家点击主武器或副武器的“更换配件”后，服务端执行以下链路：

1. 玩家当前不能是旁观者。
2. 只允许改 `primary` 或 `secondary`。
3. 从背包槽位的 `item` + `nbt` 重新构造枪械 `ItemStack`。
4. 如果不是合法 TaCZ 枪械，直接结束。
5. 调用 `TaczAddonRefitCompat` 清理 `tacz-addon` 在背包改枪场景下的冲突数据。
6. 读取该槽位已有的 `attachmentPreset`。
7. 把预设重新应用到枪上。
8. 如果枪上已有被黑名单禁用的配件，先卸下。
9. 收集玩家物品栏里“当前枪能装上、且不在配件黑名单里”的附件，作为改枪沙盒库存。
10. 创建编辑会话，并把预设数据同步给客户端。

### 5.3 改枪会话怎么保证可回滚

对应实现：`AttachmentEditSessionManager`

开始改枪时，服务端会创建一个“沙盒物品栏”：

1. 先完整快照玩家当前背包。
2. 清空玩家当前物品栏。
3. 把待改的枪放进当前热键栏位。
4. 把允许使用的配件依次塞进主物品栏。
5. 至少保留 `1` 个空格，确保 TaCZ 的卸下操作有空位可落地。
6. 记录会话信息，包括：
   - 背包 ID
   - 槽位名
   - 当前热键栏位
   - 原先选中的热键栏位
   - 整包快照
   - 超时时间
   - 实际塞入的配件数量
   - 因空间不足被截断的配件数量

当前会话超时时间固定为 `120000ms`，也就是 `120` 秒。

会话结束逻辑：

- 正常保存：恢复原物品栏快照，结束会话
- 失败或超时：恢复原物品栏快照，并提示已回滚

### 5.4 配件保存时服务端会校验什么

对应实现：`AttachmentPresetSaveService`

保存时服务端会检查：

1. 当前玩家是否还持有有效会话。
2. 会话里的背包 ID 和槽位是否与本次保存一致。
3. 当前热键栏位物品是否仍然是一把枪。
4. 枪上是否还存在被黑名单禁用的已安装配件。

保存成功后会写回以下字段：

- `item`: 当前枪械物品 ID
- `nbt`: 当前枪械完整 NBT
- `attachmentPreset`: 由当前枪状态重新构造出的配件预设字符串

如果保存失败：

- 槽位内容会恢复到保存前的旧值
- 会话会被中止
- 玩家物品栏会恢复到改枪前快照

## 6. 房间、地图与对局流程

### 6.1 房间入口在哪里

对应实现：`CreateMenuButtonsHandler`

客户端打开暂停菜单后，会自动加两个按钮：

- `背包设置`
- `Frontline / TeamDeathMatch`

这两个入口都不是命令面板，而是暂停菜单上的固定按钮。

### 6.2 房间列表如何同步

对应实现：`CodTdmRoomManager`

房间列表来自当前 FPSMCore 已注册地图的读端口快照。每个房间同步的信息包括：

- 当前阶段
- 玩家人数
- 最大人数
- 各队人数
- 各队比分
- 剩余时间
- 是否设置结束传送点

同步策略：

- 玩家订阅房间大厅后，立即收到一次完整快照
- 房间脏标记会触发增量推送
- 脏推送最短节流时间为 `350ms`
- 即使没有脏更新，也会每 `1000ms` 做一次稳态刷新

### 6.3 入房、选队、准备和投票的规则

对应实现：

- 入房与离房：`TdmRoomInteractionService`
- Ready 状态：`CodTdmVoteCoordinator`
- 投票：`VoteService`

当前服务端规则：

- 只有 `WAITING` 阶段允许加入房间。
- 玩家加入时如果未指定队伍，会按 `maxTeamDiff` 自动找一个可加入队伍。
- 指定队伍时会检查：
  - 队伍是否存在
  - 队伍是否已满
  - 加入后是否超过 `maxTeamDiff`
- 加入成功后：
  - 初始化 Ready 状态为 `false`
  - 立刻启用房间饱食度锁定
  - 如果地图没设置结束传送点，立即提示
- Ready 状态只能在 `WAITING` 阶段改。
- 开始投票只能在 `WAITING` 阶段发起。
- 开始投票发起前必须满足：
  - 房间人数达到 `minPlayersToStart`
  - 所有已加入玩家都已准备
  - 当前地图已经设置结束传送点
- 结束投票只能在 `WARMUP` 或 `PLAYING` 阶段发起。
- 投票超时时间固定为 `15` 秒。
- 通过人数按百分比阈值换算：
  - 开始投票使用 `votePercentageToStart`
  - 结束投票使用 `votePercentageToEnd`

### 6.4 对局阶段状态机

对应实现：`PhaseStateMachine`

当前阶段固定为：

1. `WAITING`
2. `COUNTDOWN`
3. `WARMUP`
4. `PLAYING`
5. `ENDED`

各阶段的服务端动作：

- `WAITING`
  - 等待玩家入房、选队、准备、发起开始投票
- `COUNTDOWN`
  - 广播倒计时
  - 最后 `blackoutStartTicks` 进入黑屏提示
- `WARMUP`
  - 房间成员切回 `ADVENTURE`
  - 已加入队伍的玩家传送到开局出生点
  - 已加入队伍的玩家发放装备
- `PLAYING`
  - 重置正式计时
  - 已加入队伍的玩家再次传送到出生点
  - 已加入队伍的玩家再次发放装备
  - 开始正式计分
- `ENDED`
  - 触发结算通知与战绩导出
  - 已加入队伍的玩家清空背包
  - 房间成员回到 `ADVENTURE`
  - 清理本局临时状态
  - 经过 `300` tick 结算页后，若有结束传送点则把已加入队伍的玩家统一传送，否则逐个提示缺失

### 6.5 复活、无敌、死亡视角和回血

对应实现：

- 复活计时与无敌：`RespawnService`
- 死亡视角：`DeathCamService`
- 呼吸回血：`CombatRegenService`
- 饱食度锁定：`RoomFoodLockService` + `RoomFoodLockEventHandler`

当前逻辑：

- 玩家死亡后进入复活倒计时，默认 `respawnDelayTicks = 40`
- 复活成功后：
  - 切回 `ADVENTURE`
  - 回满生命值
  - 食物值重置为 `20`
  - 清除状态效果
  - 重新发放装备
  - 进入无敌期，默认 `invincibilityTicks = 30`
- 死亡视角持续时间默认 `deathCamTicks = 30`
- 呼吸回血只在 `PLAYING` 阶段执行
- 玩家受伤后先进入冷却，默认 `combatRegenDelayTicks = 120`
- 冷却结束后按 `combatRegenHalfHeartsPerSecond` 逐 tick 回血

房间饱食度锁定的意义：

- 房间内玩家的饱食度被强制锁到 `20`
- 原版自然回血会被拦截
- 只有 `CombatRegenService` 通过白名单窗口触发的自定义回血可以生效

### 6.6 动态复活点如何选

对应实现：

- 安全校验：`SpawnSafetyValidator`
- 候选点评分：`DynamicRespawnSelector`

`DYNAMIC_CANDIDATE` 的选择不是随机点名，而是先做过滤，再做评分。

安全过滤条件：

- 出生点必须在可生成坐标范围内
- 地面必须能站人
- 脚下和头顶不能有流体
- 脚下和头顶不能有碰撞体
- 不能是火、灵魂火、岩浆、仙人掌、甜浆果丛、凋零玫瑰
- 玩家碰撞箱必须能在该点无碰撞落地

通过安全过滤后，再按下列因素评分：

- 离最近敌人越远越好
- 敌人平均距离越远越好
- 离队友的距离会参与平衡
- 出生点被队友直视的比例会参与平衡
- 如果敌人能直视该点，会被额外扣分

如果当前没有任何可用动态点：

- 玩家会暂时保持旁观
- 复活服务会按重试间隔继续尝试

### 6.7 敌我高光、敌方血条和 HUD

对应实现：

- 敌我关系追踪：`TdmCombatMarkerTracker`
- HUD 绘制：`TdmHudOverlay`

当前客户端表现：

- `WARMUP`
  - 队友高光为白色
  - 敌人也会高光，颜色为黄色
- `PLAYING`
  - 队友维持高光
  - 敌人不再常亮高光
  - 敌方血条按“视线 + 瞄准”触发

敌方血条触发条件：

1. 目标必须是活着的敌人。
2. 必须在最大距离内，默认 `96` 格。
3. 本地玩家必须能看到目标。
4. 满足以下任一条件：
   - 准星直接命中该敌人
   - 目标持续处于视锥内达到阈值

默认视锥参数：

- 半角 `30` 度
- 连续判定 `20` tick
- 可见缓冲 `3` tick

### 6.8 战绩导出与结算页

对应实现：`CodTdmMatchResultExporter`

结算时服务端会导出 JSON，内容包括：

- 地图名
- 开始时间和结束时间
- ISO 时间戳
- 持续秒数
- 胜利队伍
- 各队比分
- 每名参赛玩家的：
  - UUID
  - 名字
  - 所属队伍
  - 击杀
  - 死亡
  - K/D

客户端结算 HUD 还会从房间玩家快照里展示 MVP、SVP 与全员面板。

## 7. 地图创建、命令链和工具物品

### 7.1 模式名和队伍名

当前建议使用的模式名：

- `frontline`
- `teamdeathmatch`

兼容旧别名：

- `cdptdm` 会被规范化为 `frontline`
- `cdptacticaltdm` 会被规范化为 `teamdeathmatch`

当前默认队伍名：

- `kortac`
- `specgru`

### 7.2 `/cdp map` 命令链的实际行为

对应实现：`MapManagementCommand`

`/cdp map create <type> <map> <from> <to>`

- 先校验模式是否合法
- 再校验地图名非空且未重复
- 用对应模式的工厂构造 `BaseMap`
- 注册到 FPSMCore
- 立即持久化
- 持久化失败时回滚注册

`/cdp map delete <type> <map>`

- 先删持久化文件
- 再把房间里的人踢出该地图
- 调用 `resetGame`
- 最后从 FPSMCore 注销地图

`/cdp map spawn add ...`

- 要求当前维度和地图维度一致
- 要求坐标在地图区域内
- 要求队伍存在
- 要求复活点类型合法
- 不能添加重复坐标
- 保存失败时回滚到旧的 `TeamSpawnProfile`

`/cdp map spawn remove ...`

- 按 1 基序号删除
- 删除后会清空已分配的出生点缓存
- 删除 `INITIAL` 点后如果还有剩余 `INITIAL` 点，会重新分配

`/cdp map spawn clear ...`

- 清空指定队伍下指定类型的全部点
- 保存失败时同样回滚

`/cdp map spawn merge <type> <map>`

- 仅 `teamdeathmatch` 可用
- 会收集当前地图两队下全部 `DYNAMIC_CANDIDATE` 点并先去重
- 合并结果会把这份去重后的动态点并集分别写回两队
- 例如红队有 `abcde`，蓝队有 `abjh`，合并后两队都会得到 `abcdejh`
- 合并结果仍然写回两队各自独立的 `TeamSpawnProfile`
- 合并完成后管理员依然可以继续对两队独立增删动态复活点
- 保存失败时同样回滚

`/cdp map endtp <show|set> <地图名>`

- `show` / `set` 只按地图名查找
- 如果不同模式下存在同名地图，会报歧义错误
- `set` 会直接覆盖已有结束传送点
- `set` 不再要求坐标位于地图区域内，也允许设置到其他维度

### 7.3 工具物品工作流

#### 地图创建工具

物品：`codpattern:map_creator_tool`

对应实现：

- 物品逻辑：`MapCreatorTool`
- 创建动作：`MapCreatorToolActionC2SPacket`

操作方式：

- 左键方块：记录 `pos1`
- 右键方块：记录 `pos2`
- `Ctrl + 右键`：打开工具界面，填写模式和地图名并执行创建

工具会保留草稿信息，并在手持时渲染区域预览。

#### 复活点工具

物品：`codpattern:spawn_point_tool`

对应实现：

- 物品逻辑：`SpawnPointTool`
- 工具界面动作：`SpawnPointToolActionC2SPacket`

操作方式：

- `Ctrl + 右键`：打开工具界面，选择模式、地图、队伍和点类型
- 选择 `teamdeathmatch` 且当前点类型为 `DYNAMIC_CANDIDATE` 时，可直接在界面内对当前模式 + 地图执行动态点合并
- 左键方块：在所点方块上方一格写入复活点

服务端校验与命令链一致：

- 地图存在
- 队伍存在
- 当前维度匹配
- 点击点在地图区域内
- 坐标不能重复
- 合并动态点时要求当前地图模式支持动态复活，且队伍数正好为 2

这个工具也会在手持时渲染地图区域和现有复活点预览。

## 8. 配置文件说明

### 8.1 `backpack_config.json`

路径：

- `<世界>/serverconfig/codpattern/backpack_rules/backpack_config.json`

当前用途：

- 保存每名玩家的所有背包
- 保存当前选中的背包编号
- 保存 4 个槽位的物品 ID、NBT 和配件预设

重要字段：

- `playerData`
- `selectedBackpack`
- `backpacks_MAP`
- `item_MAP`
- `attachmentPreset`

维护建议：

- 运行中不要直接手改
- 批量修改时先停服
- 这个文件没有针对人工冲突编辑做额外保护

### 8.2 `weapon_filter.json`

路径：

- `<世界>/serverconfig/codpattern/backpack_rules/weapon_filter.json`

当前字段：

- `primaryWeaponTabs`
- `secondaryWeaponTabs`
- `blockedItemNamespaces`
- `blockedWeaponIds`
- `blockedAttachmentNamespaces`
- `blockedAttachmentIds`
- `throwablesEnabled`
- `ammunitionPerMagazineMultiple`

默认文件里会带占位示例值：

- `example_gunpack`
- `namespace:gunid`
- `example_attachment_pack`
- `namespace:attachmentid`

这些占位值不会自动替换成真实枪包名，需要你手动改成自己的内容。

### 8.3 `tdm_rules/config.json`

路径：

- `<世界>/serverconfig/codpattern/tdm_rules/config.json`

当前默认字段如下：

| 字段 | 默认值 | 说明 |
|---|---:|---|
| `timeLimitSeconds` | `420` | 正式阶段时长，单位秒 |
| `scoreLimit` | `75` | 击杀分上限 |
| `invincibilityTicks` | `30` | 复活无敌时长 |
| `respawnDelayTicks` | `40` | 复活等待时间 |
| `warmupTimeTicks` | `400` | 热身阶段时长 |
| `preGameCountdownTicks` | `200` | 开局倒计时时长 |
| `blackoutStartTicks` | `60` | 倒计时末段黑屏时长 |
| `deathCamTicks` | `30` | 死亡视角时长 |
| `minPlayersToStart` | `1` | 开始投票前所需最少人数 |
| `votePercentageToStart` | `60` | 开始投票通过阈值 |
| `votePercentageToEnd` | `75` | 结束投票通过阈值 |
| `combatRegenDelayTicks` | `120` | 受伤后开始回血前的等待时间 |
| `combatRegenHalfHeartsPerSecond` | `5.0` | 每秒恢复的半颗心数量 |
| `maxTeamDiff` | `1` | 自动分队允许的最大人数差 |
| `markerFocusHalfAngleDegrees` | `30.0` | 敌方血条判定视锥半角 |
| `markerFocusRequiredTicks` | `20` | 触发敌方血条需要的连续判定 tick |
| `markerBarMaxDistance` | `96.0` | 敌方血条最大判定距离 |
| `markerVisibleGraceTicks` | `3` | 敌方血条防闪烁缓冲 tick |

这份配置只在服务端启动时加载一次。

### 8.4 地图数据文件

路径：

- `<游戏目录>/fpsmatch/<世界名>/frontline/<地图名>.json`
- `<游戏目录>/fpsmatch/<世界名>/teamdeathmatch/<地图名>.json`

文件里保存的是：

- 地图区域
- 队伍设置
- `INITIAL` 复活点
- `DYNAMIC_CANDIDATE` 复活点
- 结束传送点

## 9. 首次部署到可开局的最短流程

1. 把 `codpattern` 与依赖模组放进 `mods/`。
2. 启动一次服务器，让 `serverconfig/codpattern/` 和 `fpsmatch/<世界名>/` 自动生成。
3. 调整 `weapon_filter.json`，确认枪械分类和黑名单。
4. 调整 `tdm_rules/config.json`，然后重启服务器。
5. 用 `/cdp map create` 建图。
6. 给 `kortac` 和 `specgru` 至少各配置 `1` 个 `INITIAL` 点。
7. 给双方继续配置足够数量的 `DYNAMIC_CANDIDATE` 点。
8. 配置 `endtp`。
9. 让玩家进房、选队、准备、发起开始投票。

## 10. 开服前检查清单

- TaCZ 版本不低于 `1.1.6`
- 地图已经真正保存到 `fpsmatch/<世界名>/...`
- 双方都有 `INITIAL` 点
- 双方都有足够的 `DYNAMIC_CANDIDATE` 点
- `endtp` 已配置
- `weapon_filter.json` 已改成你的枪包策略
- `tdm_rules/config.json` 已按你的节奏重启生效
- 世界存档与 `fpsmatch/` 已备份
