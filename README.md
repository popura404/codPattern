# COD Pattern

[English README](README.en.md)

* > ****发布状态：Beta。建议先在测试环境验证后再部署到正式服务器，并提前备份存档与配置。***

## 项目概览

COD Pattern 是一个面向 **TaCZ + FPSMatch** 的附属模组，提供 COD 风格的：

* 背包预设与重生装备发放
* 局内武器改装与配件预设
* TDM（团队死斗）房间与对局流程

项目当前采用“服务端权威 + 客户端同步”的实现思路，优先保证多人联机下的配置一致性与流程稳定性。

## 主要功能

### 1) 背包管理与装备发放

* 支持背包的创建、复制、重命名、删除、选择；单玩家最多 `10` 套背包。
* 每套背包支持 `primary / secondary / tactical / lethal` 四个槽位。
* 玩家重生时自动发放所选背包（常规逻辑仅作用于已加入房间/对局的玩家）。
* 玩家进服时自动同步背包与筛选配置，避免重连后状态丢失。
* 管理员可通过命令触发“全服或指定玩家”即时发放。

### 2) 局内武器改装与配件预设

* 支持在背包配置界面中编辑主武器、副武器与投掷物槽位。
* 基于 TaCZ 的枪械能力执行配件改装，由服务端完成校验与落盘。
* 支持按“玩家 UUID + 背包编号 + 槽位”管理配件预设（`.snbt`）。
* 支持改装结果回写与异常回滚，降低客户端与服务端状态偏差。

### 3) TDM 房间与对局流程（FPSMatch 适配）

* 暂停菜单提供“团队竞技”入口，可进行房间浏览、加入、离开与队伍选择。
* 支持自动分队与人数差约束（`maxTeamDiff`）。
* 支持准备状态、开始投票、结束投票（含通过阈值与超时机制）。
* 对局阶段完整流转：`WAITING -> COUNTDOWN -> WARMUP -> PLAYING -> ENDED`。
* 支持击杀计分、复活延迟、无敌帧、死亡视角、阶段 HUD 与结算页。
* 结算信息包含 MVP/SVP 与全员战绩展示，并自动导出 JSON 战绩记录。

### 4) 筛选与兼容能力

* 武器筛选支持主副武器标签控制（`primaryWeaponTabs` / `secondaryWeaponTabs`）。
* 支持枪包命名空间黑名单过滤（`blockedItemNamespaces`）。
* 支持投掷物开关与弹药倍率配置（`throwablesEnabled` / `ammunitionPerMagazineMultiple`）。
* 支持 LR Tactical 与 Physics Mod 联动；未加载时保持主流程可用。

## 命令与入口

### `/cdp` 命令

* `/cdp screen`
  * 打开背包界面（调试入口）。
* `/cdp update`
  * 同步武器筛选与背包配置到所有在线玩家（需 OP 权限）。
* `/cdp distribute [target]`
  * 强制发放背包装备到全部在线玩家或指定玩家（需 OP 权限）。

### FPSMatch 命令链

* TDM 地图与流程管理请使用 `/fpsm tdm ...`（例如 `/fpsm tdm create <mapName>`）。
* 旧的 `/codtdm` 命令已标记弃用。

## 配置

服务端配置目录位于世界存档下 `serverconfig/codpattern/`：

* `backpackconfig`
  * 玩家背包数据（JSON，文件名无扩展名）。
* `filterconfig`
  * 武器筛选配置（JSON，文件名无扩展名）。
  * 关键字段：
    * `primaryWeaponTabs` / `secondaryWeaponTabs`
    * `blockedItemNamespaces`
    * `throwablesEnabled`
    * `ammunitionPerMagazineMultiple`
* `attachment_preset/`
  * 配件预设（按玩家 UUID 与背包槽位保存 `.snbt` 文件）。
* `tdmconfig/config.json`
  * TDM 流程参数（时间、分数、复活、投票、加入策略、平衡策略等）。
* `tdm_match_records/`
  * 对局结束后导出的战绩记录（`.json`）。

### `tdmconfig/config.json` 默认参数

| 字段 | 默认值 | 说明 |
|---|---:|---|
| `timeLimitSeconds` | `420` | 对局时长（秒） |
| `scoreLimit` | `75` | 胜利击杀数 |
| `invincibilityTicks` | `10` | 复活无敌帧（tick） |
| `respawnDelayTicks` | `40` | 复活延迟（tick） |
| `warmupTimeTicks` | `400` | 热身阶段时长（tick） |
| `preGameCountdownTicks` | `200` | 开局倒计时（tick） |
| `blackoutStartTicks` | `60` | 开局黑屏提示时长（tick） |
| `deathCamTicks` | `30` | 死亡视角时长（tick） |
| `minPlayersToStart` | `1` | 最低开局人数 |
| `votePercentageToStart` | `60` | 开始投票通过阈值（%） |
| `votePercentageToEnd` | `75` | 结束投票通过阈值（%） |
| `allowJoinDuringPlaying` | `true` | 进行中是否允许加入 |
| `joinAsSpectatorWhenPlaying` | `true` | 进行中加入是否先以旁观进入 |
| `maxTeamDiff` | `1` | 队伍人数允许最大差值 |

## 兼容性与依赖

* **Minecraft:** `1.20.1`
* **Forge:** `47.4.0+`
* **Java:** `17`
* **必需依赖:**
  * TaCZ `1.1.6+`
  * FPSMatch `1.2.5+`
* **可选联动:**
  * LR Tactical（投掷物/近战资源）
  * Physics Mod（死亡实体物理表现）

## 部署建议

* 非强制发放逻辑仅对“已加入房间/对局”的玩家生效。
* 若地图未设置 TDM 结束传送点，结算阶段会提示但不会自动回传。
* 上线前建议完成以下检查：
  * `tdmconfig/config.json` 是否符合服务器节奏
  * `filterconfig` 是否符合枪包筛选策略
  * 地图是否已配置队伍出生点与结束传送点

## 问题反馈

如遇到 Bug 或兼容性问题，请前往本项目 Issues 反馈，并附带：

* 复现步骤
* 日志片段
* 模组列表与版本

## 更新日志

当前版本：`v0.4.5b`  
详细更新请参阅 `CHANGES.md`。

## 许可证

本项目采用 **GPLv3** 许可证。详细信息请参阅根目录 `LICENSE.txt`。

## 作者信息

* **作者:** popura404
* **联系方式:** `gzyoung2330351551@163.com`
