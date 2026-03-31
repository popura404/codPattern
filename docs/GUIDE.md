# COD Pattern Guide

[项目概览](README.md) | [更新日志](CHANGES.md)

> 面向服主、管理员和玩家的详细操作指南。内容以当前代码实现和仓库内样例数据为准。
> 本文基于ai撰写，内容仅供参考

## 1. 先弄清楚这个模组在管什么

COD Pattern 目前主要管理 4 件事：

1. 玩家背包预设。
2. TaCZ 枪械的局内改装入口。
3. `frontline` / `teamdeathmatch` 地图与房间流程。
4. 对局配置、筛选配置和战绩导出。

实际使用时可以把它理解成：

- `背包` 决定玩家重生时拿到什么。
- `地图` 决定房间是否存在。
- `房间` 本质上就是某张已注册地图的可加入入口。
- `TaCZ 原生改枪界面` 已被禁用，必须走 COD Pattern 的背包改装流程。

## 2. 运行环境与依赖

- Minecraft: `1.20.1`
- Forge: `47.4.0+`
- Java: `17`
- 必需依赖: `TaCZ 1.1.6+`
- 可选联动: `LR Tactical`、`Physics Mod`、`tacz-addon 1.1.6`

部署前建议同时备份两类数据：

- 世界存档目录下的 `serverconfig/codpattern/`
- 游戏根目录下的 `fpsmatch/`

第二项很重要。地图数据不在世界存档里，而是在 `fpsmatch/<世界名>/...` 下保存。

## 3. 首次部署流程

1. 把 `codpattern` 和依赖模组放进 `mods/`。
2. 启动一次游戏或服务器，让默认配置自动生成。
3. 关闭服务器后检查目录是否已经生成。

首次启动后，你至少会看到这些路径：

- `<世界>/serverconfig/codpattern/backpack_rules/backpack_config.json`
- `<世界>/serverconfig/codpattern/backpack_rules/weapon_filter.json`
- `<世界>/serverconfig/codpattern/tdm_rules/config.json`
- `<世界>/serverconfig/codpattern/tdm_match_records/`
- `<游戏目录>/fpsmatch/<世界名>/frontline/`
- `<游戏目录>/fpsmatch/<世界名>/teamdeathmatch/`

如果你使用的是当前仓库自带的开发运行目录，对应样例位置就是：

- `run/saves/新的世界/serverconfig/codpattern/...`
- `run/fpsmatch/新的世界/...`

## 4. 10 分钟开出第一张图

这是最快的一套实操顺序，建议第一次先按这个流程走通。

### 4.1 选择模式和地图名

当前可用模式：

- `frontline`
- `teamdeathmatch`

建议地图名直接用英文、数字、下划线，避免后面命令里频繁加引号。  
如果地图名里有空格，请用引号包起来。

### 4.2 创建地图区域

先站到目标维度里，用命令创建地图区域：

```mcfunction
/cdp map create teamdeathmatch factory -296 62 -92 -172 98 -38
```

含义是：

- 模式: `teamdeathmatch`
- 地图名: `factory`
- 第一个坐标: 地图区域角点 1
- 第二个坐标: 地图区域角点 2

创建成功后，这张地图会被立即保存，并作为一个房间出现在房间列表里。

### 4.3 给两边添加初始出生点

队伍名固定使用：

- `kortac`
- `specgru`

复活点类型建议先用两种：

- `INITIAL`
- `DYNAMIC_CANDIDATE`

先给双方至少各加 1 个 `INITIAL`：

```mcfunction
/cdp map spawn add teamdeathmatch factory kortac INITIAL -248 81 -52
/cdp map spawn add teamdeathmatch factory specgru INITIAL -180 64 -70
```

这类点主要用于开局、等待阶段或基础出生分配。

### 4.4 给两边补动态复活点

然后继续给双方补多个 `DYNAMIC_CANDIDATE`：

```mcfunction
/cdp map spawn add teamdeathmatch factory kortac DYNAMIC_CANDIDATE -242 86 -70
/cdp map spawn add teamdeathmatch factory kortac DYNAMIC_CANDIDATE -259 96 -41
/cdp map spawn add teamdeathmatch factory specgru DYNAMIC_CANDIDATE -186 77 -65
/cdp map spawn add teamdeathmatch factory specgru DYNAMIC_CANDIDATE -200 77 -40
```

建议每边至少放 3 到 6 个动态点，分散在己方半区和中前场，避免连续复活在同一位置。

### 4.5 设置结算传送点

如果不设置这个点，比赛结束后系统会提示，但不会自动把人送回。

```mcfunction
/cdp map endtp set factory -230 72 -60
```

这里的 `endtp` 子命令只按地图名查找，不带模式参数。  
所以不同模式下尽量不要起完全相同的地图名，否则可能触发同名歧义。

### 4.6 自检

创建完成后，至少跑一遍这些检查：

```mcfunction
/cdp map list teamdeathmatch
/cdp map spawn list teamdeathmatch factory kortac INITIAL
/cdp map spawn list teamdeathmatch factory specgru INITIAL
/cdp map spawn list teamdeathmatch factory kortac DYNAMIC_CANDIDATE
/cdp map spawn list teamdeathmatch factory specgru DYNAMIC_CANDIDATE
/cdp map endtp show factory
```

如果这些命令都能正确返回，房间就已经具备基本可玩条件。

## 5. 玩家操作指南

### 5.1 打开背包界面

玩家按 `ESC` 后，会在暂停菜单看到一个 `背包设置` 按钮。

进入后可进行这些操作：

- 左键背包: 选中该背包。
- 右键背包: 打开更多操作。
- 悬停背包: 显示 `配置` 按钮。
- 点击 `+ 添加背包`: 新增背包。

当前限制：

- 每名玩家最多 `10` 套背包。
- 新玩家默认会自动生成 `3` 套初始背包。
- 至少要保留 `1` 套背包，不能全部删空。

### 5.2 背包里能改什么

每套背包固定有 4 个槽位：

- `primary`
- `secondary`
- `tactical`
- `lethal`

其中：

- `primary` 和 `secondary` 一般放 TaCZ 枪械。
- `tactical` 和 `lethal` 一般放 LR Tactical 投掷物。

### 5.3 选择武器

在背包界面里点 `配置` 后进入武器配置界面。

操作方式：

- 点击主武器或副武器槽位，进入武器选择页。
- 按标签页切换分类。
- 点击目标武器，结果会立刻写回该背包。
- 投掷物槽位同理，但只显示投掷物列表。

武器列表不是全量显示，而是受 `weapon_filter.json` 控制。

### 5.4 改配件

主武器或副武器槽位里如果当前是合法 TaCZ 枪械，悬停时会出现 `更换配件` 按钮。

注意这几个限制：

- 只有 `primary` 和 `secondary` 支持配件改装。
- 旁观状态下不能进入改装。
- 黑名单配件无法保存。
- 保存成功后，配件预设会直接写进该背包槽位的 `attachmentPreset` 字段。

如果玩家尝试打开 TaCZ 自带改枪界面，会被拦截并引导回 COD Pattern 背包界面。

### 5.5 背包什么时候发放

正常逻辑下，玩家在重生时自动领取当前选中的背包。  
但这条逻辑默认只对已经加入房间或对局的玩家生效。

也就是说：

- 单纯进服务器但没进房间，不一定会自动拿到背包。
- 已经加入房间并参加对局，重生时会自动发放。
- 管理员可以用 `/cdp distribute` 强制发放。

### 5.6 加入房间与开始对局

玩家按 `ESC` 后，会看到 `Frontline / TeamDeathMatch` 按钮。

进入房间界面后的标准流程：

1. 在左侧选中一个房间。
2. 点击房间右侧按钮加入房间。
3. 加入后，在右侧选择 `kortac` 或 `specgru`。
4. 点击 `准备`。
5. 全员准备完成后，由任意一名房间成员发起开始投票。
6. 所有人在弹窗里投票通过后，对局开始。

当前最稳妥的使用方式是：

- 让玩家在 `WAITING` 阶段完成入房、选边、准备。
- 比赛已经开始后，新玩家不要指望一定还能正常插入当前对局。

### 5.7 对局阶段说明

完整阶段流转为：

- `WAITING`
- `COUNTDOWN`
- `WARMUP`
- `PLAYING`
- `ENDED`

一般对应体验如下：

- `WAITING`: 等人、选队、准备、发起开始投票。
- `COUNTDOWN`: 开局倒计时。
- `WARMUP`: 热身阶段。
- `PLAYING`: 正式计分阶段。
- `ENDED`: 结算和回传阶段。

### 5.8 对局中会发生什么

当前实现里已经包含：

- 击杀播报
- 队伍比分
- 死亡视角
- 复活倒计时
- 复活无敌
- 呼吸回血
- 敌方高光
- 敌方血条显示
- 结算界面与战绩导出

如果当前没有可用复活点，玩家可能会被临时留在旁观状态并等待系统重试。

## 6. 管理员命令总表

### 6.1 常用命令

```mcfunction
/cdp screen
/cdp update
/cdp distribute
/cdp distribute <玩家>
```

说明：

- `/cdp screen`
  用于调试，直接打开背包界面。
- `/cdp update`
  将当前服务器内的武器筛选和背包缓存同步到所有在线玩家客户端。
- `/cdp distribute`
  强制给所有在线玩家发当前背包。
- `/cdp distribute <玩家>`
  强制给指定玩家发当前背包。

### 6.2 地图命令

```mcfunction
/cdp map list
/cdp map list <模式>
/cdp map create <frontline|teamdeathmatch> <名称> <起点> <终点>
/cdp map delete <模式> <名称>
/cdp map spawn list <模式> <地图> <队伍> <INITIAL|DYNAMIC_CANDIDATE>
/cdp map spawn add <模式> <地图> <队伍> <INITIAL|DYNAMIC_CANDIDATE> <坐标>
/cdp map spawn remove <模式> <地图> <队伍> <INITIAL|DYNAMIC_CANDIDATE> <序号>
/cdp map spawn clear <模式> <地图> <队伍> <INITIAL|DYNAMIC_CANDIDATE>
/cdp map endtp show <地图>
/cdp map endtp set <地图> <坐标>
/cdp map endtp clear <地图>
```

推荐约定：

- 模式只用 `frontline` 或 `teamdeathmatch`
- 队伍只用 `kortac` 或 `specgru`
- 复活点类型只用 `INITIAL` 或 `DYNAMIC_CANDIDATE`

## 7. 可选的工具流建图方式

如果你不想每次都手打坐标，可以直接用工具物品。

### 7.1 地图创建工具

给自己工具：

```mcfunction
/give @s codpattern:map_creator_tool
```

用法：

- 左键方块: 记录区域点 1
- 右键方块: 记录区域点 2
- `Ctrl + 右键`: 打开工具界面，设置模式和地图名，并执行创建

### 7.2 复活点工具

给自己工具：

```mcfunction
/give @s codpattern:spawn_point_tool
```

用法：

- `Ctrl + 右键`: 打开工具界面，选择模式、地图、队伍、复活点类型
- 左键方块: 在所点方块上方记录一个复活点

这个工具会自动做几项校验：

- 当前维度必须和地图所在维度一致
- 复活点必须在地图区域内
- 不能重复添加同一坐标点

如果你是第一次部署，仍然建议先把命令流走通，再改用工具流。

## 8. 配置文件说明

### 8.1 `backpack_config.json`

路径：

- `<世界>/serverconfig/codpattern/backpack_rules/backpack_config.json`

作用：

- 保存每个玩家的背包数据
- 保存当前选择的背包编号
- 保存槽位里的武器、NBT 和配件预设

重点字段：

- `playerData`
  以玩家 UUID 为键。
- `selectedBackpack`
  当前选中的背包编号。
- `backpacks_MAP`
  玩家持有的全部背包。
- `item_MAP`
  单个背包下的 4 个槽位内容。
- `attachmentPreset`
  主武器或副武器的配件预设，通常由系统自动生成。

建议：

- 不要在服务器运行中手改这个文件。
- 如果确实要人工批量修改，先停服，改完再开服。
- 不要把 `/cdp update` 当成这个文件的可靠热重载命令；它更适合做在线同步，不适合替代停服重载。
- 手改时主要关注 `playerData` 区域即可，下面那几组 `itemDataP1`、`itemDataS1` 之类更像默认模板。

### 8.2 `weapon_filter.json`

路径：

- `<世界>/serverconfig/codpattern/backpack_rules/weapon_filter.json`

作用：

- 控制玩家在背包界面里能看到什么枪和什么配件
- 控制投掷物是否启用
- 控制枪械发放时的弹药倍率

常用字段：

- `primaryWeaponTabs`
  主武器分类页。
- `secondaryWeaponTabs`
  副武器分类页。
- `blockedItemNamespaces`
  按命名空间屏蔽整包枪。
- `blockedWeaponIds`
  精确屏蔽某把枪，格式 `namespace:gunid`。
- `blockedAttachmentNamespaces`
  按命名空间屏蔽整包配件。
- `blockedAttachmentIds`
  精确屏蔽某个配件，格式 `namespace:attachmentid`。
- `throwablesEnabled`
  是否允许投掷物槽位生效。
- `ammunitionPerMagazineMultiple`
  发放枪械时的备弹倍率。

如果你修改的是这个文件：

1. 保存文件。
2. 让管理员执行 `/cdp update`。

这能把最新筛选规则重新同步给在线玩家。

### 8.3 `tdm_rules/config.json`

路径：

- `<世界>/serverconfig/codpattern/tdm_rules/config.json`

作用：

- 控制对局节奏、计分、复活、投票和敌方血条判定

最常调的字段：

- `timeLimitSeconds`
- `scoreLimit`
- `respawnDelayTicks`
- `invincibilityTicks`
- `warmupTimeTicks`
- `preGameCountdownTicks`
- `minPlayersToStart`
- `votePercentageToStart`
- `votePercentageToEnd`
- `maxTeamDiff`

对局观感相关字段：

- `markerFocusHalfAngleDegrees`
- `markerFocusRequiredTicks`
- `markerBarMaxDistance`
- `markerVisibleGraceTicks`

这个文件在服务端启动时加载。  
如果你手改了它，最稳妥的方式是重启服务器，不要指望在线热更新。

### 8.4 地图文件

路径：

- `<游戏目录>/fpsmatch/<世界名>/frontline/<地图名>.json`
- `<游戏目录>/fpsmatch/<世界名>/teamdeathmatch/<地图名>.json`

这里保存的是：

- 地图区域
- 队伍人数上限
- `INITIAL` 出生点
- `DYNAMIC_CANDIDATE` 出生点
- 结束传送点

如果你迁移服务器，只复制世界存档不够，还要把 `fpsmatch/` 一起复制。

### 8.5 战绩导出

路径：

- `<世界>/serverconfig/codpattern/tdm_match_records/`

每局结束后会自动写出一个 JSON，内容通常包括：

- 地图名
- 开始时间和结束时间
- 持续秒数
- 胜利队伍
- 双方分数
- 参赛玩家 K/D

## 9. 推荐的开服检查清单

正式上线前建议逐项确认：

- TaCZ 已安装，版本不低于 `1.1.6`
- 至少创建了 1 张地图
- 双方都配置了 `INITIAL` 复活点
- 双方都配置了足够的 `DYNAMIC_CANDIDATE`
- 已设置 `endtp`
- `weapon_filter.json` 已按你的枪包策略调整
- `tdm_rules/config.json` 已按服务器节奏调整
- 已备份世界存档和 `fpsmatch/`

## 10. 常见问题

### 10.1 玩家重生后没拿到背包

先检查这几个点：

- 玩家是不是还没加入房间
- 当前玩家是不是旁观状态
- `weapon_filter.json` 是否把对应物品屏蔽掉了
- 管理员是否可以用 `/cdp distribute <玩家>` 强制补发

### 10.2 房间列表里没有地图

常见原因：

- 地图根本没创建成功
- 地图文件没有被正确保存在 `fpsmatch/`
- 迁移服务器时漏拷了 `fpsmatch/`

### 10.3 比赛结束后没有自动传回

大概率是没设置结束传送点，执行：

```mcfunction
/cdp map endtp show <地图名>
/cdp map endtp set <地图名> <坐标>
```

### 10.4 玩家总是复活失败或被卡旁观

优先检查：

- 地图是否缺少动态复活点
- 动态点是否全放在危险区
- 地图区域和复活点维度是否一致

### 10.5 配件保存失败

常见原因：

- 玩家当时是旁观
- 当前槽位不是枪械
- 当前改装里含有被黑名单禁用的配件

### 10.6 使用 `tacz-addon` 时卸载配件异常

先确认：

```mcfunction
/gamerule liberateAttachment false
```

## 11. 一句话总结推荐实践

如果你想稳定开服，按这条线执行就够了：

1. 先开服生成配置。
2. 先用命令把地图、出生点和结束传送点配齐。
3. 再调 `weapon_filter.json` 和 `tdm_rules/config.json`。
4. 最后让玩家进房、选边、准备、投票开始。

这样最不容易把问题混在一起。
