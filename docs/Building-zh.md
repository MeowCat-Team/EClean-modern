# 构建与依赖更新

[返回首页](../README-zh.md) · [English](Building.md) | 简体中文

默认构建 `common` 和 `paper`，需要 JDK 25，生成的插件位于 `paper/build/libs/`。Paper API 固定为 `26.1.2.build.74-stable`；项目提交 Gradle 依赖锁与 SHA-256 校验清单，wrapper 下载使用官方 SHA-256，CI Action 固定提交。校验清单以当前成功构建的依赖为基线，并不等同于漏洞扫描或所有发布方签名验证。

```shell
./gradlew build
```

Windows 使用 `gradlew.bat build`。

更新依赖时先修改版本目录，再显式生成候选锁与校验数据，审查下载来源和校验差异后提交，日常 CI 不自动刷新这些文件：

```shell
./gradlew build --write-locks --write-verification-metadata sha256
```

## 发布版本

`Release` 工作流会构建并测试 `modern` 分支，从 `build.gradle.kts` 读取版本号，从 `docs/Changelog.md` 提取对应的更新日志。创建 GitHub Release 后，会将同一份 Paper/Folia JAR 和更新日志发布到 [Modrinth 的 EClean-Modern 项目](https://modrinth.com/plugin/ecl-modern)。

请在仓库的 Actions secrets 中配置 `MODRINTH_TOKEN`，使用有权为项目 `VW7EmMIj`（`ecl-modern`）创建版本的 Modrinth 令牌。工作流会在创建 tag 前检查是否已配置令牌。Minecraft 版本从 `gradle/libs.versions.toml` 中固定的 Paper API 版本读取，平台标记为 Paper 和 Folia。若 GitHub Release 已成功、Modrinth 发布失败，可使用 **Re-run failed jobs** 单独重跑 `publish-modrinth`，无需重新创建 tag 或构建。
