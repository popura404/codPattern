# COD Pattern

[仓库入口](../README.md) | [English README](README.en.md) | [详细 Guide](GUIDE.md) | [Q&A](QANDA.md) | [更新日志](CHANGES.md)

> 发布状态：Beta，本文对应 `0.8.6b`。旧世界升级前请备份世界存档和游戏目录下的 `fpsmatch/`，迁移步骤见 [GUIDE.md](GUIDE.md)。

## 项目概览

COD Pattern 是一个面向 **TaCZ + 内置 FPSM 兼容核心** 的 Forge 模组，提供 COD 风格的背包预设、武器改装、房间管理和团队对抗玩法。

主模组独立提供 `frontline`（FTL）与 `teamdeathmatch`（TDM）。Zombies 已拆分为独立 addon，主模组不包含其玩法实现，也不依赖 addon 才能启动。安装 addon 后，可通过主模组的统一入口使用它注册的模式。

背包、武器筛选、房间状态、地图编辑和对局阶段均由服务端校验，再同步给客户端。已提供简中、繁中、English 和日本語语言资源。

## 主要功能

### 背包管理与装备发放

- 支持背包创建、复制、重命名、删除和选择，每名玩家最多 `10` 套。
- 新玩家首次初始化背包数据时生成 `3` 套默认背包。
- 每套背包保存 `primary / secondary / tactical / lethal` 四类装备。
- 加入房间后的装备发放和复活补给使用当前选择的背包；不同模式可接管自己的发放规则。
- 管理员可通过 `/cdp distribute [target]` 强制发放，省略目标时作用于全部在线玩家。
- LR Tactical 未安装时，相关近战、投掷物候选、投掷物专用槽和默认投掷物发放自动禁用。安装 LR 后，投掷物仍受服务端筛选开关限制。

### 武器选择、筛选与改枪

- 服务端校验装备槽位、物品 ID、NBT、武器分类与黑名单。
- 主武器和副武器支持配件改装，配件预设随背包保存到 `attachmentPreset`。
- 配件黑名单同时作用于候选列表、已装配件清理和保存校验。
- TaCZ 原生改枪界面已禁用，统一使用 COD Pattern 的背包改枪界面。
- 配件候选结合玩家持有的配件与 `tacz-addon` 兼容候选；最终是否允许安装仍由服务端判定。

### 房间与对局流程

- 暂停菜单提供房间入口，可查看地图、加入房间、选择队伍和准备。
- FTL／TDM 房间仅在 `WAITING` 阶段接受加入，支持开始投票、结束投票及阶段同步。
- FTL 与 TDM 使用各自的复活规则；TDM 支持动态候选出生点与候选点合并。
- 支持热身、开局倒计时、比分与击杀播报、死亡视角、复活无敌、呼吸回血、敌我高光、世界空间敌方血条和结算页。
- 开始投票前检查是否已配置结束传送点；实际传送时若落点不可用，再给出传送失败提示。
- 强制结束会执行玩家恢复与模式资源清理，恢复未完成的房间会显示对应状态。

### 地图创建与管理

- 地图创建工具用于选择范围并创建 FTL／TDM 地图。
- 出生点工具按模式显示可用的点位层和区域层，用于预览、添加、删除、清空及动态候选合并。
- 地图管理工具集中提供列表筛选、范围与维度信息、状态查看、改名、结束点设置、强制结束和删除。
- 改名要求地图空闲且相关恢复已完成，同时更新地图身份与存储目录；操作前会校验名称和冲突。
- 删除先结束对局、恢复并移出成员与观战者，清理完成后归档地图目录并注销地图。恢复未完成时保留地图，可在管理页查看进度、重试或取消。
- 单张地图的结束点、后续新建地图使用的全局默认结束点、已有地图的命令批量设置分别保存和操作。修改全局默认不会覆盖已有地图。

## 命令与入口

下表中的权限等级是服务端命令权限等级。游戏内地图工具要求等级 `2`；地图管理页中的结束点设置也要求等级 `2`。

### `/cdp`

| 命令 | 权限 | 作用 |
|---|---:|---|
| `/cdp test` | 无额外限制 | 向执行玩家显示测试消息 |
| `/cdp screen` | 无额外限制 | 为执行玩家打开背包界面 |
| `/cdp update` | `2` | 重读武器筛选配置，向在线玩家同步筛选与背包数据 |
| `/cdp distribute [target]` | `2` | 强制发放背包；省略目标时发放给全部在线玩家 |
| `/cdp mode debug room` | `2` | 查看执行玩家所在房间 |
| `/cdp mode debug entities <room>` | `2` | 查看房间所属实体 |
| `/cdp mode debug clear_entities <room>` | `2` | 清理房间所属实体 |
| `/cdp mode debug state <room>` | `2` | 查看模式运行状态，限玩家执行 |
| `/cdp mode debug areas <type> <map>` | `2` | 查看地图区域层信息 |

`<room>` 使用 `模式|地图名` 并加引号，如 `"frontline|arena"`。命令中的地图名建议统一用双引号包住，如 `"训练场"` 或 `"训练场 A"`，避免中文、空格或特殊字符导致参数解析失败。装备发放会清空并重建接收者的物品栏，观战者不发放。

### `/cdp map`

| 命令 | 权限 | 作用 |
|---|---:|---|
| `/cdp map list [type]` | `2` | 列出已注册模式，或指定模式下的地图 |
| `/cdp map delete <type> <map>` | `2` | 提交地图删除，恢复与清理进度可在管理页查看 |
| `/cdp map endtp show <map>` | `3` | 查看指定地图的结束传送点；不同模式存在同名地图时会提示歧义 |
| `/cdp map endtp set` | `3` | 用执行位置、维度和水平朝向批量覆盖全部支持结束点的已有地图 |
| `/cdp map migrate check` | `4` | 检查旧存储并列出迁移计划与冲突 |
| `/cdp map migrate confirm` | `4` | 重新检查并执行可迁移部分，完成后需要重启 |

`endtp set` 不设置全局默认；要给以后创建的地图设置默认位置，请使用地图管理页。

### 强制结束与地图工具

- `/roomforceend <mode> <map>`：权限等级 `2`，强制结束对局并恢复玩家，保留房间成员；地图管理页也提供此操作。
- `codpattern:map_management_tool`：右键打开地图管理页。
- `codpattern:map_creator_tool`：左键和右键分别选择两个角点，`Ctrl + 右键` 打开创建界面，选择模式并填写地图名。
- `codpattern:spawn_point_tool`：`Ctrl + 右键` 打开设置界面，选择地图、团队及点位层或区域层后编辑。
- 地图创建、出生点和区域编辑统一使用工具；旧的对应命令已移除。Zombies 地图部署使用 addon 提供的工具。

## 配置与目录

以下路径都相对于当前世界目录，而不是游戏根目录。

### `serverconfig/codpattern/backpack_rules/`

- `backpack_config.json`：玩家背包、所选背包与槽位物品数据，配件预设保存在槽位的 `attachmentPreset` 中。
- `weapon_filter.json`：武器分类、物品与配件黑名单、投掷物开关及弹药倍率。

筛选配置的主要字段为 `primaryWeaponTabs`、`secondaryWeaponTabs`、`blockedItemNamespaces`、`blockedWeaponIds`、`blockedAttachmentNamespaces`、`blockedAttachmentIds`、`throwablesEnabled` 和 `ammunitionPerMagazineMultiple`。修改后可用 `/cdp update` 重读并同步；该命令不是全部配置的通用重载命令，背包部分同步的是内存数据，不会重新读取磁盘上的 `backpack_config.json`。

### `serverconfig/codpattern/maps/`

| 路径 | 内容 |
|---|---|
| `defaults.json` | 后续新建地图使用的全局结束点默认值 |
| `builtin/rules/config.json` | FTL／TDM 共用对局配置 |
| `builtin/frontline/m-<编码>/map.json` | FTL 地图定义 |
| `builtin/teamdeathmatch/m-<编码>/map.json` | TDM 地图定义 |
| `builtin/frontline/records/` | FTL 战绩导出 |
| `builtin/teamdeathmatch/records/` | TDM 战绩导出 |
| `.storage/` | 迁移记录、管理操作记录和归档数据 |

地图目录名中的编码是地图名 UTF-8 字节的十六进制表示；改名请使用管理页。addon 的地图和规则存放位置由对应模式注册，不能假定所有模式都在 `builtin/` 下。

旧版 `fpsmatch/` 地图和 `serverconfig/codpattern/tdm_rules/config.json` 通过迁移流程转入新目录。先备份，再运行 `check` 查看源路径、目标路径和冲突；确保相关房间无人且对局结束后执行 `confirm`。迁移涉及的模式会保持锁定，直到重启服务端或重新进入单人世界。具体恢复方式见 [GUIDE.md](GUIDE.md)。

### FTL／TDM 对局配置

`builtin/rules/config.json` 中已用于对局的参数默认值如下，时间字段中 `Ticks` 为游戏刻。保留字段 `warmupTimeTicks`、`preGameCountdownTicks` 和 `blackoutStartTicks` 当前不控制对应的 PvP 阶段时序，详见 [GUIDE.md](GUIDE.md)。

| 字段 | 默认值 | 说明 |
|---|---:|---|
| `timeLimitSeconds` | `420` | 正式阶段时长（秒） |
| `scoreLimit` | `75` | 击杀分上限 |
| `invincibilityTicks` | `30` | 复活无敌时间 |
| `respawnDelayTicks` | `40` | 复活延迟 |
| `deathCamTicks` | `30` | 死亡视角时长 |
| `minPlayersToStart` | `1` | 开始投票最少人数，当前默认为测试用途 |
| `votePercentageToStart` | `60` | 开始投票通过百分比 |
| `votePercentageToEnd` | `75` | 结束投票通过百分比 |
| `combatRegenDelayTicks` | `120` | 受伤后开始回血的等待时间 |
| `combatRegenHalfHeartsPerSecond` | `5.0` | 每秒恢复的半颗心数量 |
| `maxTeamDiff` | `1` | 自动分队、指定入队和换队允许的最大人数差 |
| `markerFocusHalfAngleDegrees` | `30.0` | 敌方血条判定视锥半角 |
| `markerFocusRequiredTicks` | `20` | 敌方血条显示所需判定时间 |
| `markerBarMaxDistance` | `96.0` | 敌方血条最大判定距离 |
| `markerVisibleGraceTicks` | `3` | 敌方血条防闪烁缓冲时间 |

## 文档导航

- 安装、建图、对局、地图管理、存储迁移与构建：[GUIDE.md](GUIDE.md)
- 常见问题与排查：[QANDA.md](QANDA.md)
- 版本历史：[CHANGES.md](CHANGES.md)
- 英文说明：[README.en.md](README.en.md)

## 兼容性与依赖

- Minecraft：开发和构建目标为 `1.20.1`。
- Forge：项目构建使用 `47.4.0`；模组元数据声明的最低版本为 `47`。其他组合应自行验证，不将声明范围视为完整测试范围。
- Java：`17`。
- 必需依赖：TaCZ `1.1.6+`，客户端与服务端均需安装。
- 内置组件：FPSM 兼容核心，无需额外安装 `fpsmatch.jar`。
- 可选联动：LR Tactical `0.3.0+`。缺少 LR 不影响主模组的 FTL／TDM 运行。
- 可选模式：`codpattern_zombies` addon。主模组声明接受 `0.2.0b+`，还需同时满足 addon 自身的依赖要求。主模组与 addon 分开构建、分开安装。
- Physics Mod 与 `tacz-addon` 出现在客户端开发运行配置中，不是主模组声明的必需依赖。

客户端与服务端应使用一致的主模组及所需 addon 组合。源码构建和可选 LR 的开发启动方式见 [GUIDE.md](GUIDE.md)。

## 许可证

本项目采用 **GPL-3.0-only** 许可证，详见根目录 [LICENSE.txt](../LICENSE.txt)。相关致谢见 [CREDITS.txt](../CREDITS.txt)。
