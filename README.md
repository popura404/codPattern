# COD Pattern

[中文文档](docs/README.md) | [English README](docs/README.en.md) | [详细 Guide](docs/GUIDE.md) | [Q&A](docs/QANDA.md) | [更新日志](docs/CHANGES.md)

> 当前版本：`0.8.6b`（Beta）

COD Pattern 是一个基于 **TaCZ + 内置 FPSM 兼容核心** 的 Forge 模组，提供 COD 风格的背包预设、局内改枪、房间与对局流程，以及对应的服务端存储和客户端 HUD。

主模组独立提供 `frontline`（FTL）与 `teamdeathmatch`（TDM）。Zombies 由可选的独立 addon 提供；LR Tactical 也是可选联动，未安装时自动禁用相关近战、投掷物选择、专用槽位和默认投掷物发放。

`0.8.6b` 的地图操作集中在游戏内工具：地图创建工具划定范围，出生点工具按模式能力编辑点位与区域，地图管理工具查看状态、改名、设置结束点、强制结束和处理删除。全局结束点默认值用于后续新建地图，已有地图可单独设置或通过命令批量覆盖。

- 运行环境：Minecraft `1.20.1`、Java `17`、Forge；项目构建使用 Forge `47.4.0`。
- 必需依赖：TaCZ `1.1.6+`。FPSM 兼容核心已内置，无需额外安装 `fpsmatch.jar`。
- 地图目录：`<世界>/serverconfig/codpattern/maps/`。旧存储通过 `/cdp map migrate check`、`/cdp map migrate confirm` 手动迁移，完成后重启服务端或重新进入单人世界。

- [项目功能、依赖与命令](docs/README.md)
- [安装、开图、管理、迁移与构建](docs/GUIDE.md)
- [常见问题](docs/QANDA.md)
- [英文说明](docs/README.en.md)
- [版本历史](docs/CHANGES.md)

本项目采用 [GPL-3.0-only](LICENSE.txt) 许可证，相关致谢见 [CREDITS.txt](CREDITS.txt)。
