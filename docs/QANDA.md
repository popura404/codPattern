# COD Pattern Q&A

[项目概览](README.md) | [详细 Guide](GUIDE.md) | [更新日志](CHANGES.md)

> 这一页只收录当前实现下最常见的问题和排查方向，不重复展开 Guide 里的完整流程说明。

## 1. 玩家重生后为什么没有拿到背包

先检查这几项：

1. 玩家是否已经加入房间或对局。
2. 玩家当时是否仍然是旁观者。
3. 当前所选背包是否真的存在。
4. 槽位里的枪是否被 `weapon_filter.json` 黑名单拦掉了。

当前实现里，自动发放走的是 `BackpackDistributor.distributeBackpackItems(player)`：

- 非强制发放要求玩家已经在房间或对局里。
- 旁观者不会被发放。
- 命中黑名单的枪槽位会被直接跳过。

补救方式：

- 管理员执行 `/cdp distribute <玩家>`
- 如果还是不发，继续查 `backpack_config.json` 和 `weapon_filter.json`

## 2. 为什么我改了 `weapon_filter.json`，玩家界面没更新

因为这个文件虽然可以在线重读，但不会自己推到客户端。

当前正确做法：

1. 保存 `weapon_filter.json`
2. 执行 `/cdp update`

`/cdp update` 会重新读取筛选配置，并把筛选配置和每个玩家的背包数据同步到客户端。

## 3. 为什么我改了 `tdm_rules/config.json`，游戏里没有生效

因为这份配置只在服务端启动时加载。

当前实现入口是 `CodPattern.onServerStarting -> CodTdmConfig.load(server)`，没有对应的在线热重载命令。

处理方式：

1. 停服或重启服务器
2. 重新进入游戏验证

## 4. 房间列表里为什么没有我的地图

常见原因有四类：

1. 地图根本没有创建成功。
2. 地图持久化失败，文件没有落到 `fpsmatch/<世界名>/<模式>/`。
3. 你查的是错误模式。
4. 迁移服务器时漏拷了 `fpsmatch/` 目录。

先执行：

```mcfunction
/cdp map list
/cdp map list frontline
/cdp map list teamdeathmatch
```

再到游戏根目录检查：

- `fpsmatch/<世界名>/frontline/`
- `fpsmatch/<世界名>/teamdeathmatch/`

## 5. 为什么比赛进行中不能加入房间

因为当前实现明确只允许在 `WAITING` 阶段加入。

房间加入逻辑在 `TdmRoomInteractionService.joinRoom`，其中有一条硬性判断：

- 只要不是 `WAITING`，直接返回 `PHASE_LOCKED`

当前版本没有开放“进行中加入并自动转旁观”的配置项。

## 6. 为什么 TaCZ 原生改枪界面会被直接关掉

这是当前版本的设计，不是故障。

客户端会拦截 TaCZ 原生 `GunRefitScreen`，然后直接替换成 COD Pattern 的 `BackpackMenuScreen`。这样做是为了把：

- 配件候选过滤
- 配件黑名单拦截
- 配件保存
- 背包槽位回写

全部放到同一套服务端流程里处理。

## 7. 为什么背包改枪时没有自动带出当前枪上的配件

如果你说的是进入 COD Pattern 的背包改枪流程后，枪上原有配件没有自动填回到界面里，先确认是否安装了 `taczaddon`。

当前这部分自动填充能力依赖 `taczaddon` 提供的兼容层；没装时仍然可以进入 COD Pattern 的改枪流程，但不会有这类自动识别与回填效果。

处理方式：

1. 安装与当前 TaCZ 版本匹配的 `taczaddon`
2. 重启客户端与服务端后再验证

## 8. 配件保存失败通常是因为什么

最常见的原因：

1. 玩家当前不是有效改枪会话。
2. 会话超时了。
3. 保存时选中的背包 ID 或槽位和开改枪时不一致。
4. 当前热键栏位上的物品已经不是枪。
5. 枪上装了被黑名单禁用的配件。

当前改枪会话超时时间固定为 `120` 秒。

如果提示保存失败，服务端会回滚到旧的槽位数据，并恢复玩家改枪前的原始物品栏。

## 9. 为什么复活后一直卡旁观

优先检查动态复活点。

`teamdeathmatch` 的动态复活要同时满足：

- 点位本身安全
- 脚部和头部没有碰撞体
- 玩家包围盒落点不撞模
- 当前存在可用候选点

如果所有 `DYNAMIC_CANDIDATE` 都不可用，系统会保留旁观状态并继续重试。

排查顺序：

1. 双方是否都配置了足够数量的 `DYNAMIC_CANDIDATE`
2. 点位脚部和头部是否被有碰撞体的方块占住
3. 玩家落点附近是否因为墙体、半砖、天花板等原因导致包围盒撞模
4. 点位维度是否和地图维度一致

## 10. 比赛结束后为什么没有自动传回

最常见原因是历史地图没有设置 `endtp`，或是通过旧版本流程开了局。

当前 `ENDED` 阶段结束时会按三种情况处理：

- 有结束传送点且可用：统一传送
- 没有结束传送点：逐个提示缺失
- 有结束传送点但落点不可用：逐个提示回传失败

检查命令：

```mcfunction
/cdp map endtp show <地图名>
```

设置命令：

```mcfunction
/cdp map endtp set
```

补充说明：

- 当前版本发起开始投票前会强制检查 `endtp`
- `endtp set` 会直接覆盖所有地图的原值，并使用命令执行位置作为结束传送点
- 运行时仍会检查 `endtp` 落点脚部/头部是否无碰撞体，以及玩家包围盒是否会撞模

## 11. 为什么 `endtp` 命令会提示地图名歧义

因为现在只有 `endtp show` 还会按地图名查找，且不带模式参数。

如果 `frontline` 和 `teamdeathmatch` 下存在同名地图，`show` / `set` 都会报歧义。

处理方式：

- 避免两个模式使用同名地图
- 或先删掉其中一个重名地图再设置

## 12. 为什么新增背包后没有自动切过去

这是当前服务端逻辑的固定行为。

`BackpackConfigRepository.addCustomBackpack` 会在新增成功后尽量保留新增前的 `selectedBackpack`，不会强制把新背包设为当前背包。

如果需要切换，要手动点选那套背包。

## 13. 为什么我只复制了世界存档，地图还是全没了

因为地图不保存在世界存档里。

当前地图文件保存在：

- `fpsmatch/<世界名>/frontline/`
- `fpsmatch/<世界名>/teamdeathmatch/`

迁移时至少要同时复制：

1. 世界存档
2. `serverconfig/codpattern/`
3. `fpsmatch/`
