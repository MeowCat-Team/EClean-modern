# 构建与依赖更新

[返回首页](../README-zh.md) · [English](Building.md) | 简体中文

默认构建 `common`、`paper` 和 Minecraft 26.1.2 的 Fabric 模块，需要 JDK 25。Paper 插件位于 `paper/build/libs/`，Fabric 模组位于 `fabric/build/26.1.2/libs/`。Paper API 固定为 `26.1.2.build.74-stable`；项目提交 Gradle 依赖锁与 SHA-256 校验清单，wrapper 下载使用官方 SHA-256，CI Action 固定提交。校验清单以当前成功构建的依赖为基线，并不等同于漏洞扫描或所有发布方签名验证。

```shell
./gradlew build
```

Windows 使用 `gradlew.bat build`。

Fabric 共用源码也支持 26.2，使用 `./gradlew :fabric:build -PminecraftVersion=26.2` 构建。两个游戏版本分别保存产物和依赖锁，CI 会验证两边。安装与运行说明见 [Fabric 文档](Fabric-zh.md)。

Fabric 使用 Mojang 的公开 API 编译，关闭 Loom 可选的访问与接口变换。构建只统一本地生成的服务端编译 JAR 的 ZIP 条目顺序和时间戳，保留全部类与资源字节，再按固定 SHA-256 校验完整 JAR，使全新环境的构建可复现，无需为依赖验证设置例外。

更新依赖时先修改版本目录，再显式生成候选锁与校验数据，审查下载来源和校验差异后提交，日常 CI 不自动刷新这些文件：

```shell
./gradlew build --write-locks --write-verification-metadata sha256
./gradlew :fabric:build -PminecraftVersion=26.2 --write-locks --write-verification-metadata sha256
```

## 发布版本

`Release` 工作流会构建并测试 `modern` 分支及两个 Fabric 目标版本，创建 tag 前会使用两份 Fabric 最终产物分别启动真实服务端进行验证。工作流从 `build.gradle.kts` 读取版本号，从 `docs/Changelog.md` 提取更新日志。GitHub Release 包含 Paper/Folia JAR 和两份 Fabric JAR，之后分别发布同一份产物和日志到 [Modrinth 的 EClean-Modern 项目](https://modrinth.com/plugin/ecl-modern)。

请在仓库的 Actions secrets 中配置 `MODRINTH_TOKEN`，使用有权为项目 `VW7EmMIj`（`ecl-modern`）创建版本的 Modrinth 令牌。工作流会在创建 tag 前检查令牌。Paper/Folia 的游戏版本跟随固定的 Paper API；Fabric 使用 `<version>+fabric.<minecraft>` 作为发布版本号，标注精确游戏版本与必需的 Fabric API。若 GitHub Release 已成功、Modrinth 发布失败，可使用 **Re-run failed jobs** 重跑失败的发布任务，无需重新创建 tag 或构建。
