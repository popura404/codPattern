# COD Pattern Guide

[项目概览](README.md) | [Q&A](QANDA.md) | [更新日志](CHANGES.md)

> 本文对应 `0.8.6b`，说明主模组已经实现的玩法、配置和地图管理流程。Zombies 是可选独立附属，具体玩法与部署工具以附属仓库为准。

## 1. 安装与首次开局

### 1.1 运行环境

| 项目 | 要求 |
|---|---|
| Minecraft | 开发与构建目标为 `1.20.1` |
| Forge | 构建版本为 `47.4.0`，模组元数据声明 `47+` |
| Java | `17` |
| TaCZ | 必需，`1.1.6+` |
| LR Tactical | 可选，`0.3.0+`；提供近战与投掷物 |
| COD Pattern Zombies | 可选；安装时要求 `0.2.0b+` |

主模组提供 Frontline（FTL）和 TeamDeathMatch（TDM），包含背包预设、改枪、房间大厅、地图工具、对局 HUD 和战绩导出。FPSMatch 的相关代码已经在主模组中。

没有 LR Tactical 时，主副武器、房间和对局继续工作；LR 近战、投掷物选择、专用槽和默认投掷物发放不可用。需要 LR 内容时，客户端与服务端应安装一致的依赖。

`tacz-addon` 与 Physics Mod 属于可选联动。背包预设和配件保存由主模组提供，不要求安装 `tacz-addon`。

### 1.2 从安装到开局

1. 将主模组与 TaCZ 放入客户端、服务端的 `mods/`，按需要安装可选模组。
2. 启动世界生成配置。升级旧存档时，先按第 8 节执行迁移检查。
3. 修改武器筛选和对局配置；武器筛选用 `/cdp update` 同步，对局配置修改后重启服务器。
4. 用地图创建工具选择范围，创建 `frontline` 或 `teamdeathmatch` 地图。
5. 用复活点工具给 `kortac`、`specgru` 各添加至少一个 `INITIAL` 点。TDM 再配置 `DYNAMIC_CANDIDATE` 点。
6. 用地图管理工具给地图设置结束传送点，或在创建地图前设置全局默认结束点。
7. 玩家从暂停菜单进入房间，选队、准备并发起开始投票。

对局发放装备会清空玩家物品栏。进入对局前请存放随身物品；结束对局的恢复流程不提供入房前物品栏备份。

## 2. 背包、武器与投掷物

### 2.1 背包入口与默认内容

暂停菜单底部提供背包设置和模式房间入口。玩家首次登录时生成 3 套背包，默认选择背包 1。

| 背包 | 主武器 | 副武器 |
|---|---|---|
| 1 | `tacz:hk_g3` | `tacz:glock_17` |
| 2 | `tacz:ak47` | `tacz:deagle` |
| 3 | `tacz:m4a1` | `tacz:p320` |
| 新增背包 | `tacz:m4a1` | `tacz:p320` |

每套背包保存 `primary`、`secondary`、`tactical`、`lethal` 四个槽位。安装 LR Tactical 时，代码中的默认 `tactical` 为 `lrtactical:m67`，默认 `lethal` 为 `lrtactical:smoke_grenade`；未安装时保留这两个键，内容为空。

背包管理规则：

- ID 使用 `1` 到 `10`，新增和复制优先填补空缺编号，最多 10 套。
- 至少保留一套；删除当前背包后，切换到剩余编号最小的一套。
- 新增背包保留原来的选中项。
- 名称去除首尾空格，拒绝空名称，超过 32 个字符时截断。
- 复制会保留槽位物品、数量、NBT 和配件预设。

选择背包是修改下次发放时使用的预设。地图开局、复活及管理员发放命令会读取当前选择。

### 2.2 武器选择与服务端校验

主副武器可用分类由 `weapon_filter.json` 决定。默认主武器分类为 `rifle`、`sniper`、`shotgun`、`smg`、`mg`；默认副武器分类为 `pistol`、`rpg`、`melee`。

服务端会检查背包与槽位是否存在、物品 ID 是否有效、NBT 能否解析、枪械分类是否匹配，以及枪械和已安装配件是否命中黑名单。主副武器只接受允许分类中的 TaCZ 枪械或 LR 近战物品。

投掷物选择要求同时满足：

- LR Tactical 已安装。
- `throwablesEnabled` 为 `true`。
- 物品是 `lrtactical:throwable`，NBT 中包含 `ThrowableId`。

校验通过后才保存物品和规范化的 SNBT。配件预设由独立改枪流程管理。

### 2.3 装备发放

装备发放前会清空整个玩家物品栏和投掷物运行状态，然后按当前背包配置发放：

| 配置槽位 | 发放位置 |
|---|---|
| `primary` | 热键栏第 1 格，内部索引 `0` |
| `secondary` | 热键栏第 2 格，内部索引 `1` |
| `tactical` | 投掷物专用槽 1 |
| `lethal` | 投掷物专用槽 2 |

TaCZ 枪械按弹匣倍率配置备弹；命中武器黑名单的物品会被跳过，已有的禁用配件会在发放时移除。投掷物只有在 LR 可用且配置启用时发放。

普通发放要求玩家在房间中，并且不是旁观者。管理员命令 `/cdp distribute [target]` 可绕过入房条件，但仍然跳过旁观者。省略 `target` 时发给所有在线玩家，不是只发给执行者。

### 2.4 投掷物专用槽

两个投掷物按键默认都没有绑定。先在游戏控制设置的 COD Pattern 分类中绑定，再在房间内使用。

按住对应按键时，投掷物会临时放到当前手持栏位并开始准备；达到 LR 投掷物自身的准备时间后松开按键，执行投掷。过早松开或用滚轮取消时，会取消使用。结束后恢复原手持物品，剩余投掷物回到专用槽。

两个槽位独立于普通热键栏。专用投掷流程要求玩家有房间上下文，管理员在房间外发放物品不会解除这一限制。

## 3. TaCZ 改枪与配件预设

### 3.1 从背包进入改枪

TaCZ 原生 `GunRefitScreen` 入口会被替换为背包菜单，并显示提示。请在背包装备页将鼠标移到主武器或副武器卡片上，再点击出现的「更换配件」按钮；直接点击武器卡片会进入更换武器列表。

服务端从该槽位的物品数据构造枪械，重新应用已保存的配件预设，卸下禁用配件，并准备候选配件。候选来自玩家物品栏中可安装的配件，以及可选 `tacz-addon` 提供的兼容候选；附属候选会按配件 ID 去重，再执行兼容性与黑名单校验。

已有配件预设的加载和保存不依赖 `tacz-addon`。该联动主要用于补充候选与处理改枪场景中的冲突数据。

### 3.2 改枪会话

每次改枪都会建立服务端编辑会话，记录背包 ID、槽位、物品栏快照与原手持栏位。玩家临时使用待改枪械和候选配件，物理沙盒物品栏至少保留一格空位供卸下配件，改枪操作另有服务端编辑库存。

按 `Esc` 返回背包或正常关闭改枪界面时，会自动向服务端提交保存；当前没有单独的“取消改装”入口，不能把退出界面当作撤销。会话正常结束或被服务端中止后，会恢复进入改枪前的物品栏与手持栏位。超时阈值为 120 秒，服务端在后续 tick 检查并中止在线会话，提示回滚。

旁观者不能开启改枪会话，投掷物和近战槽内容也不能作为 TaCZ 枪械改装。

### 3.3 保存内容

保存以服务端编辑会话中的枪械为准。服务端检查会话、背包和槽位是否一致，确认枪械有效且没有禁用配件后，重新生成 `attachmentPreset`。

正常保存保留原槽位的 `item`、`count`、`nbt`，更新配件预设；重新构造装备时再应用该预设。客户端提交的预设文本和枪械 NBT 不是最终保存依据。

保存失败会还原本次修改过的槽位数据并结束会话。改枪成功后的配件不会作为额外物品留在玩家物品栏中。

## 4. 房间与对局

### 4.1 模式与大厅

主模组注册两个模式：

| 模式 | 标识 | 复活方式 |
|---|---|---|
| Frontline / FTL | `frontline` | 开局和死亡后使用本队 `INITIAL` 点 |
| TeamDeathMatch / TDM | `teamdeathmatch` | 开局使用 `INITIAL`，死亡后优先动态候选点，失败时回退 `INITIAL` |

旧标识 `cdptdm`、`cdptacticaltdm` 分别兼容映射到上述模式。默认队伍为 `kortac` 和 `specgru`。

大厅展示已注册房间的阶段、人数、队伍、比分、剩余时间与结束点状态。进入大厅后订阅服务端房间快照，变更由服务端继续推送。安装附属后的额外模式通过模式注册接口接入。

### 4.2 入房、选队与投票

FTL／TDM 只在 `WAITING` 阶段接受入房。自动分队会检查人数差；指定队伍还会检查队伍存在、容量和 `maxTeamDiff`。入房后准备状态初始为未准备，房间内启用饱食度锁定。

开始投票需要：

1. 当前处于 `WAITING`。
2. 参赛人数达到 `minPlayersToStart`。
3. 所有参赛玩家已准备。
4. 地图已有结束传送点。

准备状态只能在等待阶段修改。结束投票只能在 `WARMUP` 或 `PLAYING` 发起。投票最长持续 15 秒，开始和结束的默认通过比例分别为 `60%`、`75%`，人数门槛向上取整。成员离开时会移出投票并重新计算门槛。

### 4.3 阶段流程

| 阶段 | 行为 |
|---|---|
| `WAITING` | 入房、选队、准备和开始投票 |
| `COUNTDOWN` | 固定 200 tick 倒计时，末段进入黑屏过渡 |
| `WARMUP` | 固定 400 tick 准备阶段；恢复冒险模式、传送到开局点、静默发放装备，并锁定移动 |
| `PLAYING` | 解锁移动、从零开始正式计时和计分；不再次传送或发放装备 |
| `ENDED` | 导出战绩、清理本局装备和临时状态，显示 3 页结算，每页 100 tick；之后执行房间重置和玩家恢复 |

正常 20 TPS 下，倒计时 10 秒、准备 20 秒、结算 15 秒。黑屏在倒计时第 140 tick 开始，淡入、保持、淡出分别为 60、100、60 tick，横跨倒计时和准备阶段。

正式阶段默认最多 420 秒，击杀分上限 75。到达胜利条件或结束投票通过后进入结算。

### 4.4 复活、无敌与回血

死亡后进入死亡视角和复活倒计时。默认死亡视角 30 tick，复活等待 40 tick，成功复活后的无敌期 30 tick。

成功复活会回到冒险模式，恢复满血和 20 饱食度，清除状态效果，再发放当前背包装备。传送失败时保持旁观并再次尝试；连续失败达到 5 次后停止自动重试，清除死亡视角和倒计时提示，玩家仍保持旁观，需要管理员检查出生点。

TDM 动态点会先做落地、流体、碰撞和危险方块检查，再根据敌我距离、视线等因素评分。没有可用动态点时尝试本队 `INITIAL` 点，所以两种点都应设置在可安全站立的位置。

房间内饱食度固定为 20，自然回血、治疗药水等常规回血被拦截。呼吸回血仅在 `PLAYING` 执行：默认受伤后等待 120 tick，再以每秒 5 个生命值、即 2.5 颗心的速度恢复。

### 4.5 HUD 与战绩

HUD 显示队伍比分、计时、击杀播报、死亡倒计时和结算结果。准备阶段队友高光为白色、敌人为黄色；正式阶段保留队友高光，敌人改用视线与瞄准条件触发血条。

敌方血条要求目标存活、处于最大距离内且本地玩家能看到目标。准星直接命中敌人，或敌人持续处于视锥中达到阈值时显示。默认最大距离 96 格、视锥半角 30 度、持续判定 20 tick、可见缓冲 3 tick。

结算导出 JSON 战绩，包含地图、起止时间、持续时长、胜队、队伍比分，以及参赛玩家 UUID、名字、队伍、击杀、死亡和 K/D。客户端结算展示 MVP、SVP 与全员战绩。

## 5. 创建地图与配置复活点

### 5.1 工具与权限

地图工具要求管理员权限等级 2。可用以下命令取得：

```mcfunction
/give @s codpattern:map_creator_tool
/give @s codpattern:spawn_point_tool
/give @s codpattern:map_management_tool
```

地图创建、复活点和区域编辑使用工具界面。当前命令树不再提供这些操作的旧命令入口。

### 5.2 地图创建工具

手持 `codpattern:map_creator_tool`：

- 左键方块记录第一个角点。
- 右键方块记录第二个角点。
- `Ctrl + 右键` 打开界面，选择模式、填写地图名并创建。

选点时会显示区域预览，草稿保存在工具中。两个角点定义地图的三维范围，创建时需要有效范围、已注册的模式和未占用的地图名。

地图名会去除首尾空格，不能为空，不能含控制字符，UTF-8 编码长度最多 100 字节；创建界面另有 64 字符的输入限制。中文通常占多个字节。地图目录由名称编码生成，不直接使用显示名称作为目录名。

### 5.3 复活点工具

手持 `codpattern:spawn_point_tool`，`Ctrl + 右键` 打开界面，选择模式、地图、队伍和点类型，然后左键方块，在方块上方一格记录点位。

- FTL 配置双方的 `INITIAL` 点。
- TDM 配置双方的 `INITIAL` 和 `DYNAMIC_CANDIDATE` 点。
- 界面可查看、删除点位，或清空当前队伍的当前点位层。
- TDM 选择动态候选点时，可合并双方动态点；该操作要求地图支持动态复活且恰好有两支队伍。
- 手持工具会预览地图边界和当前点位层。

服务端检查地图、队伍、当前维度、被点击方块是否在地图范围内，以及实际记录坐标是否重复。记录位置是该方块上方一格，选点时应留出顶部空间。实际复活还会检查安全性；成功写入坐标不代表运行时一定可以站立。

工具另有通用区域编辑界面：左键、右键分别记录区域角点，再在界面添加、删除或清空区域。可选图层取决于模式注册内容，主模组的 FTL／TDM 当前没有区域图层。

## 6. 地图管理与结束传送

### 6.1 管理界面

右键使用 `codpattern:map_management_tool` 打开管理页。按模式选择地图后，可以查看维度、范围、尺寸、运行状态和结束点，并执行改名、删除、强制结束或打开结束传送设置。

管理请求会携带服务端会话与数据版本。地图被其他管理员修改、服务器重启或房间发生变化后，旧界面操作可能被拒绝，需要刷新后再提交。

### 6.2 改名

改名要求地图空闲、无人占用、没有未完成的玩家恢复或模式资源清理，且存储可用。名称使用与创建相同的基本规则，并拒绝当前名称以及同模式中忽略大小写后重复的名称。

服务端会更新地图定义、存储目录和运行时注册。保存或替换失败时执行回滚；不能完整回滚的操作进入恢复状态，需要检查服务端日志并重启完成恢复。

### 6.3 强制结束

管理页的强制结束与以下命令使用同一个结束服务：

```mcfunction
/roomforceend <mode> <map>
```

强制结束终止当前轮次、清理临时状态、处理本轮登记的实体与玩家恢复，保留房间成员。恢复内容包括游戏模式、视角、控制状态、重生位置和需要撤销的属性修改；已登记为对局装备的物品会清空。

传送优先尝试结束点，失败时尝试记录的返回位置，再尝试主世界出生点附近的安全位置。离线玩家、尚未复活的玩家或尚未加载的实体可能需要后续处理，管理页会显示恢复进度，并允许重试。未完成的恢复记录会保留，玩家重新登录或复活时继续处理。

### 6.4 删除

删除支持已有成员或正在运行的地图。确认后先保存删除请求并阻止新的加入、开局和编辑，再依次结束对局、恢复玩家、移出成员与观战者、回收实体并完成模式清理。

全部前置工作完成后，地图目录归档到当前世界的 `serverconfig/codpattern/maps/.storage/trash/`，再从运行时注销。遇到恢复等待或保存失败时不会直接丢弃地图，管理员可查看进度、重试或取消删除。取消停止后续删除，不会重新启动已经结束的对局。

### 6.5 单图结束点与全局默认值

管理页提供两个入口：

- 选中地图后的「结束传送」：修改这张地图使用的结束点。
- 「全局设置」：保存当前世界以后新建地图使用的默认结束点。

表单填写维度、方块坐标和朝向。使用当前位置或全局默认值按钮只会填入草稿，点击保存后才写入。服务端检查维度存在、坐标处于生成范围、建筑高度和世界边界内，朝向会规范化，俯仰角保存为 0。

全局默认值保存在当前世界的 `serverconfig/codpattern/maps/defaults.json`，只在地图首次创建时复制到地图中；修改全局值不会覆盖已有地图，旧地图缺少结束点时也不会自动补上。单图编辑要求地图空闲且没有待恢复状态。

`/cdp map endtp set` 仍保留为批量操作：把命令执行位置及朝向写入所有支持结束点的已有地图。它与 GUI 全局默认设置的作用不同，执行前请确认需要批量覆盖。

## 7. 配置文件

以下路径均相对于当前世界存档。

### 7.1 背包与筛选

| 路径 | 内容 |
|---|---|
| `serverconfig/codpattern/backpack_rules/backpack_config.json` | 所有玩家背包与当前选择 |
| `serverconfig/codpattern/backpack_rules/weapon_filter.json` | 武器分类、黑名单、投掷物开关与弹药倍率 |

背包文件以 `playerData` 保存 UUID 对应数据，玩家数据包含 `selectedBackpack` 和 `backpacks_MAP`，每套背包的 `item_MAP` 中保存 `item`、`count`、`nbt` 与 `attachmentPreset`。

背包仓库使用内存缓存，界面操作后写回文件。手动修改应停服进行；`/cdp update` 读取的是当前背包仓库，不保证重新读取外部修改过的背包 JSON。

武器筛选字段与默认值：

| 字段 | 默认值 | 作用 |
|---|---|---|
| `primaryWeaponTabs` | `rifle, sniper, shotgun, smg, mg` | 主武器允许分类 |
| `secondaryWeaponTabs` | `pistol, rpg, melee` | 副武器允许分类 |
| `blockedItemNamespaces` | `example_gunpack` | 禁用枪包命名空间 |
| `blockedWeaponIds` | `namespace:gunid` | 禁用具体枪械 ID |
| `blockedAttachmentNamespaces` | `example_attachment_pack` | 禁用配件包命名空间 |
| `blockedAttachmentIds` | `namespace:attachmentid` | 禁用具体配件 ID |
| `throwablesEnabled` | `true` | 是否启用投掷物 |
| `ammunitionPerMagazineMultiple` | `6` | 按弹匣容量配置备弹的倍率，发放时负值按 0 处理 |

分类和黑名单字段都是 JSON 字符串数组。黑名单中的默认项是占位示例，请替换为实际的枪械或配件 ID；不需要限制时使用空数组。TaCZ 的枪械与配件内容 ID 不等于通用物品 `tacz:modern_kinetic_gun` 的注册 ID。

修改筛选文件后执行 `/cdp update`，服务端会重新读取、规范化筛选配置，并将筛选配置与每位在线玩家的背包数据同步到客户端。该命令不会重载地图或对局节奏配置。

### 7.2 FTL／TDM 共用配置

路径：`serverconfig/codpattern/maps/builtin/rules/config.json`。服务端启动时加载，修改后重启。

| 字段 | 默认值 | 当前作用 |
|---|---:|---|
| `timeLimitSeconds` | `420` | 正式对局时长，秒 |
| `scoreLimit` | `75` | 击杀分上限 |
| `invincibilityTicks` | `30` | 复活无敌时间 |
| `respawnDelayTicks` | `40` | 复活等待及重试间隔 |
| `deathCamTicks` | `30` | 死亡视角时间 |
| `minPlayersToStart` | `1` | 开始投票所需最少参赛人数 |
| `votePercentageToStart` | `60` | 开始投票通过比例 |
| `votePercentageToEnd` | `75` | 结束投票通过比例 |
| `combatRegenDelayTicks` | `120` | 受伤后回血等待时间 |
| `combatRegenHalfHeartsPerSecond` | `5.0` | 每秒恢复的生命值，1 为半颗心 |
| `maxTeamDiff` | `1` | 自动分队、指定入队和换队允许的最大人数差 |
| `markerFocusHalfAngleDegrees` | `30.0` | 敌方血条判定视锥半角，度 |
| `markerFocusRequiredTicks` | `20` | 持续处于视锥中的判定时间 |
| `markerBarMaxDistance` | `96.0` | 敌方血条最大距离，格 |
| `markerVisibleGraceTicks` | `3` | 血条可见缓冲时间 |
| `warmupTimeTicks` | `400` | 字段保留；当前准备阶段使用固定 400 tick |
| `preGameCountdownTicks` | `200` | 字段保留；当前倒计时使用固定 200 tick |
| `blackoutStartTicks` | `60` | 字段保留；当前黑屏按第 4.3 节的固定时序执行 |

未标注单位的时间字段为 tick。最后三个字段仍会写入配置，但当前主模组的 PVP 状态机不读取它们来决定准备、倒计时和黑屏长度。

### 7.3 地图与管理数据

| 路径 | 内容 |
|---|---|
| `serverconfig/codpattern/maps/builtin/frontline/m-<名称编码>/map.json` | FTL 地图定义 |
| `serverconfig/codpattern/maps/builtin/teamdeathmatch/m-<名称编码>/map.json` | TDM 地图定义 |
| `serverconfig/codpattern/maps/builtin/frontline/records/` | FTL 战绩 |
| `serverconfig/codpattern/maps/builtin/teamdeathmatch/records/` | TDM 战绩 |
| `serverconfig/codpattern/maps/defaults.json` | 当前世界的新地图默认结束点 |
| `serverconfig/codpattern/maps/.storage/` | 迁移、地图管理日志和删除归档等内部数据 |
| `data/codpattern/room-recovery.json` | 房间轮次、玩家恢复进度及待清理实体记录 |

地图目录名为 `m-` 加地图名 UTF-8 字节的十六进制编码。地图文件保存地图范围、队伍、可用复活点层与结束传送点。优先使用游戏内工具修改，避免手动重命名目录或删除管理日志。

`defaults.json` 的格式版本为 `version: 1`，结束点字段为 `matchEndTeleportPoint`。文件不存在表示未设置默认值，可通过管理页首次保存。损坏的文件不会自动修复，并会阻止全局设置读取、保存及需要读取默认值的新建地图操作；应停服备份后修复文件或恢复有效备份，再打开管理页。

## 8. 旧地图存储迁移与备份

当前地图存储跟随世界存档。程序会检测旧目录，但不会在启动时自动搬迁。

旧数据可能位于运行目录下的 `fpsmatch/<世界名称>/`，以及世界存档中的 `serverconfig/codpattern/tdm_rules/`、`tdm_match_records/`、`tactical_tdm_match_records/`。检测到旧规则、冲突或恢复问题时，相关模式可能处于不可用状态，应先检查迁移报告。

迁移步骤：

1. 停服，完整备份世界存档和运行目录中的 `fpsmatch/`。
2. 启动服务器，确保受影响的地图没有成员、观战者或进行中的对局。
3. 以权限等级 4 执行 `/cdp map migrate check`，查看源目录、目标目录和冲突报告。
4. 确认报告后执行 `/cdp map migrate confirm`。
5. 等待迁移结果，检查报告中的失败项，然后重启服务器。
6. 重启后确认地图列表、规则、复活点和结束点，再试开一局。

迁移按单元处理，先暂存并校验内容，再写入目标，核验目标成功后删除对应旧文件。冲突数据不会直接覆盖，部分失败时可能已有其他单元完成迁移。执行后受影响模式持续锁定到重启；处理失败原因后，可重新检查并继续确认迁移。

世界中的报告位于 `serverconfig/codpattern/maps/.storage/migration.json`，运行目录的迁移标记位于 `fpsmatch/.codpattern-migrations/`。请保留这些记录，不要仅复制地图 JSON 后就删除其余文件。

日常备份应复制完整世界存档。仍保留旧地图或迁移标记的服务器也要备份 `fpsmatch/`，同时记录原世界和运行目录的绝对路径。Zombies 等附属的存储迁移能力由对应附属注册，主模组不会代替未安装的附属解释其全部数据。

**已有迁移记录的存档不能直接搬到任意新路径后使用。** `migration.json` 绑定世界绝对路径，迁移文件清单也记录绝对路径，外部迁移标记按世界路径生成名称。改变路径后可能报 `Invalid or relocated migration journal`，并阻止地图加载或使用。当前 `check`／`confirm` 不会自动修复跨路径搬迁；恢复此类备份时应优先保持原绝对路径和配套目录，不要通过删除日志或标记绕过检查。

## 9. 常用命令

| 命令 | 权限等级 | 作用 |
|---|---:|---|
| `/cdp screen` | 无额外等级要求，限玩家执行 | 打开背包界面 |
| `/cdp update` | 2 | 重读筛选并同步在线玩家配置 |
| `/cdp distribute [target]` | 2 | 强制发放当前预设；省略目标时发给所有在线玩家 |
| `/cdp map list [type]` | 2 | 列出模式，或列出指定模式的地图 |
| `/cdp map delete <type> <map>` | 2 | 提交地图删除请求 |
| `/roomforceend <mode> <map>` | 2 | 强制结束当前轮次并处理恢复 |
| `/cdp map endtp show <map>` | 3 | 查看结束点；不同模式同名时提示歧义 |
| `/cdp map endtp set` | 3 | 用执行位置覆盖所有支持结束点的已有地图 |
| `/cdp map migrate check` | 4 | 检查迁移计划与冲突 |
| `/cdp map migrate confirm` | 4 | 执行迁移 |

命令中的地图名建议统一用双引号包住，例如 `/roomforceend frontline "训练场 A"`，避免中文、空格或特殊字符导致参数解析失败。`/cdp mode debug ...` 和 `/cdp test` 也保留，用于诊断；完整参数和权限见 [项目概览](README.md)。

## 10. 本地构建与验证

本仓库是独立主模组 Gradle 工程，不会自动发现、编译或打包相邻的 Zombies 附属源码。附属应在自己的仓库构建，并按附属说明开展联合开发。

构建需要完整的 `gradle/` 目录，包括 Wrapper 文件和 `build.gradle` 引用的构建脚本。当前仓库的 `.gitignore` 忽略了整个 `gradle/`，这些文件尚未纳入版本管理，因此干净克隆不能直接构建。请先补齐与源码版本一致的 `gradle/`；仅重新生成 Wrapper 仍缺少项目构建脚本。

以下命令适用于文件完整的源码目录。使用 Java 17，在仓库根目录运行：

```bash
bash ./gradlew build
bash ./gradlew runClient
bash ./gradlew runServer
```

Linux／WSL 示例通过 `bash` 运行 Wrapper，不要求脚本已有执行权限。Windows 命令提示符可将 `bash ./gradlew` 替换为 `gradlew.bat`，PowerShell 使用 `.\gradlew.bat`。构建产物位于 `build/libs/`，开发运行目录为 `run/`。

LR Tactical 默认只加入编译依赖，不加入开发运行环境。需要测试 LR 联动时显式启用：

```bash
bash ./gradlew runClient -PwithLrTactical=true
bash ./gradlew runServer -PwithLrTactical=true
bash ./gradlew runServer -PwithLrTactical=false
```

非专服开发运行会加入 Physics Mod 与 `tacz-addon`；专服、GameTest 相关入口会排除这些开发运行依赖。默认主模组验证入口为：

```bash
bash ./gradlew runMainCompatibilitySuite
bash ./gradlew runGameTestServer
```

需要独立 GameTest 运行目录时，可加 `-PgameTestRunDir=run-gametest`。这些入口验证主模组拥有的通用能力、FTL／TDM 与 PVP 行为，附属测试在附属仓库执行。
