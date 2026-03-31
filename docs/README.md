# COD Pattern

[仓库入口](../README.md) | [English README](README.en.md) | [详细 Guide](GUIDE.md) | [Q&A](QANDA.md) | [更新日志](CHANGES.md)

> 发布状态：Beta。建议先在测试环境验证后再部署到正式服务器，并提前备份世界存档、`serverconfig/codpattern/` 和 `fpsmatch/`。

## 项目概览

COD Pattern 是一个面向 **TaCZ + 内置 FPSM 兼容核心** 的模组，提供 COD 风格的：

- 背包预设与重生装备发放
- 局内武器改装与配件预设保存
- `frontline / teamdeathmatch` 的地图、房间与对局流程
- 多语言界面与系统提示文本（简中 / 繁中 / English / 日本語）

项目当前采用“服务端权威 + 客户端同步”的实现方式。背包、筛选、房间状态和对局阶段都由服务端裁定，再同步到客户端显示。

## 主要功能

### 背包管理与装备发放

- 支持背包创建、复制、重命名、删除、选择，单玩家最多 `10` 套。
- 每套背包固定 `primary / secondary / tactical / lethal` 四个槽位。
- 新玩家首次登录时自动生成 `3` 套默认背包。
- 玩家重生时自动发放当前选中的背包。
- 非强制发放逻辑只对已加入房间或对局的玩家生效。
- 管理员可用 `/cdp distribute [target]` 强制发放。

### 武器选择、筛选与改枪

- 背包武器选择由服务端校验槽位、物品 ID、NBT、武器分类和黑名单。
- 配件改装只允许 `primary` / `secondary`，并由服务端保存到 `attachmentPreset`。
- 配件黑名单会同时作用于候选列表、已装配件清理和保存拦截。
- TaCZ 原生改枪界面已全局禁用，统一走 COD Pattern 背包改枪流程。

### 房间、地图与对局流程

- 暂停菜单提供统一房间入口。
- 当前支持 `frontline` 与 `teamdeathmatch` 两种模式。
- 支持地图区域创建、出生点配置、结束传送点配置和持久化保存。
- 房间加入只允许在 `WAITING` 阶段进行。
- 支持准备状态、开始投票、结束投票、阶段流转和房间列表同步。
- `teamdeathmatch` 支持动态复活点候选与出生安全校验。
- 支持击杀播报、比分、死亡视角、复活无敌、呼吸回血、敌我高光、敌方血条和结算页。

### 持久化、兼容与本地化

- 背包数据保存在 `serverconfig/codpattern/backpack_rules/backpack_config.json`
- 武器筛选保存在 `serverconfig/codpattern/backpack_rules/weapon_filter.json`
- TDM 配置保存在 `serverconfig/codpattern/tdm_rules/config.json`
- 地图数据保存在 `<游戏目录>/fpsmatch/<世界名>/...`
- 支持 LR Tactical、Physics Mod、`tacz-addon 1.1.6`
- 已提供 `zh_cn / zh_tw / en_us / ja_jp` 语言资源

## 命令与入口

### `/cdp`

- `/cdp screen`
  - 打开背包界面，主要用于调试入口。
- `/cdp update`
  - 重新读取 `weapon_filter.json`，并把背包与筛选配置同步给所有在线玩家。
- `/cdp distribute [target]`
  - 强制发放背包装备。

### `/cdp map`

- `/cdp map list [type]`
  - 查看模式列表或某模式下的地图列表。
- `/cdp map create <frontline|teamdeathmatch> <name> <from> <to>`
  - 创建地图区域并立即保存。
- `/cdp map delete <type> <name>`
  - 删除地图及其持久化数据。
- `/cdp map spawn <list|add|remove|clear> ...`
  - 管理 `INITIAL` / `DYNAMIC_CANDIDATE` 复活点。
- `/cdp map endtp <show|set|clear> <map> [pos]`
  - 管理结算阶段结束传送点。

## 配置与目录

### `backpack_rules/backpack_config.json`

- 保存玩家背包、所选背包和槽位物品数据。
- 配件预设直接存储在槽位的 `attachmentPreset` 字段中。

### `backpack_rules/weapon_filter.json`

- 控制武器分类、黑名单、投掷物开关和弹药倍率。
- 关键字段：
  - `primaryWeaponTabs`
  - `secondaryWeaponTabs`
  - `blockedItemNamespaces`
  - `blockedWeaponIds`
  - `blockedAttachmentNamespaces`
  - `blockedAttachmentIds`
  - `throwablesEnabled`
  - `ammunitionPerMagazineMultiple`

### `tdm_rules/config.json`

当前版本真实生效的默认字段如下：

| 字段 | 默认值 | 说明 |
|---|---:|---|
| `timeLimitSeconds` | `420` | 正式阶段时长（秒） |
| `scoreLimit` | `75` | 击杀分上限 |
| `invincibilityTicks` | `30` | 复活无敌帧 |
| `respawnDelayTicks` | `40` | 复活延迟 |
| `warmupTimeTicks` | `400` | 热身阶段时长 |
| `preGameCountdownTicks` | `200` | 开局倒计时 |
| `blackoutStartTicks` | `60` | 倒计时末段黑屏 |
| `deathCamTicks` | `30` | 死亡视角时长 |
| `minPlayersToStart` | `1` | 开始投票前最少人数 |
| `votePercentageToStart` | `60` | 开始投票通过阈值 |
| `votePercentageToEnd` | `75` | 结束投票通过阈值 |
| `combatRegenDelayTicks` | `120` | 受伤后开始回血前的等待时间 |
| `combatRegenHalfHeartsPerSecond` | `5.0` | 每秒恢复半颗心数量 |
| `maxTeamDiff` | `1` | 自动分队允许的最大人数差 |
| `markerFocusHalfAngleDegrees` | `30.0` | 敌方血条判定视锥半角 |
| `markerFocusRequiredTicks` | `20` | 触发敌方血条需要的连续判定 tick |
| `markerBarMaxDistance` | `96.0` | 敌方血条最大判定距离 |
| `markerVisibleGraceTicks` | `3` | 敌方血条防闪烁缓冲 tick |

### 战绩导出目录

- `frontline` -> `serverconfig/codpattern/tdm_match_records/`
- `teamdeathmatch` -> `serverconfig/codpattern/tactical_tdm_match_records/`

## 文档导航

- 想看完整实现说明：前往 [GUIDE.md](GUIDE.md)
- 想查常见问题：前往 [QANDA.md](QANDA.md)
- 想看版本历史：前往 [CHANGES.md](CHANGES.md)

## 兼容性与依赖

- Minecraft: `1.20.1`
- Forge: `47.4.0+`
- Java: `17`
- 必需依赖: TaCZ `1.1.6+`
- 内置组件: FPSM 兼容核心，无需额外安装 `fpsmatch.jar`

## 许可证

本项目采用 **GPL-3.0-only** 许可证。详细信息请参阅根目录 `LICENSE.txt`。
