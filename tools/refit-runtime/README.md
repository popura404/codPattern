# 纯 TaCZ 发行环境改枪验收

这些命令从项目根目录运行。需要 Python 3.10 或更高版本、JDK 17、Minecraft 1.20.1 / Forge 47.4.0 的本地游戏库与资源、一次成功的本项目 Gradle 构建，以及要验证的 TaCZ 和 CDP 发行 JAR。构建会产生 ForgeGradle 缓存、命名过的编译依赖以及 `build/createMcpToSrg/output.tsrg`。可用 `--gradle-home` 指定已有 Gradle 缓存。Windows 与 Linux x86_64 均使用本机 Python 配套的本机 JDK。

### 准备服务端

```sh
python tools/refit-runtime/production_prepare.py --tacz-jar "/path/to/tacz.jar" --cdp-jar "/path/to/codpattern.jar" --libraries "/path/to/.minecraft/libraries" --output build/refit-production --gametest
```

把示例路径换为实际位置。Java 工具未加入 PATH 时给准备命令传 `--java` 和 `--javac`。准备过程从已安装的发行包复制原始 JAR，记录 SHA-256，构造正式 SRG 类路径并生成独立的 `smoke` 和 `gametest` 目录。可重复传 `--gunpack /path/to/pack.zip` 或枪包目录，让服务端和后续客户端使用同一份装备。每次准备指定新的输出目录。

在 Windows 上启动 `build/refit-production/gametest/launch.cmd`，在 Linux 上运行 `build/refit-production/gametest/launch.sh`，可执行 `codpattern_refit` 命名空间的普通验收。该配置单独加载用于启用生产 GameTest 钩子的测试 JAR。相邻的 `smoke` 配置则启动普通服务器。

### 服务器时长

```sh
python tools/refit-runtime/run_duration.py --runtime-dir build/refit-production
```

时长验收从准备结果创建独立的正常速率专服，使用 `codpattern_refit_duration`，实际执行安装、卸下、取消。通过条件包括超过 2400 tick、至少 120 秒实时时间以及服务器正常退出。`duration-normal-server*/duration-result.json` 和同目录的 `console.log` 保存本次结果。直接用加速的 GameTestServer 得到的 tick 数不代表这项时长结果。

### 客户端

```sh
python tools/refit-runtime/client_prepare.py --runtime-dir build/refit-production --libraries "/path/to/.minecraft/libraries" --assets "/path/to/.minecraft/assets" --output build/refit-client
python tools/refit-runtime/client_probe.py --runtime-dir build/refit-client --world "/path/to/stopped-test-world" --stage refit --slot primary
python tools/refit-runtime/client_probe.py --runtime-dir build/refit-client --world "/path/to/stopped-test-world" --stage immediate-close --slot secondary
```

`--world` 指向已关闭且含 `level.dat` 的测试世界。探针在客户端配置中复制该世界，经 CDP 背包界面进入实际 TaCZ 改枪页。每次运行结束后，世界副本与结果一并归档。可在相同参数上先加 `--prepare-only`，单独验证 Java 探针的编译和从开发命名到正式命名的重映射，再去掉该参数实际运行。`client_probe.py` 支持 `--javac`，默认沿用准备时的 Java 运行时。

每个阶段均可指定 `--slot primary` 或 `--slot secondary`。一次在同一个客户端目录运行一个探针。

| 阶段 | 验收内容 |
| --- | --- |
| `world` | 启动客户端并进入复制的测试世界 |
| `refit` | 从背包进入改枪，操作配件，保存后重开并核对 |
| `immediate-close` | 点击安装后在同一 tick 关闭，核对保存与重开结果 |
| `duration` | 在编辑器保持至少 120 秒、超过 2400 tick，然后安装、保存并重开 |
| `hot-filter` | 在服务端热更新过滤配置，核对正常 UI 中候选项隐藏、恢复以及恢复后的安装保存 |
| `hsv` | 通过真实滑块调整枪和配件的激光颜色，核对保存与重开。枪包中需要可配对且支持颜色编辑的枪和激光配件 |
| `large-catalog` | 用两个实际兼容配件构造 1057 个测试条目，通过目录同步、末页选择及安装检验分页 |

配置可从默认枪包或通过 `--gunpack` 准备的额外枪包选择测试装备。普通安装流程需要能容纳测试所需配件的枪与兼容候选；缺少装备会报告具体条件。测试结果写入 `build/refit-client/client/client-probe-runs/<run-id>/`，包括运行参数、测试模组哈希、控制台日志、结果及探针截图。成功需要匹配当前阶段的结果字段和正常的进程退出码。
