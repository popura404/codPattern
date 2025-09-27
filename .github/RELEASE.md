# 发布指南

## 🚀 自动化发布流程

本项目使用GitHub Actions实现完全自动化的构建和发布流程。

### 📦 发布类型

#### 1. 正式发布 (Release)
- **触发条件**: 推送带有 `v` 前缀的标签 (例如: `v0.1.6b`, `v1.0.0`)
- **发布到**: GitHub Releases (标记为正式发布)
- **文件命名**: `codpattern-{version}.jar`

**创建正式发布步骤:**
```bash
# 1. 更新 gradle.properties 中的版本号
mod_version=0.1.7

# 2. 提交版本更新
git add gradle.properties
git commit -m "Bump version to 0.1.7"
git push

# 3. 创建并推送标签
git tag v0.1.7
git push origin v0.1.7
```

#### 2. 开发版本 (Pre-release)
- **触发条件**: 推送到 `develop` 分支
- **发布到**: GitHub Releases (标记为预发布)
- **文件命名**: `codpattern-{version}-dev-{date}-{commit}.jar`
- **标签格式**: `dev-YYYYMMDD-{short-commit}`

#### 3. 快照版本 (Snapshot)
- **触发条件**: 推送到 `main` 分支 (非标签推送)
- **发布到**: GitHub Releases (标记为预发布)
- **文件命名**: `codpattern-{version}-snapshot-{date}-{commit}.jar`
- **标签格式**: `snapshot-YYYYMMDD-{short-commit}`

### 🔧 工作流程详情

#### 构建流程 (`build.yml`)
1. **环境设置**: Java 17, Ubuntu Latest
2. **依赖缓存**: Gradle缓存以加速构建
3. **代码检查**: 运行 `codeCheck` 任务
4. **完整构建**: 使用 `releaseBuild` 任务
5. **生成源码**: 创建 sources JAR
6. **上传构件**: 保存构建产物

#### 发布流程
- **正式发布**: 自动检测版本标签，创建GitHub Release
- **预发布**: 自动生成时间戳标签，创建Pre-release
- **资产上传**: 自动上传主JAR和源码JAR

#### PR构建 (`pr-build.yml`)
- **触发条件**: Pull Request 创建/更新
- **快速构建**: 使用 `devBuild` 任务
- **状态反馈**: 在PR中自动评论构建状态

### 📋 构建要求

#### 系统要求
- Java 17 (Temurin发行版)
- Gradle 8.8+
- Ubuntu Latest (GitHub Actions)

#### 依赖项目
- Minecraft 1.20.1
- MinecraftForge 47.4.0+
- Timeless and Classics Zero (TACZ)

### 🛠️ 手动操作

#### 本地测试构建
```bash
# 快速开发构建
./gradlew devBuild

# 完整发布构建
./gradlew releaseBuild

# 生成发布包
./gradlew distPackage
```

#### 手动触发工作流
可以在GitHub Actions页面手动触发 `workflow_dispatch` 事件。

### 📝 版本管理策略

#### 版本号格式
- **主版本**: `x.y.z` (例如: `1.0.0`)
- **测试版本**: `x.y.zb` (例如: `0.1.6b`)
- **候选版本**: `x.y.z-rc.n` (例如: `1.0.0-rc.1`)

#### 分支策略
- **`main`**: 主分支，稳定代码
- **`develop`**: 开发分支，最新功能
- **`feature/*`**: 功能分支

### 🔍 故障排除

#### 构建失败
1. 检查Java版本兼容性
2. 验证Gradle构建脚本
3. 查看GitHub Actions日志
4. 确认依赖项可用性

#### 发布失败
1. 检查标签格式是否正确
2. 验证GitHub token权限
3. 确认Release API访问权限

### 📞 支持

如有问题，请：
1. 查看GitHub Actions运行日志
2. 在Issues页面报告问题
3. 检查本文档的故障排除部分