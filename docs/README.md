# COD Pattern

[English README](README.en.md)

* > ****发布状态：Beta。建议先在测试环境验证后再部署到正式服务器，并提前备份存档与配置。***

## 项目概览

COD Pattern 是一个面向 **TaCZ + 内置 FPSM 兼容核心** 的模组，提供 COD 风格的：

* 背包预设与重生装备发放
* 局内武器改装与配件预设
* `Frontline / TeamDeathMatch` 房间、地图与对局流程
* 多语言界面与系统提示文本（简中 / 繁中 / English / 日本語）

项目当前采用“服务端权威 + 客户端同步”的实现思路，优先保证多人联机下的配置一致性与流程稳定性。

## 主要功能

### 1) 背包管理与装备发放

* 支持背包的创建、复制、重命名、删除、选择；单玩家最多 `10` 套背包。
* 每套背包支持 `primary / secondary / tactical / lethal` 四个槽位。
* 背包系列界面已针对不同 `GUI Scale` 采用固定文字缩放基准，降低高低缩放下的文字失衡与可读性波动。
* 默认背包名、复制后缀与装备提示等文本会随客户端语言自动本地化。
* 玩家重生时自动发放所选背包（常规逻辑仅作用于已加入房间/对局的玩家）。
* 玩家进服时自动同步背包与筛选配置，避免重连后状态丢失。
* 管理员可通过命令触发“全服或指定玩家”即时发放。

### 2) 局内武器改装与配件预设

* 支持在背包配置界面中编辑主武器、副武器与投掷物槽位。
* 基于 TaCZ 的枪械能力执行配件改装，由服务端完成校验与落盘。
* 配件预设现直接并入背包配置，通过背包编号与槽位进行管理。
* 支持改装结果回写与异常回滚，降低客户端与服务端状态偏差。

### 3) 房间、地图与对局流程（内置 FPSM 兼容层）

* 暂停菜单提供统一房间入口，可进行房间浏览、加入、离开与队伍选择。
* 支持 `frontline / teamdeathmatch` 两种模式，统一纳入房间系统、地图数据与持久化管理。
* 地图现支持区域创建、出生点配置、结束传送点配置与持久化保存。
* 房间界面已针对不同 `GUI Scale` 采用固定文字缩放基准，保持列表、信息面板与操作按钮的文字观感一致。
* 支持自动分队与人数差约束（`maxTeamDiff`）。
* 支持准备状态、开始投票、结束投票（含通过阈值与超时机制）。
* 对局阶段完整流转：`WAITING -> COUNTDOWN -> WARMUP -> PLAYING -> ENDED`。
* `teamdeathmatch` 模式支持动态复活点候选与出生安全校验。
* 支持击杀播报、击杀计分、复活延迟、无敌帧、呼吸回血、死亡视角、阶段 HUD、敌我高光与敌方血条显示。
* 结算信息包含 MVP/SVP 与全员战绩展示，并自动导出 JSON 战绩记录。

### 4) 筛选、兼容与本地化

* 武器筛选支持主副武器标签控制（`primaryWeaponTabs` / `secondaryWeaponTabs`）。
* 支持枪包命名空间黑名单与精确武器黑名单过滤（`blockedItemNamespaces` / `blockedWeaponIds`）。
* 支持配件命名空间黑名单与精确配件黑名单过滤（`blockedAttachmentNamespaces` / `blockedAttachmentIds`）。
* 支持投掷物开关与弹药倍率配置（`throwablesEnabled` / `ammunitionPerMagazineMultiple`）。
* 支持 LR Tactical 与 Physics Mod 联动；未加载时保持主流程可用。
* 已适配 `tacz-addon 1.1.6` 在背包改装场景下的卸载冲突，避免“卸载按钮卡住/无法拆卸”的问题。
* 已全局禁用 TaCZ 原生改枪界面，统一改为通过 COD Pattern 背包改装流程操作。
* 已提供 `zh_cn / zh_tw / en_us / ja_jp` 本地化资源，覆盖主要界面、提示与错误信息。

## 命令与入口

### `/cdp` 命令

* `/cdp screen`
  * 打开背包界面（调试入口）。
* `/cdp update`
  * 同步武器筛选与背包配置到所有在线玩家（需 OP 权限）。
* `/cdp distribute [target]`
  * 强制发放背包装备到全部在线玩家或指定玩家（需 OP 权限）。

### `/cdp map` 命令链

* `/cdp map list [type]`
  * 查看已注册模式或指定模式下的地图列表。
* `/cdp map create <frontline|teamdeathmatch> <名称> <起点> <终点>`
  * 创建地图区域并立即保存。
* `/cdp map delete <type> <名称>`
  * 删除指定地图及其持久化数据。
* `/cdp map spawn <list|add|remove|clear> ...`
  * 管理队伍出生点与动态复活点候选。
* `/cdp map endtp <show|set|clear> <地图> [坐标]`
  * 管理结算阶段结束传送点。

## 配置

服务端配置目录位于世界存档下 `serverconfig/codpattern/`：

* `backpack_rules/backpack_config.json`
  * 玩家背包数据（JSON）。
  * 配件预设现已直接并入每个背包槽位的 `attachmentPreset` 字段。
* `backpack_rules/weapon_filter.json`
  * 武器筛选配置（JSON）。
  * 关键字段：
    * `primaryWeaponTabs` / `secondaryWeaponTabs`
    * `blockedItemNamespaces`
    * `blockedWeaponIds`（格式：`namespace:gunid`）
    * `blockedAttachmentNamespaces`
    * `blockedAttachmentIds`（格式：`namespace:attachmentid`）
    * `throwablesEnabled`
    * `ammunitionPerMagazineMultiple`
* `tdm_rules/config.json`
  * TDM 流程参数（时间、分数、复活、投票、加入策略、平衡策略等）。
  * 局内敌我显示现固定为高光 + 敌方血条，已移除旧的标识点样式切换项。
* `tdm_match_records/`
  * 对局结束后导出的战绩记录（`.json`）。
* 旧路径 `backpackconfig` / `filterconfig` / `attachment_preset/` / `tdmconfig/`
  * 已废弃，当前版本不再兼容读取，旧存档需要人工迁移。

### `tdm_rules/config.json` 默认参数

| 字段 | 默认值 | 说明 |
|---|---:|---|
| `timeLimitSeconds` | `420` | 对局时长（秒） |
| `scoreLimit` | `75` | 胜利击杀数 |
| `invincibilityTicks` | `30` | 复活无敌帧（tick） |
| `respawnDelayTicks` | `40` | 复活延迟（tick） |
| `warmupTimeTicks` | `400` | 热身阶段时长（tick） |
| `preGameCountdownTicks` | `200` | 开局倒计时（tick） |
| `blackoutStartTicks` | `60` | 开局黑屏提示时长（tick） |
| `deathCamTicks` | `30` | 死亡视角时长（tick） |
| `minPlayersToStart` | `1` | 最低开局人数 |
| `votePercentageToStart` | `60` | 开始投票通过阈值（%） |
| `votePercentageToEnd` | `75` | 结束投票通过阈值（%） |
| `combatRegenDelayTicks` | `120` | 受伤后开始回血前的等待时间（tick） |
| `combatRegenHalfHeartsPerSecond` | `5.0` | 每秒回复的半颗心数量 |
| `allowJoinDuringPlaying` | `true` | 进行中是否允许加入 |
| `joinAsSpectatorWhenPlaying` | `true` | 进行中加入是否先以旁观进入 |
| `maxTeamDiff` | `1` | 队伍人数允许最大差值 |
| `markerFocusHalfAngleDegrees` | `30.0` | 敌方血条判定视锥半角（度） |
| `markerFocusRequiredTicks` | `20` | 视锥内持续判定触发敌方血条所需 tick |
| `markerBarMaxDistance` | `96.0` | 敌方血条判定最大距离（格） |
| `markerVisibleGraceTicks` | `3` | 敌方血条可见时的防闪烁缓冲 tick |

## 兼容性与依赖

* **Minecraft:** `1.20.1`
* **Forge:** `47.4.0+`
* **Java:** `17`
* **必需依赖:**
  * TaCZ `1.1.6+`
* **内置组件:**
  * FPSM 兼容核心（无需额外安装 `fpsmatch.jar`）
* **可选联动:**
  * LR Tactical（投掷物/近战资源）
  * Physics Mod（死亡实体物理表现）
  * tacz-addon `1.1.6`（背包改装流程已做兼容处理）

## 部署建议

* 非强制发放逻辑仅对“已加入房间/对局”的玩家生效。
* 若地图未设置 TDM 结束传送点，结算阶段会提示但不会自动回传。
* 若启用 `tacz-addon` 并在改装界面出现卸载异常，建议确认：`/gamerule liberateAttachment false`。
* 上线前建议完成以下检查：
  * `tdm_rules/config.json` 是否符合服务器节奏
  * `backpack_rules/weapon_filter.json` 是否符合枪包筛选策略
  * 地图是否已配置队伍出生点与结束传送点

## 问题反馈

如遇到 Bug 或兼容性问题，请前往本项目 Issues 反馈，并附带：

* 复现步骤
* 日志片段
* 模组列表与版本

## 更新日志

当前版本：`v0.6.5b`  
详细更新请参阅 `CHANGES.md`。

## 许可证

本项目采用 **GPL-3.0-only** 许可证。详细信息请参阅根目录 `LICENSE.txt`。

## 作者信息

* **作者:** popura404
* **联系方式:** `gzyoung2330351551@163.com`
