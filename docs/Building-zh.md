# 构建与依赖更新

[返回首页](../README-zh.md) · [English](Building.md) | 简体中文

`architectury` 分支包含五个模块：`common` 提供平台无关的规则与服务，`paper` 负责 Paper/Folia 适配，`mod-common` 提供共用原生 Minecraft 运行时，`fabric` 与 `neoforge` 提供加载器入口和转换后的产物。默认构建两种加载器的 Minecraft 26.1.2 版本，需要 JDK 25。Paper 插件位于 `paper/build/libs/`，模组位于 `<loader>/build/<minecraftVersion>/libs/`；Paper API 仍固定为 `26.1.2.build.74-stable`。

项目提交 Gradle 依赖锁与 SHA-256 校验清单，wrapper 下载使用官方 SHA-256，CI Action 固定提交。校验清单以当前成功构建的依赖为基线，并不等同于漏洞扫描或所有发布方签名验证。

```shell
./gradlew build
```

Windows 使用 `gradlew.bat build`。

两种模组加载器共用源码也支持 26.2，使用 `./gradlew :mod-common:check :fabric:build :neoforge:build -PminecraftVersion=26.2` 构建。各加载器、游戏版本分别保存产物与依赖锁，Build CI 验证四种组合。安装与运行说明见 [Fabric 文档](Fabric-zh.md) 和 [NeoForge 文档](NeoForge-zh.md)。

Paper 编译 API 的固定版本不代表运行兼容范围。已测试的具体构建与校验值记录在 [`paper/compatibility.json`](../paper/compatibility.json)，1.21.5–26.2 的范围及 Java 25 要求见 [Paper 文档](Paper-zh.md)。Build CI 除单元测试外，还会在清单中的每个 Paper 版本上验证最终插件 JAR。

Architectury 根据目标加载器转换 `mod-common`。最终模组包含领域代码、转换后的原生实现、共用资源，以及内置 Kotlin/配置库。两种加载器都需另外安装 Architectury API，Fabric 还需 Fabric API。生产 smoke 会核对实际运行的最终模组与匹配的运行依赖。

更新依赖时先修改版本目录，再显式生成候选锁与校验数据，审查下载来源和校验差异后提交，日常 CI 不自动刷新这些文件：

```shell
./gradlew build --write-locks --write-verification-metadata sha256
./gradlew :mod-common:check :fabric:build :neoforge:build -PminecraftVersion=26.2 --write-locks --write-verification-metadata sha256
```

## 发布版本

手动触发的 `Release` 工作流会构建并测试 `architectury` 分支，创建 tag 前使用 Paper 最终插件验证兼容清单中的每个版本，并使用四份 Fabric/NeoForge 产物分别启动真实服务端。工作流从 `build.gradle.kts` 读取版本号，从 `docs/Changelog.md` 提取更新日志。GitHub Release 包含一份 Paper/Folia JAR、两份 Fabric JAR 和两份 NeoForge JAR，之后分别发布同一份产物和日志到 [Modrinth 的 EClean-Modern 项目](https://modrinth.com/plugin/ecl-modern)。

请在仓库的 Actions secrets 中配置 `MODRINTH_TOKEN`，使用有权为项目 `VW7EmMIj`（`ecl-modern`）创建版本的 Modrinth 令牌。工作流会在创建 tag 前检查令牌。Paper/Folia 使用兼容清单中的完整已测试列表，历史 Alpha 构建在测试时显式启用。模组版本标注精确的游戏、加载器元数据与必需的 Architectury API，Fabric 另需 Fabric API。

Modrinth 版本号与显示名称标注平台及支持的游戏版本：

| 产物 | 版本号 | 显示名称 |
| --- | --- | --- |
| Paper/Folia | `<version>+paper.<minimum>-<maximum>` | `<version> (Paper / Folia, MC <minimum>-<maximum>)` |
| Fabric | `<version>+fabric.<minecraft>` | `<version> (Fabric, MC <minecraft>)` |
| NeoForge | `<version>+neoforge.<minecraft>` | `<version> (NeoForge, MC <minecraft>)` |

0.3.6 的 Paper 版本号为 `0.3.6+paper.1.21.5-26.2`，显示名称为 `0.3.6 (Paper / Folia, MC 1.21.5-26.2)`。NeoForge 示例为 `0.3.6+neoforge.26.2` / `0.3.6 (NeoForge, MC 26.2)`。工作流显式设置 `modrinth-version` 与 `modrinth-name`；Modrinth 的部分列表与选择器可能显示版本号。若 GitHub Release 已成功、Modrinth 发布失败，可使用 **Re-run failed jobs** 重跑失败的发布任务，无需重新创建 tag 或构建。

已发布的版本可运行 **Modrinth version names** 工作流，填写基础版本号（例如 `0.3.4`）。它只按该发布原有的游戏版本更新名称和版本号：单版本继续标注精确版本，多版本 Paper 使用范围，并保留文件和游戏元数据。可先在本地运行 `python .github/scripts/modrinth-names.py --base-version 0.3.4 --check` 预览改名计划。
