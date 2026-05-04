# 协作僵尸模式初版玩法方案

版本：草案 0.7（执行关系补充版）

本文档只定义玩法、数据和运行逻辑，不绑定具体源码文件。僵尸模式应接入当前通用模式框架，但不能写成 TDM 特例；房间准备、投票和部分展示能力复用通用能力，不复用 TDM 的队伍比分、重生语义或阶段 packet。

本文是权威玩法规格，不是多 agent 的唯一执行任务书。并行开发时必须同时阅读 `docs/ZOMBIES_MODE_AI_AGENT_DEVELOPMENT_PLAN.md`；文件所有权、分工顺序、共享注册表修改和阶段交付边界以该执行计划为准。测试用例和阶段验收以 `docs/ZOMBIES_TEST_MATRIX.md` 为准。

## 阅读路径

- 先读 `1-3`：确认模式目标、硬规则、开局和波次状态机。
- 实现运行时读 `4-6`：玩家状态、波次怪物、经济奖励。
- 实现地图和工具读 `7`、`8` 和附录 A：地图 schema、对象规则、部署工具 GUI。
- 实现客户端读 `9`、`10`：背包、武器状态、HUD 同步。
- 排期和验收读 `11-14`：模块边界、性能要求、里程碑和最终标准。
- 多 agent 分工开发读 `docs/ZOMBIES_MODE_AI_AGENT_DEVELOPMENT_PLAN.md`：包结构、owner、共享文件、阶段依赖和 Definition of Done。

## 1. 目标与范围

### 1.1 设计目标

- 做“协作波次生存 + 编号屏障推进 + 购买成长”的独立 PvE 模式。
- 初版房间固定最多 4 名玩家，4 人上限硬编码到模式定义、加入校验和房间展示逻辑中。
- 同一张僵尸地图同一时间只能被一个僵尸房间占用；不同地图上的房间运行态完全独立。
- 一局必须能完整开始、推进波次、生成怪物、结算胜负并清理实体、HUD、装备和玩家临时状态。
- 初版使用有限波次表，不做无限波次。
- 不直接复刻任何现有服务器或作品的地图、文案、素材、精确数值和完整波次表。

### 1.2 初版分期

“初版/首版”指 MVP 1 到 MVP 3 全部完成后的第一轮可交付版本。

| 阶段 | 目标 |
|---|---|
| MVP 1 | 房间、地图持久化、单槽僵尸背包、开局投票、规则冻结、波次刷怪、击杀/助攻、死亡观战、胜负和清理闭环 |
| MVP 2 | 编号屏障推进、武器墙、子弹补给箱、装甲购买点和基础购买交互 |
| MVP 3 | 电源开关、汽水机、终极机器、玩家/武器成长和波间复活 |
| MVP 4+ | 窗口、倒地救援、神秘箱运行池、Boss 波、地图目标、撤离结局、配件和更细难度配置 |

第一张测试地图按 MVP 3 的对象规模制作，但暂时不做窗口。地图应包含玩家 init 复活点、`1` 号及后续编号僵尸复活点组、至少一组编号屏障、武器墙、子弹补给箱、装甲购买点、唯一电源开关、汽水机和终极机器。

### 1.3 核心循环

玩家加入房间后准备，房间成员发起开始投票。投票通过后先执行开局校验和本局快照冻结；成功后锁定房间、记录本局成员、传送到固定分配的 `INITIAL` init 复活点、发初始装备，进入 20 秒开局倒计时；倒计时结束后进入第一波前的 5 秒波间准备，按目标波次执行武器墙刷新，倒计时结束后开始第一波。

波次中，波次导演按当前波次配置和已激活的编号僵尸复活点生成怪物。玩家通过击杀和助攻获得个人点数，用点数购买武器、补弹、解除屏障、开启电源、购买装甲、购买汽水机 buff 或强化当前手持武器。波次结束后进入波间准备，MVP 3 起死亡观战玩家按规则在波间复活。

所有本局成员都死亡观战、主动离开或断线超时转死亡时，本局失败。推进到扫描出的最大波次之后进入胜利结算。同一事件 tick 内同时满足胜利和全员死亡失败时，失败优先。

## 2. 规则归属与配置

### 2.1 硬编码规则

以下规则不写入配置文件，也不开放配置覆盖：

- 房间人数上限固定 4 人。
- 开局倒计时固定 20 秒。
- 不再设置独立开波等待阶段；开局倒计时结束后直接进入第一波前的波间准备。
- 单波持续时间上限固定 1200 秒。
- 中途加入策略固定为 `reject_after_start`。
- 开始投票必须所有已加入玩家都处于准备状态，不开放配置覆盖。
- 友伤固定关闭：玩家来源伤害、汽水机爆炸和其它模式伤害都不能对队友生效。
- 玩家经济固定独立：点数、购买、武器实例强化、汽水机 buff 都归属个人，不存在全队共享点数池。
- 初始手枪使用子弹补给箱时免费补弹。
- 僵尸初始武器首版只允许 `pistol`，不开放近战初始武器。
- 对局内饱食度固定锁定到最大值，生命回复机制复用现有通用战斗回复逻辑。
- 初版完整地图只允许一个电源开关；第一张测试地图必须包含且只包含一个电源开关。
- 僵尸房间 roster 使用单队逻辑 key `survivors`，不使用 Kortac/Specgru 假队伍。

### 2.2 规则配置文件

全局配置只保存少量房间规则和波次默认值；每波配置只写本波差异。地图对象价格、墙枪刷新规则、汽水机、终极机器、电源、屏障等参数属于地图对象数据，不写入全局规则配置。

```text
serverconfig/codpattern/zombies_rules/
  config.json
  waves/
    wave_001.json
    wave_002.json
    wave_003.json
    wave_010_boss.json
```

初始 `config.json` 模板：

```json
{
  "room": {
    "startVoteTimeoutSeconds": 15,
    "startVoteRequiredPercent": 60,
    "intermissionSeconds": 5,
    "failDelaySeconds": 8,
    "offlineGraceSeconds": 120,
    "deadPlayerPolicy": "spectate_until_wave_intermission"
  },
  "defaults": {
    "healthMultiplier": 1.0,
    "damageMultiplier": 1.0,
    "speedMultiplier": 1.0,
    "maxAlive": 8,
    "spawnIntervalTicks": 40,
    "killPoints": 10,
    "assistPoints": 3
  }
}
```

### 2.3 JSON 命名约定

- 对象类型名使用 snake_case，例如 `zombie_spawn`、`weapon_wall`、`ammo_box`。
- payload 字段使用 lowerCamelCase，例如 `objectId`、`weaponLevel`、`refreshWaves`。
- 地图存档按对象类型保存复数列表；对象类型名 wrapper 只用于单对象示例片段。
- 不再混用 `weaponWall`、`zombieSpawn` 这类 camelCase 对象类型。

### 2.4 快照冻结

开始投票通过后，锁房和传送前必须先完成开局校验和本局快照冻结。冻结内容包括：

- 波次表、最大波次号、全局默认值和房间规则。
- 地图对象、对象价格、对象启用状态初始值。
- 僵尸复活点、编号屏障、玩家 init 复活点和 `endtp`。
- 武器墙刷新规则、稀有度权重、候选武器权重和枪等级倍率。
- 僵尸背包配置和备弹倍率。

开局前置校验规则：所有可能失败的检查都必须发生在锁房、传送、发装备和 HUD 初始化之前。旧实体清理、背包读取、传送目标可用性检查和对象状态初始化也都属于开局前置成功条件。任意开局失败都保持或回到等待/准备阶段，不锁房、不传送、不发放装备、不初始化 HUD、不清理玩家现有装备，玩家仍保留在房间准备列表中。对局中规则重载只影响之后新开的房间，不修改已经开始的房间。

### 2.5 通用框架落地约束

僵尸模式必须作为通用模式的一等 runtime/provider 接入，而不是 TDM 的派生特例。落地约束：

- 模式定义使用 `gameType=zombies`、单队协作 roster `survivors`、`JoinPolicy.MODE_DEFINED`、`LifecycleKind.WAVE_OR_ROUND_LOOP` 和 `ScoreboardKind.PROGRESS_METRICS`；不创建 TDM 假队伍。
- 能力开关只声明实际需要的能力：`READY_STATE`、`START_VOTE`、`MATCH_END_TELEPORT`、`MODE_SPECIFIC_MAP_FEATURES`；对象交互和运行态同步由僵尸 runtime 暴露对应 port。
- 房间外围交互复用通用按钮、弹窗、准备状态和房间列表更新能力，但投票语义、队伍语义、比分语义、重生语义和阶段 packet 不能直接复用 TDM 实现。
- 开始投票服务必须满足本文的固定投票快照、固定分母、快照成员离开立即失败、投票通过后才执行开局校验和冻结。
- 僵尸背包和武器筛选配置必须与 TDM 配置隔离。实现方式使用 zombies 专用 repository/cache/同步 packet，或在现有 repository 中按 mode key 或 Path 严格隔离；不能用僵尸配置覆盖 TDM 配置。
- 僵尸规则、背包和武器筛选配置保存失败必须返回失败结果、写日志并提示操作者；不能沿用静默吞掉保存异常的行为。
- 僵尸地图对象使用自己的持久化 provider 和对象集合 schema；部署工具写入 zombies provider，不把对象挂到 TDM 点位结构下。
- 僵尸怪物目标追踪由僵尸房间目标追踪服务负责。生成怪物时注入自定义 target goal，或由房间 tick 驱动目标选择；只处理登记到本房间的僵尸模式怪物。
- 客户端 HUD 使用独立 `ZombiesHudOverlay`，只读取 `ClientModeRuntimeState` / `ClientModeObjectState`，不读取 `ClientTdmState`。
- 僵尸房间必须接管 `ModeRespawnPolicyPort`，避免玩家死亡/复活时触发 TDM 背包重发。僵尸波间复活由僵尸 runtime 自己恢复位置、生命、装备实例和玩家状态。

## 3. 对局状态机

### 3.1 阶段

| 阶段 | 说明 | 主要逻辑 |
|---|---|---|
| 等待/准备 | 玩家加入房间、选择僵尸初始武器、切换准备状态 | 最多 4 人，不生成怪物，不计分 |
| 开局投票 | 房间成员发起开始投票 | 只允许投票快照成员投票；投票期间临时拒绝新玩家加入；投票通过后进入校验和冻结 |
| 开局倒计时 | 全部开局前置校验、冻结和旧实体清理成功后传送玩家到固定分配的 `INITIAL` init 复活点，开始 20 秒倒计时 | 锁定房间，记录本局成员，发初始装备，不生成怪物，不计分 |
| 波间准备 | 每波开始前的 5 秒自动倒计时准备阶段，第一波也包括 | 允许购买、解除屏障、MVP 3 波间复活；进入阶段时按目标波次刷新武器墙；倒计时结束后自动进入目标波 |
| 波次中 | 当前波次正在进行 | 刷怪、奖励、死亡观战、对象交互 |
| 胜利 | 下一波编号大于扫描出的最大波次号 | 展示胜利提示，短倒计时后清理 |
| 失败 | 全员死亡/离开/断线超时转死亡，或单波超时 | 停止刷怪，展示失败提示，短倒计时后清理 |
| 结束 | 结算清理和传送 | 复用当前地图 `endtp`，清理玩家装备和临时状态 |

### 3.2 开局投票

1. 等待/准备阶段允许已加入玩家切换准备状态。
2. 所有已加入玩家准备后，任意房间成员可以发起开始投票。
3. 发起投票时记录投票成员快照，投票只面向该快照内成员。
4. 投票期间临时拒绝新玩家加入；投票失败或校验/冻结失败后解除临时限制。
5. 投票期间快照成员主动离开房间时，当前投票立即失败。
6. 分母为发起投票时的已加入玩家数。
7. 所需赞成票为 `ceil(totalPlayers * startVoteRequiredPercent / 100)`，并限制在 `1..totalPlayers`。
8. 赞成票达到所需票数立即通过。
9. 剩余未投票玩家即使全赞成也无法通过时，投票立即失败。
10. 超时按 `startVoteTimeoutSeconds` 处理，默认值 15 秒。
11. 投票失败后保持等待/准备阶段。
12. 投票通过后先校验并冻结快照，成功后才锁房、传送、发装备和初始化 HUD。

### 3.3 加入和成员身份

投票发起后到投票结束前，局外玩家临时不能加入该房间。开局校验和冻结成功并锁定房间后，局外玩家不能中途加入，也不能以观战身份插入当前对局。本局成员固定为投票成员快照内成功进入锁房流程的玩家。MVP 3 的波间复活只对这些本局成员生效。

### 3.4 结算清理

胜利或失败后必须清理：

- 本房间登记的所有怪物和无效实体引用。
- 玩家点数、死亡观战状态、connection/life 临时状态、汽水机 buff、装甲状态和武器实例状态。
- 僵尸模式发放的临时装备和 HUD 快照。
- 编号屏障、电源状态、武器墙当前出售结果等本局对象状态。

清理后按当前地图 `endtp` 传送在线玩家。离线本局成员结算时记录 `postGameTeleportPending`，玩家回连后先清空僵尸临时状态并传送到 `endtp`，再允许正常离开房间或进入下一局。开局被僵尸模式清除的普通背包战斗物品不暂存、不恢复。

## 4. 玩家状态

### 4.1 玩家临时字段

| 字段 | 用途 |
|---|---|
| points | 本局点数，内部按 float 保存，HUD 和提示向下取整显示为 int |
| kills | 击杀数 |
| assists | 助攻数 |
| deaths | 死亡次数 |
| downs | 倒地次数，初版保留字段但不启用救援逻辑 |
| life_state | 生命状态：存活、死亡观战 |
| connection_state | 连接状态：在线、离线、离开 |
| player_buffs | 已购买汽水机强化，死亡复活时清空 |
| starter_weapon_state | 初始手枪状态，死亡复活时保留 |
| primary_weapon_state | 当前唯一购买类主武器的 `gunId`、枪等级、强化等级、实例倍率和备弹，死亡复活时保留 |
| armor_state | 当前装甲等级和全局减伤倍率，死亡复活时保留 |
| current_group | 当前所在编号区域或最近激活组，可用于提示和刷怪权重 |
| offline_since_tick | 玩家断线开始 tick；在线时为空 |
| last_alive_target_pos | 玩家最后一次可作为怪物追踪目标的位置；离线存活玩家使用该位置作为虚拟目标 |
| death_equipment_snapshot | 死亡进入观战时保存的僵尸模式装备快照，波间复活时原路发放 |

### 4.2 死亡和观战

1. 玩家受到致命伤害时直接进入死亡流程，初版不拦截为倒地状态。
2. 死亡表现可复用现有死亡动画或死亡镜头表现，但不能复用 TDM 队伍比分、重生发包或结算语义。
3. 死亡时保存僵尸模式装备快照，拒绝掉落局内装备；死亡动画结束后玩家进入死亡观战状态，不能攻击、购买或触发对象交互。
4. MVP 1 和 MVP 2 阶段死亡玩家观战到本局结束。
5. MVP 3 必须实现波间复活，作为初版最终交付规则。
6. 单人对局中唯一玩家死亡时立即失败，不进入波间复活。
7. 所有本局成员都处于死亡观战、离开或断线超时转死亡状态时，房间失败。
8. `downs` 只为 MVP 4 倒地救援预留，不参与初版失败判定。
9. 玩家死亡后，正在以该玩家为目标的僵尸模式怪物立即重新选择其它 `life_state=存活` 的本局成员；没有可用存活目标时按失败判定或断线 grace 规则处理。

### 4.3 波间复活

MVP 3 起，除单人对局外，已记录为本局成员的死亡观战玩家会在房间仍有存活玩家且成功进入波间准备时复活。断线但尚未超过 `offlineGraceSeconds` 的存活玩家仍计入“房间仍有存活玩家”。复活时机固定为进入波间准备阶段的开始 tick，先执行武器墙刷新和对象状态更新，再执行死亡玩家复活，最后广播 HUD 快照。

复活点模型：

1. 僵尸模式只使用与 frontline 一样的 `SpawnPointKind.INITIAL` 玩家 init 复活点。
2. 开局传送和波间复活共用同一组本局冻结的 `INITIAL` 点位，不再维护独立波间复活点位池。
3. 不支持 `DYNAMIC_CANDIDATE`，不做动态复活点合并，不按玩家当前位置、怪物位置、危险区域或视线重新评分选点。
4. 开局冻结时按地图存档顺序固定 `INITIAL` 点位列表；本局成员按投票快照顺序固定分配 `spawnIndex = memberIndex % initialSpawnCount`。
5. 同一名玩家开局传送、波间复活和异常重试都优先回到自己的固定 `spawnIndex` 点位；`INITIAL` 点位少于本局人数时允许多人共用同一点位。
6. `INITIAL` 点位只做基础合法性校验：维度一致、在地图范围内、坐标可解析、至少 1 个点位。运行时不做安全点动态替换。
7. 如果固定点位在运行时不可用，服务端只按冻结列表顺序尝试下一个 `INITIAL` 点位；这属于固定兜底，不进入动态复活逻辑。全部 `INITIAL` 点位不可用时，该玩家保持死亡观战，记录错误并等待下一次波间复活重试。

复活规则：

1. 保留初始武器和购买类主武器。
2. 保留武器实例属性，包括枪等级、终极机器强化等级、武器伤害倍率和其它武器自身属性。
3. 保留点数、装甲等级、备弹、局内装备和其它非玩家强化 buff 的局内状态。
4. 清空玩家强化 buff，包括双倍血量、更快移动速度、双倍备弹、更高爆头倍率等汽水机强化。
5. 清空双倍备弹时，所有僵尸模式武器的备弹按 `floor(currentReserveAmmo / 2)` 裁剪。
6. 复活位置使用该玩家固定分配的 `INITIAL` 点位；不做动态安全点选择，也不因为附近有怪物而改点。
7. 复活时生命值回满到当前基础最大生命值，饱食度维持最大值，不提供额外无敌保护。
8. 死亡时保存的僵尸模式装备快照在复活时原路发放；发放后同步武器实例状态、备弹、装甲和 HUD。
9. 双倍血量等玩家强化 buff 在死亡观战和对局结束时清空；波间复活统一重建基础最大生命值并回满，因此不保留死亡前当前生命值，也不需要对当前血量做裁剪。初版不提供“存活中主动移除双倍血量”的交互。

### 4.4 断线和离房

1. 断线后按 `offlineGraceSeconds` 保留原局内状态，默认值为 120 秒。
2. 断线期间 `connection_state=offline`，`life_state` 先保持断线前状态。
3. 断线超时前，如果断线前 `life_state=存活`，该玩家在失败判定、HUD 存活人数、怪物追踪目标池和波间复活前提中仍算存活玩家。
4. 怪物目标追踪按 `life_state=存活` 选择目标，不按在线状态排除。在线存活玩家使用玩家实体作为目标；离线但未超时的存活玩家使用 `last_alive_target_pos` 作为虚拟目标位置。
5. 超过 `offlineGraceSeconds` 后，如果玩家仍离线，则把 `life_state` 改为死亡观战，但仍保留本局成员身份。
6. 波间复活只对 `connection_state=online` 且 `life_state=死亡观战` 的本局成员执行。
7. 离线死亡玩家回连后改为 `connection_state=online`，但仍等待下一次符合条件的波间复活。
8. 主动离开房间改为 `connection_state=left`，并从当前本局成员可复活名单中移除。

### 4.5 物品保护

已进入本局的玩家死亡时拒绝掉落物品。局内玩家主动丢弃武器或装甲也应被拒绝，避免临时装备离开状态机。旁观者移动物品或容器转移初版暂不额外处理。

## 5. 波次和怪物

### 5.1 波次文件

每波文件控制本波血量倍率、速度倍率、伤害倍率、同时存活上限、刷怪节奏、原版实体种类和数量。初版不维护僵尸模式专属怪物类型表，也不使用 `normal_zombie`、`fast_zombie` 这类模式类型字段。

```json
{
  "wave": 4,
  "healthMultiplier": 1.25,
  "damageMultiplier": 1.10,
  "speedMultiplier": 1.08,
  "maxAlive": 10,
  "spawnIntervalTicks": 35,
  "mobs": [
    { "entity": "minecraft:zombie", "count": 18, "killPoints": 10, "assistPoints": 3 },
    { "entity": "minecraft:husk", "count": 4, "killPoints": 15, "assistPoints": 5 }
  ]
}
```

继承规则：

- 没写的倍率、`maxAlive` 和 `spawnIntervalTicks` 从 `defaults` 继承。
- `mobs[].killPoints` 和 `mobs[].assistPoints` 缺失时从 `defaults` 继承。
- 本波预算完全由 `mobs[].count` 决定，不按玩家人数缩放。
- 初版不发命中奖励。

### 5.2 最大波次和缺失波次

启动、规则重载或房间开局校验时扫描 `waves/wave_*.json`。如果文件内有 `wave` 字段，必须和文件名编号一致；如果没有 `wave` 字段，则使用文件名编号。完成冲突校验后，取有效波次编号最大值作为最大波次，不再单独配置 `maxWave`。

规则：

- 没有任何有效波次配置时，房间不能开始。
- 显式 `mobs: []` 或所有 `mobs[].count` 总和为 0 的波次是合法空波。
- 不强制 `wave_001` 存在；例如只存在 `wave_010.json` 时，1-9 波按空波推进到第 10 波。空波仍先进入对应目标波次的 5 秒波间准备，开波后立即完成。
- 缺失波次不是致命问题，不作为开局错误。
- 空波必须显式写 `mobs: []`；文件存在但缺少 `mobs` 字段时按配置错误处理。

### 5.3 配置校验

| 字段 | 合法范围 |
|---|---|
| `wave` / 文件名编号 | 整数，`>= 1` |
| `healthMultiplier` / `damageMultiplier` / `speedMultiplier` | 有限正数，`> 0`；超出 `0.01-100.0` 给 warning |
| `maxAlive` | 整数，`>= 1`；本波怪物总数为 0 时可忽略 |
| `spawnIntervalTicks` | 整数，`>= 1` |
| `mobs[].count` | 整数，`>= 0` |
| `killPoints` / `assistPoints` | 整数，`>= 0` |
| 对象价格和扣费项 | 整数，`>= 0` |
| `zombie_spawn.group` / `barrier.group` | 整数，`>= 1` |
| `zombie_spawn.weight` | 有限正数，`> 0` |
| `ammunitionPerMagazineMultiple` | 有限数，`>= 0`，默认 `7.0` |
| 僵尸默认初始武器 | 必须通过 `zombies_weapon_filter.json` 校验，且分类必须是 `pistol` |
| 枪等级倍率、终极机器倍率 | 有限正数，`> 0` |
| 武器墙 `refreshWaves[]` | 整数，`>= 1`；运行时额外在第 1 波和最大波的波间准备强制刷新 |
| 武器墙稀有度 `rank` | 整数，`>= 1`；同一武器墙内必须唯一 |
| 武器墙稀有度 `baseWeight` / `waveFactor` | 有限数；`baseWeight > 0`，最高稀有度还要求 `1.0 + waveFactor > 0` |
| 武器 `weightsByRarity` | 引用已声明的稀有度 id，权重为有限正数，`> 0` |
| 武器墙最高稀有度候选 | 每个武器墙最高稀有度至少要被 1 把武器引用 |
| 终极机器等级 | 玩家实例从 `upgradeLevel=0` 开始；对象 `levels` 只配置可购买目标等级 `1..maxUpgradeLevel`，且必须连续 |
| 装甲等级 | 只允许 `1`、`2`、`3`；伤害倍率必须 `> 0` 且 `<= 1` |

波次配置冲突、无法解析、`count > 0` 的怪物条目缺少有效 `entity`、实体 id 无效，或 `entity` 不是可作为怪物生成的原版生物实体时，应阻止本局开始并提示配置错误。

### 5.4 刷怪和 AI

1. 开局默认激活 `group=1` 僵尸复活点组。
2. 只从已激活的合法僵尸复活点刷怪。
3. 当前存活怪物数低于本波 `maxAlive` 时，按 `spawnIntervalTicks` 间隔尝试刷怪。
4. 每次刷怪从已激活、合法且区块可用的复活点中按 `weight` 加权随机选择位置。
5. 每次生成后登记到当前房间，死亡、回收、异常离场或结算时按实体终止原因统计和清理。
6. 开局倒计时结束后先进入目标波次 `1` 的波间准备；之后每波预算用尽且房间内归属怪物为 0 时，计算下一目标波次。
7. 如果下一目标波次大于最大波次，立即进入胜利；否则进入该目标波次的波间准备。
8. 波间准备按 `intermissionSeconds` 自动倒计时，倒计时结束后进入目标波次。
9. 单波持续超过 20 分钟时强制失败。
10. 怪物卡住超过 20 秒，或离所有存活玩家实体/虚拟目标位置太远超过 15 秒，立即回收。
11. 如果已激活合法复活点全部因区块不可用而暂时不可刷，不消耗本波预算；等待区块可用、换用其它已激活合法点，或等待单波超时兜底。

实体终止原因：

- `KILLED`：被玩家、玩家归属的模式伤害或其它允许发奖励的来源击杀；消耗本波已生成预算，按奖励规则发点，注销实体。
- `RECYCLED_RETRY`：卡住、过远、目标无效、区块临时不可用或寻路异常导致的模式回收；不发奖励，把该只怪计回未生成预算或重刷队列，不能因此推进波次。
- `REMOVED_CONSUME_BUDGET`：管理员命令、调试命令或模式明确要求消耗预算的移除；不发奖励，但消耗本波预算。
- `CLEANUP`：胜利、失败、重置或服务器停服清理；不发奖励，不触发波次推进。

僵尸模式怪物应使用房间级目标追踪服务，不依赖原版怪物视线发现玩家。怪物可以无视遮挡锁定本房间存活玩家；离线但未超时的存活玩家以最后目标位置参与追踪。视线只影响攻击命中，不影响目标选择。移动仍使用原版寻路和碰撞规则，不做穿墙移动。该追踪逻辑只作用于僵尸模式生成并登记到本房间的怪物，不修改全局原版怪物 AI。

## 6. 经济与奖励

### 6.1 点数

点数只在当前房间本局内有效，不持久化。内部按 float 保存；击杀、助攻和汽水机分数倍率都写入精确浮点值；HUD、房间列表和提示文本统一向下取整显示为 int。

玩家经济独立：玩家只拥有自己的点数、击杀、助攻、死亡观战状态、汽水机 buff、武器实例强化和购买状态。购买武器、补弹、装甲、电源、汽水机和终极机器只扣购买者个人点数。编号屏障和电源开关由交互玩家个人支付，结果对全队生效。初版不做队友捐点或屏障均摊付款。

### 6.2 奖励

| 行为 | 奖励 |
|---|---|
| 击杀怪物 | 默认 10 点，可由 `mobs[].killPoints` 覆盖 |
| 助攻 | 默认 3 点，可由 `mobs[].assistPoints` 覆盖 |
| 修复窗口 | MVP 4+ |
| 救起队友 | MVP 4+ |

助攻规则：

- 怪物在房间内维护伤害贡献记录。
- 助攻阈值硬编码为对该怪物造成过大于 0 的有效玩家伤害。
- 最终击杀玩家获得 `killPoints`。
- 其它满足条件且 `connection_state != left` 的本局成员各获得一次 `assistPoints`；死亡观战和离线未超时玩家可以获得已造成伤害对应的助攻记账，主动离开玩家不再获得后续奖励。
- 击杀者不额外获得助攻奖励。
- 同一只怪物对同一玩家最多发放一次助攻。
- 环境伤害、摔落和非玩家来源击杀默认不发击杀或助攻奖励；机关奖励不进入初版配置。

### 6.3 经济节奏

首版经济目标：

- 平均玩家第 2-3 波可以买到低级墙枪。
- 第 3-5 波能由一名玩家支付第一道编号屏障。
- 第 5-7 波开始考虑更高枪等级、装甲或第一次终极机器强化。

波次怪物数量、击杀/助攻奖励和地图对象价格应按这个节奏反推。

## 7. 地图和对象

### 7.1 地图持久化和占用

僵尸模式应注册自己的地图持久化 provider。地图创建、对象新增、删除、清空和参数修改后立即写入世界目录；保存失败时回滚内存改动并提示管理员。

存档路径：

```text
fpsmatch/<world>/zombies/<mapName>.json
```

不要保存到全局配置目录，也不要混进 TDM 的 `frontline` 或 `teamdeathmatch` 地图数据。服务器启动或世界加载时读取僵尸地图存档并注册地图；存档缺失或解码失败时只跳过对应地图并写日志，不阻止其它模式地图加载。删除地图时同时删除对应持久化文件。

对局运行态初版不持久化。服务器停服、崩溃后重启或世界热重载时，正在进行的僵尸对局视为已经结束；启动恢复流程必须扫描并清理带僵尸房间归属标记的残留实体，释放地图占用，清空在线玩家僵尸 HUD 和临时状态。玩家进入僵尸对局时应写入轻量临时标记，异常重启后回连时如果检测到该标记或玩家仍位于非活跃僵尸地图内，应清除僵尸临时状态并尽量传送到对应地图 `endtp`；无法解析地图时传送到安全兜底点并写日志。活跃地图不允许删除或重命名；对象和参数修改可以保存到地图文件，但只影响下一局，当前对局继续使用开局冻结快照。

僵尸地图存档至少包含：

1. `mapName`、`levelName`、地图范围 `areaData` 和复用现有语义的 `endtp`。
2. 玩家 init 复活点列表，使用 `SpawnPointKind.INITIAL` 语义。
3. 僵尸复活点列表及其 `group`、`weight`。
4. 编号屏障列表及其 `group`、`cost`、玩家阻挡配置、覆盖范围或交互点。
5. 武器墙、子弹补给箱、装甲购买点、汽水机、终极机器、电源开关和神秘箱对象列表。
6. 窗口对象作为 MVP 4 扩展字段保留。

### 7.2 对象总表

| 对象 | 初版行为 | 世界形态 |
|---|---|---|
| 玩家 init 复活点 | 开局传送位置；MVP 3 起死亡玩家在波间准备开始时也回到该点位集合 | `INITIAL` 点位，不需要方块 |
| 僵尸复活点 | 保存维度、坐标、朝向、`group` 和 `weight`；同号屏障解除后激活对应组 | 地图点位，不需要方块 |
| 编号屏障 | 花费点数解除；同号任意屏障解除后，同号屏障全部消失 | 地图区域对象，可由地图建筑表现 |
| 武器墙 | 按枪等级部署；进入第 1 波、最大波和配置刷新波次的波间准备时抽取当前出售武器 | 地图对象绑定方块位置；装饰方块只用于视觉，不要求功能方块 |
| 子弹补给箱 | 给当前僵尸模式武器补备弹；初始手枪免费，购买类主武器按枪等级取价 | 地图对象绑定方块位置；装饰方块只用于视觉，不要求功能方块 |
| 装甲购买点 | 购买 1-3 级装甲，提供本局全局减伤，不做补充或耐久 | 地图对象绑定方块位置；装饰方块或展示实体只用于视觉，不要求功能方块 |
| 电源开关 | 花费点数开启；初版完整地图只允许单个电源开关；只解锁 `requiresPower=true` 的汽水机和终极机器 | 独立功能方块 `codpattern:zombies_power_switch` |
| 汽水机 | 默认需要电源，允许配置免电源；购买玩家 buff；死亡复活清空 | 地图对象绑定方块位置；装饰方块只用于视觉，不保存玩法状态 |
| 终极机器 | 默认需要电源，允许配置免电源；强化当前手持武器实例；死亡复活保留 | 地图对象绑定方块位置；装饰方块只用于视觉，不保存玩法状态 |
| 神秘箱 | 部署工具先保存位置和基础参数；运行时随机池放到 MVP 4 | 地图对象绑定方块位置；MVP 4 前不要求功能方块 |
| 窗口 | MVP 4+ 做刷怪入口、破坏、修复和修复奖励 | 地图区域对象或方块集合，MVP 4 扩展时补充运行细则 |

除电源开关外，初版购买/交互对象的玩法权威都来自地图对象数据和房间运行态，不来自世界方块 NBT 或 BlockEntity。模组提供装饰方块用于统一视觉，例如 `codpattern:zombies_soda_machine`、`codpattern:zombies_ultimate_machine`、`codpattern:zombies_ammo_box`、`codpattern:zombies_weapon_wall_panel`、`codpattern:zombies_armor_station`；部署和运行逻辑只要求对象 `pos` 命中有效地图对象。方块类型不匹配时部署工具给 warning，不阻止保存或开局。

只有两类强启用关系：

- `barrier.group -> zombie_spawn.group`
- `power_switch -> requiresPower=true 的 soda_machine / ultimate_machine`

其它对象初版不做编号区域、房间前置、电源前置或波次前置限制；`requiredBarrierGroup` 和 `requiredWave` 不进入初版 schema。

### 7.3 地图对象校验

1. 所有对象必须在地图范围内，维度必须和地图一致。
2. 玩家 `INITIAL` init 复活点至少 1 个；该点位同时用于开局传送和波间复活。
3. 僵尸地图不允许声明 `DYNAMIC_CANDIDATE` 玩家点位；部署工具不提供动态点合并入口。
4. `group=1` 僵尸复活点至少 1 个，且必须合法、权重大于 0。
5. 非 `group=1` 僵尸复活点也必须通过坐标、维度和权重校验。
6. 屏障组没有同号僵尸复活点时只给管理员提示，不阻止保存或开局。
7. 同一张地图内所有对象的 `objectId` 必须全局唯一。
8. 电源开关最多 1 个；MVP 3 完整校验和第一张测试地图必须恰好 1 个。
9. 电源开关位置必须是 `codpattern:zombies_power_switch` 或电源功能方块白名单内的方块；不匹配时是 error。
10. 如果地图存在 `requiresPower=true` 的汽水机或终极机器，则必须存在该唯一电源开关。
11. 屏障、武器墙、子弹补给箱、汽水机、终极机器、电源开关和装甲购买点的价格或参数必须完整合法。
12. 装甲购买点 `armorLevel` 只能是 `1`、`2` 或 `3`，`damageTakenMultiplier` 必须 `> 0` 且 `<= 1`。
13. 武器墙、子弹补给箱、装甲购买点、汽水机和终极机器的位置如果不是对应装饰方块，只给 warning，不阻止保存或开局。

校验 profile：

- `MVP1_MINIMAL`：必须有地图范围、`endtp`、玩家 `INITIAL` init 复活点、`group=1` 僵尸复活点和有效波次表。
- `MVP2_PURCHASES`：在 `MVP1_MINIMAL` 基础上校验编号屏障、武器墙、子弹补给箱和装甲购买点参数。
- `MVP3_FULL_INITIAL`：在 `MVP2_PURCHASES` 基础上要求唯一电源开关、汽水机和终极机器完整可用；首版最终验收使用该 profile。

### 7.4 部署工具

部署工具沿用现有地图工具的交互方式：先用地图创建工具选择模式和地图范围，再用部署工具选择地图、对象类型和参数，左键/右键在世界中取点，界面内做预览、添加、删除和清空。

不要复用 TDM 的队伍点位语义。僵尸模式对象应保存在自己的对象列表或点位层里，不挂到 Kortac/Specgru 这类队伍下。

| 部署类型 | 保存形态 | 工具参数 |
|---|---|---|
| 玩家 init 复活点 | repeatable point layer `INITIAL` | 坐标、维度、yaw、pitch；开局和波间复活共用 |
| 僵尸复活点 | repeatable point/object `zombie_spawn` | `group`、`weight` |
| 编号屏障 | repeatable area/object `barrier` | `group`、`cost`、`blocksPlayersOnly=true` |
| 武器墙 | repeatable object `weapon_wall` | `weaponLevel`、`levelDamageMultiplier`、`price`、`refreshWaves[]`、`rarityPools[]`、`weapons[].weightsByRarity`、装饰方块 warning |
| 子弹补给箱 | repeatable object `ammo_box` | `pricesByWeaponLevel`、装饰方块 warning |
| 装甲购买点 | repeatable object `armor_station` | `armorLevel`、`buyCost`、`damageTakenMultiplier`、装饰方块 warning |
| 汽水机 | repeatable object `soda_machine` | `buffId`、`cost`、`requiresPower`，默认 `true`、装饰方块 warning |
| 终极机器 | repeatable object `ultimate_machine` | `maxUpgradeLevel`、`levels`、`requiresPower`，默认 `true`、装饰方块 warning |
| 电源开关 | single block/object `power_switch` | `objectId`、功能方块状态、`cost`、可选提示文本 |
| 神秘箱 | repeatable object `mystery_box` | `cost`、可选武器池 |
| 窗口 | repeatable object `window` | MVP 4+ |

完整 GUI 开发方案见附录 A。主文只保留部署对象、保存形态和地图规则，避免地图规则章节被 UI 细节淹没。

### 7.5 地图对象落地初步方案

`ZombiesMapData` 初版拆为 common 数据和 zombies payload 两层：

- common：`schemaVersion`、`gameType`、`mapName`、`levelName`、`areaData`、`endtp`。
- point lists：`initialSpawns[]`、`zombieSpawns[]`。
- object lists：`barriers[]`、`weaponWalls[]`、`ammoBoxes[]`、`armorStations[]`、`sodaMachines[]`、`ultimateMachines[]`、`mysteryBoxes[]`。
- single object：`powerSwitch`，不存在或超过 1 个按校验 profile 处理。
- reserved：`windows[]`，MVP 4+ 使用。

部署工具的 UI 按对象类型操作这些集合。保存时通过僵尸专用 persistence provider 写入世界目录；保存失败必须回滚内存改动并向管理员提示。现有通用编辑 port 不能表达 repeatable object 时，初版部署工具直接调用僵尸地图服务，但不能把僵尸对象挂到 TDM 队伍点位下。

## 8. 地图对象细则

### 8.1 编号屏障和复活点

开局默认激活 1 号僵尸复活点组。玩家与任意 `2` 号屏障交互并支付费用后，所有 `2` 号屏障消失，同时激活 `2` 号僵尸复活点组；`3`、`4`、`5` 号以此类推。

```json
{
  "zombie_spawn": {
    "objectId": "zombie_spawn_2_a",
    "group": 2,
    "weight": 1.0,
    "dimension": "minecraft:overworld",
    "pos": { "x": 120, "y": 65, "z": -34 },
    "yaw": 90.0,
    "pitch": 0.0
  },
  "barrier": {
    "objectId": "barrier_2_a",
    "group": 2,
    "cost": 750,
    "blocksPlayersOnly": true,
    "dimension": "minecraft:overworld",
    "area": {
      "from": { "x": 118, "y": 64, "z": -30 },
      "to": { "x": 120, "y": 66, "z": -30 }
    },
    "interactionPos": { "x": 118, "y": 65, "z": -30 }
  }
}
```

编号屏障初版只阻隔玩家，不阻隔怪物、投射物、掉落物或其它实体。屏障解除后，同组屏障在本局内同时移除视觉、交互和阻挡状态；对局结束后地图存档中的屏障配置仍保留，下一局重新生效。

### 8.2 武器墙

武器墙按枪等级部署。地图对象长期保存该墙的枪等级、枪等级伤害倍率、购买价格、刷新波次、稀有度权重和候选武器权重。

武器墙不做承载玩法状态的功能方块。`codpattern:zombies_weapon_wall_panel` 装饰方块或地图自定义墙面外观只承担视觉；当前出售武器、刷新结果和购买校验都保存在本局对象状态中。

刷新时机：

- 进入第 1 波前的波间准备时强制刷新。
- 进入扫描出的最大波前的波间准备时强制刷新。
- 进入 `refreshWaves[]` 中声明波次前的波间准备时刷新。
- 未到刷新波次的波间准备时继续出售上一次抽取结果。

刷新结果写入本局对象状态，不长期写回地图存档。

第 1 波刷新发生在 20 秒开局倒计时结束后的第一波波间准备开始时。所有刷新都绑定到目标波次的波间准备开始事件；同一个波间同时命中强制刷新和 `refreshWaves[]` 时只刷新一次。

候选武器池由同一个 `weapons[]` 列表表达。不同稀有度通过 `weightsByRarity` 引用同一把武器并配置不同刷率，避免为普通、稀有、史诗等池重复维护一批 `gunId`。

刷新公式：

```text
如果 maxWave <= 1:
  p = 1.0
否则:
  p = clamp((currentWave - 1) / (maxWave - 1), 0.0, 1.0)

highest = 该武器墙 rarityPools 中 rank 最大的稀有度

如果 rarity.rank < highest.rank:
  rarityProgress = 1.0 - p
否则:
  rarityProgress = 0.05 + 0.95 * p

rarityEffectiveWeight = rarity.baseWeight * max(0.0, 1.0 + rarity.waveFactor * p) * rarityProgress
weaponEffectiveWeight = rarityEffectiveWeight * weapon.weightsByRarity[rarity.id]
```

当 `currentWave == maxWave` 时，所有非最高稀有度的 `rarityProgress` 都是 `0`，刷新结果必定来自最高稀有度池。最高稀有度池内如果有多把武器，仍按各自 `weightsByRarity` 加权随机。

```json
{
  "weapon_wall": {
    "objectId": "weapon_wall_level_2_01",
    "weaponLevel": 2,
    "levelDamageMultiplier": 1.25,
    "price": 900,
    "refreshWaves": [1, 4, 7, 10],
    "rarityPools": [
      { "id": "common", "rank": 1, "baseWeight": 80.0, "waveFactor": -0.70 },
      { "id": "rare", "rank": 2, "baseWeight": 18.0, "waveFactor": 0.50 },
      { "id": "epic", "rank": 3, "baseWeight": 2.0, "waveFactor": 2.00 }
    ],
    "weapons": [
      { "gunId": "tacz:m4a1", "weightsByRarity": { "common": 3.0, "rare": 1.0 } },
      { "gunId": "tacz:ak47", "weightsByRarity": { "common": 2.0, "rare": 2.0 } },
      { "gunId": "tacz:hk_g3", "weightsByRarity": { "epic": 1.0 } }
    ],
    "dimension": "minecraft:overworld",
    "pos": { "x": 130, "y": 65, "z": -42 }
  }
}
```

### 8.3 子弹补给箱

子弹补给箱只处理当前手持的僵尸模式武器。默认最大备弹按该武器弹匣容量乘以 `zombies_weapon_filter.json` 的 `ammunitionPerMagazineMultiple` 计算，默认倍率 `7.0`。

子弹补给箱不做承载玩法状态的功能方块。`codpattern:zombies_ammo_box` 装饰方块只承担视觉；补弹价格和交互结果以地图对象和玩家武器实例为准。

- 初始手枪免费补弹，不读取补给箱价格表。
- 购买类主武器按当前枪等级读取 `pricesByWeaponLevel`。
- 当前手持非僵尸模式武器、近战武器或无弹药武器时，交互失败并提示原因。

```json
{
  "ammo_box": {
    "objectId": "ammo_box_01",
    "pricesByWeaponLevel": {
      "1": 200,
      "2": 350,
      "3": 500
    },
    "dimension": "minecraft:overworld",
    "pos": { "x": 131, "y": 65, "z": -42 }
  }
}
```

### 8.4 装甲购买点

装甲分为 1-3 级，视觉材质按等级硬编码，怪物伤害减免倍率由部署工具写入具体装甲购买点对象。购买后玩家获得本局全局怪物伤害减免，死亡复活时保留。

装甲购买点不做承载玩法状态的功能方块。`codpattern:zombies_armor_station` 装饰方块或展示实体只承担视觉；购买状态属于玩家 `armor_state`，不写入世界方块。

| 等级 | 硬编码表现 |
|---|---|
| 1 | 铁甲套 |
| 2 | 金甲套 |
| 3 | 钻石甲套 |

装甲不维护耐久，不提供补充。购买更高等级装甲会替换低等级装甲。重复购买已持有等级或更低等级时不扣费，只提示已拥有更高或相同等级装甲。

如果实现上直接发放原版盔甲物品，需要避免原版盔甲减伤和僵尸模式倍率重复叠加；最终怪物伤害由僵尸模式服务统一计算，盔甲套装主要承担视觉和占位表现。

```json
{
  "armor_station": {
    "objectId": "armor_station_02",
    "armorLevel": 2,
    "buyCost": 1250,
    "damageTakenMultiplier": 0.50,
    "dimension": "minecraft:overworld",
    "pos": { "x": 133, "y": 65, "z": -42 }
  }
}
```

### 8.5 电源开关

每局开始时电源默认关闭。初版完整地图只允许一个电源开关。任意存活玩家与该电源开关交互并支付费用后，本房间电源开启，解锁所有 `requiresPower=true` 的汽水机和终极机器。电源开启后不再关闭；重复交互只提示已开启，不重复扣费。

电源开关做成独立功能方块 `codpattern:zombies_power_switch`。开启后方块应能释放红石信号，方便地图机关或视觉反馈接入；初版玩法逻辑仍只把电源用于汽水机和终极机器。电源方块使用 BlockState 或 BlockEntity 处理表现和红石输出，但本局“是否已开启”的权威状态仍保存在房间运行态，结算时随房间对象状态清理。

```json
{
  "power_switch": {
    "objectId": "power_switch_01",
    "block": "codpattern:zombies_power_switch",
    "cost": 1000,
    "emitsRedstoneWhenPowered": true,
    "dimension": "minecraft:overworld",
    "pos": { "x": 134, "y": 65, "z": -44 }
  }
}
```

### 8.6 汽水机

汽水机默认需要电源开启后使用，`requiresPower` 缺失时按 `true` 处理；显式配置 `requiresPower=false` 的汽水机可在电源关闭时使用。buff 由模组实现为 effect，地图对象保存 `buffId`、价格和可选 `requiresPower`。

汽水机不做承载玩法状态的功能方块。`codpattern:zombies_soda_machine` 装饰方块用于地图视觉；玩家交互时服务端以地图对象 `soda_machine.pos` 和 `objectId` 为准，方块本身不保存购买状态、电源状态、玩家 buff 状态或冷却状态。地图作者使用其它方块做外观时，部署工具只给装饰方块 warning。

首版内置效果：

- 双倍血量。
- `1.25` 倍速度。
- 受伤爆炸。
- 双倍备弹。
- `1.25` 倍分数。
- 死亡猎手：`1.5` 倍爆头伤害。

细则：

1. `1.25` 倍分数作用于击杀和助攻奖励，奖励写入玩家 float 点数。
2. 双倍备弹提升玩家僵尸模式武器的最大备弹容量；购买时当前持有的所有僵尸模式武器备弹也按 2 倍提升，之后子弹补给箱可补到双倍上限。
3. 死亡复活清空双倍备弹时，所有僵尸模式武器备弹按 `floor(currentReserveAmmo / 2)` 裁剪。
4. 受伤爆炸只在玩家受到本房间怪物有效伤害时触发，单玩家冷却 5 秒，爆炸半径初版硬编码 4 格。
5. 受伤爆炸对范围内僵尸模式怪物造成该怪物当前最大生命值 `15%` 的模式伤害。
6. 受伤爆炸击杀僵尸时，伤害来源归属触发爆炸的玩家，并按正常击杀和助攻规则发放奖励。
7. 所有玩家来源伤害和受伤爆炸都拒绝友伤。
8. 同一名玩家重复购买已拥有的同一 `buffId` 时不扣费，只提示已拥有；不同 `buffId` 可以同时持有，初版不设总数量上限。
9. 双倍血量购买成功时把最大生命提升到基础最大生命的 2 倍，并立即把当前生命回满到新的最大值。
10. 双倍血量在玩家死亡观战和对局结束时清空；波间复活统一恢复基础最大生命值并回满血，不保留死亡前当前生命值。

```json
{
  "soda_machine": {
    "objectId": "soda_health_01",
    "buffId": "double_health",
    "cost": 1500,
    "requiresPower": true,
    "dimension": "minecraft:overworld",
    "pos": { "x": 132, "y": 65, "z": -44 }
  }
}
```

### 8.7 终极机器

终极机器默认需要电源开启后使用，`requiresPower` 缺失时按 `true` 处理；显式配置 `requiresPower=false` 的终极机器可在电源关闭时使用。首版只强化玩家当前手持武器，不打开背包内武器选择 UI，也不一次性强化全部武器。

终极机器不做承载玩法状态的功能方块。`codpattern:zombies_ultimate_machine` 装饰方块用于地图视觉；强化等级、价格、倍率和玩家实例状态都来自地图对象与玩家武器实例，不能写入世界方块 NBT 或 BlockEntity。

玩家武器实例初始 `upgradeLevel=0`。终极机器对象的 `levels` 只配置可购买目标等级 `1..maxUpgradeLevel`，每次成功交互把当前手持武器提升到下一等级，并读取该目标等级的价格和倍率。

当前手持物必须是僵尸模式枪械武器；初始手枪和购买类主武器都可以强化。近战武器、非僵尸模式武器、无有效武器实例或已达强化上限时交互失败，不扣费并提示原因。

```json
{
  "ultimate_machine": {
    "objectId": "ultimate_machine_01",
    "maxUpgradeLevel": 3,
    "levels": {
      "1": { "cost": 1200, "damageMultiplier": 1.25 },
      "2": { "cost": 2500, "damageMultiplier": 1.5 },
      "3": { "cost": 5000, "damageMultiplier": 2.0 }
    },
    "requiresPower": true,
    "dimension": "minecraft:overworld",
    "pos": { "x": 136, "y": 65, "z": -44 }
  }
}
```

## 9. 武器、背包与成长

### 9.1 僵尸背包

僵尸模式不直接沿用 TDM 的重生发包逻辑，也不搬入 TDM 的完整背包选择界面。背包系统作为公共入口存在，但不同模式使用自己的背包页面、配置和发放语义。

MVP 1 必须完成：

- 背包 GUI 顶部中间提供两个模式按钮：`团队竞技` 和 `僵尸生存`。
- 点击 `团队竞技` 保留现有 TDM 背包选择和配置逻辑。
- 点击 `僵尸生存` 打开僵尸模式专用简化页面。
- 僵尸生存页面不显示背包列表，不支持多个背包选择，不显示 LR 战术/杀伤手雷栏。
- 僵尸生存页面只显示一个初始武器槽。
- 僵尸初始武器槽使用独立筛选配置。
- 初始武器首版只开放 `pistol`，不允许 `melee`。

配置路径：

```text
serverconfig/codpattern/backpack_rules/
  backpack_config.json
  weapon_filter.json
  zombies_backpack_config.json
  zombies_weapon_filter.json
```

`zombies_backpack_config.json`：

```json
{
  "playerData": {
    "<player-uuid>": {
      "weapon": {
        "item": "tacz:modern_kinetic_gun",
        "count": 1,
        "nbt": "{GunId:\"tacz:glock_17\",GunCurrentAmmoCount:17,GunFireMode:\"SEMI\",HasBulletInBarrel:1}"
      }
    }
  }
}
```

`zombies_weapon_filter.json`：

```json
{
  "weaponTabs": ["pistol"],
  "defaultWeapon": {
    "item": "tacz:modern_kinetic_gun",
    "count": 1,
    "nbt": "{GunId:\"tacz:glock_17\",GunCurrentAmmoCount:17,GunFireMode:\"SEMI\",HasBulletInBarrel:1}"
  },
  "ammunitionPerMagazineMultiple": 7.0,
  "blockedItemNamespaces": ["example_gunpack"],
  "blockedWeaponIds": ["namespace:gunid"],
  "blockedAttachmentNamespaces": ["example_attachment_pack"],
  "blockedAttachmentIds": ["namespace:attachmentid"]
}
```

开局发放只读取僵尸单槽配置。发放时直接清空普通背包带入的战斗物品，不暂存、不恢复；只给一把初始手枪；不发 TDM 副武器、战术投掷物、杀伤投掷物和配装补给。

### 9.2 武器持有模型

1. 玩家默认持有一把初始手枪。
2. 购买类主武器最多持有一把，独立于初始手枪。
3. 同一个 TaCZ `gunId` 出现在不同枪等级池时，按不同僵尸模式武器实例处理。
4. 购买墙枪时，如果已持有同 `gunId` 且同枪等级的主武器，交互失败并提示使用子弹补给箱。
5. 如果 `gunId` 不同，或 `gunId` 相同但枪等级不同，视为购买新主武器；支付价格后替换当前主武器，并丢弃旧主武器的僵尸模式实例状态。
6. 购买新主武器时把该武器备弹补到配置最大备弹，默认最大备弹为弹匣容量的 `7` 倍。
7. 对局结束时，僵尸模式装备、点数、玩家强化、武器实例状态和观战状态都必须清空，不能留到大厅、普通生存或下一局；开局被清空的普通背包战斗物品不恢复。

### 9.3 武器伤害模型

服务端应把僵尸模式专用字段写到武器 ItemStack 或等价实例状态中，例如 `weaponLevel`、`levelDamageMultiplier`、`upgradeLevel` 和 `upgradeDamageMultiplier`。

TaCZ 命中事件只在“本房间玩家使用僵尸模式武器命中本房间僵尸模式怪物”时读取这些字段并修改本次枪械伤害。不要修改 TaCZ 全局伤害配置。

伤害倍率分层：

1. 枪等级倍率：来自武器墙或神秘箱抽到的枪等级，决定基础伤害倍率，也用于补给箱按枪等级取价。
2. 武器强化等级倍率：来自终极机器，写入武器实例，死亡复活时保留。
3. 最终武器倍率：`levelDamageMultiplier * upgradeDamageMultiplier`。
4. 玩家 buff 倍率：汽水机爆头倍率等作为临时玩家倍率叠加，死亡复活时清空。

## 10. HUD 与同步

HUD 数据通过僵尸模式自己的运行态快照同步，不复用 TDM 比分、阶段 packet 或 `ClientTdmState`。客户端渲染使用独立 `ZombiesHudOverlay`，只读取通用运行态和对象状态缓存。非僵尸模式不显示僵尸 HUD。玩家离房、断线或对局结束后清空 HUD。

局内 HUD 至少展示：

- 当前波次。
- 剩余怪物数：本波已生成存活怪物 + 未生成预算怪物。
- 当前个人点数。
- 当前阶段和倒计时。
- 队友存活、死亡观战、离线或离开状态。
- 交互对象提示，例如费用、条件不足、电源未开启。

初版布局：

| 区域 | 内容 |
|---|---|
| 顶部居中 | 阶段名和倒计时 |
| 左上 | 当前波次、剩余怪物数 |
| 左下或经验条上方 | 当前个人点数 |
| 右侧 | 最多 4 人的队友状态列表 |
| 准星附近 | 交互提示 |
| 屏幕中央短提示 | 波次开始、波次完成、胜利、失败 |

快照示例：

```json
{
  "phaseKey": "WAVE_ACTIVE",
  "remainingTimeTicks": 0,
  "metrics": [
    { "key": "wave", "translationKey": "hud.codpattern.zombies.wave", "value": 4, "display": "NUMBER" },
    { "key": "zombies_left", "translationKey": "hud.codpattern.zombies.left", "value": 12, "display": "NUMBER" },
    { "key": "alive_players", "translationKey": "hud.codpattern.zombies.alive", "value": 2, "display": "NUMBER" },
    { "key": "max_players", "translationKey": "hud.codpattern.zombies.max_players", "value": 4, "display": "NUMBER" }
  ],
  "playerValues": {
    "points": { "type": "INT", "value": "640" },
    "kills": { "type": "INT", "value": "18" },
    "life_state": { "type": "STRING", "value": "ALIVE" },
    "connection_state": { "type": "STRING", "value": "ONLINE" }
  },
  "prompts": [
    { "key": "weapon_wall", "translationKey": "hud.codpattern.zombies.buy_weapon" }
  ]
}
```

观战玩家仍能看到波次、剩余怪物和队友状态，但不能看到可购买交互提示。交互提示以服务端校验结果为准，客户端只负责展示。

房间列表和房间详情展示模式名、阶段、当前波次、玩家数、存活人数和剩余怪物数。房间 roster 使用单队逻辑 key `survivors`，客户端按该 key 渲染 1-4 名玩家，不显示 TDM 队伍标题或比分。

队友状态同步不使用 TDM 队伍比分。初版使用 `survivors` roster 提供玩家列表，再用运行态 `playerValues` 中的 `survivor.<uuid>.life_state`、`survivor.<uuid>.connection_state`、`survivor.<uuid>.points` 等 key 补充状态。交互提示如需展示价格或动态条件，优先通过对象状态或 playerValues 提供参数；如果现有 `ModePrompt` 只能表达 key 和 translationKey，则不要直接发送拼好的本地化文本，prompt 参数字段作为独立扩展项处理。

## 11. 实现模块划分

| 模块 | 职责 |
|---|---|
| 模式定义 | 注册僵尸模式、人数、展示信息和能力开关 |
| 房间运行时 | 阶段、波次、计时器、胜负和清理 |
| 开局准备与投票 | 准备状态、开始投票、校验冻结、传送和开波前倒计时 |
| 波次导演 | 校验波次配置、读取 `entity` 列表、计算预算和刷怪节奏 |
| 怪物目标追踪 | 为本房间怪物选择存活玩家或离线存活玩家最后位置，处理仇恨转移 |
| 实体归属 | 记录怪物房间归属，处理死亡和清理 |
| 玩家状态 | 点数、死亡观战、连接状态、复活、buff、武器、装甲 |
| 经济服务 | 奖励、扣费、购买校验、防刷 |
| 交互对象 | 屏障、武器墙、补给箱、装甲、电源、汽水机、终极机器 |
| 背包系统 | 公共模式入口、TDM 背包页、僵尸单槽初始武器页 |
| 武器服务 | 武器墙刷新、唯一主武器替换、补弹、实例倍率和终极机器强化 |
| 装甲服务 | 1-3 级装甲、全局伤害倍率、购买和复活保留 |
| 玩家强化服务 | 汽水机 buff、死亡复活清空、临时属性应用和移除 |
| 地图 schema | 僵尸地图对象、对象校验规则和持久化 |
| 地图工具 | 沿用通用地图工具交互，放置和维护僵尸对象 |
| 部署工具 GUI | 僵尸对象三栏编辑界面、表单校验、坐标采集、世界预览、保存回滚 |
| 运行态同步 | HUD、对象状态和房间 metrics |
| 僵尸 HUD | 独立 overlay、vanilla HUD 抑制条件、room context 清理 |
| 调试工具 | 输出房间状态、实体数量、当前波次和对象状态 |

### 11.1 性能与可观测性要求

初版实现必须带资源预算和调试输出，避免多房间运行时难以定位卡顿或残留：

- 每个房间的怪物同时存活数受本波 `maxAlive` 限制；实现层还应有全服僵尸模式怪物总上限，达到上限时暂停新刷怪并写限流日志。
- 怪物目标追踪不应每 tick 对所有玩家和所有怪物做全量昂贵扫描；按固定间隔或分片更新，并缓存本房间存活目标列表与离线目标位置。
- HUD 和对象状态同步应按固定间隔发送，并使用 revision 避免旧包覆盖新状态；没有状态变化时可以跳过或降频。
- 交互提示检测应限制频率和距离，只对当前玩家所在房间的对象做 raycast/范围判断。
- 玩家 `INITIAL` 点位可用性检查、怪物卡住检测和过远回收都要记录原因码，便于区分固定点位不可用、`RECYCLED_RETRY`、配置错误和性能限流。
- 调试命令至少能输出 `roomId`、地图名、阶段、当前波次、存活/未生成怪物数、实体归属数量、对象状态、玩家 life/connection 状态、最近刷怪失败原因和最近清理数量。

## 12. 里程碑

### MVP 1：基础房间与波次闭环

- 创建僵尸模式房间，固定最多 4 人。
- 同一张僵尸地图同一时间只允许一个房间占用。
- 注册僵尸模式地图持久化 provider，支持创建、加载和删除僵尸地图。
- 地图最少包含玩家 `INITIAL` init 复活点、`group=1` 僵尸复活点和 `endtp`。
- 部署工具 GUI 1 支持地图选择、点位/区域采集、玩家 init 复活点、僵尸复活点和屏障编辑。
- MVP 1 的屏障只做数据保存、编辑和校验，不做玩家阻挡、购买解除、视觉移除或激活同组僵尸复活点；这些运行逻辑属于 MVP 2。
- 增加僵尸生存单槽初始武器完整 GUI 和独立筛选配置。
- 房间成员准备并发起开始投票。
- 投票通过后先校验波次配置、数值范围、地图对象、`group=1` 僵尸复活点和 `endtp`。
- 任意开局前置失败时不锁房、不传送、不发装、不初始化 HUD，玩家仍停留在房间准备阶段。
- 校验通过后冻结快照、完成旧实体清理、锁房、记录本局成员、传送到固定分配的 `INITIAL` init 复活点、发初始装备。
- 完成 20 秒开局倒计时，随后进入第 1 波前的 5 秒波间准备。
- 每波开始前的波间准备按 5 秒自动倒计时推进到目标波次；最后一波结束后直接胜利。
- 开局默认激活 `group=1` 僵尸复活点组。
- 按波次 `entity` 配置生成原版实体，并登记到本房间实体归属。
- 实现房间级怪物目标追踪，目标为本局 `life_state=存活` 玩家或其最后目标位置。
- 击杀和助攻给点数。
- 波次结束进入下一目标波次的波间准备；缺失波次按空波推进。
- 玩家死亡后播放死亡表现并进入观战。
- 全员死亡观战、主动离开或断线超时转死亡后失败；单人死亡直接失败。
- HUD 展示波次、剩余怪物数、个人点数、阶段倒计时和 `survivors` 队友状态。
- 胜利结算做简单提示、短倒计时、清理和 `endtp` 传送。

### MVP 2：地图推进与基础购买

- 增加 `2` 号及后续编号僵尸复活点组和编号屏障。
- 继续只从已激活编号复活点刷怪。
- 编号屏障可购买解除，同组屏障全部消失并激活同组复活点。
- 部署工具 GUI 2 支持补给箱、装甲点、电源、汽水机和终极机器基础表单。
- MVP 2 的电源、汽水机和终极机器只做 schema、表单、保存和校验占位，不执行开电、buff、requiresPower 解锁或武器强化；这些运行逻辑属于 MVP 3。
- 增加武器墙工具，保存价格、枪等级倍率、刷新波次、稀有度权重和候选武器权重。
- 部署工具 GUI 3 在 MVP 2 支持武器墙高级子面板和 `MVP1_MINIMAL` / `MVP2_PURCHASES` 校验摘要；`MVP3_FULL_INITIAL` 摘要等 MVP 3 validator 完成后补齐。
- 运行时在第 1 波、最大波和配置刷新波次的波间准备开始时抽取武器墙当前出售武器。
- 增加武器墙购买流程和唯一主武器规则。
- 增加子弹补给箱，初始手枪免费，购买类主武器按枪等级取价。
- 增加 1-3 级装甲购买点。
- HUD 展示对象提示和购买失败原因。
- 所有购买交互由服务端做原子扣费与发货。

### MVP 3：电源、成长与波间复活

- 增加付费电源开关运行逻辑。
- 增加终极机器，支持武器强化等级和武器实例倍率。
- 增加汽水机，支持内置 effect 类型玩家强化 buff。
- 支持本局成员死亡玩家在波间准备开始时回到固定分配的 `INITIAL` init 复活点；不做动态安全点选择，回满血、饱食度锁定最大、无额外无敌保护，并原路发放死亡时保存的僵尸装备快照。
- 中途加入仍保持硬编码拒绝。
- 通过波次文件增加更多原版实体组合和更高压的倍率、数量配置。

### 当前进度标记（2026-05-04）

- [x] MVP 1 完成点：房间、地图、规则冻结、初始装备、波次、战斗、死亡观战、cleanup、HUD、debug、胜利/失败 closure JVM 自动化和真实实体清理 GameTest-only 入口已具备；本轮不考虑人工验收。
- [x] MVP 1 本轮收口：`ZombiesMvp1DeepCoverageCompatTest` 已接入阶段 suite 和 MVP123 suite；自动化/代码侧完成，不以人工验收或 MVP4+ 作为阻塞项。
- [x] MVP 2 完成点：对象 schema/runtime、屏障/墙枪/补给箱/装甲购买、交互提示、真实墙枪 primary 发放、补给箱 tagged weapon 备弹同步、装备 NBT 基础、部署工具 LIST 行级增删改、deploy object editor 自动化、`WEAPON_WALL` CRUD 和对象交互 closure JVM 自动化已具备。
- [x] MVP 2 本轮收口：`ZombiesMvp2DeepCoverageCompatTest` 已接入阶段 suite 和 MVP123 suite；新增屏障真实玩家移动 enforcement GameTest 覆盖；自动化/代码侧完成。
- [x] MVP 3 完成点：`MVP3_FULL_INITIAL` validator、纯状态电源/汽水/终极服务、波间复活、cleanup/reconnect、装备快照恢复、power switch 方块注册/同步、weapon NBT 伤害倍率读取、runtime closure JVM 自动化、纯 JVM MVP123 自动化烟测和更多 GameTest-only 覆盖入口已具备。
- [x] MVP 3 本轮收口：`ZombiesMvp3DeepCoverageCompatTest` 已接入阶段 suite 和 MVP123 suite；新增 power switch placed block + points、ammo box tagged primary inventory sync GameTest 覆盖；自动化/代码侧完成。
- [x] 本轮验证：`./gradlew compileJava --rerun-tasks`、`./gradlew testClasses`、普通 JVM `ZombiesMvp123CompatTestSuite` 和 `./gradlew runGameTestServer` 已通过；GameTest 汇总为 `13 GAME TESTS COMPLETE` / `All 13 required tests passed :)`。
- [x] 本轮结论：在不考虑人工验收点、不考虑 MVP4+ 的口径下，MVP 1 到 MVP 3 自动化/代码侧完成。

### MVP 4：内容扩展

- 窗口破坏、修复和修复奖励。
- 倒地救援和救起奖励。
- 神秘箱运行池。
- Boss 波。
- 地图目标或撤离结局。
- 更细的武器池、配件和难度配置。

## 13. 关键边界情况

- 所有玩家主动离开：立即结束房间并清理实体。
- 开局前置失败：保持或回到等待/准备阶段，不锁房、不传送、不发装、不初始化 HUD。
- 开局倒计时和第一波前波间准备期间离房/断线：按本局成员状态处理；如果所有本局成员都离开、死亡观战或断线超时转死亡，则失败并清理，否则倒计时继续。
- 单人死亡：直接失败，不提供自救或每波自动复活。
- 中途加入：投票期间临时拒绝，锁房后拒绝，不能通过观战或波间复活进入当前对局。
- 波间复活：只对投票成员快照内成功进入锁房流程的本局成员生效；位置来自该玩家固定分配的 `INITIAL` init 复活点；死亡时保存装备，复活时原路发放。
- 缺失波次：按空波推进，不提前胜利，不发该波奖励；空波仍有该目标波次的 5 秒波间准备，开波后立即完成。
- 空波次目录：没有任何有效波次配置时不能开局。
- 波次冲突：多个文件声明同一个 `wave`，或文件名编号与文件内 `wave` 冲突时不能开局。
- 最大波次结束：下一波编号大于最大波次号时胜利结算。
- 胜负同 tick：失败优先。
- 单波超时：超过 20 分钟强制失败。
- 怪物卡住或寻路失败：卡住超过 20 秒，或离所有存活玩家实体/虚拟目标位置太远超过 15 秒，按 `RECYCLED_RETRY` 回收。
- 区块未加载：不在未加载僵尸复活点刷怪，不消耗预算。
- 地图对象缺失：缺少玩家 `INITIAL` init 复活点、`group=1` 僵尸复活点、合法 `endtp` 或必需对象参数时不能开局。
- 电源开关：初版完整地图只允许一个电源开关。
- 商店重复交互：服务端扣费和发货必须原子化。
- 僵尸背包隔离：僵尸开局只读取僵尸单槽 pistol 配置，不能读取或发放 TDM 完整背包；开局清空的普通背包战斗物品不暂存、不恢复。
- 友伤：所有玩家来源伤害和受伤爆炸都拒绝友伤。
- 房间隔离：不同房间的波次、怪物、电源、对象状态、武器墙刷新结果和 HUD 快照不能共享。
- 伤害倍率范围：只对本房间僵尸模式武器命中本房间怪物生效，不能污染其它模式或全局 TaCZ 配置。
- 服务器重启或热重载：运行中对局不恢复，启动时清理残留僵尸实体、HUD 和玩家临时状态并释放地图占用。

## 14. 验收标准

- 不需要 TDM 假队伍和假比分也能显示房间和进入游戏。
- 房间开始后只影响本房间玩家和本房间怪物。
- 开始投票通过后、锁房和传送前能校验波次配置、地图对象、`group=1` 合法僵尸复活点和 `endtp`。
- 任意开局前置失败后，玩家仍在房间准备阶段，未被传送、未发装、无僵尸 HUD、房间未锁定。
- 非法配置不会进入可卡住的对局。
- 击杀、助攻、波次推进、点数、失败和清理逻辑稳定。
- 玩家经济独立，个人点数、奖励、购买、汽水机 buff 和武器实例强化状态不会串账。
- 投票期间局外玩家临时不能加入，房间锁定后局外玩家不能中途加入。
- 本局成员死亡观战后按波间规则回到固定分配的 `INITIAL` init 复活点，装备快照、血量、饱食度和 buff 清理符合规则；单人死亡直接失败。
- 断线玩家按 `offlineGraceSeconds` 保留，超时前存活断线玩家仍计入存活判定，超时后转死亡观战。
- 怪物追踪只按 `life_state=存活` 判断目标资格；在线玩家用实体目标，离线未超时玩家用最后目标位置。
- 电源需要个人付费开启，且只影响 `requiresPower=true` 的汽水机和终极机器。
- 地图完整校验下电源开关恰好一个，roster 使用 `survivors` 单队 key，不出现 TDM 假队伍或假比分。
- 僵尸部署工具 GUI 能选择 `zombies` 地图、编辑所有 MVP 3 必需对象、运行 `MVP3_FULL_INITIAL` 校验，并在保存失败时保留草稿且回滚内存改动。
- 武器购买、子弹补给箱、唯一主武器、备弹倍率、武器墙刷新和装甲等级/减伤规则按本局快照执行。
- 屏障价格、墙枪刷新规则、子弹补给箱价格、汽水机和终极机器参数来自地图对象快照。
- 地图对象状态可同步给客户端，玩家看到明确交互反馈。
- 对局结束后复用 `endtp` 传送，且没有残留怪物、残留 HUD、残留玩家点数、残留局内装备或残留对象状态；开局清空的普通背包战斗物品不会被恢复。

## 附录 A：部署工具 GUI 开发方案

### A.1 目标和入口

僵尸部署工具 GUI 的目标是让管理员不写 JSON 也能维护一张僵尸地图的对象集合，并且所有保存动作都走服务端校验和僵尸地图持久化 provider。

落地为独立工具和独立界面，不把复杂对象表单塞进现有出生点工具：

- 地图范围和 `endtp` 继续复用现有地图创建/地图管理能力；地图类型需要支持 `zombies`。
- 僵尸对象维护使用新的 `ZombiesDeployTool` 工具物品。
- 客户端界面命名为 `ZombiesDeployToolScreen`，服务端打开包命名为 `OpenZombiesDeployToolScreenS2CPacket`，客户端动作包命名为 `ZombiesDeployToolActionC2SPacket`。
- 初版通用 `ModeMapEditPort` 还不能表达 repeatable object 和嵌套参数时，部署工具直接调用僵尸地图服务；数据仍必须写到 `fpsmatch/<world>/zombies/<mapName>.json`。
- 只有 OP 或满足现有 `ToolAccessHelper` 管理权限的玩家可打开和保存；无权限时服务端拒绝并清空客户端临时预览。

工具打开流程：

1. 玩家手持僵尸部署工具右键打开 GUI。
2. 服务端读取当前世界内所有 `zombies` 地图，返回地图列表、当前选择、对象类型列表、选中对象、地图校验摘要和工具草稿坐标。
3. 客户端只展示和编辑草稿；所有新增、更新、删除、清空、校验、保存都发送动作包，由服务端重新读取权限和地图状态后执行。
4. 服务端每次动作完成后回发完整 screen snapshot，不依赖客户端本地状态作为最终事实。

### A.2 屏幕布局

界面采用三栏结构：

| 区域 | 内容 | 要求 |
|---|---|---|
| 左栏 | 地图选择、校验 profile、对象类型列表 | 地图列表只显示 `zombies`；对象类型用固定顺序；不可用的 MVP 4 类型置灰 |
| 中栏 | 当前类型对象列表和当前对象摘要 | 显示 `objectId`、坐标、关键参数；支持上一项/下一项、复制、删除、清空当前类型 |
| 右栏 | 当前对象参数表单 | 按类型显示字段；数字字段用输入框和步进按钮；布尔字段用开关；枚举字段用循环按钮或下拉 |
| 底栏 | 坐标采集、保存、校验、关闭 | 提供“使用脚下位置”“使用准星方块”“保存对象”“运行校验”“关闭” |

显示规则：

- 屏幕不暂停游戏，布局沿用现有 `MapCreatorToolScreen` / `SpawnPointToolScreen` 的半透明面板和小尺寸按钮风格。
- 低分辨率下右栏表单可滚动；按钮文字过长时使用省略和 tooltip，不能挤出按钮。
- 所有字段展示本地化 key，不在 packet 中发送拼好的中文文本；动态错误可以发送错误码和参数。
- 界面顶部始终显示当前地图名、地图是否被活跃房间占用、保存状态和最近一次错误。

### A.3 客户端动作和服务端 packet

`ZombiesDeployToolActionC2SPacket` 支持以下动作：

| 动作 | 用途 |
|---|---|
| `REFRESH` | 重新请求地图列表、对象列表和校验摘要 |
| `SAVE_SELECTIONS` | 保存工具物品上的当前地图、对象类型、选中对象和草稿坐标 |
| `SELECT_MAP` | 切换地图 |
| `SELECT_OBJECT_TYPE` | 切换对象类型 |
| `SELECT_OBJECT` | 切换当前对象 |
| `SET_FIELD` | 更新当前草稿字段；服务端按字段 schema 解析 |
| `CAPTURE_PLAYER_POS` | 用玩家当前位置和朝向填充点位 |
| `CAPTURE_LOOK_BLOCK` | 用玩家准星方块填充对象位置或交互点 |
| `SET_AREA_POS_1` / `SET_AREA_POS_2` | 设置区域对象的两个角 |
| `ADD_OBJECT` | 用当前草稿创建对象 |
| `UPDATE_OBJECT` | 保存当前对象参数 |
| `DUPLICATE_OBJECT` | 复制当前对象并生成新 `objectId` |
| `DELETE_OBJECT` | 删除当前对象 |
| `CLEAR_OBJECT_TYPE` | 清空当前类型对象 |
| `VALIDATE_MAP` | 按选择的校验 profile 运行地图校验 |

服务端处理要求：

- 每个动作都重新检查权限、手持工具类型、地图是否存在、对象类型是否合法。
- `ADD_OBJECT` / `UPDATE_OBJECT` / `DELETE_OBJECT` / `CLEAR_OBJECT_TYPE` 必须原子执行：先复制内存地图、应用变更、运行基础校验、写文件；任一步失败都回滚内存改动。
- 保存成功后增加地图编辑 revision，并回发最新 snapshot；客户端收到旧 revision 的预览或表单刷新应丢弃。
- 地图被活跃僵尸房间占用时允许保存对象数据到文件，但 GUI 必须提示“当前对局使用开局冻结快照，本次修改只影响下一局”；删除或重命名活跃地图仍禁止。
- 批量清空必须二次确认。确认可以用 packet 内 `confirmToken` 或 GUI 二段式按钮，不能一次误点直接清空。

### A.4 坐标采集和世界预览

工具需要同时支持 GUI 内按钮采集和世界左/右键采集：

- 点位对象：`CAPTURE_PLAYER_POS` 保存玩家脚下坐标、维度、yaw、pitch；`CAPTURE_LOOK_BLOCK` 保存准星方块中心点，yaw/pitch 仍取玩家朝向。
- 方块对象：电源开关默认使用准星方块坐标，并保存 `block` 和 `emitsRedstoneWhenPowered`；该坐标必须是电源功能方块，否则服务端拒绝保存。
- 区域对象：编号屏障使用 `SET_AREA_POS_1` / `SET_AREA_POS_2` 采集范围，再用 `CAPTURE_LOOK_BLOCK` 设置 `interactionPos`。
- 普通交互对象：武器墙、补给箱、装甲点、汽水机、终极机器和神秘箱使用 `pos`，可选保存 yaw/pitch 供客户端提示朝向；这些对象不要求功能方块，方块类型只参与装饰方块 warning。

客户端预览：

- 当前地图范围用淡色边框展示。
- 当前类型对象全部显示简化标记，当前选中对象高亮。
- 点位显示小型十字和 `objectId` 或序号；区域显示包围盒；方块对象显示方块轮廓。
- 预览只渲染当前地图附近一定距离内的对象，避免大型地图一次性绘制过多标签。
- 预览颜色固定：玩家 `INITIAL` init 复活点绿色、僵尸复活点红色、屏障黄色、商店对象蓝色、电源紫色、错误对象橙红色。

### A.5 对象表单字段

除 `INITIAL` 点位外，所有对象新增时都自动生成唯一 `objectId`，默认格式为 `<type>_<short_map>_<index>`；管理员可手动修改，但保存时必须校验同地图全局唯一。`INITIAL` 点位沿用通用 `SpawnPointData`，不要求 `objectId`。

| 对象类型 | 必填字段 | 默认值和 GUI 行为 |
|---|---|---|
| `INITIAL` | `dimension`、`pos`、`yaw`、`pitch`，`kind=INITIAL` | 玩家 init 复活点；新增时用玩家当前位置；开局传送和波间复活共用；僵尸模式不显示 `DYNAMIC_CANDIDATE` 表单 |
| `zombie_spawn` | `objectId`、`group`、`weight`、`dimension`、`pos`、`yaw`、`pitch` | `group` 默认 1，`weight` 默认 1.0；权重必须大于 0 |
| `barrier` | `objectId`、`group`、`cost`、`blocksPlayersOnly`、`dimension`、`area`、`interactionPos` | `blocksPlayersOnly` 初版固定 true 且置灰；区域两角和交互点必须在地图范围内 |
| `weapon_wall` | `objectId`、`weaponLevel`、`levelDamageMultiplier`、`price`、`refreshWaves[]`、`rarityPools[]`、`weapons[]`、`dimension`、`pos` | 使用高级子面板编辑稀有度和武器池；第 1 波和最大波刷新不要求写入 `refreshWaves[]`；装饰方块 `codpattern:zombies_weapon_wall_panel` |
| `ammo_box` | `objectId`、`pricesByWeaponLevel`、`dimension`、`pos` | 价格表按枪等级行编辑；缺少某等级价格时该等级购买类主武器不能补弹；装饰方块 `codpattern:zombies_ammo_box` |
| `armor_station` | `objectId`、`armorLevel`、`buyCost`、`damageTakenMultiplier`、`dimension`、`pos` | `armorLevel` 只能 1-3；材质只展示硬编码结果，不允许编辑；装饰方块 `codpattern:zombies_armor_station` |
| `power_switch` | `objectId`、`block`、`cost`、`emitsRedstoneWhenPowered`、`dimension`、`pos` | single object；已有电源开关时新增按钮置灰，只允许编辑或删除现有对象；必须是功能方块 `codpattern:zombies_power_switch` |
| `soda_machine` | `objectId`、`buffId`、`cost`、`requiresPower`、`dimension`、`pos` | `requiresPower` 默认 true；`buffId` 用内置 buff 下拉；装饰方块 `codpattern:zombies_soda_machine` |
| `ultimate_machine` | `objectId`、`maxUpgradeLevel`、`levels`、`requiresPower`、`dimension`、`pos` | `levels` 以等级行编辑，必须连续覆盖 `1..maxUpgradeLevel`；装饰方块 `codpattern:zombies_ultimate_machine` |
| `mystery_box` | `objectId`、`cost`、`dimension`、`pos`、可选武器池 | MVP 4 前只保存基础参数，运行池面板置灰 |
| `window` | `objectId`、`dimension`、`area`、可选交互点 | MVP 4 前仅保留 schema，GUI 中默认隐藏或置灰 |

武器墙高级子面板：

- `rarityPools[]` 行字段：`id`、`rank`、`baseWeight`、`waveFactor`；`rank` 同墙内唯一。
- `weapons[]` 行字段：`gunId` 和 `weightsByRarity`；只允许引用已声明稀有度 id。
- 提供“新增稀有度”“新增武器”“删除行”“复制行”按钮。
- 保存前展示最高稀有度是否至少有 1 把候选武器；没有则阻止保存。
- `refreshWaves[]` 用逗号分隔输入或小型列表编辑，保存时解析为去重升序整数列表。

### A.6 校验和错误展示

GUI 内校验分三层：

1. 客户端轻校验：空字段、数字格式、明显越界、重复表单行。
2. 服务端对象校验：对象类型字段、坐标维度、地图范围、`objectId` 唯一、价格和倍率范围。
3. 地图 profile 校验：`MVP1_MINIMAL`、`MVP2_PURCHASES`、`MVP3_FULL_INITIAL`。

错误展示要求：

- 字段级错误显示在字段下方或 tooltip，阻止保存当前对象。
- 地图级错误显示在左栏校验摘要中，按严重级别分为 `error` 和 `warning`。
- `warning` 不阻止保存，但 `MVP3_FULL_INITIAL` 下的必需对象缺失必须是 `error`。
- 保存失败必须保留玩家当前草稿，不因为服务端拒绝而清空表单。

### A.7 与命令和调试能力的关系

GUI 是主要编辑入口，命令作为兜底和自动化入口：

- `/cdp map list zombies`、`show`、`delete`、`set endtp` 仍可用于管理地图。
- GUI 的“运行校验”复用同一套地图校验服务，调试命令和开局校验也调用同一实现，避免三套规则漂移。
- GUI 保存后日志至少包含管理员名、地图名、动作、对象类型、`objectId` 和结果。
- “导出对象 JSON”和“从 JSON 粘贴导入当前对象”归入 GUI 4，不影响初版交付。

### A.8 开发分期

| 阶段 | 范围 | 验收 |
|---|---|---|
| GUI 1 | 工具物品、打开界面、地图选择、对象类型切换、点位/区域采集、`INITIAL`、`zombie_spawn`、`barrier` | 可创建 MVP 1 最小地图对象，保存失败可回滚，预览能看到点和区域 |
| GUI 2 | `ammo_box`、`armor_station`、`power_switch`、`soda_machine`、`ultimate_machine` 基础表单和 single power 约束 | 可完成 MVP 3 第一张测试地图除武器墙外的全部对象 |
| GUI 3 | `weapon_wall` 高级子面板、稀有度/武器池编辑、刷新波次编辑、地图 profile 校验摘要 | MVP 2 可不用手写 JSON 完成墙枪配置并展示 `MVP1_MINIMAL` / `MVP2_PURCHASES` 摘要；MVP 3 validator 合入后补齐 `MVP3_FULL_INITIAL` 并通过完整校验 |
| GUI 4 | 预览优化、复制/批量删除确认、对象导入导出、`mystery_box` / `window` MVP 4 扩展 | 不影响初版交付，可随内容扩展追加 |
